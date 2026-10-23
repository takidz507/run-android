package com.takidz.run.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Message {
    public String id;
    public String type = "text"; // "text" | "file" | "voice"
    public String text = "";
    public String author = "Guest";
    public String userId = "";
    public String createdAt = "";
    public ReplyRef replyTo = null;
    public String caption = null;
    public FileAttachment file = null;

    // #18: Emoji reactions (emoji -> list of userIds)
    public Map<String, List<String>> reactions = new HashMap<>();

    // #26: Delivery and Read receipt tracking (userIds/deviceHashes)
    public List<String> deliveredTo = new ArrayList<>();
    public List<String> readBy = new ArrayList<>();

    // Transient UI state (not serialized to MQTT room JSON)
    public transient String translatedText = null;
    public transient boolean showingTranslated = false;
    // 0 = sending, 1 = sent (✓), 2 = delivered (✓✓), 3 = read (accent ✓✓)
    public transient int ackState = 1;

    public boolean isVoiceMessage() {
        return "voice".equalsIgnoreCase(type) || (file != null && file.voice);
    }

    public static class ReplyRef {
        public String id;
        public String text;
        public String author;

        public ReplyRef() {}

        public ReplyRef(String id, String text, String author) {
            this.id = id;
            this.text = text;
            this.author = author;
        }
    }

    public static class FileAttachment {
        public String name;
        public long size;
        public String mime;
        public String url;
        public String dataUrl; // Web v2 Pro inline DataURL field
        public boolean voice = false; // Web v2 Pro voice flag
        public String caption;
        public int duration; // seconds for voice messages

        public String getEffectiveUrl() {
            if (dataUrl != null && !dataUrl.trim().isEmpty()) {
                return dataUrl.trim();
            }
            if (url != null && !url.trim().isEmpty()) {
                return url.trim();
            }
            return "";
        }
    }
}
