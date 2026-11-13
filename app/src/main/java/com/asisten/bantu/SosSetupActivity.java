package com.asisten.bantu;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/** Kontak darurat untuk SOS, Mode Latihan SOS (simulasi), dan akses Kartu Identitas Medis. */
public class SosSetupActivity extends AppCompatActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sos);
        KobraConfig config = new KobraConfig(this);

        final android.view.View contentRoot = ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        if (config.hasSecurityPin() && !getIntent().getBooleanExtra(CaregiverActivity.EXTRA_PIN_UNLOCKED, false)) {
            if (contentRoot != null) contentRoot.setVisibility(android.view.View.INVISIBLE);
            SecurityAndPinHelper.requirePinIfSet(this, config, Speaker.get(this), "Kontak Darurat SOS",
                    new SecurityAndPinHelper.PinCallback() {
                        @Override public void onUnlocked() {
                            if (contentRoot != null) contentRoot.setVisibility(android.view.View.VISIBLE);
                        }
                        @Override public void onCancelled() {
                            finish();
                        }
                    });
        }

        EditText etName1 = findViewById(R.id.etName1);
        EditText etPhone1 = findViewById(R.id.etPhone1);
        EditText etName2 = findViewById(R.id.etName2);
        EditText etPhone2 = findViewById(R.id.etPhone2);
        TextView tvStatus = findViewById(R.id.tvSosStatus);
        Button btnSave = findViewById(R.id.btnSaveSos);
        Button btnPractice = findViewById(R.id.btnSosPractice);
        Button btnMedical = findViewById(R.id.btnOpenMedicalCard);

        etName1.setText(config.getSos("name1"));
        etPhone1.setText(config.getSos("phone1"));
        etName2.setText(config.getSos("name2"));
        etPhone2.setText(config.getSos("phone2"));
        updateStatusText(config, tvStatus);

        btnSave.setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            String p1 = etPhone1.getText().toString().trim();
            String p2 = etPhone2.getText().toString().trim();
            if (!p1.isEmpty() && !p1.matches("[0-9+ ]{6,}") || !p2.isEmpty() && !p2.matches("[0-9+ ]{6,}")) {
                tvStatus.setText("Nomor telepon hanya boleh angka, spasi, atau tanda +.");
                return;
            }
            config.setSos("name1", etName1.getText().toString());
            config.setSos("phone1", p1);
            config.setSos("name2", etName2.getText().toString());
            config.setSos("phone2", p2);
            updateStatusText(config, tvStatus);
            HapticFeedback.vibrateSuccess(this);
            Toast.makeText(this, "Kontak SOS disimpan", Toast.LENGTH_SHORT).show();
            Speaker.get(this).speak("Kontak darurat disimpan.");
        });

        btnPractice.setOnClickListener(v -> {
            HapticFeedback.vibrateSosMorse(this, false);
            String target = config.hasSosContact()
                    ? (config.getSos("name1").isEmpty() ? config.getSos("phone1") : config.getSos("name1"))
                    : "nomor darurat 112";
            String simMsg = "Mode Latihan SOS aktif. Ini hanya simulasi, tidak ada SMS atau panggilan telepon asli yang dikirim. "
                    + "Pada keadaan darurat nyata, Kobra akan mengirim SMS koordinat GPS Google Maps ke " + target
                    + ", menyalakan getaran Morse SOS, lalu menelepon secara otomatis.";
            tvStatus.setText("Simulasi Latihan SOS selesai (tidak mengirim SMS/telepon asli).");
            Speaker.get(this).speak(simMsg);
        });

        btnMedical.setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            startActivity(new Intent(this, MedicalCardActivity.class));
        });
    }

    private void updateStatusText(KobraConfig config, TextView tvStatus) {
        String n1 = config.getSos("name1");
        String p1 = config.getSos("phone1");
        String n2 = config.getSos("name2");
        String p2 = config.getSos("phone2");
        if (p1.isEmpty() && p2.isEmpty()) {
            tvStatus.setText("Belum diatur (Default: 112)");
        } else {
            StringBuilder sb = new StringBuilder("Tersimpan: ");
            if (!p1.isEmpty()) {
                sb.append(n1.isEmpty() ? "Kontak 1" : n1).append(" (").append(p1).append(")");
            }
            if (!p2.isEmpty()) {
                if (!p1.isEmpty()) sb.append(", ");
                sb.append(n2.isEmpty() ? "Kontak 2" : n2).append(" (").append(p2).append(")");
            }
            tvStatus.setText(sb.toString());
        }
    }
}
