package com.takidz.run.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.takidz.run.models.OnlineUser;
import com.takidz.run.models.RecentRoom;
import com.takidz.run.models.SignalMessage;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PrefsManager {
    private static final String PREFS_NAME = "run_native_prefs_v1";
    private static final String KEY_USER_ID = "run_user_id";
    private static final String KEY_USER_NAME = "run_user_name";
    private static final String KEY_USER_BIO = "run_user_bio";
    private static final String KEY_USER_AVATAR = "run_user_avatar_url";
    private static final String KEY_THEME_MODE = "run_theme_mode"; // "dark" | "amoled" | "light"
    private static final String KEY_PREF_LANG = "run_pref_lang"; // e.g. "ar", "en", "fr"
    private static final String KEY_AUTO_TRANSLATE = "run_auto_translate";
    private static final String KEY_NOTIFICATIONS = "run_notifications_enabled";
    private static final String KEY_RECENT_ROOMS = "run_recent_rooms_v1";

    // New Friend & Signaling Keys
    private static final String KEY_FRIENDS_LIST = "run_friends_list_v1";
    private static final String KEY_PENDING_REQUESTS = "run_pending_requests_v1";
    private static final String KEY_SENT_REQUESTS = "run_sent_requests_v1";
    private static final String KEY_PENDING_INVITES = "run_pending_invites_v1";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public PrefsManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getUserId() {
        String id = prefs.getString(KEY_USER_ID, null);
        if (id == null || id.isEmpty()) {
            id = "u-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            prefs.edit().putString(KEY_USER_ID, id).apply();
        }
        return id;
    }

    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, "");
    }

    public void setUserName(String name) {
        prefs.edit().putString(KEY_USER_NAME, name != null ? name.trim() : "").apply();
    }

    public String getUserBio() {
        return prefs.getString(KEY_USER_BIO, "");
    }

    public void setUserBio(String bio) {
        prefs.edit().putString(KEY_USER_BIO, bio != null ? bio.trim() : "").apply();
    }

    public String getUserAvatarUrl() {
        return prefs.getString(KEY_USER_AVATAR, "");
    }

    public void setUserAvatarUrl(String avatarUrl) {
        prefs.edit().putString(KEY_USER_AVATAR, avatarUrl != null ? avatarUrl.trim() : "").apply();
    }

    public String getThemeMode() {
        return prefs.getString(KEY_THEME_MODE, "dark");
    }

    public void setThemeMode(String mode) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply();
    }

    public String getPreferredLanguage() {
        return prefs.getString(KEY_PREF_LANG, "ar");
    }

    public void setPreferredLanguage(String lang) {
        prefs.edit().putString(KEY_PREF_LANG, lang).apply();
    }

    public boolean isAutoTranslateEnabled() {
        return prefs.getBoolean(KEY_AUTO_TRANSLATE, false);
    }

    public void setAutoTranslateEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_TRANSLATE, enabled).apply();
    }

    public boolean isNotificationsEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATIONS, true);
    }

    public void setNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply();
    }

    public List<RecentRoom> getRecentRooms() {
        String json = prefs.getString(KEY_RECENT_ROOMS, null);
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type listType = new TypeToken<ArrayList<RecentRoom>>() {}.getType();
            List<RecentRoom> list = gson.fromJson(json, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void saveRecentRoom(String code, String name) {
        if (code == null || code.isEmpty()) return;
        List<RecentRoom> current = getRecentRooms();
        List<RecentRoom> updated = new ArrayList<>();
        String displayName = (name == null || name.trim().isEmpty()) ? ("Room " + code) : name.trim();
        updated.add(new RecentRoom(code, displayName, RoomCryptoUtils.nowIso()));
        for (RecentRoom r : current) {
            if (r != null && r.code != null && !r.code.equalsIgnoreCase(code)) {
                updated.add(r);
            }
            if (updated.size() >= 12) break;
        }
        prefs.edit().putString(KEY_RECENT_ROOMS, gson.toJson(updated)).apply();
    }

    public void removeRecentRoom(String code) {
        if (code == null) return;
        List<RecentRoom> current = getRecentRooms();
        List<RecentRoom> filtered = new ArrayList<>();
        for (RecentRoom r : current) {
            if (r != null && r.code != null && !r.code.equalsIgnoreCase(code)) {
                filtered.add(r);
            }
        }
        prefs.edit().putString(KEY_RECENT_ROOMS, gson.toJson(filtered)).apply();
    }

    // ==========================================
    // Friends & P2P Signaling Support
    // ==========================================

    public List<OnlineUser> getFriends() {
        String json = prefs.getString(KEY_FRIENDS_LIST, null);
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type listType = new TypeToken<ArrayList<OnlineUser>>() {}.getType();
            List<OnlineUser> list = gson.fromJson(json, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public boolean isFriend(String userId) {
        if (userId == null || userId.isEmpty()) return false;
        for (OnlineUser u : getFriends()) {
            if (u.userId != null && u.userId.equals(userId)) return true;
        }
        return false;
    }

    public void addFriend(String userId, String name, String avatarUrl) {
        if (userId == null || userId.isEmpty()) return;
        List<OnlineUser> friends = getFriends();
        friends.removeIf(u -> u.userId != null && u.userId.equals(userId));
        OnlineUser newFriend = new OnlineUser(userId, name, avatarUrl, "", System.currentTimeMillis(), false);
        friends.add(0, newFriend);
        prefs.edit().putString(KEY_FRIENDS_LIST, gson.toJson(friends)).apply();
        removePendingRequest(userId);
        removeSentRequest(userId);
    }

    public void removeFriend(String userId) {
        if (userId == null) return;
        List<OnlineUser> friends = getFriends();
        boolean removed = friends.removeIf(u -> u.userId != null && u.userId.equals(userId));
        if (removed) {
            prefs.edit().putString(KEY_FRIENDS_LIST, gson.toJson(friends)).apply();
        }
    }

    public List<SignalMessage> getPendingRequestsReceived() {
        String json = prefs.getString(KEY_PENDING_REQUESTS, null);
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type listType = new TypeToken<ArrayList<SignalMessage>>() {}.getType();
            List<SignalMessage> list = gson.fromJson(json, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void addPendingRequest(SignalMessage req) {
        if (req == null || req.fromUserId == null) return;
        List<SignalMessage> list = getPendingRequestsReceived();
        list.removeIf(r -> r.fromUserId != null && r.fromUserId.equals(req.fromUserId));
        list.add(0, req);
        prefs.edit().putString(KEY_PENDING_REQUESTS, gson.toJson(list)).apply();
    }

    public void removePendingRequest(String fromUserId) {
        if (fromUserId == null) return;
        List<SignalMessage> list = getPendingRequestsReceived();
        boolean removed = list.removeIf(r -> r.fromUserId != null && r.fromUserId.equals(fromUserId));
        if (removed) {
            prefs.edit().putString(KEY_PENDING_REQUESTS, gson.toJson(list)).apply();
        }
    }

    public Set<String> getSentRequestUserIds() {
        return prefs.getStringSet(KEY_SENT_REQUESTS, new HashSet<>());
    }

    public boolean hasSentRequest(String toUserId) {
        if (toUserId == null) return false;
        return getSentRequestUserIds().contains(toUserId);
    }

    public void addSentRequest(String toUserId) {
        if (toUserId == null) return;
        Set<String> set = new HashSet<>(getSentRequestUserIds());
        set.add(toUserId);
        prefs.edit().putStringSet(KEY_SENT_REQUESTS, set).apply();
    }

    public void removeSentRequest(String toUserId) {
        if (toUserId == null) return;
        Set<String> set = new HashSet<>(getSentRequestUserIds());
        if (set.remove(toUserId)) {
            prefs.edit().putStringSet(KEY_SENT_REQUESTS, set).apply();
        }
    }

    public List<SignalMessage> getPendingRoomInvites() {
        String json = prefs.getString(KEY_PENDING_INVITES, null);
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type listType = new TypeToken<ArrayList<SignalMessage>>() {}.getType();
            List<SignalMessage> list = gson.fromJson(json, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void addRoomInvite(SignalMessage invite) {
        if (invite == null || invite.roomCode == null) return;
        List<SignalMessage> list = getPendingRoomInvites();
        list.removeIf(i -> i.roomCode != null && i.roomCode.equals(invite.roomCode));
        list.add(0, invite);
        prefs.edit().putString(KEY_PENDING_INVITES, gson.toJson(list)).apply();
    }

    public void removeRoomInvite(String inviteId) {
        if (inviteId == null) return;
        List<SignalMessage> list = getPendingRoomInvites();
        boolean removed = list.removeIf(i -> (i.id != null && i.id.equals(inviteId)) || (i.roomCode != null && i.roomCode.equals(inviteId)));
        if (removed) {
            prefs.edit().putString(KEY_PENDING_INVITES, gson.toJson(list)).apply();
        }
    }
}
