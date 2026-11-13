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
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.util.Size;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mata Kedua Kobra:
 * - Baca KTP & KK (offline)
 * - Baca & Penghitung Lembar Uang Tunai berurutan (offline OCR + AI fallback)
 * - Deteksi warna & kecocokan padu padan pakaian (offline)
 * - Pembaca layar perangkat digital real-time (ATM, timbangan, meteran)
 * - Pemindai struk & screenshot transfer bank (kamera / galeri)
 * - Pemindai Barcode & QR Code offline (kemasan, obat, URL phishing check)
 * - Deteksi objek sekitar dengan panduan bunyi bip kamera
 * - Terjemahan & Ringkasan hasil OCR dengan ML Kit Translate & Gemma 3 1B
 */
public class VisionActivity extends AppCompatActivity {

    static volatile VisionActivity current;

    private static final Pattern NIK = Pattern.compile("(?<!\\d)\\d{16}(?!\\d)");

    private PreviewView previewView;
    private TextView tvResult, tvStatus;
    private Button btnScan, btnBack, btnReset, btnPickImage, btnSummarize, btnTranslate;
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
    private final KobraScreenHelper screenHelper = new KobraScreenHelper();
    private final ExecutorService cameraExec = Executors.newSingleThreadExecutor();
    private final TextRecognizer ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private BarcodeScanner barcodeScanner;
    private ObjectDetector streamObjectDetector;
    private ToneGenerator toneGenerator;

    // Status mode lanjutan
    private long runningCashTotal = 0L;
    private int billCount = 0;
    private String firstOutfitColor = null;
    private String lastCapturedRawText = "";
    private volatile boolean liveProcessing = false;
    private long lastLiveSpeakAt = 0L;
    private long lastLiveBeepAt = 0L;
    private String lastLiveText = "";

