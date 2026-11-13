package com.asisten.bantu;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pengaturan API key, model AI offline .task, kontrol suara TTS, kontras, catatan, dan cadangan data JSON. */
public class SettingsActivity extends AppCompatActivity {

    private EditText etKey, etModel, etN8n, etNotes;
    private TextView tvStatus, tvModelStatus, tvImportProgress, tvSpeechRate, tvSpeechPitch, tvBackupStatus, tvUpdateStatus;
    private Button btnImportModel, btnDeleteModel, btnLoadModel, btnTestTts, btnBackupData, btnRestoreData, btnCheckUpdate, btnViewCrashLog;
    private ProgressBar pbImportModel;
    private SeekBar sbSpeechRate, sbSpeechPitch;
    private SwitchCompat swHighContrast;
    private RadioGroup rgAssistantStyle;
    private RadioButton rbStyleRingkas, rbStyleRamah, rbStyleDaerah;
    private boolean loadingFields = false;
    private KobraConfig config;
    private Speaker speaker;
    private AppUpdater appUpdater;
    private Uri pendingInstallUri = null;
    private final KobraScreenHelper screenHelper = new KobraScreenHelper();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String[]> modelPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    processSelectedModelFile(uri);
                }
            });

    private final ActivityResultLauncher<String> backupLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri != null) {
                    exportBackupToUri(uri);
                }
            });

    private final ActivityResultLauncher<String[]> restoreLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importBackupFromUri(uri);
                }
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        config = new KobraConfig(this);
        speaker = Speaker.get(this);

        final View contentRoot = ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        if (config.hasSecurityPin() && !getIntent().getBooleanExtra(CaregiverActivity.EXTRA_PIN_UNLOCKED, false)) {
            if (contentRoot != null) contentRoot.setVisibility(View.INVISIBLE);
            SecurityAndPinHelper.requirePinIfSet(this, config, speaker, "Pengaturan AI & Cadangan",
                    new SecurityAndPinHelper.PinCallback() {
                        @Override public void onUnlocked() {
                            if (contentRoot != null) contentRoot.setVisibility(View.VISIBLE);
                        }
                        @Override public void onCancelled() {
                            finish();
                        }
                    });
        }

        etKey = findViewById(R.id.etApiKey);
        etModel = findViewById(R.id.etModel);
        etN8n = findViewById(R.id.etN8nUrl);
        etNotes = findViewById(R.id.etNotes);
        tvStatus = findViewById(R.id.tvSettingsStatus);
        tvModelStatus = findViewById(R.id.tvModelStatus);
        tvImportProgress = findViewById(R.id.tvImportProgress);
        tvSpeechRate = findViewById(R.id.tvSpeechRate);
        tvSpeechPitch = findViewById(R.id.tvSpeechPitch);
        tvBackupStatus = findViewById(R.id.tvBackupStatus);
        btnImportModel = findViewById(R.id.btnImportModel);
        btnDeleteModel = findViewById(R.id.btnDeleteModel);
        btnLoadModel = findViewById(R.id.btnLoadModel);
        pbImportModel = findViewById(R.id.pbImportModel);
        sbSpeechRate = findViewById(R.id.sbSpeechRate);
        sbSpeechPitch = findViewById(R.id.sbSpeechPitch);
        swHighContrast = findViewById(R.id.swHighContrast);
        rgAssistantStyle = findViewById(R.id.rgAssistantStyle);
        rbStyleRingkas = findViewById(R.id.rbStyleRingkas);
        rbStyleRamah = findViewById(R.id.rbStyleRamah);
        rbStyleDaerah = findViewById(R.id.rbStyleDaerah);
        btnTestTts = findViewById(R.id.btnTestTts);
        btnBackupData = findViewById(R.id.btnBackupData);
        btnRestoreData = findViewById(R.id.btnRestoreData);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        btnViewCrashLog = findViewById(R.id.btnViewCrashLog);
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus);
        appUpdater = new AppUpdater(this);
        tvUpdateStatus.setText("Versi terpasang: " + BuildConfig.VERSION_NAME);

        loadFieldsFromConfig();

        sbSpeechRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float rate = progressToTtsValue(progress);
                tvSpeechRate.setText(String.format(Locale.US, "Kecepatan Suara (Rate): %.1fx", rate));
                if (fromUser) {
                    config.setSpeechRate(rate);
                    speaker.applyVoiceSettings();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbSpeechPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float pitch = progressToTtsValue(progress);
                tvSpeechPitch.setText(String.format(Locale.US, "Nada Suara (Pitch): %.1fx", pitch));
                if (fromUser) {
                    config.setSpeechPitch(pitch);
                    speaker.applyVoiceSettings();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        swHighContrast.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setHighContrast(isChecked);
            applyHighContrastVisual(isChecked);
        });

        rgAssistantStyle.setOnCheckedChangeListener((group, checkedId) -> {
            if (loadingFields) return;
            String style = styleFromCheckedId(checkedId);
            config.setAssistantStyle(style);
            speaker.speak("Gaya bahasa asisten diatur ke mode " + config.getAssistantStyleLabel() + ".");
        });

        btnTestTts.setOnClickListener(v -> {
            saveFields();
            speaker.speak("Ini contoh suara asisten Kobra OS dengan kecepatan dan nada yang Anda pilih.");
        });

        Button save = findViewById(R.id.btnSaveSettings);
        Button test = findViewById(R.id.btnTestAi);
        Button back = findViewById(R.id.btnBackSettings);

        save.setOnClickListener(v -> {
            saveFields();
            tvStatus.setText("Pengaturan tersimpan.");
            speaker.speak("Seluruh pengaturan, suara asisten, dan catatan tersimpan.");
        });

        test.setOnClickListener(v -> {
            saveFields();
            tvStatus.setText("Menguji koneksi AI...");
            new AiClient(config).chat(AiClient.SYSTEM_TUNANETRA,
                    "Perkenalkan dirimu dalam satu kalimat singkat.", new AiClient.Callback() {
                        @Override public void onResult(String text) {
                            tvStatus.setText("AI menjawab: " + text);
                            speaker.speak(text);
                        }
                        @Override public void onError(String message) {
                            tvStatus.setText("Gagal: " + message);
                        }
                    });
        });

        btnImportModel.setOnClickListener(v -> {
            // Membuka pemilih berkas SAF Android dengan tipe */* agar berkas .task tidak terblokir
            speaker.speak("Membuka pengelola berkas. Pilih berkas model berakhiran titik task.");
            try {
                modelPickerLauncher.launch(new String[]{"*/*"});
            } catch (Exception e) {
                tvImportProgress.setVisibility(View.VISIBLE);
                tvImportProgress.setText("Gagal membuka pemilih berkas: " + e.getMessage());
                speaker.speak("Gagal membuka pemilih berkas di HP ini.");
            }
        });

        btnDeleteModel.setOnClickListener(v -> confirmDeleteLocalModel());
        btnLoadModel.setOnClickListener(v -> loadOrTestLocalModel());

        btnBackupData.setOnClickListener(v -> {
            saveFields();
            String dateStr = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
            String fileName = "KobraOS_Backup_" + dateStr + ".json";
            speaker.speak("Pilih lokasi penyimpanan untuk berkas cadangan data.");
            try {
                backupLauncher.launch(fileName);
            } catch (Exception e) {
                tvBackupStatus.setTextColor(0xFFFF5252);
                tvBackupStatus.setText("Gagal membuka penyimpanan: " + e.getMessage());
                speaker.speak("Gagal membuka penyimpanan untuk mencadangkan data.");
            }
        });

        btnRestoreData.setOnClickListener(v -> {
            speaker.speak("Pilih berkas cadangan Kobra OS berakhiran titik j s o n.");
            try {
                restoreLauncher.launch(new String[]{"application/json", "*/*"});
            } catch (Exception e) {
                tvBackupStatus.setTextColor(0xFFFF5252);
                tvBackupStatus.setText("Gagal membuka pemilih berkas: " + e.getMessage());
                speaker.speak("Gagal membuka pemilih berkas cadangan.");
            }
        });

        btnCheckUpdate.setOnClickListener(v -> startOtaUpdateCheck());

        if (btnViewCrashLog != null) {
            btnViewCrashLog.setOnClickListener(v -> showLastCrashLogDialog());
        }

        back.setOnClickListener(v -> {
            saveFields();
            finish();
        });
    }

    private void showLastCrashLogDialog() {
        String log = config != null ? config.getLastCrashLog() : "";
        if (log == null || log.trim().isEmpty()) {
            String msg = "Belum ada catatan crash error yang terekam. Aplikasi berjalan normal.";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            speaker.speak(msg);
            new AlertDialog.Builder(this)
                    .setTitle("Log Crash Terakhir")
                    .setMessage(msg)
                    .setPositiveButton("Tutup", null)
                    .show();
            return;
        }
        speaker.speak("Menampilkan catatan log crash terakhir.");
        new AlertDialog.Builder(this)
                .setTitle("Log Crash Terakhir")
                .setMessage(log)
                .setPositiveButton("Salin Log Error", (d, w) -> {
                    try {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("KobraCrashLog", log));
                            Toast.makeText(this, "Log error disalin ke clipboard.", Toast.LENGTH_SHORT).show();
                            speaker.speak("Log error berhasil disalin.");
                        }
                    } catch (Throwable ignored) {}
                })
                .setNeutralButton("Hapus Log", (d, w) -> {
                    config.clearLastCrashLog();
                    Toast.makeText(this, "Log crash dibersihkan.", Toast.LENGTH_SHORT).show();
                    speaker.speak("Log crash telah dibersihkan.");
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    private void startOtaUpdateCheck() {
        btnCheckUpdate.setEnabled(false);
        tvUpdateStatus.setTextColor(0xFFFFFF00);
        tvUpdateStatus.setText("Memeriksa rilis terbaru Kobra OS (versi saat ini " + BuildConfig.VERSION_NAME + ")...");
        speaker.speak("Memeriksa pembaruan Kobra OS dari GitHub.");

        appUpdater.checkLatestRelease(new AppUpdater.CheckCallback() {
            @Override public void onUpdateAvailable(AppUpdater.ReleaseInfo info) {
                if (isFinishing() || isDestroyed()) return;
                btnCheckUpdate.setEnabled(true);
                tvUpdateStatus.setTextColor(0xFF00E676);
                tvUpdateStatus.setText("Tersedia versi baru: " + info.tagName + " — " + info.releaseNotesForSpeech);
                speaker.speak("Pembaruan Kobra OS tersedia, versi " + info.tagName
                        + ". Catatan rilis: " + info.releaseNotesForSpeech);

                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Pembaruan Kobra OS " + info.tagName)
                        .setMessage("Versi baru " + info.tagName + " tersedia.\n\nCatatan rilis:\n" + info.releaseNotesForSpeech)
                        .setPositiveButton("Unduh & Pasang", (d, w) -> beginApkDownload(info))
                        .setNegativeButton("Nanti", null)
                        .show();
            }

            @Override public void onUpToDate(String currentVersion, String latestTag) {
                if (isFinishing() || isDestroyed()) return;
                btnCheckUpdate.setEnabled(true);
                tvUpdateStatus.setTextColor(0xFF00E676);
                String msg = "Kobra OS sudah menggunakan versi terbaru (" + currentVersion
                        + (latestTag.isEmpty() ? "" : ", rilis: " + latestTag) + ").";
                tvUpdateStatus.setText(msg);
                speaker.speak(msg);
            }

            @Override public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                btnCheckUpdate.setEnabled(true);
                tvUpdateStatus.setTextColor(0xFFFF5252);
                tvUpdateStatus.setText(message);
                speaker.speak(message);
            }
        });
    }

    private void beginApkDownload(AppUpdater.ReleaseInfo info) {
        tvUpdateStatus.setTextColor(0xFFFFFF00);
        tvUpdateStatus.setText("Mengunduh APK " + info.tagName + "...");
        appUpdater.downloadAndInstall(info, new AppUpdater.DownloadCallback() {
            @Override public void onStarted(long downloadId, String fileName) {
                if (isFinishing() || isDestroyed()) return;
                tvUpdateStatus.setText("Mengunduh " + fileName + " lewat DownloadManager...");
                speaker.speak("Mengunduh paket pembaruan Kobra OS. Harap tunggu hingga unduhan selesai.");
            }

            @Override public void onCompleted(Uri apkUri, File apkFile) {
                if (isFinishing() || isDestroyed()) return;
                tvUpdateStatus.setTextColor(0xFF00E676);
                tvUpdateStatus.setText("Unduhan selesai. Membuka installer paket...");
                boolean launched = AppUpdater.launchPackageInstaller(SettingsActivity.this, apkUri);
                if (!launched) {
                    pendingInstallUri = apkUri;
                    tvUpdateStatus.setTextColor(0xFFFFAB40);
                    tvUpdateStatus.setText("Aktifkan izin instal aplikasi untuk Kobra OS lalu kembali ke halaman ini.");
                    speaker.speak("Aktifkan izin instal aplikasi dari sumber ini untuk Kobra OS, lalu kembali untuk melanjutkan pemasangan.");
                } else {
                    pendingInstallUri = null;
                    speaker.speak("Installer pembaruan Kobra OS dibuka.");
                }
            }

            @Override public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                tvUpdateStatus.setTextColor(0xFFFF5252);
                tvUpdateStatus.setText(message);
                speaker.speak(message);
            }
        });
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (screenHelper.handleKeyEvent(this, event, speaker)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (screenHelper.handleTouchEvent(this, ev, speaker)) return true;
        return super.dispatchTouchEvent(ev);
    }

    @Override protected void onResume() {
        super.onResume();
        updateModelStatus();
        if (pendingInstallUri != null && getPackageManager().canRequestPackageInstalls()) {
            Uri uri = pendingInstallUri;
            pendingInstallUri = null;
            AppUpdater.launchPackageInstaller(this, uri);
        }
    }

    @Override protected void onDestroy() {
        if (appUpdater != null) appUpdater.unregisterReceiver();
        executor.shutdown();
        super.onDestroy();
    }

    private void loadFieldsFromConfig() {
        loadingFields = true;
        etKey.setText(config.getApiKey());
        etModel.setText(config.getModel());
        etN8n.setText(config.getN8nUrl());
        etNotes.setText(config.getNotes());

        float rate = config.getSpeechRate();
        float pitch = config.getSpeechPitch();
        sbSpeechRate.setProgress(ttsValueToProgress(rate));
        sbSpeechPitch.setProgress(ttsValueToProgress(pitch));
        tvSpeechRate.setText(String.format(Locale.US, "Kecepatan Suara (Rate): %.1fx", rate));
        tvSpeechPitch.setText(String.format(Locale.US, "Nada Suara (Pitch): %.1fx", pitch));

        String style = config.getAssistantStyle();
        if (KobraConfig.STYLE_RINGKAS.equals(style)) {
            rbStyleRingkas.setChecked(true);
        } else if (KobraConfig.STYLE_DAERAH.equals(style)) {
            rbStyleDaerah.setChecked(true);
        } else {
            rbStyleRamah.setChecked(true);
        }

        boolean hc = config.isHighContrast();
        swHighContrast.setChecked(hc);
        applyHighContrastVisual(hc);
        loadingFields = false;
    }

    private String styleFromCheckedId(int checkedId) {
        if (checkedId == R.id.rbStyleRingkas) return KobraConfig.STYLE_RINGKAS;
        if (checkedId == R.id.rbStyleDaerah) return KobraConfig.STYLE_DAERAH;
        return KobraConfig.STYLE_RAMAH;
    }

    private void applyHighContrastVisual(boolean highContrast) {
        View root = findViewById(android.R.id.content);
        if (root != null) {
            root.setBackgroundColor(highContrast ? 0xFF000000 : 0xFF0A0A0A);
        }
        int textColor = highContrast ? 0xFFFFFF00 : 0xFFFFFFFF;
        etKey.setTextColor(textColor);
        etModel.setTextColor(textColor);
        etN8n.setTextColor(textColor);
        etNotes.setTextColor(textColor);
        tvSpeechRate.setTextColor(textColor);
        tvSpeechPitch.setTextColor(textColor);
        rbStyleRingkas.setTextColor(textColor);
        rbStyleRamah.setTextColor(textColor);
        rbStyleDaerah.setTextColor(textColor);
    }

    private static float progressToTtsValue(int progress) {
        return Math.max(0.5f, Math.min(2.0f, 0.5f + (progress * 0.1f)));
    }

    private static int ttsValueToProgress(float value) {
        return Math.max(0, Math.min(15, Math.round((value - 0.5f) * 10f)));
    }

    private void updateModelStatus() {
        LocalLlmEngine engine = LocalLlmEngine.getInstance(this);
        File modelFile = engine.getModelFile();
        boolean exists = modelFile.exists() && modelFile.length() > 1024 * 1024;
        btnDeleteModel.setEnabled(modelFile.exists());
        btnDeleteModel.setAlpha(modelFile.exists() ? 1.0f : 0.45f);
        btnLoadModel.setEnabled(exists);
        btnLoadModel.setAlpha(exists ? 1.0f : 0.45f);

        if (exists) {
            long mb = modelFile.length() / (1024 * 1024);
            if (engine.isInitialized()) {
                tvModelStatus.setText("Status model: Aktif & Siap (" + mb + " MB, " + LocalLlmEngine.MODEL_FILENAME + ")");
                tvModelStatus.setTextColor(0xFF00E676);
            } else if (engine.isSafeMode()) {
                String err = engine.getLastError().isEmpty() ? LocalLlmEngine.CRASH_SAFE_MODE_REASON : engine.getLastError();
                tvModelStatus.setText("Status model: " + err + " (" + mb + " MB)");
                tvModelStatus.setTextColor(0xFFFFAB40);
            } else {
                tvModelStatus.setText("Status model: Terpasang (" + mb + " MB, lazy-load hemat RAM)");
                tvModelStatus.setTextColor(0xFF38BDF8);
            }
        } else {
            tvModelStatus.setText("Status model: Belum terpasang / Mode Aman (Pilih berkas .task dari HP)");
            tvModelStatus.setTextColor(0xFFFFEB3B);
        }
    }

    private void confirmDeleteLocalModel() {
        LocalLlmEngine engine = LocalLlmEngine.getInstance(this);
        File modelFile = engine.getModelFile();
        if (!modelFile.exists()) {
            speaker.speak("Tidak ada berkas model lokal yang tersimpan.");
            updateModelStatus();
            return;
        }
        long mb = modelFile.length() / (1024 * 1024);
        new AlertDialog.Builder(this)
                .setTitle("Hapus Model Lokal?")
                .setMessage("Hapus berkas model " + LocalLlmEngine.MODEL_FILENAME + " (" + mb + " MB) dari penyimpanan internal dan reset Mode Aman?")
                .setPositiveButton("Hapus", (d, w) -> {
                    boolean deleted = engine.deleteLocalModel();
                    updateModelStatus();
                    tvImportProgress.setVisibility(View.VISIBLE);
                    if (deleted) {
                        tvImportProgress.setTextColor(0xFF00E676);
                        tvImportProgress.setText("Model lokal berhasil dihapus. Ruang penyimpanan dibebaskan.");
                        speaker.speak("Berkas model lokal berhasil dihapus dan Mode Aman direset.");
                    } else {
                        tvImportProgress.setTextColor(0xFFFF5252);
                        tvImportProgress.setText("Gagal menghapus berkas model lokal.");
                        speaker.speak("Gagal menghapus berkas model lokal.");
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void loadOrTestLocalModel() {
        LocalLlmEngine engine = LocalLlmEngine.getInstance(this);
        if (!engine.isModelPresent()) {
            speaker.speak("Berkas model lokal belum terpasang. Impor berkas titik task terlebih dahulu.");
            updateModelStatus();
            return;
        }
        btnLoadModel.setEnabled(false);
        btnImportModel.setEnabled(false);
        pbImportModel.setVisibility(View.VISIBLE);
        pbImportModel.setIndeterminate(true);
        tvImportProgress.setVisibility(View.VISIBLE);
        tvImportProgress.setTextColor(0xFFFFFF00);
        tvImportProgress.setText("Memuat model lokal (batas 512 token, dengan Crash-Guard aktif)...");
        speaker.speak("Mencoba memuat model lokal ke memori dengan perlindungan crash guard.");

        executor.execute(() -> {
            boolean ready = engine.forceInitEngine();
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                btnImportModel.setEnabled(true);
                pbImportModel.setVisibility(View.GONE);
                updateModelStatus();
                if (ready) {
                    tvImportProgress.setTextColor(0xFF00E676);
                    tvImportProgress.setText("Model lokal berhasil dimuat dan siap digunakan (512 token).");
                    speaker.speak("Model AI offline berhasil dimuat dan siap digunakan.");
                } else {
                    String err = engine.getLastError().isEmpty() ? LocalLlmEngine.CRASH_SAFE_MODE_REASON : engine.getLastError();
                    tvImportProgress.setTextColor(0xFFFFAB40);
                    tvImportProgress.setText(err);
                    speaker.speak("Model tidak dapat dimuat ke RAM. Kobra tetap aman di Mode Aman.");
                }
            });
        });
    }

    private void exportBackupToUri(Uri uri) {
        tvBackupStatus.setTextColor(0xFFFFFF00);
        tvBackupStatus.setText("Mengekspor cadangan data...");
        executor.execute(() -> {
            try {
                String json = config.exportBackupJson();
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new IOException("Tidak dapat menulis ke berkas tujuan.");
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    tvBackupStatus.setTextColor(0xFF00E676);
                    tvBackupStatus.setText("Cadangan JSON berhasil disimpan (tanpa berkas .task).");
                    speaker.speak("Cadangan data Kobra OS berhasil disimpan.");
                });
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "Kesalahan ekspor";
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    tvBackupStatus.setTextColor(0xFFFF5252);
                    tvBackupStatus.setText("Gagal mencadangkan data: " + msg);
                    speaker.speak("Gagal mencadangkan data. " + msg);
                });
            }
        });
    }

    private void importBackupFromUri(Uri uri) {
        String name = queryFileName(uri);
        if (!name.isEmpty() && !name.toLowerCase(Locale.ROOT).endsWith(".json")) {
            tvBackupStatus.setTextColor(0xFFFF5252);
            tvBackupStatus.setText("Berkas bukan JSON: " + name);
            speaker.speak("Berkas yang dipilih bukan berkas cadangan JSON.");
            return;
        }
        tvBackupStatus.setTextColor(0xFFFFFF00);
        tvBackupStatus.setText("Memulihkan data dari berkas cadangan...");
        executor.execute(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) throw new IOException("Berkas cadangan tidak dapat dibuka.");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) {
                    baos.write(buf, 0, len);
                }
                String json = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                int count = config.importBackupJson(json);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    loadFieldsFromConfig();
                    speaker.applyVoiceSettings();
                    tvBackupStatus.setTextColor(0xFF00E676);
                    tvBackupStatus.setText("Berhasil memulihkan " + count + " entri pengaturan dan kontak darurat.");
                    speaker.speak("Data cadangan berhasil dipulihkan sebanyak " + count + " pengaturan.");
                });
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "Format tidak valid";
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    tvBackupStatus.setTextColor(0xFFFF5252);
                    tvBackupStatus.setText("Gagal memulihkan data: " + msg);
                    speaker.speak("Gagal memulihkan data cadangan. " + msg);
                });
            }
        });
    }

    private void processSelectedModelFile(Uri uri) {
        String fileName = queryFileName(uri);

        // Validasi ekstensi .task
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".task")) {
            speaker.speak("Berkas yang dipilih bukan berkas model titik task. Silakan pilih berkas yang berakhiran titik task.");
            tvImportProgress.setVisibility(View.VISIBLE);
            tvImportProgress.setTextColor(0xFFFF5252);
            tvImportProgress.setText("Gagal: Berkas bukan bertipe .task (" + (!fileName.isEmpty() ? fileName : "format tidak dikenal") + ").");
            return;
        }

        long fileSize = queryFileSize(uri);
        final long finalFileSize = fileSize;

        speaker.speak("Memulai penyalinan model titik task dari penyimpanan HP. Harap tunggu sebentar.");
        btnImportModel.setEnabled(false);
        pbImportModel.setVisibility(View.VISIBLE);
        pbImportModel.setProgress(0);
        tvImportProgress.setVisibility(View.VISIBLE);
        tvImportProgress.setTextColor(0xFFFFFF00);
        tvImportProgress.setText("Menyiapkan penyalinan: " + fileName);

        executor.execute(() -> {
            File filesDir = getFilesDir();
            File destFile = new File(filesDir, LocalLlmEngine.MODEL_FILENAME);
            File tempFile = new File(filesDir, LocalLlmEngine.MODEL_FILENAME + ".tmp");

            if (tempFile.exists()) {
                tempFile.delete();
            }

            try (InputStream is = getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(tempFile)) {

                if (is == null) {
                    throw new IOException("Tidak dapat membaca data berkas yang dipilih.");
                }

                byte[] buffer = new byte[65536]; // Buffer 64 KB untuk penyalinan cepat & hemat memori
                int read;
                long totalRead = 0;
                long lastUpdateTime = 0;

                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                    totalRead += read;

                    long now = System.currentTimeMillis();
                    if (now - lastUpdateTime > 350) {
                        lastUpdateTime = now;
                        final int percent = finalFileSize > 0 ? (int) ((totalRead * 100) / finalFileSize) : -1;
                        final long mb = totalRead / (1024 * 1024);
                        mainHandler.post(() -> {
                            if (percent >= 0) {
                                pbImportModel.setIndeterminate(false);
                                pbImportModel.setProgress(percent);
                                tvImportProgress.setText("Menyalin: " + percent + "% (" + mb + " MB)");
                            } else {
                                pbImportModel.setIndeterminate(true);
                                tvImportProgress.setText("Menyalin: " + mb + " MB...");
                            }
                        });
                    }
                }
                fos.flush();

                // Hapus berkas tujuan lama terlebih dahulu untuk mencegah penumpukan ruang penyimpanan
                if (destFile.exists()) {
                    destFile.delete();
                }

                // Ganti nama berkas sementara menjadi nama resmi yang dibaca LocalLlmEngine
                if (!tempFile.renameTo(destFile)) {
                    copyFile(tempFile, destFile);
                    tempFile.delete();
                }

                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    btnImportModel.setEnabled(true);
                    pbImportModel.setVisibility(View.GONE);
                    LocalLlmEngine engine = LocalLlmEngine.getInstance(SettingsActivity.this);
                    engine.close();
                    config.clearLlmSafeMode();
                    updateModelStatus();
                    long mb = destFile.length() / (1024 * 1024);
                    tvImportProgress.setTextColor(0xFF00E676);
                    tvImportProgress.setText("Model tersimpan (" + mb + " MB). Ketuk Muat / Uji Model Lokal atau langsung pakai di AI Chat.");
                    speaker.speak("Penyalinan model offline selesai. Model siap dimuat saat Anda membuka obrolan atau menekan tombol Muat Model Lokal.");
                });

            } catch (Exception e) {
                if (tempFile.exists()) tempFile.delete();
                final String err = e.getMessage() != null ? e.getMessage() : "Kesalahan sistem";
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    btnImportModel.setEnabled(true);
                    pbImportModel.setVisibility(View.GONE);
                    tvImportProgress.setTextColor(0xFFFF5252);
                    tvImportProgress.setText("Gagal mengimpor model: " + err);
                    speaker.speak("Gagal mengimpor model offline. " + err);
                });
            }
        });
    }

    private String queryFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (col >= 0) {
                        result = cursor.getString(col);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (result == null && uri.getPath() != null) {
            int cut = uri.getPath().lastIndexOf('/');
            result = cut != -1 ? uri.getPath().substring(cut + 1) : uri.getPath();
        }
        return result != null ? result : "";
    }

    private long queryFileSize(Uri uri) {
        long size = -1;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int col = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (col >= 0) {
                        size = cursor.getLong(col);
                    }
                }
            } catch (Exception ignored) {}
        }
        return size;
    }

    private void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private void saveFields() {
        config.setApiKey(etKey.getText().toString());
        config.setModel(etModel.getText().toString());
        config.setN8nUrl(etN8n.getText().toString());
        config.setNotes(etNotes.getText().toString());
        config.setSpeechRate(progressToTtsValue(sbSpeechRate.getProgress()));
        config.setSpeechPitch(progressToTtsValue(sbSpeechPitch.getProgress()));
        config.setHighContrast(swHighContrast.isChecked());
        config.setAssistantStyle(styleFromCheckedId(rgAssistantStyle.getCheckedRadioButtonId()));
        speaker.applyVoiceSettings();
    }
}
