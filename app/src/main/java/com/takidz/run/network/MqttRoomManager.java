package com.takidz.run.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.takidz.run.models.DirectoryData;
import com.takidz.run.models.Message;
import com.takidz.run.models.RoomData;
import com.takidz.run.models.TaskItem;
import com.takidz.run.utils.PrefsManager;
import com.takidz.run.utils.RoomCryptoUtils;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Full Native Android port of the `run` MQTT real-time synchronization engine.
 * - Primary & Fallback WebSocket MQTT Brokers:
 *   1. wss://broker.emqx.io:8084/mqtt
 *   2. wss://broker.hivemq.com:8884/mqtt
 * - Exact Topic hierarchy:
 *   - Room state:      runapp/v1/room/<CODE> (Retained, QoS 1)
 *   - Public Directory: runapp/v1/directory  (Retained, QoS 1)
 *   - Typing indicator: runapp/v1/room/<CODE>/typing (Non-retained, QoS 0)
 *   - Global Presence:  runapp/v1/presence/<userId> (Retained, QoS 0)
 *   - Room Presence:    runapp/v1/room/<CODE>/presence/<userId> (Retained, QoS 0)
 */
public class MqttRoomManager {
    private static final String TAG = "MqttRoomManager";

    public static final String[] MQTT_BROKERS = new String[]{
            "wss://broker.emqx.io:8084/mqtt",
            "wss://broker.hivemq.com:8884/mqtt"
    };

    public static final String TOPIC_DIRECTORY = "runapp/v1/directory";
    public static final String TOPIC_PRESENCE_PREFIX = "runapp/v1/presence/";
    public static final String TOPIC_ROOM_PREFIX = "runapp/v1/room/";

    private static final long HEARTBEAT_INTERVAL_MS = 20_000L; // 20s heartbeat
    private static final long PRESENCE_TTL_MS = 60_000L;       // 60s offline threshold
    private static final long TYPING_THROTTLE_MS = 2_500L;     // 2.5s typing throttle
    private static final long TYPING_EXPIRE_MS = 4_000L;       // 4s typing expiry

    private static MqttRoomManager instance;

    private final Context appContext;
    private final PrefsManager prefs;
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private MqttAsyncClient mqttClient;
    private int currentBrokerIndex = 0;
    private boolean isConnecting = false;

    // State
    private DirectoryData directoryData = new DirectoryData();
    private String activeRoomCode = null;
    private RoomData activeRoomData = null;
    private boolean receivedInitialRoomState = false;

    // Presence maps: userId -> lastSeenTimestampMs
    private final Map<String, Long> globalPresence = new ConcurrentHashMap<>();
    private final Map<String, Long> roomPresence = new ConcurrentHashMap<>();
    private final Map<String, String> typingUsers = new ConcurrentHashMap<>();
    private long lastTypingSentMs = 0L;

    public interface GlobalListener {
        void onConnectionChanged(boolean connected, String brokerUrl);
        void onGlobalPresenceChanged(int onlineCount);
        void onDirectoryUpdated(DirectoryData directory);
    }

    public interface RoomListener {
        void onRoomStateUpdated(RoomData roomData);
        void onRoomPresenceChanged(int roomOnlineCount);
        void onTypingUsersChanged(List<String> typingNames);
        void onRoomDeleted();
        void onConnectionStateChanged(boolean connected);
    }

