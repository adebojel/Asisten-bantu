package com.asisten.bantu;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pengelola Mesin LLM Offline (MediaPipe LLM Inference API 0.10.14) untuk Kobra OS.
 * Memuat model .task secara aman dengan alokasi token adaptif RAM (256/512) serta
 * fallback otomatis ke backend CPU tanpa memblokir berkas model yang valid.
 */
public class LocalLlmEngine {

    private static final String TAG = "KobraLocalLLM";
    private static final int MAX_HISTORY_TURNS = 8;
    private static final int MAX_PROMPT_CHARS = 2400;
    private static final long LOW_AVAIL_RAM_THRESHOLD_BYTES = 1600L * 1024L * 1024L;

    // Nama berkas model Gemma 3 1B di penyimpanan internal (/data/data/com.asisten.bantu/files/)
    public static final String MODEL_FILENAME = "gemma3-1b-it-int4.task";
    public static final String CRASH_SAFE_MODE_REASON =
            "Mode Aman: Memori perangkat sedang terbatas saat memuat model lokal. Tekan Reset atau Muat / Uji untuk mencoba ulang di mode hemat CPU 256 token.";

    public interface StreamingCallback {
        void onPartialResult(String partialText, boolean isComplete);
        void onError(String error);
    }

    public interface SummaryCallback {
        void onSummary(String summary);
    }

    public interface InitCallback {
        void onCompleted(boolean success, String message);
    }

    private static final class ChatTurn {
        final String user;
        final String assistant;

        ChatTurn(String user, String assistant) {
            this.user = user;
            this.assistant = assistant;
        }
    }

    private static volatile LocalLlmEngine sInstance;

    private LlmInference llmInference;
    private boolean isInitialized = false;
    private boolean safeMode = false;
    private String lastError = "";
    private final Context context;
    private final Handler mainHandler;
    private final ExecutorService initExec = Executors.newSingleThreadExecutor();
    // Invalidates queued initialization callbacks when the accessibility service releases this engine.
    private final AtomicLong initGeneration = new AtomicLong(0L);
    private Speaker activeSpeaker;
    private StreamingCallback activeCallback;
    private final StringBuilder sentenceBuffer = new StringBuilder();
    private final StringBuilder fullResponseBuffer = new StringBuilder();
    private final List<ChatTurn> sessionHistory = new ArrayList<>();
    private String pendingHistoryUserPrompt = "";
    private boolean shouldRecordHistory = false;

