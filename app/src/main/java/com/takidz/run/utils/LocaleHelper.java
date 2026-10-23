package com.takidz.run.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.Window;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * #28: Full Arabic mode & Localization engine.
 * - Complete RTL layout mirroring across activities, dialogs, and views.
 * - Arabic-friendly dates, relative times, and localized numerals.
 * - English remains the default language ("en").
 */
public class LocaleHelper {

    public static void applyLocaleAndLayoutDirection(Activity activity, PrefsManager prefs) {
        if (activity == null || prefs == null) return;
        boolean isAr = prefs.isArabic();
        Locale locale = new Locale(isAr ? "ar" : "en");
        Locale.setDefault(locale);
        try {
            Configuration config = new Configuration(activity.getResources().getConfiguration());
            config.setLocale(locale);
            config.setLayoutDirection(locale);
            //noinspection deprecation
            activity.getResources().updateConfiguration(config, activity.getResources().getDisplayMetrics());
        } catch (Exception ignored) {}

        Window window = activity.getWindow();
        if (window != null && window.getDecorView() != null) {
            window.getDecorView().setLayoutDirection(
                    isAr ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR
            );
        }
    }

    public static void applyViewDirection(View root, boolean isArabic) {
        if (root == null) return;
        root.setLayoutDirection(isArabic ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    }

    public static String getGreeting(String userName, boolean isArabic) {
        String cleanName = (userName == null || userName.trim().isEmpty())
                ? (isArabic ? "صديقنا" : "there")
                : userName.trim();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (isArabic) {
            if (hour < 12) return "صباح الخير، " + cleanName;
            if (hour < 18) return "طاب يومك، " + cleanName;
            return "مساء الخير، " + cleanName;
        } else {
            if (hour < 12) return "Good morning, " + cleanName;
            if (hour < 18) return "Good afternoon, " + cleanName;
            return "Good evening, " + cleanName;
        }
    }

    public static String formatNumber(int number, boolean isArabic) {
        try {
            NumberFormat nf = NumberFormat.getInstance(new Locale(isArabic ? "ar" : "en"));
            return nf.format(number);
        } catch (Exception e) {
            return String.valueOf(number);
        }
    }

    public static String formatIsoTime(String iso, boolean isArabic) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = parser.parse(iso);
            if (date != null) {
                Locale loc = new Locale(isArabic ? "ar" : "en");
                SimpleDateFormat localFormat = new SimpleDateFormat("HH:mm", loc);
                localFormat.setTimeZone(TimeZone.getDefault());
                return localFormat.format(date);
            }
        } catch (Exception ignored) {}
        if (iso.length() >= 16) {
            return iso.substring(11, 16);
        }
        return "";
    }

    public static String formatRelativeTime(String isoOrTs, boolean isArabic) {
        if (isoOrTs == null || isoOrTs.isEmpty()) return isArabic ? "مؤخراً" : "Recently";
        long ts = RoomCryptoUtils.parseIsoOrLongTimestamp(isoOrTs);
        return formatRelativeTimeMs(ts, isArabic);
    }

    public static String formatRelativeTimeMs(long ts, boolean isArabic) {
        long diffSec = Math.max(1L, (System.currentTimeMillis() - ts) / 1000L);
        if (isArabic) {
            if (diffSec < 60) return "الآن";
            long mins = diffSec / 60;
            if (mins < 60) return "منذ " + formatNumber((int) mins, true) + " د";
            long hours = mins / 60;
            if (hours < 24) return "منذ " + formatNumber((int) hours, true) + " س";
            long days = hours / 24;
            return "منذ " + formatNumber((int) days, true) + " ي";
        } else {
            if (diffSec < 60) return "just now";
            long mins = diffSec / 60;
            if (mins < 60) return mins + "m ago";
            long hours = mins / 60;
            if (hours < 24) return hours + "h ago";
            long days = hours / 24;
            return days + "d ago";
        }
    }

    public static String t(boolean ar, String enText, String arText) {
        return ar ? arText : enText;
    }
}
