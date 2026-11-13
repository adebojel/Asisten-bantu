package com.asisten.bantu;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pembaca dokumen .txt dan PDF (menggunakan Android PdfRenderer + ML Kit OCR)
 * dengan kontrol suara & tombol jeda, lanjut, ulangi paragraf, ringkasan Gemma 3, dan terjemahan offline.
 */
public class DocumentReaderActivity extends AppCompatActivity {

    static volatile DocumentReaderActivity current;

    private TextView tvStatus, tvParagraph;
    private Button btnPlayPause;
    private Speaker speaker;
    private AiClient aiClient;
    private final KobraScreenHelper screenHelper = new KobraScreenHelper();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<String> paragraphs = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isPlaying = false;
    private boolean awaitingParagraphEnd = false;

    private final ActivityResultLauncher<String[]> docPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    loadDocument(uri);
                }
            });

    private final Speaker.Listener speechListener = new Speaker.Listener() {
        @Override public void onSpeechStart() {}
        @Override public void onSpeechEnd() {
            if (isPlaying && awaitingParagraphEnd) {
                awaitingParagraphEnd = false;
                main.postDelayed(() -> {
                    if (!isPlaying) return;
                    if (currentIndex + 1 < paragraphs.size()) {
                        currentIndex++;
                        speakCurrentParagraph();
                    } else {
                        isPlaying = false;
                        updatePlayButton();
                        tvStatus.setText("Pembacaan dokumen selesai (" + paragraphs.size() + " paragraf).");
                        speaker.speak("Pembacaan dokumen selesai.");
                    }
                }, 250L);
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_reader);

        tvStatus = findViewById(R.id.tvDocStatus);
        tvParagraph = findViewById(R.id.tvDocParagraph);
        btnPlayPause = findViewById(R.id.btnDocPlayPause);

        speaker = Speaker.get(this);
        aiClient = new AiClient(new KobraConfig(this));
        speaker.addListener(speechListener);

        findViewById(R.id.btnPickDocument).setOnClickListener(v -> {
            HapticFeedback.vibrateAction(this);
            pauseReading(false);
            speaker.speak("Membuka pemilih dokumen. Pilih file teks atau PDF.");
            try {
                docPickerLauncher.launch(new String[]{"text/plain", "application/pdf", "*/*"});
            } catch (Exception e) {
                speaker.speak("Gagal membuka pemilih dokumen di HP ini.");
            }
        });

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        findViewById(R.id.btnDocNext).setOnClickListener(v -> nextParagraph());
        findViewById(R.id.btnDocPrev).setOnClickListener(v -> prevParagraph());
        findViewById(R.id.btnDocRepeat).setOnClickListener(v -> repeatParagraph());
        findViewById(R.id.btnDocSummarize).setOnClickListener(v -> summarizeDocument());
        findViewById(R.id.btnDocTranslate).setOnClickListener(v -> translateCurrentParagraph());
        findViewById(R.id.btnDocBack).setOnClickListener(v -> {
            pauseReading(false);
            finish();
        });

        speaker.speak("Pembaca dokumen siap. Tekan Pilih Dokumen untuk membuka file teks atau PDF.");
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
    }

    @Override protected void onPause() {
        super.onPause();
        if (current == this) current = null;
    }

    @Override protected void onDestroy() {
        isPlaying = false;
        awaitingParagraphEnd = false;
        speaker.removeListener(speechListener);
        executor.shutdown();
        super.onDestroy();
    }

    public void togglePlayPause() {
        HapticFeedback.vibrateAction(this);
        if (paragraphs.isEmpty()) {
            speaker.speak("Belum ada dokumen yang dimuat.");
            return;
        }
        if (isPlaying) {
            pauseReading(true);
        } else {
            resumeReading();
        }
    }

    public void pauseReading(boolean announce) {
        isPlaying = false;
        awaitingParagraphEnd = false;
        speaker.stop();
        updatePlayButton();
        if (announce) {
            tvStatus.setText("Dijeda pada paragraf " + (currentIndex + 1) + " dari " + paragraphs.size());
            speaker.speak("Dijeda.");
        }
    }

    public void resumeReading() {
        if (paragraphs.isEmpty()) {
            speaker.speak("Belum ada dokumen yang dimuat.");
            return;
        }
        isPlaying = true;
        updatePlayButton();
        speakCurrentParagraph();
    }

    public void repeatParagraph() {
        HapticFeedback.vibrateAction(this);
        if (paragraphs.isEmpty()) {
            speaker.speak("Belum ada dokumen yang dimuat.");
            return;
        }
        isPlaying = true;
        updatePlayButton();
        speakCurrentParagraph();
    }

    public void nextParagraph() {
        HapticFeedback.vibrateAction(this);
        if (paragraphs.isEmpty()) {
            speaker.speak("Belum ada dokumen.");
            return;
        }
        if (currentIndex + 1 < paragraphs.size()) {
            currentIndex++;
            isPlaying = true;
            updatePlayButton();
            speakCurrentParagraph();
        } else {
            speaker.speak("Sudah berada di paragraf terakhir.");
        }
    }

    public void prevParagraph() {
        HapticFeedback.vibrateAction(this);
        if (paragraphs.isEmpty()) {
            speaker.speak("Belum ada dokumen.");
            return;
        }
        if (currentIndex > 0) {
            currentIndex--;
            isPlaying = true;
            updatePlayButton();
            speakCurrentParagraph();
        } else {
            speaker.speak("Sudah berada di paragraf pertama.");
        }
    }

    public void summarizeDocument() {
        HapticFeedback.vibrateAction(this);
        if (paragraphs.isEmpty()) {
            speaker.speak("Belum ada dokumen untuk diringkas.");
            return;
        }
        pauseReading(false);
        StringBuilder joined = new StringBuilder();
        for (String p : paragraphs) {
            joined.append(p).append("\n");
            if (joined.length() >= 1500) break;
        }
        tvStatus.setText("Meringkas dokumen...");
        speaker.speak("Sebentar, saya ringkas isi dokumen.");
        LocalLlmEngine.getInstance(this).summarizeText("dokumen", joined.toString(), aiClient, speaker, summary -> {
            tvStatus.setText("Ringkasan dokumen:");
            tvParagraph.setText(summary);
            speaker.speak(summary);
        });
    }

    public void translateCurrentParagraph() {
        HapticFeedback.vibrateAction(this);
        if (paragraphs.isEmpty()) {
            speaker.speak("Belum ada paragraf untuk diterjemahkan.");
            return;
        }
        pauseReading(false);
        String p = paragraphs.get(currentIndex);
        OfflineTranslatorHelper.translate(this, p, TranslateLanguage.ENGLISH, TranslateLanguage.INDONESIAN,
                new OfflineTranslatorHelper.Callback() {
                    @Override public void onTranslated(String translatedText, String targetLangLabel) {
                        tvStatus.setText("Terjemahan paragraf " + (currentIndex + 1) + ":");
                        tvParagraph.setText(translatedText);
                        speaker.speak(translatedText);
                    }
                    @Override public void onStatus(String statusMessage) {
                        tvStatus.setText(statusMessage);
                    }
                    @Override public void onError(String message) {
                        tvStatus.setText(message);
                        speaker.speak(message);
                    }
                });
    }

    private void speakCurrentParagraph() {
        if (paragraphs.isEmpty() || currentIndex < 0 || currentIndex >= paragraphs.size()) return;
        String text = paragraphs.get(currentIndex);
        tvStatus.setText("Paragraf " + (currentIndex + 1) + " dari " + paragraphs.size());
        tvParagraph.setText(text);
        awaitingParagraphEnd = true;
        speaker.speak(text);
    }

    private void updatePlayButton() {
        btnPlayPause.setText(isPlaying ? "⏸ JEDA" : "▶ PUTAR");
    }

    private void loadDocument(Uri uri) {
        String name = queryFileName(uri);
        String mime = getContentResolver().getType(uri);
        boolean isPdf = (mime != null && mime.toLowerCase(Locale.ROOT).contains("pdf"))
                || name.toLowerCase(Locale.ROOT).endsWith(".pdf");

        tvStatus.setText("Memuat dokumen: " + name + "...");
        speaker.speak("Memuat dokumen " + name + ". Harap tunggu.");
        paragraphs.clear();
        currentIndex = 0;

        executor.execute(() -> {
            try {
                String extracted = isPdf ? extractPdfText(uri) : extractTxtText(uri);
                List<String> split = splitIntoParagraphs(extracted);
                main.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    paragraphs.addAll(split);
                    if (paragraphs.isEmpty()) {
                        tvStatus.setText("Dokumen kosong atau tidak berisi teks yang terbaca.");
                        tvParagraph.setText("");
                        speaker.speak("Dokumen tidak berisi teks yang dapat dibaca.");
                    } else {
                        isPlaying = true;
                        updatePlayButton();
                        speaker.speak("Dokumen dimuat, terdapat " + paragraphs.size() + " paragraf. Memulai pembacaan.");
                        main.postDelayed(this::speakCurrentParagraph, 2400L);
                    }
                });
            } catch (Exception e) {
                String err = e.getMessage() != null ? e.getMessage() : "Kesalahan membaca berkas";
                main.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    tvStatus.setText("Gagal memuat dokumen: " + err);
                    speaker.speak("Gagal memuat dokumen. " + err);
                });
            }
        });
    }

    private String extractTxtText(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private String extractPdfText(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd == null) throw new IllegalStateException("File PDF tidak dapat dibuka.");
            try (PdfRenderer renderer = new PdfRenderer(pfd)) {
                int pageCount = Math.min(renderer.getPageCount(), 35);
                for (int i = 0; i < pageCount; i++) {
                    try (PdfRenderer.Page page = renderer.openPage(i)) {
                        int w = Math.min(1600, Math.max(800, page.getWidth() * 2));
                        int h = (int) ((double) page.getHeight() * w / Math.max(1, page.getWidth()));
                        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        bmp.eraseColor(0xFFFFFFFF);
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        Text res = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)));
                        bmp.recycle();
                        if (res.getText() != null && !res.getText().trim().isEmpty()) {
                            sb.append(res.getText().trim()).append("\n\n");
                        }
                    }
                }
            }
        } finally {
            try { recognizer.close(); } catch (Exception ignored) {}
        }
        return sb.toString();
    }

    private static List<String> splitIntoParagraphs(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return out;
        String[] blocks = text.split("\\r?\\n\\s*\\r?\\n+");
        for (String b : blocks) {
            String clean = b.replaceAll("\\s+", " ").trim();
            if (clean.isEmpty()) continue;
            // Pecah blok yang sangat panjang agar nyaman dijeda/diulangi
            while (clean.length() > 380) {
                int cut = clean.lastIndexOf('.', 380);
                if (cut < 120) cut = clean.lastIndexOf(' ', 380);
                if (cut < 80) cut = 380;
                out.add(clean.substring(0, cut + 1).trim());
                clean = clean.substring(cut + 1).trim();
            }
            if (!clean.isEmpty()) out.add(clean);
        }
        return out;
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
        return result != null ? result : "dokumen";
    }
}