    private final List<GlobalListener> globalListeners = new ArrayList<>();
    private final List<RoomListener> roomListeners = new ArrayList<>();

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            publishPresenceHeartbeat();
            pruneExpiredPresence();
            mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
    };

    private MqttRoomManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = new PrefsManager(appContext);
        mainHandler.post(heartbeatRunnable);
    }

    public static synchronized MqttRoomManager getInstance(Context context) {
        if (instance == null) {
            instance = new MqttRoomManager(context);
        }
        return instance;
    }

    public void addGlobalListener(GlobalListener l) {
        if (!globalListeners.contains(l)) globalListeners.add(l);
        l.onConnectionChanged(isConnected(), getCurrentBrokerUrl());
        l.onGlobalPresenceChanged(getGlobalOnlineCount());
        l.onDirectoryUpdated(directoryData);
    }

    public void removeGlobalListener(GlobalListener l) {
        globalListeners.remove(l);
    }

    public void addRoomListener(RoomListener l) {
        if (!roomListeners.contains(l)) roomListeners.add(l);
        if (activeRoomData != null) {
            l.onRoomStateUpdated(activeRoomData);
        }
        l.onRoomPresenceChanged(getRoomOnlineCount());
        l.onConnectionStateChanged(isConnected());
    }

    public void removeRoomListener(RoomListener l) {
        roomListeners.remove(l);
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    public String getCurrentBrokerUrl() {
        return MQTT_BROKERS[currentBrokerIndex % MQTT_BROKERS.length];
    }

    public DirectoryData getDirectoryData() {
        return directoryData;
    }

    public RoomData getActiveRoomData() {
        return activeRoomData;
    }

    public synchronized void ensureConnected() {
        if (isConnected() || isConnecting) return;
        connectToBroker(currentBrokerIndex);
    }

    private void connectToBroker(int brokerIdx) {
        isConnecting = true;
        currentBrokerIndex = brokerIdx % MQTT_BROKERS.length;
        final String brokerUrl = MQTT_BROKERS[currentBrokerIndex];

        String userId = prefs.getUserId();
        String shortUser = userId.length() > 8 ? userId.substring(0, 8) : userId;
        String randSuffix = RoomCryptoUtils.randomCode(8).toLowerCase();
        String clientId = "run-" + shortUser + "-" + randSuffix;

        try {
            if (mqttClient != null) {
                try { mqttClient.close(); } catch (Exception ignored) {}
            }
            mqttClient = new MqttAsyncClient(brokerUrl, clientId, new MemoryPersistence());
            mqttClient.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    isConnecting = false;
                    mainHandler.post(() -> {
                        subscribeCoreTopics();
                        publishPresenceHeartbeat();
                        notifyConnectionChanged(true, serverURI);
                    });
                }

                @Override
                public void connectionLost(Throwable cause) {
                    mainHandler.post(() -> notifyConnectionChanged(false, brokerUrl));
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleIncomingMessage(topic, message.getPayload());
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(12); // 12 seconds as in web spec
            options.setKeepAliveInterval(30);
            options.setAutomaticReconnect(true);

            mqttClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    isConnecting = false;
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.w(TAG, "Broker failed: " + brokerUrl + ", trying fallback...", exception);
                    isConnecting = false;
                    // Fallback to next broker
                    int nextIdx = (currentBrokerIndex + 1) % MQTT_BROKERS.length;
                    mainHandler.postDelayed(() -> connectToBroker(nextIdx), 1500);
                }
            });
        } catch (Exception e) {
            isConnecting = false;
            Log.e(TAG, "MQTT init error", e);
        }
    }

    private void subscribeCoreTopics() {
        if (!isConnected()) return;
        try {
            mqttClient.subscribe(TOPIC_PRESENCE_PREFIX + "+", 0);
            mqttClient.subscribe(TOPIC_DIRECTORY, 1);
            if (activeRoomCode != null) {
                subscribeRoomTopics(activeRoomCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Subscribe error", e);
        }
    }

    private void subscribeRoomTopics(String code) {
        if (!isConnected() || code == null) return;
        try {
            String roomTopic = TOPIC_ROOM_PREFIX + code;
            String typingTopic = roomTopic + "/typing";
            String presenceTopic = roomTopic + "/presence/+";
            mqttClient.subscribe(roomTopic, 1);
            mqttClient.subscribe(typingTopic, 0);
            mqttClient.subscribe(presenceTopic, 0);
        } catch (Exception e) {
            Log.e(TAG, "Room subscribe error", e);
        }
    }

    private void unsubscribeRoomTopics(String code) {
        if (!isConnected() || code == null) return;
        try {
            String roomTopic = TOPIC_ROOM_PREFIX + code;
            mqttClient.unsubscribe(new String[]{
                    roomTopic,
                    roomTopic + "/typing",
                    roomTopic + "/presence/+"
            });
        } catch (Exception ignored) {}
    }

    /**
     * Create a brand new room (6 chars for private, 12 chars for public directory group)
     */
    public String createNewRoom(String roomName, boolean isPublic, String optionalPassword) {
        boolean hasPw = isPublic && optionalPassword != null && !optionalPassword.trim().isEmpty();
        String seed = hasPw ? RoomCryptoUtils.randomHexSeed(16) : null;
        String verifier = hasPw ? RoomCryptoUtils.computePasswordVerifier(seed, optionalPassword.trim()) : null;

        String code;
        if (isPublic) {
            if (hasPw) {
                code = RoomCryptoUtils.deriveProtectedGroupTopicKey(seed, optionalPassword.trim());
            } else {
                code = RoomCryptoUtils.randomCode12();
            }
        } else {
            code = RoomCryptoUtils.randomCode6();
        }

        RoomData room = new RoomData();
        room.meta.code = code;
        room.meta.name = (roomName == null || roomName.trim().isEmpty()) ? ("Room " + code) : roomName.trim();
        room.meta.createdAt = RoomCryptoUtils.nowIso();

        if (isPublic) {
            String dirId = RoomCryptoUtils.makeId("dir");
            room.meta.dirId = dirId;
            DirectoryData.PublicGroupEntry entry = new DirectoryData.PublicGroupEntry();
            entry.id = dirId;
            entry.name = room.meta.name;
            entry.hasPassword = hasPw;
            entry.key = hasPw ? null : code;
            entry.seed = seed;
            entry.verifier = verifier;
            entry.createdAt = room.meta.createdAt;
            addOrUpdateDirectoryEntry(entry);
        }

        joinRoom(code, room.meta.name, room);
        publishRoomState();
        return code;
    }

    /**
     * Join an existing room by 6 or 12 char code
     */
    public void joinRoom(String code, String fallbackName, RoomData initialCreatedRoom) {
        String cleanCode = code.trim().toUpperCase();
        if (activeRoomCode != null && !activeRoomCode.equals(cleanCode)) {
            clearRoomPresence(activeRoomCode);
            unsubscribeRoomTopics(activeRoomCode);
        }

        activeRoomCode = cleanCode;
        roomPresence.clear();
        typingUsers.clear();
        receivedInitialRoomState = (initialCreatedRoom != null);

        if (initialCreatedRoom != null) {
            activeRoomData = initialCreatedRoom;
        } else {
            activeRoomData = new RoomData();
            activeRoomData.meta.code = cleanCode;
            activeRoomData.meta.name = (fallbackName != null && !fallbackName.isEmpty()) ? fallbackName : ("Room " + cleanCode);
            activeRoomData.meta.createdAt = RoomCryptoUtils.nowIso();
        }

        updateLocalAvatarInRoom(activeRoomData);
        prefs.saveRecentRoom(cleanCode, activeRoomData.meta.name);

        ensureConnected();
        subscribeRoomTopics(cleanCode);
        publishPresenceHeartbeat();
    }

    public void leaveActiveRoom() {
        if (activeRoomCode != null) {
            clearRoomPresence(activeRoomCode);
            unsubscribeRoomTopics(activeRoomCode);
            activeRoomCode = null;
            activeRoomData = null;
            roomPresence.clear();
            typingUsers.clear();
        }
    }

    private void updateLocalAvatarInRoom(RoomData room) {
        if (room == null) return;
        String userId = prefs.getUserId();
        String name = prefs.getUserName();
        if (name.isEmpty()) name = "Guest";
        room.avatars.put(userId, new RoomData.AvatarProfile(name, "", prefs.getUserBio()));
    }

    public void sendTextMessage(String text, Message.ReplyRef replyTo) {
        if (activeRoomData == null || text == null || text.trim().isEmpty()) return;
        Message msg = new Message();
        msg.id = RoomCryptoUtils.makeId("m");
        msg.type = "text";
        msg.text = text.trim();
        msg.author = prefs.getUserName().isEmpty() ? "Guest" : prefs.getUserName();
        msg.userId = prefs.getUserId();
        msg.createdAt = RoomCryptoUtils.nowIso();
        msg.replyTo = replyTo;

        appendMessageAndPublish(msg);
    }

    public void sendFileOrVoiceMessage(String type, Message.FileAttachment fileAttachment, String caption, Message.ReplyRef replyTo) {
        if (activeRoomData == null || fileAttachment == null) return;
        Message msg = new Message();
        msg.id = RoomCryptoUtils.makeId("m");
        msg.type = type; // "file" or "voice"
        msg.text = caption != null ? caption : fileAttachment.name;
        msg.caption = caption;
        msg.author = prefs.getUserName().isEmpty() ? "Guest" : prefs.getUserName();
        msg.userId = prefs.getUserId();
        msg.createdAt = RoomCryptoUtils.nowIso();
        msg.file = fileAttachment;
        msg.replyTo = replyTo;

        appendMessageAndPublish(msg);
    }

    private void appendMessageAndPublish(Message msg) {
        updateLocalAvatarInRoom(activeRoomData);
        activeRoomData.messages.add(msg);
        if (activeRoomData.messages.size() > 600) {
            activeRoomData.messages = new ArrayList<>(
                    activeRoomData.messages.subList(activeRoomData.messages.size() - 600, activeRoomData.messages.size())
            );
        }
        notifyRoomStateUpdated();
        publishRoomState();
    }

    public void addTask(String taskTitle) {
        if (activeRoomData == null || taskTitle == null || taskTitle.trim().isEmpty()) return;
        TaskItem task = new TaskItem();
        task.id = RoomCryptoUtils.makeId("t");
        task.text = taskTitle.trim();
        task.done = false;
        task.author = prefs.getUserName().isEmpty() ? "Guest" : prefs.getUserName();
        task.userId = prefs.getUserId();
        task.createdAt = RoomCryptoUtils.nowIso();

        activeRoomData.tasks.add(task);
        if (activeRoomData.tasks.size() > 300) {
            activeRoomData.tasks = new ArrayList<>(
                    activeRoomData.tasks.subList(activeRoomData.tasks.size() - 300, activeRoomData.tasks.size())
            );
        }
        notifyRoomStateUpdated();
        publishRoomState();
    }

    public void toggleTask(String taskId, boolean done) {
        if (activeRoomData == null) return;
        for (TaskItem t : activeRoomData.tasks) {
            if (t.id != null && t.id.equals(taskId)) {
                t.done = done;
                break;
            }
        }
        notifyRoomStateUpdated();
        publishRoomState();
    }

    public void deleteTask(String taskId) {
        if (activeRoomData == null) return;
        activeRoomData.tasks.removeIf(t -> t.id != null && t.id.equals(taskId));
        notifyRoomStateUpdated();
        publishRoomState();
    }

    public void publishRoomState() {
        if (!isConnected() || activeRoomCode == null || activeRoomData == null) return;
        try {
            String json = gson.toJson(activeRoomData);
            String topic = TOPIC_ROOM_PREFIX + activeRoomCode;
            mqttClient.publish(topic, json.getBytes(StandardCharsets.UTF_8), 1, true);
        } catch (Exception e) {
            Log.e(TAG, "Publish room state failed", e);
        }
    }

    /**
     * Delete room: publish empty retained payload on room topic, remove from directory & recent rooms.
     */
    public void deleteActiveRoom() {
        if (activeRoomCode == null) return;
        String codeToRemove = activeRoomCode;
        String dirId = (activeRoomData != null && activeRoomData.meta != null) ? activeRoomData.meta.dirId : null;

        if (isConnected()) {
            try {
                mqttClient.publish(TOPIC_ROOM_PREFIX + codeToRemove, new byte[0], 1, true);
            } catch (Exception ignored) {}
        }

        if (dirId != null) {
            removeDirectoryEntry(dirId);
        }
        prefs.removeRecentRoom(codeToRemove);
        leaveActiveRoom();
        for (RoomListener l : new ArrayList<>(roomListeners)) {
            l.onRoomDeleted();
        }
    }

    private void addOrUpdateDirectoryEntry(DirectoryData.PublicGroupEntry entry) {
        if (directoryData.groups == null) directoryData.groups = new ArrayList<>();
        directoryData.groups.removeIf(g -> g.id != null && g.id.equals(entry.id));
        directoryData.groups.add(0, entry);
        publishDirectory();
    }

    private void removeDirectoryEntry(String dirId) {
        if (directoryData.groups == null) return;
        boolean removed = directoryData.groups.removeIf(g -> g.id != null && g.id.equals(dirId));
        if (removed) {
            publishDirectory();
        }
    }

    private void publishDirectory() {
        if (!isConnected()) return;
        try {
            String json = gson.toJson(directoryData);
            mqttClient.publish(TOPIC_DIRECTORY, json.getBytes(StandardCharsets.UTF_8), 1, true);
            for (GlobalListener l : new ArrayList<>(globalListeners)) {
                l.onDirectoryUpdated(directoryData);
            }
        } catch (Exception ignored) {}
    }

    public void sendTypingSignal() {
        if (!isConnected() || activeRoomCode == null) return;
        long now = System.currentTimeMillis();
        if (now - lastTypingSentMs < TYPING_THROTTLE_MS) return;
        lastTypingSentMs = now;

        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("userId", prefs.getUserId());
            obj.addProperty("name", prefs.getUserName().isEmpty() ? "Guest" : prefs.getUserName());
            obj.addProperty("ts", now);
            String topic = TOPIC_ROOM_PREFIX + activeRoomCode + "/typing";
            mqttClient.publish(topic, obj.toString().getBytes(StandardCharsets.UTF_8), 0, false);
        } catch (Exception ignored) {}
    }

    private void publishPresenceHeartbeat() {
        if (!isConnected()) return;
        long now = System.currentTimeMillis();
        String userId = prefs.getUserId();
        String userName = prefs.getUserName().isEmpty() ? "Guest" : prefs.getUserName();

        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("userId", userId);
            obj.addProperty("name", userName);
            obj.addProperty("ts", now);
            byte[] bytes = obj.toString().getBytes(StandardCharsets.UTF_8);

            mqttClient.publish(TOPIC_PRESENCE_PREFIX + userId, bytes, 0, true);
            globalPresence.put(userId, now);

            if (activeRoomCode != null) {
                String roomPresTopic = TOPIC_ROOM_PREFIX + activeRoomCode + "/presence/" + userId;
                mqttClient.publish(roomPresTopic, bytes, 0, true);
                roomPresence.put(userId, now);
            }
        } catch (Exception ignored) {}
    }

    private void clearRoomPresence(String roomCode) {
        if (!isConnected() || roomCode == null) return;
        try {
            String roomPresTopic = TOPIC_ROOM_PREFIX + roomCode + "/presence/" + prefs.getUserId();
            mqttClient.publish(roomPresTopic, new byte[0], 0, true);
        } catch (Exception ignored) {}
    }

    private void pruneExpiredPresence() {
        long cutoff = System.currentTimeMillis() - PRESENCE_TTL_MS;
        globalPresence.entrySet().removeIf(e -> e.getValue() < cutoff);
        roomPresence.entrySet().removeIf(e -> e.getValue() < cutoff);

        for (GlobalListener l : new ArrayList<>(globalListeners)) {
            l.onGlobalPresenceChanged(getGlobalOnlineCount());
        }
        for (RoomListener l : new ArrayList<>(roomListeners)) {
            l.onRoomPresenceChanged(getRoomOnlineCount());
        }
    }

    public int getGlobalOnlineCount() {
        long cutoff = System.currentTimeMillis() - PRESENCE_TTL_MS;
        int count = 0;
        for (Long ts : globalPresence.values()) {
            if (ts != null && ts >= cutoff) count++;
        }
        return Math.max(1, count);
    }

    public int getRoomOnlineCount() {
        long cutoff = System.currentTimeMillis() - PRESENCE_TTL_MS;
        int count = 0;
        for (Long ts : roomPresence.values()) {
            if (ts != null && ts >= cutoff) count++;
        }
        return Math.max(1, count);
    }

    private void handleIncomingMessage(String topic, byte[] payloadBytes) {
        String payload = payloadBytes != null ? new String(payloadBytes, StandardCharsets.UTF_8).trim() : "";

        // 1. Global Presence
        if (topic.startsWith(TOPIC_PRESENCE_PREFIX)) {
            String uId = topic.substring(TOPIC_PRESENCE_PREFIX.length());
            updatePresenceMap(globalPresence, uId, payload);
            mainHandler.post(() -> {
                int count = getGlobalOnlineCount();
                for (GlobalListener l : new ArrayList<>(globalListeners)) {
                    l.onGlobalPresenceChanged(count);
                }
            });
            return;
        }

        // 2. Public Directory
        if (TOPIC_DIRECTORY.equals(topic)) {
            if (!payload.isEmpty()) {
                try {
                    DirectoryData parsed = gson.fromJson(payload, DirectoryData.class);
                    if (parsed != null && parsed.groups != null) {
                        directoryData = parsed;
                        mainHandler.post(() -> {
                            for (GlobalListener l : new ArrayList<>(globalListeners)) {
                                l.onDirectoryUpdated(directoryData);
                            }
                        });
                    }
                } catch (Exception ignored) {}
            }
            return;
        }

        if (activeRoomCode == null) return;
        String expectedRoomTopic = TOPIC_ROOM_PREFIX + activeRoomCode;

        // 3. Room Typing Indicator
        if (topic.equals(expectedRoomTopic + "/typing")) {
            try {
                JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
                String uId = obj.has("userId") ? obj.get("userId").getAsString() : "";
                String name = obj.has("name") ? obj.get("name").getAsString() : "User";
                if (!uId.isEmpty() && !uId.equals(prefs.getUserId())) {
                    typingUsers.put(uId, name);
                    notifyTypingChanged();
                    mainHandler.postDelayed(() -> {
                        typingUsers.remove(uId);
                        notifyTypingChanged();
                    }, TYPING_EXPIRE_MS);
                }
            } catch (Exception ignored) {}
            return;
        }

        // 4. Room Presence
        if (topic.startsWith(expectedRoomTopic + "/presence/")) {
            String uId = topic.substring((expectedRoomTopic + "/presence/").length());
            updatePresenceMap(roomPresence, uId, payload);
            mainHandler.post(() -> {
                int count = getRoomOnlineCount();
                for (RoomListener l : new ArrayList<>(roomListeners)) {
                    l.onRoomPresenceChanged(count);
                }
            });
            return;
        }

        // 5. Room State JSON
        if (topic.equals(expectedRoomTopic)) {
            if (payload.isEmpty()) {
                // Empty retained payload means the room was deleted!
                if (receivedInitialRoomState) {
                    final String deletedCode = activeRoomCode;
                    mainHandler.post(() -> {
                        prefs.removeRecentRoom(deletedCode);
                        leaveActiveRoom();
                        for (RoomListener l : new ArrayList<>(roomListeners)) {
                            l.onRoomDeleted();
                        }
                    });
                }
                return;
            }

            try {
                RoomData remote = gson.fromJson(payload, RoomData.class);
                if (remote != null) {
                    receivedInitialRoomState = true;
                    RoomData merged = mergeRoomData(remote, activeRoomData, activeRoomCode);
                    activeRoomData = merged;
                    if (merged.meta != null && merged.meta.name != null && !merged.meta.name.isEmpty()) {
                        prefs.saveRecentRoom(activeRoomCode, merged.meta.name);
                    }
                    mainHandler.post(this::notifyRoomStateUpdated);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed parsing room JSON", e);
            }
        }
    }

    private void updatePresenceMap(Map<String, Long> map, String userId, String payload) {
        if (payload == null || payload.isEmpty()) {
            map.remove(userId);
            return;
        }
        try {
            JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
            long ts = obj.has("ts") ? obj.get("ts").getAsLong() : System.currentTimeMillis();
            if (System.currentTimeMillis() - ts <= PRESENCE_TTL_MS) {
                map.put(userId, ts);
            } else {
                map.remove(userId);
            }
        } catch (Exception e) {
            map.put(userId, System.currentTimeMillis());
        }
    }

    /**
     * Merges remote and local RoomData by item IDs, matching the web app's mergeRoomData(remote, local)
     */
    private RoomData mergeRoomData(RoomData remote, RoomData local, String code) {
        RoomData out = new RoomData();
        out.kind = "run-room";
        out.version = 1;

        if (remote.meta != null) {
            out.meta = remote.meta;
        } else if (local != null && local.meta != null) {
            out.meta = local.meta;
        }
        out.meta.code = code;

        // Merge Messages by ID
        Map<String, Message> msgMap = new LinkedHashMap<>();
        if (local != null && local.messages != null) {
            for (Message m : local.messages) {
                if (m != null && m.id != null) msgMap.put(m.id, m);
            }
        }
        if (remote.messages != null) {
            for (Message m : remote.messages) {
                if (m != null && m.id != null) {
                    Message existing = msgMap.get(m.id);
                    if (existing != null) {
                        m.translatedText = existing.translatedText;
                        m.showingTranslated = existing.showingTranslated;
                    }
                    msgMap.put(m.id, m);
                }
            }
        }
        List<Message> mergedMsgs = new ArrayList<>(msgMap.values());
        Collections.sort(mergedMsgs, Comparator.comparing(m -> m.createdAt != null ? m.createdAt : ""));
        if (mergedMsgs.size() > 600) {
            mergedMsgs = new ArrayList<>(mergedMsgs.subList(mergedMsgs.size() - 600, mergedMsgs.size()));
        }
        out.messages = mergedMsgs;

        // Merge Tasks by ID (remote overwrites existing ID's done status)
        Map<String, TaskItem> taskMap = new LinkedHashMap<>();
        if (local != null && local.tasks != null) {
            for (TaskItem t : local.tasks) {
                if (t != null && t.id != null) taskMap.put(t.id, t);
            }
        }
        if (remote.tasks != null) {
            for (TaskItem t : remote.tasks) {
                if (t != null && t.id != null) taskMap.put(t.id, t);
            }
        }
        List<TaskItem> mergedTasks = new ArrayList<>(taskMap.values());
        if (mergedTasks.size() > 300) {
            mergedTasks = new ArrayList<>(mergedTasks.subList(mergedTasks.size() - 300, mergedTasks.size()));
        }
        out.tasks = mergedTasks;

        // Merge Avatars
        out.avatars = new HashMap<>();
        if (local != null && local.avatars != null) out.avatars.putAll(local.avatars);
        if (remote.avatars != null) out.avatars.putAll(remote.avatars);

        return out;
    }

    private void notifyConnectionChanged(boolean connected, String brokerUrl) {
        for (GlobalListener l : new ArrayList<>(globalListeners)) {
            l.onConnectionChanged(connected, brokerUrl);
        }
        for (RoomListener l : new ArrayList<>(roomListeners)) {
            l.onConnectionStateChanged(connected);
        }
    }

    private void notifyRoomStateUpdated() {
        if (activeRoomData == null) return;
        for (RoomListener l : new ArrayList<>(roomListeners)) {
            l.onRoomStateUpdated(activeRoomData);
        }
    }

    private void notifyTypingChanged() {
        mainHandler.post(() -> {
            List<String> names = new ArrayList<>(typingUsers.values());
            for (RoomListener l : new ArrayList<>(roomListeners)) {
                l.onTypingUsersChanged(names);
            }
        });
    }
}
