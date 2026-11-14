package com.asisten.bantu;

import android.app.ActivityManager;
import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central registry for local model formats and device-aware recommendations.
 * MediaPipe .task and LiteRT-LM .litertlm are stored separately and never overwrite
 * each other. Recommendations are conservative; actual compatibility is verified
 * only when the selected runtime initializes the model.
 */
public final class LocalModelRegistry {
    public enum RuntimeKind { MEDIAPIPE_TASK, LITERT_LM }
    public enum DeviceTier { LOW_RAM, STANDARD, HIGH_MEMORY }

    public static final class ModelEntry {
        public final String id;
        public final String displayName;
        public final String fileName;
        public final RuntimeKind runtime;
        public final long minimumFileBytes;
        public final String description;

        ModelEntry(String id, String displayName, String fileName, RuntimeKind runtime,
                   long minimumFileBytes, String description) {
            this.id = id;
            this.displayName = displayName;
            this.fileName = fileName;
            this.runtime = runtime;
            this.minimumFileBytes = minimumFileBytes;
            this.description = description;
        }
    }

    private static final long MIN_MODEL_BYTES = 1024L * 1024L;
    private static final ModelEntry GEMMA_TASK = new ModelEntry(
            "gemma3-1b-task", "Gemma 3 1B IT INT4",
            LocalLlmEngine.MODEL_FILENAME, RuntimeKind.MEDIAPIPE_TASK,
            MIN_MODEL_BYTES, "MediaPipe .task"
    );
    private static final ModelEntry QWEN_LITERT = new ModelEntry(
            "qwen3-0.6b-litert", "Qwen3 0.6B INT4",
            "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            RuntimeKind.LITERT_LM, MIN_MODEL_BYTES, "LiteRT-LM .litertlm"
    );

    private LocalModelRegistry() {}

    public static File directory(Context context) {
        File dir = new File(context.getFilesDir(), "local_models");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File fileFor(Context context, ModelEntry entry) {
        if (entry.runtime == RuntimeKind.MEDIAPIPE_TASK) {
            return new File(context.getFilesDir(), entry.fileName);
        }
        return new File(directory(context), entry.fileName);
    }

    public static List<ModelEntry> supportedModels() {
        List<ModelEntry> models = new ArrayList<>();
        models.add(GEMMA_TASK);
        models.add(QWEN_LITERT);
        return Collections.unmodifiableList(models);
    }

    public static ModelEntry findByFileName(String fileName) {
        if (fileName == null) return null;
        for (ModelEntry entry : supportedModels()) {
            if (entry.fileName.equalsIgnoreCase(fileName)) return entry;
        }
        return null;
    }

    public static ModelEntry findByExtension(String fileName) {
        if (fileName == null) return null;
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".task")) return GEMMA_TASK;
        if (lower.endsWith(".litertlm")) return QWEN_LITERT;
        return null;
    }

    public static boolean isValidFile(File file, ModelEntry entry) {
        return file != null && entry != null && file.isFile()
                && file.length() >= entry.minimumFileBytes;
    }

    public static DeviceTier getDeviceTier(Context context) {
        ActivityManager manager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return DeviceTier.LOW_RAM;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        long availableMb = info.availMem / (1024L * 1024L);
        long totalMb = info.totalMem / (1024L * 1024L);
        if (manager.isLowRamDevice() || availableMb < 1400L || totalMb < 4500L) {
            return DeviceTier.LOW_RAM;
        }
        if (availableMb >= 3000L && totalMb >= 7000L) {
            return DeviceTier.HIGH_MEMORY;
        }
        return DeviceTier.STANDARD;
    }

    public static String recommendation(Context context) {
        switch (getDeviceTier(context)) {
            case LOW_RAM:
                return "Pilih model paling kecil; tutup aplikasi lain sebelum memuat AI lokal.";
            case HIGH_MEMORY:
                return "Perangkat memiliki ruang memori lebih besar; model 1B dapat dicoba.";
            default:
                return "Gunakan model 0.6B terlebih dahulu dan batasi panjang konteks.";
        }
    }
}
