package com.takidz.run.utils;

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * #20: Light sound & vibration touches.
 * Optional subtle vibration and soft warm paper acoustic tone on message send/receive and gestures.
 * Strictly respects OS Silent / Vibrate / Do Not Disturb (DND) modes and the Settings toggle.
 */
public class FeedbackTouchHelper {

    private static final ExecutorService SOUND_EXECUTOR = Executors.newSingleThreadExecutor();

    public static boolean canVibrate(Context context, PrefsManager prefs) {
        if (context == null || prefs == null || !prefs.isTouchFeedbackEnabled()) return false;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null && am.getRingerMode() == AudioManager.RINGER_MODE_SILENT) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    int filter = nm.getCurrentInterruptionFilter();
                    if (filter != NotificationManager.INTERRUPTION_FILTER_ALL
                            && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean canPlaySoftSound(Context context, PrefsManager prefs) {
        if (!canVibrate(context, prefs)) return false;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            return am != null && am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
        } catch (Exception e) {
            return false;
        }
    }

    public static void onMessageSent(Context context, PrefsManager prefs, View anchorView) {
        if (!canVibrate(context, prefs)) return;
        triggerSubtleHaptic(context, anchorView, 12);
        if (canPlaySoftSound(context, prefs)) {
            playSoftPaperTone(587.33, 45, 0.08f); // Soft D5 warm wooden tap
        }
    }

    public static void onMessageReceived(Context context, PrefsManager prefs) {
        if (!canVibrate(context, prefs)) return;
        triggerSubtleHaptic(context, null, 10);
        if (canPlaySoftSound(context, prefs)) {
            playSoftPaperTone(783.99, 55, 0.06f); // Soft G5 gentle chime
        }
    }

    public static void onGestureTick(Context context, PrefsManager prefs, View view) {
        if (!canVibrate(context, prefs)) return;
        if (view != null) {
            try {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                return;
            } catch (Exception ignored) {}
        }
        triggerSubtleHaptic(context, null, 8);
    }

    private static void triggerSubtleHaptic(Context context, View view, long durationMs) {
        try {
            if (view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                return;
            }
            Vibrator vib = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vib == null || !vib.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(durationMs, 60));
            } else {
                //noinspection deprecation
                vib.vibrate(durationMs);
            }
        } catch (Exception ignored) {}
    }

    private static void playSoftPaperTone(final double freqHz, final int durationMs, final float maxGain) {
        SOUND_EXECUTOR.execute(() -> {
            AudioTrack track = null;
            try {
                int sampleRate = 22050;
                int numSamples = Math.max(1, (sampleRate * durationMs) / 1000);
                short[] pcm = new short[numSamples];
                for (int i = 0; i < numSamples; i++) {
                    double progress = (double) i / numSamples;
                    // Exponential decay envelope for a warm, quiet paper-tap sound
                    double env = Math.sin(Math.PI * Math.min(1.0, progress * 5.0)) * Math.exp(-4.5 * progress);
                    double sample = Math.sin(2.0 * Math.PI * i * freqHz / sampleRate) * env * maxGain;
                    pcm[i] = (short) (sample * Short.MAX_VALUE);
                }
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                AudioFormat format = new AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build();
                track = new AudioTrack(attrs, format, pcm.length * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
                track.write(pcm, 0, pcm.length);
                track.play();
                Thread.sleep(durationMs + 20L);
            } catch (Exception ignored) {
            } finally {
                if (track != null) {
                    try { track.release(); } catch (Exception ignored) {}
                }
            }
        });
    }
}
