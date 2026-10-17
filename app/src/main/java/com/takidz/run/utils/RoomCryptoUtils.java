app/src/main/java/com/takidz/run/utils/RoomCrypt
oUtils.java
package com.takidz.run.utils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
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
SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
Locale.US);
sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
return sdf.format(new Date());
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
/**
* Matches web password verifier: first 12 chars of SHA256(seed + "::pw::" + password)

*/
public static String computePasswordVerifier(String seed, String password) {
String hash = sha256Hex(seed + "::pw::" + password);
return hash.length() >= 12 ? hash.substring(0, 12) : hash;
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
}
