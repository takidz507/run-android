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

    public static class Meta {
        public String code = "";
        public String name = "";
        public String createdAt = "";
        public String dirId = null;
    }

    public static class AvatarProfile {
        public String name = "";
        public String avatarUrl = "";
        public String bio = "";

        public AvatarProfile() {}

        public AvatarProfile(String name, String avatarUrl, String bio) {
            this.name = name;
            this.avatarUrl = avatarUrl;
            this.bio = bio;
        }
    }
}
