package com.asisten.bantu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class KobraLogicTest {

    @Test
    public void testOfflineBrain() throws Exception {
        String kbPath = "src/main/assets/kobra_kb.txt";
        if (!Files.exists(Paths.get(kbPath))) {
            kbPath = "app/src/main/assets/kobra_kb.txt";
        }
        OfflineBrain b = OfflineBrain.fromText(new String(Files.readAllBytes(Paths.get(kbPath)), StandardCharsets.UTF_8));

        assertEquals("Hasilnya 8.", b.answer("berapa 5 tambah 3"));
        assertEquals("Hasilnya 100.", b.answer("dua puluh lima kali empat"));
        assertEquals("Hasilnya 25.", b.answer("100 dibagi 4"));
        assertEquals("Tidak bisa dibagi dengan nol.", b.answer("10 dibagi 0"));
        assertEquals("Hasilnya 420.", b.answer("seratus dua puluh tambah tiga ratus"));
        assertEquals("Hasilnya -997500.", b.answer("dua ribu lima ratus kurang satu juta"));
        assertEquals("Hasilnya 20.", b.answer("(2 + 3) x 4"));
        assertEquals("Hasilnya 14.", b.answer("2 + 3 x 4"));
        assertEquals("Hasilnya 3,5.", b.answer("7 / 2"));
        assertEquals("Hasilnya 15.", b.answer("dua belas tambah tiga"));
        assertEquals("Hasilnya 121.", b.answer("tolong hitung sebelas kali sebelas"));

        assertNull(OfflineBrain.calculate("dua kali sehari"));
        assertNull(OfflineBrain.calculate("uang saya kurang"));
        assertNull(OfflineBrain.calculate("tiga kali"));
        assertNull(OfflineBrain.calculate("jam tujuh"));

        assertEquals(Arrays.asList(6, 30), OfflineBrain.numbers("alarm jam 6 30"));
        assertEquals(Arrays.asList(7, 20), OfflineBrain.numbers("jam tujuh dua puluh"));
        assertEquals(Integer.valueOf(5), OfflineBrain.firstNumber("timer lima menit"));
        assertEquals(Arrays.asList(6, 45), OfflineBrain.numbers("bangunkan jam 6:45"));
        assertNull(OfflineBrain.firstNumber("tekan nomor"));
        assertEquals(Integer.valueOf(12), OfflineBrain.firstNumber("jam dua belas malam"));

        assertTrue(b.answer("tolong saya tersedak").contains("lima tepukan"));
        assertTrue(b.answer("ada pendarahan hebat").contains("Tekan luka"));
        assertTrue(b.answer("tangan saya terbakar").contains("dua puluh menit"));
        assertTrue(b.answer("nomor darurat berapa").contains("112"));
        assertTrue(b.answer("aku lagi sedih banget").contains("mendengarkan"));
        assertTrue(b.answer("halo").contains("Ada yang bisa"));
        assertNull(b.answer("halo saya mau tanya tentang cuaca di jakarta besok"));
        assertNull(b.answer("siapa presiden amerika"));
        assertNull(b.answer("   "));
        assertTrue(b.answer("panduan p3k").contains("tersedak"));
    }

    @Test
    public void testPhoneFormat() {
        assertEquals("6281234567890", PhoneFormat.toInternational("0812-3456-7890"));
        assertEquals("6281234567890", PhoneFormat.toInternational("+62 812 3456 7890"));
        assertEquals("6281234567890", PhoneFormat.toInternational("6281234567890"));
        assertEquals("6281234567890", PhoneFormat.toInternational("81234567890"));
        assertEquals("14155550100", PhoneFormat.toInternational("+1 (415) 555-0100"));
        assertEquals("628123456", PhoneFormat.toInternational("0062 812 3456"));
        assertEquals("", PhoneFormat.toInternational(""));
        assertEquals("", PhoneFormat.toInternational(null));
    }

    @Test
    public void testAppMatcher() {
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
        List<AppMatcher.App> list = new ArrayList<>();
        for (String[] x : a) list.add(new AppMatcher.App(x[0], x[1]));

        assertEquals("WhatsApp", pick(list, "whatsapp"));
        assertEquals("WhatsApp", pick(list, "WhatsApp"));
        assertEquals("WhatsApp", pick(list, "wa"));
        assertEquals("WhatsApp Business", pick(list, "whatsapp business"));
        assertEquals("Instagram", pick(list, "ig"));
        assertEquals("Facebook", pick(list, "fb"));
        assertEquals("Instagram", pick(list, "insta"));
        assertEquals("Telegram", pick(list, "tele"));
        assertEquals("YouTube", pick(list, "yt"));
        assertEquals("YouTube", pick(list, "yutub"));
        assertEquals("YouTube Music", pick(list, "youtube music"));
        assertEquals("Google Maps", pick(list, "maps"));
        assertEquals("Google Maps", pick(list, "peta"));
        assertEquals("Google Maps", pick(list, "google maps"));
        assertEquals("Galeri", pick(list, "foto"));
        assertEquals("Galeri", pick(list, "galeri"));
        assertEquals("Kamera", pick(list, "kamera"));
        assertEquals("Telepon", pick(list, "telepon"));
        assertEquals("Pesan", pick(list, "pesan"));
        assertEquals("Kontak", pick(list, "kontak"));
        assertEquals("Jam", pick(list, "jam"));
        assertEquals("Kalkulator", pick(list, "kalkulator"));
        assertEquals("Play Store", pick(list, "toko aplikasi"));
        assertEquals("Chrome", pick(list, "browser"));
        assertEquals("Pengaturan", pick(list, "setelan"));
        assertEquals("DANA", pick(list, "dana"));
        assertEquals("Gojek", pick(list, "gojek"));
        assertEquals("?", pick(list, "google"));
        assertEquals("-", pick(list, "xyzabc"));
        assertEquals("-", pick(list, "  "));
    }

    private static String pick(List<AppMatcher.App> apps, String q) {
        List<AppMatcher.Match> m = AppMatcher.find(apps, q, 4);
        if (m.isEmpty()) return "-";
        return AppMatcher.isClear(m) ? m.get(0).app.label : "?";
    }
}
