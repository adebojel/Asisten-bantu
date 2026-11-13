package com.asisten.bantu;

import android.Manifest;
import android.content.Context;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Size;
import android.widget.Button;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Mode tongkat: mendeteksi benda besar di depan dan memberi peringatan dengan suara dan getaran. */
public class NavigationActivity extends AppCompatActivity {

    static volatile NavigationActivity current;

    private static final long SPEAK_COOLDOWN_MS = 2500L;
    private static final double NEAR_RATIO = 0.25;

    private PreviewView previewView;
    private TextView tvStatus, tvObstacle;
    private Button btnStart, btnStop;
    private ObjectDetector detector;
    private Speaker speaker;
    private Vibrator vibrator;
    private final ExecutorService analyzerExec = Executors.newSingleThreadExecutor();
    private volatile boolean navigating = false;
    private volatile boolean processing = false;
    private long lastSpeakAt = 0L;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);
        previewView = findViewById(R.id.previewViewNav);
        tvStatus = findViewById(R.id.tvNavStatus);
        tvObstacle = findViewById(R.id.tvObstacle);
        btnStart = findViewById(R.id.btnStartNav);
        btnStop = findViewById(R.id.btnStopNav);
        speaker = Speaker.get(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

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

        speaker.speak("Mode tongkat siap. Bilang Kobra mulai untuk mulai, atau Kobra berhenti.");
        if (Perms.has(this, Manifest.permission.CAMERA)) startCamera();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 202);
    }

    @Override protected void onResume() {
        super.onResume();
        current = this;
    }

    @Override protected void onPause() {
        super.onPause();
        if (current == this) current = null;
        stopNavigation();
    }

    @Override protected void onDestroy() {
        navigating = false;
        analyzerExec.shutdown();
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 202 && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            speaker.speak("Izin kamera belum aktif. Buka Kobra, lalu tekan Izin.");
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
        InputImage img = InputImage.fromMediaImage(proxy.getImage(), proxy.getImageInfo().getRotationDegrees());
        detector.process(img)
                .addOnSuccessListener(this::onObjects)
                .addOnCompleteListener(t -> {
                    processing = false;
                    proxy.close();
                });
    }

    private void onObjects(List<DetectedObject> objects) {
        if (!navigating) return;
        DetectedObject nearest = null;
        double nearestRatio = 0;
        for (DetectedObject o : objects) {
            double w = o.getBoundingBox().width();
            double h = o.getBoundingBox().height();
            double ratio = Math.max(w, h) / 640.0;
            if (ratio > nearestRatio) {
                nearestRatio = ratio;
                nearest = o;
            }
        }
        if (nearest == null) {
            tvObstacle.setText("Jalan di depan terlihat aman");
            return;
        }
        String label = "halangan";
        if (!nearest.getLabels().isEmpty()) label = nearest.getLabels().get(0).getText().toLowerCase(Locale.ROOT);
        boolean close = nearestRatio >= NEAR_RATIO;
        tvObstacle.setText((close ? "Ada " + label + " dekat di depan" : "Ada " + label + " di depan, masih jauh"));

        long now = System.currentTimeMillis();
        if (close && now - lastSpeakAt > SPEAK_COOLDOWN_MS) {
            lastSpeakAt = now;
            speaker.speak("Awas, ada " + label + " dekat di depan. Berhenti atau pelan-pelan.");
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
        tvStatus.setText("Mode tongkat aktif");
        speaker.speak("Mode tongkat aktif. Saya bantu awasi halangan di depan.");
    }

    public void stopNavigation() {
        if (!navigating) return;
        navigating = false;
        tvStatus.setText("Mode tongkat berhenti");
        speaker.speak("Mode tongkat berhenti.");
    }
}
