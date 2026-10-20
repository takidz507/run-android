package com.takidz.run.models;

public class RecentRoom {
    public String code;
    public String name;
    public String joinedAt;
    public String visitedAt;

    public RecentRoom() {}

    public RecentRoom(String code, String name, String joinedAt) {
        this.code = code;
        this.name = name;
        this.joinedAt = joinedAt;
        this.visitedAt = joinedAt;
    }
}
