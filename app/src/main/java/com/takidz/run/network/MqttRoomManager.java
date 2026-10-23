package com.takidz.run.network;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.takidz.run.RunApplication;
import com.takidz.run.models.AckPayload;
import com.takidz.run.models.DirectoryData;
import com.takidz.run.models.FeedbackBoardData;
import com.takidz.run.models.FeedbackPost;
import com.takidz.run.models.Message;
import com.takidz.run.models.OnlineUser;
import com.takidz.run.models.RoomData;
import com.takidz.run.models.SignalMessage;
import com.takidz.run.models.TaskItem;
import com.takidz.run.ui.RoomActivity;
import com.takidz.run.utils.FeedbackTouchHelper;
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

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Run v3.0.0 Serverless Real-Time MQTT Synchronization Engine.
 * 100% byte-compatible with `current-web-index-v2pro.html` and Android `2.0.0-pro`.
 * Implements:
 * - #17: Per-room identity (color + background style) synced via RoomData.meta & retained /identity topic
 * - #18: Quick emoji reactions synced via Message.reactions & retained /reactions topic
 * - #20: Subtle vibration/sound touch integration on incoming messages
 * - #24: Hashed device serial deduplication so one physical device = one online user
 * - #25 & #30: Encrypted code-based phone-to-phone transfer & Web-to-App account pairing
 * - #26: Batched/throttled delivery & read indicators (/acks) + old-client presence/echo inference
 * - #27: Timed per-room notification mute enforcement
 * - #29: Live ConnectionQuality indicator (CONNECTED / WEAK / RECONNECTING)
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
    public static final String TOPIC_FEEDBACK_BOARD = "runapp/v1/feedback/board";
    public static final String TOPIC_SIGNALS_PREFIX = "runapp/v1/signals/";
    public static final String TOPIC_TRANSFER_PREFIX = "runapp/v1/transfer/";

    private static final long HEARTBEAT_INTERVAL_MS = 20_000L; // 20s heartbeat
    private static final long PRESENCE_TTL_MS = 60_000L;       // 60s offline threshold
    private static final long TYPING_THROTTLE_MS = 2_500L;     // 2.5s typing throttle
    private static final long TYPING_EXPIRE_MS = 4_000L;       // 4s typing expiry
    private static final long ACK_BATCH_DELAY_MS = 1_500L;     // 1.5s batch throttle for #26 acks

    public enum ConnectionQuality {
        CONNECTED,
        WEAK,
        RECONNECTING
    }

    private static MqttRoomManager instance;

    private final Context appContext;
    private final PrefsManager prefs;
    private final Gson gson = RoomCryptoUtils.createProtocolGson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private MqttAsyncClient mqttClient;
    private int currentBrokerIndex = 0;
    private boolean isConnecting = false;
    private long lastPublishLatencyMs = 120L;
    private int consecutiveFailures = 0;

    // Lifecycle & Notification State
    private boolean appInForeground = true;
    private boolean chatActivelyVisible = false;
    private boolean pendingRoomStatePublish = false;
    private boolean joiningExistingRoom = false;
    private final Set<String> knownMessageIds = Collections.synchronizedSet(new HashSet<>());

    // Room, Directory, & Feedback State
    private DirectoryData directoryData = new DirectoryData();
    private FeedbackBoardData feedbackBoardData = new FeedbackBoardData();
    private String activeRoomCode = null;
    private RoomData activeRoomData = null;
    private boolean receivedInitialRoomState = false;

    // #17 & #18: Cached subtopic states so Web normalization never strips room identity or reactions
    private RoomData.RoomIdentity activeRoomIdentity = null;
    private final Map<String, Map<String, List<String>>> activeRoomReactions = new ConcurrentHashMap<>();

    // #26: Delivery & Read Receipt tracking
    private final Set<String> deliveredMessageIds = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> readMessageIds = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> outboundAckQueueIds = Collections.synchronizedSet(new HashSet<>());
    private String outboundAckStatus = "delivered";
    private boolean ackFlushScheduled = false;

    // #25 & #30: Pending transfer code redemption listener
    private String pendingTransferTopic = null;
    private String pendingTransferCode = null;
    private TransferRedeemCallback pendingTransferCallback = null;
    private Runnable pendingTransferTimeoutRunnable = null;

    // Presence & Typing maps (keyed by userId, deduplicated by deviceHash in #24)
    private final Map<String, Long> globalPresence = new ConcurrentHashMap<>();
    private final Map<String, OnlineUser> onlineUsersMap = new ConcurrentHashMap<>();
    private final Map<String, OnlineUser> roomPresenceUsers = new ConcurrentHashMap<>();
    private final Map<String, Long> roomPresence = new ConcurrentHashMap<>();
    private final Map<String, String> typingUsers = new ConcurrentHashMap<>();
    private final Map<String, Runnable> typingExpiryRunnables = new ConcurrentHashMap<>();
    private long lastTypingSentMs = 0L;

    public interface GlobalListener {
        void onConnectionChanged(boolean connected, String brokerUrl);
        default void onConnectionQualityChanged(ConnectionQuality quality, String brokerUrl) {}
        void onGlobalPresenceChanged(int onlineCount);
        void onDirectoryUpdated(DirectoryData directory);
        default void onOnlineUsersChanged(List<OnlineUser> onlineUsers) {}
        default void onFeedbackBoardUpdated(FeedbackBoardData board) {}
        default void onSignalReceived(SignalMessage signal) {}
    }

    public interface RoomListener {
        void onRoomStateUpdated(RoomData roomData);
        void onRoomPresenceChanged(int roomOnlineCount);
        void onTypingUsersChanged(List<String> typingNames);
        void onRoomDeleted();
        void onConnectionStateChanged(boolean connected);
        default void onConnectionQualityChanged(ConnectionQuality quality) {}
        default void onRoomIdentityUpdated(RoomData.RoomIdentity identity) {}
    }

    public interface TransferPublishCallback {
        void onCodeGenerated(String code);
        void onError(String message);
    }

    public interface TransferRedeemCallback {
        void onSuccess();
        void onError(String message);
    }

    private final List<GlobalListener> globalListeners = new ArrayList<>();
    private final List<RoomListener> roomListeners = new ArrayList<>();

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (!appInForeground) return;
            publishPresenceHeartbeat();
            pruneExpiredPresence();
            mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
    };

    private final Runnable flushAcksRunnable = new Runnable() {
        @Override
        public void run() {
            ackFlushScheduled = false;
            flushPendingAcksNow();
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

    public void setAppForeground(boolean inForeground) {
        if (this.appInForeground == inForeground) return;
        this.appInForeground = inForeground;
        mainHandler.removeCallbacks(heartbeatRunnable);
        if (inForeground) {
            ensureConnected();
            mainHandler.post(heartbeatRunnable);
        } else {
            clearGlobalPresence();
            if (activeRoomCode != null) {
                clearRoomPresence(activeRoomCode);
            }
        }
    }

    public void setChatActivelyVisible(boolean visible) {
        this.chatActivelyVisible = visible;
        if (visible && activeRoomData != null && activeRoomData.messages != null) {
            queueAckForMessagesFromOthers(activeRoomData.messages, "read");
        }
    }

    public void addGlobalListener(GlobalListener l) {
        if (!globalListeners.contains(l)) globalListeners.add(l);
        l.onConnectionChanged(isConnected(), getCurrentBrokerUrl());
        l.onConnectionQualityChanged(getConnectionQuality(), getCurrentBrokerUrl());
        l.onGlobalPresenceChanged(getGlobalOnlineCount());
        l.onDirectoryUpdated(directoryData);
        l.onOnlineUsersChanged(getOnlineUsersList());
        l.onFeedbackBoardUpdated(feedbackBoardData);
    }

    public void removeGlobalListener(GlobalListener l) {
        globalListeners.remove(l);
    }

    public void addRoomListener(RoomListener l) {
        if (!roomListeners.contains(l)) roomListeners.add(l);
        if (activeRoomData != null) {
            l.onRoomStateUpdated(activeRoomData);
        }
        if (activeRoomIdentity != null) {
            l.onRoomIdentityUpdated(activeRoomIdentity);
        }
        l.onRoomPresenceChanged(getRoomOnlineCount());
        l.onConnectionStateChanged(isConnected());
        l.onConnectionQualityChanged(getConnectionQuality());
    }

    public void removeRoomListener(RoomListener l) {
        roomListeners.remove(l);
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    /**
     * #29: Computes live connection quality state (CONNECTED / WEAK / RECONNECTING).
     */
    public ConnectionQuality getConnectionQuality() {
        if (!isConnected() || isConnecting) {
            return ConnectionQuality.RECONNECTING;
        }
        if (consecutiveFailures > 0 || lastPublishLatencyMs > 2200L || isNetworkConstrained()) {
            return ConnectionQuality.WEAK;
        }
        return ConnectionQuality.CONNECTED;
    }

    private boolean isNetworkConstrained() {
        try {
            ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network net = cm.getActiveNetwork();
            if (net == null) return true;
            NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            if (caps == null) return true;
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return true;
            int downKbps = caps.getLinkDownstreamBandwidthKbps();
            return downKbps > 0 && downKbps < 150;
        } catch (Exception e) {
            return false;
        }
    }

    public String getCurrentBrokerUrl() {
        return MQTT_BROKERS[currentBrokerIndex % MQTT_BROKERS.length];
    }

    public DirectoryData getDirectoryData() {
        return directoryData;
    }

    public FeedbackBoardData getFeedbackBoardData() {
        return feedbackBoardData;
    }

    public RoomData getActiveRoomData() {
        return activeRoomData;
    }

    public String getActiveRoomCode() {
        return activeRoomCode;
    }

    public RoomData.RoomIdentity getActiveRoomIdentity() {
        return activeRoomIdentity;
    }

    public synchronized void ensureConnected() {
        if (isConnected() || isConnecting) return;
        connectToBroker(currentBrokerIndex);
    }

    private void connectToBroker(int brokerIdx) {
        isConnecting = true;
        currentBrokerIndex = brokerIdx % MQTT_BROKERS.length;
        final String brokerUrl = MQTT_BROKERS[currentBrokerIndex];
        notifyConnectionChanged(false, brokerUrl);

        String userId = prefs.getUserId();
        String shortUser = userId.length() > 8 ? userId.substring(0, 8) : userId;
        String randSuffix = RoomCryptoUtils.randomCode(8).toLowerCase(Locale.US);
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
                    consecutiveFailures = 0;
                    mainHandler.post(() -> {
                        subscribeCoreTopics();
                        if (appInForeground) {
                            publishPresenceHeartbeat();
                        }
                        if (activeRoomCode != null && activeRoomData != null && pendingRoomStatePublish) {
                            publishRoomState();
                        }
                        notifyConnectionChanged(true, serverURI);
                    });
                }

                @Override
                public void connectionLost(Throwable cause) {
                    consecutiveFailures++;
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
            options.setConnectionTimeout(12);
            options.setKeepAliveInterval(30);
            options.setAutomaticReconnect(true);

            final long connectStart = System.currentTimeMillis();
            mqttClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    isConnecting = false;
                    lastPublishLatencyMs = Math.max(50L, System.currentTimeMillis() - connectStart);
                    consecutiveFailures = 0;
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.w(TAG, "Broker failed: " + brokerUrl + ", trying fallback...", exception);
                    isConnecting = false;
                    consecutiveFailures++;
                    int nextIdx = (currentBrokerIndex + 1) % MQTT_BROKERS.length;
                    mainHandler.postDelayed(() -> connectToBroker(nextIdx), 1500);
                }
            });
        } catch (Exception e) {
            isConnecting = false;
            Log.e(TAG, "MQTT init error", e);
        }
    }

    public void refreshSubscriptionsForCurrentUser() {
        subscribeCoreTopics();
        publishPresenceHeartbeat();
    }

    private void subscribeCoreTopics() {
        if (!isConnected()) return;
        try {
            mqttClient.subscribe(TOPIC_PRESENCE_PREFIX + "+", 0);
            mqttClient.subscribe(TOPIC_DIRECTORY, 1);
            mqttClient.subscribe(TOPIC_FEEDBACK_BOARD, 1);
            mqttClient.subscribe(TOPIC_SIGNALS_PREFIX + prefs.getUserId(), 1);
            if (activeRoomCode != null) {
                subscribeRoomTopics(activeRoomCode);
            }
            if (pendingTransferTopic != null) {
                mqttClient.subscribe(pendingTransferTopic, 1);
            }
        } catch (Exception e) {
            Log.e(TAG, "Subscribe error", e);
        }
    }

    private void subscribeRoomTopics(String code) {
        if (!isConnected() || code == null) return;
        try {
            String roomTopic = TOPIC_ROOM_PREFIX + code;
            mqttClient.subscribe(roomTopic, 1);
            mqttClient.subscribe(roomTopic + "/typing", 0);
            mqttClient.subscribe(roomTopic + "/presence/+", 0);
            mqttClient.subscribe(roomTopic + "/identity", 1);  // #17: Retained room identity
            mqttClient.subscribe(roomTopic + "/acks", 0);      // #26: Delivery & read receipts
            mqttClient.subscribe(roomTopic + "/reactions", 1); // #18: Retained message reactions
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
                    roomTopic + "/presence/+",
                    roomTopic + "/identity",
                    roomTopic + "/acks",
                    roomTopic + "/reactions"
            });
        } catch (Exception ignored) {}
    }

    public String createNewRoom(String roomName, boolean isPublic, String optionalPassword) {
        return createNewRoomWithIdentity(roomName, isPublic, optionalPassword, "#B8E646", "paper");
    }

    public String createNewRoomWithIdentity(String roomName, boolean isPublic, String optionalPassword, String accentHex, String bgStyle) {
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

        String now = RoomCryptoUtils.nowIso();
        String myId = prefs.getUserId();

        RoomData room = new RoomData();
        room.meta.code = code;
        room.meta.name = (roomName == null || roomName.trim().isEmpty()) ? ("Room " + code) : roomName.trim();
        room.meta.createdAt = now;
        room.meta.ownerId = myId;
        room.meta.accentColor = (accentHex != null && !accentHex.isEmpty()) ? accentHex : "#B8E646";
        room.meta.bgStyle = (bgStyle != null && !bgStyle.isEmpty()) ? bgStyle : "paper";
        room.meta.identityVersion = 1L;
        room.identity = new RoomData.RoomIdentity(code, myId, room.meta.accentColor, room.meta.bgStyle, 1L, now);
        room.note = new RoomData.NoteData("", prefs.getUserName(), now, 0L);

        prefs.markRoomOwned(code);

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
            entry.by = myId;
            addOrUpdateDirectoryEntry(entry);
        }

        joinRoom(code, room.meta.name, room);
        publishRoomIdentity(room.identity);
        publishRoomState();
        return code;
    }

    public void joinRoom(String code, String fallbackName, RoomData initialCreatedRoom) {
        String cleanCode = code.trim().toUpperCase(Locale.US);
        if (activeRoomCode != null && !activeRoomCode.equals(cleanCode)) {
            clearRoomPresence(activeRoomCode);
            unsubscribeRoomTopics(activeRoomCode);
        }

        activeRoomCode = cleanCode;
        roomPresence.clear();
        roomPresenceUsers.clear();
        clearAllTypingTimers();
        knownMessageIds.clear();
        activeRoomReactions.clear();
        activeRoomIdentity = null;

        receivedInitialRoomState = (initialCreatedRoom != null);
        joiningExistingRoom = (initialCreatedRoom == null);
        pendingRoomStatePublish = (initialCreatedRoom != null);

        if (initialCreatedRoom != null) {
            activeRoomData = initialCreatedRoom;
            activeRoomIdentity = initialCreatedRoom.identity;
        } else {
            // Load local room cache immediately so upgrade/re-entry feels instant with zero data loss
            RoomData cached = prefs.getCachedRoomData(cleanCode);
            if (cached != null && cached.meta != null) {
                activeRoomData = cached;
                activeRoomIdentity = cached.identity;
                if (cached.messages != null) {
                    for (Message m : cached.messages) {
                        if (m != null && m.id != null) knownMessageIds.add(m.id);
                    }
                }
            } else {
                activeRoomData = new RoomData();
                activeRoomData.meta.code = cleanCode;
                activeRoomData.meta.name = (fallbackName != null && !fallbackName.isEmpty()) ? fallbackName : ("Room " + cleanCode);
                activeRoomData.meta.createdAt = RoomCryptoUtils.nowIso();
                activeRoomData.note = new RoomData.NoteData();
            }
        }

        updateLocalAvatarInRoom(activeRoomData);
        saveActiveRoomToPrefs();

        ensureConnected();
        subscribeRoomTopics(cleanCode);
        if (appInForeground) {
            publishPresenceHeartbeat();
        }
    }

    private void saveActiveRoomToPrefs() {
        if (activeRoomCode == null || activeRoomData == null) return;
        String rName = (activeRoomData.meta != null && activeRoomData.meta.name != null && !activeRoomData.meta.name.isEmpty())
                ? activeRoomData.meta.name : ("Room " + activeRoomCode);
        String preview = "";
        if (activeRoomData.messages != null && !activeRoomData.messages.isEmpty()) {
            Message last = activeRoomData.messages.get(activeRoomData.messages.size() - 1);
            if (last != null) {
                String author = (last.author != null && !last.author.isEmpty()) ? last.author : "Guest";
                String body = last.isVoiceMessage() ? "🎙 Voice message"
                        : ("file".equals(last.type) ? ("📎 " + (last.text != null ? last.text : "Attachment"))
                        : (last.text != null ? last.text : ""));
                preview = author + ": " + body;
            }
        }
        String accent = (activeRoomIdentity != null) ? activeRoomIdentity.accentColor
                : (activeRoomData.meta != null ? activeRoomData.meta.accentColor : null);
        String bg = (activeRoomIdentity != null) ? activeRoomIdentity.bgStyle
                : (activeRoomData.meta != null ? activeRoomData.meta.bgStyle : null);
        String owner = (activeRoomIdentity != null) ? activeRoomIdentity.ownerId
                : (activeRoomData.meta != null ? activeRoomData.meta.ownerId : null);

        prefs.saveRecentRoomDetailed(activeRoomCode, rName, preview, accent, bg, owner);
        prefs.saveCachedRoomData(activeRoomCode, activeRoomData);
    }

    public void leaveActiveRoom() {
        if (activeRoomCode != null) {
            clearRoomPresence(activeRoomCode);
            unsubscribeRoomTopics(activeRoomCode);
            activeRoomCode = null;
            activeRoomData = null;
            activeRoomIdentity = null;
            activeRoomReactions.clear();
            pendingRoomStatePublish = false;
            joiningExistingRoom = false;
            roomPresence.clear();
            roomPresenceUsers.clear();
            clearAllTypingTimers();
            knownMessageIds.clear();
        }
    }

    public void syncLocalProfileToActiveRoom() {
        if (activeRoomData != null) {
            updateLocalAvatarInRoom(activeRoomData);
            notifyRoomStateUpdated();
            publishRoomState();
        }
    }

    private void updateLocalAvatarInRoom(RoomData room) {
        if (room == null) return;
        if (room.avatars == null) room.avatars = new HashMap<>();
        String userId = prefs.getUserId();
        String avatarUrl = prefs.getUserAvatarUrl();
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
            String name = prefs.getUserName().isEmpty() ? "Guest" : prefs.getUserName();
            room.avatars.put(userId, new RoomData.AvatarProfile(name, avatarUrl, prefs.getUserBio()));
        }
    }

    // ==========================================
    // #17: Per-Room Identity Sync
    // ==========================================

    public void updateActiveRoomIdentity(String accentColorHex, String bgStyle) {
        if (activeRoomCode == null || activeRoomData == null) return;
        String now = RoomCryptoUtils.nowIso();
        long nextVer = 1L;
        if (activeRoomIdentity != null) {
            nextVer = activeRoomIdentity.version + 1L;
        } else if (activeRoomData.meta != null && activeRoomData.meta.identityVersion > 0) {
            nextVer = activeRoomData.meta.identityVersion + 1L;
        }
        String ownerId = (activeRoomIdentity != null && activeRoomIdentity.ownerId != null && !activeRoomIdentity.ownerId.isEmpty())
                ? activeRoomIdentity.ownerId
                : ((activeRoomData.meta != null && activeRoomData.meta.ownerId != null && !activeRoomData.meta.ownerId.isEmpty())
                ? activeRoomData.meta.ownerId : prefs.getUserId());

        RoomData.RoomIdentity identity = new RoomData.RoomIdentity(
                activeRoomCode,
                ownerId,
                accentColorHex != null ? accentColorHex : "#B8E646",
                bgStyle != null ? bgStyle : "paper",
                nextVer,
                now
        );
        activeRoomIdentity = identity;
        if (activeRoomData.meta != null) {
            activeRoomData.meta.ownerId = ownerId;
            activeRoomData.meta.accentColor = identity.accentColor;
            activeRoomData.meta.bgStyle = identity.bgStyle;
            activeRoomData.meta.identityVersion = nextVer;
        }
        activeRoomData.identity = identity;
        prefs.markRoomOwned(activeRoomCode);
        saveActiveRoomToPrefs();

        publishRoomIdentity(identity);
        publishRoomState();
        notifyRoomIdentityUpdated(identity);
        notifyRoomStateUpdated();
    }

    private void publishRoomIdentity(RoomData.RoomIdentity identity) {
        if (!isConnected() || activeRoomCode == null || identity == null) return;
        try {
            String topic = TOPIC_ROOM_PREFIX + activeRoomCode + "/identity";
            String json = gson.toJson(identity);
            mqttClient.publish(topic, json.getBytes(StandardCharsets.UTF_8), 1, true);
        } catch (Exception ignored) {}
    }

    // ==========================================
    // #18: Message Emoji Reactions
    // ==========================================

    public void toggleMessageReaction(String messageId, String emoji) {
        if (activeRoomData == null || activeRoomData.messages == null || messageId == null || emoji == null) return;
        String myId = prefs.getUserId();
        for (Message m : activeRoomData.messages) {
            if (m != null && messageId.equals(m.id)) {
                if (m.reactions == null) m.reactions = new HashMap<>();
                List<String> users = m.reactions.get(emoji);
                if (users == null) {
                    users = new ArrayList<>();
                    m.reactions.put(emoji, users);
                }
                if (users.contains(myId)) {
                    users.remove(myId);
                    if (users.isEmpty()) {
                        m.reactions.remove(emoji);
                    }
                } else {
                    users.add(myId);
                }
                activeRoomReactions.put(messageId, new HashMap<>(m.reactions));
                break;
            }
        }
        publishRoomReactions();
        notifyRoomStateUpdated();
        publishRoomState();
    }

    private void publishRoomReactions() {
        if (!isConnected() || activeRoomCode == null) return;
        try {
            String topic = TOPIC_ROOM_PREFIX + activeRoomCode + "/reactions";
            String json = gson.toJson(activeRoomReactions);
            mqttClient.publish(topic, json.getBytes(StandardCharsets.UTF_8), 1, true);
        } catch (Exception ignored) {}
    }

    // ==========================================
    // Chat, Files, Voice, Tasks & Notes
    // ==========================================

    public Message sendTextMessage(String text, Message.ReplyRef replyTo) {
        if (activeRoomData == null || text == null || text.trim().isEmpty()) return null;
        Message msg = new Message();
        msg.id = RoomCryptoUtils.makeId("m");
        msg.type = "text";
        msg.text = text.trim();
        msg.author = prefs.getUserName().isEmpty() ? "Guest" : prefs.getUserName();
        msg.userId = prefs.getUserId();
        msg.createdAt = RoomCryptoUtils.nowIso();
        msg.replyTo = replyTo;
        msg.ackState = isConnected() ? 1 : 0;

        appendMessageAndPublish(msg);
        return msg;
    }

    public Message sendFileOrVoiceMessage(String type, Message.FileAttachment fileAttachment, String caption, Message.ReplyRef replyTo) {
        if (activeRoomData == null || fileAttachment == null) return null;
        Message msg = new Message();
        msg.id = RoomCryptoUtils.makeId("m");
        // For Web v2 Pro interop: Web expects msg.type = "file" and file.voice = true for voice messages!
        boolean isVoice = "voice".equalsIgnoreCase(type) || fileAttachment.voice;
        msg.type = "file";
        fileAttachment.voice = isVoice;

        // Ensure both dataUrl and url are populated properly for Web v2 Pro and Android v2 Pro
        String effective = fileAttachment.getEffectiveUrl();
        if (effective.startsWith("data:")) {
            fileAttachment.dataUrl = effective;
            fileAttachment.url = effective;
        } else {
            fileAttachment.url = effective;
        }

        msg.text = (caption != null && !caption.trim().isEmpty()) ? caption.trim() : fileAttachment.name;
        msg.caption = (caption != null && !caption.trim().isEmpty()) ? caption.trim() : null;
        fileAttachment.caption = msg.caption;
        msg.author = prefs.getUserName().isEmpty() ? "Guest" : prefs.getUserName();
        msg.userId = prefs.getUserId();
        msg.createdAt = RoomCryptoUtils.nowIso();
        msg.file = fileAttachment;
        msg.replyTo = replyTo;
        msg.ackState = isConnected() ? 1 : 0;

        appendMessageAndPublish(msg);
        return msg;
    }

    private void appendMessageAndPublish(Message msg) {
        updateLocalAvatarInRoom(activeRoomData);
        knownMessageIds.add(msg.id);
        // If another user is already active in the room right now, mark delivered once published
        if (getOtherUsersInRoomCount() > 0 && isConnected()) {
            deliveredMessageIds.add(msg.id);
            msg.ackState = 2;
        }
        activeRoomData.messages.add(msg);
        if (activeRoomData.messages.size() > 600) {
            activeRoomData.messages = new ArrayList<>(
                    activeRoomData.messages.subList(activeRoomData.messages.size() - 600, activeRoomData.messages.size())
            );
        }
        saveActiveRoomToPrefs();
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
        saveActiveRoomToPrefs();
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
        saveActiveRoomToPrefs();
        notifyRoomStateUpdated();
        publishRoomState();
    }

    public void deleteTask(String taskId) {
        if (activeRoomData == null) return;
        activeRoomData.tasks.removeIf(t -> t.id != null && t.id.equals(taskId));
        saveActiveRoomToPrefs();
        notifyRoomStateUpdated();
        publishRoomState();
    }

    public void updateRoomNote(String newContent) {
        if (activeRoomData == null) return;
        if (activeRoomData.note == null) {
            activeRoomData.note = new RoomData.NoteData();
        }
        activeRoomData.note.content = newContent != null ? newContent : "";
        activeRoomData.note.lastEditedBy = prefs.getUserName().isEmpty() ? "Guest" : prefs.getUserName();
        activeRoomData.note.lastEditedAt = RoomCryptoUtils.nowIso();
        activeRoomData.note.version++;
        saveActiveRoomToPrefs();
        notifyRoomStateUpdated();
        publishRoomState();
    }

    public void publishRoomState() {
        if (activeRoomCode == null || activeRoomData == null) return;
        if (!isConnected()) {
            pendingRoomStatePublish = true;
            return;
        }
        try {
            final long startMs = System.currentTimeMillis();
            String json = gson.toJson(activeRoomData);
            String topic = TOPIC_ROOM_PREFIX + activeRoomCode;
            mqttClient.publish(topic, json.getBytes(StandardCharsets.UTF_8), 1, true, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    lastPublishLatencyMs = Math.max(20L, System.currentTimeMillis() - startMs);
                    consecutiveFailures = 0;
                    mainHandler.post(() -> {
                        if (activeRoomData != null && activeRoomData.messages != null) {
                            boolean updated = false;
                            for (Message m : activeRoomData.messages) {
                                if (m != null && m.ackState == 0) {
                                    m.ackState = getOtherUsersInRoomCount() > 0 ? 2 : 1;
                                    updated = true;
                                }
                            }
                            if (updated) notifyRoomStateUpdated();
                        }
                        notifyQualityChanged();
                    });
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    consecutiveFailures++;
                    mainHandler.post(() -> notifyQualityChanged());
                }
            });
            pendingRoomStatePublish = false;
        } catch (Exception e) {
            pendingRoomStatePublish = true;
            Log.e(TAG, "Publish room state failed", e);
        }
    }

    // ==========================================
    // #26: Batched / Throttled Delivery & Read Acks
    // ==========================================

    private void queueAckForMessagesFromOthers(List<Message> messages, String status) {
        if (messages == null || activeRoomCode == null) return;
        String myId = prefs.getUserId();
        boolean addedAny = false;
        for (Message m : messages) {
            if (m != null && m.id != null && m.userId != null && !m.userId.equals(myId)) {
                if ("read".equals(status)) {
                    if (readMessageIds.add(m.id)) {
                        outboundAckQueueIds.add(m.id);
                        addedAny = true;
                    }
                } else {
                    if (deliveredMessageIds.add(m.id)) {
                        outboundAckQueueIds.add(m.id);
                        addedAny = true;
                    }
                }
            }
        }
        if ("read".equals(status)) {
            outboundAckStatus = "read";
        }
        if (addedAny && !ackFlushScheduled) {
            ackFlushScheduled = true;
            mainHandler.postDelayed(flushAcksRunnable, ACK_BATCH_DELAY_MS);
        }
    }

    private void flushPendingAcksNow() {
        if (!isConnected() || activeRoomCode == null || outboundAckQueueIds.isEmpty()) return;
        try {
            List<String> batch = new ArrayList<>(outboundAckQueueIds);
            outboundAckQueueIds.clear();
            if (batch.size() > 60) {
                batch = batch.subList(batch.size() - 60, batch.size());
            }
            AckPayload ack = new AckPayload(
                    prefs.getUserId(),
                    prefs.getDeviceIdentityHash(),
                    outboundAckStatus,
                    batch
            );
            outboundAckStatus = "delivered";
            String topic = TOPIC_ROOM_PREFIX + activeRoomCode + "/acks";
            mqttClient.publish(topic, gson.toJson(ack).getBytes(StandardCharsets.UTF_8), 0, false);
        } catch (Exception ignored) {}
    }

    private void handleIncomingAckPayload(String payload) {
        if (payload == null || payload.isEmpty() || activeRoomData == null || activeRoomData.messages == null) return;
        try {
            AckPayload ack = gson.fromJson(payload, AckPayload.class);
            if (ack == null || ack.fromUserId == null || ack.fromUserId.equals(prefs.getUserId())) return;
            if (ack.fromDeviceHash != null && !ack.fromDeviceHash.isEmpty()
                    && ack.fromDeviceHash.equals(prefs.getDeviceIdentityHash())) {
                return;
            }
            if (ack.messageIds == null || ack.messageIds.isEmpty()) return;

            boolean isRead = "read".equalsIgnoreCase(ack.status);
            boolean changed = false;
            for (String msgId : ack.messageIds) {
                if (msgId == null) continue;
                deliveredMessageIds.add(msgId);
                if (isRead) readMessageIds.add(msgId);
            }
            for (Message m : activeRoomData.messages) {
                if (m != null && m.id != null && ack.messageIds.contains(m.id)) {
                    int newState = isRead ? 3 : Math.max(m.ackState, 2);
                    if (m.ackState != newState) {
                        m.ackState = newState;
                        changed = true;
                    }
                }
            }
            if (changed) {
                mainHandler.post(this::notifyRoomStateUpdated);
            }
        } catch (Exception ignored) {
            // #26 contract: gracefully ignore unknown or malformed ack payloads
        }
    }

    /**
     * #26: Updates ack states for our own messages when old clients (Web v2 Pro / Android v2 Pro)
     * are in the room (inferring delivered when another user is online in the room, and read when
     * another user replies, types, or posts a newer message).
     */
    private void refreshAckStatesForActiveRoom(boolean peerJustTypedOrMessaged) {
        if (activeRoomData == null || activeRoomData.messages == null) return;
        String myId = prefs.getUserId();
        boolean hasOtherUserOnline = getOtherUsersInRoomCount() > 0;

        // Find timestamp of latest message from another user
        String latestOtherMsgIso = "";
        for (Message m : activeRoomData.messages) {
            if (m != null && m.userId != null && !m.userId.equals(myId) && m.createdAt != null) {
                if (m.createdAt.compareTo(latestOtherMsgIso) > 0) {
                    latestOtherMsgIso = m.createdAt;
                }
            }
        }

        for (Message m : activeRoomData.messages) {
            if (m == null || m.id == null || m.userId == null || !m.userId.equals(myId)) continue;
            if (readMessageIds.contains(m.id)) {
                m.ackState = 3;
            } else if (peerJustTypedOrMessaged || (!latestOtherMsgIso.isEmpty() && m.createdAt != null && m.createdAt.compareTo(latestOtherMsgIso) <= 0)) {
                readMessageIds.add(m.id);
                m.ackState = 3;
            } else if (deliveredMessageIds.contains(m.id) || hasOtherUserOnline) {
                deliveredMessageIds.add(m.id);
                m.ackState = Math.max(m.ackState, 2);
            } else if (m.ackState == 0 && isConnected()) {
                m.ackState = 1;
            }
        }
    }

    private int getOtherUsersInRoomCount() {
        String myId = prefs.getUserId();
        String myDev = prefs.getDeviceIdentityHash();
        long cutoff = System.currentTimeMillis() - PRESENCE_TTL_MS;
        int others = 0;
        for (OnlineUser u : roomPresenceUsers.values()) {
            if (u != null && u.lastSeenTs >= cutoff) {
                boolean selfById = u.userId != null && u.userId.equals(myId);
                boolean selfByDev = u.deviceHash != null && !u.deviceHash.isEmpty() && u.deviceHash.equals(myDev);
                if (!selfById && !selfByDev) {
                    others++;
                }
            }
        }
        return others;
    }

    // ==========================================
    // #25 & #30: Account Transfer & Web-App Pairing via Short Code
    // ==========================================

    public void publishAccountTransferCode(TransferPublishCallback callback) {
        ensureConnected();
        final String code = RoomCryptoUtils.randomCode6();
        final String topicId = RoomCryptoUtils.deriveTransferTopicId(code);
        final String topic = TOPIC_TRANSFER_PREFIX + topicId;
        final String json = prefs.exportBackupJson();
        final String encrypted = RoomCryptoUtils.encryptTransferPayload(json, code);

        mainHandler.postDelayed(() -> {
            if (!isConnected()) {
                if (callback != null) callback.onError("Not connected to MQTT broker yet. Please wait a moment and try again.");
                return;
            }
            try {
                mqttClient.publish(topic, encrypted.getBytes(StandardCharsets.UTF_8), 1, true, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        mainHandler.post(() -> {
                            if (callback != null) callback.onCodeGenerated(code);
                        });
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        mainHandler.post(() -> {
                            if (callback != null) callback.onError("Failed to publish pairing code.");
                        });
                    }
                });
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage());
            }
        }, isConnected() ? 50L : 1500L);
    }

    public void redeemAccountTransferCode(String rawCode, TransferRedeemCallback callback) {
        if (rawCode == null || rawCode.trim().isEmpty()) {
            if (callback != null) callback.onError("Please enter a valid code.");
            return;
        }
        final String cleanCode = rawCode.trim().toUpperCase(Locale.US);
        // Check if user pasted raw JSON backup directly
        if (rawCode.trim().startsWith("{")) {
            boolean ok = prefs.importTransferOrBackupJson(rawCode.trim(), true);
            if (ok) {
                refreshSubscriptionsForCurrentUser();
                if (callback != null) callback.onSuccess();
            } else {
                if (callback != null) callback.onError("Invalid backup data.");
            }
            return;
        }

        ensureConnected();
        final String topicId = RoomCryptoUtils.deriveTransferTopicId(cleanCode);
        pendingTransferTopic = TOPIC_TRANSFER_PREFIX + topicId;
        pendingTransferCode = cleanCode;
        pendingTransferCallback = callback;

        if (pendingTransferTimeoutRunnable != null) {
            mainHandler.removeCallbacks(pendingTransferTimeoutRunnable);
        }
        pendingTransferTimeoutRunnable = () -> {
            if (pendingTransferCallback != null) {
                TransferRedeemCallback cb = pendingTransferCallback;
                clearPendingTransferState();
                cb.onError("Pairing code not found or expired. Make sure the code was generated on your other device.");
            }
        };
        mainHandler.postDelayed(pendingTransferTimeoutRunnable, 8500L);

        if (isConnected()) {
            try {
                mqttClient.subscribe(pendingTransferTopic, 1);
            } catch (Exception e) {
                clearPendingTransferState();
                if (callback != null) callback.onError("Connection error: " + e.getMessage());
            }
        }
    }

    private void clearPendingTransferState() {
        if (pendingTransferTimeoutRunnable != null) {
            mainHandler.removeCallbacks(pendingTransferTimeoutRunnable);
            pendingTransferTimeoutRunnable = null;
        }
        if (isConnected() && pendingTransferTopic != null) {
            try { mqttClient.unsubscribe(pendingTransferTopic); } catch (Exception ignored) {}
        }
        pendingTransferTopic = null;
        pendingTransferCode = null;
        pendingTransferCallback = null;
    }

    public void deleteActiveRoom() {
        if (activeRoomCode == null) return;
        String codeToRemove = activeRoomCode;
        String dirId = (activeRoomData != null && activeRoomData.meta != null) ? activeRoomData.meta.dirId : null;

        if (isConnected()) {
            try {
                Set<String> usersToClear = new HashSet<>(roomPresence.keySet());
                usersToClear.add(prefs.getUserId());
                if (activeRoomData != null && activeRoomData.avatars != null) {
                    usersToClear.addAll(activeRoomData.avatars.keySet());
                }
                for (String uId : usersToClear) {
                    String presTopic = TOPIC_ROOM_PREFIX + codeToRemove + "/presence/" + uId;
                    mqttClient.publish(presTopic, new byte[0], 0, true);
                }
                mqttClient.publish(TOPIC_ROOM_PREFIX + codeToRemove + "/identity", new byte[0], 1, true);
                mqttClient.publish(TOPIC_ROOM_PREFIX + codeToRemove + "/reactions", new byte[0], 1, true);
                mqttClient.publish(TOPIC_ROOM_PREFIX + codeToRemove, new byte[0], 1, true);
            } catch (Exception ignored) {}
        }

        if (dirId != null && !dirId.isEmpty()) {
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

    public void removeDirectoryEntry(String dirId) {
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

    // ==========================================
    // Public Feedback Board (#11)
    // ==========================================

    public void createFeedbackPost(String title, String content, String category) {
        if (title == null || title.trim().isEmpty()) return;
        FeedbackPost post = new FeedbackPost();
        post.id = RoomCryptoUtils.makeId("fb");
        post.title = title.trim();
        post.content = (content != null) ? content.trim() : "";
        post.category = (category != null && !category.isEmpty()) ? category : "Idea";
        post.author = prefs.getUserName().isEmpty() ? "Guest" : prefs.getUserName();
        post.userId = prefs.getUserId();
        post.createdAt = RoomCryptoUtils.nowIso();
        post.upvotes = new ArrayList<>();
        post.upvotes.add(prefs.getUserId());
        post.hearts = new ArrayList<>();

        if (feedbackBoardData.posts == null) feedbackBoardData.posts = new ArrayList<>();
        feedbackBoardData.posts.add(0, post);
        publishFeedbackBoard();
    }

    public void toggleFeedbackVote(String postId, boolean isHeart) {
        if (feedbackBoardData == null || feedbackBoardData.posts == null || postId == null) return;
        String myId = prefs.getUserId();
        for (FeedbackPost p : feedbackBoardData.posts) {
            if (p != null && p.id != null && p.id.equals(postId)) {
                if (isHeart) {
                    if (p.hearts == null) p.hearts = new ArrayList<>();
                    if (p.hearts.contains(myId)) p.hearts.remove(myId);
                    else p.hearts.add(myId);
                } else {
                    if (p.upvotes == null) p.upvotes = new ArrayList<>();
                    if (p.upvotes.contains(myId)) p.upvotes.remove(myId);
                    else p.upvotes.add(myId);
                }
                break;
            }
        }
        publishFeedbackBoard();
    }

    public void deleteFeedbackPost(String postId) {
        if (feedbackBoardData == null || feedbackBoardData.posts == null || postId == null) return;
        boolean removed = feedbackBoardData.posts.removeIf(p -> p != null && postId.equals(p.id));
        if (removed) {
            publishFeedbackBoard();
        }
    }

    private void publishFeedbackBoard() {
        if (!isConnected()) return;
        try {
            String json = gson.toJson(feedbackBoardData);
            mqttClient.publish(TOPIC_FEEDBACK_BOARD, json.getBytes(StandardCharsets.UTF_8), 1, true);
            mainHandler.post(() -> {
                for (GlobalListener l : new ArrayList<>(globalListeners)) {
                    l.onFeedbackBoardUpdated(feedbackBoardData);
                }
            });
        } catch (Exception ignored) {}
    }

    // ==========================================
    // #13 & #24: Online Users (Device-Deduplicated) & P2P Signaling
    // ==========================================

    public List<OnlineUser> getOnlineUsersList() {
        long cutoff = System.currentTimeMillis() - PRESENCE_TTL_MS;
        String myId = prefs.getUserId();
        String myDeviceHash = prefs.getDeviceIdentityHash();

        if (isConnected() && appInForeground) {
            OnlineUser self = new OnlineUser(
                    myId,
                    myDeviceHash,
                    prefs.getUserName().isEmpty() ? "Guest" : prefs.getUserName(),
                    prefs.getUserAvatarUrl(),
                    prefs.getUserBio(),
                    System.currentTimeMillis(),
                    true,
                    "Android"
            );
            onlineUsersMap.put(myId, self);
        }

        // #24: Deduplicate by deviceHash (if present) or userId, keeping the freshest heartbeat
        Map<String, OnlineUser> deduped = new LinkedHashMap<>();
        for (OnlineUser u : onlineUsersMap.values()) {
            if (u == null || u.lastSeenTs < cutoff) continue;
            boolean isSelfUser = (u.userId != null && u.userId.equals(myId))
                    || (u.deviceHash != null && !u.deviceHash.isEmpty() && u.deviceHash.equals(myDeviceHash));
            u.isSelf = isSelfUser;
            String key = isSelfUser ? ("SELF::" + myDeviceHash) : u.getDedupeKey();
            OnlineUser existing = deduped.get(key);
            if (existing == null || u.lastSeenTs >= existing.lastSeenTs) {
                deduped.put(key, u);
            }
        }

        List<OnlineUser> list = new ArrayList<>(deduped.values());
        list.sort((a, b) -> {
            if (a.isSelf && !b.isSelf) return -1;
            if (!a.isSelf && b.isSelf) return 1;
            return Long.compare(b.lastSeenTs, a.lastSeenTs);
        });
        return list;
    }

    public void sendFriendRequest(OnlineUser targetUser) {
        if (targetUser == null || targetUser.userId == null || targetUser.isSelf) return;
        SignalMessage sig = new SignalMessage();
        sig.id = RoomCryptoUtils.makeId("sig");
        sig.type = SignalMessage.TYPE_FRIEND_REQUEST;
        sig.fromUserId = prefs.getUserId();
        sig.fromUserName = prefs.getUserName().isEmpty() ? "Guest" : prefs.getUserName();
        sig.fromAvatarUrl = prefs.getUserAvatarUrl();
        sig.toUserId = targetUser.userId;
        sig.timestamp = System.currentTimeMillis();

        prefs.addSentRequest(targetUser.userId);
        publishSignal(targetUser.userId, sig);
    }

    public void acceptFriendRequest(SignalMessage req) {
        if (req == null || req.fromUserId == null) return;
        prefs.addFriend(req.fromUserId, req.fromUserName, req.fromAvatarUrl);
        SignalMessage sig = new SignalMessage();
        sig.id = RoomCryptoUtils.makeId("sig");
        sig.type = SignalMessage.TYPE_FRIEND_ACCEPT;
        sig.fromUserId = prefs.getUserId();
        sig.fromUserName = prefs.getUserName().isEmpty() ? "Guest" : prefs.getUserName();
        sig.fromAvatarUrl = prefs.getUserAvatarUrl();
        sig.toUserId = req.fromUserId;
        sig.timestamp = System.currentTimeMillis();

        publishSignal(req.fromUserId, sig);
    }

    public void declineFriendRequest(SignalMessage req) {
        if (req == null || req.fromUserId == null) return;
        prefs.removePendingRequest(req.fromUserId);
        SignalMessage sig = new SignalMessage();
        sig.id = RoomCryptoUtils.makeId("sig");
        sig.type = SignalMessage.TYPE_FRIEND_DECLINE;
        sig.fromUserId = prefs.getUserId();
        sig.fromUserName = prefs.getUserName().isEmpty() ? "Guest" : prefs.getUserName();
        sig.toUserId = req.fromUserId;
        sig.timestamp = System.currentTimeMillis();

        publishSignal(req.fromUserId, sig);
    }

    public void sendRoomInvite(OnlineUser targetUser, String roomCode, String roomName) {
        if (targetUser == null || targetUser.userId == null || roomCode == null) return;
        SignalMessage sig = new SignalMessage();
        sig.id = RoomCryptoUtils.makeId("sig");
        sig.type = SignalMessage.TYPE_ROOM_INVITE;
        sig.fromUserId = prefs.getUserId();
        sig.fromUserName = prefs.getUserName().isEmpty() ? "Guest" : prefs.getUserName();
        sig.fromAvatarUrl = prefs.getUserAvatarUrl();
        sig.toUserId = targetUser.userId;
        sig.roomCode = roomCode;
        sig.roomName = (roomName != null && !roomName.isEmpty()) ? roomName : ("Room " + roomCode);
        sig.timestamp = System.currentTimeMillis();

        publishSignal(targetUser.userId, sig);
    }

    private void publishSignal(String targetUserId, SignalMessage signal) {
        if (!isConnected() || targetUserId == null) return;
        try {
            String topic = TOPIC_SIGNALS_PREFIX + targetUserId;
            String json = gson.toJson(signal);
            mqttClient.publish(topic, json.getBytes(StandardCharsets.UTF_8), 1, false);
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
            obj.addProperty("typing", true); // Required by current-web-index-v2pro.html!
            obj.addProperty("at", now);
            obj.addProperty("ts", now);
            String topic = TOPIC_ROOM_PREFIX + activeRoomCode + "/typing";
            mqttClient.publish(topic, obj.toString().getBytes(StandardCharsets.UTF_8), 0, false);
        } catch (Exception ignored) {}
    }

    private void publishPresenceHeartbeat() {
        if (!isConnected() || !appInForeground) return;
        long now = System.currentTimeMillis();
        String userId = prefs.getUserId();
        String deviceHash = prefs.getDeviceIdentityHash();
        String userName = prefs.getUserName().isEmpty() ? "Guest" : prefs.getUserName();
        String avatarUrl = prefs.getUserAvatarUrl();
        String bio = prefs.getUserBio();

        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("userId", userId);
            obj.addProperty("deviceHash", deviceHash); // #24: Hashed per-device serial
            obj.addProperty("name", userName);
            obj.addProperty("avatarUrl", avatarUrl);
            obj.addProperty("avatar", avatarUrl);      // Web v2 Pro compatibility field
            obj.addProperty("bio", bio);
            obj.addProperty("lastSeenTs", now);        // Web v2 Pro compatibility field
            obj.addProperty("at", now);                // Web v2 Pro compatibility field
            obj.addProperty("ts", now);                // Android v2 Pro compatibility field
            obj.addProperty("platform", "Android");
            byte[] bytes = obj.toString().getBytes(StandardCharsets.UTF_8);

            mqttClient.publish(TOPIC_PRESENCE_PREFIX + userId, bytes, 0, true);
            globalPresence.put(userId, now);
            OnlineUser selfUser = new OnlineUser(userId, deviceHash, userName, avatarUrl, bio, now, true, "Android");
            onlineUsersMap.put(userId, selfUser);

            if (activeRoomCode != null) {
                String roomPresTopic = TOPIC_ROOM_PREFIX + activeRoomCode + "/presence/" + userId;
                mqttClient.publish(roomPresTopic, bytes, 0, true);
                roomPresence.put(userId, now);
                roomPresenceUsers.put(userId, selfUser);
            }
        } catch (Exception ignored) {}
    }

    private void clearGlobalPresence() {
        if (!isConnected()) return;
        try {
            String userId = prefs.getUserId();
            mqttClient.publish(TOPIC_PRESENCE_PREFIX + userId, new byte[0], 0, true);
            globalPresence.remove(userId);
            onlineUsersMap.remove(userId);
        } catch (Exception ignored) {}
    }

    private void clearRoomPresence(String roomCode) {
        if (!isConnected() || roomCode == null) return;
        try {
            String userId = prefs.getUserId();
            String roomPresTopic = TOPIC_ROOM_PREFIX + roomCode + "/presence/" + userId;
            mqttClient.publish(roomPresTopic, new byte[0], 0, true);
            roomPresence.remove(userId);
            roomPresenceUsers.remove(userId);
        } catch (Exception ignored) {}
    }

    private void clearAllTypingTimers() {
        for (Runnable r : typingExpiryRunnables.values()) {
            mainHandler.removeCallbacks(r);
        }
        typingExpiryRunnables.clear();
        typingUsers.clear();
    }

    private void pruneExpiredPresence() {
        long cutoff = System.currentTimeMillis() - PRESENCE_TTL_MS;
        globalPresence.entrySet().removeIf(e -> e.getValue() < cutoff);
        onlineUsersMap.entrySet().removeIf(e -> e.getValue().lastSeenTs < cutoff);
        roomPresence.entrySet().removeIf(e -> e.getValue() < cutoff);
        roomPresenceUsers.entrySet().removeIf(e -> e.getValue().lastSeenTs < cutoff);

        final int count = getGlobalOnlineCount();
        final List<OnlineUser> users = getOnlineUsersList();

        for (GlobalListener l : new ArrayList<>(globalListeners)) {
            l.onGlobalPresenceChanged(count);
            l.onOnlineUsersChanged(users);
        }
        for (RoomListener l : new ArrayList<>(roomListeners)) {
            l.onRoomPresenceChanged(getRoomOnlineCount());
        }
    }

    /**
     * #24: Counts unique devices (not sessions/tabs or renamed duplicates).
     */
    public int getGlobalOnlineCount() {
        if (!isConnected()) return 0;
        List<OnlineUser> deduped = getOnlineUsersList();
        int count = deduped.size();
        if (count == 0 && appInForeground) count = 1;
        return count;
    }

    /**
     * #24: Counts unique devices in the active room.
     */
    public int getRoomOnlineCount() {
        if (!isConnected()) return 0;
        long cutoff = System.currentTimeMillis() - PRESENCE_TTL_MS;
        String myId = prefs.getUserId();
        String myDev = prefs.getDeviceIdentityHash();
        Set<String> uniqueKeys = new HashSet<>();
        for (OnlineUser u : roomPresenceUsers.values()) {
            if (u != null && u.lastSeenTs >= cutoff) {
                boolean isSelf = (u.userId != null && u.userId.equals(myId))
                        || (u.deviceHash != null && !u.deviceHash.isEmpty() && u.deviceHash.equals(myDev));
                uniqueKeys.add(isSelf ? ("SELF::" + myDev) : u.getDedupeKey());
            }
        }
        if (uniqueKeys.isEmpty() && activeRoomCode != null && appInForeground) {
            return 1;
        }
        return uniqueKeys.size();
    }

    private void handleIncomingMessage(String topic, byte[] payloadBytes) {
        String payload = payloadBytes != null ? new String(payloadBytes, StandardCharsets.UTF_8).trim() : "";

        // 0. #25 & #30: Account Transfer / Pairing Topic
        if (pendingTransferTopic != null && topic.equals(pendingTransferTopic)) {
            if (!payload.isEmpty() && pendingTransferCode != null && pendingTransferCallback != null) {
                final String decrypted = RoomCryptoUtils.decryptTransferPayload(payload, pendingTransferCode);
                mainHandler.post(() -> {
                    TransferRedeemCallback cb = pendingTransferCallback;
                    String topicToClear = pendingTransferTopic;
                    clearPendingTransferState();
                    if (decrypted != null && prefs.importTransferOrBackupJson(decrypted, true)) {
                        // Clear one-time transfer payload from broker
                        if (isConnected() && topicToClear != null) {
                            try { mqttClient.publish(topicToClear, new byte[0], 1, true); } catch (Exception ignored) {}
                        }
                        refreshSubscriptionsForCurrentUser();
                        if (cb != null) cb.onSuccess();
                    } else {
                        if (cb != null) cb.onError("Invalid or expired transfer code.");
                    }
                });
            }
            return;
        }

        // 1. Global Presence
        if (topic.startsWith(TOPIC_PRESENCE_PREFIX)) {
            String uId = topic.substring(TOPIC_PRESENCE_PREFIX.length());
            updatePresenceMap(globalPresence, onlineUsersMap, uId, payload);
            mainHandler.post(() -> {
                int count = getGlobalOnlineCount();
                List<OnlineUser> users = getOnlineUsersList();
                for (GlobalListener l : new ArrayList<>(globalListeners)) {
                    l.onGlobalPresenceChanged(count);
                    l.onOnlineUsersChanged(users);
                }
            });
            return;
        }

        // 2. Direct P2P Signals (Friend Requests, Room Invites)
        if (topic.startsWith(TOPIC_SIGNALS_PREFIX)) {
            if (!payload.isEmpty()) {
                try {
                    SignalMessage sig = gson.fromJson(payload, SignalMessage.class);
                    if (sig != null && sig.type != null) {
                        handleIncomingSignal(sig);
                    }
                } catch (Exception ignored) {}
            }
            return;
        }

        // 3. Public Directory
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

        // 4. Public Feedback Board
        if (TOPIC_FEEDBACK_BOARD.equals(topic)) {
            if (!payload.isEmpty()) {
                try {
                    FeedbackBoardData parsed = gson.fromJson(payload, FeedbackBoardData.class);
                    if (parsed != null && parsed.posts != null) {
                        feedbackBoardData = parsed;
                        mainHandler.post(() -> {
                            for (GlobalListener l : new ArrayList<>(globalListeners)) {
                                l.onFeedbackBoardUpdated(feedbackBoardData);
                            }
                        });
                    }
                } catch (Exception ignored) {}
            }
            return;
        }

        if (activeRoomCode == null) return;
        String expectedRoomTopic = TOPIC_ROOM_PREFIX + activeRoomCode;

        // 5. #17: Room Identity Retained Topic
        if (topic.equals(expectedRoomTopic + "/identity")) {
            if (!payload.isEmpty()) {
                try {
                    RoomData.RoomIdentity remoteId = gson.fromJson(payload, RoomData.RoomIdentity.class);
                    if (remoteId != null && remoteId.accentColor != null) {
                        if (activeRoomIdentity == null || remoteId.version >= activeRoomIdentity.version) {
                            activeRoomIdentity = remoteId;
                            if (activeRoomData != null) {
                                activeRoomData.identity = remoteId;
                                if (activeRoomData.meta != null) {
                                    activeRoomData.meta.ownerId = remoteId.ownerId;
                                    activeRoomData.meta.accentColor = remoteId.accentColor;
                                    activeRoomData.meta.bgStyle = remoteId.bgStyle;
                                    activeRoomData.meta.identityVersion = remoteId.version;
                                }
                                saveActiveRoomToPrefs();
                            }
                            mainHandler.post(() -> notifyRoomIdentityUpdated(remoteId));
                        }
                    }
                } catch (Exception ignored) {}
            }
            return;
        }

        // 6. #26: Room Delivery & Read Acks Topic
        if (topic.equals(expectedRoomTopic + "/acks")) {
            handleIncomingAckPayload(payload);
            return;
        }

        // 7. #18: Room Emoji Reactions Retained Topic
        if (topic.equals(expectedRoomTopic + "/reactions")) {
            if (!payload.isEmpty()) {
                try {
                    Type mapType = new TypeToken<Map<String, Map<String, List<String>>>>() {}.getType();
                    Map<String, Map<String, List<String>>> parsed = gson.fromJson(payload, mapType);
                    if (parsed != null) {
                        activeRoomReactions.putAll(parsed);
                        if (activeRoomData != null && activeRoomData.messages != null) {
                            for (Message m : activeRoomData.messages) {
                                if (m != null && m.id != null && activeRoomReactions.containsKey(m.id)) {
                                    m.reactions = activeRoomReactions.get(m.id);
                                }
                            }
                            mainHandler.post(this::notifyRoomStateUpdated);
                        }
                    }
                } catch (Exception ignored) {}
            }
            return;
        }

        // 8. Room Typing Indicator
        if (topic.equals(expectedRoomTopic + "/typing")) {
            try {
                JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
                String uId = obj.has("userId") ? obj.get("userId").getAsString() : "";
                String name = obj.has("name") ? obj.get("name").getAsString() : "User";
                boolean isTyping = !obj.has("typing") || obj.get("typing").getAsBoolean();
                if (!uId.isEmpty() && !uId.equals(prefs.getUserId())) {
                    Runnable prev = typingExpiryRunnables.get(uId);
                    if (prev != null) {
                        mainHandler.removeCallbacks(prev);
                    }
                    if (!isTyping) {
                        typingUsers.remove(uId);
                        typingExpiryRunnables.remove(uId);
                        notifyTypingChanged();
                    } else {
                        typingUsers.put(uId, name);
                        Runnable expireTask = () -> {
                            typingUsers.remove(uId);
                            typingExpiryRunnables.remove(uId);
                            notifyTypingChanged();
                        };
                        typingExpiryRunnables.put(uId, expireTask);
                        notifyTypingChanged();
                        mainHandler.postDelayed(expireTask, TYPING_EXPIRE_MS);
                        // #26: If a peer in the room is typing, our sent messages have been seen
                        refreshAckStatesForActiveRoom(true);
                        mainHandler.post(this::notifyRoomStateUpdated);
                    }
                }
            } catch (Exception ignored) {}
            return;
        }

        // 9. Room Presence
        if (topic.startsWith(expectedRoomTopic + "/presence/")) {
            String uId = topic.substring((expectedRoomTopic + "/presence/").length());
            updatePresenceMap(roomPresence, roomPresenceUsers, uId, payload);
            refreshAckStatesForActiveRoom(false);
            mainHandler.post(() -> {
                int count = getRoomOnlineCount();
                for (RoomListener l : new ArrayList<>(roomListeners)) {
                    l.onRoomPresenceChanged(count);
                }
                notifyRoomStateUpdated();
            });
            return;
        }

        // 10. Room State JSON
        if (topic.equals(expectedRoomTopic)) {
            if (payload.isEmpty()) {
                if (receivedInitialRoomState || joiningExistingRoom) {
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
                    boolean wasInitialSync = !receivedInitialRoomState;
                    receivedInitialRoomState = true;
                    joiningExistingRoom = false;

                    List<Message> newlyArrivedFromOthers = new ArrayList<>();
                    if (remote.messages != null) {
                        String myId = prefs.getUserId();
                        for (Message m : remote.messages) {
                            if (m != null && m.id != null) {
                                boolean wasKnown = !knownMessageIds.add(m.id);
                                if (!wasKnown && !wasInitialSync && m.userId != null && !m.userId.equals(myId)) {
                                    newlyArrivedFromOthers.add(m);
                                }
                            }
                        }
                    }

                    RoomData merged = mergeRoomData(remote, activeRoomData, activeRoomCode);
                    activeRoomData = merged;

                    // #26: Queue delivery/read acks for messages from others
                    if (merged.messages != null && !merged.messages.isEmpty()) {
                        String ackType = (appInForeground && chatActivelyVisible) ? "read" : "delivered";
                        queueAckForMessagesFromOthers(merged.messages, ackType);
                    }
                    refreshAckStatesForActiveRoom(!newlyArrivedFromOthers.isEmpty());
                    saveActiveRoomToPrefs();

                    if (!newlyArrivedFromOthers.isEmpty()) {
                        Message latest = newlyArrivedFromOthers.get(newlyArrivedFromOthers.size() - 1);
                        String rName = (merged.meta != null && merged.meta.name != null) ? merged.meta.name : ("Room " + activeRoomCode);
                        if (chatActivelyVisible && appInForeground && !prefs.isRoomMuted(activeRoomCode)) {
                            // #20: Subtle vibration/sound touch when message arrives in open room
                            FeedbackTouchHelper.onMessageReceived(appContext, prefs);
                        } else {
                            triggerLocalMessageNotification(activeRoomCode, rName, latest);
                        }
                    }

                    mainHandler.post(() -> {
                        if (activeRoomIdentity != null) {
                            notifyRoomIdentityUpdated(activeRoomIdentity);
                        }
                        notifyRoomStateUpdated();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed parsing room JSON", e);
            }
        }
    }

    private void handleIncomingSignal(SignalMessage sig) {
        mainHandler.post(() -> {
            boolean ar = prefs.isArabic();
            if (SignalMessage.TYPE_FRIEND_REQUEST.equals(sig.type)) {
                prefs.addPendingRequest(sig);
                triggerSignalNotification(
                        ar ? "طلب صداقة جديد" : "New Friend Request",
                        ar ? (sig.fromUserName + " أرسل لك طلب صداقة!") : (sig.fromUserName + " sent you a friend request!")
                );
            } else if (SignalMessage.TYPE_FRIEND_ACCEPT.equals(sig.type)) {
                prefs.addFriend(sig.fromUserId, sig.fromUserName, sig.fromAvatarUrl);
                triggerSignalNotification(
                        ar ? "تم قبول طلب الصداقة" : "Friend Request Accepted",
                        ar ? (sig.fromUserName + " قبل طلب الصداقة!") : (sig.fromUserName + " accepted your friend request!")
                );
            } else if (SignalMessage.TYPE_FRIEND_DECLINE.equals(sig.type)) {
                prefs.removeSentRequest(sig.fromUserId);
            } else if (SignalMessage.TYPE_ROOM_INVITE.equals(sig.type)) {
                prefs.addRoomInvite(sig);
                triggerSignalNotification(
                        ar ? ("دعوة إلى غرفة: " + sig.roomName) : ("Room Invite: " + sig.roomName),
                        ar ? (sig.fromUserName + " يدعوك للانضمام إلى #" + sig.roomCode)
                                : (sig.fromUserName + " invited you to join #" + sig.roomCode)
                );
            }

            for (GlobalListener l : new ArrayList<>(globalListeners)) {
                l.onSignalReceived(sig);
            }
        });
    }

    private void triggerSignalNotification(String title, String body) {
        if (!prefs.isNotificationsEnabled()) return;
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, RunApplication.CHANNEL_ID_MESSAGES)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true);

            NotificationManagerCompat.from(appContext).notify(
                    (title + body).hashCode(),
                    builder.build()
            );
        } catch (Exception ignored) {}
    }

    private void triggerLocalMessageNotification(String roomCode, String roomName, Message msg) {
        // #27: Respect both global notification toggle AND per-room timed mute!
        if (!prefs.isNotificationsEnabled() || prefs.isRoomMuted(roomCode) || chatActivelyVisible) return;
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        try {
            Intent intent = new Intent(appContext, RoomActivity.class);
            intent.putExtra("room_code", roomCode);
            intent.putExtra("room_name", roomName);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    appContext,
                    roomCode.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            boolean ar = prefs.isArabic();
            String author = (msg.author != null && !msg.author.isEmpty()) ? msg.author : (ar ? "مستخدم" : "User");
            String body;
            if (msg.isVoiceMessage()) {
                body = ar ? "🎙 أرسل رسالة صوتية" : "🎙 Sent a voice message";
            } else if ("file".equals(msg.type)) {
                body = (ar ? "📎 مرفق: " : "📎 Attachment: ") + (msg.text != null ? msg.text : "");
            } else {
                body = msg.text != null ? msg.text : (ar ? "رسالة جديدة" : "New message");
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, RunApplication.CHANNEL_ID_MESSAGES)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle(roomName + " • " + author)
                    .setContentText(body)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);

            NotificationManagerCompat.from(appContext).notify(
                    (roomCode + "_" + msg.id).hashCode(),
                    builder.build()
            );
        } catch (Exception e) {
            Log.w(TAG, "Notification error", e);
        }
    }

    private void updatePresenceMap(Map<String, Long> tsMap, Map<String, OnlineUser> userMap, String userId, String payload) {
        if (payload == null || payload.isEmpty()) {
            tsMap.remove(userId);
            userMap.remove(userId);
            return;
        }
        try {
            JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
            long ts = System.currentTimeMillis();
            if (obj.has("lastSeenTs") && !obj.get("lastSeenTs").isJsonNull()) {
                ts = obj.get("lastSeenTs").getAsLong();
            } else if (obj.has("at") && !obj.get("at").isJsonNull()) {
                ts = obj.get("at").getAsLong();
            } else if (obj.has("ts") && !obj.get("ts").isJsonNull()) {
                ts = obj.get("ts").getAsLong();
            }

            if (System.currentTimeMillis() - ts <= PRESENCE_TTL_MS) {
                String deviceHash = obj.has("deviceHash") && !obj.get("deviceHash").isJsonNull()
                        ? obj.get("deviceHash").getAsString() : "";
                String name = obj.has("name") && !obj.get("name").isJsonNull()
                        ? obj.get("name").getAsString() : "User";
                String avatarUrl = "";
                if (obj.has("avatarUrl") && !obj.get("avatarUrl").isJsonNull()) {
                    avatarUrl = obj.get("avatarUrl").getAsString();
                } else if (obj.has("avatar") && !obj.get("avatar").isJsonNull()) {
                    avatarUrl = obj.get("avatar").getAsString();
                }
                String bio = obj.has("bio") && !obj.get("bio").isJsonNull()
                        ? obj.get("bio").getAsString() : "";
                String platform = obj.has("platform") && !obj.get("platform").isJsonNull()
                        ? obj.get("platform").getAsString() : "Web";

                // #24: Remove stale entries from the same physical device (deviceHash) so renaming/clearing data never duplicates!
                if (!deviceHash.isEmpty()) {
                    for (Map.Entry<String, OnlineUser> entry : new ArrayList<>(userMap.entrySet())) {
                        OnlineUser existing = entry.getValue();
                        if (existing != null && deviceHash.equals(existing.deviceHash) && !entry.getKey().equals(userId)) {
                            if (ts >= existing.lastSeenTs) {
                                userMap.remove(entry.getKey());
                                tsMap.remove(entry.getKey());
                            }
                        }
                    }
                }

                boolean isSelf = userId.equals(prefs.getUserId())
                        || (!deviceHash.isEmpty() && deviceHash.equals(prefs.getDeviceIdentityHash()));
                tsMap.put(userId, ts);
                userMap.put(userId, new OnlineUser(userId, deviceHash, name, avatarUrl, bio, ts, isSelf, platform));
            } else {
                tsMap.remove(userId);
                userMap.remove(userId);
            }
        } catch (Exception e) {
            tsMap.put(userId, System.currentTimeMillis());
        }
    }

    private RoomData mergeRoomData(RoomData remote, RoomData local, String code) {
        RoomData out = new RoomData();
        out.kind = "run-room";
        out.version = 1;

        if (remote != null && remote.meta != null) {
            out.meta = remote.meta;
        } else if (local != null && local.meta != null) {
            out.meta = local.meta;
        }
        if (out.meta == null) {
            out.meta = new RoomData.Meta();
        }
        out.meta.code = code;
        if (out.meta.name == null || out.meta.name.trim().isEmpty()) {
            out.meta.name = (local != null && local.meta != null && local.meta.name != null && !local.meta.name.isEmpty())
                    ? local.meta.name : ("Room " + code);
        }

        // #17: Preserve Room Identity across merges even if a Web client normalized meta
        RoomData.RoomIdentity bestIdentity = activeRoomIdentity;
        if (remote != null && remote.identity != null) {
            if (bestIdentity == null || remote.identity.version >= bestIdentity.version) {
                bestIdentity = remote.identity;
            }
        }
        if (remote != null && remote.meta != null && remote.meta.accentColor != null) {
            if (bestIdentity == null || remote.meta.identityVersion >= bestIdentity.version) {
                bestIdentity = new RoomData.RoomIdentity(
                        code,
                        remote.meta.ownerId != null ? remote.meta.ownerId : "",
                        remote.meta.accentColor,
                        remote.meta.bgStyle != null ? remote.meta.bgStyle : "paper",
                        Math.max(1L, remote.meta.identityVersion),
                        RoomCryptoUtils.nowIso()
                );
            }
        }
        if (bestIdentity == null && local != null && local.identity != null) {
            bestIdentity = local.identity;
        }
        if (bestIdentity != null) {
            activeRoomIdentity = bestIdentity;
            out.identity = bestIdentity;
            out.meta.ownerId = bestIdentity.ownerId;
            out.meta.accentColor = bestIdentity.accentColor;
            out.meta.bgStyle = bestIdentity.bgStyle;
            out.meta.identityVersion = bestIdentity.version;
        }

        // Merge Messages by ID
        Map<String, Message> msgMap = new LinkedHashMap<>();
        if (local != null && local.messages != null) {
            for (Message m : local.messages) {
                if (m != null && m.id != null) msgMap.put(m.id, m);
            }
        }
        if (remote != null && remote.messages != null) {
            for (Message m : remote.messages) {
                if (m != null && m.id != null) {
                    Message existing = msgMap.get(m.id);
                    if (existing != null) {
                        m.translatedText = existing.translatedText;
                        m.showingTranslated = existing.showingTranslated;
                        m.ackState = Math.max(existing.ackState, m.ackState);
                        if ((m.reactions == null || m.reactions.isEmpty()) && existing.reactions != null) {
                            m.reactions = existing.reactions;
                        }
                    }
                    if (activeRoomReactions.containsKey(m.id)) {
                        m.reactions = activeRoomReactions.get(m.id);
                    } else if (m.reactions != null && !m.reactions.isEmpty()) {
                        activeRoomReactions.put(m.id, m.reactions);
                    }
                    msgMap.put(m.id, m);
                }
            }
        }
        List<Message> mergedMsgs = new ArrayList<>(msgMap.values());
        mergedMsgs.sort(Comparator.comparing(m -> m.createdAt != null ? m.createdAt : ""));
        if (mergedMsgs.size() > 600) {
            mergedMsgs = new ArrayList<>(mergedMsgs.subList(mergedMsgs.size() - 600, mergedMsgs.size()));
        }
        out.messages = mergedMsgs;

        // Merge Tasks by ID
        Map<String, TaskItem> taskMap = new LinkedHashMap<>();
        if (local != null && local.tasks != null) {
            for (TaskItem t : local.tasks) {
                if (t != null && t.id != null) taskMap.put(t.id, t);
            }
        }
        if (remote != null && remote.tasks != null) {
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
        if (remote != null && remote.avatars != null) out.avatars.putAll(remote.avatars);

        // Merge Shared Note
        if (remote != null && remote.note != null) {
            if (local == null || local.note == null || remote.note.version >= local.note.version) {
                out.note = remote.note;
            } else {
                out.note = local.note;
            }
        } else if (local != null && local.note != null) {
            out.note = local.note;
        } else {
            out.note = new RoomData.NoteData();
        }

        return out;
    }

    private void notifyConnectionChanged(boolean connected, String brokerUrl) {
        ConnectionQuality quality = getConnectionQuality();
        for (GlobalListener l : new ArrayList<>(globalListeners)) {
            l.onConnectionChanged(connected, brokerUrl);
            l.onConnectionQualityChanged(quality, brokerUrl);
        }
        for (RoomListener l : new ArrayList<>(roomListeners)) {
            l.onConnectionStateChanged(connected);
            l.onConnectionQualityChanged(quality);
        }
    }

    private void notifyQualityChanged() {
        ConnectionQuality quality = getConnectionQuality();
        String url = getCurrentBrokerUrl();
        for (GlobalListener l : new ArrayList<>(globalListeners)) {
            l.onConnectionQualityChanged(quality, url);
        }
        for (RoomListener l : new ArrayList<>(roomListeners)) {
            l.onConnectionQualityChanged(quality);
        }
    }

    private void notifyRoomStateUpdated() {
        if (activeRoomData == null) return;
        for (RoomListener l : new ArrayList<>(roomListeners)) {
            l.onRoomStateUpdated(activeRoomData);
        }
    }

    private void notifyRoomIdentityUpdated(RoomData.RoomIdentity identity) {
        if (identity == null) return;
        for (RoomListener l : new ArrayList<>(roomListeners)) {
            l.onRoomIdentityUpdated(identity);
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
