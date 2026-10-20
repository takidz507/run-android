package com.takidz.run.models;

import java.util.ArrayList;
import java.util.List;

public class FeedbackPost {
    public String id;
    public String title = "";
    public String content = "";
    public String category = "Idea"; // Idea | Feature | Bug | Improvement
    public String author = "Guest";
    public String userId = "";
    public String createdAt = "";
    public List<String> upvotes = new ArrayList<>();
    public List<String> hearts = new ArrayList<>();
    public String ownerResponse = null;

    public FeedbackPost() {}

    public int getUpvoteCount() {
        return upvotes != null ? upvotes.size() : 0;
    }

    public int getHeartCount() {
        return hearts != null ? hearts.size() : 0;
    }

    public int getTotalScore() {
        return getUpvoteCount() + (getHeartCount() * 2);
    }

    public boolean hasUpvoted(String uid) {
        return upvotes != null && uid != null && upvotes.contains(uid);
    }

    public boolean hasHearted(String uid) {
        return hearts != null && uid != null && hearts.contains(uid);
    }
}
