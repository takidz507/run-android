package com.takidz.run.models;
import java.util.ArrayList;
import java.util.List;
public class DirectoryData {
public String kind = "run-directory";
public int version = 1;
public List<PublicGroupEntry> groups = new ArrayList<>();
public static class PublicGroupEntry {
public String id;
public String name;
public String key; // 12-char room code (or null/hidden if password-protected)
public boolean hasPassword = false;
public String seed;
public String verifier;
public String createdAt;

}
}
