package com.takidz.run.utils;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Run v3.3.0 «Run Agent» Client-Side Helper.
 * Identifies the permanent AI Agent ("موس") in UI surfaces by its userId prefix ("agent-")
 * and known agent IDs without altering the wire protocol.
 */
public final class AgentHelper {

    public static final String AGENT_ID_PREFIX = "agent-";

    public static final String[] KNOWN_AGENT_IDS = new String[]{
            "agent-mous",
            "agent-run",
            "agent-main",
            "agent-bot",
            "agent-1",
            "agent"
    };

    public static final String AGENT_ROOM_TITLE = "Run Agent — موس";
    public static final String AGENT_SHORT_NAME_AR = "موس";
    public static final String AGENT_SHORT_NAME_EN = "Mous";

    // Requirement 7: Quick command chips in the Agent room (insert text into input, do not send immediately)
    public static final String[][] QUICK_COMMANDS = new String[][]{
            {"الحالة", "/حالة"},
            {"كتم ساعة", "/كتم 1h"},
            {"رد بصوتك", "رد بصوتك: "},
            {"طلب إصلاح", "طلب إصلاح: "}
    };

    private static final Pattern APK_URL_PATTERN = Pattern.compile(
            "(https?://[^\\s\"'<>]+\\.apk(?:\\?[^\\s\"'<>]*)?)",
            Pattern.CASE_INSENSITIVE
    );

    private AgentHelper() {}

    /**
     * Requirement 2: Checks whether a userId belongs to the Run Agent ("agent-" prefix or known agent ID list).
     */
    public static boolean isAgentUserId(String userId) {
        if (userId == null) return false;
        String clean = userId.trim().toLowerCase(Locale.US);
        if (clean.isEmpty()) return false;
        if (clean.startsWith(AGENT_ID_PREFIX)) {
            return true;
        }
        for (String known : KNOWN_AGENT_IDS) {
            if (known.equals(clean)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a display name corresponds to the agent ("موس" / "Mous" / "Run Agent").
     */
    public static boolean isAgentDisplayName(String name) {
        if (name == null) return false;
        String clean = name.trim().toLowerCase(Locale.US);
        return clean.equals("موس")
                || clean.equals("mous")
                || clean.contains("run agent")
                || clean.startsWith("agent-");
    }

    /**
     * Requirement 5: Checks if a deliveredTo or readBy list contains an agent userId ("agent-*").
     */
    public static boolean containsAgentId(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return false;
        for (String id : userIds) {
            if (isAgentUserId(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Requirement 8: Extracts a direct .apk link from message text if present so one-tap APK opening works
     * even when the link is sent inside message text.
     */
    public static String extractFirstApkUrl(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = APK_URL_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Checks whether a filename, MIME type, or URL points to an Android APK package.
     */
    public static boolean isApkResource(String fileName, String mime, String url) {
        if (mime != null && mime.toLowerCase(Locale.US).contains("android.package-archive")) {
            return true;
        }
        if (fileName != null && fileName.toLowerCase(Locale.US).endsWith(".apk")) {
            return true;
        }
        if (url != null) {
            String lower = url.toLowerCase(Locale.US);
            int q = lower.indexOf('?');
            if (q > 0) lower = lower.substring(0, q);
            return lower.endsWith(".apk");
        }
        return false;
    }
}
