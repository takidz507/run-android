package com.takidz.run.network;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Handles file & voice uploads matching the web app logic:
 * - Files <= 350 KB (358,400 bytes) are encoded directly as a DataURL (data:<mime>;base64,...).
 * - Larger files (up to 200 MB) are uploaded anonymously to https://catbox.moe/user/api.php.
 */
public class CatboxUploader {
    private static final String CATBOX_API_URL = "https://catbox.moe/user/api.php";
    private static final long INLINE_DATA_URL_MAX_BYTES = 350 * 1024; // 350 KB

    private final OkHttpClient httpClient;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface UploadCallback {
        void onSuccess(String fileUrlOrDataUrl, String fileName, long fileSize, String mimeType);
        void onError(String errorMessage);
    }

    public CatboxUploader() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public void processAndUploadUri(Context context, Uri uri, UploadCallback callback) {
        executor.execute(() -> {
            try {
                ContentResolver resolver = context.getContentResolver();
                String mime = resolver.getType(uri);
                if (mime == null) mime = "application/octet-stream";

                String fileName = queryFileName(resolver, uri);
                byte[] bytes = readAllBytes(resolver.openInputStream(uri));
                long size = bytes.length;

                if (size <= INLINE_DATA_URL_MAX_BYTES && (mime.startsWith("image/") || mime.startsWith("audio/"))) {
                    String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                    String dataUrl = "data:" + mime + ";base64," + base64;
                    postSuccess(callback, dataUrl, fileName, size, mime);
                } else {
                    String remoteUrl = uploadBytesToCatbox(bytes, fileName, mime);
                    postSuccess(callback, remoteUrl, fileName, size, mime);
                }
            } catch (Exception e) {
                postError(callback, "فشل رفع الملف: " + e.getMessage());
            }
        });
    }

    public void processAndUploadFile(File file, String mimeType, UploadCallback callback) {
        executor.execute(() -> {
            try {
                byte[] bytes = readAllBytes(new FileInputStream(file));
                long size = bytes.length;
                String name = file.getName();
                String mime = mimeType != null ? mimeType : "audio/mp4";

                if (size <= INLINE_DATA_URL_MAX_BYTES) {
                    String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                    String dataUrl = "data:" + mime + ";base64," + base64;
                    postSuccess(callback, dataUrl, name, size, mime);
                } else {
                    String remoteUrl = uploadBytesToCatbox(bytes, name, mime);
                    postSuccess(callback, remoteUrl, name, size, mime);
                }
            } catch (Exception e) {
                postError(callback, "فشل رفع التسجيل الصوتي: " + e.getMessage());
            }
        });
    }

    private String uploadBytesToCatbox(byte[] bytes, String fileName, String mimeType) throws IOException {
        RequestBody fileBody = RequestBody.create(bytes, MediaType.parse(mimeType));
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("reqtype", "fileupload")
                .addFormDataPart("fileToUpload", fileName, fileBody)
                .build();

        Request request = new Request.Builder()
                .url(CATBOX_API_URL)
                .header("User-Agent", "RunAndroidNative/2.0")
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            String body = response.body().string().trim();
            if (!body.startsWith("http")) {
                throw new IOException(body);
            }
            return body;
        }
    }

    private byte[] readAllBytes(InputStream in) throws IOException {
        if (in == null) throw new IOException("Empty stream");
        try (InputStream input = in; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] data = new byte[16384];
            int nRead;
            while ((nRead = input.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        }
    }

    private String queryFileName(ContentResolver resolver, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            } catch (Exception ignored) {}
        }
        if (result == null) {
            result = uri.getLastPathSegment();
            if (result == null) result = "file_" + System.currentTimeMillis();
        }
        return result;
    }

    private void postSuccess(UploadCallback cb, String url, String name, long size, String mime) {
        mainHandler.post(() -> cb.onSuccess(url, name, size, mime));
    }

    private void postError(UploadCallback cb, String err) {
        mainHandler.post(() -> cb.onError(err));
    }
}
