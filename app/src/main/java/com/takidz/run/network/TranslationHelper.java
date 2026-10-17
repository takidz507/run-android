package com.takidz.run.network;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
/**

* Bounded & deduplicated translation engine:
* - LRU Cache capped at 200 entries (#17).
* - Fixed concurrency pool of 3 threads (#2).
* - In-flight request deduplication (`PENDING_KEYS`) so rapid room state updates never trigger
duplicate HTTP calls (#2).
*/
public class TranslationHelper {
private static final int MAX_CACHE_SIZE = 200;
private static final Map<String, String> LRU_CACHE = Collections.synchronizedMap(
new LinkedHashMap<String, String>(64, 0.75f, true) {
@Override
protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
return size() > MAX_CACHE_SIZE;
}
}
);
private static final Set<String> PENDING_KEYS = ConcurrentHashMap.newKeySet();
private static final ExecutorService TRANSLATION_EXECUTOR =
Executors.newFixedThreadPool(3);
private final OkHttpClient client = new OkHttpClient();
private final Handler mainHandler = new Handler(Looper.getMainLooper());
public interface TranslationCallback {

void onTranslated(String translatedText);
void onError(String error);
}
public String getCached(String messageId, String targetLang) {
return LRU_CACHE.get(messageId + "::" + targetLang);
}
public boolean isPending(String messageId, String targetLang) {
return PENDING_KEYS.contains(messageId + "::" + targetLang);
}
public void translate(String messageId, String text, String targetLang, TranslationCallback
callback) {
if (text == null || text.trim().isEmpty()) {
if (callback != null) callback.onError("نص فارغ");
return;
}
final String cacheKey = messageId + "::" + targetLang;
String cached = LRU_CACHE.get(cacheKey);
if (cached != null) {
if (callback != null) callback.onTranslated(cached);
return;
}
// Prevent duplicate concurrent requests for the same message

if (!PENDING_KEYS.add(cacheKey)) {
return;
}
TRANSLATION_EXECUTOR.execute(() -> {
try {
String url =
"https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
+ Uri.encode(targetLang)
+ "&dt=t&q="
+ Uri.encode(text);
Request req = new Request.Builder()
.url(url)
.header("User-Agent", "Mozilla/5.0")
.build();
try (Response resp = client.newCall(req).execute()) {
if (!resp.isSuccessful() || resp.body() == null) {
throw new Exception("HTTP " + resp.code());
}
String jsonStr = resp.body().string();
JSONArray outer = new JSONArray(jsonStr);
JSONArray sentences = outer.getJSONArray(0);
StringBuilder sb = new StringBuilder();

for (int i = 0; i < sentences.length(); i++) {
JSONArray part = sentences.getJSONArray(i);
if (!part.isNull(0)) {
sb.append(part.getString(0));
}
}
String result = sb.toString().trim();
LRU_CACHE.put(cacheKey, result);
PENDING_KEYS.remove(cacheKey);
if (callback != null) {
mainHandler.post(() -> callback.onTranslated(result));
}
}
} catch (Exception e) {
PENDING_KEYS.remove(cacheKey);
if (callback != null) {
mainHandler.post(() -> callback.onError("تعذرت الترجمة: " + e.getMessage()));
}
}
});
}
}
