package com.asisten.bantu;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Layar pemulihan ringan ketika terjadi uncaught exception di aplikasi.
 * Menampilkan penyebab error serta menyediakan tombol Salin Log Error dan Kembali ke Beranda.
 */
public class CrashRecoveryActivity extends AppCompatActivity {

    public static final String EXTRA_CRASH_SUMMARY = "extra_crash_summary";
    public static final String EXTRA_CRASH_LOG = "extra_crash_log";

    private String fullLog = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash_recovery);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Pemulihan Kobra OS");
        }

        TextView tvSummary = findViewById(R.id.tvCrashSummary);
        TextView tvDetails = findViewById(R.id.tvCrashDetails);
        Button btnCopy = findViewById(R.id.btnCopyCrashLog);
        Button btnHome = findViewById(R.id.btnReturnHome);

        String summary = getIntent() != null ? getIntent().getStringExtra(EXTRA_CRASH_SUMMARY) : null;
        String passedLog = getIntent() != null ? getIntent().getStringExtra(EXTRA_CRASH_LOG) : null;
        if (passedLog == null || passedLog.trim().isEmpty()) {
            try {
                passedLog = new KobraConfig(this).getLastCrashLog();
            } catch (Throwable ignored) {}
        }
        fullLog = (passedLog != null && !passedLog.trim().isEmpty())
                ? passedLog
                : "Tidak ada detail stacktrace yang tersimpan.";

        if (summary == null || summary.trim().isEmpty()) {
            summary = "Terjadi error tak terduga. Silakan lihat log di bawah atau kembali ke beranda.";
        }

        if (tvSummary != null) tvSummary.setText(summary);
        if (tvDetails != null) tvDetails.setText(fullLog);

        try {
            Speaker.get(this).speak("Kobra OS mengalami kendala tak terduga dan membuka layar pemulihan. Tekan Kembali ke Beranda atau Salin Log Error.");
        } catch (Throwable ignored) {}

        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> copyLog());
        }
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> returnHome());
        }
    }

    private void copyLog() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("KobraCrashLog", fullLog));
                Toast.makeText(this, "Log error disalin ke clipboard.", Toast.LENGTH_LONG).show();
                try {
                    Speaker.get(this).speak("Log error berhasil disalin.");
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            Toast.makeText(this, "Gagal menyalin log: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void returnHome() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        } catch (Throwable ignored) {}
        finish();
    }
}
