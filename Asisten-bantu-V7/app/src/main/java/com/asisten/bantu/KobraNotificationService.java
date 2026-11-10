package com.asisten.bantu;

import android.app.Notification;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Membacakan notifikasi masuk (pesan, panggilan masuk, dan lainnya) dan menyimpan beberapa yang terbaru
 * agar bisa dibaca ulang dengan perintah "Kobra baca notifikasi". Perlu diaktifkan pengguna di pengaturan akses notifikasi.
 */
public class KobraNotificationService extends NotificationListenerService {

    private static final int MAX_RECENT = 12;
    private static final int MAX_SPOKEN = 220;
    private static final List<String> RECENT = new ArrayList<>();
    private static final Map<String, String> LAST_BY_KEY = new HashMap<>();
    private static volatile boolean connected = false;

    public static boolean isConnected() { return connected; }

    public static synchronized String summary(int count) {
        if (!connected) return "Akses notifikasi belum aktif. Buka Kobra, lalu tekan Akses notifikasi.";
        if (RECENT.isEmpty()) return "Belum ada notifikasi baru.";
        int n = Math.min(count, RECENT.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append("Notifikasi ").append(i + 1).append(". ")
                    .append(RECENT.get(RECENT.size() - 1 - i)).append(" ");
        }
        return sb.toString().trim();
    }

    private static synchronized void remember(String line) {
        RECENT.add(line);
        while (RECENT.size() > MAX_RECENT) RECENT.remove(0);
    }

    @Override public void onListenerConnected() { connected = true; }

    @Override public void onListenerDisconnected() {
        connected = false;
        try {
            requestRebind(new ComponentName(this, KobraNotificationService.class));
        } catch (Exception ignored) { }
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            handle(sbn);
        } catch (Exception ignored) { }
    }

    private void handle(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return;
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        boolean call = Notification.CATEGORY_CALL.equals(n.category);
        if (sbn.isOngoing() && !call) return;

        Bundle ex = n.extras;
        String title = str(ex.getCharSequence(Notification.EXTRA_TITLE));
        String text = str(ex.getCharSequence(Notification.EXTRA_TEXT));
        if (title.isEmpty() && text.isEmpty()) return;

        String content = title + "|" + text;
        synchronized (KobraNotificationService.class) {
            if (content.equals(LAST_BY_KEY.get(sbn.getKey()))) return;
            LAST_BY_KEY.put(sbn.getKey(), content);
            if (LAST_BY_KEY.size() > 100) LAST_BY_KEY.clear();
        }

        String body = title.isEmpty() ? text : (text.isEmpty() ? title : title + ". " + text);
        String spoken = (call ? "Panggilan. " : appLabel(sbn.getPackageName()) + ". ") + body;
        if (spoken.length() > MAX_SPOKEN) spoken = spoken.substring(0, MAX_SPOKEN);
        remember(spoken);
        if (new KobraConfig(this).isReadNotifications()) Speaker.get(this).speakQueued(spoken);
    }

    private static String str(CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }
}
