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
    public static final String MODEL_FILENAME = "gemma3-1b-it-int4.task";

    public interface StreamingCallback {
        void onPartialResult(String partialText, boolean isComplete);
        void onError(String error);
    }

    private static volatile LocalLlmEngine sInstance;

    private LlmInference llmInference;
    private boolean isInitialized = false;
    private boolean safeMode = false;
    private String lastError = "";
    private final Context context;
    private final Handler mainHandler;
    private Speaker activeSpeaker;
    private StreamingCallback activeCallback;
    private final StringBuilder sentenceBuffer = new StringBuilder();

    public static synchronized LocalLlmEngine getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new LocalLlmEngine(context.getApplicationContext());
        }
        return sInstance;
    }

    public LocalLlmEngine(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Mengembalikan objek File untuk model internal.
     */
    public File getModelFile() {
        return new File(context.getFilesDir(), MODEL_FILENAME);
    }

    /**
     * Memeriksa apakah mesin LLM sudah berhasil diinisialisasi dan siap di memori.
     */
    public synchronized boolean isInitialized() {
        return isInitialized && llmInference != null && !safeMode;
    }

    /**
     * True jika aplikasi berjalan di Mode Aman karena model belum tersedia atau memori penuh.
     */
    public synchronized boolean isSafeMode() {
        return safeMode || !isModelPresent();
    }

    public synchronized String getLastError() {
        return lastError;
    }

    /**
     * Memeriksa keberadaan berkas model terkuantisasi di penyimpanan internal.
     */
    public boolean isModelPresent() {
        File modelFile = getModelFile();
        return modelFile.exists() && modelFile.length() > 1024 * 1024;
    }

    /**
     * Memuat model bahasa offline ke memori. Dipanggil pada thread latar belakang.
     * Menangani OOM / memori penuh dengan masuk ke Mode Aman tanpa crash.
     */
    public synchronized boolean initEngine() {
        if (isInitialized && llmInference != null) {
            safeMode = false;
            return true;
        }

        File modelFile = getModelFile();
        if (!modelFile.exists() || modelFile.length() <= 1024 * 1024) {
            Log.w(TAG, "Berkas model Gemma 3 1B belum ditemukan di: " + modelFile.getAbsolutePath());
            safeMode = true;
            lastError = "Model belum terpasang";
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
                        if (activeCallback != null) {
                            final StreamingCallback cb = activeCallback;
                            mainHandler.post(() -> cb.onPartialResult(partialResult, done));
                        }

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
                        if (activeCallback != null) {
                            final StreamingCallback cb = activeCallback;
                            mainHandler.post(() -> cb.onError(e != null ? e.getMessage() : "Galat inferensi"));
                        }
                        if (activeSpeaker != null) {
                            final Speaker spk = activeSpeaker;
                            mainHandler.post(() -> spk.speak("Terjadi kendala saat memproses jawaban offline. Beralih ke Mode Aman."));
                        }
                    })
                    .build();

            llmInference = LlmInference.createFromOptions(context, options);
            isInitialized = true;
            safeMode = false;
            lastError = "";
            Log.i(TAG, "Mesin LLM Offline (Gemma 3 1B) berhasil diinisialisasi.");
            return true;

        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "Memori penuh (OOM) saat memuat model LLM offline. Masuk ke Mode Aman.", oom);
            cleanupPartialInference();
            safeMode = true;
            lastError = "Memori HP tidak cukup (OOM)";
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "Gagal menginisialisasi MediaPipe LLM Inference. Masuk ke Mode Aman.", t);
            cleanupPartialInference();
            safeMode = true;
            lastError = t.getMessage() != null ? t.getMessage() : "Gagal memuat model";
            return false;
        }
    }

    private void cleanupPartialInference() {
        if (llmInference != null) {
            try { llmInference.close(); } catch (Throwable ignored) {}
            llmInference = null;
        }
        isInitialized = false;
    }

    /**
     * Memuat ulang mesin bahasa offline (misal setelah model baru diimpor).
     */
    public synchronized boolean reloadEngine() {
        close();
        return initEngine();
    }

    /**
     * Mengirimkan instruksi/pertanyaan pengguna ke model offline
     * dan mengalirkan teks balasan ke antrean pembacaan TTS Speaker.
     */
    public void generateResponseAsync(String prompt, Speaker speaker) {
        generateResponseAsync(prompt, speaker, null);
    }

    /**
     * Mengirimkan instruksi ke model offline dengan dukungan callback streaming untuk UI.
     * Jika mesin gagal atau memori penuh, langsung masuk ke Mode Aman tanpa crash.
     */
    public synchronized void generateResponseAsync(String prompt, Speaker speaker, StreamingCallback callback) {
        if (!isInitialized || llmInference == null || safeMode) {
            safeMode = true;
            String msg = "Model AI offline belum tersedia atau memori penuh. Beralih ke Mode Aman perintah sistem lokal.";
            if (speaker != null) {
                mainHandler.post(() -> speaker.speak(msg));
            }
            if (callback != null) {
                mainHandler.post(() -> callback.onError(msg));
            }
            return;
        }

        try {
            this.activeSpeaker = speaker;
            this.activeCallback = callback;
            this.sentenceBuffer.setLength(0);
            llmInference.generateResponseAsync(prompt);
        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "Memori penuh saat inferensi LLM offline", oom);
            cleanupPartialInference();
            safeMode = true;
            lastError = "Memori penuh saat inferensi";
            String msg = "Memori HP penuh saat memproses model offline. Beralih ke Mode Aman perintah sistem lokal.";
            if (speaker != null) {
                mainHandler.post(() -> speaker.speak(msg));
            }
            if (callback != null) {
                mainHandler.post(() -> callback.onError(msg));
            }
        } catch (Throwable t) {
            Log.e(TAG, "Galat saat mengeksekusi inferensi LLM offline", t);
            safeMode = true;
            lastError = t.getMessage() != null ? t.getMessage() : "Kesalahan inferensi";
            String msg = "Model offline mengalami kendala. Beralih ke Mode Aman perintah sistem lokal.";
            if (speaker != null) {
                mainHandler.post(() -> speaker.speak(msg));
            }
            if (callback != null) {
                mainHandler.post(() -> callback.onError(msg));
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
    public synchronized void close() {
        cleanupPartialInference();
        activeSpeaker = null;
        activeCallback = null;
        Log.i(TAG, "Sumber daya LLM Offline berhasil dibebaskan.");
    }
}
