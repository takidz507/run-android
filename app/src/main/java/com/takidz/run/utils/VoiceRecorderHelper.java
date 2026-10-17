package com.takidz.run.utils;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;

public class VoiceRecorderHelper {
    private MediaRecorder recorder;
    private File currentOutputFile;
    private long startTimeMs;
    private boolean isRecording = false;

    private MediaPlayer activePlayer;
    private String playingMessageId;

    public interface PlaybackListener {
        void onStarted(String messageId);
        void onCompleted(String messageId);
        void onError(String error);
    }

    public File startRecording(Context context) throws Exception {
        stopRecording();
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
        return currentOutputFile;
    }

    public int stopRecording() {
        if (!isRecording || recorder == null) return 0;
        int durationSec = (int) Math.max(1, (System.currentTimeMillis() - startTimeMs) / 1000);
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
                File tempAudio = new File(context.getCacheDir(), "play_" + messageId + ".m4a");
                try (FileOutputStream fos = new FileOutputStream(tempAudio)) {
                    fos.write(audioBytes);
                }
                activePlayer.setDataSource(tempAudio.getAbsolutePath());
            } else {
                activePlayer.setDataSource(urlOrDataUri);
            }

            activePlayer.setOnPreparedListener(mp -> {
                mp.start();
                if (listener != null) listener.onStarted(messageId);
            });
            activePlayer.setOnCompletionListener(mp -> {
                playingMessageId = null;
                if (listener != null) listener.onCompleted(messageId);
            });
            activePlayer.setOnErrorListener((mp, what, extra) -> {
                playingMessageId = null;
                if (listener != null) listener.onError("تعذر تشغيل المقطع الصوتي");
                return true;
            });
            activePlayer.prepareAsync();
        } catch (Exception e) {
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
    }

    public String getPlayingMessageId() {
        return playingMessageId;
    }
}
