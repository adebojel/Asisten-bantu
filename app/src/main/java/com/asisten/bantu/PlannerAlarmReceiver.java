package com.asisten.bantu;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import java.util.Locale;

/**
 * Penerima alarm pengingat Offline Daily Planner (#73).
 * Berbunyi dengan nada peringatan, getaran haptik, notifikasi lokal, dan pengumuman suara TTS.
 */
public class PlannerAlarmReceiver extends BroadcastReceiver {

    public static final String EXTRA_ID = "planner_id";
    public static final String EXTRA_TITLE = "planner_title";
    public static final String EXTRA_HOUR = "planner_hour";
    public static final String EXTRA_MINUTE = "planner_minute";
    private static final String CHANNEL_ID = "kobra_planner";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        int id = intent.getIntExtra(EXTRA_ID, 2001);
        String title = intent.getStringExtra(EXTRA_TITLE);
        int hour = intent.getIntExtra(EXTRA_HOUR, -1);
        int minute = intent.getIntExtra(EXTRA_MINUTE, -1);
        if (title == null || title.trim().isEmpty()) {
            title = "Agenda terjadwal";
        } else {
            title = title.trim();
        }

        String timeText = (hour >= 0 && minute >= 0)
                ? String.format(Locale.US, " pukul %02d:%02d", hour, minute)
                : "";
        String speech = "Pengingat agenda" + timeText + ". " + title + ".";

        try {
            HapticFeedback.vibrateWarning(context);
        } catch (Exception ignored) {}

        try {
            ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_ALARM, 85);
            tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 650);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { tg.release(); } catch (Exception ignored) {}
            }, 850L);
        } catch (Exception ignored) {}

        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel ch = new NotificationChannel(
                            CHANNEL_ID, "Agenda Harian Kobra OS", NotificationManager.IMPORTANCE_HIGH);
                    ch.setDescription("Pengingat jadwal dan agenda offline");
                    nm.createNotificationChannel(ch);
                }
                PendingIntent open = PendingIntent.getActivity(
                        context,
                        id,
                        new Intent(context, KobraLauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Notification notif = new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentTitle("Pengingat Agenda" + timeText)
                        .setContentText(title)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(open)
                        .build();
                nm.notify(3000 + (id % 500), notif);
            }
        } catch (Exception ignored) {}

        try {
            Speaker.get(context.getApplicationContext()).speakQueued(speech);
        } catch (Exception ignored) {}
    }
}
