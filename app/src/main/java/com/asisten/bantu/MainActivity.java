package com.asisten.bantu;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

/** Halaman setup: izin, aksesibilitas, AI, SOS. Dibuka dari ikon aplikasi. */
public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvStatus = findViewById(R.id.tvStatus);

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

    private void showStatus() {
        KobraConfig c = new KobraConfig(this);
        String text = "Status Kobra OS\n"
                + "Mikrofon: " + (Perms.has(this, android.Manifest.permission.RECORD_AUDIO) ? "siap" : "belum diizinkan") + "\n"
                + "Kamera: " + (Perms.has(this, android.Manifest.permission.CAMERA) ? "siap" : "belum diizinkan") + "\n"
                + "Aksesibilitas: " + (AsistenAccessibilityService.getInstance() != null ? "aktif" : "belum aktif") + "\n"
                + "Akses notifikasi: " + (KobraNotificationService.isConnected() ? "aktif" : "belum aktif") + "\n"
                + "Internet: " + (NetworkState.isOnline(this) ? "tersambung (fitur online aktif)" : "tidak tersambung (mode offline)") + "\n"
                + "AI gratis (OpenRouter): " + (c.hasAi() ? "siap" : "belum diisi API key");
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
