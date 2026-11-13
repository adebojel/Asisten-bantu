package com.asisten.bantu;

import android.content.Intent;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.StatFs;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

/** Halaman setup: izin, aksesibilitas, AI, SOS, panduan suara. Dibuka dari ikon aplikasi. */
public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private Speaker speaker;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvStatus = findViewById(R.id.tvStatus);
        speaker = Speaker.get(this);

        findViewById(R.id.btnVoiceGuide).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            speakAppGuide();
        });
        findViewById(R.id.btnPermissions).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            ActivityCompat.requestPermissions(this, Perms.required(), 100);
        });
        findViewById(R.id.btnAccessibility).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
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
        findViewById(R.id.btnNotifAccess).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            toast("Cari Kobra OS di daftar, lalu aktifkan.");
        });
        findViewById(R.id.btnCompass).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(this, CompassActivity.class));
        });
        findViewById(R.id.btnMedicalCard).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(this, MedicalCardActivity.class));
        });
        findViewById(R.id.btnProductivity).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(this, ProductivityActivity.class));
        });
        findViewById(R.id.btnPrayer).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(this, PrayerActivity.class));
        });
        findViewById(R.id.btnCaregiver).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(this, CaregiverActivity.class));
        });
        findViewById(R.id.btnSecondEyeMenu).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            openSecondEyeChooser();
        });
        findViewById(R.id.btnDocumentReader).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(this, DocumentReaderActivity.class));
        });
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

    @Override protected void onResume() {
        super.onResume();
        showStatus();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        showStatus();
    }

    private void speakAppGuide() {
        String guide = "Panduan Suara Kobra OS. Di halaman ini terdapat tombol utama berikut. "
                + "Tombol satu, Beri izin, untuk mengaktifkan mikrofon, kamera, lokasi, kontak, SMS, telepon, dan Bluetooth darurat. "
                + "Tombol dua, Aktifkan layanan aksesibilitas Kobra, agar Kobra bisa mendengar suara, deteksi jatuh, tombol volume, dan membantu mengendalikan layar. "
                + "Tombol tiga, Aktifkan akses notifikasi, untuk membacakan pesan dan panggilan masuk. "
                + "Tombol empat, Pengaturan AI, untuk mengisi API key, mengimpor model offline titik task, mengatur kecepatan, nada, serta gaya bahasa suara, kontras tinggi, dan mencadangkan atau memulihkan data. "
                + "Tombol lima, Kontak darurat SOS, untuk mengatur nomor tujuan saat darurat dan mode latihan SOS. "
                + "Tombol enam, Pembatasan baterai, agar Kobra tidak dimatikan sistem saat hemat daya. "
                + "Tombol tujuh, Jalankan Kobra di latar belakang. "
                + "Tombol delapan, Jadikan Kobra sebagai layar utama. "
                + "Tersedia juga tombol Kompas Suara dan Rute Pulang, Kartu Identitas Medis Darurat, "
                + "Produktivitas untuk memo, pengingat barang, pengeluaran dan jadwal obat, Jadwal Sholat dan Kiblat offline, "
                + "Mode Pendamping untuk PIN keamanan dan keluarga, menu Mata Kedua untuk hitung uang tunai, padu padan pakaian, layar ATM, struk transfer, barcode, dan cari objek, "
                + "serta Pembaca Dokumen untuk file teks dan PDF.";
        speaker.speak(guide);
    }

    private void showStatus() {
        KobraConfig c = new KobraConfig(this);
        LocalLlmEngine llm = LocalLlmEngine.getInstance(this);

        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int bat = bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
        boolean charging = bm != null && bm.isCharging();

        StatFs stat = new StatFs(getFilesDir().getAbsolutePath());
        long freeMb = stat.getAvailableBytes() / (1024L * 1024L);
        String freeText = freeMb >= 1024
                ? String.format(java.util.Locale.US, "%.1f GB", freeMb / 1024.0)
                : freeMb + " MB";

        String llmStatus;
        if (llm.isInitialized()) llmStatus = "aktif (Gemma 3 1B siap)";
        else if (llm.isModelPresent()) llmStatus = llm.isSafeMode() ? "Mode Aman (" + llm.getLastError() + ")" : "terpasang (siap dimuat)";
        else llmStatus = "Mode Aman (model .task belum diimpor)";

        String sosText = c.hasSosContact() ? "sudah diatur" : "Belum diatur (Default: 112)";
        String medText = c.hasMedicalCard() ? "terisi" : "belum diisi";

        String text = "Status Kobra OS\n"
                + "Mikrofon: " + (Perms.has(this, android.Manifest.permission.RECORD_AUDIO) ? "siap" : "belum diizinkan") + "\n"
                + "Kamera: " + (Perms.has(this, android.Manifest.permission.CAMERA) ? "siap" : "belum diizinkan") + "\n"
                + "Aksesibilitas: " + (AsistenAccessibilityService.getInstance() != null ? "aktif" : "belum aktif") + "\n"
                + "Akses notifikasi: " + (KobraNotificationService.isConnected() ? "aktif" : "belum aktif") + "\n"
                + "Internet (" + NetworkState.connectionType(this) + "): " + (NetworkState.isOnline(this) ? "tersambung" : "mode offline") + "\n"
                + "AI Offline: " + llmStatus + "\n"
                + "AI Online (OpenRouter): " + (c.hasAi() ? "siap" : "belum diisi API key") + "\n"
                + "Baterai: " + (bat >= 0 ? bat + "% (" + (charging ? "mengisi" : "tidak mengisi") + ")" : "tidak terbaca") + "\n"
                + "Sisa penyimpanan: " + freeText + "\n"
                + "Kontak darurat SOS: " + sosText + "\n"
                + "Kartu Medis Darurat: " + medText + "\n"
                + "Deteksi Jatuh & Kunci Saku: " + (c.isFallDetectionEnabled() ? "aktif" : "mati")
                + " | Mode Privasi: " + (c.isPrivacyMode() ? "AKTIF (kamera & mic diputus)" : "nonaktif") + "\n"
                + "Kunci PIN Keamanan: " + (c.hasSecurityPin() ? "AKTIF" : "belum dipasang")
                + " | Privat Otomatis BT: " + (c.isAutoPrivateOnBt() ? "aktif" : "mati") + "\n"
                + "Gaya Bahasa Asisten: " + c.getAssistantStyleLabel()
                + " | Memori Chat: " + llm.getSessionTurnCount() + " giliran";
        tvStatus.setText(text);
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
