package com.asisten.bantu;

import android.Manifest;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mata Kobra: foto kamera lalu dibacakan.
 * KTP dibaca offline dengan ML Kit. Uang, warna, orang, dan deskripsi memakai AI gambar (OpenRouter).
 */
public class VisionActivity extends AppCompatActivity {

    static volatile VisionActivity current;

    private static final Pattern NIK = Pattern.compile("(?<!\\d)\\d{16}(?!\\d)");

    private PreviewView previewView;
    private TextView tvResult, tvStatus;
    private Button btnScan, btnBack;
    private String mode = "describe";
    private ImageCapture imageCapture;
    private Camera boundCamera;
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private boolean autoTorchChecked = false;
    private boolean autoTorchEnabled = false;
    private Speaker speaker;
    private KobraConfig config;
    private AiClient ai;
    private boolean busy = false;
    private final ExecutorService cameraExec = Executors.newSingleThreadExecutor();
    private final TextRecognizer ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vision);
        previewView = findViewById(R.id.previewViewVision);
        tvResult = findViewById(R.id.tvVisionResult);
        tvStatus = findViewById(R.id.tvVisionStatus);
        btnScan = findViewById(R.id.btnScanVision);
        btnBack = findViewById(R.id.btnBackVision);

        String m = getIntent().getStringExtra("mode");
        mode = m == null ? "describe" : m;
        speaker = Speaker.get(this);
        config = new KobraConfig(this);
        ai = new AiClient(config);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }

        if (config.isPrivacyMode()) {
            tvStatus.setText("Mode Privasi Penuh Aktif");
            speak("Mode privasi penuh sedang aktif. Kamera tidak dapat dibuka sebelum mode privasi dimatikan.");
            btnScan.setEnabled(false);
            btnBack.setOnClickListener(v -> finish());
            return;
        }

        btnScan.setOnClickListener(v -> captureNow());
        btnBack.setOnClickListener(v -> finish());
        tvStatus.setText("Mode: " + modeLabel());
        speaker.speak(intro());

        if (Perms.has(this, Manifest.permission.CAMERA)) startCamera();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 201);
    }

    @Override protected void onResume() {
        super.onResume();
        current = this;
        if (!config.isPrivacyMode() && sensorManager != null && lightSensor != null) {
            autoTorchChecked = false;
            sensorManager.registerListener(lightListener, lightSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (current == this) current = null;
        if (sensorManager != null) {
            sensorManager.unregisterListener(lightListener);
        }
        disableTorch();
    }

    @Override protected void onDestroy() {
        disableTorch();
        cameraExec.shutdown();
        super.onDestroy();
    }

    private final SensorEventListener lightListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() != Sensor.TYPE_LIGHT || autoTorchChecked || boundCamera == null) return;
            float lux = event.values[0];
            autoTorchChecked = true;
            if (sensorManager != null) {
                sensorManager.unregisterListener(this);
            }
            if (lux < 12.0f && boundCamera.getCameraInfo().hasFlashUnit()) {
                boundCamera.getCameraControl().enableTorch(true);
                autoTorchEnabled = true;
                tvStatus.setText("Mode: " + modeLabel() + " (Senter otomatis nyala)");
                speak("Tempat gelap terdeteksi, senter dinyalakan otomatis.");
            }
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private void disableTorch() {
        if (autoTorchEnabled && boundCamera != null) {
            try {
                boundCamera.getCameraControl().enableTorch(false);
            } catch (Exception ignored) {}
            autoTorchEnabled = false;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 201 && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            speak("Izin kamera belum aktif. Buka Kobra, lalu tekan Izin.");
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();
                provider.unbindAll();
                boundCamera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
            } catch (Exception e) {
                speak("Kamera tidak bisa dibuka.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /** Dipanggil dari tombol SCAN atau dari perintah suara "Kobra scan". */
    public void captureNow() {
        if (imageCapture == null) { speak("Kamera belum siap."); return; }
        if (busy) return;
        busy = true;
        tvStatus.setText("Memotret...");
        imageCapture.takePicture(cameraExec, new ImageCapture.OnImageCapturedCallback() {
            @Override public void onCaptureSuccess(@NonNull ImageProxy image) {
                Bitmap bmp = toBitmap(image);
                image.close();
                runOnUiThread(() -> analyze(bmp));
            }
            @Override public void onError(@NonNull ImageCaptureException e) {
                runOnUiThread(() -> {
                    busy = false;
                    speak("Gagal memotret. Coba lagi.");
                });
            }
        });
    }

    private static Bitmap toBitmap(ImageProxy image) {
        ByteBuffer buf = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bmp == null) return null;
        int rot = image.getImageInfo().getRotationDegrees();
        if (rot == 0) return bmp;
        Matrix m = new Matrix();
        m.postRotate(rot);
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
    }

    private void analyze(Bitmap bmp) {
        if (bmp == null) { done("Foto tidak bisa dibaca. Coba lagi."); return; }
        switch (mode) {
            case "ktp":
                readKtp(bmp);
                return;
            case "color":
                done("Warnanya " + OfflineVision.colorName(bmp) + ".");
                return;
            case "face":
                OfflineVision.countFaces(bmp, this::done);
                return;
            case "money":
                if (!canUseAi()) {
                    done("Baca uang butuh internet dan API key AI. Sambungkan internet, lalu bilang Kobra scan.");
                    return;
                }
                askAi(bmp, promptFor("money"));
                return;
            default:
                if (canUseAi()) askAi(bmp, promptFor("describe"));
                else OfflineVision.describe(bmp, this::done);
        }
    }

    private boolean canUseAi() {
        return config.hasAi() && NetworkState.isOnline(this);
    }

    private void askAi(Bitmap bmp, String prompt) {
        tvStatus.setText("Menganalisis dengan AI online...");
        speak("Sebentar, saya analisis gambarnya.");
        ai.describeImage(prompt, bmp, new AiClient.Callback() {
            @Override public void onResult(String text) { done(text); }
            @Override public void onError(String message) { done(message); }
        });
    }

    /** Baca KTP offline dengan ML Kit. Tidak butuh internet. */
    private void readKtp(Bitmap bmp) {
        ocr.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener(result -> done(parseKtp(result.getText())))
                .addOnFailureListener(e -> done("Dokumen gagal dibaca. Dekatkan kamera dan pastikan cahaya cukup."));
    }

    static String parseKtp(String text) {
        String nik = null, nama = null, ttl = null, alamat = null;
        Matcher m = NIK.matcher(text);
        if (m.find()) nik = m.group();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            String low = line.toLowerCase(Locale.ROOT);
            if (low.startsWith("nama")) nama = valueOf(line);
            else if (low.contains("lahir")) ttl = valueOf(line);
            else if (low.startsWith("alamat")) alamat = valueOf(line);
        }
        if (nik == null && nama == null) {
            return "Dokumen ini tidak terbaca sebagai KTP. Dekatkan kamera dan pastikan cahaya cukup.";
        }
        StringBuilder sb = new StringBuilder();
        if (text.toLowerCase(Locale.ROOT).contains("kartu keluarga")) sb.append("Ini Kartu Keluarga. ");
        else sb.append("Ini KTP. ");
        if (nik != null) sb.append("Nomor induk kependudukan ").append(nik.replaceAll("(?<=\\d)(?=\\d)", " ")).append(". ");
        if (nama != null && !nama.isEmpty()) sb.append("Nama ").append(nama).append(". ");
        if (ttl != null && !ttl.isEmpty()) sb.append("Tempat tanggal lahir ").append(ttl).append(". ");
        if (alamat != null && !alamat.isEmpty()) sb.append("Alamat ").append(alamat).append(".");
        return sb.toString();
    }

    private static String valueOf(String line) {
        int i = line.indexOf(':');
        return i < 0 ? "" : line.substring(i + 1).trim();
    }

    private void done(String text) {
        busy = false;
        tvStatus.setText("Selesai. Tekan SCAN untuk foto berikutnya.");
        tvResult.setText(text);
        speak(text);
    }

    private void speak(String text) { speaker.speak(text); }

    private String modeLabel() {
        switch (mode) {
            case "ktp": return "Baca KTP dan KK (offline)";
            case "money": return "Baca uang";
            case "color": return "Deteksi warna (offline)";
            case "face": return "Hitung wajah (offline)";
            default: return "Lihat sekitar (online jika tersedia, offline otomatis)";
        }
    }

    private String intro() {
        switch (mode) {
            case "ktp": return "Mode baca KTP. Dekatkan KTP ke kamera, lalu bilang Kobra scan atau tekan SCAN.";
            case "money": return "Mode baca uang. Arahkan kamera ke uang, lalu bilang Kobra scan.";
            case "color": return "Mode warna. Arahkan kamera ke benda, lalu bilang Kobra scan.";
            case "face": return "Mode hitung orang. Arahkan kamera, lalu bilang Kobra scan.";
            default: return "Mode lihat sekitar. Arahkan kamera ke depan, lalu bilang Kobra scan.";
        }
    }

    private static String promptFor(String mode) {
        switch (mode) {
            case "money":
                return "Ini uang kertas atau koin apa? Sebutkan nominal rupiahnya dengan jelas. "
                        + "Jika tidak yakin, katakan tidak yakin dan sarankan cara memotret yang lebih baik.";
            case "color":
                return "Sebutkan warna utama benda di tengah gambar dalam satu kalimat singkat.";
            case "face":
                return "Berapa orang yang terlihat di gambar? Jelaskan posisinya singkat: kiri, tengah, kanan, dekat atau jauh. "
                        + "Jika tidak ada orang, katakan tidak ada.";
            default:
                return "Jelaskan apa yang ada di depan kamera secara singkat. Sebutkan jika ada bahaya seperti "
                        + "tangga, lubang, kendaraan, kaca, atau halangan di depan.";
        }
    }
}
