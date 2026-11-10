package com.asisten.bantu;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession;

import java.io.File;

/**
 * Pengelola LLM Offline (MediaPipe LLM Inference API) untuk Kobra OS.
 * Memuat berkas .task dari direktori internal /data/data/com.asisten.bantu/files/
 */
public class LocalLlmEngine {

    private static final String TAG = "LocalLlmEngine";
    private static final String MODEL_FILENAME = "gemma-2b-it-gpu-int4.task"; // Atau gemma-3-270m-it-gpu-int4.task

    private LlmInference llmInference;
    private LlmInferenceSession session;
    private boolean isInitialized = false;
    private final Context context;
    private final Handler mainHandler;

    public LocalLlmEngine(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Mengecek apakah berkas model .task tersedia di penyimpanan internal.
     */
    public boolean isModelPresent() {
        File modelFile = new File(context.getFilesDir(), MODEL_FILENAME);
        return modelFile.exists() && modelFile.length() > 0;
    }

    /**
     * Memuat dan menginisialisasi model bahasa offline di memori.
     * Sebaiknya dipanggil pada thread latar belakang (Background Thread).
     */
    public synchronized boolean initEngine() {
        if (isInitialized && llmInference != null) {
            return true;
        }

        File modelFile = new File(context.getFilesDir(), MODEL_FILENAME);
        if (!modelFile.exists()) {
            Log.e(TAG, "Berkas model tidak ditemukan di: " + modelFile.getAbsolutePath());
            return false;
        }

        try {
            Log.d(TAG, "Mulai memuat model dari: " + modelFile.getAbsolutePath());

            // 1. Konfigurasi Tingkat Model (LlmInferenceOptions)
            LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.getAbsolutePath())
                    .setMaxTokens(512) // Membatasi konteks agar hemat RAM
                    .setPreferredBackend(LlmInference.Backend.GPU) // Akselerasi GPU
                    .build();

            llmInference = LlmInference.createFromOptions(context, options);

            // 2. Konfigurasi Sesi Percakapan (LlmInferenceSessionOptions)
            LlmInferenceSession.LlmInferenceSessionOptions sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTemperature(0.3f) // Nilai rendah agar respons faktual dan ringkas
                    .setTopK(40)
                    .build();

            session = LlmInferenceSession.createFromOptions(llmInference, sessionOptions);
            isInitialized = true;
            Log.i(TAG, "Model LLM Offline berhasil diinisialisasi.");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Gagal menginisialisasi MediaPipe LLM Inference", e);
            isInitialized = false;
            return false;
        }
    }

    /**
     * Mengirimkan pertanyaan ke model offline secara terstreaming
     * dan mengalirkan kalimat ke Speaker TTS Kobra OS.
     *
     * @param prompt Frasa pertanyaan dari pengguna
     * @param speaker Objek Speaker TTS utama Kobra OS
     */
    public void generateResponseAsync(String prompt, Speaker speaker) {
        if (!isInitialized || session == null) {
            if (speaker != null) {
                speaker.speak("Otak offline belum siap atau berkas model belum diunduh.");
            }
            return;
        }

        // Penyangga teks untuk menggabungkan token terstream menjadi frasa utuh sebelum dibaca TTS
        StringBuilder sentenceBuffer = new StringBuilder();

        try {
            session.addQueryChunk(prompt);
            session.generateResponseAsync((partialResult, done) -> {
                if (partialResult != null && !partialResult.isEmpty()) {
                    sentenceBuffer.append(partialResult);

                    // Mengirimkan teks ke TTS setiap kali menemukan tanda baca (titik/koma/ganti baris)
                    String currentText = sentenceBuffer.toString();
                    if (currentText.contains(".") || currentText.contains(",") || currentText.contains("\n")) {
                        String sentenceToSpeak = cleanTextForTts(currentText);
                        sentenceBuffer.setLength(0); // Kosongkan penyangga

                        if (!sentenceToSpeak.isEmpty() && speaker != null) {
                            mainHandler.post(() -> speaker.speakQueued(sentenceToSpeak));
                        }
                    }
                }

                // Saat generasi token selesai
                if (done) {
                    String remainingText = cleanTextForTts(sentenceBuffer.toString());
                    if (!remainingText.isEmpty() && speaker != null) {
                        mainHandler.post(() -> speaker.speakQueued(remainingText));
                    }
                    Log.d(TAG, "Generasi respons LLM selesai.");
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Galat saat menghasilkan respons LLM", e);
            if (speaker != null) {
                mainHandler.post(() -> speaker.speak("Maaf, terjadi masalah saat memproses jawaban offline."));
            }
        }
    }

    /**
     * Membersihkan simbol markdown atau karakter khusus sesuai aturan Kobra OS.
     */
    private String cleanTextForTts(String text) {
        if (text == null) return "";
        return text.replaceAll("[*#_`~\\-]", "") // Hapus markdown
                   .replaceAll("\\s+", " ")     // Rapikan spasi ganda
                   .trim();
    }

    /**
     * Membersihkan sumber daya memori saat service dihentikan.
     */
    public void close() {
        if (session != null) {
            try { session.close(); } catch (Exception ignored) {}
            session = null;
        }
        if (llmInference != null) {
            try { llmInference.close(); } catch (Exception ignored) {}
            llmInference = null;
        }
        isInitialized = false;
        Log.i(TAG, "Sumber daya LLM Offline berhasil dibebaskan.");
    }
}
