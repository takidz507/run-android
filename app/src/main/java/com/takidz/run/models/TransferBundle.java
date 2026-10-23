package com.takidz.run.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransferBundle {
    public String kind = "run-account-transfer";
    public int version = 3;
    public String userId = "";
    public String deviceHash = "";
    public String userName = "";
    public String userBio = "";
    public String userAvatarUrl = "";
    public String themeMode = "warm_paper";
    public String uiLang = "en";
    public String preferredLanguage = "ar";
    public boolean autoTranslate = false;
    public boolean notificationsEnabled = true;
    public boolean touchFeedbackEnabled = true;
    public String liteModePref = "auto";
    public List<RecentRoom> recentRooms = new ArrayList<>();
    public List<OnlineUser> friends = new ArrayList<>();
    public List<SavedMessage> savedMessages = new ArrayList<>();
    public Map<String, Long> mutedRooms = new HashMap<>();
    public Map<String, String> ownedRoomSecrets = new HashMap<>();
    public long exportedAt = System.currentTimeMillis();

    public TransferBundle() {}
}
