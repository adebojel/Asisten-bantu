package com.asisten.bantu;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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
import androidx.core.app.ActivityCompat;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pusat Pengaturan Terpadu Kobra OS (5 Kategori Modern):
 * 1) Izin Sistem & Asisten Digital Default
 * 2) Kecerdasan Buatan & Model (Gemini API Key, OpenRouter, Gemma 3 1B .task)
 * 3) Memori AI Lokal (Persistent Memory Manager CRUD)
 * 4) Kontrol Suara, Nomor Layar, Grid, Air Gestures, Barge-In & TTS
 * 5) Keamanan, Daily Briefing, Guardian Check-In, Backup/Restore JSON, OTA & Crash Log
 */
public class SettingsActivity extends AppCompatActivity {

    private EditText etGeminiKey, etKey, etModel, etN8n, etNotes, etCustomAssistantName;
    private TextView tvStatus, tvSystemPermSummary, tvModelStatus, tvImportProgress;
    private TextView tvSpeechRate, tvSpeechPitch, tvBackupStatus, tvUpdateStatus, tvMemoryCountInfo, tvFloatingOrbAlpha;
    private TextView tvActiveVoiceSummary;
    private Button btnImportModel, btnDeleteModel, btnLoadModel, btnTestTts;
    private Button btnBackupData, btnRestoreData, btnCheckUpdate, btnViewCrashLog;
    private Button btnPresetKobra, btnPresetArka, btnPresetBima, btnPresetNala, btnPresetCakra, btnPresetTara, btnPresetKustom, btnApplyCustomVoice;
    private LinearLayout layoutMemoryList, layoutCustomVoiceConfig;
    private ProgressBar pbImportModel;
    private SeekBar sbSpeechRate, sbSpeechPitch, sbFloatingOrbAlpha;
    private SwitchCompat swFloatingOrb, swFloatingOrbAutoHide;
    private SwitchCompat swGlobalScreenReader, swVisualNumbers, swGridOverlay, swAirGestures, swBargeIn;
    private SwitchCompat swHighContrast, swFallDetection, swPocketLock, swDailyBriefing;
    private RadioGroup rgAssistantStyle, rgGuardianInterval;
    private RadioButton rbStyleRingkas, rbStyleRamah, rbStyleDaerah;
    private RadioButton rbGuardOff, rbGuard15, rbGuard30, rbGuard60;
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
                if (uri != null) processSelectedModelFile(uri);
            });

    private final ActivityResultLauncher<String> backupLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri != null) exportBackupToUri(uri);
            });

    private final ActivityResultLauncher<String[]> restoreLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) importBackupFromUri(uri);
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        config = new KobraConfig(this);
        speaker = Speaker.get(this);

        final View contentRoot = ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        if (config.hasSecurityPin() && !getIntent().getBooleanExtra(CaregiverActivity.EXTRA_PIN_UNLOCKED, false)) {
            if (contentRoot != null) contentRoot.setVisibility(View.INVISIBLE);
            SecurityAndPinHelper.requirePinIfSet(this, config, speaker, "Pusat Pengaturan Kobra OS",
                    new SecurityAndPinHelper.PinCallback() {
                        @Override public void onUnlocked() {
                            if (contentRoot != null) contentRoot.setVisibility(View.VISIBLE);
                        }
                        @Override public void onCancelled() {
                            finish();
                        }
                    });
        }

        // Bind views
        etGeminiKey = findViewById(R.id.etGeminiApiKey);
        etKey = findViewById(R.id.etApiKey);
        etModel = findViewById(R.id.etModel);
        etN8n = findViewById(R.id.etN8nUrl);
        etNotes = findViewById(R.id.etNotes);
        tvStatus = findViewById(R.id.tvSettingsStatus);
        tvSystemPermSummary = findViewById(R.id.tvSystemPermSummary);
        tvModelStatus = findViewById(R.id.tvModelStatus);
        tvImportProgress = findViewById(R.id.tvImportProgress);
        tvSpeechRate = findViewById(R.id.tvSpeechRate);
        tvSpeechPitch = findViewById(R.id.tvSpeechPitch);
        tvBackupStatus = findViewById(R.id.tvBackupStatus);
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus);
        tvMemoryCountInfo = findViewById(R.id.tvMemoryCountInfo);
        tvActiveVoiceSummary = findViewById(R.id.tvActiveVoiceSummary);
        layoutMemoryList = findViewById(R.id.layoutMemoryList);
        layoutCustomVoiceConfig = findViewById(R.id.layoutCustomVoiceConfig);
        etCustomAssistantName = findViewById(R.id.etCustomAssistantName);

        btnPresetKobra = findViewById(R.id.btnPresetKobra);
        btnPresetArka = findViewById(R.id.btnPresetArka);
        btnPresetBima = findViewById(R.id.btnPresetBima);
        btnPresetNala = findViewById(R.id.btnPresetNala);
        btnPresetCakra = findViewById(R.id.btnPresetCakra);
        btnPresetTara = findViewById(R.id.btnPresetTara);
        btnPresetKustom = findViewById(R.id.btnPresetKustom);
        btnApplyCustomVoice = findViewById(R.id.btnApplyCustomVoice);

        btnImportModel = findViewById(R.id.btnImportModel);
        btnDeleteModel = findViewById(R.id.btnDeleteModel);
        btnLoadModel = findViewById(R.id.btnLoadModel);
        pbImportModel = findViewById(R.id.pbImportModel);
        sbSpeechRate = findViewById(R.id.sbSpeechRate);
        sbSpeechPitch = findViewById(R.id.sbSpeechPitch);

        swFloatingOrb = findViewById(R.id.swFloatingOrb);
        swFloatingOrbAutoHide = findViewById(R.id.swFloatingOrbAutoHide);
        tvFloatingOrbAlpha = findViewById(R.id.tvFloatingOrbAlpha);
        sbFloatingOrbAlpha = findViewById(R.id.sbFloatingOrbAlpha);
        swGlobalScreenReader = findViewById(R.id.swGlobalScreenReader);
        swVisualNumbers = findViewById(R.id.swVisualNumbers);
        swGridOverlay = findViewById(R.id.swGridOverlay);
        swAirGestures = findViewById(R.id.swAirGestures);
        swBargeIn = findViewById(R.id.swBargeIn);
        swHighContrast = findViewById(R.id.swHighContrast);
        swFallDetection = findViewById(R.id.swFallDetection);
        swPocketLock = findViewById(R.id.swPocketLock);
        swDailyBriefing = findViewById(R.id.swDailyBriefing);

        rgAssistantStyle = findViewById(R.id.rgAssistantStyle);
        rbStyleRingkas = findViewById(R.id.rbStyleRingkas);
        rbStyleRamah = findViewById(R.id.rbStyleRamah);
        rbStyleDaerah = findViewById(R.id.rbStyleDaerah);

        rgGuardianInterval = findViewById(R.id.rgGuardianInterval);
        rbGuardOff = findViewById(R.id.rbGuardOff);
        rbGuard15 = findViewById(R.id.rbGuard15);
        rbGuard30 = findViewById(R.id.rbGuard30);
        rbGuard60 = findViewById(R.id.rbGuard60);

        btnTestTts = findViewById(R.id.btnTestTts);
        btnBackupData = findViewById(R.id.btnBackupData);
        btnRestoreData = findViewById(R.id.btnRestoreData);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        btnViewCrashLog = findViewById(R.id.btnViewCrashLog);
        appUpdater = new AppUpdater(this);
        tvUpdateStatus.setText("Versi terpasang: " + BuildConfig.VERSION_NAME);

        // Card 1: System Permissions & Default Digital Assistant
        findViewById(R.id.btnSettingsPermissions).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            try {
                ActivityCompat.requestPermissions(this, Perms.required(), 100);
            } catch (Throwable t) {
                Toast.makeText(this, "Gagal meminta izin: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btnSettingsAccessibility).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            KobraScreenHelper.safeStartActivity(this, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                    speaker, "Pengaturan Aksesibilitas tidak tersedia di HP ini.");
        });
        findViewById(R.id.btnSettingsOverlay).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            KobraScreenHelper.safeStartActivity(this, i, speaker, "Pengaturan Overlay tidak tersedia.");
        });
        findViewById(R.id.btnSettingsNotifAccess).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            KobraScreenHelper.safeStartActivity(this, new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                    speaker, "Pengaturan Akses Notifikasi tidak tersedia.");
        });
        findViewById(R.id.btnSettingsBattery).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            askBatteryExemption();
        });
        findViewById(R.id.btnSettingsStartService).setOnClickListener(v -> {
            HapticFeedback.vibrateSuccess(this);
            KobraForegroundService.start(this);
            speaker.speak("Layanan latar belakang Kobra diaktifkan.");
        });
        findViewById(R.id.btnSettingsLauncher).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            KobraScreenHelper.safeStartActivity(this, new Intent(Settings.ACTION_HOME_SETTINGS),
                    speaker, "Pengaturan Launcher Utama tidak didukung langsung.");
        });
        findViewById(R.id.btnSettingsDefaultAssistant).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            Intent voiceIntent = new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS);
            if (!KobraScreenHelper.safeStartActivity(this, voiceIntent, speaker, "")) {
                KobraScreenHelper.safeStartActivity(this, new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                        speaker, "Pengaturan Asisten Digital Default tidak tersedia di perangkat ini.");
            }
            speaker.speak("Pilih Kobra OS sebagai Asisten Digital Default Anda.");
        });

        loadFieldsFromConfig();
        refreshSystemPermSummary();
        refreshMemoryListUi();

        // Card 3: Local Memory Manager
        findViewById(R.id.btnAddMemory).setOnClickListener(v -> showMemoryDialog(null));
        findViewById(R.id.btnClearMemories).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Hapus Semua Memori AI?")
                    .setMessage("Semua fakta pribadi yang tersimpan di Memori Lokal akan dihapus.")
                    .setPositiveButton("Hapus Semua", (d, w) -> {
                        LocalMemoryStore.clearAll(this);
                        refreshMemoryListUi();
                        speaker.speak("Semua memori lokal telah dihapus.");
                    })
                    .setNegativeButton("Batal", null)
                    .show();
        });

        // Card 4: 7 Preset Nama Nusantara & Karakter Suara Ala Gemini
        if (btnPresetKobra != null) btnPresetKobra.setOnClickListener(v -> selectVoicePreset(VoiceProfileManager.PRESET_KOBRA));
        if (btnPresetArka != null) btnPresetArka.setOnClickListener(v -> selectVoicePreset(VoiceProfileManager.PRESET_ARKA));
        if (btnPresetBima != null) btnPresetBima.setOnClickListener(v -> selectVoicePreset(VoiceProfileManager.PRESET_BIMA));
        if (btnPresetNala != null) btnPresetNala.setOnClickListener(v -> selectVoicePreset(VoiceProfileManager.PRESET_NALA));
        if (btnPresetCakra != null) btnPresetCakra.setOnClickListener(v -> selectVoicePreset(VoiceProfileManager.PRESET_CAKRA));
        if (btnPresetTara != null) btnPresetTara.setOnClickListener(v -> selectVoicePreset(VoiceProfileManager.PRESET_TARA));
        if (btnPresetKustom != null) btnPresetKustom.setOnClickListener(v -> selectVoicePreset(VoiceProfileManager.PRESET_KUSTOM));
        if (btnApplyCustomVoice != null) btnApplyCustomVoice.setOnClickListener(v -> applyCustomVoiceProfile());

        // Card 5: Voice & Sensors listeners
        swFloatingOrb.setOnCheckedChangeListener((b, isChecked) -> {
            if (loadingFields) return;
            config.setFloatingOrbEnabled(isChecked);
            if (isChecked) {
                AsistenAccessibilityService.showFloatingOrb();
                speaker.speak("Gelembung mikrofon melayang global diaktifkan.");
            } else {
                AsistenAccessibilityService.refreshFloatingOrbSettings();
                speaker.speak("Gelembung mikrofon melayang global dimatikan.");
            }
        });
        swFloatingOrbAutoHide.setOnCheckedChangeListener((b, isChecked) -> {
            if (loadingFields) return;
            config.setFloatingOrbAutoHide(isChecked);
            AsistenAccessibilityService.refreshFloatingOrbSettings();
            speaker.speak(isChecked
                    ? "Mode otomatis sembunyi ke pinggir layar diaktifkan."
                    : "Mode otomatis sembunyi ke pinggir layar dimatikan.");
        });
        sbFloatingOrbAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = 25 + progress;
                tvFloatingOrbAlpha.setText("Tingkat Transparansi Gelembung: " + percent + "%");
                if (fromUser) {
                    config.setFloatingOrbAlpha(percent / 100f);
                    AsistenAccessibilityService.refreshFloatingOrbSettings();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        swGlobalScreenReader.setOnCheckedChangeListener((b, isChecked) -> {
            if (loadingFields) return;
            UnifiedAccessibilityEngine.setGlobalReaderEnabled(this, isChecked);
            speaker.speak(isChecked ? "Pembaca layar global diaktifkan." : "Pembaca layar global dimatikan.");
        });
        swVisualNumbers.setOnCheckedChangeListener((b, isChecked) -> {
            if (!loadingFields) config.setVisualNumbersEnabled(isChecked);
        });
        swGridOverlay.setOnCheckedChangeListener((b, isChecked) -> {
            if (!loadingFields) config.setGridOverlayEnabled(isChecked);
        });
        swAirGestures.setOnCheckedChangeListener((b, isChecked) -> {
            if (!loadingFields) config.setAirGesturesEnabled(isChecked);
        });
        swBargeIn.setOnCheckedChangeListener((b, isChecked) -> {
            if (!loadingFields) config.setBargeInEnabled(isChecked);
        });
        swHighContrast.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setHighContrast(isChecked);
            applyHighContrastVisual(isChecked);
        });

        sbSpeechRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float rate = progressToRateValue(progress);
                tvSpeechRate.setText(String.format(Locale.US, "Kecepatan Suara (Rate): %.1fx", rate));
                if (fromUser && !loadingFields) {
                    promoteToCustomIfNeeded();
                    config.setSpeechRate(rate);
                    VoiceProfileManager.syncAllTts(SettingsActivity.this);
                    refreshVoicePresetUi();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbSpeechPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float pitch = progressToPitchValue(progress);
                tvSpeechPitch.setText(String.format(Locale.US, "Nada Suara (Pitch): %.1fx", pitch));
                if (fromUser && !loadingFields) {
                    promoteToCustomIfNeeded();
                    config.setSpeechPitch(pitch);
                    VoiceProfileManager.syncAllTts(SettingsActivity.this);
                    refreshVoicePresetUi();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        rgAssistantStyle.setOnCheckedChangeListener((group, checkedId) -> {
            if (loadingFields) return;
            config.setAssistantStyle(styleFromCheckedId(checkedId));
            speaker.speak("Gaya bahasa asisten diatur ke mode " + config.getAssistantStyleLabel() + ".");
        });

        btnTestTts.setOnClickListener(v -> {
            saveFields();
            speaker.speak(VoiceProfileManager.formatSampleGreeting(this));
        });

        // Card 5: Guardian SOS & Daily Briefing
        findViewById(R.id.btnSettingsSos).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            KobraScreenHelper.safeStartActivity(this, new Intent(this, SosSetupActivity.class),
                    speaker, "Gagal membuka Kontak Darurat SOS.");
        });
        swFallDetection.setOnCheckedChangeListener((b, isChecked) -> {
            if (!loadingFields) config.setFallDetectionEnabled(isChecked);
        });
        swPocketLock.setOnCheckedChangeListener((b, isChecked) -> {
            if (!loadingFields) config.setPocketLockEnabled(isChecked);
        });
        swDailyBriefing.setOnCheckedChangeListener((b, isChecked) -> {
            if (!loadingFields) config.setDailyBriefingEnabled(isChecked);
        });
        rgGuardianInterval.setOnCheckedChangeListener((group, checkedId) -> {
            if (loadingFields) return;
            int min = guardianMinutesFromId(checkedId);
            GuardianManager.scheduleCheckIn(this, min);
            speaker.speak(min > 0
                    ? "Guardian Check-In dijadwalkan setiap " + min + " menit."
                    : "Guardian Check-In berkala dimatikan.");
        });
        findViewById(R.id.btnTestDailyBriefing).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            GuardianManager.speakDailyBriefing(this);
        });
        findViewById(R.id.btnTestGuardianCheck).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            GuardianManager.triggerManualCheckInNow(this);
        });

        // Save, Test AI, Model, Backup, OTA, Crash Log
        Button save = findViewById(R.id.btnSaveSettings);
        Button test = findViewById(R.id.btnTestAi);
        ImageButton back = findViewById(R.id.btnBackSettings);

        save.setOnClickListener(v -> {
            saveFields();
            tvStatus.setText("Seluruh pengaturan tersimpan.");
            speaker.speak("Seluruh pengaturan, API key, sensor, dan keamanan telah disimpan.");
        });

        test.setOnClickListener(v -> {
            saveFields();
            tvStatus.setText("Menguji koneksi AI...");
            String activeName = VoiceProfileManager.getActiveAssistantName(this);
            String gemini = CommandRouter.resolveGeminiApiKey(this);
            if (!gemini.isEmpty()) {
                executor.execute(() -> {
                    String res = CommandRouter.callGeminiBlocking(this, "Perkenalkan dirimu sebagai " + activeName + " dari Kobra OS dalam satu kalimat singkat.", null);
                    mainHandler.post(() -> {
                        if (res != null && !res.isEmpty()) {
                            tvStatus.setText("Gemini menjawab: " + res);
                            speaker.speak(res);
                        } else {
                            testOpenRouterFallback();
                        }
                    });
                });
            } else {
                testOpenRouterFallback();
            }
        });

        btnImportModel.setOnClickListener(v -> {
            speaker.speak("Membuka pengelola berkas. Pilih berkas model berakhiran titik task.");
            try {
                modelPickerLauncher.launch(new String[]{"*/*"});
            } catch (Exception e) {
                tvImportProgress.setVisibility(View.VISIBLE);
                tvImportProgress.setText("Gagal membuka pemilih berkas: " + e.getMessage());
            }
        });
        btnDeleteModel.setOnClickListener(v -> confirmDeleteLocalModel());
        btnLoadModel.setOnClickListener(v -> loadOrTestLocalModel());

        btnBackupData.setOnClickListener(v -> {
            saveFields();
            String dateStr = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
            speaker.speak("Pilih lokasi penyimpanan untuk berkas cadangan data.");
            try {
                backupLauncher.launch("KobraOS_Backup_" + dateStr + ".json");
            } catch (Exception e) {
                tvBackupStatus.setText("Gagal membuka penyimpanan: " + e.getMessage());
            }
        });
        btnRestoreData.setOnClickListener(v -> {
            speaker.speak("Pilih berkas cadangan Kobra OS berakhiran titik j s o n.");
            try {
                restoreLauncher.launch(new String[]{"application/json", "*/*"});
            } catch (Exception e) {
                tvBackupStatus.setText("Gagal membuka pemilih berkas: " + e.getMessage());
            }
        });

        btnCheckUpdate.setOnClickListener(v -> startOtaUpdateCheck());
        btnViewCrashLog.setOnClickListener(v -> showLastCrashLogDialog());

        back.setOnClickListener(v -> {
            saveFields();
            finish();
        });
    }

    private void testOpenRouterFallback() {
        new AiClient(config).chat(AiClient.SYSTEM_TUNANETRA,
                "Perkenalkan dirimu dalam satu kalimat singkat.", new AiClient.Callback() {
                    @Override public void onResult(String text) {
                        tvStatus.setText("OpenRouter menjawab: " + text);
                        speaker.speak(text);
                    }
                    @Override public void onError(String message) {
                        tvStatus.setText("Gagal uji AI: " + message);
                        speaker.speak(message);
                    }
                });
    }

    private void refreshSystemPermSummary() {
        if (tvSystemPermSummary == null) return;
        boolean mic = Perms.has(this, Manifest.permission.RECORD_AUDIO);
        boolean cam = Perms.has(this, Manifest.permission.CAMERA);
        boolean acc = AsistenAccessibilityService.getInstance() != null;
        boolean notif = KobraNotificationService.isConnected();
        boolean ovl = Settings.canDrawOverlays(this);
        tvSystemPermSummary.setText(
                "Mikrofon: " + (mic ? "Aktif" : "Belum") +
                " • Kamera: " + (cam ? "Aktif" : "Belum") +
                " • Aksesibilitas: " + (acc ? "Aktif" : "Mati") +
                "\nOverlay: " + (ovl ? "Aktif" : "Belum") +
                " • Notifikasi: " + (notif ? "Aktif" : "Belum")
        );
    }

    private void refreshMemoryListUi() {
        if (layoutMemoryList == null) return;
        layoutMemoryList.removeAllViews();
        List<LocalMemoryStore.MemoryItem> items = LocalMemoryStore.listMemories(this);
        tvMemoryCountInfo.setText(items.isEmpty()
                ? "Belum ada memori tersimpan. Tambah manual atau katakan ingat bahwa..."
                : "Total " + items.size() + " memori tersimpan (selalu disertakan dalam konteks AI).");

        for (LocalMemoryStore.MemoryItem item : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(18, 14, 18, 14);
            row.setBackgroundResource(R.drawable.bg_telemetry_pill);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 10;
            row.setLayoutParams(lp);

            TextView tv = new TextView(this);
            tv.setText("[" + item.getCategory() + "] " + item.getText());
            tv.setTextColor(0xFFE2E8F0);
            tv.setTextSize(12f);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(tvLp);

            Button btnEdit = new Button(this);
            btnEdit.setText("Ubah");
            btnEdit.setAllCaps(false);
            btnEdit.setTextSize(11f);
            btnEdit.setTextColor(0xFF00E5FF);
            btnEdit.setBackgroundResource(R.drawable.bg_glass_button_secondary);
            btnEdit.setOnClickListener(v -> showMemoryDialog(item));

            Button btnDel = new Button(this);
            btnDel.setText("Hapus");
            btnDel.setAllCaps(false);
            btnDel.setTextSize(11f);
            btnDel.setTextColor(0xFFFFE4E6);
            btnDel.setBackgroundResource(R.drawable.bg_glass_button_danger);
            btnDel.setOnClickListener(v -> {
                LocalMemoryStore.deleteMemory(this, item.getId());
                refreshMemoryListUi();
                speaker.speak("Memori dihapus.");
            });

            row.addView(tv);
            row.addView(btnEdit);
            row.addView(btnDel);
            layoutMemoryList.addView(row);
        }
    }

    private void showMemoryDialog(LocalMemoryStore.MemoryItem existing) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(36, 20, 36, 10);

        final EditText etText = new EditText(this);
        etText.setHint("Contoh: Saya suka jawaban singkat atau Saya alergi debu");
        if (existing != null) etText.setText(existing.getText());

        final EditText etCat = new EditText(this);
        etCat.setHint("Kategori (mis. Preferensi, Kesehatan, Keluarga)");
        etCat.setText(existing != null ? existing.getCategory() : "Preferensi");

        box.addView(etText);
        box.addView(etCat);

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Tambah Memori AI Lokal" : "Ubah Memori AI Lokal")
                .setView(box)
                .setPositiveButton("Simpan", (d, w) -> {
                    String txt = etText.getText().toString().trim();
                    String cat = etCat.getText().toString().trim();
                    if (txt.isEmpty()) return;
                    if (existing == null) {
                        LocalMemoryStore.addMemory(this, txt, cat.isEmpty() ? "Preferensi" : cat);
                        speaker.speak("Memori baru disimpan.");
                    } else {
                        LocalMemoryStore.updateMemory(this, existing.getId(), txt, cat.isEmpty() ? "Preferensi" : cat);
                        speaker.speak("Memori diperbarui.");
                    }
                    refreshMemoryListUi();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void askBatteryExemption() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Toast.makeText(this, "Kobra sudah bebas dari pembatasan baterai.", Toast.LENGTH_SHORT).show();
                speaker.speak("Kobra sudah bebas dari pembatasan baterai.");
                return;
            }
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            KobraScreenHelper.safeStartActivity(this, i, speaker, "Pengaturan baterai tidak tersedia.");
        } catch (Throwable ignored) {}
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
        tvUpdateStatus.setTextColor(0xFFFFD600);
        tvUpdateStatus.setText("Memeriksa rilis terbaru Kobra OS (versi saat ini " + BuildConfig.VERSION_NAME + ")...");
        speaker.speak("Memeriksa pembaruan Kobra OS dari GitHub.");

        appUpdater.checkLatestRelease(new AppUpdater.CheckCallback() {
            @Override public void onUpdateAvailable(AppUpdater.ReleaseInfo info) {
                if (isFinishing() || isDestroyed()) return;
                btnCheckUpdate.setEnabled(true);
                tvUpdateStatus.setTextColor(0xFF00E5FF);
                tvUpdateStatus.setText("Tersedia versi baru: " + info.tagName + " - " + info.releaseNotesForSpeech);
                speaker.speak("Pembaruan Kobra OS tersedia, versi " + info.tagName);

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
                tvUpdateStatus.setTextColor(0xFF00E5FF);
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
        tvUpdateStatus.setTextColor(0xFFFFD600);
        tvUpdateStatus.setText("Mengunduh APK " + info.tagName + "...");
        appUpdater.downloadAndInstall(info, new AppUpdater.DownloadCallback() {
            @Override public void onStarted(long downloadId, String fileName) {
                if (isFinishing() || isDestroyed()) return;
                tvUpdateStatus.setText("Mengunduh " + fileName + " lewat DownloadManager...");
                speaker.speak("Mengunduh paket pembaruan Kobra OS.");
            }

            @Override public void onCompleted(Uri apkUri, File apkFile) {
                if (isFinishing() || isDestroyed()) return;
                tvUpdateStatus.setTextColor(0xFF00E5FF);
                tvUpdateStatus.setText("Unduhan selesai. Membuka installer paket...");
                boolean launched = AppUpdater.launchPackageInstaller(SettingsActivity.this, apkUri);
                if (!launched) {
                    pendingInstallUri = apkUri;
                    tvUpdateStatus.setTextColor(0xFFFFD600);
                    tvUpdateStatus.setText("Aktifkan izin instal aplikasi untuk Kobra OS lalu kembali ke halaman ini.");
                } else {
                    pendingInstallUri = null;
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
        refreshSystemPermSummary();
        refreshMemoryListUi();
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
        etGeminiKey.setText(config.getGeminiApiKey());
        etKey.setText(config.getApiKey());
        etModel.setText(config.getModel());
        etN8n.setText(config.getN8nUrl());
        etNotes.setText(config.getNotes());

        swFloatingOrb.setChecked(config.isFloatingOrbEnabled());
        swFloatingOrbAutoHide.setChecked(config.isFloatingOrbAutoHide());
        int orbPercent = Math.max(25, Math.min(100, Math.round(config.getFloatingOrbAlpha() * 100f)));
        sbFloatingOrbAlpha.setProgress(orbPercent - 25);
        tvFloatingOrbAlpha.setText("Tingkat Transparansi Gelembung: " + orbPercent + "%");

        swGlobalScreenReader.setChecked(UnifiedAccessibilityEngine.isGlobalReaderEnabled(this));
        swVisualNumbers.setChecked(config.isVisualNumbersEnabled());
        swGridOverlay.setChecked(config.isGridOverlayEnabled());
        swAirGestures.setChecked(config.isAirGesturesEnabled());
        swBargeIn.setChecked(config.isBargeInEnabled());
        swFallDetection.setChecked(config.isFallDetectionEnabled());
        swPocketLock.setChecked(config.isPocketLockEnabled());
        swDailyBriefing.setChecked(config.isDailyBriefingEnabled());

        int gMin = config.getGuardianIntervalMinutes();
        if (gMin == 15) rbGuard15.setChecked(true);
        else if (gMin == 30) rbGuard30.setChecked(true);
        else if (gMin >= 60) rbGuard60.setChecked(true);
        else rbGuardOff.setChecked(true);

        float rate = VoiceProfileManager.clampRate(config.getSpeechRate());
        float pitch = VoiceProfileManager.clampPitch(config.getSpeechPitch());
        sbSpeechRate.setProgress(rateValueToProgress(rate));
        sbSpeechPitch.setProgress(pitchValueToProgress(pitch));
        tvSpeechRate.setText(String.format(Locale.US, "Kecepatan Suara (Rate): %.1fx", rate));
        tvSpeechPitch.setText(String.format(Locale.US, "Nada Suara (Pitch): %.1fx", pitch));

        if (etCustomAssistantName != null) {
            String customName = config.getCustomAssistantName();
            etCustomAssistantName.setText(customName.isEmpty() ? VoiceProfileManager.getActiveAssistantName(this) : customName);
        }
        refreshVoicePresetUi();

        String style = config.getAssistantStyle();
        if (KobraConfig.STYLE_RINGKAS.equals(style)) rbStyleRingkas.setChecked(true);
        else if (KobraConfig.STYLE_DAERAH.equals(style)) rbStyleDaerah.setChecked(true);
        else rbStyleRamah.setChecked(true);

        boolean hc = config.isHighContrast();
        swHighContrast.setChecked(hc);
        applyHighContrastVisual(hc);
        loadingFields = false;
    }

    private void selectVoicePreset(String presetId) {
        HapticFeedback.vibrateAction(this);
        VoiceProfileManager.VoicePreset preset = VoiceProfileManager.applyPreset(this, presetId);
        loadingFields = true;
        float rate = VoiceProfileManager.clampRate(config.getSpeechRate());
        float pitch = VoiceProfileManager.clampPitch(config.getSpeechPitch());
        sbSpeechRate.setProgress(rateValueToProgress(rate));
        sbSpeechPitch.setProgress(pitchValueToProgress(pitch));
        tvSpeechRate.setText(String.format(Locale.US, "Kecepatan Suara (Rate): %.1fx", rate));
        tvSpeechPitch.setText(String.format(Locale.US, "Nada Suara (Pitch): %.1fx", pitch));
        if (etCustomAssistantName != null) {
            if (preset.isCustom()) {
                String currentCustom = config.getCustomAssistantName();
                etCustomAssistantName.setText(currentCustom.isEmpty() ? preset.getDefaultWakeWord() : currentCustom);
            } else {
                etCustomAssistantName.setText(preset.getDefaultWakeWord());
            }
        }
        loadingFields = false;
        refreshVoicePresetUi();
        String greeting = VoiceProfileManager.formatSampleGreeting(this);
        if (tvStatus != null) {
            tvStatus.setText("Karakter suara aktif: " + VoiceProfileManager.getActiveAssistantName(this) + " (" + preset.getDescription() + ")");
        }
        speaker.speak(greeting);
    }

    private void applyCustomVoiceProfile() {
        HapticFeedback.vibrateAction(this);
        String rawName = etCustomAssistantName != null ? etCustomAssistantName.getText().toString() : "";
        float pitch = progressToPitchValue(sbSpeechPitch.getProgress());
        float rate = progressToRateValue(sbSpeechRate.getProgress());
        VoiceProfileManager.saveCustomProfile(this, rawName, pitch, rate);
        if (etCustomAssistantName != null) {
            etCustomAssistantName.setText(VoiceProfileManager.getActiveAssistantName(this));
        }
        refreshVoicePresetUi();
        String greeting = VoiceProfileManager.formatSampleGreeting(this);
        if (tvStatus != null) {
            tvStatus.setText("Profil suara kustom diterapkan: " + VoiceProfileManager.getActiveAssistantName(this));
        }
        speaker.speak(greeting);
    }

    private void promoteToCustomIfNeeded() {
        VoiceProfileManager.VoicePreset current = VoiceProfileManager.getActivePreset(this);
        if (!current.isCustom()) {
            String preservedName = VoiceProfileManager.getActiveAssistantName(this);
            config.setCustomAssistantName(preservedName);
            config.setVoiceProfileId(VoiceProfileManager.PRESET_KUSTOM);
            if (etCustomAssistantName != null) {
                etCustomAssistantName.setText(preservedName);
            }
        }
    }

    private void refreshVoicePresetUi() {
        VoiceProfileManager.VoicePreset preset = VoiceProfileManager.getActivePreset(this);
        String activeName = VoiceProfileManager.getActiveAssistantName(this);
        float pitch = VoiceProfileManager.clampPitch(preset.isCustom() ? config.getSpeechPitch() : preset.getPitch());
        float rate = VoiceProfileManager.clampRate(preset.isCustom() ? config.getSpeechRate() : preset.getRate());

        if (tvActiveVoiceSummary != null) {
            tvActiveVoiceSummary.setText(String.format(
                    Locale.US,
                    "Aktif: %s (%s • Pitch %.1fx • Rate %.1fx)",
                    activeName, preset.getDescription(), pitch, rate
            ));
        }
        if (layoutCustomVoiceConfig != null) {
            layoutCustomVoiceConfig.setVisibility(preset.isCustom() ? View.VISIBLE : View.GONE);
        }

        stylePresetButton(btnPresetKobra, VoiceProfileManager.PRESET_KOBRA.equals(preset.getId()), false);
        stylePresetButton(btnPresetArka, VoiceProfileManager.PRESET_ARKA.equals(preset.getId()), false);
        stylePresetButton(btnPresetBima, VoiceProfileManager.PRESET_BIMA.equals(preset.getId()), false);
        stylePresetButton(btnPresetNala, VoiceProfileManager.PRESET_NALA.equals(preset.getId()), false);
        stylePresetButton(btnPresetCakra, VoiceProfileManager.PRESET_CAKRA.equals(preset.getId()), false);
        stylePresetButton(btnPresetTara, VoiceProfileManager.PRESET_TARA.equals(preset.getId()), false);
        stylePresetButton(btnPresetKustom, VoiceProfileManager.PRESET_KUSTOM.equals(preset.getId()), true);
    }

    private void stylePresetButton(Button btn, boolean selected, boolean isCustomBtn) {
        if (btn == null) return;
        btn.setBackgroundResource(selected ? R.drawable.bg_persona_chip_active : R.drawable.bg_persona_chip_idle);
        if (selected) {
            btn.setTextColor(0xFF00E5FF);
        } else {
            btn.setTextColor(isCustomBtn ? 0xFFFFD600 : 0xFFE2E8F0);
        }
    }

    private int guardianMinutesFromId(int id) {
        if (id == R.id.rbGuard15) return 15;
        if (id == R.id.rbGuard30) return 30;
        if (id == R.id.rbGuard60) return 60;
        return 0;
    }

    private String styleFromCheckedId(int checkedId) {
        if (checkedId == R.id.rbStyleRingkas) return KobraConfig.STYLE_RINGKAS;
        if (checkedId == R.id.rbStyleDaerah) return KobraConfig.STYLE_DAERAH;
        return KobraConfig.STYLE_RAMAH;
    }

    private void applyHighContrastVisual(boolean highContrast) {
        int textColor = highContrast ? 0xFFFFFF00 : 0xFFE2E8F0;
        etGeminiKey.setTextColor(textColor);
        etKey.setTextColor(textColor);
        etModel.setTextColor(textColor);
        etN8n.setTextColor(textColor);
        etNotes.setTextColor(textColor);
        if (etCustomAssistantName != null) etCustomAssistantName.setTextColor(textColor);
        tvSpeechRate.setTextColor(textColor);
        tvSpeechPitch.setTextColor(textColor);
    }

    private static float progressToRateValue(int progress) {
        return Math.max(0.5f, Math.min(2.0f, 0.5f + (progress * 0.1f)));
    }

    private static int rateValueToProgress(float value) {
        return Math.max(0, Math.min(15, Math.round((value - 0.5f) * 10f)));
    }

    private static float progressToPitchValue(int progress) {
        return Math.max(0.5f, Math.min(1.8f, 0.5f + (progress * 0.1f)));
    }

    private static int pitchValueToProgress(float value) {
        return Math.max(0, Math.min(13, Math.round((value - 0.5f) * 10f)));
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
                tvModelStatus.setTextColor(0xFF00E5FF);
            } else if (engine.isSafeMode()) {
                String err = engine.getLastError().isEmpty() ? LocalLlmEngine.CRASH_SAFE_MODE_REASON : engine.getLastError();
                tvModelStatus.setText("Status model: " + err + " (" + mb + " MB)");
                tvModelStatus.setTextColor(0xFFFFD600);
            } else {
                tvModelStatus.setText("Status model: Terpasang (" + mb + " MB, lazy-load hemat RAM)");
                tvModelStatus.setTextColor(0xFF00E5FF);
            }
        } else {
            tvModelStatus.setText("Status model: Belum terpasang / Mode Aman (Pilih berkas .task dari HP)");
            tvModelStatus.setTextColor(0xFFFFD600);
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
                .setMessage("Hapus berkas model " + LocalLlmEngine.MODEL_FILENAME + " (" + mb + " MB)?")
                .setPositiveButton("Hapus", (d, w) -> {
                    boolean deleted = engine.deleteLocalModel();
                    updateModelStatus();
                    tvImportProgress.setVisibility(View.VISIBLE);
                    tvImportProgress.setTextColor(deleted ? 0xFF00E5FF : 0xFFFF5252);
                    tvImportProgress.setText(deleted ? "Model lokal berhasil dihapus." : "Gagal menghapus berkas model.");
                    speaker.speak(deleted ? "Model lokal berhasil dihapus." : "Gagal menghapus berkas model.");
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void loadOrTestLocalModel() {
        LocalLlmEngine engine = LocalLlmEngine.getInstance(this);
        if (!engine.isModelPresent()) {
            speaker.speak("Berkas model lokal belum terpasang.");
            updateModelStatus();
            return;
        }
        btnLoadModel.setEnabled(false);
        btnImportModel.setEnabled(false);
        pbImportModel.setVisibility(View.VISIBLE);
        pbImportModel.setIndeterminate(true);
        tvImportProgress.setVisibility(View.VISIBLE);
        tvImportProgress.setTextColor(0xFFFFD600);
        tvImportProgress.setText("Memuat model lokal (batas 512 token, Crash-Guard aktif)...");
        speaker.speak("Mencoba memuat model lokal ke memori.");

        executor.execute(() -> {
            boolean ready = engine.forceInitEngine();
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                btnImportModel.setEnabled(true);
                pbImportModel.setVisibility(View.GONE);
                updateModelStatus();
                if (ready) {
                    tvImportProgress.setTextColor(0xFF00E5FF);
                    tvImportProgress.setText("Model lokal berhasil dimuat dan siap digunakan.");
                    speaker.speak("Model AI offline berhasil dimuat.");
                } else {
                    String err = engine.getLastError().isEmpty() ? LocalLlmEngine.CRASH_SAFE_MODE_REASON : engine.getLastError();
                    tvImportProgress.setTextColor(0xFFFFD600);
                    tvImportProgress.setText(err);
                    speaker.speak("Model tidak dapat dimuat ke RAM. Kobra tetap aman di Mode Aman.");
                }
            });
        });
    }

    private void exportBackupToUri(Uri uri) {
        tvBackupStatus.setTextColor(0xFFFFD600);
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
                    tvBackupStatus.setTextColor(0xFF00E5FF);
                    tvBackupStatus.setText("Cadangan JSON berhasil disimpan.");
                    speaker.speak("Cadangan data Kobra OS berhasil disimpan.");
                });
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "Kesalahan ekspor";
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    tvBackupStatus.setTextColor(0xFFFF5252);
                    tvBackupStatus.setText("Gagal mencadangkan data: " + msg);
                });
            }
        });
    }

    private void importBackupFromUri(Uri uri) {
        String name = queryFileName(uri);
        if (!name.isEmpty() && !name.toLowerCase(Locale.ROOT).endsWith(".json")) {
            tvBackupStatus.setTextColor(0xFFFF5252);
            tvBackupStatus.setText("Berkas bukan JSON: " + name);
            return;
        }
        tvBackupStatus.setTextColor(0xFFFFD600);
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
                    refreshMemoryListUi();
                    speaker.applyVoiceSettings();
                    tvBackupStatus.setTextColor(0xFF00E5FF);
                    tvBackupStatus.setText("Berhasil memulihkan " + count + " entri pengaturan.");
                    speaker.speak("Data cadangan berhasil dipulihkan.");
                });
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "Format tidak valid";
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    tvBackupStatus.setTextColor(0xFFFF5252);
                    tvBackupStatus.setText("Gagal memulihkan data: " + msg);
                });
            }
        });
    }

    private void processSelectedModelFile(Uri uri) {
        String fileName = queryFileName(uri);
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".task")) {
            speaker.speak("Berkas yang dipilih bukan berkas model titik task.");
            tvImportProgress.setVisibility(View.VISIBLE);
            tvImportProgress.setTextColor(0xFFFF5252);
            tvImportProgress.setText("Gagal: Berkas bukan bertipe .task.");
            return;
        }

        final long finalFileSize = queryFileSize(uri);
        btnImportModel.setEnabled(false);
        pbImportModel.setVisibility(View.VISIBLE);
        pbImportModel.setProgress(0);
        tvImportProgress.setVisibility(View.VISIBLE);
        tvImportProgress.setTextColor(0xFFFFD600);
        tvImportProgress.setText("Menyalin: " + fileName);

        executor.execute(() -> {
            File filesDir = getFilesDir();
            File destFile = new File(filesDir, LocalLlmEngine.MODEL_FILENAME);
            File tempFile = new File(filesDir, LocalLlmEngine.MODEL_FILENAME + ".tmp");
            if (tempFile.exists()) tempFile.delete();

            try (InputStream is = getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(tempFile)) {
                if (is == null) throw new IOException("Tidak dapat membaca berkas.");
                byte[] buffer = new byte[65536];
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
                if (destFile.exists()) destFile.delete();
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
                    tvImportProgress.setTextColor(0xFF00E5FF);
                    tvImportProgress.setText("Model tersimpan (" + mb + " MB).");
                    speaker.speak("Penyalinan model offline selesai.");
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
                    if (col >= 0) result = cursor.getString(col);
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
                    if (col >= 0) size = cursor.getLong(col);
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
        String gKey = etGeminiKey.getText().toString().trim();
        config.setGeminiApiKey(gKey);
        CommandRouter.setUserGeminiApiKey(this, gKey);
        config.setApiKey(etKey.getText().toString());
        config.setModel(etModel.getText().toString());
        config.setN8nUrl(etN8n.getText().toString());
        config.setNotes(etNotes.getText().toString());

        config.setFloatingOrbEnabled(swFloatingOrb.isChecked());
        config.setFloatingOrbAutoHide(swFloatingOrbAutoHide.isChecked());
        config.setFloatingOrbAlpha((25 + sbFloatingOrbAlpha.getProgress()) / 100f);

        UnifiedAccessibilityEngine.setGlobalReaderEnabled(this, swGlobalScreenReader.isChecked());
        config.setVisualNumbersEnabled(swVisualNumbers.isChecked());
        config.setGridOverlayEnabled(swGridOverlay.isChecked());
        config.setAirGesturesEnabled(swAirGestures.isChecked());
        config.setBargeInEnabled(swBargeIn.isChecked());
        config.setFallDetectionEnabled(swFallDetection.isChecked());
        config.setPocketLockEnabled(swPocketLock.isChecked());
        config.setDailyBriefingEnabled(swDailyBriefing.isChecked());

        config.setSpeechRate(progressToRateValue(sbSpeechRate.getProgress()));
        config.setSpeechPitch(progressToPitchValue(sbSpeechPitch.getProgress()));
        if (VoiceProfileManager.getActivePreset(this).isCustom() && etCustomAssistantName != null) {
            String customName = VoiceProfileManager.sanitizeWakeWord(etCustomAssistantName.getText().toString());
            if (!customName.isEmpty()) {
                config.setCustomAssistantName(customName);
            }
        }
        config.setHighContrast(swHighContrast.isChecked());
        config.setAssistantStyle(styleFromCheckedId(rgAssistantStyle.getCheckedRadioButtonId()));
        VoiceProfileManager.syncAllTts(this);
        AsistenAccessibilityService.refreshFloatingOrbSettings();
    }
}
