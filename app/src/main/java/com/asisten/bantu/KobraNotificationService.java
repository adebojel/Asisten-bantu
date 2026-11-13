package com.asisten.bantu;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Membacakan notifikasi masuk (pesan, panggilan masuk/Caller ID TTS, dan lainnya),
 * menyaring tautan phishing/penipuan, dan menyimpan aksi RemoteInput untuk Balas Pesan Suara
 * langsung pada WhatsApp/SMS tanpa membuka aplikasi.
 */
public class KobraNotificationService extends NotificationListenerService {

    private static final int MAX_RECENT = 12;
    private static final int MAX_SPOKEN = 280;
    private static final List<String> RECENT = new ArrayList<>();
    private static final Map<String, String> LAST_BY_KEY = new HashMap<>();
    private static volatile boolean connected = false;
    private static volatile ReplyTarget lastReplyTarget = null;

    private static final class ReplyTarget {
        final PendingIntent actionIntent;
        final RemoteInput[] remoteInputs;
        final String sender;
        final String appName;

        ReplyTarget(PendingIntent actionIntent, RemoteInput[] remoteInputs, String sender, String appName) {
            this.actionIntent = actionIntent;
            this.remoteInputs = remoteInputs;
            this.sender = sender;
            this.appName = appName;
        }
    }

    public static boolean isConnected() { return connected; }

    public static boolean hasReplyTarget() {
        return lastReplyTarget != null;
    }

    public static String getLastReplySender() {
        ReplyTarget t = lastReplyTarget;
        return t != null ? t.sender : "";
    }

    public static String getLastReplyApp() {
        ReplyTarget t = lastReplyTarget;
        return t != null ? t.appName : "";
    }

    /** Membalas notifikasi pesan terakhir menggunakan RemoteInput secara langsung. */
    public static boolean replyToLast(Context context, String message) {
        ReplyTarget target = lastReplyTarget;
        if (target == null || message == null || message.trim().isEmpty()) return false;
        try {
            Intent intent = new Intent();
            Bundle results = new Bundle();
            for (RemoteInput ri : target.remoteInputs) {
                results.putCharSequence(ri.getResultKey(), message.trim());
            }
            RemoteInput.addResultsToIntent(target.remoteInputs, intent, results);
            target.actionIntent.send(context, 0, intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

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

        String appName = appLabel(sbn.getPackageName());

        if (!call) {
            captureReplyAction(n, title.isEmpty() ? appName : title, appName);
        }

        String displayTitle = call ? resolveCallerDisplay(title) : title;
        String body = displayTitle.isEmpty() ? text : (text.isEmpty() ? displayTitle : displayTitle + ". " + text);

        String phishWarning = call ? "" : SecurityAndPinHelper.inspectForPhishing(title + " " + text);
        String spoken;
        if (call) {
            spoken = "Telepon masuk dari " + displayTitle + ". Bilang Kobra angkat telepon atau tolak telepon.";
        } else {
            spoken = appName + ". " + body;
        }
        if (spoken.length() > MAX_SPOKEN) spoken = spoken.substring(0, MAX_SPOKEN);
        if (!phishWarning.isEmpty()) {
            spoken = phishWarning + " Isi pesan: " + spoken;
        }
        remember(spoken);

        if (!phishWarning.isEmpty() || new KobraConfig(this).isReadNotifications()) {
            Speaker.get(this).speakQueued(spoken);
        }
    }

    private void captureReplyAction(Notification n, String sender, String appName) {
        if (n.actions == null) return;
        for (Notification.Action action : n.actions) {
            if (action == null || action.actionIntent == null) continue;
            RemoteInput[] ris = action.getRemoteInputs();
            if (ris != null && ris.length > 0) {
                lastReplyTarget = new ReplyTarget(action.actionIntent, ris, sender, appName);
                return;
            }
        }
    }

    private String resolveCallerDisplay(String rawTitle) {
        if (rawTitle == null || rawTitle.trim().isEmpty()) return "penelepon tidak dikenal";
        String trimmed = rawTitle.trim();
        if (!trimmed.matches(".*\\d{4,}.*") || !Perms.has(this, Manifest.permission.READ_CONTACTS)) {
            return trimmed;
        }
        try (Cursor cur = getContentResolver().query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(trimmed)),
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                String name = cur.getString(0);
                if (name != null && !name.trim().isEmpty()) return name.trim();
            }
        } catch (Exception ignored) {}
        return trimmed;
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
