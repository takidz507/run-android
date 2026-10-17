package com.takidz.run.utils;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;

public class VoiceRecorderHelper {
    public static final int MAX_RECORD_DURATION_SEC = 120; // 120 seconds cap (#8)

    private MediaRecorder recorder;
    private File currentOutputFile;
    private long startTimeMs;
    private boolean isRecording = false;

    private MediaPlayer activePlayer;
    private File activeTempPlaybackFile;
    private String playingMessageId;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private RecordingProgressListener recordingListener;

    public interface RecordingProgressListener {
        void onTick(int elapsedSeconds, int maxSeconds);
        void onMaxDurationReached(File recordedFile, int durationSeconds);
    }

    public interface PlaybackListener {
        void onStarted(String messageId);
        void onCompleted(String messageId);
        void onError(String error);
    }

    private final Runnable recordingTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRecording) return;
            int elapsed = (int) ((System.currentTimeMillis() - startTimeMs) / 1000);
            if (recordingListener != null) {
                recordingListener.onTick(elapsed, MAX_RECORD_DURATION_SEC);
            }
            if (elapsed >= MAX_RECORD_DURATION_SEC) {
                File out = currentOutputFile;
                int dur = stopRecording();
                if (recordingListener != null && out != null && out.exists()) {
                    recordingListener.onMaxDurationReached(out, dur);
                }
                return;
            }
            timerHandler.postDelayed(this, 1000L);
        }
    };

    public VoiceRecorderHelper(Context context) {
        cleanupStaleAudioCache(context);
    }

    private void cleanupStaleAudioCache(Context context) {
        if (context == null) return;
        File cacheDir = context.getCacheDir();
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - (24L * 3600L * 1000L);
        for (File f : files) {
            String name = f.getName();
            if ((name.startsWith("play_") || name.startsWith("voice_")) && f.lastModified() < cutoff) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    public File startRecording(Context context, RecordingProgressListener listener) throws Exception {
        cancelRecording();
        this.recordingListener = listener;
        File cacheDir = context.getCacheDir();
        currentOutputFile = new File(cacheDir, "voice_" + System.currentTimeMillis() + ".m4a");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            recorder = new MediaRecorder(context);
        } else {
            recorder = new MediaRecorder();
        }
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioEncodingBitRate(64000);
        recorder.setAudioSamplingRate(44100);
        recorder.setOutputFile(currentOutputFile.getAbsolutePath());
        recorder.prepare();
        recorder.start();
        startTimeMs = System.currentTimeMillis();
        isRecording = true;

        timerHandler.removeCallbacks(recordingTimerRunnable);
        timerHandler.postDelayed(recordingTimerRunnable, 1000L);
        return currentOutputFile;
    }

    public int stopRecording() {
        timerHandler.removeCallbacks(recordingTimerRunnable);
        if (!isRecording || recorder == null) return 0;
        int durationSec = (int) Math.min(MAX_RECORD_DURATION_SEC, Math.max(1, (System.currentTimeMillis() - startTimeMs) / 1000));
        try {
            recorder.stop();
        } catch (Exception ignored) {}
        try {
            recorder.release();
        } catch (Exception ignored) {}
        recorder = null;
        isRecording = false;
        return durationSec;
    }

    /**
     * Safely stops and discards an active recording when the user leaves the activity (#3).
     */
    public void cancelRecording() {
        timerHandler.removeCallbacks(recordingTimerRunnable);
        if (isRecording && recorder != null) {
            try {
                recorder.stop();
            } catch (Exception ignored) {}
            try {
                recorder.release();
            } catch (Exception ignored) {}
            recorder = null;
            isRecording = false;
            if (currentOutputFile != null && currentOutputFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                currentOutputFile.delete();
            }
            currentOutputFile = null;
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    public File getCurrentOutputFile() {
        return currentOutputFile;
    }

    public void playVoiceUrlOrDataUri(Context context, String messageId, String urlOrDataUri, PlaybackListener listener) {
        stopPlayback();
        try {
            activePlayer = new MediaPlayer();
            playingMessageId = messageId;

            if (urlOrDataUri.startsWith("data:")) {
                int commaIdx = urlOrDataUri.indexOf(',');
                if (commaIdx == -1) throw new Exception("Invalid Data URI");
                String base64Part = urlOrDataUri.substring(commaIdx + 1);
                byte[] audioBytes = Base64.decode(base64Part, Base64.DEFAULT);
                activeTempPlaybackFile = new File(context.getCacheDir(), "play_" + messageId + "_" + System.currentTimeMillis() + ".m4a");
                try (FileOutputStream fos = new FileOutputStream(activeTempPlaybackFile)) {
                    fos.write(audioBytes);
                }
                activePlayer.setDataSource(activeTempPlaybackFile.getAbsolutePath());
            } else {
                activePlayer.setDataSource(urlOrDataUri);
            }

            activePlayer.setOnPreparedListener(mp -> {
                mp.start();
                if (listener != null) listener.onStarted(messageId);
            });
            activePlayer.setOnCompletionListener(mp -> {
                deleteActiveTempPlaybackFile();
                playingMessageId = null;
                if (listener != null) listener.onCompleted(messageId);
            });
            activePlayer.setOnErrorListener((mp, what, extra) -> {
                deleteActiveTempPlaybackFile();
                playingMessageId = null;
                if (listener != null) listener.onError("تعذر تشغيل المقطع الصوتي");
                return true;
            });
            activePlayer.prepareAsync();
        } catch (Exception e) {
            deleteActiveTempPlaybackFile();
            playingMessageId = null;
            if (listener != null) listener.onError("خطأ في تشغيل الصوت: " + e.getMessage());
        }
    }

    public void stopPlayback() {
        if (activePlayer != null) {
            try {
                if (activePlayer.isPlaying()) activePlayer.stop();
            } catch (Exception ignored) {}
            try {
                activePlayer.release();
            } catch (Exception ignored) {}
            activePlayer = null;
            playingMessageId = null;
        }
        deleteActiveTempPlaybackFile();
    }

    private void deleteActiveTempPlaybackFile() {
        if (activeTempPlaybackFile != null && activeTempPlaybackFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            activeTempPlaybackFile.delete();
            activeTempPlaybackFile = null;
        }
    }

    public String getPlayingMessageId() {
        return playingMessageId;
    }
}
