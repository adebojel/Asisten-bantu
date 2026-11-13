package com.asisten.bantu;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Layar terpadu Produktivitas Offline:
 * Tanggal Masehi/Hijriah, Agenda CalendarContract, Memo Cepat,
 * Pengingat Lokasi Barang Rumah, Pembukuan Pengeluaran Harian, dan Jadwal Minum Obat.
 */
public class ProductivityActivity extends AppCompatActivity {

    private ProductivityManager manager;
    private Speaker speaker;

    private TextView tvDateHijri, tvMemoPreview, tvItemPreview, tvExpensePreview, tvMedSchedList;
    private EditText etMemoInput, etItemName, etItemLoc, etExpenseAmount, etExpenseNote, etMedSchedName, etMedSchedTime;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_productivity);

        manager = new ProductivityManager(this);
        speaker = Speaker.get(this);

        tvDateHijri = findViewById(R.id.tvDateHijri);
        tvMemoPreview = findViewById(R.id.tvMemoPreview);
        tvItemPreview = findViewById(R.id.tvItemPreview);
        tvExpensePreview = findViewById(R.id.tvExpensePreview);
        tvMedSchedList = findViewById(R.id.tvMedSchedList);

        etMemoInput = findViewById(R.id.etMemoInput);
        etItemName = findViewById(R.id.etItemName);
        etItemLoc = findViewById(R.id.etItemLoc);
        etExpenseAmount = findViewById(R.id.etExpenseAmount);
        etExpenseNote = findViewById(R.id.etExpenseNote);
        etMedSchedName = findViewById(R.id.etMedSchedName);
        etMedSchedTime = findViewById(R.id.etMedSchedTime);

        refreshAll();

        findViewById(R.id.btnSpeakAgenda).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            String msg = ProductivityManager.getGregorianAndHijriDate() + " " + ProductivityManager.readTodayAgenda(this);
            tvDateHijri.setText(msg);
            speaker.speak(msg);
        });

        findViewById(R.id.btnAddMemo).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            String txt = etMemoInput.getText().toString().trim();
            if (txt.isEmpty()) {
                speaker.speak("Isi memo masih kosong.");
                return;
            }
            manager.addMemo(txt);
            etMemoInput.setText("");
            refreshAll();
            HapticFeedback.vibrateSuccess(this);
            speaker.speak("Memo disimpan.");
        });

        findViewById(R.id.btnReadMemos).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            String s = manager.readMemos(5);
            tvMemoPreview.setText(s);
            speaker.speak(s);
        });

        findViewById(R.id.btnSaveItem).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            String item = etItemName.getText().toString().trim();
            String loc = etItemLoc.getText().toString().trim();
            if (item.isEmpty() || loc.isEmpty()) {
                speaker.speak("Isi nama barang dan lokasinya.");
                return;
            }
            manager.rememberItem(item, loc);
            refreshAll();
            HapticFeedback.vibrateSuccess(this);
            speaker.speak("Diingat: " + item + " di " + loc + ".");
        });

        findViewById(R.id.btnFindItem).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            String q = etItemName.getText().toString().trim();
            String res = manager.findItem(q);
            tvItemPreview.setText(res);
            speaker.speak(res);
        });

        findViewById(R.id.btnAddExpense).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            String amtRaw = etExpenseAmount.getText().toString().trim();
            if (amtRaw.isEmpty()) {
                speaker.speak("Masukkan nominal pengeluaran terlebih dahulu.");
                return;
            }
            try {
                long amt = Long.parseLong(amtRaw);
                if (amt <= 0) throw new NumberFormatException();
                String note = etExpenseNote.getText().toString().trim();
                manager.addExpense(amt, note);
                etExpenseAmount.setText("");
                etExpenseNote.setText("");
                refreshAll();
                HapticFeedback.vibrateSuccess(this);
                speaker.speak("Pengeluaran " + amt + " rupiah dicatat.");
            } catch (NumberFormatException e) {
                speaker.speak("Nominal tidak valid.");
            }
        });

        findViewById(R.id.btnExpenseSummary).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            String sum = manager.getExpenseSummary();
            tvExpensePreview.setText(sum);
            speaker.speak(sum);
        });

        findViewById(R.id.btnAddMedSched).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            String name = etMedSchedName.getText().toString().trim();
            String timeRaw = etMedSchedTime.getText().toString().trim();
            if (name.isEmpty() || !timeRaw.matches("\\d{1,2}:\\d{2}")) {
                speaker.speak("Isi nama obat dan jam dengan format JJ titik dua MM, misalnya 07:30.");
                return;
            }
            String[] parts = timeRaw.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            if (h > 23 || m > 59) {
                speaker.speak("Jam atau menit tidak valid.");
                return;
            }
            manager.addMedSchedule(name, h, m);
            etMedSchedName.setText("");
            etMedSchedTime.setText("");
            refreshAll();
            HapticFeedback.vibrateSuccess(this);
            speaker.speak("Jadwal obat " + name + " pukul " + timeRaw + " ditambahkan.");
        });

        findViewById(R.id.btnMarkMedTaken).setOnClickListener(v -> {
            HapticFeedback.vibrateSuccess(this);
            manager.markMedicationTaken("");
            refreshAll();
            speaker.speak("Semua jadwal obat hari ini ditandai sudah diminum.");
        });

        findViewById(R.id.btnBackProductivity).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            finish();
        });
    }

    private void refreshAll() {
        tvDateHijri.setText(ProductivityManager.getGregorianAndHijriDate());
        tvMemoPreview.setText(manager.readMemos(3));
        tvItemPreview.setText(manager.listAllItems());
        tvExpensePreview.setText(manager.getExpenseSummary());
        tvMedSchedList.setText(manager.listMedSchedulesSpeech());
    }
}
