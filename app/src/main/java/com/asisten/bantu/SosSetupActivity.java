package com.asisten.bantu;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/** Kontak darurat untuk SOS. Dua kontak: SMS berisi lokasi, lalu telepon kontak pertama. */
public class SosSetupActivity extends AppCompatActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sos);
        KobraConfig config = new KobraConfig(this);

        EditText etName1 = findViewById(R.id.etName1);
        EditText etPhone1 = findViewById(R.id.etPhone1);
        EditText etName2 = findViewById(R.id.etName2);
        EditText etPhone2 = findViewById(R.id.etPhone2);
        TextView tvStatus = findViewById(R.id.tvSosStatus);
        Button btnSave = findViewById(R.id.btnSaveSos);

        etName1.setText(config.getSos("name1"));
        etPhone1.setText(config.getSos("phone1"));
        etName2.setText(config.getSos("name2"));
        etPhone2.setText(config.getSos("phone2"));
        updateStatusText(config, tvStatus);

        btnSave.setOnClickListener(v -> {
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
            Toast.makeText(this, "Kontak SOS disimpan", Toast.LENGTH_SHORT).show();
            Speaker.get(this).speak("Kontak darurat disimpan.");
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
