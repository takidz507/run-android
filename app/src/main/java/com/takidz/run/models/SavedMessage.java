package com.takidz.run.models;

public class SavedMessage {
    public String id = "";
    public String messageId = "";
    public String roomCode = "";
    public String roomName = "";
    public String author = "Guest";
    public String userId = "";
    public String text = "";
    public String type = "text";
    public String mediaUrl = "";
    public String createdAt = "";
    public long savedAtTs = 0L;

    public SavedMessage() {}

    public SavedMessage(String messageId, String roomCode, String roomName, Message msg) {
        this.messageId = messageId != null ? messageId : "";
        this.roomCode = roomCode != null ? roomCode.toUpperCase() : "";
        this.id = this.roomCode + "::" + this.messageId;
        this.roomName = (roomName != null && !roomName.trim().isEmpty()) ? roomName.trim() : ("Room " + this.roomCode);
        if (msg != null) {
            this.author = (msg.author != null && !msg.author.isEmpty()) ? msg.author : "Guest";
            this.userId = msg.userId != null ? msg.userId : "";
            this.type = msg.isVoiceMessage() ? "voice" : (msg.type != null ? msg.type : "text");
            if (msg.text != null && !msg.text.isEmpty()) {
                this.text = msg.text;
            } else if (msg.caption != null && !msg.caption.isEmpty()) {
                this.text = msg.caption;
            } else if (msg.file != null && msg.file.name != null) {
                this.text = msg.file.name;
            } else {
                this.text = "Saved item";
            }
            if (msg.file != null) {
                this.mediaUrl = msg.file.getEffectiveUrl();
            }
            this.createdAt = msg.createdAt != null ? msg.createdAt : "";
        }
        this.savedAtTs = System.currentTimeMillis();
    }
}
