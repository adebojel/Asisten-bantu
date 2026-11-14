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
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
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
    private ObjectDetector detector;
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

        ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build();
        detector = ObjectDetection.getClient(options);

        btnStart.setOnClickListener(v -> startNavigation());
        btnStop.setOnClickListener(v -> stopNavigation());

        speaker.speak("Mode tongkat radar tiga dimensi siap. Bilang Kobra mulai atau ketuk Mulai Radar.");
        if (Perms.has(this, Manifest.permission.CAMERA)) startCamera();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 202);
    }

    private void refreshGuardianStatus() {
        if (tvGuardianStatusNav == null) return;
        KobraConfig cfg = new KobraConfig(this);
        int interval = cfg.getGuardianIntervalMinutes();
        if (cfg.isGuardianAwaitingResponse()) {
            tvGuardianStatusNav.setText("Guardian: Menunggu konfirmasi aman!");
            tvGuardianStatusNav.setTextColor(0xFFFFD600);
        } else if (interval > 0) {
            tvGuardianStatusNav.setText("Guardian Aktif (setiap " + interval + " menit)");
            tvGuardianStatusNav.setTextColor(0xFF00E5FF);
        } else {
            tvGuardianStatusNav.setText("Guardian Check-In: Manual / Mati");
            tvGuardianStatusNav.setTextColor(0xFF94A3B8);
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
        refreshGuardianStatus();
    }

    @Override protected void onPause() {
        super.onPause();
        if (current == this) current = null;
        stopNavigation();
    }

    @Override protected void onDestroy() {
        navigating = false;
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
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(analyzerExec, this::analyzeFrame);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
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
        detector.process(img)
                .addOnSuccessListener(objs -> onObjects(objs, imgW, imgH))
                .addOnCompleteListener(t -> {
                    processing = false;
                    proxy.close();
                });
    }

    private void onObjects(List<DetectedObject> objects, int imgW, int imgH) {
        if (!navigating) return;
        List<SonarRadarView.ObstacleBlip> blips = new ArrayList<>();
        DetectedObject nearest = null;
        double nearestRatio = 0;

        float safeW = Math.max(1f, imgW);
        float safeH = Math.max(1f, imgH);
        for (DetectedObject o : objects) {
            double w = o.getBoundingBox().width();
            double h = o.getBoundingBox().height();
            double ratio = Math.max(w / safeW, h / safeH);
            String lbl = o.getLabels().isEmpty() ? "objek" : o.getLabels().get(0).getText().toLowerCase(Locale.ROOT);
            float nx = ((o.getBoundingBox().centerX() / safeW) - 0.5f) * 1.6f;
            float ny = ((o.getBoundingBox().centerY() / safeH) - 0.5f) * 1.6f;
            blips.add(new SonarRadarView.ObstacleBlip(nx, ny, ratio >= NEAR_RATIO, lbl));

            if (ratio > nearestRatio) {
                nearestRatio = ratio;
                nearest = o;
            }
        }
        if (sonarRadarView != null) {
            sonarRadarView.updateObstacles(blips);
        }

        if (nearest == null) {
            tvObstacle.setText("Jalur di depan terlihat bersih");
            return;
        }
        String label = nearest.getLabels().isEmpty()
                ? "halangan"
                : nearest.getLabels().get(0).getText().toLowerCase(Locale.ROOT);
        boolean close = nearestRatio >= NEAR_RATIO;
        tvObstacle.setText(close ? "BAHAYA DEKAT: " + label : "Terdeteksi: " + label + " (jarak aman)");

        long now = System.currentTimeMillis();
        if (close && now - lastSpeakAt > SPEAK_COOLDOWN_MS) {
            lastSpeakAt = now;
            speaker.speak("Awas, ada " + label + " dekat di depan. Pelan-pelan atau berhenti.");
            vibrate();
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
        navigating = true;
        tvStatus.setText("Sonar Radar 3D AKTIF");
        speaker.speak("Mode tongkat aktif. Radar memantau halangan di depan.");
    }

    public void stopNavigation() {
        if (!navigating) return;
        navigating = false;
        if (sonarRadarView != null) sonarRadarView.updateObstacles(new ArrayList<>());
        tvStatus.setText("Sonar Radar berhenti");
        speaker.speak("Mode tongkat berhenti.");
    }
}
