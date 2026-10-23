package com.takidz.run.models;

import java.util.ArrayList;
import java.util.List;

public class AckPayload {
    public String kind = "run-ack";
    public int version = 1;
    public String fromUserId = "";
    public String fromDeviceHash = "";
    public String status = "delivered"; // "delivered" | "read"
    public List<String> messageIds = new ArrayList<>();
    public long ts = 0L;

    public AckPayload() {}

    public AckPayload(String fromUserId, String fromDeviceHash, String status, List<String> messageIds) {
        this.fromUserId = fromUserId != null ? fromUserId : "";
        this.fromDeviceHash = fromDeviceHash != null ? fromDeviceHash : "";
        this.status = status != null ? status : "delivered";
        if (messageIds != null) {
            this.messageIds.addAll(messageIds);
        }
        this.ts = System.currentTimeMillis();
    }
}
