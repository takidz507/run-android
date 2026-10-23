package com.takidz.run.models;

public class RecentRoom {
    public String code;
    public String name;
    public String joinedAt;
    public String visitedAt;
    public String at; // Web v2 Pro compatibility field
    public String lastMessagePreview = "";
    public String accentColor = null;
    public String bgStyle = null;
    public String ownerId = null;
    public boolean archived = false;
    public int onlineCountHint = 0;

    public RecentRoom() {}

    public RecentRoom(String code, String name, String joinedAt) {
        this.code = code;
        this.name = name;
        this.joinedAt = joinedAt;
        this.visitedAt = joinedAt;
        this.at = joinedAt;
    }

    public String getEffectiveTime() {
        if (visitedAt != null && !visitedAt.isEmpty()) return visitedAt;
        if (at != null && !at.isEmpty()) return at;
        return joinedAt != null ? joinedAt : "";
    }
}
