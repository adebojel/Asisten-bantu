package com.asisten.bantu;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Deteksi benda offline dengan fallback Image Labeling jika Object Detection gagal. */
public final class SafeObjectDetector {
    private static final String TAG = "KobraSafeDetector";
    public static final class DetectedItem {
        @NonNull public final String label;
        @Nullable public final Rect boundingBox;
        public final float confidence;
        public DetectedItem(@NonNull String label, @Nullable Rect box, float confidence) {
            this.label = label; this.boundingBox = box; this.confidence = confidence;
        }
    }
    public interface Callback { void onDetected(@NonNull List<DetectedItem> items, boolean usedFallback); }

    private final boolean streamMode;
    private ObjectDetector objectDetector;
    private ImageLabeler imageLabeler;
    private volatile boolean fallback;

    public SafeObjectDetector(boolean streamMode) {
        this.streamMode = streamMode;
        try {
            ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                    .setDetectorMode(streamMode ? ObjectDetectorOptions.STREAM_MODE : ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                    .enableMultipleObjects().enableClassification().build();
            objectDetector = ObjectDetection.getClient(options);
        } catch (Throwable error) {
            fallback = true;
            Log.w(TAG, "Object Detection tidak tersedia; gunakan Image Labeling", error);
        }
    }

    public void process(@Nullable Bitmap bitmap, @NonNull Callback callback) {
        if (bitmap == null) { callback.onDetected(Collections.emptyList(), fallback); return; }
        try { process(InputImage.fromBitmap(bitmap, 0), callback); }
        catch (Throwable error) {
            Log.w(TAG, "InputImage gagal dibuat", error);
            callback.onDetected(Collections.emptyList(), true);
        }
    }

    public void process(@Nullable InputImage image, @NonNull Callback callback) {
        if (image == null) { callback.onDetected(Collections.emptyList(), fallback); return; }
        if (!fallback && objectDetector != null) {
            try {
                objectDetector.process(image)
                        .addOnSuccessListener(items -> callback.onDetected(fromObjects(items), false))
                        .addOnFailureListener(error -> {
                            Log.w(TAG, "Object Detection gagal; beralih ke Image Labeling", error);
                            fallback = true;
                            closeObjectDetector();
                            runLabeler(image, callback);
                        });
                return;
            } catch (Throwable error) {
                Log.w(TAG, "Object Detection melempar error; beralih ke Image Labeling", error);
                fallback = true;
                closeObjectDetector();
            }
        }
        runLabeler(image, callback);
    }

    private void runLabeler(InputImage image, Callback callback) {
        try {
            if (imageLabeler == null) imageLabeler = ImageLabeling.getClient(
                    new ImageLabelerOptions.Builder().setConfidenceThreshold(0.50f).build());
            imageLabeler.process(image)
                    .addOnSuccessListener(labels -> callback.onDetected(fromLabels(labels), true))
                    .addOnFailureListener(error -> {
                        Log.w(TAG, "Image Labeling fallback gagal", error);
                        callback.onDetected(Collections.emptyList(), true);
                    });
        } catch (Throwable error) {
            Log.w(TAG, "Image Labeling tidak tersedia", error);
            callback.onDetected(Collections.emptyList(), true);
        }
    }

    private static List<DetectedItem> fromObjects(List<DetectedObject> objects) {
        List<DetectedItem> result = new ArrayList<>();
        if (objects == null) return result;
        for (DetectedObject object : objects) {
            if (object == null) continue;
            String label = "benda"; float confidence = 0.55f;
            if (!object.getLabels().isEmpty()) {
                DetectedObject.Label first = object.getLabels().get(0);
                label = translate(first.getText()); confidence = first.getConfidence();
            }
            result.add(new DetectedItem(label, object.getBoundingBox(), confidence));
        }
        return result;
    }

    private static List<DetectedItem> fromLabels(List<ImageLabel> labels) {
        List<DetectedItem> result = new ArrayList<>();
        if (labels == null) return result;
        for (int i = 0; i < Math.min(5, labels.size()); i++) {
            ImageLabel item = labels.get(i);
            if (item != null) result.add(new DetectedItem(translate(item.getText()), null, item.getConfidence()));
        }
        return result;
    }

    private static String translate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "sebuah benda";
        switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "chair": return "kursi"; case "table": case "desk": return "meja";
            case "door": return "pintu"; case "bottle": return "botol";
            case "cup": case "mug": return "gelas"; case "phone": return "ponsel";
            case "laptop": case "computer": return "komputer"; case "book": return "buku";
            case "person": case "human": return "orang"; case "car": case "vehicle": return "kendaraan";
            case "plant": case "tree": case "flower": return "tanaman"; case "stairs": return "tangga";
            default: return raw.trim().toLowerCase(Locale.ROOT);
        }
    }

    private void closeObjectDetector() {
        if (objectDetector != null) { try { objectDetector.close(); } catch (Throwable ignored) {} objectDetector = null; }
    }

    public void close() {
        closeObjectDetector();
        if (imageLabeler != null) { try { imageLabeler.close(); } catch (Throwable ignored) {} imageLabeler = null; }
    }
}
