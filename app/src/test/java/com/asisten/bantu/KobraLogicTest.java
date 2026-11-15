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
    public void testLiveCameraAnalysisModes() {
        assertTrue(VisionActivity.supportsLiveAnalysisMode("live_companion"));
        assertTrue(VisionActivity.supportsLiveAnalysisMode("digital_screen"));
        assertTrue(VisionActivity.supportsLiveAnalysisMode("barcode"));
        assertTrue(VisionActivity.supportsLiveAnalysisMode("find_object"));
        assertEquals(false, VisionActivity.supportsLiveAnalysisMode("describe"));
        assertEquals(false, VisionActivity.supportsLiveAnalysisMode(""));
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

    @Test
    public void testVoiceSilenceCommandParser() {
        assertTrue(VoiceSilenceCommandParser.isSilenceCommand("diam dulu"));
        assertTrue(VoiceSilenceCommandParser.isSilenceCommand("tolong jangan bicara"));
        assertTrue(VoiceSilenceCommandParser.isSilenceCommand("MODE DIAM!"));
        assertTrue(VoiceSilenceCommandParser.isSilenceCommand("matikan asisten sekarang"));
        assertTrue(VoiceSilenceCommandParser.isWakeActivationCommand("aktifkan"));
        assertTrue(VoiceSilenceCommandParser.isWakeActivationCommand("bangun dan dengarkan"));
        assertTrue(VoiceSilenceCommandParser.isWakeActivationCommand("saya panggil"));
        assertTrue(!VoiceSilenceCommandParser.isSilenceCommand("diameter benda ini"));
        assertTrue(!VoiceSilenceCommandParser.isSilenceCommand("putar musik"));
        assertTrue(!VoiceSilenceCommandParser.isWakeActivationCommand("aktifitas harian"));
        assertTrue(!VoiceSilenceCommandParser.isWakeActivationCommand(null));
    }

    @Test
    public void testVoiceAccessIndonesianNumberTargets() {
        assertEquals(Integer.valueOf(1), OfflineBrain.firstNumber("klik nomor satu"));
        assertEquals(Integer.valueOf(10), OfflineBrain.firstNumber("klik nomor sepuluh"));
        assertEquals(Integer.valueOf(11), OfflineBrain.firstNumber("klik nomor sebelas"));
        assertEquals(Integer.valueOf(32), OfflineBrain.firstNumber("klik nomor tiga puluh dua"));
        assertEquals(Integer.valueOf(60), OfflineBrain.firstNumber("klik nomor enam puluh"));
    }

    @Test
    public void testAdaptiveTorchLuxThresholds() {
        assertTrue(VisionActivity.shouldEnableAutoTorch(0.5f));
        assertTrue(VisionActivity.shouldEnableAutoTorch(11.9f));
        assertTrue(!VisionActivity.shouldEnableAutoTorch(12.0f));
        assertTrue(!VisionActivity.shouldEnableAutoTorch(30.0f));
        assertTrue(!VisionActivity.shouldDisableAutoTorch(25.0f));
        assertTrue(VisionActivity.shouldDisableAutoTorch(25.1f));
        assertTrue(!VisionActivity.shouldDisableAutoTorch(-1.0f));
    }

    @Test
    public void testWhatsAppPackageDetection() {
        assertTrue(com.asisten.bantu.voiceaccess.NodeIndexer.isWhatsAppPackage("com.whatsapp"));
        assertTrue(com.asisten.bantu.voiceaccess.NodeIndexer.isWhatsAppPackage("com.whatsapp.w4b"));
        assertTrue(!com.asisten.bantu.voiceaccess.NodeIndexer.isWhatsAppPackage("com.android.messages"));
        assertTrue(!com.asisten.bantu.voiceaccess.NodeIndexer.isWhatsAppPackage(null));
    }

    @Test
    public void testVisibleBalanceExtractionRequiresBalanceLabel() {
        assertTrue(OfflineBrain.visibleBalanceForSpeech("Saldo Anda Rp 1.250.000").contains("Rp 1.250.000"));
        assertTrue(OfflineBrain.visibleBalanceForSpeech("Available balance: IDR 500,000").contains("IDR 500,000"));
        assertEquals("", OfflineBrain.visibleBalanceForSpeech("OTP 123456, rekening 1234567890"));
        assertEquals("", OfflineBrain.visibleBalanceForSpeech("Saldo sedang dimuat"));
        assertEquals("", OfflineBrain.visibleBalanceForSpeech(null));
    }

    @Test
    public void testVoiceScreenTargetLabelMatchingDoesNotGuessAmbiguousItems() {
        java.util.List<com.asisten.bantu.voiceaccess.NodeIndexer.IndexedNode> nodes =
                new java.util.ArrayList<>();
        nodes.add(new com.asisten.bantu.voiceaccess.NodeIndexer.IndexedNode(
                1, "Putar video", 100, 100, new android.graphics.Rect(50, 50, 150, 150),
                false, false, false, false, "", "TextView"));
        nodes.add(new com.asisten.bantu.voiceaccess.NodeIndexer.IndexedNode(
                2, "Putar berikutnya", 200, 100, new android.graphics.Rect(150, 50, 250, 150),
                false, false, false, false, "", "TextView"));
        assertNull(com.asisten.bantu.voiceaccess.NodeIndexer.findByLabel(nodes, "putar"));
        assertEquals("Putar video",
                com.asisten.bantu.voiceaccess.NodeIndexer.findByLabel(nodes, "putar video").getLabel());
        assertEquals("Putar berikutnya",
                com.asisten.bantu.voiceaccess.NodeIndexer.findByLabel(nodes, "berikutnya").getLabel());
        nodes.add(new com.asisten.bantu.voiceaccess.NodeIndexer.IndexedNode(
                3, "Putar video", 300, 100, new android.graphics.Rect(250, 50, 350, 150),
                false, false, false, false, "", "TextView"));
        assertNull(com.asisten.bantu.voiceaccess.NodeIndexer.findByLabel(nodes, "putar video"));
    }

    @Test
    public void testSpeechInterruptCommandParser() {
        assertTrue(VoiceSilenceCommandParser.isSpeechInterruptCommand("stop"));
        assertTrue(VoiceSilenceCommandParser.isSpeechInterruptCommand("Kobra berhenti bicara sekarang"));
        assertTrue(VoiceSilenceCommandParser.isSpeechInterruptCommand("hentikan bacaan"));
        assertTrue(VoiceSilenceCommandParser.isSpeechInterruptCommand("jeda suara"));
        assertTrue(!VoiceSilenceCommandParser.isSpeechInterruptCommand("desktop terbuka"));
        assertTrue(!VoiceSilenceCommandParser.isSpeechInterruptCommand("stop video"));
        assertTrue(!VoiceSilenceCommandParser.isSpeechInterruptCommand("hentikan video"));
        assertTrue(!VoiceSilenceCommandParser.isSpeechInterruptCommand("stop musik"));
        assertTrue(!VoiceSilenceCommandParser.isSpeechInterruptCommand("baca layar"));
    }

    @Test
    public void testCallStatusParserDoesNotGuess() {
        assertEquals(CallStatusParser.Status.RINGING, CallStatusParser.parse("Memanggil..."));
        assertEquals(CallStatusParser.Status.RINGING, CallStatusParser.parse("Ringing"));
        assertEquals(CallStatusParser.Status.CONNECTED, CallStatusParser.parse("Panggilan terhubung"));
        assertEquals(CallStatusParser.Status.ENDED, CallStatusParser.parse("Panggilan berakhir"));
        assertEquals(CallStatusParser.Status.ENDED, CallStatusParser.parse("No answer"));
        assertEquals(CallStatusParser.Status.UNKNOWN, CallStatusParser.parse("Nomor sudah dipilih"));
        assertEquals(CallStatusParser.Status.UNKNOWN, CallStatusParser.parse(null));
    }

    @Test
    public void testImmediateBargeInRequiresUnambiguousPhrase() {
        assertTrue(VoiceSilenceCommandParser.isImmediateSpeechInterruptCommand("Kobra berhenti bicara"));
        assertTrue(VoiceSilenceCommandParser.isImmediateSpeechInterruptCommand("hentikan bacaan"));
        assertTrue(VoiceSilenceCommandParser.isImmediateSpeechInterruptCommand("diam dulu"));
        assertTrue(!VoiceSilenceCommandParser.isImmediateSpeechInterruptCommand("stop"));
        assertTrue(!VoiceSilenceCommandParser.isImmediateSpeechInterruptCommand("stop video"));
        assertTrue(!VoiceSilenceCommandParser.isImmediateSpeechInterruptCommand("hentikan video"));
    }

    @Test
    public void testExplicitCancelDoesNotMatchNormalMessageText() {
        assertTrue(VoiceSilenceCommandParser.isExplicitCancelCommand("tidak"));
        assertTrue(VoiceSilenceCommandParser.isExplicitCancelCommand("jangan kirim"));
        assertTrue(VoiceSilenceCommandParser.isExplicitCancelCommand("tidak jadi"));
        assertTrue(!VoiceSilenceCommandParser.isExplicitCancelCommand("saya tidak bisa datang besok"));
        assertTrue(!VoiceSilenceCommandParser.isExplicitCancelCommand("jangan lupa bawa dokumen"));
        assertTrue(!VoiceSilenceCommandParser.isExplicitCancelCommand("saya batal berangkat kemarin"));
    }

    @Test
    public void testPersistentSilenceIsSeparateFromOneShotStop() {
        assertTrue(VoiceSilenceCommandParser.isSilenceCommand("diam dulu"));
        assertTrue(VoiceSilenceCommandParser.isSilenceCommand("Kobra diam"));
        assertTrue(!VoiceSilenceCommandParser.isSilenceCommand("berhenti bicara"));
    }


    private static String pick(List<AppMatcher.App> apps, String q) {
        List<AppMatcher.Match> m = AppMatcher.find(apps, q, 4);
        if (m.isEmpty()) return "-";
        return AppMatcher.isClear(m) ? m.get(0).app.label : "?";
    }
}
