package com.takidz.run.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * #21: Lite mode for weak devices.
 * Automatically detects low-RAM hardware, low core counts, OS Battery Saver mode,
 * or disabled system animator scale to reduce animation complexity while keeping the app fast.
 */
public class DeviceCapabilityHelper {

    public static boolean isWeakOrPowerSaverDevice(Context context) {
        if (context == null) return false;
        try {
            // 1. Check OS Power Save Mode (Battery Saver)
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isPowerSaveMode()) {
                return true;
            }

            // 2. Check ActivityManager low-RAM flag & total physical RAM (< 3 GB)
            ActivityManager am = (PowerManager.class != null)
                    ? (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE) : null;
            if (am != null) {
                if (am.isLowRamDevice()) return true;
                ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(memInfo);
                long threeGb = 3L * 1024L * 1024L * 1024L;
                if (memInfo.totalMem > 0 && memInfo.totalMem < threeGb) {
                    return true;
                }
            }

            // 3. Check CPU core count (<= 4 cores on older SoCs)
            if (Runtime.getRuntime().availableProcessors() <= 4) {
                return true;
            }

            // 4. Check OS animator duration scale
            float animScale = Settings.Global.getFloat(
                    context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f
            );
            if (animScale <= 0.05f) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static String getDeviceStatusSummary(Context context, PrefsManager prefs) {
        boolean ar = prefs.isArabic();
        String pref = prefs.getLiteModePref();
        if ("on".equalsIgnoreCase(pref)) {
            return ar ? "الوضع الخفيف مفعل يدوياً" : "Lite mode forced ON";
        }
        if ("off".equalsIgnoreCase(pref)) {
            return ar ? "الحركات الكاملة مفعلة يدوياً" : "Full motion forced ON";
        }
        boolean autoWeak = isWeakOrPowerSaverDevice(context);
        if (autoWeak) {
            return ar ? "تلقائي: مفعل (توفير الطاقة / جهاز خفيف)" : "Auto: Engaged (Power Saver / Lite Hardware)";
        } else {
            return ar ? "تلقائي: أداء قياسي (60 إطار/ث)" : "Auto: Full 60fps animations active";
        }
    }
}
