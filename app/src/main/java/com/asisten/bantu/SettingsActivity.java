package com.asisten.bantu;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
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
    private EditText etThemeBgHex, etThemeCardHex, etThemeAccentHex, etThemeTextHex, etAiThemePrompt;
    private TextView tvStatus, tvSystemPermSummary, tvModelStatus, tvImportProgress;
    private TextView tvSpeechRate, tvSpeechPitch, tvBackupStatus, tvUpdateStatus, tvMemoryCountInfo, tvFloatingOrbAlpha;
    private TextView tvActiveVoiceSummary, tvActiveThemeSummary, tvAutoContrastPreview, tvAiThemeStatus;
    private Button btnImportModel, btnDeleteModel, btnLoadModel, btnTestTts;
    private Button btnBackupData, btnRestoreData, btnCheckUpdate, btnViewCrashLog;
    private Button btnPresetKobra, btnPresetArka, btnPresetBima, btnPresetNala, btnPresetCakra, btnPresetTara, btnPresetKustom, btnApplyCustomVoice;
    private Button btnApplyCustomTheme, btnGenerateAiTheme;
    private View viewSwatchBg, viewSwatchCard, viewSwatchAccent, viewSwatchText;
    private LinearLayout cardThemeCyber, cardThemeHighContrast, cardThemeZamrud, cardThemeEmas;
    private LinearLayout cardThemeAmethyst, cardThemeMerah, cardThemeDaylight, cardThemeOled;
    private LinearLayout layoutMemoryList, layoutCustomVoiceConfig, layoutGeminiLiveVoiceChoices;
    private TextView tvGeminiLiveVoiceSummary;
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
        WindowInsetsHelper.applySystemBarInsets(this, true);
        ThemeManager.applyTheme(this);
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
        layoutGeminiLiveVoiceChoices = findViewById(R.id.layoutGeminiLiveVoiceChoices);
        tvGeminiLiveVoiceSummary = findViewById(R.id.tvGeminiLiveVoiceSummary);
        setupGeminiLiveVoiceChoices();
        etCustomAssistantName = findViewById(R.id.etCustomAssistantName);

        btnPresetKobra = findViewById(R.id.btnPresetKobra);
        btnPresetArka = findViewById(R.id.btnPresetArka);
        btnPresetBima = findViewById(R.id.btnPresetBima);
        btnPresetNala = findViewById(R.id.btnPresetNala);
        btnPresetCakra = findViewById(R.id.btnPresetCakra);
        btnPresetTara = findViewById(R.id.btnPresetTara);
        btnPresetKustom = findViewById(R.id.btnPresetKustom);
        btnApplyCustomVoice = findViewById(R.id.btnApplyCustomVoice);

        tvActiveThemeSummary = findViewById(R.id.tvActiveThemeSummary);
        tvAutoContrastPreview = findViewById(R.id.tvAutoContrastPreview);
        tvAiThemeStatus = findViewById(R.id.tvAiThemeStatus);
        etThemeBgHex = findViewById(R.id.etThemeBgHex);
        etThemeCardHex = findViewById(R.id.etThemeCardHex);
        etThemeAccentHex = findViewById(R.id.etThemeAccentHex);
        etThemeTextHex = findViewById(R.id.etThemeTextHex);
        etAiThemePrompt = findViewById(R.id.etAiThemePrompt);
        viewSwatchBg = findViewById(R.id.viewSwatchBg);
        viewSwatchCard = findViewById(R.id.viewSwatchCard);
        viewSwatchAccent = findViewById(R.id.viewSwatchAccent);
        viewSwatchText = findViewById(R.id.viewSwatchText);
        btnApplyCustomTheme = findViewById(R.id.btnApplyCustomTheme);
        btnGenerateAiTheme = findViewById(R.id.btnGenerateAiTheme);
        cardThemeCyber = findViewById(R.id.cardThemeCyber);
        cardThemeHighContrast = findViewById(R.id.cardThemeHighContrast);
        cardThemeZamrud = findViewById(R.id.cardThemeZamrud);
        cardThemeEmas = findViewById(R.id.cardThemeEmas);
        cardThemeAmethyst = findViewById(R.id.cardThemeAmethyst);
        cardThemeMerah = findViewById(R.id.cardThemeMerah);
        cardThemeDaylight = findViewById(R.id.cardThemeDaylight);
        cardThemeOled = findViewById(R.id.cardThemeOled);

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

        // Card 5: Manajemen Tema Multi-Varian, Kustom Hex & Generator Tema AI
        setupThemeEditorControls();

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
            if (loadingFields) return;
            config.setHighContrast(isChecked);
            if (isChecked) {
                selectThemePreset(ThemeManager.PRESET_HIGH_CONTRAST, false);
            } else if (ThemeManager.PRESET_HIGH_CONTRAST.equals(config.getThemeId())) {
                selectThemePreset(ThemeManager.PRESET_KOBRA_CYBER, false);
            } else {
                ThemeManager.applyTheme(this);
                refreshThemeUi();
                refreshVoicePresetUi();
            }
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
            String providerSummary = OnlineAiRouter.getActiveProviderSummary(this);
            tvStatus.setText("Menguji koneksi " + providerSummary + "...");
            OnlineAiRouter.testConnection(this, new OnlineAiRouter.Callback() {
                @Override public void onResult(String text, String providerLabel) {
                    if (isFinishing() || isDestroyed()) return;
                    tvStatus.setText(providerLabel + " menjawab: " + text);
                    speaker.speak(text);
                }

                @Override public void onError(String message) {
                    if (isFinishing() || isDestroyed()) return;
                    tvStatus.setText("Gagal uji " + providerSummary + ": " + message);
                    speaker.speak(message);
                }
            });
        });

        btnImportModel.setOnClickListener(v -> {
            speaker.speak("Membuka pengelola berkas. Pilih model berformat task atau litertlm.");
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
        OnlineAiRouter.testConnection(this, new OnlineAiRouter.Callback() {
            @Override public void onResult(String text, String providerLabel) {
                if (isFinishing() || isDestroyed()) return;
                tvStatus.setText(providerLabel + " menjawab: " + text);
                speaker.speak(text);
            }

            @Override public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
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
            btnDel.setTag(ThemeManager.TAG_BTN_DANGER);
            btnDel.setOnClickListener(v -> {
                LocalMemoryStore.deleteMemory(this, item.getId());
                refreshMemoryListUi();
                speaker.speak("Memori dihapus.");
            });

            row.addView(tv);
            row.addView(btnEdit);
            row.addView(btnDel);
            ThemeManager.applyToDynamicView(this, row);
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
        ThemeManager.applyTheme(this);
        refreshThemeUi();
        refreshVoicePresetUi();
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
        populateThemeEditorFields(ThemeManager.getActiveTheme(this));
        loadingFields = false;
        ThemeManager.applyTheme(this);
        refreshThemeUi();
        refreshVoicePresetUi();
    }


    /**
     * Pilihan karakter suara yang mengikuti nama pada Gemini Live picker.
     * Nama API yang disimpan adalah padanan karakter dari katalog resmi Live API,
     * bukan klaim bahwa suara proprietary Gemini Android tersedia di TTS lokal.
     */
    private void setupGeminiLiveVoiceChoices() {
        if (layoutGeminiLiveVoiceChoices == null) return;
        layoutGeminiLiveVoiceChoices.removeAllViews();
        String selected = config.getGeminiLiveVoiceId();
        VoiceProfileManager.GeminiLiveVoiceProfile active =
                VoiceProfileManager.getGeminiLiveVoice(selected);
        if (tvGeminiLiveVoiceSummary != null) {
            tvGeminiLiveVoiceSummary.setText("Gaya aktif: " + active.getDisplayName()
                    + " • Referensi karakter: " + active.getApiVoiceName()
                    + "\nTTS lokal memakai mesin suara Android yang tersedia di HP; ini bukan salinan suara Gemini.");
        }

        for (int start = 0; start < VoiceProfileManager.GEMINI_LIVE_VOICES.size(); start += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = (int) (6 * getResources().getDisplayMetrics().density);
            for (int col = start; col < Math.min(start + 2, VoiceProfileManager.GEMINI_LIVE_VOICES.size()); col++) {
                VoiceProfileManager.GeminiLiveVoiceProfile profile =
                        VoiceProfileManager.GEMINI_LIVE_VOICES.get(col);
                Button choice = new Button(this);
                choice.setText(profile.getDisplayName() + "\n" + profile.getCharacter());
                choice.setAllCaps(false);
                choice.setTextSize(11f);
                choice.setMinHeight((int) (52 * getResources().getDisplayMetrics().density));
                choice.setBackgroundResource(profile.getId().equals(selected)
                        ? R.drawable.bg_persona_chip_active : R.drawable.bg_persona_chip_idle);
                choice.setTextColor(android.graphics.Color.parseColor(profile.getId().equals(selected)
                        ? "#00E5FF" : "#E2E8F0"));
                LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (col == start) buttonParams.rightMargin =
                        (int) (6 * getResources().getDisplayMetrics().density);
                row.addView(choice, buttonParams);
                choice.setOnClickListener(v -> {
                    config.setGeminiLiveVoiceId(profile.getId());
                    config.setSpeechPitch(profile.getPitch());
                    config.setSpeechRate(profile.getRate());
                    VoiceProfileManager.syncAllTts(SettingsActivity.this);
                    setupGeminiLiveVoiceChoices();
                    speaker.speak("Gaya suara " + profile.getDisplayName()
                            + " diterapkan ke suara lokal perangkat.");
                });
            }
            layoutGeminiLiveVoiceChoices.addView(row, rowParams);
        }
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

        ThemeManager.styleChip(btnPresetKobra, VoiceProfileManager.PRESET_KOBRA.equals(preset.getId()), false);
        ThemeManager.styleChip(btnPresetArka, VoiceProfileManager.PRESET_ARKA.equals(preset.getId()), false);
        ThemeManager.styleChip(btnPresetBima, VoiceProfileManager.PRESET_BIMA.equals(preset.getId()), false);
        ThemeManager.styleChip(btnPresetNala, VoiceProfileManager.PRESET_NALA.equals(preset.getId()), false);
        ThemeManager.styleChip(btnPresetCakra, VoiceProfileManager.PRESET_CAKRA.equals(preset.getId()), false);
        ThemeManager.styleChip(btnPresetTara, VoiceProfileManager.PRESET_TARA.equals(preset.getId()), false);
        ThemeManager.styleChip(btnPresetKustom, VoiceProfileManager.PRESET_KUSTOM.equals(preset.getId()), true);
    }

    private void setupThemeEditorControls() {
        bindThemePresetCard(cardThemeCyber, ThemeManager.PRESET_KOBRA_CYBER);
        bindThemePresetCard(cardThemeHighContrast, ThemeManager.PRESET_HIGH_CONTRAST);
        bindThemePresetCard(cardThemeZamrud, ThemeManager.PRESET_ZAMRUD);
        bindThemePresetCard(cardThemeEmas, ThemeManager.PRESET_EMAS);
        bindThemePresetCard(cardThemeAmethyst, ThemeManager.PRESET_AMETHYST);
        bindThemePresetCard(cardThemeMerah, ThemeManager.PRESET_MERAH);
        bindThemePresetCard(cardThemeDaylight, ThemeManager.PRESET_DAYLIGHT);
        bindThemePresetCard(cardThemeOled, ThemeManager.PRESET_OLED);

        TextWatcher hexWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!loadingFields) updateThemePreviewSwatches();
            }
        };
        if (etThemeBgHex != null) etThemeBgHex.addTextChangedListener(hexWatcher);
        if (etThemeCardHex != null) etThemeCardHex.addTextChangedListener(hexWatcher);
        if (etThemeAccentHex != null) etThemeAccentHex.addTextChangedListener(hexWatcher);
        if (etThemeTextHex != null) etThemeTextHex.addTextChangedListener(hexWatcher);

        bindColorPicker(R.id.btnPickBgColor, viewSwatchBg, etThemeBgHex, "Warna Latar Utama");
        bindColorPicker(R.id.btnPickCardColor, viewSwatchCard, etThemeCardHex, "Warna Kartu");
        bindColorPicker(R.id.btnPickAccentColor, viewSwatchAccent, etThemeAccentHex, "Warna Aksen & Tombol");
        bindColorPicker(R.id.btnPickTextColor, viewSwatchText, etThemeTextHex, "Warna Teks Utama");

        if (btnApplyCustomTheme != null) {
            btnApplyCustomTheme.setOnClickListener(v -> applyManualCustomTheme());
        }
        if (btnGenerateAiTheme != null) {
            btnGenerateAiTheme.setOnClickListener(v -> generateAiTheme());
        }
    }

    private void bindThemePresetCard(View card, String themeId) {
        if (card != null) {
            card.setOnClickListener(v -> selectThemePreset(themeId, true));
        }
    }

    private void bindColorPicker(int btnId, View swatch, EditText target, String title) {
        View.OnClickListener listener = v -> showColorPaletteDialog(target, title);
        View btn = findViewById(btnId);
        if (btn != null) btn.setOnClickListener(listener);
        if (swatch != null) swatch.setOnClickListener(listener);
    }

    private void selectThemePreset(String themeId, boolean speakAnnouncement) {
        HapticFeedback.vibrateAction(this);
        ThemeManager.KobraTheme applied = ThemeManager.applyPreset(this, themeId);
        loadingFields = true;
        if (swHighContrast != null) swHighContrast.setChecked(applied.isHighContrast());
        populateThemeEditorFields(applied);
        loadingFields = false;

        ThemeManager.applyTheme(this);
        refreshThemeUi();
        refreshVoicePresetUi();
        if (tvStatus != null) {
            tvStatus.setText("Tema aktif: " + applied.getName() + " (" + applied.getSubtitle() + ")");
        }
        if (speakAnnouncement) {
            speaker.speak("Tema " + applied.getName() + " diterapkan.");
        }
    }

    private void applyManualCustomTheme() {
        HapticFeedback.vibrateAction(this);
        ThemeManager.KobraTheme current = ThemeManager.getActiveTheme(this);
        String bg = ThemeManager.normalizeHex(etThemeBgHex != null ? etThemeBgHex.getText().toString() : "", current.getBgHex());
        String card = ThemeManager.normalizeHex(etThemeCardHex != null ? etThemeCardHex.getText().toString() : "", current.getCardHex());
        String accent = ThemeManager.normalizeHex(etThemeAccentHex != null ? etThemeAccentHex.getText().toString() : "", current.getAccentHex());
        String text = ThemeManager.normalizeHex(etThemeTextHex != null ? etThemeTextHex.getText().toString() : "", current.getTextHex());

        ThemeManager.KobraTheme applied = ThemeManager.applyCustomTheme(this, "Tema Kustom Lokal", bg, card, accent, text);
        loadingFields = true;
        if (swHighContrast != null) swHighContrast.setChecked(false);
        populateThemeEditorFields(applied);
        loadingFields = false;

        ThemeManager.applyTheme(this);
        refreshThemeUi();
        refreshVoicePresetUi();
        speaker.speak("Tema kustom lokal diterapkan dengan pelindung kontras otomatis.");
    }

    private void generateAiTheme() {
        HapticFeedback.vibrateAction(this);
        String prompt = etAiThemePrompt != null ? etAiThemePrompt.getText().toString().trim() : "";
        if (prompt.isEmpty()) {
            if (tvAiThemeStatus != null) {
                tvAiThemeStatus.setText("Tulis deskripsi tema dulu, misalnya: hutan malam tenang tombol hijau.");
            }
            speaker.speak("Tulis deskripsi tema terlebih dahulu sebelum membuat tema AI.");
            return;
        }
        btnGenerateAiTheme.setEnabled(false);
        if (tvAiThemeStatus != null) {
            tvAiThemeStatus.setText("Merancang palet warna kontras tinggi dari deskripsi Anda...");
        }
        speaker.speak("Sedang merancang tema tampilan dari deskripsi Anda.");

        ThemeManager.generateThemeWithAi(this, prompt, new ThemeManager.AiThemeCallback() {
            @Override public void onThemeGenerated(ThemeManager.KobraTheme theme, String sourceLabel) {
                if (isFinishing() || isDestroyed()) return;
                btnGenerateAiTheme.setEnabled(true);
                loadingFields = true;
                if (swHighContrast != null) swHighContrast.setChecked(false);
                populateThemeEditorFields(theme);
                loadingFields = false;

                ThemeManager.applyTheme(SettingsActivity.this);
                refreshThemeUi();
                refreshVoicePresetUi();
                String msg = "Tema '" + theme.getName() + "' aktif (" + sourceLabel + ").";
                if (tvAiThemeStatus != null) tvAiThemeStatus.setText(msg);
                if (tvStatus != null) tvStatus.setText(msg);
                speaker.speak("Tema " + theme.getName() + " berhasil dibuat dan langsung diterapkan.");
            }

            @Override public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                btnGenerateAiTheme.setEnabled(true);
                if (tvAiThemeStatus != null) tvAiThemeStatus.setText(message);
                speaker.speak(message);
            }
        });
    }

    private void showColorPaletteDialog(EditText target, String title) {
        if (target == null) return;
        List<ThemeManager.PaletteColor> palette = ThemeManager.getPaletteChoices();
        String[] labels = new String[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            ThemeManager.PaletteColor pc = palette.get(i);
            labels[i] = pc.getName() + " (" + pc.getHex() + ")";
        }
        new AlertDialog.Builder(this)
                .setTitle("Pilih " + title)
                .setItems(labels, (d, which) -> {
                    ThemeManager.PaletteColor chosen = palette.get(which);
                    target.setText(chosen.getHex());
                    updateThemePreviewSwatches();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void populateThemeEditorFields(ThemeManager.KobraTheme theme) {
        if (etThemeBgHex != null) etThemeBgHex.setText(theme.getBgHex());
        if (etThemeCardHex != null) etThemeCardHex.setText(theme.getCardHex());
        if (etThemeAccentHex != null) etThemeAccentHex.setText(theme.getAccentHex());
        if (etThemeTextHex != null) etThemeTextHex.setText(theme.getTextHex());
        updateThemePreviewSwatches();
    }

    private void updateThemePreviewSwatches() {
        ThemeManager.KobraTheme cur = ThemeManager.getActiveTheme(this);
        String bgHex = ThemeManager.normalizeHex(etThemeBgHex != null ? etThemeBgHex.getText().toString() : "", cur.getBgHex());
        String cardHex = ThemeManager.normalizeHex(etThemeCardHex != null ? etThemeCardHex.getText().toString() : "", cur.getCardHex());
        String accentHex = ThemeManager.normalizeHex(etThemeAccentHex != null ? etThemeAccentHex.getText().toString() : "", cur.getAccentHex());
        String textHex = ThemeManager.normalizeHex(etThemeTextHex != null ? etThemeTextHex.getText().toString() : "", cur.getTextHex());

        setSwatchColor(viewSwatchBg, ThemeManager.parseColorSafe(bgHex, cur.getBgColor()));
        setSwatchColor(viewSwatchCard, ThemeManager.parseColorSafe(cardHex, cur.getCardColor()));
        setSwatchColor(viewSwatchAccent, ThemeManager.parseColorSafe(accentHex, cur.getAccentColor()));
        setSwatchColor(viewSwatchText, ThemeManager.parseColorSafe(textHex, cur.getTextColor()));

        int accentColor = ThemeManager.parseColorSafe(accentHex, cur.getAccentColor());
        int forcedBtnText = ThemeManager.computeContrastTextColor(accentColor);
        String btnTextHex = ThemeManager.colorToHex(forcedBtnText);
        if (tvAutoContrastPreview != null) {
            String desc = ThemeManager.isColorLight(accentColor)
                    ? "Latar tombol terang -> Teks otomatis hitam pekat " + btnTextHex
                    : "Latar tombol gelap -> Teks otomatis putih bersih " + btnTextHex;
            tvAutoContrastPreview.setText("Auto-Contrast Guard: " + desc);
        }
    }

    private void setSwatchColor(View swatch, int color) {
        if (swatch == null) return;
        float d = getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(6f * d);
        gd.setColor(color);
        gd.setStroke((int) Math.max(1f, 1.5f * d), 0x80FFFFFF);
        swatch.setBackground(gd);
    }

    private void refreshThemeUi() {
        ThemeManager.KobraTheme active = ThemeManager.getActiveTheme(this);
        if (tvActiveThemeSummary != null) {
            tvActiveThemeSummary.setText("Tema Aktif: " + active.getName()
                    + " • Tombol " + active.getAccentHex()
                    + " (Teks " + active.getButtonTextHex() + ")");
        }
        stylePresetPreviewCard(cardThemeCyber, ThemeManager.PRESET_KOBRA_CYBER.equals(active.getId()), "#0F2138", "#00E5FF");
        stylePresetPreviewCard(cardThemeHighContrast, ThemeManager.PRESET_HIGH_CONTRAST.equals(active.getId()), "#121212", "#FFFFFF");
        stylePresetPreviewCard(cardThemeZamrud, ThemeManager.PRESET_ZAMRUD.equals(active.getId()), "#0A2921", "#00E676");
        stylePresetPreviewCard(cardThemeEmas, ThemeManager.PRESET_EMAS.equals(active.getId()), "#211B12", "#FFB300");
        stylePresetPreviewCard(cardThemeAmethyst, ThemeManager.PRESET_AMETHYST.equals(active.getId()), "#1E1136", "#D500F9");
        stylePresetPreviewCard(cardThemeMerah, ThemeManager.PRESET_MERAH.equals(active.getId()), "#2E0D11", "#FF5252");
        stylePresetPreviewCard(cardThemeDaylight, ThemeManager.PRESET_DAYLIGHT.equals(active.getId()), "#FFFFFF", "#0066CC");
        stylePresetPreviewCard(cardThemeOled, ThemeManager.PRESET_OLED.equals(active.getId()), "#0A0F18", "#40C4FF");
    }

    private void stylePresetPreviewCard(LinearLayout card, boolean selected, String bgHex, String borderHex) {
        if (card == null) return;
        float d = getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(14f * d);
        gd.setColor(Color.parseColor(bgHex));
        int strokeW = selected ? (int) Math.max(2f, 2.8f * d) : (int) Math.max(1f, 1.2f * d);
        int strokeColor = selected ? Color.parseColor(borderHex) : ThemeManager.adjustAlpha(Color.parseColor(borderHex), 110);
        gd.setStroke(strokeW, strokeColor);
        card.setBackground(gd);
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
        File taskFile = engine.getModelFile();
        LocalModelRegistry.ModelEntry liteEntry = LocalModelRegistry.findByFileName(
                "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm");
        File liteFile = LocalModelRegistry.fileFor(this, liteEntry);
        boolean taskExists = taskFile.exists() && taskFile.length() > 1024 * 1024;
        boolean liteExists = LocalModelRegistry.isValidFile(liteFile, liteEntry);

        btnDeleteModel.setEnabled(taskExists);
        btnDeleteModel.setAlpha(taskExists ? 1.0f : 0.45f);
        // Load/Test checks LiteRT-LM first, then legacy MediaPipe .task.
        boolean anyModelExists = taskExists || liteExists;
        btnLoadModel.setEnabled(anyModelExists);
        btnLoadModel.setAlpha(anyModelExists ? 1.0f : 0.45f);

        StringBuilder status = new StringBuilder("Model lokal: ");
        if (taskExists) {
            status.append("Gemma 1B .task tersedia (")
                    .append(taskFile.length() / (1024 * 1024)).append(" MB)");
            if (engine.isInitialized()) status.append(" — aktif");
            else if (engine.isSafeMode()) status.append(" — perlu pemeriksaan runtime");
        } else {
            status.append("Gemma .task belum terpasang");
        }
        status.append(" | ");
        if (liteExists) {
            status.append("Qwen3 0.6B .litertlm tersedia (")
                    .append(liteFile.length() / (1024 * 1024)).append(" MB; runtime LiteRT-LM)");
        } else {
            status.append("Qwen3 .litertlm belum diimpor");
        }
        status.append("\nRekomendasi perangkat: ").append(LocalModelRegistry.recommendation(this));
        tvModelStatus.setText(status.toString());
        tvModelStatus.setTextColor((taskExists || liteExists) ? 0xFF00E5FF : 0xFFFFD600);
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
        LocalModelRegistry.ModelEntry liteEntry = LocalModelRegistry.findByFileName(
                "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm");
        File liteFile = LocalModelRegistry.fileFor(this, liteEntry);

        if (LocalModelRegistry.isValidFile(liteFile, liteEntry)) {
            if (!LocalModelRegistry.canAttemptLiteRtModel(this)) {
                tvImportProgress.setVisibility(View.VISIBLE);
                tvImportProgress.setTextColor(0xFFFFD600);
                tvImportProgress.setText("Memori bebas belum cukup. Tutup aplikasi lain dan coba lagi; model tidak dimuat agar HP tetap stabil.");
                speaker.speak("Memori bebas belum cukup untuk menguji Qwen tiga. Tutup aplikasi lain lalu coba lagi.");
                return;
            }
            btnLoadModel.setEnabled(false);
            btnImportModel.setEnabled(false);
            pbImportModel.setVisibility(View.VISIBLE);
            pbImportModel.setIndeterminate(true);
            tvImportProgress.setVisibility(View.VISIBLE);
            tvImportProgress.setTextColor(0xFFFFD600);
            tvImportProgress.setText("Memuat dan menguji Qwen3 0.6B LiteRT-LM...");
            speaker.speak("Menguji model lokal ringan Qwen tiga.");
            LocalModelRuntimeManager.answerAsync(this,
                    "Jawab dalam bahasa Indonesia dengan satu kalimat: Apakah model lokal Kobra siap digunakan?",
                    (answer, error) -> {
                        mainHandler.post(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            btnLoadModel.setEnabled(true);
                            btnImportModel.setEnabled(true);
                            pbImportModel.setVisibility(View.GONE);
                            updateModelStatus();
                            if (answer != null && !answer.trim().isEmpty()) {
                                tvImportProgress.setTextColor(0xFF00E5FF);
                                tvImportProgress.setText("Qwen3 berhasil dimuat dan menjawab: " + answer);
                                speaker.speak("Qwen tiga berhasil diuji. " + answer);
                            } else {
                                tvImportProgress.setTextColor(0xFFFF5252);
                                tvImportProgress.setText("Qwen3 belum dapat digunakan: " + error);
                                speaker.speak("Uji Qwen tiga gagal. " + error);
                            }
                        });
                        return kotlin.Unit.INSTANCE;
                    });
            return;
        }

        LocalLlmEngine engine = LocalLlmEngine.getInstance(this);
        if (!engine.isModelPresent()) {
            speaker.speak("Belum ada model lokal yang dapat diuji. Impor model task atau litertlm terlebih dahulu.");
            updateModelStatus();
            return;
        }
        btnLoadModel.setEnabled(false);
        btnImportModel.setEnabled(false);
        pbImportModel.setVisibility(View.VISIBLE);
        pbImportModel.setIndeterminate(true);
        tvImportProgress.setVisibility(View.VISIBLE);
        tvImportProgress.setTextColor(0xFFFFD600);
        tvImportProgress.setText("Memuat model Gemma .task (token adaptif RAM + fallback CPU)...");
        speaker.speak("Mencoba memuat model Gemma lokal ke memori.");

        engine.initEngineAsync(true, (ready, message) -> {
            if (isFinishing() || isDestroyed()) return;
            btnLoadModel.setEnabled(true);
            btnImportModel.setEnabled(true);
            pbImportModel.setVisibility(View.GONE);
            updateModelStatus();
            if (ready) {
                tvImportProgress.setTextColor(0xFF00E5FF);
                tvImportProgress.setText("Gemma berhasil dimuat dan siap digunakan.");
                speaker.speak("Model AI offline Gemma berhasil dimuat.");
            } else {
                tvImportProgress.setTextColor(0xFFFFD600);
                tvImportProgress.setText(message);
                speaker.speak("Gemma tidak dapat dimuat ke RAM. Kobra tetap aman dengan perintah lokal.");
            }
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
        String sourceName = queryFileName(uri);
        LocalModelRegistry.ModelEntry entry = LocalModelRegistry.findByExtension(sourceName);
        if (entry == null) {
            tvImportProgress.setVisibility(View.VISIBLE);
            tvImportProgress.setTextColor(0xFFFF5252);
            tvImportProgress.setText("Format tidak didukung. Pilih model .task atau .litertlm.");
            speaker.speak("Format model tidak didukung. Pilih berkas task atau litertlm.");
            return;
        }

        final long sourceSize = queryFileSize(uri);
        if (sourceSize > 0 && sourceSize < entry.minimumFileBytes) {
            tvImportProgress.setVisibility(View.VISIBLE);
            tvImportProgress.setTextColor(0xFFFF5252);
            tvImportProgress.setText("Berkas model terlalu kecil atau tidak lengkap.");
            speaker.speak("Berkas model terlalu kecil atau tidak lengkap.");
            return;
        }

        btnImportModel.setEnabled(false);
        pbImportModel.setVisibility(View.VISIBLE);
        pbImportModel.setProgress(0);
        tvImportProgress.setVisibility(View.VISIBLE);
        tvImportProgress.setTextColor(0xFFFFD600);
        tvImportProgress.setText("Memeriksa ruang dan menyalin model " + entry.displayName);

        executor.execute(() -> {
            File destFile = LocalModelRegistry.fileFor(SettingsActivity.this, entry);
            File parent = destFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            File tempFile = new File(destFile.getParentFile(), destFile.getName() + ".tmp");
            File backupFile = new File(destFile.getParentFile(), destFile.getName() + ".bak");
            boolean replaced = false;

            try {
                long requiredBytes = sourceSize > 0 ? sourceSize : entry.minimumFileBytes;
                long freeBytes = destFile.getParentFile().getUsableSpace();
                if (freeBytes < requiredBytes + (32L * 1024L * 1024L)) {
                    throw new IOException("Ruang internal tidak cukup. Kosongkan minimal "
                            + ((requiredBytes / (1024 * 1024)) + 32) + " MB lalu coba lagi.");
                }
                if (tempFile.exists() && !tempFile.delete()) {
                    throw new IOException("Tidak dapat membersihkan berkas sementara sebelumnya.");
                }

                long totalRead = 0;
                try (InputStream is = getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(tempFile)) {
                    if (is == null) throw new IOException("Tidak dapat membaca berkas sumber.");
                    byte[] buffer = new byte[65536];
                    int read;
                    long lastUpdateTime = 0;
                    while ((read = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                        totalRead += read;
                        long now = System.currentTimeMillis();
                        if (now - lastUpdateTime > 350) {
                            lastUpdateTime = now;
                            final int percent = sourceSize > 0 ? (int) Math.min(100, (totalRead * 100) / sourceSize) : -1;
                            final long mb = totalRead / (1024 * 1024);
                            mainHandler.post(() -> {
                                if (isFinishing() || isDestroyed()) return;
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
                    fos.getFD().sync();
                }

                if (totalRead < entry.minimumFileBytes || (sourceSize > 0 && totalRead != sourceSize)) {
                    throw new IOException("Ukuran salinan tidak cocok. Model lama tetap dipertahankan.");
                }
                if (backupFile.exists() && !backupFile.delete()) {
                    throw new IOException("Tidak dapat membersihkan cadangan model lama.");
                }
                if (destFile.exists()) {
                    if (!destFile.renameTo(backupFile)) {
                        throw new IOException("Tidak dapat mengamankan model lama.");
                    }
                    replaced = true;
                }
                if (!tempFile.renameTo(destFile)) {
                    copyFile(tempFile, destFile);
                    if (destFile.length() != totalRead) throw new IOException("Verifikasi salinan gagal.");
                    tempFile.delete();
                }
                if (!LocalModelRegistry.isValidFile(destFile, entry) || destFile.length() != totalRead) {
                    throw new IOException("Verifikasi model akhir gagal.");
                }
                if (backupFile.exists()) backupFile.delete();

                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    btnImportModel.setEnabled(true);
                    pbImportModel.setVisibility(View.GONE);
                    if (entry.runtime == LocalModelRegistry.RuntimeKind.MEDIAPIPE_TASK) {
                        LocalLlmEngine.getInstance(SettingsActivity.this).close();
                        config.clearLlmSafeMode();
                    }
                    updateModelStatus();
                    tvImportProgress.setTextColor(0xFF00E5FF);
                    tvImportProgress.setText(entry.displayName + " berhasil diimpor (" +
                            (destFile.length() / (1024 * 1024)) + " MB).");
                    speaker.speak("Model " + entry.displayName + " berhasil disimpan. Runtime akan memeriksa kompatibilitas saat dimuat.");
                });
            } catch (Exception e) {
                if (tempFile.exists()) tempFile.delete();
                if (replaced && backupFile.exists()) {
                    if (destFile.exists()) destFile.delete();
                    backupFile.renameTo(destFile);
                }
                final String err = e.getMessage() != null ? e.getMessage() : "Kesalahan sistem";
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    btnImportModel.setEnabled(true);
                    pbImportModel.setVisibility(View.GONE);
                    tvImportProgress.setTextColor(0xFFFF5252);
                    tvImportProgress.setText("Gagal mengimpor model: " + err);
                    speaker.speak("Gagal mengimpor model. Model sebelumnya tetap dipertahankan jika tersedia.");
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
