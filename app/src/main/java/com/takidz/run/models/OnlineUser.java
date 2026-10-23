package com.takidz.run.models;

public class OnlineUser {
    public String userId = "";
    public String deviceHash = ""; // #24: Hashed per-device serial for accurate unique-device counting
    public String name = "Guest";
    public String avatarUrl = "";
    public String bio = "";
    public long lastSeenTs = 0L;
    public boolean isSelf = false;
    public String platform = "Android";

    public OnlineUser() {}

    public OnlineUser(String userId, String name, String avatarUrl, String bio, long lastSeenTs, boolean isSelf) {
        this(userId, "", name, avatarUrl, bio, lastSeenTs, isSelf, "Android");
    }

    public OnlineUser(String userId, String deviceHash, String name, String avatarUrl, String bio, long lastSeenTs, boolean isSelf, String platform) {
        this.userId = userId != null ? userId : "";
        this.deviceHash = deviceHash != null ? deviceHash : "";
        this.name = (name != null && !name.trim().isEmpty()) ? name.trim() : "Guest";
        this.avatarUrl = avatarUrl != null ? avatarUrl : "";
        this.bio = bio != null ? bio : "";
        this.lastSeenTs = lastSeenTs;
        this.isSelf = isSelf;
        this.platform = (platform != null && !platform.isEmpty()) ? platform : "Android";
    }

    public String getDedupeKey() {
        if (deviceHash != null && !deviceHash.trim().isEmpty()) {
            return "dev:" + deviceHash.trim();
        }
        return "uid:" + (userId != null ? userId.trim() : "");
    }
}
