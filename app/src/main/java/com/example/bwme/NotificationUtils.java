package com.example.bwme;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public final class NotificationUtils {
    private static final String TAG = "NotificationUtils";
    private static final String CHANNEL_ID = "bwme_budget_channel";
    private static final String CHANNEL_NAME = "Budget alerts";

    public static final int NOTIF_ID_DAILY = 1001;
    public static final int NOTIF_ID_WEEKLY = 1002;
    public static final int NOTIF_ID_MONTHLY = 1003;

    public static void createNotificationChannel(Context ctx) {
        if (ctx == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Notifications when you exceed configured budgets");
            ch.enableLights(true);
            ch.enableVibration(true);
            ch.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    public static void sendNotification(Context ctx, String title, String body, int id) {
        if (ctx == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "POST_NOTIFICATIONS permission not granted; skipping notification");
                return;
            }
        }

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build());
        } catch (SecurityException se) {
            Log.w(TAG, "Failed to post notification due to SecurityException", se);
        } catch (Exception e) {
            Log.w(TAG, "Failed to post notification", e);
        }
    }

    public static void sendNotification(Context ctx, String title, String body) {
        int id = (int) (System.currentTimeMillis() & 0x7fffffff);
        sendNotification(ctx, title, body, id);
    }
}