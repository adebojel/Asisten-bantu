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

        findViewById(R.id.btnVoiceGuide).setOnClickListener(v -> speakAppGuide());
        findViewById(R.id.btnPermissions).setOnClickListener(v ->
                ActivityCompat.requestPermissions(this, Perms.required(), 100));
        findViewById(R.id.btnAccessibility).setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            toast("Cari Kobra OS di daftar, lalu aktifkan.");
        });
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnSos).setOnClickListener(v ->
                startActivity(new Intent(this, SosSetupActivity.class)));
        findViewById(R.id.btnBattery).setOnClickListener(v -> askBatteryExemption());
        findViewById(R.id.btnStartService).setOnClickListener(v -> {
            KobraForegroundService.start(this);
            toast("Kobra berjalan di latar belakang.");
            speaker.speak("Layanan latar belakang Kobra diaktifkan.");
        });
        findViewById(R.id.btnLauncher).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)));
        findViewById(R.id.btnNotifAccess).setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            toast("Cari Kobra OS di daftar, lalu aktifkan.");
        });
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
        String guide = "Panduan Suara Kobra OS. Di halaman ini terdapat delapan tombol utama. "
                + "Tombol satu, Beri izin, untuk mengaktifkan mikrofon, kamera, lokasi, kontak, SMS, dan telepon. "
                + "Tombol dua, Aktifkan layanan aksesibilitas Kobra, agar Kobra bisa mendengar suara dan membantu mengendalikan layar. "
                + "Tombol tiga, Aktifkan akses notifikasi, untuk membacakan pesan dan panggilan masuk. "
                + "Tombol empat, Pengaturan AI, untuk mengisi API key, mengimpor model offline titik task, mengatur kecepatan serta nada suara, kontras tinggi, dan mencadangkan atau memulihkan data. "
                + "Tombol lima, Kontak darurat SOS, untuk mengatur nomor tujuan saat darurat. "
                + "Tombol enam, Pembatasan baterai, agar Kobra tidak dimatikan sistem saat hemat daya. "
                + "Tombol tujuh, Jalankan Kobra di latar belakang. "
                + "Tombol delapan, Jadikan Kobra sebagai layar utama.";
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
                + "Kontak darurat SOS: " + sosText;
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
