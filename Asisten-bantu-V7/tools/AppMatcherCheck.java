package com.asisten.bantu;

import java.util.ArrayList;
import java.util.List;

/** Pengujian logika murni AppMatcher dan PhoneFormat. */
public class AppMatcherCheck {
    static int fail = 0, pass = 0;

    static void eq(String name, Object actual, Object expected) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) pass++; else { fail++; System.out.println("GAGAL " + name + ": dapat <" + actual + ">, harapan <" + expected + ">"); }
    }

    static List<AppMatcher.App> apps() {
        String[][] a = {
            {"Kamera", "com.sec.android.app.camera"}, {"Galeri", "com.sec.android.gallery3d"}, {"Pesan", "com.samsung.android.messaging"},
            {"Telepon", "com.samsung.android.dialer"}, {"Kontak", "com.samsung.android.contacts"}, {"WhatsApp", "com.whatsapp"},
            {"WhatsApp Business", "com.whatsapp.w4b"}, {"Instagram", "com.instagram.android"}, {"Facebook", "com.facebook.katana"},
            {"Messenger", "com.facebook.orca"}, {"Telegram", "org.telegram.messenger"}, {"YouTube", "com.google.android.youtube"},
            {"YouTube Music", "com.google.android.apps.youtube.music"}, {"Google Maps", "com.google.android.apps.maps"},
            {"Chrome", "com.android.chrome"}, {"Gmail", "com.google.android.gm"}, {"Play Store", "com.android.vending"},
            {"Pengaturan", "com.android.settings"}, {"Jam", "com.sec.android.app.clockpackage"}, {"Kalender", "com.samsung.android.calendar"},
            {"Kalkulator", "com.sec.android.app.popupcalculator"}, {"Gojek", "com.gojek.app"}, {"Grab", "com.grabtaxi.passenger"},
            {"Shopee", "com.shopee.id"}, {"Tokopedia", "com.tokopedia.tkpd"}, {"DANA", "id.dana"}, {"Google Foto", "com.google.android.apps.photos"},
            {"Google Play Games", "com.google.android.play.games"}
        };
        List<AppMatcher.App> l = new ArrayList<>();
        for (String[] x : a) l.add(new AppMatcher.App(x[0], x[1]));
        return l;
    }

    /** Label pemenang jika jelas, "?" jika ambigu, "-" jika tidak ada. */
    static String pick(String q) {
        List<AppMatcher.Match> m = AppMatcher.find(apps(), q, 4);
        if (m.isEmpty()) return "-";
        return AppMatcher.isClear(m) ? m.get(0).app.label : "?";
    }

    public static void main(String[] args) {
        eq("whatsapp", pick("whatsapp"), "WhatsApp");
        eq("WhatsApp huruf besar", pick("WhatsApp"), "WhatsApp");
        eq("wa", pick("wa"), "WhatsApp");
        eq("whatsapp bisnis", pick("whatsapp business"), "WhatsApp Business");
        eq("ig", pick("ig"), "Instagram");
        eq("fb", pick("fb"), "Facebook");
        eq("insta", pick("insta"), "Instagram");
        eq("tele", pick("tele"), "Telegram");
        eq("yt", pick("yt"), "YouTube");
        eq("salah dengar yutub", pick("yutub"), "YouTube");
        eq("youtube music", pick("youtube music"), "YouTube Music");
        eq("maps", pick("maps"), "Google Maps");
        eq("peta", pick("peta"), "Google Maps");
        eq("google maps", pick("google maps"), "Google Maps");
        eq("foto tanpa aplikasi Foto, padanan Galeri", pick("foto"), "Galeri");
        List<AppMatcher.App> withFoto = apps();
        withFoto.add(new AppMatcher.App("Foto", "com.example.foto"));
        List<AppMatcher.Match> fm = AppMatcher.find(withFoto, "foto", 4);
        eq("foto dengan aplikasi Foto", AppMatcher.isClear(fm) ? fm.get(0).app.label : "?", "Foto");
        eq("galeri", pick("galeri"), "Galeri");
        eq("kamera", pick("kamera"), "Kamera");
        eq("telepon", pick("telepon"), "Telepon");
        eq("pesan", pick("pesan"), "Pesan");
        eq("kontak", pick("kontak"), "Kontak");
        eq("jam", pick("jam"), "Jam");
        eq("kalkulator", pick("kalkulator"), "Kalkulator");
        eq("toko aplikasi", pick("toko aplikasi"), "Play Store");
        eq("browser", pick("browser"), "Chrome");
        eq("setelan", pick("setelan"), "Pengaturan");
        eq("dana", pick("dana"), "DANA");
        eq("gojek", pick("gojek"), "Gojek");
        eq("google ambigu", pick("google"), "?");
        eq("tidak ada", pick("xyzabc"), "-");
        eq("kosong", pick("  "), "-");

        eq("nomor 0812", PhoneFormat.toInternational("0812-3456-7890"), "6281234567890");
        eq("nomor +62", PhoneFormat.toInternational("+62 812 3456 7890"), "6281234567890");
        eq("nomor 62", PhoneFormat.toInternational("6281234567890"), "6281234567890");
        eq("nomor 8", PhoneFormat.toInternational("81234567890"), "6281234567890");
        eq("nomor asing", PhoneFormat.toInternational("+1 (415) 555-0100"), "14155550100");
        eq("nomor 00", PhoneFormat.toInternational("0062 812 3456"), "628123456");
        eq("nomor kosong", PhoneFormat.toInternational(""), "");
        eq("nomor null", PhoneFormat.toInternational(null), "");

        System.out.println("Lulus: " + pass + ", gagal: " + fail);
        if (fail > 0) System.exit(1);
    }
}
