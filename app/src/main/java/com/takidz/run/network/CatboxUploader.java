package com.takidz.run.network;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Base64;

import androidx.annotation.NonNull;

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
import okio.BufferedSink;

/**
 * Handles file & voice uploads matching the web app logic with OOM protection:
 * - Pre-checks file size and rejects files > 200 MB before reading into memory.
 * - Files <= 350 KB (358,400 bytes) are encoded directly as a DataURL (data:<mime>;base64,...).
 * - Larger files (up to 200 MB) are streamed in 16KB chunks to https://catbox.moe/user/api.php without loading the entire file into RAM.
 */
public class CatboxUploader {
    private static final String CATBOX_API_URL = "https://catbox.moe/user/api.php";
    private static final long INLINE_DATA_URL_MAX_BYTES = 350 * 1024L;          // 350 KB
    public static final long MAX_UPLOAD_SIZE_BYTES = 200L * 1024L * 1024L;      // 200 MB limit

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
                .writeTimeout(180, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .build();
    }

    public void processAndUploadUri(Context context, Uri uri, UploadCallback callback) {
        executor.execute(() -> {
            try {
                ContentResolver resolver = context.getContentResolver();
                String mime = resolver.getType(uri);
                if (mime == null) mime = "application/octet-stream";

                String fileName = queryFileName(resolver, uri);
                long fileSize = queryFileSize(resolver, uri);

                if (fileSize > MAX_UPLOAD_SIZE_BYTES) {
                    postError(callback, "حجم الملف يتجاوز الحد الأقصى المسموح به (200MB)!");
                    return;
                }

                if (fileSize > 0 && fileSize <= INLINE_DATA_URL_MAX_BYTES && (mime.startsWith("image/") || mime.startsWith("audio/"))) {
                    byte[] bytes = readSmallStreamBounded(resolver.openInputStream(uri), INLINE_DATA_URL_MAX_BYTES);
                    String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                    String dataUrl = "data:" + mime + ";base64," + base64;
                    postSuccess(callback, dataUrl, fileName, bytes.length, mime);
                } else {
                    String finalMime = mime;
                    RequestBody streamBody = new RequestBody() {
                        @Override
                        public MediaType contentType() {
                            return MediaType.parse(finalMime);
                        }

                        @Override
                        public long contentLength() {
                            return fileSize > 0 ? fileSize : -1;
                        }

                        @Override
                        public void writeTo(@NonNull BufferedSink sink) throws IOException {
                            try (InputStream in = resolver.openInputStream(uri)) {
                                if (in == null) throw new IOException("تعذر فتح الملف للقراءة");
                                byte[] buf = new byte[16384];
                                long totalRead = 0;
                                int read;
                                while ((read = in.read(buf)) != -1) {
                                    totalRead += read;
                                    if (totalRead > MAX_UPLOAD_SIZE_BYTES) {
                                        throw new IOException("تجاوز الملف حد 200MB أثناء الرفع");
                                    }
                                    sink.write(buf, 0, read);
                                }
                            }
                        }
                    };
                    String remoteUrl = uploadRequestBodyToCatbox(streamBody, fileName);
                    postSuccess(callback, remoteUrl, fileName, Math.max(0, fileSize), mime);
                }
            } catch (Exception e) {
                postError(callback, "فشل رفع الملف: " + e.getMessage());
            }
        });
    }

    public void processAndUploadFile(File file, String mimeType, UploadCallback callback) {
        executor.execute(() -> {
            try {
                if (file == null || !file.exists()) {
                    postError(callback, "الملف غير موجود");
                    return;
                }
                long size = file.length();
                if (size > MAX_UPLOAD_SIZE_BYTES) {
                    postError(callback, "حجم الملف الصوتي يتجاوز الحد الأقصى (200MB)!");
                    return;
                }
                String name = file.getName();
                String mime = mimeType != null ? mimeType : "audio/mp4";

                if (size <= INLINE_DATA_URL_MAX_BYTES) {
                    byte[] bytes = readSmallStreamBounded(new FileInputStream(file), INLINE_DATA_URL_MAX_BYTES);
                    String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                    String dataUrl = "data:" + mime + ";base64," + base64;
                    postSuccess(callback, dataUrl, name, size, mime);
                } else {
                    RequestBody fileBody = RequestBody.create(file, MediaType.parse(mime));
                    String remoteUrl = uploadRequestBodyToCatbox(fileBody, name);
                    postSuccess(callback, remoteUrl, name, size, mime);
                }
            } catch (Exception e) {
                postError(callback, "فشل رفع التسجيل الصوتي: " + e.getMessage());
            }
        });
    }

    private String uploadRequestBodyToCatbox(RequestBody fileBody, String fileName) throws IOException {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("reqtype", "fileupload")
                .addFormDataPart("fileToUpload", fileName, fileBody)
                .build();

        Request request = new Request.Builder()
                .url(CATBOX_API_URL)
                .header("User-Agent", "RunAndroidNative/2.1")
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

    private byte[] readSmallStreamBounded(InputStream in, long maxBytes) throws IOException {
        if (in == null) throw new IOException("Empty stream");
        try (InputStream input = in; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] data = new byte[16384];
            long total = 0;
            int nRead;
            while ((nRead = input.read(data, 0, data.length)) != -1) {
                total += nRead;
                if (total > maxBytes) {
                    throw new IOException("حجم الملف أكبر من الحد المسموح للتضمين المباشر");
                }
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        }
    }

    private long queryFileSize(ContentResolver resolver, Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (idx >= 0 && !cursor.isNull(idx)) {
                        return cursor.getLong(idx);
                    }
                }
            } catch (Exception ignored) {}
        }
        return -1L;
    }

    private String queryFileName(ContentResolver resolver, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
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
