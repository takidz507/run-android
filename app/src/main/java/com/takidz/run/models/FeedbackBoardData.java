package com.takidz.run.models;

import java.util.ArrayList;
import java.util.List;

public class FeedbackBoardData {
    public String kind = "run-feedback-board";
    public int version = 1;
    public List<FeedbackPost> posts = new ArrayList<>();
}
