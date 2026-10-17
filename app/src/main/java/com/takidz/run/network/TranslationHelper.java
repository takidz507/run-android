package com.takidz.run.network;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Uses the free Google Translate GTX endpoint exactly like the web app:
 * - Caches translations per (messageId + targetLang)
 * - Display-only: never mutates the underlying Message.text in MQTT state
 */
public class TranslationHelper {
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface TranslationCallback {
        void onTranslated(String translatedText);
        void onError(String error);
    }

    public String getCached(String messageId, String targetLang) {
        return CACHE.get(messageId + "::" + targetLang);
    }

    public void translate(String messageId, String text, String targetLang, TranslationCallback callback) {
        if (text == null || text.trim().isEmpty()) {
            callback.onError("نص فارغ");
            return;
        }
        final String cacheKey = messageId + "::" + targetLang;
        String cached = CACHE.get(cacheKey);
        if (cached != null) {
            callback.onTranslated(cached);
            return;
        }

        executor.execute(() -> {
            try {
                String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
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
                    CACHE.put(cacheKey, result);
                    mainHandler.post(() -> callback.onTranslated(result));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("تعذرت الترجمة: " + e.getMessage()));
            }
        });
    }
}
