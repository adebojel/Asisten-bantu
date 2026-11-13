package com.asisten.bantu;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kumpulan utilitas sistem offline untuk Kobra OS:
 * - Pembaca Notifikasi Finansial Cerdas (#72)
 * - Penjadwal Offline Daily Planner via AlarmManager (#73)
 * - Pengendali Media Universal & Pemutar Audio Lokal (#74)
 * - Monitor Sinyal Seluler, Operator, & Wi-Fi (#75)
 */
public final class SystemUtilityHelper {

    private SystemUtilityHelper() {}

    // =========================================================================
    // #72. Smart Financial Notification Reader
    // =========================================================================

    private static final Set<String> BANK_PACKAGES = new HashSet<>(Arrays.asList(
            "com.bri.brimo", "id.co.bri.brimo",
            "com.bca", "com.bca.mybca.omni.android",
            "com.bankmandiri.mandirionline", "id.bmri.livin",
            "src.com.bni", "id.co.bni", "id.co.bni.wondr",
            "co.id.bankbsi.bsimobile", "id.co.bankbsi.byond",
            "id.dana", "com.gojek.app", "com.gopay.app",
            "ovo.id", "com.shopee.id", "com.shopeepay.id",
            "com.telkom.mwallet", "id.co.seabank.seabank", "com.jago.digitalbanking"
    ));

    private static final Pattern RUPIAH_PATTERN =
            Pattern.compile("(?:rp\\.?|idr)\\s*([0-9][0-9.,]*)", Pattern.CASE_INSENSITIVE);

    public static final class FinancialParseResult {
        public final boolean fromFinancialSource;
        public final boolean isSpamOrPromo;
        public final String summary;

        FinancialParseResult(boolean fromFinancialSource, boolean isSpamOrPromo, String summary) {
            this.fromFinancialSource = fromFinancialSource;
            this.isSpamOrPromo = isSpamOrPromo;
            this.summary = summary;
        }
    }

    public static FinancialParseResult parseFinancialNotification(String pkg, String appName, String title, String text) {
        String pLower = pkg == null ? "" : pkg.toLowerCase(Locale.ROOT);
        String aLower = appName == null ? "" : appName.toLowerCase(Locale.ROOT);
        String combined = ((title == null ? "" : title) + " " + (text == null ? "" : text)).trim();
        String cLower = combined.toLowerCase(Locale.ROOT);

        boolean sourceMatch = BANK_PACKAGES.contains(pLower)
                || containsAny(pLower, "brimo", "mybca", "bca", "mandiri", "livin", "bni", "wondr",
                        "bsi", "byond", "dana", "gopay", "ovo", "shopeepay", "linkaja", "seabank", "jago")
                || containsAny(aLower, "brimo", "bri", "bca", "mybca", "mandiri", "livin", "bni", "wondr",
                        "bsi", "byond", "dana", "gopay", "ovo", "shopeepay", "linkaja", "seabank", "jago");

        boolean strongTx = containsAny(cLower,
                "transfer masuk", "dana masuk", "uang masuk", "transfer berhasil",
                "pembayaran berhasil", "pembelian berhasil", "mutasi", "qris",
                "top up berhasil", "topup berhasil", "berhasil diterima", "transaksi berhasil");

        if (!sourceMatch && !strongTx) {
            return new FinancialParseResult(false, false, "");
        }

        Matcher m = RUPIAH_PATTERN.matcher(combined);
        String nominal = "";
        if (m.find()) {
            String digits = m.group(1);
            if (digits != null) {
                // Bersihkan koma desimal akhir seperti ",00"
                digits = digits.replaceAll(",00$", "").replaceAll("\\.00$", "");
                nominal = digits + " rupiah";
            }
        }

        boolean hasPromoWords = containsAny(cLower,
                "promo", "diskon", "voucher", "cashback hingga", "gratis ongkir",
                "undian", "cicilan 0%", "flash sale", "klaim sekarang", "kode promo",
                "penawaran spesial", "ajukan pinjaman", "limit hingga", "hadiah langsung");

        boolean hasConfirmedTxWords = containsAny(cLower,
                "berhasil", "diterima", "masuk", "keluar", "pembayaran", "bayar",
                "transfer", "mutasi", "top up", "topup", "debit", "kredit",
                "tarik tunai", "pembelian", "terkirim", "selesai", "sukses");

        if (hasPromoWords && (!hasConfirmedTxWords || nominal.isEmpty())) {
            return new FinancialParseResult(true, true, "");
        }
        if (nominal.isEmpty() && !hasConfirmedTxWords) {
            return new FinancialParseResult(true, true, "");
        }

        String bankLabel = resolveFinancialLabel(pLower, aLower, appName);
        String direction = "Transaksi";
        if (containsAny(cLower, "masuk", "diterima", "kredit", "top up berhasil", "topup berhasil", "menerima")) {
            direction = "Transaksi masuk";
        } else if (containsAny(cLower, "keluar", "pembayaran", "bayar", "transfer ke", "kirim", "debit", "pembelian", "tarik tunai")) {
            direction = "Transaksi keluar";
        }

        String cleanDetail = combined.replaceAll("https?://\\S+", "").replaceAll("\\s+", " ").trim();
        if (cleanDetail.length() > 160) cleanDetail = cleanDetail.substring(0, 160);

        StringBuilder sb = new StringBuilder();
        sb.append(bankLabel).append(". ").append(direction);
        if (!nominal.isEmpty()) {
            sb.append(" sebesar ").append(nominal);
        }
        sb.append(". ").append(cleanDetail);
        return new FinancialParseResult(true, false, sb.toString().trim());
    }

    private static String resolveFinancialLabel(String pkg, String appLower, String fallback) {
        if (pkg.contains("bri") || appLower.contains("brimo")) return "BRImo";
        if (pkg.contains("bca") || appLower.contains("bca")) return "BCA";
        if (pkg.contains("mandiri") || pkg.contains("bmri") || appLower.contains("livin") || appLower.contains("mandiri")) return "Mandiri";
        if (pkg.contains("bni") || appLower.contains("bni") || appLower.contains("wondr")) return "BNI";
        if (pkg.contains("bsi") || appLower.contains("bsi") || appLower.contains("byond")) return "BSI";
        if (pkg.contains("dana") || appLower.contains("dana")) return "DANA";
        if (pkg.contains("gojek") || pkg.contains("gopay") || appLower.contains("gopay")) return "GoPay";
        if (pkg.contains("ovo") || appLower.contains("ovo")) return "OVO";
        if (pkg.contains("shopee") || appLower.contains("shopee")) return "ShopeePay";
        if (pkg.contains("mwallet") || appLower.contains("linkaja")) return "LinkAja";
        if (pkg.contains("seabank") || appLower.contains("seabank")) return "SeaBank";
        if (pkg.contains("jago") || appLower.contains("jago")) return "Bank Jago";
        return (fallback == null || fallback.trim().isEmpty()) ? "Keuangan" : fallback.trim();
    }

    // =========================================================================
    // #73. Offline Daily Planner via AlarmManager
    // =========================================================================

    public static String schedulePlannerFromVoice(Context context, KobraConfig config, String body) {
        if (body == null || body.trim().isEmpty()) {
            return "Sebutkan jam dan agendanya. Contoh: Kobra ingatkan jam 16.00 minum vitamin.";
        }
        String raw = body.trim();
        int hour = -1;
        int minute = 0;
        String title = "";

        Matcher explicitTime = Pattern.compile("(?:^|\\s)(?:jam|pukul)?\\s*(\\d{1,2})[.:](\\d{2})(?:\\s*(pagi|siang|sore|malam))?(?=\\s|$)",
                Pattern.CASE_INSENSITIVE).matcher(raw);
        if (explicitTime.find()) {
            hour = parseIntSafe(explicitTime.group(1), -1);
            minute = parseIntSafe(explicitTime.group(2), 0);
            String period = explicitTime.group(3) != null ? explicitTime.group(3).toLowerCase(Locale.ROOT) : "";
            hour = adjustPeriod(hour, period, raw);
            title = (raw.substring(0, explicitTime.start()) + " " + raw.substring(explicitTime.end())).replaceAll("\\s+", " ").trim();
        } else {
            List<Integer> nums = OfflineBrain.numbers(raw);
            if (!nums.isEmpty()) {
                hour = nums.get(0);
                minute = nums.size() > 1 && nums.get(1) >= 0 && nums.get(1) <= 59 ? nums.get(1) : 0;
                hour = adjustPeriod(hour, "", raw);
                title = raw.replaceFirst("^.*?(?:jam|pukul)\\s*", "")
                        .replaceAll("\\b(jam|pukul|pagi|siang|sore|malam|lewat|kurang|tengah|nol|satu|dua|tiga|empat|lima|enam|tujuh|delapan|sembilan|sepuluh|sebelas|belas|puluh|\\d+)\\b", " ")
                        .replaceAll("\\s+", " ").trim();
                if (title.isEmpty()) {
                    title = raw.replaceAll("\\d+", "").trim();
                }
            }
        }

        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return "Waktu agenda tidak dikenali. Contoh: Kobra ingatkan jam 16.00 minum vitamin.";
        }
        if (title.isEmpty()) {
            title = "Pengingat agenda";
        }
        title = title.replaceFirst("^(untuk|buat)\\s+", "").trim();
        if (title.isEmpty()) title = "Pengingat agenda";

        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        boolean tomorrow = false;
        if (target.getTimeInMillis() <= System.currentTimeMillis() + 5000L) {
            target.add(Calendar.DAY_OF_MONTH, 1);
            tomorrow = true;
        }

        int id = config.nextPlannerId();
        long triggerMs = target.getTimeInMillis();
        config.addPlannerItem(id, hour, minute, title, triggerMs);

        scheduleAlarm(context, id, hour, minute, title, triggerMs);
        String timeStr = String.format(Locale.US, "%02d:%02d", hour, minute);
        return "Agenda dicatat dan pengingat dipasang untuk "
                + (tomorrow ? "besok " : "") + "pukul " + timeStr + ": " + title + ".";
    }

    private static int adjustPeriod(int hour, String period, String full) {
        String lower = full.toLowerCase(Locale.ROOT);
        boolean pm = "sore".equals(period) || "malam".equals(period) || "siang".equals(period)
                || lower.contains(" sore") || lower.contains(" malam") || (lower.contains(" siang") && hour < 6);
        if (pm && hour >= 1 && hour <= 11) {
            return hour + 12;
        }
        if (("malam".equals(period) || lower.contains(" malam")) && hour == 12) {
            return 0;
        }
        return hour;
    }

    private static int parseIntSafe(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (Exception e) { return fallback; }
    }

    private static void scheduleAlarm(Context context, int id, int hour, int minute, String title, long triggerMs) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(context, PlannerAlarmReceiver.class);
        intent.putExtra(PlannerAlarmReceiver.EXTRA_ID, id);
        intent.putExtra(PlannerAlarmReceiver.EXTRA_TITLE, title);
        intent.putExtra(PlannerAlarmReceiver.EXTRA_HOUR, hour);
        intent.putExtra(PlannerAlarmReceiver.EXTRA_MINUTE, minute);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
                }
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            }
        } catch (SecurityException se) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    public static String cancelAllPlannerAlarms(Context context, KobraConfig config) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        List<Integer> ids = config.getPlannerIds();
        if (am != null) {
            for (Integer id : ids) {
                if (id == null || id <= 0) continue;
                try {
                    Intent intent = new Intent(context, PlannerAlarmReceiver.class);
                    PendingIntent pi = PendingIntent.getBroadcast(
                            context, id, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                    if (pi != null) {
                        am.cancel(pi);
                        pi.cancel();
                    }
                } catch (Exception ignored) {}
            }
        }
        config.clearPlannerItems();
        return "Semua agenda pengingat harian telah dihapus.";
    }

    // =========================================================================
    // #74. Universal Media & Local Audio Controller
    // =========================================================================

    private static final class AudioTrack {
        final Uri uri;
        final String title;
        AudioTrack(Uri uri, String title) {
            this.uri = uri;
            this.title = title;
        }
    }

    private static MediaPlayer localPlayer;
    private static final List<AudioTrack> localPlaylist = new ArrayList<>();
    private static int localIndex = 0;

    public static synchronized boolean hasLocalAudioActive() {
        return localPlayer != null;
    }

    public static synchronized String playLocalAudio(Context context, Speaker speaker) {
        String perm = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (!Perms.has(context, perm)) {
            return "Izin penyimpanan audio belum aktif. Buka Kobra lalu tekan Beri Izin.";
        }

        localPlaylist.clear();
        ContentResolver cr = context.getContentResolver();
        String[] proj = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DISPLAY_NAME
        };
        try (Cursor c = cr.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                proj,
                null,
                null,
                MediaStore.Audio.Media.DATE_ADDED + " DESC")) {
            while (c != null && c.moveToNext() && localPlaylist.size() < 100) {
                long id = c.getLong(0);
                String title = c.getString(1);
                String name = c.getString(2);
                String lowerName = name != null ? name.toLowerCase(Locale.ROOT) : "";
                if (!lowerName.isEmpty() && !(lowerName.endsWith(".mp3") || lowerName.endsWith(".m4a") || lowerName.endsWith(".wav") || lowerName.endsWith(".ogg"))) {
                    continue;
                }
                String label = (title != null && !title.trim().isEmpty())
                        ? title.trim()
                        : (name != null ? name.replaceAll("\\.[^.]+$", "") : "Musik Lokal");
                Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                localPlaylist.add(new AudioTrack(uri, label));
            }
        } catch (Exception e) {
            return "Gagal memindai berkas musik di penyimpanan HP.";
        }

        if (localPlaylist.isEmpty()) {
            return "Tidak ditemukan berkas audio mp3, m4a, atau wav di penyimpanan HP.";
        }
        localIndex = 0;
        return startTrackAt(context.getApplicationContext(), speaker, 0);
    }

    private static synchronized String startTrackAt(Context appCtx, Speaker speaker, int index) {
        if (localPlaylist.isEmpty()) return "Daftar putar lokal kosong.";
        localIndex = ((index % localPlaylist.size()) + localPlaylist.size()) % localPlaylist.size();
        AudioTrack track = localPlaylist.get(localIndex);
        releaseLocalPlayerLocked();
        try {
            localPlayer = new MediaPlayer();
            localPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            localPlayer.setDataSource(appCtx, track.uri);
            localPlayer.setOnCompletionListener(mp -> {
                synchronized (SystemUtilityHelper.class) {
                    if (!localPlaylist.isEmpty()) {
                        startTrackAt(appCtx, speaker, localIndex + 1);
                    }
                }
            });
            localPlayer.prepare();
            localPlayer.start();
            return "Memutar musik lokal: " + track.title + ".";
        } catch (Exception e) {
            releaseLocalPlayerLocked();
            return "Gagal memutar berkas audio " + track.title + ".";
        }
    }

    public static synchronized String pauseOrResumeMusic(Context context, boolean pause) {
        if (localPlayer != null) {
            try {
                if (pause && localPlayer.isPlaying()) {
                    localPlayer.pause();
                    return "Musik lokal dijeda.";
                } else if (!pause && !localPlayer.isPlaying()) {
                    localPlayer.start();
                    return "Melanjutkan musik lokal.";
                }
            } catch (Exception ignored) {}
        }
        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        return pause ? "Perintah jeda musik dikirim." : "Perintah lanjut putar musik dikirim.";
    }

    public static synchronized String nextTrack(Context context, Speaker speaker) {
        if (localPlayer != null && !localPlaylist.isEmpty()) {
            return startTrackAt(context.getApplicationContext(), speaker, localIndex + 1);
        }
        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT);
        return "Beralih ke lagu berikutnya.";
    }

    public static synchronized String previousTrack(Context context, Speaker speaker) {
        if (localPlayer != null && !localPlaylist.isEmpty()) {
            return startTrackAt(context.getApplicationContext(), speaker, localIndex - 1);
        }
        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        return "Beralih ke lagu sebelumnya.";
    }

    public static synchronized String stopLocalAudio(Context context) {
        boolean wasLocal = localPlayer != null;
        releaseLocalPlayerLocked();
        localPlaylist.clear();
        if (wasLocal) return "Pemutar musik lokal dihentikan.";
        if (context != null) {
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_STOP);
        }
        return "Musik dihentikan.";
    }

    private static void releaseLocalPlayerLocked() {
        if (localPlayer != null) {
            try { localPlayer.stop(); } catch (Exception ignored) {}
            try { localPlayer.release(); } catch (Exception ignored) {}
            localPlayer = null;
        }
    }

    public static void dispatchMediaKey(Context context, int keyCode) {
        if (context == null) return;
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        long now = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
    }

    // =========================================================================
    // #75. Signal & Carrier Monitor
    // =========================================================================

    public static String buildSignalAndCarrierReport(Context context) {
        StringBuilder sb = new StringBuilder("Status sinyal dan jaringan. ");

        // 1. Operator & status seluler
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            String operator = "";
            try {
                operator = tm.getNetworkOperatorName();
            } catch (Exception ignored) {}
            if (operator == null || operator.trim().isEmpty()) {
                try {
                    operator = tm.getSimOperatorName();
                } catch (Exception ignored) {}
            }
            int simState = TelephonyManager.SIM_STATE_UNKNOWN;
            try {
                simState = tm.getSimState();
            } catch (Exception ignored) {}

            if (simState == TelephonyManager.SIM_STATE_ABSENT) {
                sb.append("Kartu SIM tidak terpasang. ");
            } else if (operator != null && !operator.trim().isEmpty()) {
                sb.append("Operator seluler: ").append(operator.trim()).append(". ");
            } else {
                sb.append("Operator seluler belum terdeteksi. ");
            }

            try {
                int dataState = tm.getDataState();
                if (dataState == TelephonyManager.DATA_CONNECTED) {
                    sb.append("Data seluler tersambung. ");
                } else if (dataState == TelephonyManager.DATA_DISCONNECTED) {
                    sb.append("Data seluler tidak aktif. ");
                }
            } catch (SecurityException ignored) {}
        }

        // 2. Status Wi-Fi dan kekuatan sinyal
        boolean wifiConnected = false;
        boolean cellConnected = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network active = cm.getActiveNetwork();
                NetworkCapabilities caps = active != null ? cm.getNetworkCapabilities(active) : null;
                if (caps != null) {
                    wifiConnected = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                    cellConnected = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                }
            }
        } catch (Exception ignored) {}

        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                if (!wm.isWifiEnabled()) {
                    sb.append("Wi-Fi sedang mati. ");
                } else {
                    WifiInfo info = wm.getConnectionInfo();
                    int rssi = info != null ? info.getRssi() : -127;
                    if (wifiConnected || (info != null && info.getNetworkId() != -1 && rssi > -120)) {
                        String ssid = info.getSSID();
                        String cleanSsid = (ssid != null && !ssid.equals("<unknown ssid>"))
                                ? ssid.replace("\"", "").trim() : "";
                        String quality = describeRssi(rssi);
                        sb.append("Wi-Fi tersambung");
                        if (!cleanSsid.isEmpty()) sb.append(" ke ").append(cleanSsid);
                        sb.append(" dengan kekuatan sinyal ").append(quality)
                                .append(" (").append(rssi).append(" dBm). ");
                    } else {
                        sb.append("Wi-Fi menyala tetapi belum tersambung ke jaringan mana pun. ");
                    }
                }
            }
        } catch (Exception ignored) {}

        if (wifiConnected) {
            sb.append("Koneksi internet aktif melalui Wi-Fi.");
        } else if (cellConnected) {
            sb.append("Koneksi internet aktif melalui data seluler.");
        } else {
            sb.append("Saat ini internet terputus.");
        }
        return sb.toString().trim();
    }

    private static String describeRssi(int rssi) {
        if (rssi >= -55) return "sangat kuat";
        if (rssi >= -67) return "kuat";
        if (rssi >= -75) return "sedang";
        return "lemah";
    }

    private static boolean containsAny(String haystack, String... needles) {
        if (haystack == null || haystack.isEmpty()) return false;
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }
}
