package com.asisten.bantu;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Button;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class VisionActivity extends BaseKobraActivity {
    private TextView tvResult;
    private TextView tvStatus;
    private TextToSpeech tts;
    private String mode = "dokumen";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vision);

        tvResult = findViewById(R.id.tvVisionResult);
        tvStatus = findViewById(R.id.tvVisionStatus);
        Button btnScan = findViewById(R.id.btnScanVision);
        Button btnBack = findViewById(R.id.btnBackVision);

        mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "dokumen";

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("id", "ID"));
                tts.setSpeechRate(0.9f);
                speakMode();
            }
        });

        btnScan.setOnClickListener(v -> startOcrScan());
        btnBack.setOnClickListener(v -> finish());

        tvStatus.setText("Mode: " + mode.toUpperCase());
    }

    private void speakMode() {
        String text = "";
        if (mode.equals("ktp")) {
            text = "Mode baca KTP aktif. Arahkan kamera ke KTP.";
        } else if (mode.equals("uang")) {
            text = "Mode baca uang aktif.";
        } else if (mode.equals("warna")) {
            text = "Mode baca warna aktif.";
        } else {
            text = "Mode baca dokumen aktif.";
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    private void startOcrScan() {
        // Simulasi OCR KTP - nanti diganti ML Kit Text Recognition offline
        String fakeOcr = "NIK: 3601234567890001\nNama: ADE BOJEL\nTempat Tgl Lahir: TANGERANG 01-01-1990\nAlamat: JL KOBRA NO 1 TANGERANG";
        processKtpResult(fakeOcr);
    }

    private void processKtpResult(String fullText) {
        String lower = fullText.toLowerCase();
        StringBuilder sb = new StringBuilder();
        sb.append("HASIL BACA KTP / KK / DOKUMEN:\n\n");
        sb.append(fullText);
        sb.append("\n\n--- ANALISA ---\n");

        // Fix illegal escape: pakai \\d bukan \d
        if (lower.contains("nik") || lower.contains("ktp") || fullText.matches(".*\\d{16}.*")) {
            sb.append("Terdeteksi KTP. ");
            // Extract NIK 16 digit
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{16}").matcher(fullText);
            if (m.find()) {
                sb.append("NIK: ").append(m.group()).append(". ");
            }
            if (lower.contains("nama")) {
                sb.append("Ada field Nama. ");
            }
        } else {
            sb.append("Dokumen umum terbaca.");
        }

        String result = sb.toString();
        tvResult.setText(result);
        tts.speak(result, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
