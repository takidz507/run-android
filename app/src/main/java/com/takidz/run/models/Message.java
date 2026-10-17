app/src/main/java/com/takidz/run/models/Message.
java
package com.takidz.run.models;
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
// Transient UI state (not serialized to MQTT room JSON)
public transient String translatedText = null;
public transient boolean showingTranslated = false;
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
public String caption;
public int duration; // seconds for voice messages
}
}
