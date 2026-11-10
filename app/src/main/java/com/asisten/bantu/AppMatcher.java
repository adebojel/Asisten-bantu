package com.asisten.bantu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mencocokkan nama aplikasi yang diucapkan dengan daftar aplikasi terpasang. Mengenali singkatan
 * (wa, ig, fb, yt), padanan nama (peta = Maps, foto = Galeri), dan salah dengar kecil (yutub = YouTube).
 * Java murni tanpa dependensi Android, sehingga bisa diuji di komputer.
 */
public final class AppMatcher {

    public static final class App {
        public final String label;
        public final String pkg;
        public App(String label, String pkg) {
            this.label = label;
            this.pkg = pkg;
        }
    }

    public static final class Match {
        public final App app;
        public final int score;
        Match(App app, int score) {
            this.app = app;
            this.score = score;
        }
    }

    private static final int MIN_SCORE = 40;
    private static final Map<String, String[]> ALIASES = new HashMap<>();

    private static void alias(String spoken, String... targets) { ALIASES.put(spoken, targets); }

    static {
        alias("wa", "whatsapp");
        alias("w a", "whatsapp");
        alias("ig", "instagram");
        alias("fb", "facebook");
        alias("yt", "youtube");
        alias("tele", "telegram");
        alias("tg", "telegram");
        alias("surel", "gmail");
        alias("email", "gmail", "email");
        alias("peta", "maps");
        alias("galeri", "galeri", "gallery", "foto", "photos");
        alias("foto", "foto", "photos", "galeri", "gallery");
        alias("kamera", "kamera", "camera");
        alias("kalender", "kalender", "calendar");
        alias("jam", "jam", "clock");
        alias("alarm", "jam", "clock");
        alias("kontak", "kontak", "contacts");
        alias("telepon", "telepon", "phone", "dialer");
        alias("telpon", "telepon", "phone", "dialer");
        alias("pesan", "pesan", "messages");
        alias("sms", "pesan", "messages");
        alias("pengaturan", "pengaturan", "settings", "setelan");
        alias("setelan", "pengaturan", "settings", "setelan");
        alias("kalkulator", "kalkulator", "calculator");
        alias("toko aplikasi", "play store");
        alias("playstore", "play store");
        alias("browser", "chrome", "browser", "internet");
        alias("internet", "chrome", "browser", "internet");
        alias("peramban", "chrome", "browser", "internet");
        alias("musik", "musik", "music", "spotify");
        alias("catatan", "catatan", "notes", "keep");
        alias("file", "file", "files", "berkas");
        alias("berkas", "file", "files", "berkas");
        alias("unduhan", "unduhan", "downloads");
        alias("perekam suara", "perekam suara", "recorder", "voice recorder");
        alias("senter", "senter", "flashlight");
    }

    private AppMatcher() {}

    static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Hasil terurut dari yang paling cocok; kosong jika tidak ada yang cukup mirip. */
    public static List<Match> find(List<App> apps, String query, int max) {
        String q = normalize(query);
        List<Match> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        List<String> terms = new ArrayList<>();
        terms.add(q);
        String[] al = ALIASES.get(q);
        if (al != null) for (String a : al) if (!terms.contains(a)) terms.add(a);

        for (App app : apps) {
            int best = 0;
            for (int i = 0; i < terms.size(); i++) {
                int s = scoreTerm(app, terms.get(i));
                if (i > 0) s = s * 9 / 10; // padanan nilainya sedikit di bawah nama yang diucapkan langsung
                best = Math.max(best, s);
            }
            if (best >= MIN_SCORE) out.add(new Match(app, best));
        }
        Collections.sort(out, (a, b) -> {
            if (a.score != b.score) return b.score - a.score;
            if (a.app.label.length() != b.app.label.length()) return a.app.label.length() - b.app.label.length();
            return a.app.label.compareToIgnoreCase(b.app.label);
        });
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** Jelas jika ada satu pemenang yang unggul dari yang kedua; selain itu sebaiknya bertanya ke pengguna. */
    public static boolean isClear(List<Match> m) {
        if (m == null || m.isEmpty()) return false;
        if (m.get(0).score < 45) return false;
        return m.size() == 1 || m.get(0).score - m.get(1).score >= 10;
    }

    private static int scoreTerm(App app, String t) {
        String l = normalize(app.label);
        if (l.isEmpty() || t.isEmpty()) return 0;
        int score = 0;
        if (l.equals(t)) score = 100;
        else if (l.startsWith(t + " ")) score = 80;
        else if (t.length() >= 3 && l.startsWith(t)) score = 75;
        else if ((" " + l + " ").contains(" " + t + " ")) score = 70;
        else if (l.length() >= 4 && (" " + t + " ").contains(" " + l + " ")) score = 65;
        else if (t.length() >= 3 && l.contains(t)) score = 55;

        if (score < 50 && t.length() >= 3) {
            String compact = t.replace(" ", "");
            if (compact.length() >= 4 && app.pkg != null && app.pkg.toLowerCase(Locale.ROOT).contains(compact)) score = Math.max(score, 50);
        }
        if (score < 50 && t.length() >= 4) {
            if (close(l, t)) score = Math.max(score, 50);
            else if (l.contains(" ")) {
                for (String w : l.split(" ")) if (w.length() >= 4 && close(w, t)) score = Math.max(score, 40);
            }
        }
        return score;
    }

    private static boolean close(String a, String b) {
        int longest = Math.max(a.length(), b.length());
        int allowed = longest >= 7 ? 2 : (longest >= 5 ? 1 : 0);
        return allowed > 0 && distance(a, b) <= allowed;
    }

    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }
}
