package com.asisten.bantu;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;

import java.io.File;

/**
 * Pengelola Mesin LLM Offline (MediaPipe LLM Inference API 0.10.14) untuk Kobra OS.
 * Menggunakan model Gemma 3 1B terkuantisasi dari direktori internal HP.
 */
public class LocalLlmEngine {

    private static final String TAG = "KobraLocalLLM";

    // Nama berkas model Gemma 3 1B di penyimpanan internal (/data/data/com.asisten.bantu/files/)
    private static final String MODEL_FILENAME = "gemma3-1b-it-int4.task";

    private LlmInference llmInference;
    private boolean isInitialized = false;
    private final Context context;
    private final Handler mainHandler;
    private Speaker activeSpeaker;
    private final StringBuilder sentenceBuffer = new StringBuilder();

    public LocalLlmEngine(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Memeriksa keberadaan berkas model terkuantisasi di penyimpanan internal.
     */
    public boolean isModelPresent() {
        File modelFile = new File(context.getFilesDir(), MODEL_FILENAME);
        return modelFile.exists() && modelFile.length() > 0;
    }

    /**
     * Memuat model bahasa offline ke memori. Dipanggil pada thread latar belakang.
     */
    public synchronized boolean initEngine() {
        if (isInitialized && llmInference != null) {
            return true;
        }

        File modelFile = new File(context.getFilesDir(), MODEL_FILENAME);
        if (!modelFile.exists()) {
            Log.w(TAG, "Berkas model Gemma 3 1B belum ditemukan di: " + modelFile.getAbsolutePath());
            return false;
        }

        try {
            Log.d(TAG, "Mulai menginisialisasi Gemma 3 1B dari: " + modelFile.getAbsolutePath());

            LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.getAbsolutePath())
                    .setMaxTokens(512)
                    .setTopK(40)
                    .setTemperature(0.3f)
                    .setResultListener((partialResult, done) -> {
                        if (partialResult != null && !partialResult.isEmpty()) {
                            sentenceBuffer.append(partialResult);

                            String currentText = sentenceBuffer.toString();
                            if (currentText.contains(".") || currentText.contains(",") || currentText.contains("\n")) {
                                String textToSpeak = cleanTextForTts(currentText);
                                sentenceBuffer.setLength(0);

                                if (!textToSpeak.isEmpty() && activeSpeaker != null) {
                                    final Speaker spk = activeSpeaker;
                                    mainHandler.post(() -> spk.speakQueued(textToSpeak));
                                }
                            }
                        }

                        if (done) {
                            String remainingText = cleanTextForTts(sentenceBuffer.toString());
                            sentenceBuffer.setLength(0);
                            if (!remainingText.isEmpty() && activeSpeaker != null) {
                                final Speaker spk = activeSpeaker;
                                mainHandler.post(() -> spk.speakQueued(remainingText));
                            }
                            Log.d(TAG, "Generasi respons Gemma 3 1B selesai.");
                        }
                    })
                    .setErrorListener(e -> {
                        Log.e(TAG, "Galat saat streaming inferensi LLM offline", e);
                        if (activeSpeaker != null) {
                            final Speaker spk = activeSpeaker;
                            mainHandler.post(() -> spk.speak("Terjadi kendala saat memproses jawaban offline."));
                        }
                    })
                    .build();

            llmInference = LlmInference.createFromOptions(context, options);
            isInitialized = true;
            Log.i(TAG, "Mesin LLM Offline (Gemma 3 1B) berhasil diinisialisasi.");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Gagal menginisialisasi MediaPipe LLM Inference", e);
            isInitialized = false;
            return false;
        }
    }

    /**
     * Mengirimkan instruksi/pertanyaan pengguna ke model offline
     * dan mengalirkan teks balasan ke antrean pembacaan TTS Speaker.
     */
    public void generateResponseAsync(String prompt, Speaker speaker) {
        if (!isInitialized || llmInference == null) {
            if (speaker != null) {
                speaker.speak("Otak offline Gemma tiga belum siap atau berkas model belum tersedia.");
            }
            return;
        }

        try {
            this.activeSpeaker = speaker;
            this.sentenceBuffer.setLength(0);
            llmInference.generateResponseAsync(prompt);
        } catch (Exception e) {
            Log.e(TAG, "Galat saat mengeksekusi inferensi LLM offline", e);
            if (speaker != null) {
                mainHandler.post(() -> speaker.speak("Terjadi kendala saat memproses jawaban offline."));
            }
        }
    }

    /**
     * Membersihkan simbol markdown dan karakter khusus agar pengucapan TTS tidak terganggu.
     */
    private String cleanTextForTts(String text) {
        if (text == null) return "";
        return text.replaceAll("[*#_`~\\-]", "")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    /**
     * Membebaskan memori sistem saat service dihentikan.
     */
    public void close() {
        if (llmInference != null) {
            try { llmInference.close(); } catch (Exception ignored) {}
            llmInference = null;
        }
        isInitialized = false;
        activeSpeaker = null;
        Log.i(TAG, "Sumber daya LLM Offline berhasil dibebaskan.");
    }
}
