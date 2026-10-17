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
* - Files <= 350 KB (358,400 bytes) are encoded directly as a DataURL
(data:<mime>;base64,...).
* - Larger files (up to 200 MB) are streamed in 16KB chunks to https://catbox.moe/user/api.php
without loading the entire file into RAM.
*/
public class CatboxUploader {
private static final String CATBOX_API_URL = "https://catbox.moe/user/api.php";
private static final long INLINE_DATA_URL_MAX_BYTES = 350 * 1024L;
public static final long MAX_UPLOAD_SIZE_BYTES = 200L * 1024L * 1024L;
limit

// 350 KB
// 200 MB

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
postError(callback, "200( حجم الملف يتجاوز الحد األقصى المسموح بهMB)!");
return;
}
if (fileSize > 0 && fileSize <= INLINE_DATA_URL_MAX_BYTES &&
(mime.startsWith("image/") || mime.startsWith("audio/"))) {
byte[] bytes = readSmallStreamBounded(resolver.openInputStream(uri),
INLINE_DATA_URL_MAX_BYTES);

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
