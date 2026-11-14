package com.asisten.bantu;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.StatFs;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

/**
 * Cockpit HUD 3D Utama Kobra OS.
 * Ringkas, 100% bebas emoji, dengan Telemetry Strip real-time (Baterai, RAM, Jaringan),
 * Voice Orb 3D interaktif, 1 tombol Settings Terpadu di kanan atas, dan 2x2 Core Grid.
 */
public class MainActivity extends AppCompatActivity {

    private ScrollView mainScroll;
    private VoiceOrbView voiceOrbMain;
    private TextView tvMainHeaderTitle, tvStatus, tvQuickMeta, tvPedometerCardSub, tvBadgeSiaga;
    private TextView tvTelemetryBattery, tvTelemetryRam, tvTelemetryNet;
    private Button btnShowFloatingOrb, btnResetAiSafeMode;
    private Button tabMain, tabUtils, tabVoice;
    private LinearLayout sectionMain, sectionUtils, sectionVoice;
    private int currentTabIndex = 0;

    private Speaker speaker;
    private final KobraScreenHelper screenHelper = new KobraScreenHelper();
    private final Speaker.Listener orbSpeechListener = new Speaker.Listener() {
        @Override public void onSpeechStart() {
            runOnUiThread(() -> {
                if (voiceOrbMain != null) voiceOrbMain.setOrbState(VoiceOrbView.OrbState.SPEAKING);
            });
        }
        @Override public void onSpeechEnd() {
            runOnUiThread(() -> {
                if (voiceOrbMain != null) voiceOrbMain.setOrbState(VoiceOrbView.OrbState.IDLE);
            });
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WindowInsetsHelper.applySystemBarInsets(this, true);
        ThemeManager.applyTheme(this);
        speaker = Speaker.get(this);

        mainScroll = findViewById(R.id.mainScroll);
        voiceOrbMain = findViewById(R.id.voiceOrbMain);
        tvMainHeaderTitle = findViewById(R.id.tvMainHeaderTitle);
        tvStatus = findViewById(R.id.tvStatus);
        tvQuickMeta = findViewById(R.id.tvQuickMeta);
        tvPedometerCardSub = findViewById(R.id.tvPedometerCardSub);
        tvBadgeSiaga = findViewById(R.id.tvBadgeSiaga);
        tvTelemetryBattery = findViewById(R.id.tvTelemetryBattery);
        tvTelemetryRam = findViewById(R.id.tvTelemetryRam);
        tvTelemetryNet = findViewById(R.id.tvTelemetryNet);

        btnShowFloatingOrb = findViewById(R.id.btnShowFloatingOrb);
        btnResetAiSafeMode = findViewById(R.id.btnResetAiSafeMode);

        tabMain = findViewById(R.id.tabMain);
        tabUtils = findViewById(R.id.tabUtils);
        tabVoice = findViewById(R.id.tabVoice);

        sectionMain = findViewById(R.id.sectionMain);
        sectionUtils = findViewById(R.id.sectionUtils);
        sectionVoice = findViewById(R.id.sectionVoice);

        // Top-right Unified Settings Gear
        ImageButton btnSettings = findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                HapticFeedback.vibrateAction(this);
                launchScreen(new Intent(this, SettingsActivity.class), "Gagal membuka Pengaturan Terpadu.");
            });
        }

        // Hero Voice Orb + Floating Orb Summon Trigger (with Barge-In)
        View.OnClickListener listenTrigger = v -> {
            HapticFeedback.vibrateAction(this);
            KobraConfig cfg = new KobraConfig(this);
            if (cfg.isBargeInEnabled() && speaker.isSpeaking()) {
                speaker.stop();
            }
            if (AsistenAccessibilityService.getInstance() == null) {
                speaker.speak("Layanan Aksesibilitas Kobra belum aktif. Membuka Pengaturan Izin Sistem.");
                launchScreen(new Intent(this, SettingsActivity.class), "Gagal membuka Pengaturan.");
            } else {
                cfg.setFloatingOrbEnabled(true);
                AsistenAccessibilityService.showFloatingOrb();
                if (voiceOrbMain != null) voiceOrbMain.setOrbState(VoiceOrbView.OrbState.LISTENING);
                AsistenAccessibilityService.requestListen();
            }
        };
        if (voiceOrbMain != null) voiceOrbMain.setOnClickListener(listenTrigger);

        if (btnShowFloatingOrb != null) {
            btnShowFloatingOrb.setOnClickListener(v -> {
                HapticFeedback.vibrateAction(this);
                KobraConfig cfg = new KobraConfig(this);
                if (AsistenAccessibilityService.getInstance() == null) {
                    speaker.speak("Aktifkan Layanan Aksesibilitas Kobra terlebih dahulu agar gelembung melayang dapat tampil di seluruh aplikasi.");
                    launchScreen(new Intent(this, SettingsActivity.class), "Gagal membuka Pengaturan.");
                } else {
                    cfg.setFloatingOrbEnabled(true);
                    AsistenAccessibilityService.showFloatingOrb();
                    KobraForegroundService.start(this);
                    speaker.speak("Gelembung suara melayang tiga dimensi ditampilkan.");
                }
            });
        }

        if (btnResetAiSafeMode != null) {
            btnResetAiSafeMode.setOnClickListener(v -> {
                HapticFeedback.vibrateWarning(this);
                showSafeModeDialog();
            });
        }

        // 3 Modern Tabs
        if (tabMain != null) tabMain.setOnClickListener(v -> selectTab(0));
        if (tabUtils != null) tabUtils.setOnClickListener(v -> selectTab(1));
        if (tabVoice != null) tabVoice.setOnClickListener(v -> selectTab(2));

        // 2x2 Core Grid
        bindClick(R.id.btnSecondEyeMenu, v -> {
            HapticFeedback.vibrateAction(this);
            openSecondEyeChooser();
        });
        bindClick(R.id.btnStickCompass, v -> {
            HapticFeedback.vibrateAction(this);
            openStickAndCompassChooser();
        });
        bindClick(R.id.btnAiChat, v -> {
            HapticFeedback.vibrateAction(this);
            launchScreen(new Intent(this, AiChatActivity.class), "Gagal membuka Asisten AI.");
        });
        bindClick(R.id.btnOpenKobraHome, v -> {
            HapticFeedback.vibrateAction(this);
            launchScreen(new Intent(this, KobraLauncherActivity.class), "Gagal membuka Layar Utama Kobra.");
        });

        // Quick Strip: Daily Briefing & Guardian Safe Ack
        bindClick(R.id.btnDailyBriefingMain, v -> {
            HapticFeedback.vibrateAction(this);
            GuardianManager.speakDailyBriefing(this);
        });
        bindClick(R.id.btnQuickGuardianAck, v -> {
            HapticFeedback.vibrateSuccess(this);
            GuardianManager.acknowledgeSafe(this, true);
        });

        // Section 1: Utilitas & Kesehatan
        bindClick(R.id.btnDocumentReader, v -> {
            HapticFeedback.vibrateAction(this);
            launchScreen(new Intent(this, DocumentReaderActivity.class), "Gagal membuka Pembaca Dokumen.");
        });
        bindClick(R.id.btnPedometer, v -> {
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
        bindClick(R.id.btnProductivity, v -> {
            HapticFeedback.vibrateAction(this);
            launchScreen(new Intent(this, ProductivityActivity.class), "Gagal membuka Agenda dan Obat.");
        });
        bindClick(R.id.btnMedicalCard, v -> {
            HapticFeedback.vibrateAction(this);
            launchScreen(new Intent(this, MedicalCardActivity.class), "Gagal membuka Kartu Medis.");
        });
        bindClick(R.id.btnCompass, v -> {
            HapticFeedback.vibrateAction(this);
            launchScreen(new Intent(this, CompassActivity.class), "Gagal membuka Kompas Suara.");
        });
        bindClick(R.id.btnPrayer, v -> {
            HapticFeedback.vibrateAction(this);
            launchScreen(new Intent(this, PrayerActivity.class), "Gagal membuka Jadwal Sholat & Kiblat.");
        });
        bindClick(R.id.btnCaregiver, v -> {
            HapticFeedback.vibrateAction(this);
            launchScreen(new Intent(this, CaregiverActivity.class), "Gagal membuka Mode Pendamping.");
        });
        bindClick(R.id.btnCheckFinance, v -> {
            HapticFeedback.vibrateAction(this);
            String tx = new KobraConfig(this).getLastFinancialTx();
            String msg = tx.isEmpty()
                    ? "Belum ada mutasi atau transaksi keuangan yang tercatat dari notifikasi bank."
                    : "Transaksi keuangan terakhir: " + tx;
            speaker.speak(msg);
            new AlertDialog.Builder(this)
                    .setTitle("Mutasi Keuangan Terakhir")
                    .setMessage(msg)
                    .setPositiveButton("Tutup", null)
                    .show();
        });
        bindClick(R.id.btnCheckSignal, v -> {
            HapticFeedback.vibrateAction(this);
            String report = SystemUtilityHelper.buildSignalAndCarrierReport(this);
            speaker.speak(report);
            new AlertDialog.Builder(this)
                    .setTitle("Status Sinyal & Operator")
                    .setMessage(report)
                    .setPositiveButton("Tutup", null)
                    .show();
        });

        // Section 2: Panduan Suara
        bindClick(R.id.btnVoiceGuide, v -> {
            HapticFeedback.vibrateAction(this);
            speakAppGuide();
        });
        bindVoiceExample(R.id.cmdNav1, "Contoh Voice Access: tampilkan nomor, klik nomor tiga, atau tekan lama dua.");
        bindVoiceExample(R.id.cmdNav2, "Contoh grid presisi: tampilkan grid, zoom lima, lalu ketik Saya sedang di jalan.");
        bindVoiceExample(R.id.cmdNav3, "Contoh pembaca layar: baca layar, gulir bawah, kembali, atau mode tidur.");
        bindVoiceExample(R.id.cmdMedia1, "Contoh media YouTube: putar murottal di YouTube, atau volume naik.");
        bindVoiceExample(R.id.cmdMedia2, "Contoh kontrol media dan telepon: jeda media, lanjut media, atau jawab telepon.");
        bindVoiceExample(R.id.cmdCam1, "Contoh kamera Mata Kedua: hitung lembar uang, baca uang, atau cocokkan baju.");
        bindVoiceExample(R.id.cmdDaily1, "Contoh memori dan keamanan: ringkasan harian, saya aman, atau ingat bahwa saya alergi kacang.");
        bindVoiceExample(R.id.cmdDaily2, "Contoh harian lain: cek langkah hari ini, cek sinyal, atau tolong SOS.");

        selectTab(0);

        if (getIntent() != null && getIntent().getBooleanExtra("auto_listen", false)) {
            if (AsistenAccessibilityService.getInstance() != null) {
                AsistenAccessibilityService.requestListen();
            }
        }
    }

    private void bindClick(int viewId, View.OnClickListener listener) {
        View v = findViewById(viewId);
        if (v != null && listener != null) {
            v.setOnClickListener(listener);
        }
    }

    private boolean launchScreen(Intent intent, String fallbackMsg) {
        return KobraScreenHelper.safeStartActivity(this, intent, speaker, fallbackMsg);
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
        HapticFeedback.vibrateAction(this);
        currentTabIndex = index;
        if (sectionMain != null) sectionMain.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        if (sectionUtils != null) sectionUtils.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        if (sectionVoice != null) sectionVoice.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        styleTab(tabMain, index == 0);
        styleTab(tabUtils, index == 1);
        styleTab(tabVoice, index == 2);

        if (mainScroll != null) {
            mainScroll.post(() -> mainScroll.scrollTo(0, 0));
        }
    }

    private void styleTab(Button tab, boolean selected) {
        ThemeManager.styleTab(tab, selected);
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
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    speaker.speak(msg);
                })
                .setNeutralButton("Reset & Buka Pengaturan", (d, w) -> {
                    new KobraConfig(this).clearLlmSafeMode();
                    launchScreen(new Intent(this, SettingsActivity.class), "Gagal membuka Pengaturan.");
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    private void openStickAndCompassChooser() {
        final String[] labels = {
                "Mode Tongkat & Sonar 3D (Deteksi Halangan)",
                "Kompas Suara, Lokasi GPS & Rute Pulang",
                "Jadwal Sholat & Kiblat Offline"
        };
        speaker.speak("Pilih mode navigasi.");
        new AlertDialog.Builder(this)
                .setTitle("Mode Tongkat & Navigasi")
                .setItems(labels, (d, which) -> {
                    if (which == 0) launchScreen(new Intent(this, NavigationActivity.class), "Gagal membuka Mode Tongkat.");
                    else if (which == 1) launchScreen(new Intent(this, CompassActivity.class), "Gagal membuka Kompas Suara.");
                    else launchScreen(new Intent(this, PrayerActivity.class), "Gagal membuka Jadwal Sholat & Kiblat.");
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void openSecondEyeChooser() {
        final String[] labels = {
                "Penghitung Lembar Uang Tunai (otomatis)",
                "Baca Uang Satuan",
                "Padu Padan Warna Pakaian",
                "Pembaca Layar Digital Real-Time (ATM)",
                "Pemindai Struk & Screenshot Transfer",
                "Pemindai Barcode & QR Code Offline",
                "Cari Objek Sekitar (kursi, pintu)",
                "Terjemahkan Teks Kamera (Offline)",
                "Baca KTP & KK Offline",
                "Hitung Orang di Depan",
                "Jelaskan Sekitar"
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
                    launchScreen(intent, "Gagal membuka kamera Mata Kedua.");
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
        ThemeManager.applyTheme(this);
        styleTab(tabMain, currentTabIndex == 0);
        styleTab(tabUtils, currentTabIndex == 1);
        styleTab(tabVoice, currentTabIndex == 2);
        speaker.addListener(orbSpeechListener);
        showStatus();
    }

    @Override protected void onPause() {
        speaker.removeListener(orbSpeechListener);
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        showStatus();
    }

    private void speakAppGuide() {
        String activeName = VoiceProfileManager.getActiveAssistantName(this);
        String guide = "Panduan Suara " + activeName + " OS HUD. Di layar utama terdapat Orb Suara tiga dimensi di bagian atas, "
                + "tombol Pengaturan Terpadu di pojok kanan atas, dan empat modul utama: Mata Kedua, Mode Tongkat, Asisten AI, serta Layar Utama. "
                + "Gunakan perintah Voice Access seperti tampilkan nomor, klik nomor, tampilkan grid, atau baca layar kapan saja. "
                + "Usap dua jari ke bawah untuk membaca notifikasi, usap dua jari ke atas untuk menghentikan suara, atau ketuk dua kali untuk mulai bicara.";
        speaker.speak(guide);
    }

    private void showStatus() {
        KobraConfig c = new KobraConfig(this);
        LocalLlmEngine llm = LocalLlmEngine.getInstance(this);

        boolean accOk = AsistenAccessibilityService.getInstance() != null;
        boolean notifOk = KobraNotificationService.isConnected();

        int bat = -1;
        boolean charging = false;
        try {
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            if (bm != null) {
                bat = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                charging = bm.isCharging();
            }
        } catch (Throwable ignored) {}

        String ramText = "-- GB";
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                double availGb = mi.availMem / (1024.0 * 1024.0 * 1024.0);
                ramText = String.format(Locale.US, "%.1f GB", availGb);
            }
        } catch (Throwable ignored) {}

        String freeText = "--";
        try {
            StatFs stat = new StatFs(getFilesDir().getAbsolutePath());
            long freeMb = stat.getAvailableBytes() / (1024L * 1024L);
            freeText = freeMb >= 1024
                    ? String.format(Locale.US, "%.1f GB", freeMb / 1024.0)
                    : freeMb + " MB";
        } catch (Throwable ignored) {}

        if (tvTelemetryBattery != null) {
            tvTelemetryBattery.setText(bat >= 0 ? bat + "%" + (charging ? " AC" : "") : "--%");
        }
        if (tvTelemetryRam != null) {
            tvTelemetryRam.setText("RAM " + ramText);
        }
        if (tvTelemetryNet != null) {
            String netLabel = NetworkState.isOnline(this) ? NetworkState.connectionType(this) : "Offline";
            tvTelemetryNet.setText(netLabel);
        }
        if (tvBadgeSiaga != null) {
            tvBadgeSiaga.setText(accOk ? "SIAGA • LAYANAN AKTIF" : "MENUNGGU AKSESIBILITAS");
        }

        boolean crashSafe = c.isLlmSafeMode() || (llm.isModelPresent() && llm.isSafeMode());
        ThemeManager.KobraTheme activeTheme = ThemeManager.getActiveTheme(this);
        if (tvStatus != null) {
            if (crashSafe) {
                String err = llm.getLastError().isEmpty() ? LocalLlmEngine.CRASH_SAFE_MODE_REASON : llm.getLastError();
                tvStatus.setText(err);
                tvStatus.setTextColor(ThemeManager.ensureContrast(0xFFFFD600, activeTheme.getCardColor(), 4.6));
            } else {
                String aiLabel = llm.isInitialized() ? "Gemma Lokal Aktif"
                        : (llm.isModelPresent() ? "Gemma Lokal Siap"
                        : (NetworkState.isOnline(this) ? OnlineAiRouter.getActiveProviderSummary(this) : "Mode Offline"));
                tvStatus.setText(aiLabel + " • Notif: " + (notifOk ? "Aktif" : "Mati") + " • Ruang: " + freeText);
                tvStatus.setTextColor(ThemeManager.ensureContrast(activeTheme.getTextColor(), activeTheme.getCardColor(), 5.0));
            }
        }
        if (btnResetAiSafeMode != null) {
            btnResetAiSafeMode.setVisibility(crashSafe ? View.VISIBLE : View.GONE);
        }

        String activeName = VoiceProfileManager.getActiveAssistantName(this);
        if (tvMainHeaderTitle != null) {
            tvMainHeaderTitle.setText(activeName.toUpperCase(Locale.ROOT) + " OS");
        }
        String sosText = c.hasSosContact() ? "SOS Siap" : "SOS Default 112";
        String persona = KobraPersona.fromId(c.getActivePersonaId()).getTitle();
        if (tvQuickMeta != null) {
            tvQuickMeta.setText("Suara: " + activeName + " • " + sosText + " • Persona: " + persona + " • Memori: " + LocalMemoryStore.listMemories(this).size());
        }
        if (tvPedometerCardSub != null) {
            tvPedometerCardSub.setText(c.getTodaySteps() + " langkah (" + c.getTodayDistanceMeters() + " m)");
        }
    }
}
