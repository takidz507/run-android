package com.takidz.run;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.takidz.run.utils.PrefsManager;
import com.takidz.run.utils.ThemeHelper;

public class RunApplication extends Application {
    public static final String CHANNEL_ID_MESSAGES = "run_room_messages";

    @Override
    public void onCreate() {
        super.onCreate();
        PrefsManager prefs = new PrefsManager(this);
        ThemeHelper.applyAppNightMode(prefs.getThemeMode());
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_MESSAGES,
                    "رسائل ومهام الغرف (Run)",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("إشعارات الرسائل والمهام الجديدة في الغرف المشتركة");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
