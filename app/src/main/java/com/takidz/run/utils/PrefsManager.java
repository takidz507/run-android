package com.takidz.run.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.takidz.run.models.Message;
import com.takidz.run.models.OnlineUser;
import com.takidz.run.models.RecentRoom;
import com.takidz.run.models.RoomData;
import com.takidz.run.models.SavedMessage;
import com.takidz.run.models.SignalMessage;
import com.takidz.run.models.TransferBundle;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Preserves 100% of v2.0.0-pro SharedPreferences keys in "run_native_prefs_v1"
 * so existing users upgrading to v3.0.0 never lose rooms, messages, profile, or settings.
 * Adds v3.0.0 support for:
 * - #14 / #22: 4 switchable themes (warm_paper, warm_paper_dark, clean_air, deep_night)
 * - #17: Per-room identity & room ownership persistence
 * - #18: First-use gesture hint tracking
 * - #20: Light sound & vibration touches toggle
 * - #21: Lite mode preference (auto / on / off)
 * - #23: Deferred deep-link first-launch check
 * - #24: Hashed per-device serial (ANDROID_ID SHA-256) so clearing data + renaming still counts as 1 user
 * - #25 & #30: Account transfer & Web-App pairing bundle export/import
 * - #27: Timed per-room mute (1h, 8h, until tomorrow) with automatic expiry
 * - #28: Full Arabic mode (UI language en/ar)
 * - #31: Personal saved messages ("My saved") across all rooms
 */
public class PrefsManager {
    private static final String PREFS_NAME = "run_native_prefs_v1";

    // Preserved v2.0.0-pro keys (NEVER rename or wipe)
    private static final String KEY_USER_ID = "run_user_id";
    private static final String KEY_USER_NAME = "run_user_name";
    private static final String KEY_USER_BIO = "run_user_bio";
    private static final String KEY_USER_AVATAR = "run_user_avatar_url";
    private static final String KEY_THEME_MODE = "run_theme_mode";
    private static final String KEY_PREF_LANG = "run_pref_lang"; // Target translation lang
    private static final String KEY_AUTO_TRANSLATE = "run_auto_translate";
    private static final String KEY_NOTIFICATIONS = "run_notifications_enabled";
    private static final String KEY_RECENT_ROOMS = "run_recent_rooms_v1";
    private static final String KEY_FRIENDS_LIST = "run_friends_list_v1";
    private static final String KEY_PENDING_REQUESTS = "run_pending_requests_v1";
    private static final String KEY_SENT_REQUESTS = "run_sent_requests_v1";
    private static final String KEY_PENDING_INVITES = "run_pending_invites_v1";

    // New v3.0.0 keys
    private static final String KEY_V3_MIGRATED = "run_v3_migrated";
    private static final String KEY_UI_LANG = "run_ui_lang_v3"; // "en" (default) | "ar"
    private static final String KEY_TOUCH_FEEDBACK = "run_touch_feedback_v3"; // #20
    private static final String KEY_LITE_MODE = "run_lite_mode_v3"; // #21: "auto" | "on" | "off"
    private static final String KEY_SEEN_GESTURE_HINT = "run_seen_gesture_hint_v3"; // #18
    private static final String KEY_SEEN_MEDIA_HINT = "run_seen_media_hint_v3"; // #18
    private static final String KEY_SAVED_MESSAGES = "run_saved_messages_v3"; // #31
    private static final String KEY_MUTED_ROOMS = "run_muted_rooms_v3"; // #27: Map<roomCode, untilEpochMs>
    private static final String KEY_OWNED_ROOMS = "run_owned_rooms_v3"; // #17: Set<roomCode>
    private static final String KEY_CACHED_ROOM_PREFIX = "run_room_cache_v3_"; // Local room cache for offline/upgrade continuity
    private static final String KEY_DEFERRED_INVITE_CHECKED = "run_deferred_invite_checked_v3"; // #23
    private static final String KEY_PAIRED_ACCOUNT_ID = "run_paired_account_id_v3"; // #25 / #30

