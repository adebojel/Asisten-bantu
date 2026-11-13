package com.asisten.bantu;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.StatFs;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.util.Locale;

/**
 * Layar utama setup dan pusat kontrol Kobra OS dengan desain gelap modern,
 * kartu ringkasan Hero, proteksi Crash-Guard AI lokal, dan 4 kelompok menu navigasi.
 */
public class MainActivity extends AppCompatActivity {

    private ScrollView mainScroll;
    private TextView tvStatus, tvPillVoice, tvPillAi, tvPillSignal, tvQuickMeta, tvPedometerCardSub;
    private Button btnSpeakKobra, btnResetAiSafeMode;
    private Button tabMain, tabSetup, tabUtils, tabVoice;
    private LinearLayout sectionMain, sectionSetup, sectionUtils, sectionVoice;
    private int activeTab = 0;

    private Speaker speaker;
    private final KobraScreenHelper screenHelper = new KobraScreenHelper();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("KOBRA OS • ASISTEN PINTAR");
        }

        speaker = Speaker.get(this);

        mainScroll = findViewById(R.id.mainScroll);
        tvStatus = findViewById(R.id.tvStatus);
        tvPillVoice = findViewById(R.id.tvPillVoice);
        tvPillAi = findViewById(R.id.tvPillAi);
        tvPillSignal = findViewById(R.id.tvPillSignal);
        tvQuickMeta = findViewById(R.id.tvQuickMeta);
        tvPedometerCardSub = findViewById(R.id.tvPedometerCardSub);

        btnSpeakKobra = findViewById(R.id.btnSpeakKobra);
        btnResetAiSafeMode = findViewById(R.id.btnResetAiSafeMode);

        tabMain = findViewById(R.id.tabMain);
        tabSetup = findViewById(R.id.tabSetup);
        tabUtils = findViewById(R.id.tabUtils);
        tabVoice = findViewById(R.id.tabVoice);

        sectionMain = findViewById(R.id.sectionMain);
        sectionSetup = findViewById(R.id.sectionSetup);
        sectionUtils = findViewById(R.id.sectionUtils);
        sectionVoice = findViewById(R.id.sectionVoice);

        // Hero Actions
        btnSpeakKobra.setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            if (AsistenAccessibilityService.getInstance() == null) {
                speaker.speak("Layanan Aksesibilitas Kobra belum aktif. Silakan aktifkan di tab Persiapan nomor dua.");
                selectTab(1);
            } else {
                AsistenAccessibilityService.requestListen();
            }
        });
        btnResetAiSafeMode.setOnClickListener(v -> {
            HapticFeedback.vibrateWarning(this);
            showSafeModeDialog();
        });

        // 4 Tab Navigasi Bawah
        tabMain.setOnClickListener(v -> selectTab(0));
        tabSetup.setOnClickListener(v -> selectTab(1));
        tabUtils.setOnClickListener(v -> selectTab(2));
        tabVoice.setOnClickListener(v -> selectTab(3));

        // Menu 1 — Utama (2x2 Grid Kartu Besar)
        findViewById(R.id.btnSecondEyeMenu).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            openSecondEyeChooser();
        });
        findViewById(R.id.btnStickCompass).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            openStickAndCompassChooser();
        });
        findViewById(R.id.btnAiChat).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(this, AiChatActivity.class));
        });
        findViewById(R.id.btnOpenKobraHome).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(this, KobraLauncherActivity.class));
        });

        // Menu 2 — Persiapan (8 Pengaturan Sistem)
        findViewById(R.id.btnPermissions).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            ActivityCompat.requestPermissions(this, Perms.required(), 100);
        });
        findViewById(R.id.btnAccessibility).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            toast("Cari Kobra OS di daftar, lalu aktifkan.");
        });
        findViewById(R.id.btnNotifAccess).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            toast("Cari Kobra OS di daftar, lalu aktifkan.");
        });
        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(this, SettingsActivity.class));
        });
        findViewById(R.id.btnSos).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(this, SosSetupActivity.class));
        });
        findViewById(R.id.btnBattery).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            askBatteryExemption();
        });
        findViewById(R.id.btnStartService).setOnClickListener(v -> {
            HapticFeedback.vibrateSuccess(this);
            KobraForegroundService.start(this);
            toast("Kobra berjalan di latar belakang.");
            speaker.speak("Layanan latar belakang Kobra diaktifkan.");
        });
        findViewById(R.id.btnLauncher).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        });

        // Menu 3 — Utilitas & Kesehatan
        findViewById(R.id.btnDocumentReader).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(this, DocumentReaderActivity.class));
        });
        findViewById(R.id.btnPedometer).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            KobraConfig cfg = new KobraConfig(this);
            String report = "Hari ini Anda telah berjalan " + cfg.getTodaySteps()
                    + " langkah, perkiraan jarak " + cfg.getTodayDistanceMeters() + " meter.";
            speaker.speak(report);
            new AlertDialog.Builder(this)
                    .setTitle("Penghitung Langkah Offline")
                    .setMessage(report)
                    .setPositiveButton("Tutup", null)
                    .show();
        });
        findViewById(R.id.btnProductivity).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(this, ProductivityActivity.class));
        });
        findViewById(R.id.btnCheckFinance).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            String tx = new KobraConfig(this).getLastFinancialTx();
            String msg = tx.isEmpty()
                    ? "Belum ada mutasi atau transaksi keuangan yang tercatat dari notifikasi bank atau dompet digital."
                    : "Transaksi keuangan terakhir: " + tx;
            speaker.speak(msg);
            new AlertDialog.Builder(this)
                    .setTitle("Mutasi Keuangan Terakhir")
                    .setMessage(msg)
                    .setPositiveButton("Tutup", null)
                    .show();
        });
        findViewById(R.id.btnCheckSignal).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            String report = SystemUtilityHelper.buildSignalAndCarrierReport(this);
            speaker.speak(report);
            new AlertDialog.Builder(this)
                    .setTitle("Status Sinyal & Operator")
                    .setMessage(report)
                    .setPositiveButton("Tutup", null)
                    .show();
        });
        findViewById(R.id.btnMedicalCard).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(this, MedicalCardActivity.class));
        });
        findViewById(R.id.btnCompass).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(this, CompassActivity.class));
        });
        findViewById(R.id.btnPrayer).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(this, PrayerActivity.class));
        });
        findViewById(R.id.btnCaregiver).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(this, CaregiverActivity.class));
        });

        // Menu 4 — Panduan Suara
        findViewById(R.id.btnVoiceGuide).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            speakAppGuide();
        });
        bindVoiceExample(R.id.cmdNav1, "Contoh perintah navigasi: Kobra, klik Kirim. Atau, Kobra, tekan Pengaturan.");
        bindVoiceExample(R.id.cmdNav2, "Contoh perintah mengetik: Kobra, ketik Saya sedang di jalan.");
        bindVoiceExample(R.id.cmdNav3, "Contoh perintah layar: Kobra, geser ke bawah. Kobra, baca layar. Atau, Kobra, kembali.");
        bindVoiceExample(R.id.cmdMedia1, "Contoh perintah YouTube: Kobra, putar murottal merdu di YouTube.");
        bindVoiceExample(R.id.cmdMedia2, "Contoh perintah pemutar lokal: Kobra, putar musik lokal.");
        bindVoiceExample(R.id.cmdMedia3, "Contoh kontrol musik: Kobra, jeda musik. Kobra, lanjut putar. Atau, lagu berikutnya.");
        bindVoiceExample(R.id.cmdCam1, "Contoh perintah uang: Kobra, hitung lembar uang. Atau, Kobra, baca uang.");
        bindVoiceExample(R.id.cmdCam2, "Contoh perintah kamera: Kobra, cocokkan baju. Atau, Kobra, cari kursi.");
        bindVoiceExample(R.id.cmdCam3, "Contoh pembaca layar dan struk: Kobra, baca layar digital. Atau, pindai struk transfer.");
        bindVoiceExample(R.id.cmdDaily1, "Contoh perintah darurat: Kobra, tolong. Bunyikan sirine bahaya. Atau, latihan SOS.");
        bindVoiceExample(R.id.cmdDaily2, "Contoh pedometer: Kobra, berapa langkah saya hari ini?");
        bindVoiceExample(R.id.cmdDaily3, "Contoh agenda harian: Kobra, ingatkan jam 16.00 minum vitamin.");
        bindVoiceExample(R.id.cmdDaily4, "Contoh keuangan dan sinyal: Kobra, cek transaksi terakhir. Atau, Kobra, cek sinyal.");

        selectTab(0);
    }

    private void bindVoiceExample(int viewId, String speech) {
        View v = findViewById(viewId);
        if (v != null) {
            v.setOnClickListener(item -> {
                HapticFeedback.vibrateAction(this);
                speaker.speak(speech);
            });
        }
    }

    private void selectTab(int index) {
        activeTab = index;
        HapticFeedback.vibrateAction(this);
        sectionMain.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        sectionSetup.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        sectionUtils.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        sectionVoice.setVisibility(index == 3 ? View.VISIBLE : View.GONE);

        styleTab(tabMain, index == 0);
        styleTab(tabSetup, index == 1);
        styleTab(tabUtils, index == 2);
        styleTab(tabVoice, index == 3);

        if (mainScroll != null) {
            mainScroll.post(() -> mainScroll.scrollTo(0, 0));
        }
    }

    private void styleTab(Button tab, boolean selected) {
        tab.setBackgroundResource(selected ? R.drawable.bg_tab_active : R.drawable.bg_tab_inactive);
        tab.setTextColor(selected ? 0xFF10B981 : 0xFF94A3B8);
    }

    private void showSafeModeDialog() {
        LocalLlmEngine llm = LocalLlmEngine.getInstance(this);
        String reason = llm.getLastError().isEmpty()
                ? LocalLlmEngine.CRASH_SAFE_MODE_REASON
                : llm.getLastError();
        new AlertDialog.Builder(this)
                .setTitle("Mode Aman AI Offline")
                .setMessage(reason + "\n\nPilih tindakan untuk mengelola model lokal:")
                .setPositiveButton("Hapus Model Lokal", (d, w) -> {
                    boolean deleted = llm.deleteLocalModel();
                    showStatus();
                    String msg = deleted
                            ? "Model lokal dihapus dan Mode Aman berhasil direset."
                            : "Gagal menghapus berkas model.";
                    toast(msg);
                    speaker.speak(msg);
                })
                .setNeutralButton("Reset & Buka Pengaturan", (d, w) -> {
                    new KobraConfig(this).clearLlmSafeMode();
                    startActivity(new Intent(this, SettingsActivity.class));
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    private void openStickAndCompassChooser() {
        final String[] labels = {
                "🦮 Mode Tongkat (Deteksi Halangan Kamera)",
                "🧭 Kompas Suara, Lokasi GPS & Rute Pulang",
                "🕌 Jadwal Sholat & Kiblat Offline"
        };
        speaker.speak("Pilih mode navigasi.");
        new AlertDialog.Builder(this)
                .setTitle("Mode Tongkat & Navigasi")
                .setItems(labels, (d, which) -> {
                    if (which == 0) startActivity(new Intent(this, NavigationActivity.class));
                    else if (which == 1) startActivity(new Intent(this, CompassActivity.class));
                    else startActivity(new Intent(this, PrayerActivity.class));
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void openSecondEyeChooser() {
        final String[] labels = {
                "💵 Penghitung Lembar Uang Tunai (jumlah otomatis)",
                "💵 Baca Uang Satuan",
                "👔 Padu Padan Warna Pakaian",
                "📟 Pembaca Layar Digital Real-Time (ATM, timbangan)",
                "🧾 Pemindai Struk & Screenshot Transfer Bank",
                "📦 Pemindai Barcode & QR Code Offline",
                "🪑 Cari Objek Sekitar (kursi, pintu, bip kamera)",
                "🌐 Terjemahkan Teks Kamera (Offline)",
                "🪪 Baca KTP & KK Offline",
                "👥 Hitung Orang di Depan",
                "👁️ Jelaskan Sekitar"
        };
        final String[] modes = {
                "money_count", "money", "outfit", "digital_screen",
                "receipt", "barcode", "find_object", "translate_ocr",
                "ktp", "face", "describe"
        };
        speaker.speak("Pilih mode Mata Kedua yang ingin digunakan.");
        new AlertDialog.Builder(this)
                .setTitle("Pilih Mode Mata Kedua")
                .setItems(labels, (dialog, which) -> {
                    Intent intent = new Intent(this, VisionActivity.class);
                    intent.putExtra("mode", modes[which]);
                    startActivity(intent);
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (screenHelper.handleKeyEvent(this, event, speaker)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (screenHelper.handleTouchEvent(this, ev, speaker)) return true;
        return super.dispatchTouchEvent(ev);
    }

    @Override protected void onResume() {
        super.onResume();
        showStatus();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        showStatus();
    }

    private void speakAppGuide() {
        String guide = "Panduan Suara Kobra OS. Layar utama terbagi menjadi empat tab di bagian bawah: Utama, Persiapan, Utilitas, dan Panduan. "
                + "Pada tab Utama tersedia empat kartu besar: Mata Kedua, Tongkat dan Kompas, Teman Ngobrol AI, dan Layar Utama Kobra. "
                + "Pada tab Persiapan tersedia delapan pengaturan sistem mulai dari izin, aksesibilitas, notifikasi, pengaturan AI, kontak darurat SOS, baterai, layanan latar belakang, hingga mode launcher. "
                + "Pada tab Utilitas tersedia Pembaca Dokumen PDF, Penghitung Langkah, Agenda dan Obat, Mutasi Keuangan Terakhir, Cek Sinyal dan Operator, serta Kartu Medis Darurat. "
                + "Anda juga bisa mengusap dua jari ke bawah untuk membaca notifikasi, usap dua jari ke atas untuk menghentikan suara, atau ketuk dua kali di mana saja untuk mulai bicara.";
        speaker.speak(guide);
    }

    private void showStatus() {
        KobraConfig c = new KobraConfig(this);
        LocalLlmEngine llm = LocalLlmEngine.getInstance(this);

        boolean micOk = Perms.has(this, Manifest.permission.RECORD_AUDIO);
        boolean camOk = Perms.has(this, Manifest.permission.CAMERA);
        boolean accOk = AsistenAccessibilityService.getInstance() != null;
        boolean notifOk = KobraNotificationService.isConnected();

        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int bat = bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
        boolean charging = bm != null && bm.isCharging();

        StatFs stat = new StatFs(getFilesDir().getAbsolutePath());
        long freeMb = stat.getAvailableBytes() / (1024L * 1024L);
        String freeText = freeMb >= 1024
                ? String.format(Locale.US, "%.1f GB", freeMb / 1024.0)
                : freeMb + " MB";

        // Pill 1: Suara & Izin
        if (micOk && camOk && accOk) {
            tvPillVoice.setText("🎤 Suara Siap");
            tvPillVoice.setTextColor(0xFF10B981);
        } else {
            tvPillVoice.setText("🎤 Perlu Izin");
            tvPillVoice.setTextColor(0xFFF59E0B);
        }

        // Pill 2: Otak AI Offline / Online
        boolean crashSafe = c.isLlmSafeMode() || (llm.isModelPresent() && llm.isSafeMode());
        if (llm.isInitialized()) {
            tvPillAi.setText("🧠 AI Aktif");
            tvPillAi.setTextColor(0xFF10B981);
        } else if (crashSafe) {
            tvPillAi.setText("⚠️ Mode Aman");
            tvPillAi.setTextColor(0xFFF59E0B);
        } else if (llm.isModelPresent()) {
            tvPillAi.setText("🧠 AI Siap");
            tvPillAi.setTextColor(0xFF38BDF8);
        } else if (c.hasAi()) {
            tvPillAi.setText("☁️ AI Online");
            tvPillAi.setTextColor(0xFF38BDF8);
        } else {
            tvPillAi.setText("🧠 Mode Lokal");
            tvPillAi.setTextColor(0xFF94A3B8);
        }

        // Pill 3: Baterai & Sinyal
        String netLabel = NetworkState.isOnline(this) ? NetworkState.connectionType(this) : "Offline";
        String batLabel = bat >= 0 ? bat + "%" + (charging ? "⚡" : "") : "--";
        tvPillSignal.setText("🔋 " + batLabel + " • " + netLabel);
        tvPillSignal.setTextColor(bat >= 0 && bat < 20 && !charging ? 0xFFEF4444 : 0xFFF8FAFC);

        // Ringkasan 1 baris Hero + status crash guard bila aktif
        if (crashSafe) {
            String err = llm.getLastError().isEmpty() ? LocalLlmEngine.CRASH_SAFE_MODE_REASON : llm.getLastError();
            tvStatus.setText(err);
            tvStatus.setTextColor(0xFFFBBF24);
            btnResetAiSafeMode.setVisibility(View.VISIBLE);
        } else {
            String aiLabel = llm.isInitialized() ? "AI Lokal Aktif"
                    : (llm.isModelPresent() ? "AI Lokal Lazy-Load" : (c.hasAi() ? "AI Online Siap" : "Perintah Lokal"));
            tvStatus.setText(aiLabel + " • Notif: " + (notifOk ? "Aktif" : "Mati") + " • Sisa ruang: " + freeText);
            tvStatus.setTextColor(0xFFE2E8F0);
            btnResetAiSafeMode.setVisibility(View.GONE);
        }

        String sosText = c.hasSosContact() ? "Kontak SOS diatur" : "Belum diatur (Default: 112)";
        String privText = c.isPrivacyMode() ? "Mode Privasi AKTIF" : "Privasi Normal";
        if (tvQuickMeta != null) {
            tvQuickMeta.setText("SOS: " + sosText + " • " + privText + " • Gaya: " + c.getAssistantStyleLabel());
        }
        if (tvPedometerCardSub != null) {
            tvPedometerCardSub.setText(c.getTodaySteps() + " langkah (" + c.getTodayDistanceMeters() + " m)");
        }
    }

    private void askBatteryExemption() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            toast("Kobra sudah bebas dari pembatasan baterai.");
            return;
        }
        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
