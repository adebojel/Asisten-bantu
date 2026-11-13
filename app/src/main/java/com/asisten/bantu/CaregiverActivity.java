package com.asisten.bantu;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

/**
 * Mode Pendamping (Caregiver Mode): antarmuka standar keluarga untuk mengatur PIN keamanan,
 * konfirmasi berlapis, mode privat Bluetooth, deteksi jatuh, jadwal obat, serta akses cepat SOS & cadangan data.
 */
public class CaregiverActivity extends AppCompatActivity {

    public static final String EXTRA_PIN_UNLOCKED = "pin_unlocked";

    private KobraConfig config;
    private ProductivityManager productivity;
    private Speaker speaker;
    private final KobraScreenHelper screenHelper = new KobraScreenHelper();

    private View rootView;
    private TextView tvPinStatus, tvCareMedList, tvCareSummary;
    private EditText etNewPin, etCareMedName, etCareMedTime;
    private SwitchCompat swLayeredConfirm, swAutoPrivateBt, swCareFall, swCarePocket;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_caregiver);

        config = new KobraConfig(this);
        productivity = new ProductivityManager(this);
        speaker = Speaker.get(this);

        rootView = findViewById(R.id.caregiverRoot);
        tvPinStatus = findViewById(R.id.tvPinStatus);
        tvCareMedList = findViewById(R.id.tvCareMedList);
        tvCareSummary = findViewById(R.id.tvCareSummary);

        etNewPin = findViewById(R.id.etNewPin);
        etCareMedName = findViewById(R.id.etCareMedName);
        etCareMedTime = findViewById(R.id.etCareMedTime);

        swLayeredConfirm = findViewById(R.id.swLayeredConfirm);
        swAutoPrivateBt = findViewById(R.id.swAutoPrivateBt);
        swCareFall = findViewById(R.id.swCareFall);
        swCarePocket = findViewById(R.id.swCarePocket);

        if (config.hasSecurityPin() && !getIntent().getBooleanExtra(EXTRA_PIN_UNLOCKED, false)) {
            rootView.setVisibility(View.INVISIBLE);
            SecurityAndPinHelper.requirePinIfSet(this, config, speaker, "Mode Pendamping",
                    new SecurityAndPinHelper.PinCallback() {
                        @Override public void onUnlocked() {
                            rootView.setVisibility(View.VISIBLE);
                            initUi();
                        }
                        @Override public void onCancelled() {
                            finish();
                        }
                    });
        } else {
            initUi();
        }
    }

    private void initUi() {
        swLayeredConfirm.setChecked(config.isLayeredConfirmEnabled());
        swAutoPrivateBt.setChecked(config.isAutoPrivateOnBt());
        swCareFall.setChecked(config.isFallDetectionEnabled());
        swCarePocket.setChecked(config.isPocketLockEnabled());

        swLayeredConfirm.setOnCheckedChangeListener((b, v) -> config.setLayeredConfirmEnabled(v));
        swAutoPrivateBt.setOnCheckedChangeListener((b, v) -> config.setAutoPrivateOnBt(v));
        swCareFall.setOnCheckedChangeListener((b, v) -> config.setFallDetectionEnabled(v));
        swCarePocket.setOnCheckedChangeListener((b, v) -> config.setPocketLockEnabled(v));

        findViewById(R.id.btnSetPin).setOnClickListener(v -> {
            String pin = etNewPin.getText().toString().trim();
            if (!pin.matches("\\d{4,6}")) {
                toast("PIN harus 4 hingga 6 angka.");
                return;
            }
            config.setSecurityPin(pin);
            etNewPin.setText("");
            refreshSummary();
            toast("PIN keamanan disimpan.");
            speaker.speak("PIN keamanan berhasil dipasang.");
        });

        findViewById(R.id.btnRemovePin).setOnClickListener(v -> {
            config.setSecurityPin("");
            refreshSummary();
            toast("PIN keamanan dihapus.");
            speaker.speak("Kunci PIN keamanan dinonaktifkan.");
        });

        findViewById(R.id.btnCareAddMed).setOnClickListener(v -> {
            String name = etCareMedName.getText().toString().trim();
            String time = etCareMedTime.getText().toString().trim();
            if (name.isEmpty() || !time.matches("\\d{1,2}:\\d{2}")) {
                toast("Isi nama obat dan jam (contoh: 08:00).");
                return;
            }
            String[] p = time.split(":");
            int h = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            if (h > 23 || m > 59) {
                toast("Format jam tidak valid.");
                return;
            }
            productivity.addMedSchedule(name, h, m);
            etCareMedName.setText("");
            etCareMedTime.setText("");
            refreshSummary();
            toast("Jadwal obat ditambahkan.");
        });

        findViewById(R.id.btnCareRemoveMed).setOnClickListener(v -> {
            productivity.removeMedSchedule(0);
            refreshSummary();
            toast("Jadwal obat pertama dihapus.");
        });

        findViewById(R.id.btnCareClearMemos).setOnClickListener(v -> {
            productivity.clearMemos();
            refreshSummary();
            toast("Semua memo dibersihkan.");
        });

        findViewById(R.id.btnCareClearExpenses).setOnClickListener(v -> {
            productivity.clearExpenses();
            refreshSummary();
            toast("Catatan pengeluaran direset.");
        });

        findViewById(R.id.btnCareSos).setOnClickListener(v ->
                startActivity(new Intent(this, SosSetupActivity.class).putExtra(EXTRA_PIN_UNLOCKED, true)));

        findViewById(R.id.btnCareMedCard).setOnClickListener(v ->
                startActivity(new Intent(this, MedicalCardActivity.class).putExtra(EXTRA_PIN_UNLOCKED, true)));

        findViewById(R.id.btnCareSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class).putExtra(EXTRA_PIN_UNLOCKED, true)));

        findViewById(R.id.btnCareBack).setOnClickListener(v -> finish());

        refreshSummary();
    }

    @Override protected void onResume() {
        super.onResume();
        if (rootView != null && rootView.getVisibility() == View.VISIBLE) {
            refreshSummary();
        }
    }

    private void refreshSummary() {
        tvPinStatus.setText(config.hasSecurityPin()
                ? "Status PIN: AKTIF (" + config.getSecurityPin().length() + " digit mengunci menu sensitif)"
                : "Status PIN: Belum diatur (menu sensitif terbuka tanpa PIN)");
        tvCareMedList.setText(productivity.listMedSchedulesSpeech());
        tvCareSummary.setText(
                productivity.readMemos(3) + "\n\n"
                        + productivity.listAllItems() + "\n\n"
                        + productivity.getExpenseSummary());
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (screenHelper.handleKeyEvent(this, event, speaker)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (screenHelper.handleTouchEvent(this, ev, speaker)) return true;
        return super.dispatchTouchEvent(ev);
    }
}