    private final ActivityResultLauncher<String[]> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    processGalleryImage(uri);
                }
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vision);
        previewView = findViewById(R.id.previewViewVision);
        tvResult = findViewById(R.id.tvVisionResult);
        tvStatus = findViewById(R.id.tvVisionStatus);
        btnScan = findViewById(R.id.btnScanVision);
        btnBack = findViewById(R.id.btnBackVision);
        btnReset = findViewById(R.id.btnResetVision);
        btnPickImage = findViewById(R.id.btnPickImageVision);
        btnSummarize = findViewById(R.id.btnSummarizeVision);
        btnTranslate = findViewById(R.id.btnTranslateVision);

        String m = getIntent().getStringExtra("mode");
        mode = m == null ? "describe" : m;
        speaker = Speaker.get(this);
        config = new KobraConfig(this);
        ai = new AiClient(config);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
        } catch (Exception ignored) {}

        if (config.isPrivacyMode()) {
            tvStatus.setText("Mode Privasi Penuh Aktif");
            speak("Mode privasi penuh sedang aktif. Kamera tidak dapat dibuka sebelum mode privasi dimatikan.");
            btnScan.setEnabled(false);
            btnBack.setOnClickListener(v -> finish());
            return;
        }

        btnScan.setOnClickListener(v -> captureNow());
        btnBack.setOnClickListener(v -> finish());
        btnReset.setOnClickListener(v -> resetCounter());
        btnPickImage.setOnClickListener(v -> openGalleryPicker());
        btnSummarize.setOnClickListener(v -> summarizeLastResult());
        btnTranslate.setOnClickListener(v -> translateLastResult(TranslateLanguage.INDONESIAN));

        tvStatus.setText("Mode: " + modeLabel());
        speaker.speak(intro());

        if (Perms.has(this, Manifest.permission.CAMERA)) startCamera();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 201);
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
        if (toneGenerator != null) {
            try { toneGenerator.release(); } catch (Exception ignored) {}
            toneGenerator = null;
        }
        try { ocr.close(); } catch (Exception ignored) {}
        if (barcodeScanner != null) {
            try { barcodeScanner.close(); } catch (Exception ignored) {}
        }
        if (streamObjectDetector != null) {
            try { streamObjectDetector.close(); } catch (Exception ignored) {}
        }
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

    private boolean isLiveStreamMode() {
        return "digital_screen".equals(mode) || "barcode".equals(mode) || "find_object".equals(mode);
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

                if (isLiveStreamMode()) {
                    if ("barcode".equals(mode) && barcodeScanner == null) {
                        barcodeScanner = BarcodeScanning.getClient();
                    }
                    if ("find_object".equals(mode) && streamObjectDetector == null) {
                        streamObjectDetector = ObjectDetection.getClient(new ObjectDetectorOptions.Builder()
                                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                                .enableMultipleObjects()
                                .enableClassification()
                                .build());
                    }
                    ImageAnalysis analysis = new ImageAnalysis.Builder()
                            .setTargetResolution(new Size(640, 480))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build();
                    analysis.setAnalyzer(cameraExec, this::analyzeLiveFrame);
                    boundCamera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis);
                } else {
                    boundCamera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
                }
            } catch (Exception e) {
                speak("Kamera tidak bisa dibuka.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeLiveFrame(ImageProxy proxy) {
        if (busy || liveProcessing || proxy.getImage() == null) {
            proxy.close();
            return;
        }
        long now = System.currentTimeMillis();
        if ("find_object".equals(mode)) {
            if (streamObjectDetector == null) { proxy.close(); return; }
            liveProcessing = true;
            int rot = proxy.getImageInfo().getRotationDegrees();
            int w = (rot == 90 || rot == 270) ? proxy.getHeight() : proxy.getWidth();
            int h = (rot == 90 || rot == 270) ? proxy.getWidth() : proxy.getHeight();
            InputImage img = InputImage.fromMediaImage(proxy.getImage(), rot);
            streamObjectDetector.process(img)
                    .addOnSuccessListener(objs -> handleLiveObjects(objs, w, h))
                    .addOnCompleteListener(t -> { liveProcessing = false; proxy.close(); });
        } else if ("barcode".equals(mode)) {
            if (now - lastLiveSpeakAt < 2200L || barcodeScanner == null) { proxy.close(); return; }
            liveProcessing = true;
            InputImage img = InputImage.fromMediaImage(proxy.getImage(), proxy.getImageInfo().getRotationDegrees());
            barcodeScanner.process(img)
                    .addOnSuccessListener(barcodes -> {
                        if (!barcodes.isEmpty()) {
                            String msg = formatBarcodeResults(barcodes);
                            if (!msg.equals(lastLiveText)) {
                                lastLiveText = msg;
                                lastLiveSpeakAt = System.currentTimeMillis();
                                lastCapturedRawText = msg;
                                HapticFeedback.vibrateSuccess(this);
                                playBeep(true);
                                runOnUiThread(() -> {
                                    tvResult.setText(msg);
                                    speak(msg);
                                });
                            }
                        }
                    })
                    .addOnCompleteListener(t -> { liveProcessing = false; proxy.close(); });
        } else if ("digital_screen".equals(mode)) {
            if (now - lastLiveSpeakAt < 2500L) { proxy.close(); return; }
            liveProcessing = true;
            InputImage img = InputImage.fromMediaImage(proxy.getImage(), proxy.getImageInfo().getRotationDegrees());
            ocr.process(img)
                    .addOnSuccessListener(res -> {
                        String raw = res.getText() != null ? res.getText().trim() : "";
                        if (raw.length() >= 2 && !raw.equals(lastLiveText)) {
                            lastLiveText = raw;
                            lastLiveSpeakAt = System.currentTimeMillis();
                            lastCapturedRawText = raw;
                            String parsed = MoneyAndReceiptParser.parseDigitalScreen(raw);
                            runOnUiThread(() -> {
                                tvResult.setText(parsed);
                                if (!speaker.isSpeaking()) {
                                    speak(parsed);
                                }
                            });
                        }
                    })
                    .addOnCompleteListener(t -> { liveProcessing = false; proxy.close(); });
        } else {
            proxy.close();
        }
    }

    private void handleLiveObjects(List<com.google.mlkit.vision.objects.DetectedObject> objs, int w, int h) {
        OfflineVision.ObjectGuidance g = OfflineVision.findNearestObject(objs, w, h);
        if (g == null) {
            runOnUiThread(() -> tvStatus.setText("Mode: Cari Objek (memindai sekitar...)"));
            return;
        }
        long now = System.currentTimeMillis();
        long beepInterval = g.closeness > 0.65 ? 300L : (g.closeness > 0.30 ? 650L : 1100L);
        if (now - lastLiveBeepAt >= beepInterval) {
            lastLiveBeepAt = now;
            playBeep(g.closeness > 0.55);
        }
        runOnUiThread(() -> {
            tvStatus.setText("Terdeteksi: " + g.label + " (" + g.direction + ")");
            tvResult.setText(g.spokenText);
        });
        if (now - lastLiveSpeakAt >= 3200L && !speaker.isSpeaking()) {
            lastLiveSpeakAt = now;
            speak(g.spokenText);
        }
    }

    private void playBeep(boolean highUrgency) {
        if (toneGenerator == null) return;
        try {
            toneGenerator.startTone(
                    highUrgency ? ToneGenerator.TONE_PROP_BEEP2 : ToneGenerator.TONE_PROP_BEEP,
                    highUrgency ? 120 : 75);
        } catch (Exception ignored) {}
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

    public void resetCounter() {
        HapticFeedback.vibrateAction(this);
        runningCashTotal = 0L;
        billCount = 0;
        firstOutfitColor = null;
        lastLiveText = "";
        tvResult.setText("Hitungan / perbandingan direset.");
        speak("Hitungan dan sesi pemindaian direset.");
    }

    private void openGalleryPicker() {
        HapticFeedback.vibrateAction(this);
        speak("Pilih gambar atau tangkapan layar transfer dari galeri HP.");
        try {
            imagePickerLauncher.launch(new String[]{"image/*"});
        } catch (Exception e) {
            speak("Gagal membuka galeri gambar di HP ini.");
        }
    }

    private void processGalleryImage(Uri uri) {
        busy = true;
        tvStatus.setText("Membaca gambar dari galeri...");
        cameraExec.execute(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                Bitmap bmp = BitmapFactory.decodeStream(is);
                runOnUiThread(() -> {
                    if (bmp == null) {
                        done("Gambar tidak dapat dibuka.");
                        return;
                    }
                    if ("receipt".equals(mode) || "describe".equals(mode) || "digital_screen".equals(mode)) {
                        analyzeReceiptOrScreen(bmp, "receipt".equals(mode) || "describe".equals(mode));
                    } else {
                        analyze(bmp);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> done("Gagal membaca gambar dari galeri."));
            }
        });
    }

    public void summarizeLastResult() {
        String source = !lastCapturedRawText.isEmpty() ? lastCapturedRawText : tvResult.getText().toString();
        if (source == null || source.trim().isEmpty()) {
            speak("Belum ada hasil bacaan untuk diringkas.");
            return;
        }
        tvStatus.setText("Meringkas bacaan...");
        speak("Sebentar, saya ringkas hasil bacaannya.");
        LocalLlmEngine.getInstance(this).summarizeText("hasil kamera", source, ai, speaker, summary -> {
            tvStatus.setText("Ringkasan selesai.");
            tvResult.setText(summary);
            speak(summary);
        });
    }

    public void translateLastResult(String targetLang) {
        String source = !lastCapturedRawText.isEmpty() ? lastCapturedRawText : tvResult.getText().toString();
        if (source == null || source.trim().isEmpty()) {
            speak("Belum ada teks yang terbaca untuk diterjemahkan.");
            return;
        }
        String srcLang = TranslateLanguage.INDONESIAN.equals(targetLang)
                ? TranslateLanguage.ENGLISH : TranslateLanguage.INDONESIAN;
        OfflineTranslatorHelper.translate(this, source, srcLang, targetLang, new OfflineTranslatorHelper.Callback() {
            @Override public void onTranslated(String translatedText, String targetLangLabel) {
                tvStatus.setText("Terjemahan (" + targetLangLabel + "):");
                tvResult.setText(translatedText);
                speak("Terjemahan dalam bahasa " + targetLangLabel + ": " + translatedText);
            }
            @Override public void onStatus(String statusMessage) {
                tvStatus.setText(statusMessage);
            }
            @Override public void onError(String message) {
                tvStatus.setText(message);
                speak(message);
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
                done("Warnanya " + OfflineVision.detailedColorName(bmp) + ".");
                return;
            case "outfit":
                analyzeOutfit(bmp);
                return;
            case "face":
                OfflineVision.countFaces(bmp, this::done);
                return;
            case "money":
                analyzeMoneySingle(bmp);
                return;
            case "money_count":
                analyzeMoneySequential(bmp);
                return;
            case "digital_screen":
                analyzeReceiptOrScreen(bmp, false);
                return;
            case "receipt":
                analyzeReceiptOrScreen(bmp, true);
                return;
            case "barcode":
                analyzeBarcodeAndPackage(bmp);
                return;
            case "translate_ocr":
                analyzeAndTranslateOcr(bmp);
                return;
            case "find_object":
                if (canUseAi()) askAi(bmp, "Sebutkan benda utama seperti kursi, pintu, meja, atau tangga dan arah posisinya (kiri, tengah, kanan) secara singkat.");
                else OfflineVision.describe(bmp, this::done);
                return;
            default:
                if (canUseAi()) askAi(bmp, promptFor("describe"));
                else OfflineVision.describe(bmp, this::done);
        }
    }

    private void analyzeOutfit(Bitmap bmp) {
        String centerColor = OfflineVision.detailedColorName(bmp);
        if (firstOutfitColor == null) {
            firstOutfitColor = centerColor;
            String singleFrameSummary = OfflineVision.analyzeOutfitHarmony(bmp);
            done("Pakaian pertama (atasan) berwarna " + centerColor + ". "
                    + "Sekarang scan pakaian kedua (bawahan) untuk cek kecocokan, atau tekan RESET. "
                    + "Analisis cepat satu frame: " + singleFrameSummary);
        } else {
            String first = firstOutfitColor;
            firstOutfitColor = null;
            String verdict = OfflineVision.evaluateOutfitMatch(first, centerColor);
            done("Pakaian pertama berwarna " + first + ", pakaian kedua berwarna " + centerColor + ". " + verdict);
        }
    }

    private void analyzeMoneySingle(Bitmap bmp) {
        ocr.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener(res -> {
                    String raw = res.getText();
                    MoneyAndReceiptParser.BillResult bill = MoneyAndReceiptParser.parseRupiahBill(raw, bmp);
                    if (bill.amount > 0 && bill.confidentOcr) {
                        done("Uang " + MoneyAndReceiptParser.formatRupiah(bill.amount) + ".");
                    } else if (canUseAi()) {
                        askAi(bmp, promptFor("money"));
                    } else if (bill.amount > 0) {
                        done(bill.explanation + ".");
                    } else {
                        done("Nominal uang belum terbaca jelas. Dekatkan kamera ke angka uang dan pastikan cahaya cukup.");
                    }
                })
                .addOnFailureListener(e -> {
                    if (canUseAi()) askAi(bmp, promptFor("money"));
                    else done("Gagal membaca uang.");
                });
    }

    private void analyzeMoneySequential(Bitmap bmp) {
        ocr.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener(res -> {
                    MoneyAndReceiptParser.BillResult bill = MoneyAndReceiptParser.parseRupiahBill(res.getText(), bmp);
                    if (bill.amount > 0 && bill.confidentOcr) {
                        addBillToTotal(bill.amount, "");
                    } else if (canUseAi()) {
                        tvStatus.setText("Membaca nominal uang dengan AI...");
                        ai.describeImage(promptFor("money"), bmp, new AiClient.Callback() {
                            @Override public void onResult(String text) {
                                long parsed = MoneyAndReceiptParser.parseAiNominal(text);
                                if (parsed > 0) {
                                    addBillToTotal(parsed, text);
                                } else {
                                    done("Nominal belum pasti, tidak ditambahkan ke total. " + text);
                                }
                            }
                            @Override public void onError(String message) {
                                if (bill.amount > 0) addBillToTotal(bill.amount, bill.explanation);
                                else done(message);
                            }
                        });
                    } else if (bill.amount > 0) {
                        addBillToTotal(bill.amount, bill.explanation);
                    } else {
                        done("Nominal lembaran uang belum terbaca. Posisikan angka uang di tengah lalu scan lagi.");
                    }
                })
                .addOnFailureListener(e -> done("Gagal memindai uang."));
    }

    private void addBillToTotal(long amount, String note) {
        billCount++;
        runningCashTotal += amount;
        HapticFeedback.vibrateSuccess(this);
        String spoken = "Lembar ke-" + billCount + ": " + MoneyAndReceiptParser.formatRupiah(amount)
                + ". Total sementara: " + MoneyAndReceiptParser.formatRupiah(runningCashTotal) + ".";
        done(note != null && !note.isEmpty() ? spoken + " (" + note + ")" : spoken);
    }

    private void analyzeReceiptOrScreen(Bitmap bmp, boolean isReceipt) {
        ocr.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener(res -> {
                    String raw = res.getText() != null ? res.getText().trim() : "";
                    lastCapturedRawText = raw;
                    if (raw.isEmpty() && canUseAi()) {
                        askAi(bmp, isReceipt
                                ? "Baca bukti transfer bank ini: sebutkan bank/aplikasi, status transaksi, nominal transfer rupiah, nama pengirim, dan nama penerima."
                                : "Baca seluruh angka dan teks yang tampil di layar perangkat digital ini secara jelas.");
                        return;
                    }
                    String parsed = isReceipt
                            ? MoneyAndReceiptParser.parseBankTransferReceipt(raw)
                            : MoneyAndReceiptParser.parseDigitalScreen(raw);
                    done(parsed);
                })
                .addOnFailureListener(e -> done("Gagal membaca teks pada layar atau struk."));
    }

    private void analyzeBarcodeAndPackage(Bitmap bmp) {
        if (barcodeScanner == null) barcodeScanner = BarcodeScanning.getClient();
        InputImage img = InputImage.fromBitmap(bmp, 0);
        barcodeScanner.process(img)
                .addOnSuccessListener(barcodes -> ocr.process(img)
                        .addOnSuccessListener(ocrRes -> {
                            String ocrTxt = ocrRes.getText() == null ? "" : ocrRes.getText().replaceAll("\\s+", " ").trim();
                            lastCapturedRawText = ocrTxt;
                            String barStr = barcodes.isEmpty() ? "Barcode tidak terlihat di foto." : formatBarcodeResults(barcodes);
                            if (!ocrTxt.isEmpty()) {
                                if (ocrTxt.length() > 160) ocrTxt = ocrTxt.substring(0, 160) + "...";
                                done(barStr + " Teks kemasan terbaca: " + ocrTxt);
                            } else {
                                done(barStr);
                            }
                        })
                        .addOnFailureListener(e -> done(barcodes.isEmpty()
                                ? "Barcode maupun teks kemasan belum terbaca."
                                : formatBarcodeResults(barcodes))))
                .addOnFailureListener(e -> done("Gagal memindai barcode."));
    }

    private void analyzeAndTranslateOcr(Bitmap bmp) {
        ocr.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener(res -> {
                    String raw = res.getText() == null ? "" : res.getText().trim();
                    lastCapturedRawText = raw;
                    if (raw.isEmpty()) {
                        done("Tidak ada teks yang terbaca untuk diterjemahkan.");
                        return;
                    }
                    tvResult.setText("Teks asli: " + raw);
                    translateLastResult(TranslateLanguage.INDONESIAN);
                    busy = false;
                })
                .addOnFailureListener(e -> done("Gagal membaca teks kamera."));
    }

    private static String formatBarcodeResults(List<Barcode> barcodes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, barcodes.size()); i++) {
            Barcode b = barcodes.get(i);
            String val = b.getRawValue() != null ? b.getRawValue() : "";
            int type = b.getValueType();
            if (type == Barcode.TYPE_URL || val.startsWith("http://") || val.startsWith("https://")) {
                String warn = SecurityAndPinHelper.inspectForPhishing(val);
                sb.append("QR Tautan: ").append(val).append(". ");
                if (!warn.isEmpty()) sb.append(warn).append(" ");
            } else if (type == Barcode.TYPE_PRODUCT || type == Barcode.TYPE_ISBN) {
                sb.append("Barcode produk: ").append(val).append(". ");
            } else {
                sb.append("Kode terbaca: ").append(val).append(". ");
            }
        }
        return sb.toString().trim();
    }

    private boolean canUseAi() {
        return config.hasAi() && NetworkState.isOnline(this);
    }

    private void askAi(Bitmap bmp, String prompt) {
        tvStatus.setText("Menganalisis dengan AI online...");
        speak("Sebentar, saya analisis gambarnya.");
        ai.describeImage(prompt, bmp, new AiClient.Callback() {
            @Override public void onResult(String text) {
                lastCapturedRawText = text;
                done(text);
            }
            @Override public void onError(String message) { done(message); }
        });
    }

    /** Baca KTP offline dengan ML Kit. Tidak butuh internet. */
    private void readKtp(Bitmap bmp) {
        ocr.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener(result -> {
                    lastCapturedRawText = result.getText();
                    done(parseKtp(result.getText()));
                })
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
            case "money_count": return "Penghitung Lembar Uang Tunai";
            case "color": return "Deteksi warna rinci (offline)";
            case "outfit": return "Padu Padan Warna Pakaian";
            case "face": return "Hitung wajah (offline)";
            case "digital_screen": return "Pembaca Layar Digital Real-Time";
            case "receipt": return "Pemindai Struk & Screenshot Transfer";
            case "barcode": return "Pemindai Barcode & QR Offline";
            case "find_object": return "Cari Objek (Bip Kamera Offline)";
            case "translate_ocr": return "Penerjemah Teks Kamera Offline";
            default: return "Lihat sekitar (online jika tersedia, offline otomatis)";
        }
    }

    private String intro() {
        switch (mode) {
            case "ktp": return "Mode baca KTP. Dekatkan KTP ke kamera, lalu bilang Kobra scan atau tekan SCAN.";
            case "money": return "Mode baca uang. Arahkan kamera ke uang, lalu bilang Kobra scan.";
            case "money_count": return "Mode penghitung lembar uang tunai. Scan satu per satu lembar uang, Kobra akan menjumlahkannya secara otomatis.";
            case "color": return "Mode warna. Arahkan kamera ke benda, lalu bilang Kobra scan.";
            case "outfit": return "Mode padu padan pakaian. Scan baju atasan dulu lalu scan bawahan, atau foto keduanya sekaligus.";
            case "face": return "Mode hitung orang. Arahkan kamera, lalu bilang Kobra scan.";
            case "digital_screen": return "Mode pembaca layar perangkat digital aktif. Arahkan ke layar ATM, timbangan, atau meteran.";
            case "receipt": return "Mode pemindai struk dan bukti transfer bank. Tekan SCAN untuk memotret struk atau tekan GALERI untuk memilih screenshot transfer.";
            case "barcode": return "Mode pemindai barcode dan QR offline aktif. Arahkan ke kemasan obat atau kode QR.";
            case "find_object": return "Mode deteksi objek aktif. Bunyi bip akan lebih cepat saat objek dekat atau di tengah layar.";
            case "translate_ocr": return "Mode penerjemah kamera. Arahkan ke teks asing lalu tekan SCAN.";
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
