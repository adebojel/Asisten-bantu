package com.asisten.bantu;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telecom.TelecomManager;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Otak Kobra: mendengarkan perintah suara (kata kunci "Kobra"), menjalankan aksi di HP,
 * membaca layar, menekan tombol lewat suara, dan mengarahkan ke mode khusus.
 */
public class AsistenAccessibilityService extends AccessibilityService {

    private static final String TAG = "KobraOS";
    private static final long CONFIRM_TIMEOUT_MS = 30000L;
    private static final long CONVERSATION_WINDOW_MS = 15000L;
    private static final long WATCHDOG_MS = 15000L;

    private static final int COLOR_IDLE = 0xFF00E676;
    private static final int COLOR_LISTEN = 0xFFFFEB3B;
    private static final int COLOR_SPEAK = 0xFF2962FF;
    private static final int COLOR_OFF = 0xFF9E9E9E;
    private static final int COLOR_NO_PERM = 0xFFD32F2F;

    private static final String HELP =
            "Perintah Kobra. Sebut kata Kobra lalu: baca KTP. Baca uang. Warna apa. Ada orang berapa. "
            + "Apa di depan. Mode tongkat. Teman ngobrol. Baca layar. Sebutkan tombol, lalu tekan nomor. "
            + "Tekan lalu nama tombol. Scroll bawah atau atas. Geser kiri atau kanan. Kembali. Beranda. "
            + "Panggil lalu nama. Angkat telepon. Tolak telepon. Kirim pesan ke nama. Kirim SMS ke nama. "
            + "Baca SMS. Baca notifikasi. Baterai. Jam berapa. Timer lima menit. Alarm jam enam. "
            + "Senter nyala. Volume naik. Mode getar. Kunci layar. Tolong untuk SOS. Talkback. "
            + "Buka lalu nama aplikasi apa saja. Buka chat lalu nama kontak, atau Telegram ke nama. Cari aplikasi lalu nama. Cari lalu kata di Google. "
            + "Hitung dua tambah tiga. Tanya pertolongan pertama, misalnya tersedak.";

    private static final Set<String> YES = new HashSet<>(Arrays.asList(
            "iya", "ya", "yakin", "oke", "ok", "lanjut", "kirim", "setuju", "benar"));
    private static final Set<String> NO = new HashSet<>(Arrays.asList(
            "tidak", "batal", "jangan", "nggak", "gak", "enggak", "salah"));

    private static volatile AsistenAccessibilityService instance;

    private KobraConfig config;
    private Speaker speaker;
    private LocalLlmEngine localLlmEngine;
    private Handler main;
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private boolean listening = false;
    private boolean micOn = true;
    private boolean warnedNoPermission = false;
    private long conversationUntil = 0L;
    private String lastSpoken = "";
    private Pending pending;
    private WindowManager windowManager;
    private Button floatingButton;
    private String torchId;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean batteryReceiverRegistered = false;
    private boolean bluetoothReceiverRegistered = false;
    private boolean warnedLow20 = false;
    private boolean warnedCritical10 = false;
    private boolean survivalModeActive = false;
    private long lastHotBatteryWarnAt = 0L;

    private SensorManager sensorManager;
    private Sensor accelSensor;
    private Sensor proximitySensor;
    private boolean fallImpactDetected = false;
    private long fallImpactAt = 0L;
    private boolean fallCountdownActive = false;
    private int fallSecondsRemaining = 0;
    private long lastFallTriggerAt = 0L;
    private boolean pocketLocked = false;
    private View screenCurtainView;
    private View pocketGuardView;
    private long lastCurtainTapAt = 0L;
    private long lastVolumePressAt = 0L;
    private int lastVolumeKeyCode = 0;

    private static final class Pending {
        final String kind;
        final String a;
        final String b;
        final String c;
        final long at = System.currentTimeMillis();
        Pending(String kind, String a, String b) { this(kind, a, b, ""); }
        Pending(String kind, String a, String b, String c) { this.kind = kind; this.a = a; this.b = b; this.c = c; }
        boolean expired() { return System.currentTimeMillis() - at > CONFIRM_TIMEOUT_MS; }
    }

    public static AsistenAccessibilityService getInstance() { return instance; }

    public LocalLlmEngine getLocalLlmEngine() {
        return localLlmEngine != null ? localLlmEngine : LocalLlmEngine.getInstance(this);
    }

    /** Dipanggil dari tombol di layar: langsung dengarkan satu perintah. */
    public static void requestListen() {
        AsistenAccessibilityService s = instance;
        if (s != null) s.onFloatingClick();
    }

    // ----------------------------------------------------------------- siklus hidup

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        main = new Handler(Looper.getMainLooper());
        config = new KobraConfig(this);
        speaker = Speaker.get(this);
        speaker.addListener(speechListener);

        // Inisialisasi Mesin LLM Offline pada Thread Latar Belakang (dengan Safe Mode Fallback)
        localLlmEngine = LocalLlmEngine.getInstance(this);
        new Thread(() -> {
            if (localLlmEngine.isModelPresent()) {
                boolean ok = localLlmEngine.initEngine();
                if (!ok && main != null) {
                    main.post(() -> speaker.speak("Model AI offline tidak dapat dimuat ke memori. Kobra berjalan di Mode Aman perintah sistem lokal."));
                }
            }
        }).start();

        networkCallback = NetworkState.watch(this, online -> main.post(() -> {
            if (online) {
                String type = NetworkState.connectionType(this);
                say("Koneksi " + type + " tersambung. Fitur online aktif.");
            } else {
                say("Koneksi Wi-Fi dan data seluler terputus. Fitur dasar tetap jalan offline.");
            }
        }));
        registerBatteryMonitor();
        registerBluetoothMonitor();
        initTorch();
        initHardwareSensors();
        setupFloatingButton();

        if (config.isPrivacyMode()) {
            micOn = false;
            setIndicator(COLOR_OFF, "🔒");
            speaker.speak("Kobra OS aktif dalam Mode Privasi Penuh. Mikrofon dan kamera dimatikan. Ketuk tombol Kobra untuk mengaktifkan kembali.");
        } else {
            speaker.speak("Kobra OS aktif. Sebut kata Kobra lalu perintah. Bilang Kobra bantuan untuk daftar perintah.");
            main.postDelayed(this::startListening, 2000);
        }
        main.postDelayed(this::checkLowStorageOnStart, 8000);
        main.postDelayed(watchdog, WATCHDOG_MS);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { stopMic(); }

