package com.asisten.bantu;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Layar Kartu Identitas Medis Darurat kontras tinggi (nama, golongan darah, alergi, kontak keluarga)
 * yang langsung dibacakan keras saat dipanggil oleh pengguna atau penolong.
 */
public class MedicalCardActivity extends AppCompatActivity {

    public static final String EXTRA_AUTO_SPEAK = "auto_speak";

    private KobraConfig config;
    private Speaker speaker;
    private EditText etName, etBlood, etAllergy, etCondition, etFamily;
    private TextView tvSummary;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medical_card);
        config = new KobraConfig(this);
        speaker = Speaker.get(this);

        tvSummary = findViewById(R.id.tvMedSummary);
        etName = findViewById(R.id.etMedName);
        etBlood = findViewById(R.id.etMedBlood);
        etAllergy = findViewById(R.id.etMedAllergy);
        etCondition = findViewById(R.id.etMedCondition);
        etFamily = findViewById(R.id.etMedFamily);

        loadFields();

        findViewById(R.id.btnSaveMed).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            saveFields();
            updateSummary();
            HapticFeedback.vibrateSuccess(this);
            speaker.speak("Kartu identitas medis darurat berhasil disimpan.");
        });

        findViewById(R.id.btnSpeakMed).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            saveFields();
            updateSummary();
            speakLoudly();
        });

        findViewById(R.id.btnBackMed).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            finish();
        });

        if (getIntent().getBooleanExtra(EXTRA_AUTO_SPEAK, false)) {
            speakLoudly();
        }
    }

    private void loadFields() {
        etName.setText(config.getMedName());
        etBlood.setText(config.getMedBlood());
        etAllergy.setText(config.getMedAllergy());
        etCondition.setText(config.getMedCondition());
        etFamily.setText(config.getMedFamily());
        updateSummary();
    }

    private void saveFields() {
        config.setMedName(etName.getText().toString());
        config.setMedBlood(etBlood.getText().toString());
        config.setMedAllergy(etAllergy.getText().toString());
        config.setMedCondition(etCondition.getText().toString());
        config.setMedFamily(etFamily.getText().toString());
    }

    private void updateSummary() {
        tvSummary.setText(config.getMedicalCardSpeech());
    }

    private void speakLoudly() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (cur < (max * 8) / 10) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0);
                }
            }
        } catch (Exception ignored) {}
        speaker.speak(config.getMedicalCardSpeech());
    }
}
