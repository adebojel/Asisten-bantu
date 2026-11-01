
package com.asisten.bantu;

import android.Manifest; import android.content.Context; import android.content.pm.PackageManager; import android.os.*; import android.speech.tts.TextToSpeech; import android.widget.Button; import android.widget.TextView; import android.widget.Toast;
import androidx.annotation.NonNull; import androidx.appcompat.app.AppCompatActivity; import androidx.camera.core.*; import androidx.camera.lifecycle.ProcessCameraProvider; import androidx.camera.view.PreviewView; import androidx.core.app.ActivityCompat; import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture; import com.google.mlkit.vision.common.InputImage; import com.google.mlkit.vision.objects.ObjectDetection; import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import java.util.Locale; import java.util.concurrent.Executor;

public class NavigationActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private PreviewView previewView; private TextView tvStatus, tvObstacle; private TextToSpeech tts; private ImageAnalysis imageAnalysis; private ProcessCameraProvider cameraProvider; private Executor mainExecutor; private boolean isNavigating = false; private Handler handler = new Handler(Looper.getMainLooper()); private Vibrator vibrator;
    private Runnable analyzeRunnable;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_navigation);
        previewView = findViewById(R.id.previewViewNav); tvStatus = findViewById(R.id.tvNavStatus); tvObstacle = findViewById(R.id.tvObstacle); Button btnStart = findViewById(R.id.btnStartNav); Button btnStop = findViewById(R.id.btnStopNav);
        tts = new TextToSpeech(this, this); mainExecutor = ContextCompat.getMainExecutor(this); vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        btnStart.setOnClickListener(v -> startNavigation()); btnStop.setOnClickListener(v -> stopNavigation());
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 102); else startCamera();
    }
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> { try { cameraProvider = future.get(); Preview preview = new Preview.Builder().build(); preview.setSurfaceProvider(previewView.getSurfaceProvider()); imageAnalysis = new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build(); imageAnalysis.setAnalyzer(mainExecutor, imageProxy -> { if (!isNavigating) { imageProxy.close(); return; } try { InputImage img = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees()); detectObstacle(img); } catch (Exception e) {} finally { imageProxy.close(); } }); cameraProvider.unbindAll(); cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis); tvStatus.setText("Kamera siap. Tekan MULAI untuk mode tongkat."); } catch (Exception e) { Toast.makeText(this, "Gagal kamera", Toast.LENGTH_SHORT).show(); } }, mainExecutor);
    }
    private void startNavigation() {
        isNavigating = true; tvStatus.setText("🦯 MODE TONGKAT AKTIF - Deteksi halangan jalan..."); tvObstacle.setText("Mendeteksi..."); speak("Mode tongkat pintar aktif. Saya akan getarkan dan kasih tau jika ada halangan di depan. Jalan pelan-pelan, arahkan kamera ke depan.");
        // Periodic voice update
        analyzeRunnable = new Runnable() { @Override public void run() { if (isNavigating) handler.postDelayed(this, 3000); } };
        handler.post(analyzeRunnable);
    }
    private void stopNavigation() { isNavigating = false; tvStatus.setText("Mode tongkat berhenti."); tvObstacle.setText("Berhenti"); speak("Mode tongkat berhenti."); if (analyzeRunnable != null) handler.removeCallbacks(analyzeRunnable); }
    private void detectObstacle(InputImage image) {
        ObjectDetectorOptions options = new ObjectDetectorOptions.Builder().setDetectorMode(ObjectDetectorOptions.STREAM_MODE).enableMultipleObjects().enableClassification().build();
        ObjectDetection.getClient(options).process(image).addOnSuccessListener(objects -> {
            if (!isNavigating) return;
            if (objects.isEmpty()) { runOnUiThread(() -> { tvObstacle.setText("✅ Aman - Jalan kosong"); tvObstacle.setBackgroundColor(0xFF00C853); }); return; }
            // Check biggest object
            float largestArea = 0; String label = "";
            for (com.google.mlkit.vision.objects.DetectedObject obj : objects) {
                float area = obj.getBoundingBox().width() * obj.getBoundingBox().height();
                if (area > largestArea) { largestArea = area; if (!obj.getLabels().isEmpty()) label = obj.getLabels().get(0).getText(); else label = "halangan"; }
            }
            // Estimate distance by area (simple heuristic)
            String warning; int vibratePattern;
            if (largestArea > 150000) { warning = "⛔ BAHAYA DEKAT! Ada " + label + " sangat dekat di depan! Berhenti!"; vibratePattern = 3; }
            else if (largestArea > 70000) { warning = "⚠️ Ada " + label + " di depan, sekitar 2 meter. Hati-hati."; vibratePattern = 2; }
            else if (largestArea > 20000) { warning = "Ada " + label + " di depan agak jauh."; vibratePattern = 1; }
            else { warning = "✅ Aman, ada objek jauh: " + label; vibratePattern = 0; }

            runOnUiThread(() -> {
                tvObstacle.setText(warning);
                if (vibratePattern >=2) tvObstacle.setBackgroundColor(0xFFD32F2F); else if (vibratePattern==1) tvObstacle.setBackgroundColor(0xFFFF9800); else tvObstacle.setBackgroundColor(0xFF00C853);
            });

            if (vibratePattern >0) {
                vibrateWarning(vibratePattern);
                if (largestArea > 70000) speakWarning(warning);
            }
        }).addOnFailureListener(e -> {});
    }
    private long lastVibrate = 0; private void vibrateWarning(int level) {
        long now = System.currentTimeMillis(); if (now - lastVibrate < 1500) return; lastVibrate = now;
        try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { if (level==3) vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,200,100,200,100,400}, -1)); else if (level==2) vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,300,100,300}, -1)); else vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)); } else { if (level==3) vibrator.vibrate(new long[]{0,200,100,200,100,400}, -1); else vibrator.vibrate(500); } } catch (Exception e) {}
    }
    private long lastSpeak = 0; private void speakWarning(String text) { long now = System.currentTimeMillis(); if (now - lastSpeak < 2500) return; lastSpeak = now; speak(text); }
    private void speak(String s){ if (tts != null) tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, null); }
    @Override public void onInit(int status){ if (status==TextToSpeech.SUCCESS){ tts.setLanguage(new Locale("id","ID")); tts.setSpeechRate(0.9f); speak("Mode tongkat pintar siap. Tekan mulai, arahkan kamera ke depan, saya akan deteksi halangan dan getarkan HP jika ada bahaya."); } }
    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){ super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode==102 && grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED) startCamera(); }
    @Override protected void onDestroy(){ stopNavigation(); if (tts != null){ tts.stop(); tts.shutdown(); } super.onDestroy(); }
}

