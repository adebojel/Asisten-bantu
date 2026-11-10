package com.asisten.bantu;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

/**
 * Menjaga Kobra tetap hidup di latar belakang. Mencoba tipe mikrofon agar Android mengizinkan
 * pengenalan suara saat layar mati; jika sistem menolak, jatuh ke tipe penggunaan khusus.
 */
public class KobraForegroundService extends Service {
    private static final String CHANNEL_ID = "kobra_v7";
    private static final int NOTIF_ID = 1001;

    public static void start(Context c) {
        Intent i = new Intent(c, KobraForegroundService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) { }
    }

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Kobra OS", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Kobra OS tetap aktif");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, KobraLauncherActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Kobra OS aktif")
                .setContentText("Ucapkan Kobra lalu perintah Anda")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (!tryStart(n, true) && !tryStart(n, false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private boolean tryStart(Notification n, boolean withMic) {
        try {
            boolean mic = withMic && Perms.has(this, Manifest.permission.RECORD_AUDIO);
            if (Build.VERSION.SDK_INT >= 34) {
                int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
                if (mic) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                startForeground(NOTIF_ID, n, type);
            } else if (Build.VERSION.SDK_INT >= 30 && mic) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIF_ID, n);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
