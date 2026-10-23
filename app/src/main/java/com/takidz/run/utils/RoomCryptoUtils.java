package com.takidz.run.utils;

import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.takidz.run.models.RoomData;
import com.takidz.run.models.SignalMessage;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class RoomCryptoUtils {
    // 32 unambiguous characters (excludes 0, O, 1, I) matching web index.html
    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String randomCode(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(length);
        for (byte b : bytes) {
            int idx = (b & 0xFF) % ALPHABET.length();
            sb.append(ALPHABET.charAt(idx));
        }
        return sb.toString();
    }

    public static String randomCode6() {
        return randomCode(6);
    }

    public static String randomCode12() {
        return randomCode(12);
    }

    public static boolean isValidRoomCode(String code) {
        if (code == null) return false;
        String clean = code.trim().toUpperCase(Locale.US);
        if (clean.length() != 6 && clean.length() != 12) return false;
        for (int i = 0; i < clean.length(); i++) {
            if (ALPHABET.indexOf(clean.charAt(i)) == -1) {
                return false;
            }
        }
        return true;
    }

    public static String makeId(String prefix) {
        String time36 = Long.toString(System.currentTimeMillis(), 36);
        String rand = Long.toString(Math.abs(RANDOM.nextLong()), 36);
        if (rand.length() > 6) rand = rand.substring(0, 6);
        return prefix + "-" + time36 + rand;
    }

    public static String nowIso() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    public static long parseIsoOrLongTimestamp(String raw) {
        if (raw == null || raw.trim().isEmpty()) return System.currentTimeMillis();
        String s = raw.trim();
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {}
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = sdf.parse(s);
            if (d != null) return d.getTime();
        } catch (Exception ignored) {}
        try {
            SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            sdf2.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = sdf2.parse(s);
            if (d != null) return d.getTime();
        } catch (Exception ignored) {}
        return System.currentTimeMillis();
    }

    public static String randomHexSeed(int byteCount) {
        byte[] bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(byteCount * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b & 0xFF));
        }
        return sb.toString();
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format(Locale.US, "%02x", b & 0xFF));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static byte[] sha256Bytes(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new byte[32];
        }
    }

    /**
     * Matches current-web-index-v2pro.html line 831: first 12 chars of SHA256(seed + "::" + password)
     */
    public static String computePasswordVerifier(String seed, String password) {
        String hash = sha256Hex(seed + "::" + password);
        return hash.length() >= 12 ? hash.substring(0, 12) : hash;
    }

    /**
     * Legacy v2.0.0-pro Android verifier fallback: first 12 chars of SHA256(seed + "::pw::" + password)
     */
    public static String computeLegacyPasswordVerifier(String seed, String password) {
        String hash = sha256Hex(seed + "::pw::" + password);
        return hash.length() >= 12 ? hash.substring(0, 12) : hash;
    }

    /**
     * Verifies password against both current Web v2 Pro verifier and legacy Android v2 verifier.
     */
    public static boolean verifyPublicGroupPassword(String seed, String password, String expectedVerifier) {
        if (seed == null || password == null || expectedVerifier == null) return false;
        String webV = computePasswordVerifier(seed, password);
        if (expectedVerifier.equalsIgnoreCase(webV)) return true;
        String legacyV = computeLegacyPasswordVerifier(seed, password);
        return expectedVerifier.equalsIgnoreCase(legacyV);
    }

    /**
     * Matches web groupKeyFromHash(SHA256(seed + "::pw::" + password), 12)
     */
    public static String deriveProtectedGroupTopicKey(String seed, String password) {
        String hashHex = sha256Hex(seed + "::pw::" + password);
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12 && (i * 2 + 2) <= hashHex.length(); i++) {
            int byteVal = Integer.parseInt(hashHex.substring(i * 2, i * 2 + 2), 16);
            sb.append(ALPHABET.charAt(byteVal % ALPHABET.length()));
        }
        return sb.toString();
    }

    /**
     * #25 & #30: Derives MQTT topic suffix from a short pairing/transfer code.
     */
    public static String deriveTransferTopicId(String pairingCode) {
        String clean = (pairingCode != null ? pairingCode.trim().toUpperCase(Locale.US) : "");
        String hash = sha256Hex("runapp-transfer-topic-v3::" + clean);
        return hash.length() >= 16 ? hash.substring(0, 16) : hash;
    }

    /**
     * #25 & #30: Encrypts account transfer JSON with AES-GCM keyed by the short transfer code.
     */
    public static String encryptTransferPayload(String plainJson, String pairingCode) {
        try {
            String clean = pairingCode.trim().toUpperCase(Locale.US);
            byte[] keyBytes = sha256Bytes("runapp-transfer-key-v3::" + clean);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
            byte[] cipherText = cipher.doFinal(plainJson.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return "v3gcm:" + Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * #25 & #30: Decrypts account transfer JSON with AES-GCM keyed by the short transfer code.
     */
    public static String decryptTransferPayload(String encryptedPayload, String pairingCode) {
        if (encryptedPayload == null || pairingCode == null) return null;
        try {
            String trimmed = encryptedPayload.trim();
            if (trimmed.startsWith("{")) {
                // Unencrypted JSON fallback (e.g. direct backup paste)
                return trimmed;
            }
            if (trimmed.startsWith("v3gcm:")) {
                trimmed = trimmed.substring("v3gcm:".length());
            }
            String clean = pairingCode.trim().toUpperCase(Locale.US);
            byte[] keyBytes = sha256Bytes("runapp-transfer-key-v3::" + clean);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            byte[] combined = Base64.decode(trimmed, Base64.NO_WRAP);
            if (combined.length <= 12) return null;
            byte[] iv = new byte[12];
            byte[] cipherText = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Creates a protocol-hardened Gson instance that is 100% interoperable between
     * Web v2 Pro (where room.avatars values are Strings and SignalMessage.timestamp is ISO String)
     * and Android v2.0.0-pro / v3.0.0.
     */
    public static Gson createProtocolGson() {
        GsonBuilder builder = new GsonBuilder();

        // 1. AvatarProfile adapter: reads either String URL (Web v2 Pro) or Object (Android v2 Pro),
        // and serializes as String URL to match current-web-index-v2pro.html wire protocol.
        builder.registerTypeAdapter(RoomData.AvatarProfile.class, new JsonDeserializer<RoomData.AvatarProfile>() {
            @Override
            public RoomData.AvatarProfile deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                if (json == null || json.isJsonNull()) {
                    return new RoomData.AvatarProfile("", "", "");
                }
                if (json.isJsonPrimitive()) {
                    return new RoomData.AvatarProfile("", json.getAsString(), "");
                }
                if (json.isJsonObject()) {
                    JsonObject obj = json.getAsJsonObject();
                    String name = obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString() : "";
                    String url = obj.has("avatarUrl") && !obj.get("avatarUrl").isJsonNull() ? obj.get("avatarUrl").getAsString() : "";
                    String bio = obj.has("bio") && !obj.get("bio").isJsonNull() ? obj.get("bio").getAsString() : "";
                    return new RoomData.AvatarProfile(name, url, bio);
                }
                return new RoomData.AvatarProfile("", "", "");
            }
        });

        builder.registerTypeAdapter(RoomData.AvatarProfile.class, new JsonSerializer<RoomData.AvatarProfile>() {
            @Override
            public JsonElement serialize(RoomData.AvatarProfile src, Type typeOfSrc, JsonSerializationContext context) {
                if (src == null || src.avatarUrl == null) {
                    return new JsonPrimitive("");
                }
                return new JsonPrimitive(src.avatarUrl);
            }
        });

        // 2. SignalMessage adapter: handles timestamp as either long (Android) or ISO-8601 String (Web v2 Pro)
        builder.registerTypeAdapter(SignalMessage.class, new JsonDeserializer<SignalMessage>() {
            @Override
            public SignalMessage deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                if (json == null || !json.isJsonObject()) return null;
                JsonObject obj = json.getAsJsonObject();
                SignalMessage sig = new SignalMessage();
                sig.id = getStr(obj, "id", "");
                sig.type = getStr(obj, "type", "");
                sig.fromUserId = getStr(obj, "fromUserId", "");
                sig.fromUserName = getStr(obj, "fromUserName", "Guest");
                sig.fromAvatarUrl = getStr(obj, "fromAvatarUrl", "");
                sig.toUserId = getStr(obj, "toUserId", "");
                sig.roomCode = getStr(obj, "roomCode", "");
                sig.roomName = getStr(obj, "roomName", "");
                if (obj.has("timestamp") && !obj.get("timestamp").isJsonNull()) {
                    JsonElement tsEl = obj.get("timestamp");
                    if (tsEl.isJsonPrimitive()) {
                        JsonPrimitive prim = tsEl.getAsJsonPrimitive();
                        if (prim.isNumber()) {
                            sig.timestamp = prim.getAsLong();
                        } else {
                            sig.timestamp = parseIsoOrLongTimestamp(prim.getAsString());
                        }
                    }
                } else {
                    sig.timestamp = System.currentTimeMillis();
                }
                return sig;
            }

            private String getStr(JsonObject obj, String key, String fallback) {
                if (obj.has(key) && !obj.get(key).isJsonNull()) {
                    try {
                        return obj.get(key).getAsString();
                    } catch (Exception ignored) {}
                }
                return fallback;
            }
        });

        return builder.create();
    }
}
