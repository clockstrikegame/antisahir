package com.antisahir.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class DailyAlarmReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "antisahir_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!SleepManager.isActive(context)) return;

        // 1. Show a notification reminding the user it's bedtime
        showBedtimeNotification(context);

        // 2. Schedule tomorrow's alarm (next day's earlier bedtime)
        SleepManager.scheduleNextDayAlarm(context);
    }

    private void showBedtimeNotification(Context context) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // Create channel (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "مضاد السهر", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("تنبيهات موعد النوم");
            nm.createNotificationChannel(ch);
        }

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                context, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String[] bedHM = { SleepManager.formatHM(SleepManager.getTodayBedtimeHM(context)) };
        int durationMins = SleepManager.getBlockDurationMins(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("🌙 حان وقت النوم!")
                .setContentText("موعد النوم: " + bedHM[0]
                        + " — سيتم حجب التطبيقات لمدة " + durationMins + " دقيقة")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);

        nm.notify(1001, builder.build());
    }
}
