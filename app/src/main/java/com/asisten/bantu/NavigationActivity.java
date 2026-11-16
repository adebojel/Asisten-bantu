package com.asisten.bantu;

import android.Manifest;
import android.content.Context;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Size;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mode Tongkat & Sonar Radar 3D:
 * Memvisualisasikan rintangan secara real-time di SonarRadarView, memberi peringatan suara & getaran,
 * serta dilengkapi tombol SOS Darurat 3D & Guardian Check-In.
 */
public class NavigationActivity extends AppCompatActivity {

    public static volatile NavigationActivity current;

    private static final long SPEAK_COOLDOWN_MS = 2500L;
    private static final double NEAR_RATIO = 0.25;

    private PreviewView previewView;
    private SonarRadarView sonarRadarView;
    private TextView tvStatus, tvObstacle, tvGuardianStatusNav;
    private Button btnStart, btnStop, btnEmergencySos, btnGuardianAck, btnGuardianCheckNow;
    private SafeObjectDetector detector;
    private ProcessCameraProvider cameraProvider;
    private Speaker speaker;
    private Vibrator vibrator;
    private final KobraScreenHelper screenHelper = new KobraScreenHelper();
    private final ExecutorService analyzerExec = Executors.newSingleThreadExecutor();
    private volatile boolean navigating = false;
    private volatile boolean processing = false;
    private long lastSpeakAt = 0L;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_radar);
        WindowInsetsHelper.applySystemBarInsets(this, true);
        ThemeManager.applyTheme(this);
        previewView = findViewById(R.id.previewViewNav);
        sonarRadarView = findViewById(R.id.sonarRadarView);
        tvStatus = findViewById(R.id.tvNavStatus);
        tvObstacle = findViewById(R.id.tvObstacle);
        tvGuardianStatusNav = findViewById(R.id.tvGuardianStatusNav);
        btnStart = findViewById(R.id.btnStartNav);
        btnStop = findViewById(R.id.btnStopNav);
        btnEmergencySos = findViewById(R.id.btnEmergencySosNav);
        btnGuardianAck = findViewById(R.id.btnGuardianAckNav);
        btnGuardianCheckNow = findViewById(R.id.btnGuardianCheckNowNav);
        ImageButton btnBackRadar = findViewById(R.id.btnBackRadar);
        if (btnBackRadar != null) btnBackRadar.setOnClickListener(v -> finish());

        speaker = Speaker.get(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        if (btnEmergencySos != null) {
            btnEmergencySos.setOnClickListener(v -> {
                HapticFeedback.vibrateWarning(this);
                EmergencyBeacon.triggerAll(this, null);
                speaker.speak("SOS darurat diaktifkan. Sirine dibunyikan dan pesan darurat disiapkan.");
            });
        }
        if (btnGuardianAck != null) {
            btnGuardianAck.setOnClickListener(v -> {
                HapticFeedback.vibrateSuccess(this);
                GuardianManager.acknowledgeSafe(this, true);
                refreshGuardianStatus();
            });
        }
        if (btnGuardianCheckNow != null) {
            btnGuardianCheckNow.setOnClickListener(v -> {
                HapticFeedback.vibrateAction(this);
                GuardianManager.triggerManualCheckInNow(this);
                refreshGuardianStatus();
            });
        }

        if (new KobraConfig(this).isPrivacyMode()) {
            tvStatus.setText("Mode Privasi Penuh Aktif");
            speaker.speak("Mode privasi penuh sedang aktif. Kamera tidak dapat dibuka sebelum mode privasi dimatikan.");
            btnStart.setEnabled(false);
            btnStop.setEnabled(false);
            return;
        }

        detector = new SafeObjectDetector(true);

        btnStart.setOnClickListener(v -> startNavigation());
        btnStop.setOnClickListener(v -> stopNavigation());

        speaker.speak("Mode tongkat radar tiga dimensi siap. Bilang Kobra mulai atau ketuk Mulai Radar.");
        if (Perms.has(this, Manifest.permission.CAMERA)) startCamera();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 202);
    }

    private void refreshGuardianStatus() {
        if (tvGuardianStatusNav == null) return;
        KobraConfig cfg = new KobraConfig(this);
        ThemeManager.KobraTheme theme = ThemeManager.getActiveTheme(this);
        int interval = cfg.getGuardianIntervalMinutes();
        if (cfg.isGuardianAwaitingResponse()) {
            tvGuardianStatusNav.setText("Guardian: Menunggu konfirmasi aman!");
            tvGuardianStatusNav.setTextColor(ThemeManager.ensureContrast(0xFFFFD600, theme.getCardColor(), 4.6));
        } else if (interval > 0) {
            tvGuardianStatusNav.setText("Guardian Aktif (setiap " + interval + " menit)");
            tvGuardianStatusNav.setTextColor(ThemeManager.ensureContrast(theme.getAccentColor(), theme.getCardColor(), 4.6));
        } else {
            tvGuardianStatusNav.setText("Guardian Check-In: Manual / Mati");
            tvGuardianStatusNav.setTextColor(ThemeManager.ensureContrast(theme.getTextColor(), theme.getCardColor(), 4.6));
        }
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
        current = this;
        ThemeManager.applyTheme(this);
        refreshGuardianStatus();
    }

    @Override protected void onPause() {
        super.onPause();
        if (current == this) current = null;
        stopNavigation();
    }

    @Override protected void onDestroy() {
        navigating = false;
        processing = false;
        if (cameraProvider != null) {
            try { cameraProvider.unbindAll(); } catch (Exception ignored) {}
            cameraProvider = null;
        }
        if (detector != null) {
            try { detector.close(); } catch (Exception ignored) {}
        }
        analyzerExec.shutdown();
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 202 && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            speaker.speak("Izin kamera belum aktif.");
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (isFinishing() || isDestroyed()) return;
            try {
                ProcessCameraProvider provider = future.get();
                if (isFinishing() || isDestroyed()) {
                    provider.unbindAll();
                    return;
                }
                cameraProvider = provider;
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(analyzerExec, this::analyzeFrame);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);

                // Mode Tongkat mulai menganalisis frame segera setelah kamera siap.
                // Tidak perlu menunggu pengguna menekan tombol Mulai Radar.
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed() && !navigating) {
                        startNavigation();
                    }
                });
            } catch (Exception e) {
                speaker.speak("Kamera tidak bisa dibuka.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeFrame(androidx.camera.core.ImageProxy proxy) {
        if (!navigating || processing || proxy.getImage() == null) {
            proxy.close();
            return;
        }
        processing = true;
        int rot = proxy.getImageInfo().getRotationDegrees();
        int imgW = (rot == 90 || rot == 270) ? proxy.getHeight() : proxy.getWidth();
        int imgH = (rot == 90 || rot == 270) ? proxy.getWidth() : proxy.getHeight();
        InputImage img = InputImage.fromMediaImage(proxy.getImage(), rot);
        detector.process(img, (items, usedFallback) -> {
            onLabels(items);
            processing = false;
            proxy.close();
        });
    }

    private void onLabels(List<SafeObjectDetector.DetectedItem> items) {
        if (!navigating) return;
        if (sonarRadarView != null) sonarRadarView.updateObstacles(new ArrayList<>());
        if (items == null || items.isEmpty()) {
            tvObstacle.setText("Objek belum dikenali — arah dan jarak tidak tersedia; jalur belum dipastikan aman");
            return;
        }
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < Math.min(3, items.size()); i++) {
            if (i > 0) names.append(", ");
            names.append(items.get(i).label);
        }
        tvObstacle.setText("Terdeteksi kemungkinan: " + names + ". Posisi dan jarak tidak tersedia.");
        long now = System.currentTimeMillis();
        if (now - lastSpeakAt > SPEAK_COOLDOWN_MS) {
            lastSpeakAt = now;
            speaker.speak("Saya mengenali kemungkinan " + names + ". Mode cadangan tidak dapat menentukan posisi atau jarak. Tetap gunakan tongkat atau pendamping.");
        }
    }

    private void vibrate() {
        if (vibrator == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(300);
        }
    }

    public void startNavigation() {
        if (isFinishing() || isDestroyed() || detector == null) return;
        navigating = true;
        tvStatus.setText("Sonar Radar 3D AKTIF");
        speaker.speak("Bantuan kamera aktif. Deteksi ini dapat melewatkan objek dan tidak mengukur jarak dengan pasti. Tetap gunakan tongkat atau anjing pemandu dan jangan menyeberang hanya berdasarkan radar.");
    }

    public void stopNavigation() {
        if (!navigating) return;
        navigating = false;
        processing = false;
        if (sonarRadarView != null) sonarRadarView.updateObstacles(new ArrayList<>());
        tvStatus.setText("Sonar Radar berhenti");
        speaker.speak("Mode tongkat berhenti.");
    }
}
