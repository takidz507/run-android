package com.takidz.run.models;

public class SignalMessage {
    public static final String TYPE_FRIEND_REQUEST = "friend_request";
    public static final String TYPE_FRIEND_ACCEPT = "friend_accept";
    public static final String TYPE_FRIEND_DECLINE = "friend_decline";
    public static final String TYPE_ROOM_INVITE = "room_invite";

    public String id = "";
    public String type = "";
    public String fromUserId = "";
    public String fromUserName = "Guest";
    public String fromAvatarUrl = "";
    public String toUserId = "";
    public String roomCode = "";
    public String roomName = "";
    public long timestamp = 0L;

    public SignalMessage() {}
}
