package com.takidz.run.models;

public class OnlineUser {
    public String userId = "";
    public String name = "Guest";
    public String avatarUrl = "";
    public String bio = "";
    public long lastSeenTs = 0L;
    public boolean isSelf = false;
    public String platform = "Android";

    public OnlineUser() {}

    public OnlineUser(String userId, String name, String avatarUrl, String bio, long lastSeenTs, boolean isSelf) {
        this.userId = userId;
        this.name = (name != null && !name.trim().isEmpty()) ? name.trim() : "Guest";
        this.avatarUrl = avatarUrl != null ? avatarUrl : "";
        this.bio = bio != null ? bio : "";
        this.lastSeenTs = lastSeenTs;
        this.isSelf = isSelf;
    }
}
