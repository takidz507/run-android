package com.takidz.run.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoomData {
    public String kind = "run-room";
    public int version = 1;
    public Meta meta = new Meta();
    public List<Message> messages = new ArrayList<>();
    public List<TaskItem> tasks = new ArrayList<>();
    public Map<String, AvatarProfile> avatars = new HashMap<>();
    public NoteData note = new NoteData();
    public RoomIdentity identity = null;

    public static class Meta {
        public String code = "";
        public String name = "";
        public String createdAt = "";
        public String dirId = null;
        // #17: Per-room identity fields
        public String ownerId = null;
        public String accentColor = null;
        public String bgStyle = null;
        public long identityVersion = 0L;
    }

    public static class RoomIdentity {
        public String kind = "run-room-identity";
        public String roomCode = "";
        public String ownerId = "";
        public String accentColor = "#B8E646";
        public String bgStyle = "paper"; // "paper" | "dots" | "lines" | "grid" | "waves" | "origami"
        public long version = 1L;
        public String updatedAt = "";

        public RoomIdentity() {}

        public RoomIdentity(String roomCode, String ownerId, String accentColor, String bgStyle, long version, String updatedAt) {
            this.roomCode = roomCode;
            this.ownerId = ownerId;
            this.accentColor = accentColor;
            this.bgStyle = bgStyle;
            this.version = version;
            this.updatedAt = updatedAt;
        }
    }

    public static class AvatarProfile {
        public String name = "";
        public String avatarUrl = "";
        public String bio = "";

        public AvatarProfile() {}

        public AvatarProfile(String name, String avatarUrl, String bio) {
            this.name = name != null ? name : "";
            this.avatarUrl = avatarUrl != null ? avatarUrl : "";
            this.bio = bio != null ? bio : "";
        }
    }

    public static class NoteData {
        public String content = "";
        public String lastEditedBy = "";
        public String lastEditedAt = "";
        public long version = 0L;

        public NoteData() {}

        public NoteData(String content, String lastEditedBy, String lastEditedAt, long version) {
            this.content = content;
            this.lastEditedBy = lastEditedBy;
            this.lastEditedAt = lastEditedAt;
            this.version = version;
        }
    }
}
