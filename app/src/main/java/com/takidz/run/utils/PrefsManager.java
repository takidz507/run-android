package com.takidz.run.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.takidz.run.models.RecentRoom;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PrefsManager {
    private static final String PREFS_NAME = "run_native_prefs_v1";
    private static final String KEY_USER_ID = "run_user_id";
    private static final String KEY_USER_NAME = "run_user_name";
    private static final String KEY_USER_BIO = "run_user_bio";
    private static final String KEY_THEME_MODE = "run_theme_mode"; // "dark" | "amoled" | "light"
    private static final String KEY_PREF_LANG = "run_pref_lang"; // e.g. "ar", "en", "fr"
    private static final String KEY_AUTO_TRANSLATE = "run_auto_translate";
    private static final String KEY_NOTIFICATIONS = "run_notifications_enabled";
    private static final String KEY_RECENT_ROOMS = "run_recent_rooms_v1";

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
            if (updated.size() >= 12) break; // Max 12 recent rooms as in web spec
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
}
