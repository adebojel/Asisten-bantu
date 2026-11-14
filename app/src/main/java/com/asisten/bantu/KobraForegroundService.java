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
 * Menjaga Kobra tetap hidup di latar belakang dan menyediakan tombol aksi notifikasi
 * untuk menampilkan ulang Gelembung Mikrofon 3D Melayang Global apabila sempat ditutup.
 */
public class KobraForegroundService extends Service {
    public static final String ACTION_SHOW_FLOATING_ORB = "com.asisten.bantu.ACTION_SHOW_FLOATING_ORB";
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
            ch.setDescription("Layanan suara dan gelembung aksesibilitas Kobra OS tetap aktif");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SHOW_FLOATING_ORB.equals(intent.getAction())) {
            new KobraConfig(this).setFloatingOrbEnabled(true);
            AsistenAccessibilityService.showFloatingOrb();
        }

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

        Intent orbIntent = new Intent(this, KobraForegroundService.class);
        orbIntent.setAction(ACTION_SHOW_FLOATING_ORB);
        PendingIntent showOrbPending = PendingIntent.getService(
                this,
                1,
                orbIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Kobra OS Siaga")
                .setContentText("Gelembung 3D dan kendali suara aktif di seluruh aplikasi")
                .setSmallIcon(R.drawable.ic_3d_kobra_logo)
                .setContentIntent(open)
                .addAction(0, "Tampilkan Gelembung", showOrbPending)
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
