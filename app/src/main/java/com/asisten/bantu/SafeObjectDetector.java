package com.asisten.bantu;

import android.graphics.Bitmap;
import android.graphics.Rect;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Detector fallback yang sengaja hanya memakai ML Kit Image Labeling.
 * Object Detection di perangkat uji melempar NoSuchMethodError pada runtime.
 * Labeling tidak menyediakan bounding box; jangan menganggapnya sebagai sensor jarak/arah.
 */
public final class SafeObjectDetector {
    public static final class DetectedItem {
        @NonNull public final String label;
        @Nullable public final Rect boundingBox;
        public final float confidence;
        public DetectedItem(@NonNull String label, @Nullable Rect box, float confidence) {
            this.label = label;
            this.boundingBox = box;
            this.confidence = confidence;
        }
    }

    public interface Callback {
        void onDetected(@NonNull List<DetectedItem> items, boolean usedFallback);
    }

    private ImageLabeler imageLabeler;
    private boolean closed = false;

    public SafeObjectDetector(boolean streamMode) { }

    public void process(@Nullable Bitmap bitmap, @NonNull Callback callback) {
        if (bitmap == null) {
            callback.onDetected(Collections.emptyList(), true);
            return;
        }
        try {
            process(InputImage.fromBitmap(bitmap, 0), callback);
        } catch (RuntimeException error) {
            callback.onDetected(Collections.emptyList(), true);
        }
    }

    public void process(@Nullable InputImage image, @NonNull Callback callback) {
        if (closed || image == null) {
            callback.onDetected(Collections.emptyList(), true);
            return;
        }
        try {
            if (imageLabeler == null) {
                imageLabeler = ImageLabeling.getClient(
                        new ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build());
            }
            imageLabeler.process(image)
                    .addOnSuccessListener(labels -> callback.onDetected(fromLabels(labels), true))
                    .addOnFailureListener(error -> callback.onDetected(Collections.emptyList(), true));
        } catch (Throwable error) {
            callback.onDetected(Collections.emptyList(), true);
        }
    }

    private static List<DetectedItem> fromLabels(List<ImageLabel> labels) {
        List<DetectedItem> result = new ArrayList<>();
        if (labels == null) return result;
        for (int i = 0; i < Math.min(5, labels.size()); i++) {
            ImageLabel item = labels.get(i);
            if (item != null) {
                result.add(new DetectedItem(translate(item.getText()), null, item.getConfidence()));
            }
        }
        return result;
    }

    private static String translate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "sebuah benda";
        switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "chair": return "kursi";
            case "table": case "desk": return "meja";
            case "door": return "pintu";
            case "bottle": return "botol";
            case "cup": case "mug": return "gelas";
            case "phone": return "ponsel";
            case "laptop": case "computer": return "komputer";
            case "book": return "buku";
            case "person": case "human": return "orang";
            case "car": case "vehicle": return "kendaraan";
            case "plant": case "tree": case "flower": return "tanaman";
            case "stairs": return "tangga";
            default: return raw.trim().toLowerCase(Locale.ROOT);
        }
    }

    public void close() {
        closed = true;
        if (imageLabeler != null) {
            try { imageLabeler.close(); } catch (Exception ignored) { }
            imageLabeler = null;
        }
    }
}