    public static synchronized LocalLlmEngine getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new LocalLlmEngine(context.getApplicationContext());
        }
        return sInstance;
    }

    public LocalLlmEngine(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        syncCrashGuardState();
    }

    private synchronized void syncCrashGuardState() {
        KobraConfig cfg = new KobraConfig(context);
        cfg.checkAndHandlePreviousLlmCrash();
        if (cfg.isLlmSafeMode()) {
            this.safeMode = true;
            String savedReason = cfg.getLlmSafeReason();
            this.lastError = savedReason.isEmpty() ? CRASH_SAFE_MODE_REASON : savedReason;
        }
    }

    /** Mengembalikan objek File untuk model internal. */
    public File getModelFile() {
        return new File(context.getFilesDir(), MODEL_FILENAME);
    }

    /** Memeriksa apakah mesin LLM sudah berhasil diinisialisasi dan siap di memori. */
    public synchronized boolean isInitialized() {
        return isInitialized && llmInference != null && !safeMode;
    }

    /** True jika aplikasi berada di Mode Aman atau berkas model belum tersedia. */
    public synchronized boolean isSafeMode() {
        syncCrashGuardState();
        return safeMode || !isModelPresent();
    }

    public synchronized String getLastError() {
        syncCrashGuardState();
        return lastError;
    }

    /** Memeriksa keberadaan berkas model .task valid di penyimpanan internal (tanpa batas ukuran maksimum). */
    public boolean isModelPresent() {
        File modelFile = getModelFile();
        return modelFile.exists() && modelFile.length() > 1024 * 1024;
    }

    /** Menghapus memori konteks percakapan sesi aktif. */
    public synchronized void clearSessionHistory() {
        sessionHistory.clear();
        pendingHistoryUserPrompt = "";
    }

    /** Mengembalikan jumlah giliran percakapan yang tersimpan dalam sesi saat ini (maks 8). */
    public synchronized int getSessionTurnCount() {
        return sessionHistory.size();
    }

    public synchronized void addSessionTurn(String userText, String assistantReply) {
        if (userText == null || userText.trim().isEmpty()
                || assistantReply == null || assistantReply.trim().isEmpty()) {
            return;
        }
        sessionHistory.add(new ChatTurn(userText.trim(), assistantReply.trim()));
        while (sessionHistory.size() > MAX_HISTORY_TURNS) {
            sessionHistory.remove(0);
        }
    }

    /**
     * Memuat model bahasa offline ke memori secara asinkron agar UI tidak terblokir.
     */
    public void initEngineAsync(boolean forceRetry, InitCallback callback) {
        final long generation = initGeneration.get();
        initExec.execute(() -> {
            // A service teardown/reset may happen while this task is waiting in the executor queue.
            if (generation != initGeneration.get()) return;
            boolean ok = forceRetry ? forceInitEngine() : initEngine();
            if (generation != initGeneration.get()) return;
            String msg = ok
                    ? "Model lokal siap digunakan."
                    : (getLastError().isEmpty() ? CRASH_SAFE_MODE_REASON : getLastError());
            if (callback != null) {
                mainHandler.post(() -> {
                    if (generation == initGeneration.get()) callback.onCompleted(ok, msg);
                });
            }
        });
    }

    /**
     * Memuat model bahasa offline ke memori secara sinkron (panggil dari background thread).
     */
    public synchronized boolean initEngine() {
        return initEngineInternal(false);
    }

    /** Mereset flag Mode Aman crash-guard di memori dan SharedPreferences tanpa memuat model saat itu juga. */
    public synchronized void resetSafeModeFlag() {
        close();
        new KobraConfig(context).clearLlmSafeMode();
        this.safeMode = false;
        this.lastError = "";
    }

    /** Mereset status Mode Aman crash-guard dan mencoba memuat model secara manual atas permintaan pengguna. */
    public synchronized boolean forceInitEngine() {
        resetSafeModeFlag();
        return initEngineInternal(true);
    }

    private synchronized boolean initEngineInternal(boolean ignoreExistingSafeMode) {
        if (isInitialized && llmInference != null) {
            safeMode = false;
            return true;
        }

        KobraConfig cfg = new KobraConfig(context);
        if (!ignoreExistingSafeMode && (cfg.checkAndHandlePreviousLlmCrash() || cfg.isLlmSafeMode())) {
            safeMode = true;
            String savedReason = cfg.getLlmSafeReason();
            lastError = savedReason.isEmpty() ? CRASH_SAFE_MODE_REASON : savedReason;
            Log.w(TAG, "Melewati pemuatan otomatis LLM karena Crash-Guard aktif: " + lastError);
            return false;
        }

        File modelFile = getModelFile();
        if (!modelFile.exists() || modelFile.length() <= 1024 * 1024) {
            Log.w(TAG, "Berkas model .task belum ditemukan di: " + modelFile.getAbsolutePath());
            safeMode = true;
            lastError = "Model belum terpasang";
            return false;
        }

        boolean lowRam = isDeviceLowAvailableRam();
        int initialMaxTokens = lowRam ? 256 : 512;
        int initialTopK = lowRam ? 24 : 40;

        try {
            Log.d(TAG, "Memuat model .task (" + (modelFile.length() / (1024 * 1024))
                    + " MB, maxTokens=" + initialMaxTokens + ")");
            LlmInference.LlmInferenceOptions options = buildOptions(modelFile, initialMaxTokens, initialTopK, lowRam);

            cfg.setLlmInitPending(true);
            try {
                llmInference = LlmInference.createFromOptions(context, options);
            } catch (Throwable primaryErr) {
                Log.w(TAG, "Pemuatan awal gagal, mencoba fallback CPU 256 token...", primaryErr);
                cleanupPartialInference();
                System.gc();
                LlmInference.LlmInferenceOptions cpuFallbackOptions = buildOptions(modelFile, 256, 24, true);
                llmInference = LlmInference.createFromOptions(context, cpuFallbackOptions);
            }

            cfg.clearLlmSafeMode();
            isInitialized = true;
            safeMode = false;
            lastError = "";
            Log.i(TAG, "Mesin LLM Offline berhasil diinisialisasi.");
            return true;

        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "Memori penuh (OOM) saat memuat model LLM offline.", oom);
            cleanupPartialInference();
            safeMode = true;
            lastError = CRASH_SAFE_MODE_REASON;
            cfg.setLlmInitPending(false);
            cfg.setLlmSafeMode(true);
            cfg.setLlmSafeReason(CRASH_SAFE_MODE_REASON);
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "Gagal menginisialisasi MediaPipe LLM Inference.", t);
            cleanupPartialInference();
            safeMode = true;
            String errMsg = t.getMessage() != null && !t.getMessage().trim().isEmpty()
                    ? t.getMessage().trim()
                    : CRASH_SAFE_MODE_REASON;
            lastError = errMsg;
            cfg.setLlmInitPending(false);
            cfg.setLlmSafeMode(true);
            cfg.setLlmSafeReason(errMsg);
            return false;
        }
    }

    private LlmInference.LlmInferenceOptions buildOptions(
            File modelFile,
            int maxTokens,
            int topK,
            boolean forceCpuBackend
    ) {
        LlmInference.LlmInferenceOptions.Builder builder = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.getAbsolutePath())
                .setMaxTokens(maxTokens)
                .setTopK(topK)
                .setTemperature(0.3f)
                .setResultListener((partialResult, done) -> {
                    if (activeCallback != null) {
                        final StreamingCallback cb = activeCallback;
                        mainHandler.post(() -> cb.onPartialResult(partialResult, done));
                    }

                    if (partialResult != null && !partialResult.isEmpty()) {
                        sentenceBuffer.append(partialResult);
                        fullResponseBuffer.append(partialResult);

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
                        String fullReply = cleanTextForTts(fullResponseBuffer.toString());
                        fullResponseBuffer.setLength(0);
                        if (shouldRecordHistory && !pendingHistoryUserPrompt.isEmpty() && !fullReply.isEmpty()) {
                            addSessionTurn(pendingHistoryUserPrompt, fullReply);
                        }
                        pendingHistoryUserPrompt = "";
                        shouldRecordHistory = false;
                        Log.d(TAG, "Generasi respons LLM offline selesai.");
                    }
                })
                .setErrorListener(e -> {
                    Log.e(TAG, "Galat saat streaming inferensi LLM offline", e);
                    pendingHistoryUserPrompt = "";
                    shouldRecordHistory = false;
                    if (activeCallback != null) {
                        final StreamingCallback cb = activeCallback;
                        mainHandler.post(() -> cb.onError(e != null ? e.getMessage() : "Galat inferensi"));
                    }
                    if (activeSpeaker != null) {
                        final Speaker spk = activeSpeaker;
                        mainHandler.post(() -> spk.speak("Terjadi kendala saat memproses jawaban offline."));
                    }
                });

        if (forceCpuBackend) {
            trySetCpuBackend(builder);
        }
        return builder.build();
    }

    private void trySetCpuBackend(LlmInference.LlmInferenceOptions.Builder builder) {
        try {
            Class<?> backendClass = Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference$Backend");
            Object[] constants = backendClass.getEnumConstants();
            if (constants == null) return;
            Object cpuEnum = null;
            for (Object c : constants) {
                if ("CPU".equals(c.toString())) {
                    cpuEnum = c;
                    break;
                }
            }
            if (cpuEnum != null) {
                Method m = builder.getClass().getMethod("setPreferredBackend", backendClass);
                m.invoke(builder, cpuEnum);
                Log.i(TAG, "Mengaktifkan LlmInference.Backend.CPU untuk model .task.");
            }
        } catch (Throwable ignored) {
            // Aman bila MediaPipe versi terpasang belum mengekspos Backend enum
        }
    }

    private boolean isDeviceLowAvailableRam() {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return false;
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return mi.lowMemory || mi.availMem < LOW_AVAIL_RAM_THRESHOLD_BYTES;
        } catch (Throwable ignored) {
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

    /** Memuat ulang mesin bahasa offline (misal setelah model baru diimpor). */
    public synchronized boolean reloadEngine() {
        return forceInitEngine();
    }

    /** Menghapus berkas model lokal .task dari penyimpanan internal dan mereset Mode Aman. */
    public synchronized boolean deleteLocalModel() {
        close();
        clearSessionHistory();
        File modelFile = getModelFile();
        File tempFile = new File(context.getFilesDir(), MODEL_FILENAME + ".tmp");
        if (tempFile.exists()) {
            tempFile.delete();
        }
        boolean deleted = !modelFile.exists() || modelFile.delete();
        new KobraConfig(context).clearLlmSafeMode();
        this.safeMode = false;
        this.lastError = "";
        return deleted;
    }

    public void generateResponseAsync(String prompt, Speaker speaker) {
        generateResponseAsync(prompt, speaker, null, true);
    }

    public void generateResponseAsync(String prompt, Speaker speaker, StreamingCallback callback) {
        generateResponseAsync(prompt, speaker, callback, true);
    }

    /**
     * Mengirimkan instruksi ke model offline dengan dukungan memori sesi (5-8 giliran terakhir)
     * serta gaya bahasa asisten dari KobraConfig.
     */
    public synchronized void generateResponseAsync(String prompt, Speaker speaker, StreamingCallback callback, boolean useHistory) {
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
            this.fullResponseBuffer.setLength(0);
            this.shouldRecordHistory = useHistory;
            this.pendingHistoryUserPrompt = useHistory && prompt != null ? prompt.trim() : "";

            String formattedPrompt = buildContextualPrompt(prompt, useHistory);
            llmInference.generateResponseAsync(formattedPrompt);
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
     * Meringkas notifikasi menumpuk atau teks OCR/dokumen panjang menggunakan model lokal offline,
     * OnlineAiRouter 3-lapis bila tersambung internet, atau ringkasan ekstraktif lokal.
     */
    public void summarizeText(String title, String longText, AiClient onlineFallback, Speaker speaker, SummaryCallback cb) {
        if (longText == null || longText.trim().isEmpty()) {
            postSummary(cb, "Tidak ada teks atau notifikasi untuk diringkas.");
            return;
        }
        String trimmed = longText.trim();
        final String finalClipped = trimmed.length() > 1500 ? trimmed.substring(0, 1500) : trimmed;
        final String finalInstruction = "Ringkas " + title + " berikut menjadi 2 hingga 3 kalimat Bahasa Indonesia yang jelas, "
                + "sebutkan inti pesan, nama pengirim, atau angka pentingnya tanpa format markdown:\n\n" + finalClipped;

        if (isInitialized()) {
            runSummaryOnGemma(finalInstruction, title, finalClipped, speaker, cb);
            return;
        }

        if (isModelPresent() && !isSafeMode()) {
            final long generation = initGeneration.get();
            initExec.execute(() -> {
                if (generation != initGeneration.get()) return;
                boolean initialized = initEngine();
                if (generation != initGeneration.get()) return;
                if (initialized) {
                    mainHandler.post(() -> {
                        if (generation == initGeneration.get()) {
                            runSummaryOnGemma(finalInstruction, title, finalClipped, speaker, cb);
                        }
                    });
                } else if (generation == initGeneration.get()) {
                    fallbackSummarize(finalInstruction, title, finalClipped, onlineFallback, cb);
                }
            });
            return;
        }

        fallbackSummarize(finalInstruction, title, finalClipped, onlineFallback, cb);
    }

    public void summarizeTextAsync(String text, SummaryCallback cb) {
        summarizeTextAsync(null, "Ringkasan", text, null, cb);
    }

    public void summarizeTextAsync(String systemInstruction, String title, String longText, Speaker speaker, SummaryCallback cb) {
        if (longText == null || longText.trim().isEmpty()) {
            postSummary(cb, "Tidak ada teks untuk diproses.");
            return;
        }
        String trimmed = longText.trim();
        final String finalClipped = trimmed.length() > 1500 ? trimmed.substring(0, 1500) : trimmed;
        final boolean isConversational = "Chat".equalsIgnoreCase(title) || "Suara".equalsIgnoreCase(title);
        final String finalInstruction;
        if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
            if (isConversational) {
                finalInstruction = systemInstruction.trim() + "\n\nPermintaan pengguna: " + finalClipped;
            } else {
                finalInstruction = systemInstruction.trim() + "\n\nRingkas " + title + " berikut:\n\n" + finalClipped;
            }
        } else {
            finalInstruction = "Ringkas " + title + " berikut menjadi 2 hingga 3 kalimat Bahasa Indonesia yang jelas tanpa markdown:\n\n" + finalClipped;
        }

        final Speaker streamSpeaker = (cb == null) ? speaker : null;
        if (isInitialized()) {
            runSummaryOnGemma(finalInstruction, title, finalClipped, streamSpeaker, cb);
            return;
        }

        if (isModelPresent() && !isSafeMode()) {
            final long generation = initGeneration.get();
            initExec.execute(() -> {
                if (generation != initGeneration.get()) return;
                boolean initialized = initEngine();
                if (generation != initGeneration.get()) return;
                if (initialized) {
                    mainHandler.post(() -> {
                        if (generation == initGeneration.get()) {
                            runSummaryOnGemma(finalInstruction, title, finalClipped, streamSpeaker, cb);
                        }
                    });
                } else if (generation == initGeneration.get()) {
                    fallbackAsyncReply(title, finalClipped, streamSpeaker, cb);
                }
            });
            return;
        }

        fallbackAsyncReply(title, finalClipped, streamSpeaker, cb);
    }

    private void fallbackAsyncReply(String title, String rawText, Speaker streamSpeaker, SummaryCallback cb) {
        if (cb != null) {
            postSummary(cb, isSafeMode() ? "Mode Aman aktif." : "Gagal memproses di model lokal.");
            return;
        }
        String fallback = buildOfflineExtractiveSummary(title, rawText);
        if (streamSpeaker != null) {
            mainHandler.post(() -> streamSpeaker.speak(fallback));
        }
    }

    private void postSummary(SummaryCallback cb, String text) {
        if (cb == null) return;
        mainHandler.post(() -> cb.onSummary(text));
    }

    private void runSummaryOnGemma(String instruction, String title, String rawText, Speaker speaker, SummaryCallback cb) {
        StringBuilder out = new StringBuilder();
        generateResponseAsync(instruction, speaker, new StreamingCallback() {
            @Override public void onPartialResult(String partialText, boolean isComplete) {
                if (partialText != null) out.append(partialText);
                if (isComplete && cb != null) {
                    String clean = cleanTextForTts(out.toString());
                    cb.onSummary(clean.isEmpty() ? buildOfflineExtractiveSummary(title, rawText) : clean);
                }
            }
            @Override public void onError(String error) {
                if (cb != null) {
                    cb.onSummary(buildOfflineExtractiveSummary(title, rawText));
                }
            }
        }, false);
    }

    /**
     * Offline-only fallback. The legacy AiClient parameter is retained for source compatibility
     * but is deliberately ignored so prompts and private text never leave the device.
     */
    private void fallbackSummarize(String instruction, String title, String rawText, AiClient ignored, SummaryCallback cb) {
        postSummary(cb, buildOfflineExtractiveSummary(title, rawText));
    }

    public static String buildOfflineExtractiveSummary(String title, String rawText) {
        String clean = rawText == null ? "" : rawText.replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) return "Tidak ada isi untuk diringkas.";
        String[] lines = rawText.split("\\n+");
        if (lines.length >= 3) {
            StringBuilder sb = new StringBuilder("Ringkasan " + title + " (" + lines.length + " bagian). ");
            int limit = Math.min(3, lines.length);
            for (int i = 0; i < limit; i++) {
                String l = lines[i].trim();
                if (!l.isEmpty()) {
                    if (l.length() > 110) l = l.substring(0, 110) + "...";
                    sb.append(l).append(". ");
                }
            }
            return sb.toString().trim();
        }
        String[] sentences = clean.split("(?<=[.!?])\\s+");
        StringBuilder sb = new StringBuilder("Ringkasan " + title + ". ");
        int count = 0;
        for (String s : sentences) {
            String t = s.trim();
            if (t.isEmpty()) continue;
            sb.append(t).append(" ");
            count++;
            if (count >= 3 || sb.length() > 260) break;
        }
        return sb.toString().trim();
    }

    private synchronized String buildContextualPrompt(String userPrompt, boolean useHistory) {
        KobraConfig cfg = new KobraConfig(context);
        String activeName = VoiceProfileManager.getActiveAssistantName(context);
        String systemPrompt = "Kamu adalah " + activeName + ", asisten suara untuk penyandang tunanetra. "
                + cfg.getAssistantStyleSystemPrompt();

        List<ChatTurn> included = new ArrayList<>();
        if (useHistory && !sessionHistory.isEmpty()) {
            int budget = MAX_PROMPT_CHARS - systemPrompt.length() - (userPrompt == null ? 0 : userPrompt.length()) - 120;
            for (int i = sessionHistory.size() - 1; i >= 0; i--) {
                ChatTurn t = sessionHistory.get(i);
                int cost = t.user.length() + t.assistant.length() + 45;
                if (budget - cost < 0) break;
                included.add(0, t);
                budget -= cost;
            }
        }

        StringBuilder sb = new StringBuilder();
        if (included.isEmpty()) {
            sb.append("<start_of_turn>user\n")
              .append(systemPrompt).append("\n\n")
              .append(userPrompt == null ? "" : userPrompt.trim())
              .append("<end_of_turn>\n<start_of_turn>model\n");
            return sb.toString();
        }

        for (int i = 0; i < included.size(); i++) {
            ChatTurn t = included.get(i);
            sb.append("<start_of_turn>user\n");
            if (i == 0) {
                sb.append(systemPrompt).append("\n\n");
            }
            sb.append(t.user).append("<end_of_turn>\n");
            sb.append("<start_of_turn>model\n").append(t.assistant).append("<end_of_turn>\n");
        }
        sb.append("<start_of_turn>user\n")
          .append(userPrompt == null ? "" : userPrompt.trim())
          .append("<end_of_turn>\n<start_of_turn>model\n");
        return sb.toString();
    }

    private String cleanTextForTts(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]+>", " ")
                   .replaceAll("[*#_`~\\-]", "")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    /** Membebaskan memori sistem saat service dihentikan. */
    public synchronized void close() {
        // Prevent queued initialization work and its UI callback from reviving a released engine.
        initGeneration.incrementAndGet();
        cleanupPartialInference();
        activeSpeaker = null;
        activeCallback = null;
        Log.i(TAG, "Sumber daya LLM Offline berhasil dibebaskan.");
    }
}
