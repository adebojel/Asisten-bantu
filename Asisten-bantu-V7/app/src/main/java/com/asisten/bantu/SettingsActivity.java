package com.asisten.bantu;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/** Pengaturan API key OpenRouter (gratis) dan model AI. */
public class SettingsActivity extends AppCompatActivity {

    private EditText etKey, etModel, etN8n;
    private TextView tvStatus;
    private KobraConfig config;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        config = new KobraConfig(this);
        etKey = findViewById(R.id.etApiKey);
        etModel = findViewById(R.id.etModel);
        tvStatus = findViewById(R.id.tvSettingsStatus);
        etKey.setText(config.getApiKey());
        etModel.setText(config.getModel());
        etN8n = findViewById(R.id.etN8nUrl);
        etN8n.setText(config.getN8nUrl());

        Button save = findViewById(R.id.btnSaveSettings);
        Button test = findViewById(R.id.btnTestAi);
        Button back = findViewById(R.id.btnBackSettings);

        save.setOnClickListener(v -> {
            saveFields();
            tvStatus.setText("Pengaturan tersimpan.");
            Speaker.get(this).speak("Pengaturan AI tersimpan.");
        });

        test.setOnClickListener(v -> {
            saveFields();
            tvStatus.setText("Menguji koneksi AI...");
            new AiClient(config).chat(AiClient.SYSTEM_TUNANETRA,
                    "Perkenalkan dirimu dalam satu kalimat singkat.", new AiClient.Callback() {
                        @Override public void onResult(String text) {
                            tvStatus.setText("AI menjawab: " + text);
                            Speaker.get(SettingsActivity.this).speak(text);
                        }
                        @Override public void onError(String message) {
                            tvStatus.setText("Gagal: " + message);
                        }
                    });
        });

        back.setOnClickListener(v -> finish());
    }

    private void saveFields() {
        config.setApiKey(etKey.getText().toString());
        config.setModel(etModel.getText().toString());
        config.setN8nUrl(etN8n.getText().toString());
    }
}
