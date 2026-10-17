package com.takidz.run.network;
import android.Manifest;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.takidz.run.RunApplication;
import com.takidz.run.models.DirectoryData;
import com.takidz.run.models.Message;
import com.takidz.run.models.RoomData;
import com.takidz.run.models.TaskItem;
import com.takidz.run.ui.RoomActivity;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
/**
* Full Native Android port of the `run` MQTT real-time synchronization engine.

* Includes Audit Fixes:
* - #1 & F1: Real local notifications when new messages arrive while chat is in background.
* - #4: Offline pending state sync on reconnect (pendingRoomStatePublish).
* - #6: Proper ghost/deleted room detection when joining a deleted room (joiningExistingRoom).
* - #9: Null-safety for RoomData.meta in mergeRoomData().
* - #10: Accurate online presence counter without artificial Math.max(1, count).
* - #11: Lifecycle-aware presence heartbeat (pauses heartbeat & clears presence when
backgrounded).
* - #19: Cancels previous typing expiry Runnable per user before scheduling a new one.
* - #20: Clears all room presence topics when deleting a room.
* - F5: Syncs user profile avatarUrl in room state.
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

private static final long PRESENCE_TTL_MS = 60_000L;

// 60s offline threshold

private static final long TYPING_THROTTLE_MS = 2_500L;

// 2.5s typing throttle

private static final long TYPING_EXPIRE_MS = 4_000L;

// 4s typing expiry

private static MqttRoomManager instance;
private final Context appContext;
private final PrefsManager prefs;
private final Gson gson = new Gson();
private final Handler mainHandler = new Handler(Looper.getMainLooper());
private MqttAsyncClient mqttClient;
private int currentBrokerIndex = 0;
private boolean isConnecting = false;
// Lifecycle & Notification State (#1, #4, #6, #11)
private boolean appInForeground = true;
private boolean chatActivelyVisible = false;
private boolean pendingRoomStatePublish = false;
private boolean joiningExistingRoom = false;
private final Set<String> knownMessageIds = Collections.synchronizedSet(new
HashSet<>());
// Room & Directory State
private DirectoryData directoryData = new DirectoryData();
private String activeRoomCode = null;
private RoomData activeRoomData = null;

private boolean receivedInitialRoomState = false;
// Presence & Typing maps
private final Map<String, Long> globalPresence = new ConcurrentHashMap<>();
private final Map<String, Long> roomPresence = new ConcurrentHashMap<>();
private final Map<String, String> typingUsers = new ConcurrentHashMap<>();
private final Map<String, Runnable> typingExpiryRunnables = new ConcurrentHashMap<>();
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
if (!appInForeground) return;
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
/**
* Fix #11: Bind heartbeat to foreground lifecycle so it doesn't drain battery forever.

*/
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
if (appInForeground) {
publishPresenceHeartbeat();
}
// Fix #4: Publish any room state created/updated while offline
if (activeRoomCode != null && activeRoomData != null &&
pendingRoomStatePublish) {
publishRoomState();
}
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
options.setConnectionTimeout(12);
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
public String createNewRoom(String roomName, boolean isPublic, String optionalPassword)
{
boolean hasPw = isPublic && optionalPassword != null &&
!optionalPassword.trim().isEmpty();
String seed = hasPw ? RoomCryptoUtils.randomHexSeed(16) : null;
String verifier = hasPw ? RoomCryptoUtils.computePasswordVerifier(seed,
optionalPassword.trim()) : null;
String code;
if (isPublic) {
if (hasPw) {
code = RoomCryptoUtils.deriveProtectedGroupTopicKey(seed,
optionalPassword.trim());
} else {
code = RoomCryptoUtils.randomCode12();
}
} else {
code = RoomCryptoUtils.randomCode6();
}
RoomData room = new RoomData();
room.meta.code = code;

room.meta.name = (roomName == null || roomName.trim().isEmpty()) ? ("Room " + code) :
roomName.trim();
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
public void joinRoom(String code, String fallbackName, RoomData initialCreatedRoom) {
String cleanCode = code.trim().toUpperCase();

if (activeRoomCode != null && !activeRoomCode.equals(cleanCode)) {
clearRoomPresence(activeRoomCode);
unsubscribeRoomTopics(activeRoomCode);
}
activeRoomCode = cleanCode;
roomPresence.clear();
clearAllTypingTimers();
knownMessageIds.clear();
receivedInitialRoomState = (initialCreatedRoom != null);
joiningExistingRoom = (initialCreatedRoom == null);
pendingRoomStatePublish = (initialCreatedRoom != null);
if (initialCreatedRoom != null) {
activeRoomData = initialCreatedRoom;
} else {
activeRoomData = new RoomData();
activeRoomData.meta.code = cleanCode;
activeRoomData.meta.name = (fallbackName != null && !fallbackName.isEmpty()) ?
fallbackName : ("Room " + cleanCode);
activeRoomData.meta.createdAt = RoomCryptoUtils.nowIso();
}
updateLocalAvatarInRoom(activeRoomData);
prefs.saveRecentRoom(cleanCode, activeRoomData.meta.name);

ensureConnected();
subscribeRoomTopics(cleanCode);
if (appInForeground) {
publishPresenceHeartbeat();
}
}
public void leaveActiveRoom() {
if (activeRoomCode != null) {
clearRoomPresence(activeRoomCode);
unsubscribeRoomTopics(activeRoomCode);
activeRoomCode = null;
activeRoomData = null;
pendingRoomStatePublish = false;
joiningExistingRoom = false;
roomPresence.clear();
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
String name = prefs.getUserName();
if (name.isEmpty()) name = "Guest";
// Fix #16 & F5: Include user's chosen avatarUrl
room.avatars.put(userId, new RoomData.AvatarProfile(name, prefs.getUserAvatarUrl(),
prefs.getUserBio()));
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
public void sendFileOrVoiceMessage(String type, Message.FileAttachment fileAttachment,
String caption, Message.ReplyRef replyTo) {
if (activeRoomData == null || fileAttachment == null) return;
Message msg = new Message();
msg.id = RoomCryptoUtils.makeId("m");
msg.type = type; // "file" or "voice"
msg.text = (caption != null && !caption.trim().isEmpty()) ? caption.trim() :
fileAttachment.name;
msg.caption = (caption != null && !caption.trim().isEmpty()) ? caption.trim() : null;
fileAttachment.caption = msg.caption;
msg.author = prefs.getUserName().isEmpty() ? "Guest" : prefs.getUserName();
msg.userId = prefs.getUserId();
msg.createdAt = RoomCryptoUtils.nowIso();
msg.file = fileAttachment;
msg.replyTo = replyTo;
appendMessageAndPublish(msg);
}
private void appendMessageAndPublish(Message msg) {

updateLocalAvatarInRoom(activeRoomData);
knownMessageIds.add(msg.id);
activeRoomData.messages.add(msg);
if (activeRoomData.messages.size() > 600) {
activeRoomData.messages = new ArrayList<>(
activeRoomData.messages.subList(activeRoomData.messages.size() - 600,
activeRoomData.messages.size())
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
activeRoomData.tasks.subList(activeRoomData.tasks.size() - 300,
activeRoomData.tasks.size())
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
/**
* Fix #4: Marks pendingRoomStatePublish = true if offline, and clears it once published.
*/
public void publishRoomState() {
if (activeRoomCode == null || activeRoomData == null) return;
if (!isConnected()) {
pendingRoomStatePublish = true;
return;
}
try {
String json = gson.toJson(activeRoomData);
String topic = TOPIC_ROOM_PREFIX + activeRoomCode;
mqttClient.publish(topic, json.getBytes(StandardCharsets.UTF_8), 1, true);
pendingRoomStatePublish = false;
} catch (Exception e) {
pendingRoomStatePublish = true;
Log.e(TAG, "Publish room state failed", e);
}

}
/**
* Fix #20: Clears all room presence topics on the broker before deleting the room state.
*/
public void deleteActiveRoom() {
if (activeRoomCode == null) return;
String codeToRemove = activeRoomCode;
String dirId = (activeRoomData != null && activeRoomData.meta != null) ?
activeRoomData.meta.dirId : null;
if (isConnected()) {
try {
// Clear retained presence for all known users in this room (#20)
Set<String> usersToClear = new HashSet<>(roomPresence.keySet());
usersToClear.add(prefs.getUserId());
if (activeRoomData != null && activeRoomData.avatars != null) {
usersToClear.addAll(activeRoomData.avatars.keySet());
}
for (String uId : usersToClear) {
String presTopic = TOPIC_ROOM_PREFIX + codeToRemove + "/presence/" + uId;
mqttClient.publish(presTopic, new byte[0], 0, true);
}
// Clear retained room state

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
mqttClient.publish(TOPIC_DIRECTORY, json.getBytes(StandardCharsets.UTF_8), 1,
true);
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
obj.addProperty("name", prefs.getUserName().isEmpty() ? "Guest" :
prefs.getUserName());
obj.addProperty("ts", now);
String topic = TOPIC_ROOM_PREFIX + activeRoomCode + "/typing";
mqttClient.publish(topic, obj.toString().getBytes(StandardCharsets.UTF_8), 0, false);
} catch (Exception ignored) {}
}
private void publishPresenceHeartbeat() {
if (!isConnected() || !appInForeground) return;
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

String roomPresTopic = TOPIC_ROOM_PREFIX + activeRoomCode + "/presence/" +
userId;
mqttClient.publish(roomPresTopic, bytes, 0, true);
roomPresence.put(userId, now);
}
} catch (Exception ignored) {}
}
private void clearGlobalPresence() {
if (!isConnected()) return;
try {
String userId = prefs.getUserId();
mqttClient.publish(TOPIC_PRESENCE_PREFIX + userId, new byte[0], 0, true);
globalPresence.remove(userId);
} catch (Exception ignored) {}
}
private void clearRoomPresence(String roomCode) {
if (!isConnected() || roomCode == null) return;
try {
String userId = prefs.getUserId();
String roomPresTopic = TOPIC_ROOM_PREFIX + roomCode + "/presence/" + userId;
mqttClient.publish(roomPresTopic, new byte[0], 0, true);
roomPresence.remove(userId);

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
roomPresence.entrySet().removeIf(e -> e.getValue() < cutoff);
for (GlobalListener l : new ArrayList<>(globalListeners)) {
l.onGlobalPresenceChanged(getGlobalOnlineCount());
}
for (RoomListener l : new ArrayList<>(roomListeners)) {
l.onRoomPresenceChanged(getRoomOnlineCount());
}
}
/**
* Fix #10: Honest online count without artificial Math.max(1, count).

*/
public int getGlobalOnlineCount() {
if (!isConnected()) return 0;
long cutoff = System.currentTimeMillis() - PRESENCE_TTL_MS;
int count = 0;
for (Long ts : globalPresence.values()) {
if (ts != null && ts >= cutoff) count++;
}
return count;
}
/**
* Fix #10: Honest room online count without artificial Math.max(1, count).
*/
public int getRoomOnlineCount() {
if (!isConnected()) return 0;
long cutoff = System.currentTimeMillis() - PRESENCE_TTL_MS;
int count = 0;
for (Long ts : roomPresence.values()) {
if (ts != null && ts >= cutoff) count++;
}
return count;

}
private void handleIncomingMessage(String topic, byte[] payloadBytes) {
String payload = payloadBytes != null ? new String(payloadBytes,
StandardCharsets.UTF_8).trim() : "";
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
// 3. Room Typing Indicator (Fix #19: Cancel previous runnable for same user)
if (topic.equals(expectedRoomTopic + "/typing")) {
try {
JsonObject obj = JsonParser.parseString(payload).getAsJsonObject();
String uId = obj.has("userId") ? obj.get("userId").getAsString() : "";
String name = obj.has("name") ? obj.get("name").getAsString() : "User";
if (!uId.isEmpty() && !uId.equals(prefs.getUserId())) {
typingUsers.put(uId, name);
Runnable prev = typingExpiryRunnables.get(uId);

if (prev != null) {
mainHandler.removeCallbacks(prev);
}
Runnable expireTask = () -> {
typingUsers.remove(uId);
typingExpiryRunnables.remove(uId);
notifyTypingChanged();
};
typingExpiryRunnables.put(uId, expireTask);
notifyTypingChanged();
mainHandler.postDelayed(expireTask, TYPING_EXPIRE_MS);
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
// Fix #6: Handle empty retained payload both when room was deleted live AND when
joining a deleted room
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
// Detect newly arrived messages for local notifications (#1 & F1)
List<Message> newlyArrivedFromOthers = new ArrayList<>();
if (remote.messages != null) {
String myId = prefs.getUserId();
for (Message m : remote.messages) {
if (m != null && m.id != null) {
boolean wasKnown = !knownMessageIds.add(m.id);
if (!wasKnown && !wasInitialSync && m.userId != null &&
!m.userId.equals(myId)) {
newlyArrivedFromOthers.add(m);
}
}
}
}
RoomData merged = mergeRoomData(remote, activeRoomData,
activeRoomCode);

activeRoomData = merged;
if (merged.meta != null && merged.meta.name != null &&
!merged.meta.name.isEmpty()) {
prefs.saveRecentRoom(activeRoomCode, merged.meta.name);
}
if (!newlyArrivedFromOthers.isEmpty()) {
Message latest = newlyArrivedFromOthers.get(newlyArrivedFromOthers.size() 1);
String rName = (merged.meta != null && merged.meta.name != null) ?
merged.meta.name : ("Room " + activeRoomCode);
triggerLocalMessageNotification(activeRoomCode, rName, latest);
}
mainHandler.post(this::notifyRoomStateUpdated);
}
} catch (Exception e) {
Log.e(TAG, "Failed parsing room JSON", e);
}
}
}
/**
* Fix #1 & F1: Fires a real Android notification when notifications are enabled and chat is not
actively visible.
*/

private void triggerLocalMessageNotification(String roomCode, String roomName, Message
msg) {
if (!prefs.isNotificationsEnabled() || chatActivelyVisible) return;
if (Build.VERSION.SDK_INT >= 33) {
if (ContextCompat.checkSelfPermission(appContext,
Manifest.permission.POST_NOTIFICATIONS)
!= PackageManager.PERMISSION_GRANTED) {
return;
}
}
try {
Intent intent = new Intent(appContext, RoomActivity.class);
intent.putExtra("room_code", roomCode);
intent.putExtra("room_name", roomName);
intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
Intent.FLAG_ACTIVITY_SINGLE_TOP);
PendingIntent pendingIntent = PendingIntent.getActivity(
appContext,
roomCode.hashCode(),
intent,
PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
);
String author = (msg.author != null && !msg.author.isEmpty()) ? msg.author : ";"مستخدم

String body;
if ("voice".equals(msg.type)) {
    body = "\uD83C\uDF99 أرسل رسالة صوتية";
} else if ("file".equals(msg.type)) {
    body = "\uD83D\uDCCE أرسل مرفقًا: " + (msg.text != null ? msg.text : "");
} else {
    body = msg.text != null ? msg.text : "رسالة جديدة";
}
NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext,
RunApplication.CHANNEL_ID_MESSAGES)
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
* Fix #9: Protects `out.meta` against NullPointerException if both remote.meta and local.meta
are null.

*/
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
out.meta.name = "Room " + code;
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
}
msgMap.put(m.id, m);
}
}
}
List<Message> mergedMsgs = new ArrayList<>(msgMap.values());
Collections.sort(mergedMsgs, Comparator.comparing(m -> m.createdAt != null ?
m.createdAt : ""));
if (mergedMsgs.size() > 600) {
mergedMsgs = new ArrayList<>(mergedMsgs.subList(mergedMsgs.size() - 600,
mergedMsgs.size()));
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
mergedTasks = new ArrayList<>(mergedTasks.subList(mergedTasks.size() - 300,
mergedTasks.size()));
}
out.tasks = mergedTasks;
// Merge Avatars
out.avatars = new HashMap<>();
if (local != null && local.avatars != null) out.avatars.putAll(local.avatars);
if (remote != null && remote.avatars != null) out.avatars.putAll(remote.avatars);

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