    @Override public void onDestroy() {
        instance = null;
        stopMic();
        destroyRecognizer();
        cancelFallCountdown(false);
        EmergencyBeacon.stopAll(this, torchId);
        removeScreenCurtain(false);
        removePocketGuard();
        if (sensorManager != null) {
            sensorManager.unregisterListener(hardwareSensorListener);
        }
        NetworkState.unwatch(this, networkCallback);
        networkCallback = null;
        if (batteryReceiverRegistered) {
            try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) { }
            batteryReceiverRegistered = false;
        }
        if (bluetoothReceiverRegistered) {
            try { unregisterReceiver(bluetoothReceiver); } catch (Exception ignored) { }
            bluetoothReceiverRegistered = false;
        }
        if (localLlmEngine != null) {
            localLlmEngine.close();
            localLlmEngine = null;
        }
        try {
            if (windowManager != null && floatingButton != null) windowManager.removeView(floatingButton);
        } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.onKeyEvent(event);
        }
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_HEADSETHOOK || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            HapticFeedback.vibrateAction(this);
            if (EmergencyBeacon.isSirenActive()) {
                EmergencyBeacon.stopAll(this, torchId);
                say("Sirine dimatikan.");
            } else {
                onFloatingClick();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            long now = System.currentTimeMillis();
            if (code == lastVolumeKeyCode && now - lastVolumePressAt <= 450L) {
                lastVolumePressAt = 0L;
                HapticFeedback.vibrateAction(this);
                if (EmergencyBeacon.isSirenActive()) {
                    EmergencyBeacon.stopAll(this, torchId);
                    say("Sirine dimatikan.");
                } else {
                    onFloatingClick();
                }
                return true;
            }
            lastVolumeKeyCode = code;
            lastVolumePressAt = now;
        }
        return super.onKeyEvent(event);
    }

    // ----------------------------------------------------------------- mikrofon

    private final Speaker.Listener speechListener = new Speaker.Listener() {
        @Override public void onSpeechStart() {
            stopMic();
            setIndicator(COLOR_SPEAK, "🔊");
        }
        @Override public void onSpeechEnd() {
            if (config != null && config.isPrivacyMode()) {
                setIndicator(COLOR_OFF, "🔒");
                return;
            }
            setIndicator(micOn ? COLOR_IDLE : COLOR_OFF, micOn ? "🐍" : "🔇");
            scheduleRetry(350);
        }
    };

    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (micOn && !listening && !pocketLocked && (config == null || !config.isPrivacyMode())
                    && speaker != null && !speaker.isSpeaking()) {
                startListening();
            }
            main.postDelayed(this, WATCHDOG_MS);
        }
    };

    private void startListening() {
        main.post(() -> {
            if (!micOn || listening || pocketLocked || (config != null && config.isPrivacyMode())
                    || speaker == null || speaker.isSpeaking()) return;
            if (!Perms.has(this, Manifest.permission.RECORD_AUDIO)) {
                setIndicator(COLOR_NO_PERM, "🎤✖");
                if (!warnedNoPermission) {
                    warnedNoPermission = true;
                    speaker.speak("Izin mikrofon belum aktif. Buka aplikasi Kobra, lalu tekan Izin.");
                }
                return;
            }
            warnedNoPermission = false;
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                setIndicator(COLOR_NO_PERM, "🎤✖");
                speaker.speak("Layanan pengenalan suara tidak tersedia di HP ini. Pasang Google atau aplikasi pengenalan suara.");
                micOn = false;
                return;
            }
            if (recognizer == null) createRecognizer();
            try {
                recognizer.startListening(recognizerIntent);
                listening = true;
                setIndicator(COLOR_LISTEN, "🎤");
            } catch (Exception e) {
                Log.w(TAG, "Gagal mulai mendengarkan", e);
                destroyRecognizer();
                scheduleRetry(2000);
            }
        });
    }

    private void stopMic() {
        if (recognizer != null && listening) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
        }
        listening = false;
    }

    private void scheduleRetry(long ms) {
        main.postDelayed(this::startListening, ms);
    }

    private void createRecognizer() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(recognitionListener);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
    }

    private void destroyRecognizer() {
        listening = false;
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) { }
        }
        recognizer = null;
    }

    private final RecognitionListener recognitionListener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) { setIndicator(COLOR_LISTEN, "🎤"); }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { listening = false; }
        @Override public void onPartialResults(Bundle partialResults) { }
        @Override public void onEvent(int eventType, Bundle params) { }

        @Override public void onError(int error) {
            listening = false;
            switch (error) {
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                case SpeechRecognizer.ERROR_CLIENT:
                    destroyRecognizer();
                    scheduleRetry(1500);
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    setIndicator(COLOR_NO_PERM, "🎤✖");
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    scheduleRetry(300);
                    break;
                default:
                    scheduleRetry(1500);
                    break;
            }
        }

        @Override public void onResults(Bundle results) {
            listening = false;
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) handleSpoken(matches.get(0));
            if (!speaker.isSpeaking()) scheduleRetry(300);
        }
    };

    // ----------------------------------------------------------------- tombol mengambang

    private void setupFloatingButton() {
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            floatingButton = new Button(this);
            floatingButton.setTextSize(14f);
            floatingButton.setTextColor(0xFF000000);
            floatingButton.setContentDescription("Tombol Kobra. Ketuk untuk bicara. Tahan lama untuk mematikan mikrofon.");
            floatingButton.setOnClickListener(v -> onFloatingClick());
            floatingButton.setOnLongClickListener(v -> {
                micOn = !micOn;
                if (micOn) {
                    speaker.speak("Mikrofon aktif.");
                    startListening();
                } else {
                    stopMic();
                    setIndicator(COLOR_OFF, "🔇");
                    speaker.speak("Mikrofon mati. Ketuk tombol Kobra untuk bicara.");
                }
                return true;
            });
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.TOP | Gravity.END;
            p.x = 10;
            p.y = 300;
            windowManager.addView(floatingButton, p);
            setIndicator(COLOR_IDLE, "🐍");
        } catch (Exception e) {
            Log.w(TAG, "Tombol mengambang gagal", e);
        }
    }

    private void onFloatingClick() {
        HapticFeedback.vibrateAction(this);
        if (EmergencyBeacon.isSirenActive()) {
            EmergencyBeacon.stopAll(this, torchId);
            say("Sirine bahaya dimatikan.");
            return;
        }
        if (config != null && config.isPrivacyMode()) {
            exitPrivacyMode();
            return;
        }
        micOn = true;
        if (speaker.isSpeaking()) speaker.stop();
        speaker.speak("Ya, saya dengarkan.");
        scheduleRetry(200);
    }

    private void setIndicator(int color, String label) {
        if (main == null) return;
        main.post(() -> {
            if (floatingButton == null) return;
            floatingButton.setBackgroundColor(color);
            floatingButton.setText(label);
        });
    }

    // ----------------------------------------------------------------- pemrosesan ucapan

    private void handleSpoken(String raw) {
        String text = raw.toLowerCase(Locale.ROOT).trim();
        if (text.isEmpty()) return;

        if (pending != null) {
            Pending p = pending;
            pending = null;
            if (!p.expired()) {
                handlePending(p, text);
                return;
            }
        }

        String cmd = stripWake(text);
        if (cmd == null) {
            if (AiChatActivity.current != null) {
                AiChatActivity.current.onVoice(text);
                return;
            }
            if (System.currentTimeMillis() > conversationUntil) return;
            cmd = text;
        }
        conversationUntil = System.currentTimeMillis() + CONVERSATION_WINDOW_MS;
        route(cmd);
    }

    private static String stripWake(String s) {
        String[] wakes = {"kobra os", "kobra", "cobra", "kobro", "kobr"};
        for (String w : wakes) {
            int i = s.indexOf(w);
            if (i >= 0) return s.substring(i + w.length()).trim();
        }
        return null;
    }

    private void handlePending(Pending p, String text) {
        boolean yes = containsWord(text, YES);
        boolean no = containsWord(text, NO);
        switch (p.kind) {
            case "sos":
                if (yes && !no) triggerSos();
                else say("SOS dibatalkan.");
                break;
            case "fall_sos":
                if (no || has(text, "aman", "baik", "hentikan", "stop", "salah")) {
                    cancelFallCountdown(true);
                } else if (yes) {
                    cancelFallCountdown(false);
                    triggerSos();
                } else {
                    pending = p;
                }
                break;
            case "call":
                if (yes && !no) placeCall(p.a, p.b);
                else say("Panggilan dibatalkan.");
                break;
            case "msg_body":
                if (no) {
                    say("Pesan dibatalkan.");
                } else {
                    pending = new Pending("msg_confirm", p.a, text);
                    say("Pesan untuk " + p.a + ": " + text + ". Bilang iya untuk mengirim.");
                }
                break;
            case "msg_confirm":
                if (yes && !no) sendWhatsApp(p.a, p.b);
                else say("Pesan dibatalkan.");
                break;
            case "app_pick":
                pickApp(p, text);
                break;
            case "sms_body":
                if (no) {
                    say("Pesan dibatalkan.");
                } else {
                    pending = new Pending("sms_confirm", p.a, text, p.b);
                    say("SMS untuk " + p.a + ": " + text + ". Bilang iya untuk mengirim.");
                }
                break;
            case "sms_confirm":
                if (yes && !no) sendSmsMessage(p.a, p.c, p.b);
                else say("Pesan dibatalkan.");
                break;
            default:
                break;
        }
    }

    private static boolean containsWord(String text, Set<String> words) {
        for (String w : text.split("\\s+")) if (words.contains(w)) return true;
        return false;
    }

    private static boolean word(String s, String w) {
        for (String t : s.split("\\s+")) if (t.equals(w)) return true;
        return false;
    }

    private static boolean has(String s, String... keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }

    private static String after(String s, String keyword) {
        int i = s.indexOf(keyword);
        return i < 0 ? "" : s.substring(i + keyword.length()).trim();
    }

    private void route(String c) {
        HapticFeedback.vibrateAction(this);
        if (c.isEmpty()) { say("Ya, saya dengarkan."); return; }

        // Bantuan dan kontrol suara & privasi
        if (has(c, "bantuan", "bisa apa", "daftar perintah")) { say(HELP); return; }
        if (has(c, "matikan sirine", "henti sirine", "hentikan sirine", "stop sirine", "matikan alarm bahaya")) {
            EmergencyBeacon.stopAll(this, torchId);
            say("Sirine bahaya dan siaran darurat dimatikan.");
            return;
        }
        if (word(c, "diam") || has(c, "berhenti bicara", "hentikan suara")) {
            EmergencyBeacon.stopAll(this, torchId);
            speaker.stop();
            return;
        }
        if (has(c, "ulangi")) { say(lastSpoken); return; }
        if (has(c, "matikan mode privasi", "keluar mode privasi", "nonaktifkan privasi")) { exitPrivacyMode(); return; }
        if (has(c, "mode privasi penuh", "mode privasi", "privasi penuh", "putus kamera mikrofon", "matikan kamera dan mikrofon")) {
            enterPrivacyMode();
            return;
        }
        if (has(c, "cek mikrofon", "status mikrofon")) { say(micOn ? "Mikrofon aktif." : "Mikrofon mati."); return; }
        if (has(c, "matikan mikrofon")) { micOn = false; stopMic(); setIndicator(COLOR_OFF, "🔇"); say("Mikrofon dimatikan."); return; }
        if (has(c, "nyalakan mikrofon", "hidupkan mikrofon")) { micOn = true; say("Mikrofon aktif."); return; }
        if (has(c, "talkback")) { handleTalkback(c); return; }

        // Pencari HP (Find My Phone) & Layar Hitam (Screen Curtain) & Mode Bertahan Hidup
        if (has(c, "kamu di mana", "kobra di mana", "posisi hp", "cari hp", "hp di mana")) {
            EmergencyBeacon.ringFindMyPhone(this, torchId, speaker);
            return;
        }
        if (has(c, "buka layar", "matikan layar hitam", "tampilkan layar", "keluar layar hitam")) {
            removeScreenCurtain(true);
            return;
        }
        if (has(c, "layar hitam", "mode layar hitam", "hemat layar", "tutup layar", "matikan tampilan")) {
            showScreenCurtain(true);
            return;
        }
        if (has(c, "mode bertahan hidup", "hemat baterai ekstrem")) {
            enterSurvivalMode(true);
            return;
        }

        // Keselamatan darurat: Sirine bahaya, Latihan SOS, Kartu Medis, Kompas, Lokasi, Rute Pulang
        if (has(c, "sirine bahaya", "bunyikan sirine", "nyalakan sirine")) {
            say("Menyalakan sirine bahaya, senter Morse SOS, dan siaran darurat Bluetooth.");
            main.postDelayed(() -> EmergencyBeacon.startDangerSiren(this, torchId), 2000L);
            return;
        }
        if (has(c, "latihan sos", "simulasi sos", "tes sos", "uji sos")) {
            triggerSosSimulation();
            return;
        }
        if (has(c, "kartu medis", "identitas medis", "data medis", "baca kartu medis")) {
            openMedicalCardAndRead();
            return;
        }
        if (has(c, "buka kompas")) {
            launch(CompassActivity.class, null);
            say("Membuka kompas suara dan penanda rute.");
            return;
        }
        if (has(c, "kompas", "arah mata angin", "menghadap mana", "arah mana")) {
            speakCompassHeading();
            return;
        }
        if (has(c, "saya di mana", "posisi saya", "lokasi saya sekarang", "alamat saya", "di mana saya")) {
            speakWhereAmI();
            return;
        }
        if (c.startsWith("simpan lokasi ") || c.startsWith("tandai lokasi ")
                || c.startsWith("simpan tempat ") || c.startsWith("tandai tempat ")) {
            String place = afterAny(c, "simpan lokasi ", "tandai lokasi ", "simpan tempat ", "tandai tempat ");
            saveCurrentFavoritePlace(place);
            return;
        }
        if (has(c, "arahkan pulang", "rute pulang", "jalan pulang", "pandu pulang")) {
            guideToFavoritePlace("Rumah");
            return;
        }
        if (c.startsWith("pandu ke ") || c.startsWith("tuntun ke ")) {
            String targetPlace = afterAny(c, "pandu ke ", "tuntun ke ");
            if (config.getFavoritePlace(targetPlace) != null) {
                guideToFavoritePlace(targetPlace);
                return;
            }
        }

        // Mode yang sedang terbuka
        if (VisionActivity.current != null && has(c, "scan", "foto", "ambil gambar", "jepret")) {
            VisionActivity.current.captureNow();
            return;
        }
        if (NavigationActivity.current != null && has(c, "mulai")) { NavigationActivity.current.startNavigation(); return; }
        if (NavigationActivity.current != null && has(c, "berhenti", "stop")) { NavigationActivity.current.stopNavigation(); return; }

        // Mata: KTP, uang, warna, orang, deskripsi
        if (has(c, "baca ktp", "baca kk", "baca dokumen", "ktp saya")) { launch(VisionActivity.class, "ktp"); return; }
        if (has(c, "baca uang", "uang berapa", "duit")) { launch(VisionActivity.class, "money"); return; }
        if (has(c, "warna")) { launch(VisionActivity.class, "color"); return; }
        if (has(c, "ada orang", "deteksi orang", "berapa orang")) { launch(VisionActivity.class, "face"); return; }
        if (has(c, "apa di depan", "lihat apa", "deskripsikan", "ada apa di depan")) { launch(VisionActivity.class, "describe"); return; }

        // Tongkat dan teman ngobrol
        if (has(c, "tongkat", "halangan")) { launch(NavigationActivity.class, null); say("Mode tongkat dibuka. Arahkan kamera ke depan."); return; }
        if (has(c, "ngobrol", "curhat", "teman bicara")) { launch(AiChatActivity.class, null); return; }

        // Darurat: hanya kalau benar-benar diminta
        if (c.equals("tolong") || word(c, "sos") || has(c, "darurat", "minta tolong")) {
            pending = new Pending("sos", "", "");
            HapticFeedback.vibrateWarning(this);
            say("Saya akan kirim SOS dengan lokasi ke kontak darurat. Bilang iya untuk lanjut.");
            return;
        }

        if (routeExtra(c)) return;

        // Navigasi layar
        if (has(c, "kembali")) { global(GLOBAL_ACTION_BACK, "Kembali."); return; }
        if (has(c, "beranda", "layar utama")) { global(GLOBAL_ACTION_HOME, "Ke layar utama."); return; }
        if (has(c, "aplikasi terbaru")) { global(GLOBAL_ACTION_RECENTS, "Aplikasi terbaru."); return; }
        if (has(c, "notifikasi")) { global(GLOBAL_ACTION_NOTIFICATIONS, "Notifikasi dibuka."); return; }
        if (has(c, "pengaturan cepat")) { global(GLOBAL_ACTION_QUICK_SETTINGS, "Pengaturan cepat dibuka."); return; }
        if (has(c, "scroll bawah", "geser bawah")) { scroll(true); return; }
        if (has(c, "scroll atas", "geser atas")) { scroll(false); return; }
        if (c.startsWith("tekan ") || c.startsWith("klik ")) { clickByName(after(c, " ")); return; }
        if (c.startsWith("ketik ")) { typeText(after(c, "ketik ")); return; }
        if (has(c, "baca layar", "isi layar", "apa di layar")) { say(readScreenText()); return; }
        if (has(c, "yang dipilih", "fokus")) { say(readFocus()); return; }

        // Info HP dan diagnostik sistem
        if (has(c, "periksa sistem", "cek sistem", "diagnostik sistem", "status sistem", "kesehatan sistem")) {
            diagnoseSystem();
            return;
        }
        if (has(c, "baterai")) {
            int bat = batteryPercent();
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            boolean charging = bm != null && bm.isCharging();
            say("Baterai " + bat + " persen" + (charging ? ", sedang diisi daya." : "."));
            return;
        }
        if (has(c, "jam berapa", "pukul berapa") || word(c, "jam")) {
            say("Sekarang pukul " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()) + ".");
            return;
        }
        if (has(c, "tanggal", "hari apa")) {
            say("Hari ini " + new SimpleDateFormat("EEEE, d MMMM yyyy", new Locale("id", "ID")).format(new Date()) + ".");
            return;
        }

        // Kontrol sistem
        if (has(c, "bluetooth")) { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Pengaturan Bluetooth dibuka."); return; }
        if (has(c, "senter", "flash")) { setTorch(!has(c, "matikan", "mati")); return; }
        if (has(c, "volume")) { adjustVolume(!has(c, "pelan", "kecil", "turun")); return; }
        if (has(c, "gps", "lokasi")) { openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Pengaturan lokasi dibuka."); return; }
        if (has(c, "kecerahan")) { openSettings(Settings.ACTION_DISPLAY_SETTINGS, "Pengaturan layar dibuka."); return; }

        // WhatsApp dan telepon
        if (has(c, "kirim pesan", "kirim wa", "kirim whatsapp", "balas pesan")) { startSendMessage(afterLast(c, " ke ")); return; }
        if (has(c, "baca pesan", "baca chat", "isi chat")) { say(readScreenText()); return; }
        if (has(c, "panggil ", "telepon ", "telpon ")) {
            startCall(afterAny(c, "panggil ", "telepon ", "telpon "));
            return;
        }

        // Media dan navigasi
        if ((has(c, "youtube") && !c.startsWith("buka ")) || c.startsWith("putar ")) {
            String yq = c.replace("putar", "").replace("youtube", "").replace("cari", "").trim().replaceAll("\\s+di$", "").trim();
            if (yq.isEmpty()) openApp("youtube");
            else playYoutube(yq);
            return;
        }
        if (has(c, "arahkan ke", "navigasi ke", "antar ke")) { navigateTo(afterAny(c, "arahkan ke", "navigasi ke", "antar ke")); return; }

        // Otak offline dulu: kalkulator dan pengetahuan dasar. Online hanya jika tidak ada jawaban lokal.
        String local = OfflineAi.get(this).answer(c);
        if (local != null) { say(local); return; }

        // Sisanya: MediaPipe LLM Offline (bila offline, dengan Safe Mode Fallback), n8n (jika webhook diatur), lalu AI OpenRouter
        if (!NetworkState.isOnline(this)) {
            LocalLlmEngine engine = getLocalLlmEngine();
            if (engine != null && engine.isModelPresent()) {
                if (engine.isSafeMode() && !engine.getLastError().isEmpty()) {
                    say("Mode Aman aktif karena " + engine.getLastError() + ". Anda tetap bisa memakai kalkulator suara dan perintah sistem lokal.");
                } else if (engine.isInitialized()) {
                    say("Memproses dengan otak offline...");
                    engine.generateResponseAsync(c, speaker);
                } else {
                    say("Menyiapkan otak offline, harap tunggu...");
                    new Thread(() -> {
                        if (engine.initEngine()) {
                            engine.generateResponseAsync(c, speaker);
                        } else {
                            main.post(() -> say("Gagal memuat model offline. Beralih ke Mode Aman perintah sistem lokal."));
                        }
                    }).start();
                }
            } else {
                say("Mode Aman aktif. Perintah itu butuh internet atau model offline titik task, dan saat ini internet terputus.");
            }
            return;
        }
        if (!config.getN8nUrl().isEmpty()) {
            say("Sebentar, saya teruskan ke otomatisasi.");
            N8nClient.send(config.getN8nUrl(), c, new N8nClient.Callback() {
                @Override public void onResult(String text) { say(text); }
                @Override public void onError(String message) { askAi(c); }
            });
            return;
        }
        askAi(c);
    }

    private static String afterLast(String s, String keyword) {
        int i = s.lastIndexOf(keyword);
        return i < 0 ? "" : s.substring(i + keyword.length()).trim();
    }

    private static String afterAny(String s, String... keywords) {
        for (String k : keywords) if (s.contains(k)) return after(s, k);
        return "";
    }

    // ----------------------------------------------------------------- perintah tambahan

    private final ArrayList<AccessibilityNodeInfo> numbered = new ArrayList<>();

    /** Notifikasi, SMS, telepon masuk, gestur, alarm, dan mode suara. True jika perintah sudah ditangani. */
    private boolean routeExtra(String c) {
        if (c.startsWith("buka ")) { handleOpen(after(c, "buka ")); return true; }

        String chatRest = null, chatApp = "whatsapp";
        if (c.startsWith("chat ke ")) chatRest = after(c, "chat ke ");
        else if (c.startsWith("chat dengan ")) chatRest = after(c, "chat dengan ");
        else if (c.startsWith("whatsapp ke ")) chatRest = after(c, "whatsapp ke ");
        else if (c.startsWith("wa ke ")) chatRest = after(c, "wa ke ");
        else if (c.startsWith("telegram ke ")) { chatRest = after(c, "telegram ke "); chatApp = "telegram"; }
        if (chatRest != null) { openChat(chatRest, chatApp); return true; }

        if (has(c, "berapa aplikasi", "jumlah aplikasi")) { say("Ada " + getApps().size() + " aplikasi yang bisa dibuka."); return true; }
        if (has(c, "sebutkan aplikasi", "daftar aplikasi", "aplikasi apa saja")) { listApps(); return true; }
        if (has(c, "cari aplikasi", "ada aplikasi")) { searchApps(afterAny(c, "cari aplikasi", "ada aplikasi")); return true; }
        if (c.startsWith("cari ") && !has(c, "youtube", "peta", "maps")) { webSearch(after(c, "cari ")); return true; }
        if (c.startsWith("google ")) { webSearch(after(c, "google ")); return true; }

        if (has(c, "matikan baca notifikasi", "jangan bacakan notifikasi", "berhenti baca notifikasi")) {
            config.setReadNotifications(false);
            say("Notifikasi tidak akan dibacakan otomatis. Bilang Kobra baca notifikasi untuk membacanya.");
            return true;
        }
        if (has(c, "nyalakan baca notifikasi", "hidupkan baca notifikasi", "bacakan notifikasi otomatis")) {
            config.setReadNotifications(true);
            say(KobraNotificationService.isConnected()
                    ? "Notifikasi baru akan dibacakan otomatis."
                    : "Pengaturan disimpan, tetapi akses notifikasi belum aktif. Buka Kobra, lalu tekan Akses notifikasi.");
            return true;
        }
        if (has(c, "baca notifikasi", "notifikasi apa", "ada notifikasi")) {
            say(KobraNotificationService.summary(5));
            return true;
        }
        if (has(c, "baca sms", "baca pesan singkat", "sms terbaru")) {
            say(MessageReader.latestSms(this, 3));
            return true;
        }
        if (has(c, "kirim sms", "kirim pesan singkat")) {
            startSendSms(afterLast(c, " ke "));
            return true;
        }
        if (has(c, "angkat telepon", "angkat telpon", "jawab telepon", "terima telepon")) {
            answerCall();
            return true;
        }
        if (has(c, "tolak telepon", "akhiri telepon", "tutup telepon", "matikan telepon", "akhiri panggilan")) {
            endCall();
            return true;
        }
        if (has(c, "kunci layar", "kunci hp")) { global(GLOBAL_ACTION_LOCK_SCREEN, "Layar dikunci."); return true; }
        if (has(c, "screenshot", "tangkapan layar", "tangkap layar")) { global(GLOBAL_ACTION_TAKE_SCREENSHOT, "Tangkapan layar diambil."); return true; }
        if (has(c, "menu daya", "matikan hp")) { global(GLOBAL_ACTION_POWER_DIALOG, "Menu daya dibuka."); return true; }
        if (has(c, "geser kiri")) { gesture(DeviceControl.swipe(this, "kiri"), "Digeser ke kiri."); return true; }
        if (has(c, "geser kanan")) { gesture(DeviceControl.swipe(this, "kanan"), "Digeser ke kanan."); return true; }
        if (has(c, "ketuk tengah", "ketuk layar")) { gesture(DeviceControl.tap(this, 0.5f, 0.5f, 60), "Layar diketuk."); return true; }
        if (has(c, "tahan layar", "tekan lama layar")) { gesture(DeviceControl.tap(this, 0.5f, 0.5f, 900), "Layar ditahan."); return true; }
        if (has(c, "sebutkan tombol", "daftar tombol", "ada tombol apa", "tombol apa saja")) { listButtons(); return true; }
        if (c.startsWith("tekan nomor") || c.startsWith("ketuk nomor") || c.startsWith("klik nomor")) {
            Integer n = OfflineBrain.firstNumber(c);
            clickNumber(n == null ? -1 : n);
            return true;
        }
        if (has(c, "timer", "hitung mundur")) { setTimer(c); return true; }
        if (has(c, "pasang alarm", "set alarm", "setel alarm", "atur alarm", "alarm jam", "alarm pukul", "bangunkan")) { setAlarm(c); return true; }
        if (has(c, "mode getar")) { setRinger(AudioManager.RINGER_MODE_VIBRATE, "Mode getar aktif."); return true; }
        if (has(c, "mode normal", "mode berdering")) { setRinger(AudioManager.RINGER_MODE_NORMAL, "Mode berdering aktif."); return true; }
        if (c.equals("buka pengaturan") || c.equals("pengaturan")) { openSettings(Settings.ACTION_SETTINGS, "Pengaturan dibuka."); return true; }
        return false;
    }

    private void gesture(boolean ok, String message) {
        say(ok ? message : "Gestur tidak bisa dilakukan sekarang.");
    }

    private void listButtons() {
        numbered.clear();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { say("Layar tidak bisa dibaca sekarang."); return; }
        collectClickables(root, numbered, 15);
        if (numbered.isEmpty()) { say("Tidak ada tombol yang bisa ditekan di layar ini."); return; }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numbered.size(); i++) {
            sb.append("Nomor ").append(i + 1).append(", ").append(labelOf(numbered.get(i))).append(". ");
        }
        sb.append("Bilang Kobra tekan nomor, lalu angkanya.");
        say(sb.toString());
    }

    private void collectClickables(AccessibilityNodeInfo node, ArrayList<AccessibilityNodeInfo> out, int max) {
        if (node == null || out.size() >= max) return;
        if (node.isClickable() && node.isVisibleToUser() && node.isEnabled()) {
            String label = labelOf(node);
            boolean duplicate = !out.isEmpty() && labelOf(out.get(out.size() - 1)).equals(label);
            if (!label.isEmpty() && !duplicate) out.add(node);
        }
        for (int i = 0; i < node.getChildCount() && out.size() < max; i++) {
            collectClickables(node.getChild(i), out, max);
        }
    }

    private String labelOf(AccessibilityNodeInfo n) {
        if (n.getText() != null && n.getText().length() > 0) return n.getText().toString();
        if (n.getContentDescription() != null && n.getContentDescription().length() > 0) return n.getContentDescription().toString();
        StringBuilder sb = new StringBuilder();
        collectText(n, sb);
        String s = sb.toString().replaceAll("\\s+", " ").trim();
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    private void clickNumber(int n) {
        if (n < 1 || n > numbered.size()) {
            say("Nomor itu tidak ada. Bilang Kobra sebutkan tombol dulu.");
            return;
        }
        boolean ok = numbered.get(n - 1).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        say(ok ? "Oke." : "Tombol itu sudah berubah. Bilang Kobra sebutkan tombol lagi.");
    }

    private void setTimer(String c) {
        Integer n = OfflineBrain.firstNumber(c);
        if (n == null || n <= 0) { say("Berapa lama? Contoh: timer lima menit."); return; }
        int mult = c.contains("detik") ? 1 : (c.contains("jam") ? 3600 : 60);
        String unit = mult == 1 ? "detik" : (mult == 3600 ? "jam" : "menit");
        Intent i = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, n * mult)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Kobra OS")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            say("Timer " + n + " " + unit + " dipasang.");
        } catch (Exception e) {
            say("Aplikasi jam di HP ini tidak mendukung timer lewat suara.");
        }
    }

    private void setAlarm(String c) {
        java.util.List<Integer> nums = OfflineBrain.numbers(c);
        if (nums.isEmpty()) { say("Jam berapa? Contoh: alarm jam enam tiga puluh."); return; }
        int hour = nums.get(0);
        int minute = nums.size() > 1 ? nums.get(1) : 0;
        if (has(c, "sore", "malam")) {
            if (hour < 12) hour += 12;
            else if (hour == 12 && has(c, "malam")) hour = 0;
        }
        if (hour > 23 || minute > 59) { say("Jam atau menitnya tidak valid."); return; }
        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Kobra OS")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            say(minute == 0
                    ? "Alarm jam " + hour + " tepat dipasang."
                    : "Alarm jam " + hour + " lewat " + minute + " menit dipasang.");
        } catch (Exception e) {
            say("Aplikasi jam di HP ini tidak mendukung alarm lewat suara.");
        }
    }

    private void setRinger(int mode, String message) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) { say("Mode suara tidak bisa diatur."); return; }
        try {
            am.setRingerMode(mode);
            say(message);
        } catch (SecurityException e) {
            say("HP ini tidak mengizinkan Kobra mengubah mode suara.");
        }
    }

    private void answerCall() {
        if (!Perms.has(this, Manifest.permission.ANSWER_PHONE_CALLS)) {
            say("Izin menjawab telepon belum aktif. Buka Kobra, lalu tekan Izin.");
            return;
        }
        try {
            TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
            tm.acceptRingingCall();
            say("Mencoba mengangkat telepon.");
        } catch (Exception e) {
            say("Tidak ada telepon yang bisa diangkat.");
        }
    }

    private void endCall() {
        if (!Perms.has(this, Manifest.permission.ANSWER_PHONE_CALLS)) {
            say("Izin mengakhiri telepon belum aktif. Buka Kobra, lalu tekan Izin.");
            return;
        }
        try {
            TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
            say(tm.endCall() ? "Telepon diakhiri." : "Tidak ada panggilan yang bisa diakhiri.");
        } catch (Exception e) {
            say("Tidak ada panggilan yang bisa diakhiri.");
        }
    }

    private void startSendSms(String target) {
        if (target.isEmpty()) { say("Kirim SMS ke siapa? Contoh: kirim SMS ke Ibu."); return; }
        if (!Perms.has(this, Manifest.permission.SEND_SMS)) {
            say("Izin SMS belum aktif. Buka Kobra, lalu tekan Izin.");
            return;
        }
        boolean digits = target.matches("[0-9+ ]{6,}");
        if (!digits && !Perms.has(this, Manifest.permission.READ_CONTACTS)) {
            say("Izin kontak belum aktif. Buka Kobra, lalu tekan Izin.");
            return;
        }
        String[] found = digits ? new String[]{target, target.replace(" ", "")} : findContact(target);
        if (found == null) { say("Kontak " + target + " tidak ditemukan."); return; }
        pending = new Pending("sms_body", found[0], found[1]);
        say("Apa isi SMS untuk " + found[0] + "?");
    }

    private void sendSmsMessage(String name, String number, String body) {
        try {
            SmsManager sm = Build.VERSION.SDK_INT >= 31 ? getSystemService(SmsManager.class) : SmsManager.getDefault();
            sm.sendMultipartTextMessage(number, null, sm.divideMessage(body), null, null);
            say("SMS dikirim ke " + name + ".");
        } catch (Exception e) {
            Log.w(TAG, "SMS gagal", e);
            say("SMS gagal dikirim ke " + name + ".");
        }
    }

    // ----------------------------------------------------------------- membuka aplikasi dan chat

    private java.util.List<AppMatcher.App> appCache;
    private long appCacheAt = 0L;

    private java.util.List<AppMatcher.App> getApps() {
        long now = System.currentTimeMillis();
        if (appCache == null || now - appCacheAt > 60000L) {
            appCache = AppLauncher.installed(this);
            appCacheAt = now;
        }
        return appCache;
    }

    /** Semua perintah yang diawali "buka": layar sistem, pengaturan, situs, chat, atau aplikasi apa pun. */
    private void handleOpen(String raw) {
        String q = raw.trim();
        if (q.isEmpty()) { say("Buka apa?"); return; }
        switch (q) {
            case "notifikasi": global(GLOBAL_ACTION_NOTIFICATIONS, "Notifikasi dibuka."); return;
            case "pengaturan cepat": global(GLOBAL_ACTION_QUICK_SETTINGS, "Pengaturan cepat dibuka."); return;
            case "aplikasi terbaru": global(GLOBAL_ACTION_RECENTS, "Aplikasi terbaru."); return;
            case "beranda": case "layar utama": global(GLOBAL_ACTION_HOME, "Ke layar utama."); return;
            case "pengaturan": openSettings(Settings.ACTION_SETTINGS, "Pengaturan dibuka."); return;
            case "chat": say("Buka chat siapa? Contoh: buka chat Ibu."); return;
            default: break;
        }
        if (q.startsWith("aplikasi ")) q = q.substring(9).trim();
        if (q.startsWith("chat ")) { openChat(q.substring(5).trim(), "whatsapp"); return; }
        if (q.startsWith("pengaturan ")) { openSettingsByName(q.substring(11).trim()); return; }
        if (q.startsWith("situs ") || q.startsWith("web ") || q.startsWith("website ")) { openSite(after(q, " ")); return; }
        openApp(q);
    }

    private void openApp(String spoken) {
        String q = spoken.trim();
        if (q.isEmpty()) { say("Buka aplikasi apa?"); return; }
        java.util.List<AppMatcher.Match> m = AppMatcher.find(getApps(), q, 4);
        if (m.isEmpty()) {
            say("Aplikasi " + q + " tidak ditemukan. Bilang Kobra cari aplikasi lalu namanya untuk mencari.");
            return;
        }
        if (AppMatcher.isClear(m)) { launchApp(m.get(0).app); return; }
        StringBuilder pkgs = new StringBuilder(), labels = new StringBuilder(), spokenList = new StringBuilder();
        for (int i = 0; i < m.size(); i++) {
            if (i > 0) { pkgs.append('\n'); labels.append('\n'); }
            pkgs.append(m.get(i).app.pkg);
            labels.append(m.get(i).app.label);
            spokenList.append("Nomor ").append(i + 1).append(", ").append(m.get(i).app.label).append(". ");
        }
        pending = new Pending("app_pick", pkgs.toString(), labels.toString());
        say("Ada beberapa aplikasi yang mirip. " + spokenList + "Bilang nomornya.");
    }

    private void pickApp(Pending p, String text) {
        if (containsWord(text, NO)) { say("Dibatalkan."); return; }
        String[] pk = p.a.split("\n"), lb = p.b.split("\n");
        Integer n = OfflineBrain.firstNumber(text);
        if (n != null && n >= 1 && n <= pk.length) { launchApp(new AppMatcher.App(lb[n - 1], pk[n - 1])); return; }
        java.util.List<AppMatcher.App> cands = new ArrayList<>();
        for (int i = 0; i < pk.length; i++) cands.add(new AppMatcher.App(lb[i], pk[i]));
        java.util.List<AppMatcher.Match> m = AppMatcher.find(cands, text, 3);
        if (AppMatcher.isClear(m)) launchApp(m.get(0).app);
        else say("Tidak jadi membuka aplikasi.");
    }

    private void launchApp(AppMatcher.App app) {
        Intent i = getPackageManager().getLaunchIntentForPackage(app.pkg);
        if (i == null) { say(app.label + " tidak bisa dibuka."); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            say("Membuka " + app.label + ".");
        } catch (Exception e) {
            say(app.label + " tidak bisa dibuka.");
        }
    }

    private void searchApps(String spoken) {
        String q = spoken.replace("bernama", "").trim();
        if (q.isEmpty()) { say("Cari aplikasi apa?"); return; }
        java.util.List<AppMatcher.Match> m = AppMatcher.find(getApps(), q, 5);
        if (m.isEmpty()) { say("Tidak ada aplikasi bernama " + q + "."); return; }
        StringBuilder sb = new StringBuilder("Ditemukan " + m.size() + " aplikasi: ");
        for (int i = 0; i < m.size(); i++) sb.append(i > 0 ? ", " : "").append(m.get(i).app.label);
        say(sb.append(". Bilang Kobra buka lalu namanya.").toString());
    }

    private void listApps() {
        java.util.List<AppMatcher.App> apps = getApps();
        if (apps.isEmpty()) { say("Daftar aplikasi tidak bisa dibaca."); return; }
        int n = Math.min(20, apps.size());
        StringBuilder sb = new StringBuilder("Ada " + apps.size() + " aplikasi. Dua puluh pertama: ");
        for (int i = 0; i < n; i++) sb.append(i > 0 ? ", " : "").append(apps.get(i).label);
        if (apps.size() > n) sb.append(". Dan ").append(apps.size() - n).append(" lainnya. Bilang Kobra cari aplikasi lalu namanya.");
        say(sb.toString());
    }

    /** Membuka chat kontak. Bisa disebut dengan aplikasinya: "ibu di telegram". Bawaan: WhatsApp. */
    private void openChat(String rest, String defaultApp) {
        String name = rest.trim(), app = defaultApp;
        for (String sep : new String[]{" di ", " lewat ", " pakai ", " via "}) {
            int i = name.lastIndexOf(sep);
            if (i > 0) {
                app = name.substring(i + sep.length()).trim();
                name = name.substring(0, i).trim();
                break;
            }
        }
        if (name.isEmpty()) { say("Buka chat siapa?"); return; }
        boolean tg = app.contains("telegram") || app.equals("tele");
        boolean wa = app.contains("whatsapp") || app.equals("wa");
        if (!tg && !wa) {
            say("Chat langsung baru saya dukung untuk WhatsApp dan Telegram. Saya buka aplikasi " + app + " saja.");
            openApp(app);
            return;
        }
        String which = tg ? "telegram" : "whatsapp";
        String label = tg ? "Telegram" : "WhatsApp";
        if (openChatDeepLink(name, which, "")) {
            say("Membuka chat " + name + " di " + label + ". Bilang Kobra baca layar untuk membaca pesan.");
            return;
        }
        if (tg) { say("Kontak " + name + " tidak ditemukan, atau Telegram belum terpasang."); return; }
        openChatByName(name);
    }

    /** Langsung membuka chat lewat tautan aplikasi memakai nomor dari kontak. False jika tidak bisa. */
    private boolean openChatDeepLink(String name, String app, String text) {
        String number;
        if (name.matches("[0-9+ ]{6,}")) {
            number = name;
        } else {
            if (!Perms.has(this, Manifest.permission.READ_CONTACTS)) return false;
            String[] found = findContact(name);
            if (found == null) return false;
            number = found[1];
        }
        String digits = PhoneFormat.toInternational(number);
        if (digits.isEmpty()) return false;
        Intent i;
        String pkg;
        if (app.equals("telegram")) {
            pkg = firstInstalled("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram");
            i = new Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=" + digits));
        } else {
            pkg = firstInstalled("com.whatsapp", "com.whatsapp.w4b");
            String url = "https://wa.me/" + digits + (text.isEmpty() ? "" : "?text=" + Uri.encode(text));
            i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        }
        if (pkg == null) return false;
        i.setPackage(pkg);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String firstInstalled(String... pkgs) {
        PackageManager pm = getPackageManager();
        for (String p : pkgs) if (pm.getLaunchIntentForPackage(p) != null) return p;
        return null;
    }

    /** Klik node yang teks atau deskripsinya persis sama dengan salah satu label (tidak asal memuat). */
    private boolean clickExact(String... labels) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo n = findExact(root, labels);
        while (n != null && !n.isClickable()) n = n.getParent();
        return n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private AccessibilityNodeInfo findExact(AccessibilityNodeInfo node, String[] labels) {
        if (node == null) return null;
        CharSequence t = node.getText(), d = node.getContentDescription();
        for (String l : labels) {
            if ((t != null && t.toString().trim().equalsIgnoreCase(l))
                    || (d != null && d.toString().trim().equalsIgnoreCase(l))) return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findExact(node.getChild(i), labels);
            if (r != null) return r;
        }
        return null;
    }

    private void openSite(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).replace(" titik ", ".").replace(" dot ", ".").replace(" ", "");
        if (s.startsWith("https://")) s = s.substring(8);
        else if (s.startsWith("http://")) s = s.substring(7);
        if (s.isEmpty()) { say("Situs apa?"); return; }
        if (!s.contains(".")) s += ".com";
        if (!NetworkState.isOnline(this)) { say("Membuka situs butuh internet, dan saat ini internet terputus."); return; }
        openUrl("https://" + s);
        say("Membuka situs " + s + ".");
    }

    private void webSearch(String raw) {
        String q = raw.replace(" di google", "").replace(" di internet", "").trim();
        if (q.isEmpty()) { say("Cari apa?"); return; }
        if (!NetworkState.isOnline(this)) { say("Pencarian butuh internet, dan saat ini internet terputus."); return; }
        openUrl("https://www.google.com/search?q=" + Uri.encode(q));
        say("Mencari " + q + " di Google. Bilang Kobra baca layar untuk membaca hasilnya.");
    }

    private void openSettingsByName(String name) {
        if (has(name, "wifi", "wi-fi", "pesawat") || word(name, "data") || word(name, "internet")) {
            openSettings(Settings.ACTION_SETTINGS, "Pengaturan WiFi dan data tidak saya kendalikan. Pengaturan utama dibuka.");
            return;
        }
        String action;
        if (has(name, "bluetooth")) action = Settings.ACTION_BLUETOOTH_SETTINGS;
        else if (has(name, "lokasi", "gps")) action = Settings.ACTION_LOCATION_SOURCE_SETTINGS;
        else if (has(name, "layar", "tampilan", "kecerahan")) action = Settings.ACTION_DISPLAY_SETTINGS;
        else if (has(name, "suara", "volume", "nada")) action = Settings.ACTION_SOUND_SETTINGS;
        else if (has(name, "baterai", "daya")) action = Settings.ACTION_BATTERY_SAVER_SETTINGS;
        else if (has(name, "aksesibilitas")) action = Settings.ACTION_ACCESSIBILITY_SETTINGS;
        else if (has(name, "aplikasi")) action = Settings.ACTION_APPLICATION_SETTINGS;
        else if (has(name, "penyimpanan", "memori")) action = Settings.ACTION_INTERNAL_STORAGE_SETTINGS;
        else if (has(name, "bahasa")) action = Settings.ACTION_LOCALE_SETTINGS;
        else if (has(name, "tanggal", "waktu")) action = Settings.ACTION_DATE_SETTINGS;
        else {
            openSettings(Settings.ACTION_SETTINGS, "Pengaturan itu belum saya kenal. Pengaturan utama dibuka.");
            return;
        }
        openSettings(action, "Pengaturan " + name + " dibuka.");
    }

    // ----------------------------------------------------------------- bicara

    private void say(String text) {
        if (text == null || text.isEmpty()) return;
        lastSpoken = text;
        conversationUntil = System.currentTimeMillis() + CONVERSATION_WINDOW_MS;
        speaker.speak(text);
    }

    private void askAi(String cmd) {
        if (!config.hasAi()) {
            say("Maaf, saya belum mengenali perintah itu. Bilang Kobra bantuan untuk daftar perintah.");
            return;
        }
        say("Sebentar, saya tanyakan ke AI.");
        new AiClient(config).chat(AiClient.SYSTEM_TUNANETRA, cmd, new AiClient.Callback() {
            @Override public void onResult(String text) { say(text); }
            @Override public void onError(String message) { say(message); }
        });
    }

    // ----------------------------------------------------------------- membuka mode

    private void launch(Class<? extends Activity> cls, String mode) {
        Intent i = new Intent(this, cls);
        if (mode != null) i.putExtra("mode", mode);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            say("Tidak bisa membuka fitur itu sekarang.");
        }
    }

    private void openSettings(String action, String message) {
        Intent i = new Intent(action);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            say(message);
        } catch (Exception e) {
            say("Pengaturan itu tidak bisa dibuka di HP ini.");
        }
    }

    private void global(int action, String message) {
        say(performGlobalAction(action) ? message : "Tidak bisa melakukan itu sekarang.");
    }

    private void openUrl(String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception e) { say("Tidak bisa membuka halaman itu."); }
    }

    // ----------------------------------------------------------------- layar dan tombol

    /** Dipakai juga oleh layar launcher. */
    public String readScreenText() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "Layar tidak bisa dibaca sekarang.";
        StringBuilder sb = new StringBuilder();
        collectText(root, sb);
        String s = sb.toString().replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return "Layar ini tidak punya teks.";
        if (s.length() > 700) s = s.substring(0, 700) + ". Masih banyak lagi.";
        return s;
    }

    private void collectText(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        CharSequence t = node.getText();
        CharSequence d = node.getContentDescription();
        if (t != null && t.length() > 0) sb.append(t).append(". ");
        else if (d != null && d.length() > 0) sb.append(d).append(". ");
        for (int i = 0; i < node.getChildCount(); i++) collectText(node.getChild(i), sb);
    }

    private String readFocus() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "Layar tidak bisa dibaca.";
        AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        if (f == null) return "Tidak ada yang dipilih.";
        CharSequence label = f.getText() != null ? f.getText() : f.getContentDescription();
        return label == null || label.length() == 0 ? "Tidak ada yang dipilih." : label.toString();
    }

    private void scroll(boolean down) {
        AccessibilityNodeInfo s = findScrollable(getRootInActiveWindow());
        if (s == null) { say("Tidak ada daftar yang bisa digulir."); return; }
        s.performAction(down ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        say(down ? "Digulir ke bawah." : "Digulir ke atas.");
    }

    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findScrollable(node.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    private void clickByName(String name) {
        say(clickAny(name) ? "Oke." : "Tombol " + name + " tidak ditemukan di layar.");
    }

    /** Mencari node yang teksnya atau deskripsinya memuat frasa, lalu klik induk yang bisa diklik. */
    private boolean clickAny(String phrase) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || phrase == null || phrase.isEmpty()) return false;
        AccessibilityNodeInfo node = findByText(root, phrase.toLowerCase(Locale.ROOT));
        AccessibilityNodeInfo n = node;
        while (n != null && !n.isClickable()) n = n.getParent();
        return n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private AccessibilityNodeInfo findByText(AccessibilityNodeInfo node, String phrase) {
        if (node == null) return null;
        CharSequence t = node.getText();
        CharSequence d = node.getContentDescription();
        boolean match = (t != null && t.toString().toLowerCase(Locale.ROOT).contains(phrase))
                || (d != null && d.toString().toLowerCase(Locale.ROOT).contains(phrase));
        if (match) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findByText(node.getChild(i), phrase);
            if (r != null) return r;
        }
        return null;
    }

    private void typeText(String text) {
        say(setTextInEditable(text) ? "Sudah diketik." : "Tidak ada kolom isian yang aktif.");
    }

    private boolean setTextInEditable(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (field == null || !field.isEditable()) field = findEditable(root);
        if (field == null) return false;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findEditable(node.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    private void handleTalkback(String c) {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        boolean on = am != null && am.isTouchExplorationEnabled();
        if (has(c, "status", "cek", "aktif", "nyala")) {
            say(on ? "TalkBack sedang aktif." : "TalkBack sedang mati.");
            return;
        }
        openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS,
                "Saya tidak bisa mengaktifkan TalkBack secara langsung karena alasan keamanan sistem. "
                + "Saya buka pengaturan aksesibilitas. Cari TalkBack lalu aktifkan.");
    }

    // ----------------------------------------------------------------- aplikasi, WhatsApp, telepon

    private boolean openWhatsApp() {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLaunchIntentForPackage("com.whatsapp");
        if (i == null) i = pm.getLaunchIntentForPackage("com.whatsapp.w4b");
        if (i == null) { say("WhatsApp tidak terpasang di HP ini."); return false; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        return true;
    }

    private void openChatByName(String name) {
        if (name.isEmpty()) { say("Buka chat siapa?"); return; }
        if (!openWhatsApp()) return;
        main.postDelayed(() -> say(clickAny(name)
                ? "Chat " + name + " dibuka. Bilang baca layar untuk membaca pesan."
                : "Chat " + name + " tidak terlihat di layar."), 2000);
    }

    private void startSendMessage(String target) {
        if (target.isEmpty()) { say("Kirim pesan ke siapa? Contoh: kirim pesan ke Ibu."); return; }
        pending = new Pending("msg_body", target, "");
        say("Apa isi pesannya untuk " + target + "?");
    }

    private void sendWhatsApp(String name, String body) {
        if (openChatDeepLink(name, "whatsapp", body)) {
            main.postDelayed(() -> say(clickExact("kirim", "send")
                    ? "Pesan terkirim ke " + name + "."
                    : "Pesan sudah diketik di chat " + name + ", tetapi tombol kirim tidak ketemu. Tekan kirim sendiri."), 3000);
            return;
        }
        if (!openWhatsApp()) return;
        main.postDelayed(() -> {
            if (!clickAny(name)) { say("Chat " + name + " tidak ditemukan. Pesan belum terkirim."); return; }
            main.postDelayed(() -> {
                if (!setTextInEditable(body)) { say("Kolom pesan tidak ketemu. Pesan belum terkirim."); return; }
                main.postDelayed(() -> {
                    if (clickExact("kirim", "send")) say("Pesan terkirim ke " + name + ".");
                    else say("Tombol kirim tidak ketemu. Pesan sudah diketik, tekan kirim sendiri.");
                }, 700);
            }, 1200);
        }, 2500);
    }

    private void startCall(String target) {
        if (target.isEmpty()) { say("Panggil siapa?"); return; }
        if (!Perms.has(this, Manifest.permission.CALL_PHONE)) {
            say("Izin telepon belum aktif. Buka Kobra, lalu tekan Izin.");
            return;
        }
        if (target.matches("[0-9+ ]{6,}")) {
            String number = target.replace(" ", "");
            pending = new Pending("call", number, number);
            say("Telepon nomor itu? Bilang iya untuk menelepon.");
            return;
        }
        String[] found = findContact(target);
        if (found == null) { say("Kontak " + target + " tidak ditemukan."); return; }
        pending = new Pending("call", found[0], found[1]);
        say("Telepon " + found[0] + "? Bilang iya untuk menelepon.");
    }

    private String[] findContact(String name) {
        if (!Perms.has(this, Manifest.permission.READ_CONTACTS)) {
            say("Izin kontak belum aktif. Buka Kobra, lalu tekan Izin.");
            return null;
        }
        try (Cursor c = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER},
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                new String[]{"%" + name + "%"}, null)) {
            if (c != null && c.moveToFirst()) return new String[]{c.getString(0), c.getString(1)};
        } catch (Exception e) {
            Log.w(TAG, "Pencarian kontak gagal", e);
        }
        return null;
    }

    private void placeCall(String name, String number) {
        if (!Perms.has(this, Manifest.permission.CALL_PHONE)) {
            say("Izin telepon belum aktif. Buka Kobra, lalu tekan Izin.");
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            say("Menelepon " + name + ".");
        } catch (Exception e) {
            Log.w(TAG, "Panggilan gagal", e);
            say("Gagal menelepon " + name + ".");
        }
    }

    private void playYoutube(String q) {
        Intent i = new Intent(Intent.ACTION_SEARCH);
        i.setPackage("com.google.android.youtube");
        i.putExtra("query", q);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            say("Mencari " + q + " di YouTube.");
        } catch (ActivityNotFoundException e) {
            openUrl("https://www.youtube.com/results?search_query=" + Uri.encode(q));
            say("Mencari " + q + " di YouTube.");
        }
    }

    private void navigateTo(String dest) {
        if (dest.isEmpty()) { say("Mau ke mana?"); return; }
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(dest)));
        i.setPackage("com.google.android.apps.maps");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            say("Membuka navigasi ke " + dest + ".");
        } catch (ActivityNotFoundException e) {
            openUrl("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(dest));
            say("Membuka peta untuk " + dest + ".");
        }
    }

    // ----------------------------------------------------------------- SOS

    /** Dipakai juga dari tombol SOS di layar launcher. */
    public void triggerSos() {
        HapticFeedback.vibrateSosMorse(this, false);
        if (!NetworkState.isOnline(this)) {
            EmergencyBeacon.startEmergencyBroadcast(this);
        }

        String name1 = config.getSos("name1");
        String p1 = config.getSos("phone1");
        String p2 = config.getSos("phone2");
        if (p1.isEmpty() && p2.isEmpty()) {
            say("Kontak darurat belum diatur. Saya telepon 112 sekarang.");
            placeCall("darurat 112", "112");
            return;
        }
        Location loc = lastKnownLocation();
        String where = loc != null
                ? "https://maps.google.com/?q=" + loc.getLatitude() + "," + loc.getLongitude()
                : "lokasi belum tersedia";
        String msg = "DARURAT dari Kobra OS. Saya tunanetra dan butuh bantuan segera. Lokasi saya: " + where;

        int sent = 0;
        if (Perms.has(this, Manifest.permission.SEND_SMS)) {
            if (!p1.isEmpty() && sendSms(p1, msg)) sent++;
            if (!p2.isEmpty() && sendSms(p2, msg)) sent++;
        }
        String who = name1.isEmpty() ? "kontak darurat" : name1;
        say("SOS dikirim ke " + sent + " kontak. "
                + (loc != null ? "Lokasi terkirim." : "Lokasi belum tersedia, nyalakan GPS.")
                + " Saya telepon " + who + ".");
        placeCall(who, p1.isEmpty() ? p2 : p1);
    }

    public void triggerSosSimulation() {
        HapticFeedback.vibrateSosMorse(this, false);
        String target = config.hasSosContact()
                ? (config.getSos("name1").isEmpty() ? config.getSos("phone1") : config.getSos("name1"))
                : "112";
        say("Mode Latihan SOS aktif. Ini hanya simulasi tanpa SMS atau panggilan asli. "
                + "Saat darurat nyata, Kobra mengirim SMS koordinat Google Maps ke " + target
                + ", menyalakan getaran Morse SOS, lalu menelepon kontak darurat.");
    }

    private void openMedicalCardAndRead() {
        Intent i = new Intent(this, MedicalCardActivity.class);
        i.putExtra(MedicalCardActivity.EXTRA_AUTO_SPEAK, true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception ignored) {}
        say(config.getMedicalCardSpeech());
    }

    private void speakCompassHeading() {
        CompassLocationHelper.readHeadingOnce(this, new CompassLocationHelper.HeadingCallback() {
            @Override public void onHeading(int degrees, String cardinalName) {
                HapticFeedback.vibrateSuccess(AsistenAccessibilityService.this);
                say("Anda menghadap " + cardinalName + ", " + degrees + " derajat.");
            }
            @Override public void onUnavailable() {
                say("Sensor kompas tidak tersedia atau belum siap di HP ini.");
            }
        });
    }

    private void speakWhereAmI() {
        say("Mencari lokasi Anda sekarang.");
        CompassLocationHelper.fetchCurrentLocation(this, new CompassLocationHelper.LocationCallback() {
            @Override public void onLocation(Location loc, String addressText) {
                HapticFeedback.vibrateSuccess(AsistenAccessibilityService.this);
                if (!addressText.isEmpty()) {
                    say("Anda berada di sekitar " + addressText + ". Akurasi " + Math.round(loc.getAccuracy()) + " meter.");
                } else {
                    say(String.format(Locale.US, "Koordinat GPS Anda: lintang %.5f, bujur %.5f.", loc.getLatitude(), loc.getLongitude()));
                }
            }
            @Override public void onError(String message) {
                HapticFeedback.vibrateWarning(AsistenAccessibilityService.this);
                say(message);
            }
        });
    }

    private void saveCurrentFavoritePlace(String rawLabel) {
        final String label = rawLabel.trim().isEmpty() ? "Rumah" : rawLabel.trim();
        say("Menyimpan koordinat lokasi untuk " + label + ".");
        CompassLocationHelper.fetchCurrentLocation(this, new CompassLocationHelper.LocationCallback() {
            @Override public void onLocation(Location loc, String addressText) {
                config.saveFavoritePlace(label, loc.getLatitude(), loc.getLongitude());
                HapticFeedback.vibrateSuccess(AsistenAccessibilityService.this);
                say("Lokasi " + label + " berhasil disimpan." + (addressText.isEmpty() ? "" : " Sekitar " + addressText + "."));
            }
            @Override public void onError(String message) {
                HapticFeedback.vibrateWarning(AsistenAccessibilityService.this);
                say(message);
            }
        });
    }

    private void guideToFavoritePlace(String queryLabel) {
        double[] coords = config.getFavoritePlace(queryLabel);
        if (coords == null) {
            say("Lokasi " + queryLabel + " belum disimpan. Bilang Kobra simpan lokasi " + queryLabel + " saat berada di sana.");
            return;
        }
        String resolved = config.getFavoritePlaceLabel(queryLabel);
        CompassLocationHelper.readHeadingOnce(this, new CompassLocationHelper.HeadingCallback() {
            @Override public void onHeading(int degrees, String cardinalName) {
                fetchAndSpeakRoute(resolved, coords[0], coords[1], degrees);
            }
            @Override public void onUnavailable() {
                fetchAndSpeakRoute(resolved, coords[0], coords[1], -1);
            }
        });
    }

    private void fetchAndSpeakRoute(String label, double lat, double lon, int headingDeg) {
        CompassLocationHelper.fetchCurrentLocation(this, new CompassLocationHelper.LocationCallback() {
            @Override public void onLocation(Location loc, String addressText) {
                HapticFeedback.vibrateSuccess(AsistenAccessibilityService.this);
                say(CompassLocationHelper.buildRouteGuidance(label, loc, lat, lon, headingDeg));
            }
            @Override public void onError(String message) {
                HapticFeedback.vibrateWarning(AsistenAccessibilityService.this);
                say(message);
            }
        });
    }

    private boolean sendSms(String number, String msg) {
        try {
            SmsManager sm = Build.VERSION.SDK_INT >= 31 ? getSystemService(SmsManager.class) : SmsManager.getDefault();
            sm.sendMultipartTextMessage(number, null, sm.divideMessage(msg), null, null);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "SMS gagal", e);
            return false;
        }
    }

    private Location lastKnownLocation() {
        if (!Perms.has(this, Manifest.permission.ACCESS_FINE_LOCATION)) return null;
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) return null;
        Location best = null;
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (SecurityException ignored) { }
        }
        return best;
    }

    // ----------------------------------------------------------------- perangkat, sensor, & keselamatan

    private void initTorch() {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                Boolean flash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) { torchId = id; break; }
            }
        } catch (Exception e) {
            Log.w(TAG, "Senter tidak terdeteksi", e);
        }
    }

    private void setTorch(boolean on) {
        if (on && config.isPrivacyMode()) {
            say("Mode privasi penuh aktif. Kamera dan senter sedang dibatasi.");
            return;
        }
        if (torchId == null) { say("Senter tidak tersedia di HP ini."); return; }
        try {
            ((CameraManager) getSystemService(CAMERA_SERVICE)).setTorchMode(torchId, on);
            HapticFeedback.vibrateSuccess(this);
            say(on ? "Senter nyala." : "Senter mati.");
        } catch (Exception e) {
            say("Gagal mengatur senter.");
        }
    }

    private void adjustVolume(boolean up) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) { say("Volume tidak bisa diatur."); return; }
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
        say(up ? "Volume naik." : "Volume turun.");
    }

    private int batteryPercent() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        return bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    private void initHardwareSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager == null) return;
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (accelSensor != null) {
            sensorManager.registerListener(hardwareSensorListener, accelSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (proximitySensor != null) {
            sensorManager.registerListener(hardwareSensorListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private final SensorEventListener hardwareSensorListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent event) {
            int type = event.sensor.getType();
            if (type == Sensor.TYPE_ACCELEROMETER) {
                handleAccelerometer(event.values[0], event.values[1], event.values[2]);
            } else if (type == Sensor.TYPE_PROXIMITY) {
                handleProximity(event.values[0], event.sensor.getMaximumRange());
            }
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private void handleAccelerometer(float x, float y, float z) {
        if (survivalModeActive || fallCountdownActive || config == null || !config.isFallDetectionEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - lastFallTriggerAt < 45000L) return;

        double mag = Math.sqrt(x * x + y * y + z * z);
        if (!fallImpactDetected) {
            if (mag > 27.5) {
                fallImpactDetected = true;
                fallImpactAt = now;
            }
        } else {
            long elapsed = now - fallImpactAt;
            if (elapsed >= 1200L && elapsed <= 3500L) {
                if (mag >= 7.0 && mag <= 12.8) {
                    fallImpactDetected = false;
                    startFallCountdown();
                }
            } else if (elapsed > 3500L) {
                fallImpactDetected = false;
            }
        }
    }

    private void startFallCountdown() {
        if (fallCountdownActive) return;
        fallCountdownActive = true;
        fallSecondsRemaining = 15;
        lastFallTriggerAt = System.currentTimeMillis();
        pending = new Pending("fall_sos", "", "");
        HapticFeedback.vibrateWarning(this);
        say("Terdeteksi jatuh keras! Bilang batal atau tidak jika Anda baik-baik saja. SOS otomatis dikirim dalam 15 detik.");
        main.postDelayed(fallCountdownTick, 1000L);
    }

    private final Runnable fallCountdownTick = new Runnable() {
        @Override public void run() {
            if (!fallCountdownActive) return;
            fallSecondsRemaining--;
            if (fallSecondsRemaining <= 0) {
                fallCountdownActive = false;
                pending = null;
                say("Waktu habis. Mengirim SOS darurat sekarang.");
                triggerSos();
                return;
            }
            if (fallSecondsRemaining == 5) {
                HapticFeedback.vibrateWarning(AsistenAccessibilityService.this);
                speaker.speak("Lima detik lagi.");
            } else if (fallSecondsRemaining == 10 || fallSecondsRemaining < 5) {
                HapticFeedback.vibrateAction(AsistenAccessibilityService.this);
            }
            main.postDelayed(this, 1000L);
        }
    };

    private void cancelFallCountdown(boolean announce) {
        if (!fallCountdownActive) return;
        fallCountdownActive = false;
        main.removeCallbacks(fallCountdownTick);
        pending = null;
        if (announce) {
            HapticFeedback.vibrateSuccess(this);
            say("Syukurlah. Deteksi jatuh dibatalkan.");
        }
    }

    private void handleProximity(float distance, float maxRange) {
        if (config == null || !config.isPocketLockEnabled()) {
            if (pocketLocked) {
                pocketLocked = false;
                removePocketGuard();
            }
            return;
        }
        boolean near = distance < maxRange && distance < 3.0f;
        if (near && !pocketLocked) {
            pocketLocked = true;
            stopMic();
            showPocketGuard();
        } else if (!near && pocketLocked) {
            pocketLocked = false;
            removePocketGuard();
            if (micOn && !config.isPrivacyMode()) {
                scheduleRetry(300);
            }
        }
    }

    private void showPocketGuard() {
        if (windowManager == null || pocketGuardView != null) return;
        main.post(() -> {
            if (pocketGuardView != null || windowManager == null) return;
            try {
                View v = new View(this);
                v.setBackgroundColor(0xE6000000);
                v.setContentDescription("Kunci sentuh saku aktif. Keluarkan HP dari saku untuk membuka.");
                WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                windowManager.addView(v, p);
                pocketGuardView = v;
            } catch (Exception ignored) {}
        });
    }

    private void removePocketGuard() {
        if (windowManager == null || pocketGuardView == null) return;
        main.post(() -> {
            try {
                if (pocketGuardView != null && windowManager != null) {
                    windowManager.removeView(pocketGuardView);
                }
            } catch (Exception ignored) {}
            pocketGuardView = null;
        });
    }

    private void showScreenCurtain(boolean announce) {
        if (windowManager == null) return;
        main.post(() -> {
            if (screenCurtainView == null) {
                try {
                    View v = new View(this);
                    v.setBackgroundColor(0xFF000000);
                    v.setContentDescription("Mode layar hitam hemat daya aktif. Ketuk dua kali untuk membuka layar.");
                    v.setOnClickListener(view -> {
                        long now = System.currentTimeMillis();
                        if (now - lastCurtainTapAt <= 500L) {
                            lastCurtainTapAt = 0L;
                            removeScreenCurtain(true);
                        } else {
                            lastCurtainTapAt = now;
                        }
                    });
                    WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                            PixelFormat.OPAQUE);
                    windowManager.addView(v, p);
                    screenCurtainView = v;
                } catch (Exception ignored) {}
            }
            if (announce) {
                HapticFeedback.vibrateSuccess(this);
                say("Mode layar hitam hemat daya aktif. Ketuk layar dua kali atau bilang Kobra buka layar untuk menonaktifkan.");
            }
        });
    }

    private void removeScreenCurtain(boolean announce) {
        main.post(() -> {
            try {
                if (screenCurtainView != null && windowManager != null) {
                    windowManager.removeView(screenCurtainView);
                }
            } catch (Exception ignored) {}
            screenCurtainView = null;
            if (announce) {
                HapticFeedback.vibrateAction(this);
                say("Layar ditampilkan kembali.");
            }
        });
    }

    private void enterPrivacyMode() {
        config.setPrivacyMode(true);
        micOn = false;
        stopMic();
        destroyRecognizer();
        EmergencyBeacon.stopAll(this, torchId);
        if (VisionActivity.current != null) {
            try { VisionActivity.current.finish(); } catch (Exception ignored) {}
        }
        if (NavigationActivity.current != null) {
            try { NavigationActivity.current.finish(); } catch (Exception ignored) {}
        }
        setIndicator(COLOR_OFF, "🔒");
        HapticFeedback.vibrateSuccess(this);
        say("Mode privasi penuh aktif. Mikrofon dan kamera telah diputus seketika. Ketuk tombol Kobra untuk mengaktifkan kembali.");
    }

    private void exitPrivacyMode() {
        config.setPrivacyMode(false);
        micOn = true;
        setIndicator(COLOR_IDLE, "🐍");
        HapticFeedback.vibrateSuccess(this);
        say("Mode privasi dimatikan. Mikrofon dan kamera aktif kembali.");
        scheduleRetry(300);
    }

    private void enterSurvivalMode(boolean manual) {
        if (survivalModeActive && !manual) return;
        survivalModeActive = true;
        EmergencyBeacon.stopAll(this, torchId);
        if (localLlmEngine != null) {
            localLlmEngine.close();
        }
        showScreenCurtain(false);
        HapticFeedback.vibrateWarning(this);
        say("Mode bertahan hidup aktif karena baterai sangat kritis. Senter, layar, dan pemrosesan berat dimatikan agar telepon dan SOS tetap siap.");
    }

    private void registerBluetoothMonitor() {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
            registerReceiver(bluetoothReceiver, filter);
            bluetoothReceiverRegistered = true;
        } catch (Exception e) {
            Log.w(TAG, "Gagal mendaftarkan pemantau headset Bluetooth", e);
        }
    }

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            boolean disconnected = AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)
                    || BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action);
            if (!disconnected && BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                disconnected = (state == BluetoothProfile.STATE_DISCONNECTED);
            }
            if (disconnected) {
                HapticFeedback.vibrateWarning(AsistenAccessibilityService.this);
                say("Perhatian, headset Bluetooth terputus. Suara dialihkan ke speaker HP.");
            }
        }
    };

    private void registerBatteryMonitor() {
        try {
            registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            batteryReceiverRegistered = true;
        } catch (Exception e) {
            Log.w(TAG, "Gagal mendaftarkan pemantau baterai", e);
        }
    }

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || !Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) return;
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);

            if (tempTenths >= 430) {
                long now = System.currentTimeMillis();
                if (now - lastHotBatteryWarnAt > 600_000L) {
                    lastHotBatteryWarnAt = now;
                    HapticFeedback.vibrateWarning(AsistenAccessibilityService.this);
                    say("Peringatan suhu baterai panas, mencapai " + (tempTenths / 10) + " derajat Celsius. Kurangi pemakaian berat atau lepas pengisi daya.");
                }
            }

            if (level < 0 || scale <= 0) return;

            int percent = (level * 100) / scale;
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;

            if (charging) {
                warnedLow20 = false;
                warnedCritical10 = false;
                if (survivalModeActive) {
                    survivalModeActive = false;
                    removeScreenCurtain(false);
                }
                return;
            }
            if (percent < 5 && !survivalModeActive) {
                warnedCritical10 = true;
                warnedLow20 = true;
                enterSurvivalMode(false);
            } else if (percent < 10 && !warnedCritical10) {
                warnedCritical10 = true;
                warnedLow20 = true;
                HapticFeedback.vibrateWarning(AsistenAccessibilityService.this);
                say("Peringatan baterai kritis, tersisa " + percent + " persen. Segera sambungkan pengisi daya.");
            } else if (percent < 20 && !warnedLow20) {
                warnedLow20 = true;
                HapticFeedback.vibrateWarning(AsistenAccessibilityService.this);
                say("Peringatan baterai lemah, tersisa " + percent + " persen. Sebaiknya segera isi daya HP.");
            }
        }
    };

    private void checkLowStorageOnStart() {
        try {
            StatFs stat = new StatFs(getFilesDir().getAbsolutePath());
            long availBytes = stat.getAvailableBytes();
            if (availBytes < 1024L * 1024L * 1024L) {
                long mb = availBytes / (1024L * 1024L);
                HapticFeedback.vibrateWarning(this);
                say("Peringatan penyimpanan internal di bawah satu gigabita, tersisa " + mb + " megabita. Kosongkan ruang agar sistem dan model AI tetap lancar.");
            }
        } catch (Exception e) {
            Log.w(TAG, "Gagal memeriksa sisa penyimpanan", e);
        }
    }

    private void diagnoseSystem() {
        StringBuilder missingPerms = new StringBuilder();
        if (!Perms.has(this, Manifest.permission.RECORD_AUDIO)) missingPerms.append("mikrofon, ");
        if (!Perms.has(this, Manifest.permission.CAMERA)) missingPerms.append("kamera, ");
        if (!Perms.has(this, Manifest.permission.ACCESS_FINE_LOCATION)) missingPerms.append("lokasi, ");
        if (!Perms.has(this, Manifest.permission.READ_CONTACTS)) missingPerms.append("kontak, ");
        if (!Perms.has(this, Manifest.permission.CALL_PHONE)) missingPerms.append("telepon, ");
        if (!Perms.has(this, Manifest.permission.SEND_SMS)) missingPerms.append("SMS, ");

        String permReport = missingPerms.length() == 0
                ? "Semua izin utama aktif"
                : "Izin yang belum aktif: " + missingPerms.substring(0, missingPerms.length() - 2);

        String notifReport = KobraNotificationService.isConnected()
                ? "Akses notifikasi aktif"
                : "Akses notifikasi belum aktif";

        LocalLlmEngine llm = getLocalLlmEngine();
        String aiReport;
        if (llm != null && llm.isInitialized()) {
            aiReport = "Model AI offline Gemma 3 aktif";
        } else if (llm != null && llm.isModelPresent()) {
            aiReport = "AI offline dalam Mode Aman karena " + (llm.getLastError().isEmpty() ? "belum dimuat" : llm.getLastError());
        } else {
            aiReport = "Model AI offline belum diimpor, Mode Aman aktif";
        }

        String netType = NetworkState.connectionType(this);
        String netReport = NetworkState.isOnline(this)
                ? "Internet tersambung melalui " + netType
                : "Internet terputus, mode offline";

        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int bat = batteryPercent();
        boolean charging = bm != null && bm.isCharging();
        String batReport = bat >= 0
                ? "Baterai " + bat + " persen" + (charging ? " dan sedang mengisi daya" : "")
                : "Status baterai tidak terbaca";

        String storageReport;
        try {
            StatFs stat = new StatFs(getFilesDir().getAbsolutePath());
            long freeMb = stat.getAvailableBytes() / (1024L * 1024L);
            if (freeMb >= 1024) {
                storageReport = String.format(new Locale("id", "ID"), "Sisa penyimpanan %.1f gigabita", freeMb / 1024.0);
            } else {
                storageReport = "Peringatan, sisa penyimpanan rendah " + freeMb + " megabita";
            }
        } catch (Exception e) {
            storageReport = "Penyimpanan tidak terbaca";
        }

        String sosReport = config.hasSosContact()
                ? "Kontak darurat SOS sudah diatur"
                : "Kontak darurat SOS belum diatur, bawaan menghubungi 112";

        say("Laporan diagnostik sistem Kobra OS. "
                + permReport + ". "
                + notifReport + ". "
                + aiReport + ". "
                + netReport + ". "
                + batReport + ". "
                + storageReport + ". "
                + sosReport + ".");
    }
}
