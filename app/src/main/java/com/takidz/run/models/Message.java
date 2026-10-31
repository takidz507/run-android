package com.takidz.run.models;

import com.takidz.run.utils.AgentHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

    // Optional top-level media fields if an external bridge sends type="voice" or type="file" with top-level url/dataUrl
    public String url = null;
    public String dataUrl = null;
    public String mime = null;
    public String name = null;
    public int duration = 0;

    // #18: Emoji reactions (emoji -> list of userIds)
    public Map<String, List<String>> reactions = new HashMap<>();

    // #26 & v3.3 Agent Receipts: Delivery and Read receipt tracking (userIds/deviceHashes)
    public List<String> deliveredTo = new ArrayList<>();
    public List<String> readBy = new ArrayList<>();

    // Transient UI state (not serialized to MQTT room JSON)
    public transient String translatedText = null;
    public transient boolean showingTranslated = false;
    // 0 = sending, 1 = sent (✓), 2 = delivered (✓✓), 3 = read (accent ✓✓)
    public transient int ackState = 1;

    public boolean isVoiceMessage() {
        if ("voice".equalsIgnoreCase(type)) return true;
        if (file != null && file.voice) return true;
        if (mime != null && mime.toLowerCase(Locale.US).startsWith("audio/")) return true;
        return false;
    }

    /**
     * Returns the effective audio/file URL or DataURL from either msg.file or top-level fields.
     */
    public String getEffectiveMediaUrl() {
        if (file != null) {
            String fUrl = file.getEffectiveUrl();
            if (!fUrl.isEmpty()) return fUrl;
        }
        if (dataUrl != null && !dataUrl.trim().isEmpty()) {
            return dataUrl.trim();
        }
        if (url != null && !url.trim().isEmpty()) {
            return url.trim();
        }
        return "";
    }

    public int getEffectiveVoiceDuration() {
        if (file != null && file.duration > 0) {
            return file.duration;
        }
        return Math.max(0, duration);
    }

    public String getEffectiveFileName() {
        if (file != null && file.name != null && !file.name.trim().isEmpty()) {
            return file.name.trim();
        }
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        String u = getEffectiveMediaUrl();
        if (u.startsWith("http")) {
            int slash = u.lastIndexOf('/');
            if (slash >= 0 && slash < u.length() - 1) {
                String tail = u.substring(slash + 1);
                int q = tail.indexOf('?');
                return q > 0 ? tail.substring(0, q) : tail;
            }
        }
        return "attachment";
    }

    public String getEffectiveMime() {
        if (file != null && file.mime != null && !file.mime.trim().isEmpty()) {
            return file.mime.trim();
        }
        return mime != null ? mime.trim() : "";
    }

    /**
     * Requirement 4: Returns the attached caption for a voice message so it renders directly under the voice player.
     */
    public String getEffectiveVoiceCaption() {
        if (caption != null && !caption.trim().isEmpty()) {
            return caption.trim();
        }
        if (file != null && file.caption != null && !file.caption.trim().isEmpty()) {
            return file.caption.trim();
        }
        if (text != null && !text.trim().isEmpty()) {
            String t = text.trim();
            String lower = t.toLowerCase(Locale.US);
            // Ignore auto-generated default voice placeholders so normal uncaptioned voice notes stay clean
            if (lower.equals("voice message")
                    || lower.startsWith("voice message (")
                    || lower.equals("رسالة صوتية")
                    || lower.startsWith("رسالة صوتية (")
                    || (file != null && file.name != null && t.equalsIgnoreCase(file.name.trim()))) {
                return "";
            }
            return t;
        }
        return "";
    }

    public boolean hasNonVoiceFileOrApk() {
        if (isVoiceMessage()) return false;
        if (!getEffectiveMediaUrl().isEmpty() && ("file".equalsIgnoreCase(type) || "apk".equalsIgnoreCase(type) || file != null)) {
            return true;
        }
        return AgentHelper.extractFirstApkUrl(text) != null;
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
