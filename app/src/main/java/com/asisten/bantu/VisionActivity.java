
package com.asisten.bantu;

import android.Manifest; import android.content.pm.PackageManager; import android.graphics.Bitmap; import android.graphics.Color; import android.os.Bundle; import android.speech.tts.TextToSpeech; import android.widget.Button; import android.widget.TextView; import android.widget.Toast;
import androidx.annotation.NonNull; import androidx.appcompat.app.AppCompatActivity; import androidx.camera.core.*; import androidx.camera.lifecycle.ProcessCameraProvider; import androidx.camera.view.PreviewView; import androidx.core.app.ActivityCompat; import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture; import com.google.mlkit.vision.common.InputImage; import com.google.mlkit.vision.text.TextRecognition; import com.google.mlkit.vision.text.latin.TextRecognizerOptions; import com.google.mlkit.vision.face.FaceDetection; import com.google.mlkit.vision.face.FaceDetectorOptions;
import java.util.Locale; import java.util.concurrent.ExecutionException; import java.util.concurrent.Executor;

public class VisionActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private PreviewView previewView; private TextView tvResult; private TextToSpeech tts; private String mode = "money"; private ImageCapture imageCapture; private ProcessCameraProvider cameraProvider; private Executor mainExecutor;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_vision);
        previewView = findViewById(R.id.previewView); tvResult = findViewById(R.id.tvVisionResult); Button btnCapture = findViewById(R.id.btnCapture); Button btnClose = findViewById(R.id.btnClose);
        mode = getIntent().getStringExtra("mode"); if (mode == null) mode = "money";
        tts = new TextToSpeech(this, this); mainExecutor = ContextCompat.getMainExecutor(this);
        btnCapture.setOnClickListener(v -> captureAndAnalyze()); btnClose.setOnClickListener(v -> finish());
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101); else startCamera();
    }
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> { try { cameraProvider = future.get(); Preview preview = new Preview.Builder().build(); preview.setSurfaceProvider(previewView.getSurfaceProvider()); imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build(); cameraProvider.unbindAll(); cameraProvider.bindToLifecycle(this, androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture); speak("Kamera siap. Mode " + mode); } catch (Exception e) { Toast.makeText(this, "Gagal kamera", Toast.LENGTH_SHORT).show(); } }, mainExecutor);
    }
    private void captureAndAnalyze() {
        if (imageCapture == null) { Toast.makeText(this, "Kamera belum siap", Toast.LENGTH_SHORT).show(); return; }
        tvResult.setText("⏳ Menganalisis..."); speak("Menganalisis");
        imageCapture.takePicture(mainExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override public void onCaptureSuccess(@NonNull ImageProxy image) { @NonNull Bitmap bitmap = image.toBitmap(); image.close(); if ("money".equals(mode)) analyzeMoney(bitmap); else if ("color".equals(mode)) analyzeColor(bitmap); else if ("face".equals(mode)) analyzeFace(bitmap); else if ("ktp".equals(mode)) analyzeKtp(bitmap); }
            @Override public void onError(@NonNull ImageCaptureException exception) { tvResult.setText("Gagal: " + exception.getMessage()); }
        });
    }
    private void analyzeMoney(Bitmap bitmap) {
        InputImage img = InputImage.fromBitmap(bitmap, 0);
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(img).addOnSuccessListener(visionText -> {
            String text = visionText.getText().toLowerCase(); String result = "Tidak terdeteksi uang.";
            if (text.contains("100000") || text.contains("100.000") || text.contains("seratus ribu")) result = "💵 SERATUS RIBU RUPIAH"; else if (text.contains("50000") || text.contains("50.000") || text.contains("lima puluh ribu")) result = "💵 LIMA PULUH RIBU RUPIAH"; else if (text.contains("20000") || text.contains("20.000")) result = "💵 DUA PULUH RIBU RUPIAH"; else if (text.contains("10000") || text.contains("10.000")) result = "💵 SEPULUH RIBU RUPIAH"; else if (text.contains("5000") || text.contains("5.000")) result = "💵 LIMA RIBU RUPIAH"; else if (text.contains("2000") || text.contains("2.000")) result = "💵 DUA RIBU RUPIAH"; else if (text.contains("1000") || text.contains("1.000")) result = "💵 SERIBU RUPIAH"; else if (text.contains("bank indonesia")) result = "💵 Uang Rupiah terdeteksi, nominal tidak jelas."; else result = "Teks: " + visionText.getText();
            tvResult.setText(result); speak(result);
        }).addOnFailureListener(e -> { tvResult.setText("Gagal OCR: " + e.getMessage()); });
    }
    private void analyzeColor(Bitmap bitmap) {
        try { int w = bitmap.getWidth(); int h = bitmap.getHeight(); int cx = w/2; int cy = h/2; long rSum=0,gSum=0,bSum=0; int cnt=0; for (int x=cx-50; x<cx+50; x+=5) for (int y=cy-50; y<cy+50; y+=5) if (x>=0 && x<w && y>=0 && y<h) { int p=bitmap.getPixel(x,y); rSum+= Color.red(p); gSum+=Color.green(p); bSum+=Color.blue(p); cnt++; } int r=(int)(rSum/cnt), g=(int)(gSum/cnt), b=(int)(bSum/cnt); String color = getColorName(r,g,b); String result = "🎨 Warna: " + color + " RGB " + r + ","+g+","+b; tvResult.setText(result); speak(result); } catch (Exception e) { tvResult.setText("Gagal warna: " + e.getMessage()); }
    }
    private String getColorName(int r,int g,int b){ if (r>200 && g>200 && b>200) return "Putih"; if (r<50 && g<50 && b<50) return "Hitam"; if (r>180 && g<100 && b<100) return "Merah"; if (r<100 && g>150 && b<100) return "Hijau"; if (r<100 && g<100 && b>150) return "Biru"; if (r>180 && g>180 && b<100) return "Kuning"; if (r>150 && g<100 && b>150) return "Ungu"; if (r>200 && g>100 && b<50) return "Oranye"; if (r>100 && g>70 && b<50) return "Coklat"; return "Campuran"; }
    private void analyzeFace(Bitmap bitmap) {
        InputImage img = InputImage.fromBitmap(bitmap,0);
        FaceDetectorOptions options = new FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build();
        FaceDetection.getClient(options).process(img).addOnSuccessListener(faces -> { String result = faces.isEmpty() ? "Tidak ada orang" : "Terdeteksi " + faces.size() + " orang di depan anda"; tvResult.setText(result); speak(result); }).addOnFailureListener(e -> tvResult.setText("Gagal deteksi"));
    }
    private void analyzeKtp(Bitmap bitmap) {
        InputImage img = InputImage.fromBitmap(bitmap,0);
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(img).addOnSuccessListener(visionText -> {
            String full = visionText.getText();
            String lower = full.toLowerCase();
            StringBuilder sb = new StringBuilder();
            sb.append("🪪 HASIL BACA KTP / KK / DOKUMEN:

");
            // Parsing NIK, Nama, dll
            String nik = extractByKeyword(full, "NIK", 16);
            String nama = extractByKeyword(full, "Nama", 30);
            String ttl = extractByKeyword(full, "Tempat", 30);
            String alamat = extractByKeyword(full, "Alamat", 40);
            if (lower.contains("nik") || lower.contains("ktp") || full.matches(".*\d{16}.*")) {
                sb.append("Dokumen terdeteksi sebagai KTP.
");
                if (!nik.isEmpty()) sb.append("NIK: ").append(nik).append("
");
                if (!nama.isEmpty()) sb.append("Nama: ").append(nama).append("
");
                if (!ttl.isEmpty()) sb.append("TTL: ").append(ttl).append("
");
                if (!alamat.isEmpty()) sb.append("Alamat: ").append(alamat).append("
");
                sb.append("
Teks lengkap: ").append(full);
            } else if (lower.contains("kartu keluarga") || lower.contains("kk")) {
                sb.append("Dokumen terdeteksi Kartu Keluarga.
Teks: ").append(full);
            } else {
                sb.append("Dokumen: 
").append(full);
            }
            String result = sb.toString();
            if (result.length() > 2000) result = result.substring(0,2000);
            tvResult.setText(result);
            // Speak important only
            String speakText = "Membacakan dokumen. ";
            if (!nik.isEmpty()) speakText += "NIK " + nik + ". ";
            if (!nama.isEmpty()) speakText += "Nama " + nama + ". ";
            speakText += " Teks lengkap: " + full.substring(0, Math.min(full.length(), 300));
            speak(speakText);
        }).addOnFailureListener(e -> { tvResult.setText("Gagal baca KTP: " + e.getMessage()); speak("Gagal baca dokumen"); });
    }
    private String extractByKeyword(String text, String keyword, int maxLen) {
        try { int idx = text.toLowerCase().indexOf(keyword.toLowerCase()); if (idx <0) return ""; int start = idx + keyword.length(); int end = Math.min(text.length(), start + maxLen); String sub = text.substring(start, end); sub = sub.replace(":", "").replace("=", "").trim(); int nl = sub.indexOf("
"); if (nl >0) sub = sub.substring(0, nl); return sub.trim(); } catch (Exception e) { return ""; }
    }
    private void speak(String s){ if (tts != null) tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, null); }
    @Override public void onInit(int status){ if (status == TextToSpeech.SUCCESS){ tts.setLanguage(new Locale("id","ID")); tts.setSpeechRate(0.9f); String intro = "Mode " + mode + ". Arahkan kamera dan tekan scan."; speak(intro); tvResult.setText(intro); } }
    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){ super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode==101 && grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED) startCamera(); }
}