    private final Context appContext;
    private final SharedPreferences prefs;
    private final Gson gson = RoomCryptoUtils.createProtocolGson();

    public PrefsManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        migrateIfNeeded();
    }

    private void migrateIfNeeded() {
        if (prefs.getBoolean(KEY_V3_MIGRATED, false)) return;
        SharedPreferences.Editor editor = prefs.edit();
        String existingTheme = prefs.getString(KEY_THEME_MODE, null);
        if (existingTheme != null) {
            // Smoothly map legacy v2 theme names to the 4 approved v3 themes
            if ("light".equalsIgnoreCase(existingTheme)) {
                editor.putString(KEY_THEME_MODE, ThemeHelper.THEME_WARM_PAPER);
            } else if ("amoled".equalsIgnoreCase(existingTheme)) {
                editor.putString(KEY_THEME_MODE, ThemeHelper.THEME_DEEP_NIGHT);
            } else if ("dark".equalsIgnoreCase(existingTheme)) {
                editor.putString(KEY_THEME_MODE, ThemeHelper.THEME_WARM_PAPER);
            }
        } else {
            editor.putString(KEY_THEME_MODE, ThemeHelper.THEME_WARM_PAPER);
        }
        editor.putBoolean(KEY_V3_MIGRATED, true);
        editor.apply();
    }

    /**
     * #24: Hardware/OS-anchored per-device serial hash.
     * Uses Android's ANDROID_ID (scoped to app signing key & user, survives app data clear).
     * Always SHA-256 hashed before leaving the device, never shown in UI.
     */
    @SuppressLint("HardwareIds")
    public String getDeviceIdentityHash() {
        String androidId = null;
        try {
            androidId = Settings.Secure.getString(appContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception ignored) {}
        if (androidId == null || androidId.trim().isEmpty() || "9774d56d682e549c".equalsIgnoreCase(androidId)) {
            // Fallback only if OS returns null
            String fallback = prefs.getString("run_device_fallback_seed", null);
            if (fallback == null) {
                fallback = UUID.randomUUID().toString();
                prefs.edit().putString("run_device_fallback_seed", fallback).apply();
            }
            androidId = fallback;
        }
        String hash = RoomCryptoUtils.sha256Hex("run-device-serial-v3::" + androidId.trim().toLowerCase(Locale.US));
        return "d-" + (hash.length() >= 16 ? hash.substring(0, 16) : hash);
    }

    /**
     * #24, #25, #30: Returns the user's stable account/device ID.
     * - If the user upgraded from v2.0.0-pro or paired with Web/another phone (#25/#30), keeps that ID.
     * - If app data was cleared on the same phone (#24), derives the ID deterministically from ANDROID_ID
     *   so the phone overwrites its own MQTT presence topic and still counts as ONE user, not two!
     */
    @SuppressLint("HardwareIds")
    public String getUserId() {
        String pairedId = prefs.getString(KEY_PAIRED_ACCOUNT_ID, null);
        if (pairedId != null && !pairedId.trim().isEmpty()) {
            return pairedId.trim();
        }
        String id = prefs.getString(KEY_USER_ID, null);
        if (id == null || id.isEmpty()) {
            String androidId = null;
            try {
                androidId = Settings.Secure.getString(appContext.getContentResolver(), Settings.Secure.ANDROID_ID);
            } catch (Exception ignored) {}
            if (androidId != null && !androidId.trim().isEmpty()) {
                String detHash = RoomCryptoUtils.sha256Hex("run-uid-v3::" + androidId.trim().toLowerCase(Locale.US));
                id = "u-" + (detHash.length() >= 12 ? detHash.substring(0, 12) : detHash);
            } else {
                id = "u-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            }
            prefs.edit().putString(KEY_USER_ID, id).apply();
        }
        return id;
    }

    public void setUserId(String newUserId) {
        if (newUserId == null || newUserId.trim().isEmpty()) return;
        String clean = newUserId.trim();
        prefs.edit()
                .putString(KEY_USER_ID, clean)
                .putString(KEY_PAIRED_ACCOUNT_ID, clean)
                .apply();
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

    // #14 & #22: Theme Mode ("warm_paper" | "warm_paper_dark" | "clean_air" | "deep_night")
    public String getThemeMode() {
        String raw = prefs.getString(KEY_THEME_MODE, ThemeHelper.THEME_WARM_PAPER);
        return ThemeHelper.normalizeThemeMode(raw);
    }

    public void setThemeMode(String mode) {
        prefs.edit().putString(KEY_THEME_MODE, ThemeHelper.normalizeThemeMode(mode)).apply();
    }

    // #28: UI Language ("en" default | "ar" full RTL Arabic mode)
    public String getUiLanguage() {
        String lang = prefs.getString(KEY_UI_LANG, "en");
        return "ar".equalsIgnoreCase(lang) ? "ar" : "en";
    }

    public boolean isArabic() {
        return "ar".equalsIgnoreCase(getUiLanguage());
    }

    public void setUiLanguage(String lang) {
        String normalized = "ar".equalsIgnoreCase(lang) ? "ar" : "en";
        prefs.edit().putString(KEY_UI_LANG, normalized).apply();
    }

    // Translation target language
    public String getPreferredLanguage() {
        return prefs.getString(KEY_PREF_LANG, "ar");
    }

    public void setPreferredLanguage(String lang) {
        prefs.edit().putString(KEY_PREF_LANG, lang != null ? lang : "ar").apply();
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

    // #20: Light sound & vibration touches
    public boolean isTouchFeedbackEnabled() {
        return prefs.getBoolean(KEY_TOUCH_FEEDBACK, true);
    }

    public void setTouchFeedbackEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TOUCH_FEEDBACK, enabled).apply();
    }

    // #21: Lite mode for weak devices ("auto" | "on" | "off")
    public String getLiteModePref() {
        return prefs.getString(KEY_LITE_MODE, "auto");
    }

    public void setLiteModePref(String mode) {
        if (!"on".equalsIgnoreCase(mode) && !"off".equalsIgnoreCase(mode)) {
            mode = "auto";
        }
        prefs.edit().putString(KEY_LITE_MODE, mode.toLowerCase(Locale.US)).apply();
    }

    public boolean isLiteModeEnabled() {
        String pref = getLiteModePref();
        if ("on".equalsIgnoreCase(pref)) return true;
        if ("off".equalsIgnoreCase(pref)) return false;
        return DeviceCapabilityHelper.isWeakOrPowerSaverDevice(appContext);
    }

    // #18: First-use gesture hints
    public boolean hasSeenGestureHint() {
        return prefs.getBoolean(KEY_SEEN_GESTURE_HINT, false);
    }

    public void setSeenGestureHint(boolean seen) {
        prefs.edit().putBoolean(KEY_SEEN_GESTURE_HINT, seen).apply();
    }

    public boolean hasSeenMediaSwipeHint() {
        return prefs.getBoolean(KEY_SEEN_MEDIA_HINT, false);
    }

    public void setSeenMediaSwipeHint(boolean seen) {
        prefs.edit().putBoolean(KEY_SEEN_MEDIA_HINT, seen).apply();
    }

    // #23: Deferred invite first-launch check
    public boolean isDeferredInviteChecked() {
        return prefs.getBoolean(KEY_DEFERRED_INVITE_CHECKED, false);
    }

    public void setDeferredInviteChecked(boolean checked) {
        prefs.edit().putBoolean(KEY_DEFERRED_INVITE_CHECKED, checked).apply();
    }

    // ==========================================
    // Recent Rooms, Archive & Local Room Cache
    // ==========================================

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
        saveRecentRoomDetailed(code, name, null, null, null, null);
    }

    public void saveRecentRoomDetailed(String code, String name, String lastPreview, String accentColor, String bgStyle, String ownerId) {
        if (code == null || code.isEmpty()) return;
        String cleanCode = code.trim().toUpperCase(Locale.US);
        List<RecentRoom> current = getRecentRooms();
        List<RecentRoom> updated = new ArrayList<>();
        String displayName = (name == null || name.trim().isEmpty()) ? ("Room " + cleanCode) : name.trim();
        RecentRoom entry = new RecentRoom(cleanCode, displayName, RoomCryptoUtils.nowIso());

        // Preserve existing metadata if not overridden
        for (RecentRoom r : current) {
            if (r != null && r.code != null && r.code.equalsIgnoreCase(cleanCode)) {
                entry.joinedAt = r.joinedAt != null ? r.joinedAt : entry.joinedAt;
                entry.archived = r.archived;
                entry.lastMessagePreview = (lastPreview != null) ? lastPreview : r.lastMessagePreview;
                entry.accentColor = (accentColor != null) ? accentColor : r.accentColor;
                entry.bgStyle = (bgStyle != null) ? bgStyle : r.bgStyle;
                entry.ownerId = (ownerId != null) ? ownerId : r.ownerId;
                break;
            }
        }
        if (lastPreview != null) entry.lastMessagePreview = lastPreview;
        if (accentColor != null) entry.accentColor = accentColor;
        if (bgStyle != null) entry.bgStyle = bgStyle;
        if (ownerId != null) entry.ownerId = ownerId;

        updated.add(entry);
        for (RecentRoom r : current) {
            if (r != null && r.code != null && !r.code.equalsIgnoreCase(cleanCode)) {
                updated.add(r);
            }
            if (updated.size() >= 24) break;
        }
        prefs.edit().putString(KEY_RECENT_ROOMS, gson.toJson(updated)).apply();
    }

    public void setRoomArchived(String code, boolean archived) {
        if (code == null) return;
        List<RecentRoom> current = getRecentRooms();
        for (RecentRoom r : current) {
            if (r != null && r.code != null && r.code.equalsIgnoreCase(code)) {
                r.archived = archived;
            }
        }
        prefs.edit().putString(KEY_RECENT_ROOMS, gson.toJson(current)).apply();
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
        prefs.edit()
                .putString(KEY_RECENT_ROOMS, gson.toJson(filtered))
                .remove(KEY_CACHED_ROOM_PREFIX + code.trim().toUpperCase(Locale.US))
                .apply();
    }

    public void saveCachedRoomData(String code, RoomData roomData) {
        if (code == null || roomData == null) return;
        try {
            String clean = code.trim().toUpperCase(Locale.US);
            prefs.edit().putString(KEY_CACHED_ROOM_PREFIX + clean, gson.toJson(roomData)).apply();
        } catch (Exception ignored) {}
    }

    public RoomData getCachedRoomData(String code) {
        if (code == null) return null;
        try {
            String clean = code.trim().toUpperCase(Locale.US);
            String json = prefs.getString(KEY_CACHED_ROOM_PREFIX + clean, null);
            if (json != null && !json.isEmpty()) {
                return gson.fromJson(json, RoomData.class);
            }
        } catch (Exception ignored) {}
        return null;
    }

    // #17: Room Ownership tracking
    public void markRoomOwned(String roomCode) {
        if (roomCode == null) return;
        Set<String> set = new HashSet<>(prefs.getStringSet(KEY_OWNED_ROOMS, new HashSet<>()));
        set.add(roomCode.trim().toUpperCase(Locale.US));
        prefs.edit().putStringSet(KEY_OWNED_ROOMS, set).apply();
    }

    public boolean isRoomOwner(String roomCode, RoomData roomData) {
        if (roomCode == null) return false;
        String clean = roomCode.trim().toUpperCase(Locale.US);
        Set<String> owned = prefs.getStringSet(KEY_OWNED_ROOMS, new HashSet<>());
        if (owned.contains(clean)) return true;
        String myUserId = getUserId();
        String myDevHash = getDeviceIdentityHash();
        if (roomData != null) {
            if (roomData.identity != null && roomData.identity.ownerId != null && !roomData.identity.ownerId.isEmpty()) {
                return roomData.identity.ownerId.equals(myUserId) || roomData.identity.ownerId.equals(myDevHash);
            }
            if (roomData.meta != null && roomData.meta.ownerId != null && !roomData.meta.ownerId.isEmpty()) {
                return roomData.meta.ownerId.equals(myUserId) || roomData.meta.ownerId.equals(myDevHash);
            }
            // If legacy room has no owner recorded yet, allow setting identity
            return true;
        }
        return false;
    }

    // ==========================================
    // #27: Timed Room Mute (1h / 8h / Until Tomorrow)
    // ==========================================

    public Map<String, Long> getMutedRoomsMap() {
        String json = prefs.getString(KEY_MUTED_ROOMS, null);
        if (json == null || json.isEmpty()) return new HashMap<>();
        try {
            Type mapType = new TypeToken<HashMap<String, Long>>() {}.getType();
            Map<String, Long> map = gson.fromJson(json, mapType);
            if (map == null) return new HashMap<>();
            long now = System.currentTimeMillis();
            boolean changed = map.entrySet().removeIf(e -> e.getValue() == null || e.getValue() <= now);
            if (changed) {
                prefs.edit().putString(KEY_MUTED_ROOMS, gson.toJson(map)).apply();
            }
            return map;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public void setRoomMutedForDurationMs(String roomCode, long durationMs) {
        if (roomCode == null) return;
        setRoomMutedUntil(roomCode, System.currentTimeMillis() + durationMs);
    }

    public void setRoomMutedUntilTomorrow(String roomCode) {
        if (roomCode == null) return;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, 8);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long target = cal.getTimeInMillis();
        if (target - System.currentTimeMillis() < 2 * 3600_000L) {
            target = System.currentTimeMillis() + 24 * 3600_000L;
        }
        setRoomMutedUntil(roomCode, target);
    }

    public void setRoomMutedUntil(String roomCode, long untilEpochMs) {
        if (roomCode == null) return;
        String clean = roomCode.trim().toUpperCase(Locale.US);
        Map<String, Long> map = getMutedRoomsMap();
        if (untilEpochMs <= System.currentTimeMillis()) {
            map.remove(clean);
        } else {
            map.put(clean, untilEpochMs);
        }
        prefs.edit().putString(KEY_MUTED_ROOMS, gson.toJson(map)).apply();
    }

    public void clearRoomMute(String roomCode) {
        setRoomMutedUntil(roomCode, 0L);
    }

    public long getRoomMutedUntil(String roomCode) {
        if (roomCode == null) return 0L;
        String clean = roomCode.trim().toUpperCase(Locale.US);
        Map<String, Long> map = getMutedRoomsMap();
        Long until = map.get(clean);
        if (until == null) return 0L;
        if (until <= System.currentTimeMillis()) {
            clearRoomMute(clean);
            return 0L;
        }
        return until;
    }

    public boolean isRoomMuted(String roomCode) {
        return getRoomMutedUntil(roomCode) > System.currentTimeMillis();
    }

    public String getRoomMuteRemainingLabel(String roomCode) {
        long until = getRoomMutedUntil(roomCode);
        if (until <= 0L) return "";
        long diffMs = Math.max(0L, until - System.currentTimeMillis());
        long totalMin = Math.max(1L, diffMs / 60_000L);
        long hours = totalMin / 60L;
        long mins = totalMin % 60L;
        boolean ar = isArabic();
        if (hours > 0) {
            return ar
                    ? String.format(Locale.getDefault(), "صامت · %d س %d د", hours, mins)
                    : String.format(Locale.US, "Muted · %dh %dm", hours, mins);
        } else {
            return ar
                    ? String.format(Locale.getDefault(), "صامت · %d د", mins)
                    : String.format(Locale.US, "Muted · %dm", mins);
        }
    }

    // ==========================================
    // #31: Personal Saved Messages ("My saved")
    // ==========================================

    public List<SavedMessage> getSavedMessages() {
        String json = prefs.getString(KEY_SAVED_MESSAGES, null);
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type listType = new TypeToken<ArrayList<SavedMessage>>() {}.getType();
            List<SavedMessage> list = gson.fromJson(json, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public boolean isMessageSaved(String roomCode, String messageId) {
        if (roomCode == null || messageId == null) return false;
        String targetId = roomCode.trim().toUpperCase(Locale.US) + "::" + messageId;
        for (SavedMessage sm : getSavedMessages()) {
            if (sm != null && targetId.equals(sm.id)) return true;
        }
        return false;
    }

    /**
     * Toggles saving a message privately into "My saved". Returns true if now saved, false if removed.
     */
    public boolean toggleSaveMessage(String roomCode, String roomName, Message msg) {
        if (roomCode == null || msg == null || msg.id == null) return false;
        String cleanCode = roomCode.trim().toUpperCase(Locale.US);
        String targetId = cleanCode + "::" + msg.id;
        List<SavedMessage> list = getSavedMessages();
        boolean removed = list.removeIf(s -> s != null && targetId.equals(s.id));
        if (removed) {
            prefs.edit().putString(KEY_SAVED_MESSAGES, gson.toJson(list)).apply();
            return false;
        } else {
            SavedMessage item = new SavedMessage(msg.id, cleanCode, roomName, msg);
            list.add(0, item);
            if (list.size() > 300) {
                list = new ArrayList<>(list.subList(0, 300));
            }
            prefs.edit().putString(KEY_SAVED_MESSAGES, gson.toJson(list)).apply();
            return true;
        }
    }

    public void removeSavedMessage(String savedId) {
        if (savedId == null) return;
        List<SavedMessage> list = getSavedMessages();
        if (list.removeIf(s -> s != null && savedId.equals(s.id))) {
            prefs.edit().putString(KEY_SAVED_MESSAGES, gson.toJson(list)).apply();
        }
    }

    // ==========================================
    // #25 & #30: Account Transfer, Pairing & Backup
    // ==========================================

    public TransferBundle createTransferBundle() {
        TransferBundle b = new TransferBundle();
        b.userId = getUserId();
        b.deviceHash = getDeviceIdentityHash();
        b.userName = getUserName();
        b.userBio = getUserBio();
        b.userAvatarUrl = getUserAvatarUrl();
        b.themeMode = getThemeMode();
        b.uiLang = getUiLanguage();
        b.preferredLanguage = getPreferredLanguage();
        b.autoTranslate = isAutoTranslateEnabled();
        b.notificationsEnabled = isNotificationsEnabled();
        b.touchFeedbackEnabled = isTouchFeedbackEnabled();
        b.liteModePref = getLiteModePref();
        b.recentRooms = getRecentRooms();
        b.friends = getFriends();
        b.savedMessages = getSavedMessages();
        b.mutedRooms = getMutedRoomsMap();
        b.exportedAt = System.currentTimeMillis();
        return b;
    }

    public String exportBackupJson() {
        return gson.toJson(createTransferBundle());
    }

    /**
     * Imports a TransferBundle (from #25 phone-to-phone code, #30 Web-App pairing, or JSON backup).
     * Also parses Web localStorage export format seamlessly!
     */
    public boolean importTransferOrBackupJson(String rawJson, boolean linkAccountIdentity) {
        if (rawJson == null || rawJson.trim().isEmpty()) return false;
        try {
            JsonObject obj = JsonParser.parseString(rawJson.trim()).getAsJsonObject();

            // Support both TransferBundle and Web v2 Pro backup JSON formats
            String importedUserId = getJsonStr(obj, "userId", getJsonStr(obj, "run_user_id", null));
            String importedName = getJsonStr(obj, "userName", getJsonStr(obj, "run_display_name", getJsonStr(obj, "name", null)));
            String importedBio = getJsonStr(obj, "userBio", getJsonStr(obj, "run_bio", getJsonStr(obj, "bio", null)));
            String importedAvatar = getJsonStr(obj, "userAvatarUrl", getJsonStr(obj, "run_avatar", getJsonStr(obj, "avatar", null)));
            String importedTheme = getJsonStr(obj, "themeMode", getJsonStr(obj, "run_theme", null));
            String importedUiLang = getJsonStr(obj, "uiLang", getJsonStr(obj, "run_ui_lang", null));
            String importedPrefLang = getJsonStr(obj, "preferredLanguage", getJsonStr(obj, "run_lang", null));

            if (linkAccountIdentity && importedUserId != null && !importedUserId.isEmpty()) {
                setUserId(importedUserId);
            }
            if (importedName != null && !importedName.isEmpty()) setUserName(importedName);
            if (importedBio != null) setUserBio(importedBio);
            if (importedAvatar != null) setUserAvatarUrl(importedAvatar);
            if (importedTheme != null) setThemeMode(importedTheme);
            if (importedUiLang != null) setUiLanguage(importedUiLang);
            if (importedPrefLang != null) setPreferredLanguage(importedPrefLang);

            TransferBundle parsed = gson.fromJson(rawJson, TransferBundle.class);
            if (parsed != null) {
                if (parsed.recentRooms != null && !parsed.recentRooms.isEmpty()) {
                    // Merge imported rooms with local rooms (no data loss!)
                    List<RecentRoom> mergedRooms = new ArrayList<>(parsed.recentRooms);
                    for (RecentRoom local : getRecentRooms()) {
                        boolean exists = false;
                        for (RecentRoom r : mergedRooms) {
                            if (r.code != null && r.code.equalsIgnoreCase(local.code)) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) mergedRooms.add(local);
                    }
                    prefs.edit().putString(KEY_RECENT_ROOMS, gson.toJson(mergedRooms)).apply();
                } else if (obj.has("run_recent_rooms_v1")) {
                    // Web localStorage format
                    JsonElement roomsEl = obj.get("run_recent_rooms_v1");
                    JsonArray arr = roomsEl.isJsonArray() ? roomsEl.getAsJsonArray()
                            : JsonParser.parseString(roomsEl.getAsString()).getAsJsonArray();
                    for (JsonElement el : arr) {
                        if (el.isJsonObject()) {
                            JsonObject rObj = el.getAsJsonObject();
                            String c = getJsonStr(rObj, "code", "");
                            String n = getJsonStr(rObj, "name", "Room " + c);
                            if (!c.isEmpty()) saveRecentRoom(c, n);
                        }
                    }
                }

                if (parsed.friends != null && !parsed.friends.isEmpty()) {
                    for (OnlineUser f : parsed.friends) {
                        if (f != null && f.userId != null) {
                            addFriend(f.userId, f.name, f.avatarUrl);
                        }
                    }
                }

                if (parsed.savedMessages != null && !parsed.savedMessages.isEmpty()) {
                    List<SavedMessage> currentSaved = getSavedMessages();
                    Map<String, SavedMessage> map = new HashMap<>();
                    for (SavedMessage s : currentSaved) if (s != null && s.id != null) map.put(s.id, s);
                    for (SavedMessage s : parsed.savedMessages) if (s != null && s.id != null) map.put(s.id, s);
                    List<SavedMessage> mergedSaved = new ArrayList<>(map.values());
                    mergedSaved.sort((a, b) -> Long.compare(b.savedAtTs, a.savedAtTs));
                    prefs.edit().putString(KEY_SAVED_MESSAGES, gson.toJson(mergedSaved)).apply();
                }

                if (parsed.mutedRooms != null && !parsed.mutedRooms.isEmpty()) {
                    for (Map.Entry<String, Long> e : parsed.mutedRooms.entrySet()) {
                        if (e.getKey() != null && e.getValue() != null) {
                            setRoomMutedUntil(e.getKey(), e.getValue());
                        }
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String getJsonStr(JsonObject obj, String key, String fallback) {
        if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsString();
            } catch (Exception ignored) {}
        }
        return fallback;
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
