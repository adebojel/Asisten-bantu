package com.asisten.bantu;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.Detection;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SafeObjectDetector implements AutoCloseable {
    public interface Callback { void onDetected(List<DetectedItem> items, boolean usedFallback); }

    public static final class DetectedItem {
        public final String label;
        public final Rect boundingBox;
        public final float confidence;
        public final int imageWidth;
        public final int imageHeight;

        public DetectedItem(String label, Rect box, float confidence) {
            this(label, box, confidence, 0, 0);
        }

        public DetectedItem(String label, Rect box, float confidence, int imageWidth, int imageHeight) {
            this.label = label;
            this.boundingBox = box == null ? null : new Rect(box);
            this.confidence = confidence;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
        }

        public String positionLabel() {
            if (boundingBox == null || imageWidth <= 0) return null;
            return classifyPosition(boundingBox.centerX() / (float) imageWidth);
        }
    }

    private final Object lifecycleLock = new Object();
    private final Object inferenceLock = new Object();
    private final boolean streamMode;
    private volatile boolean closed;
    private ObjectDetector objectDetector;
    private ImageLabeler imageLabeler;
    private long lastTimestampMs;

    /** Compatibility constructor: label-only fallback when no Android Context is available. */
    public SafeObjectDetector(boolean streamMode) { this(null, streamMode); }

    public SafeObjectDetector(Context context, boolean streamMode) {
        this.streamMode = streamMode;
        if (context != null) {
            try {
                BaseOptions base = BaseOptions.builder().setModelAssetPath("efficientdet_lite0.tflite").build();
                ObjectDetector.ObjectDetectorOptions options = ObjectDetector.ObjectDetectorOptions.builder()
                        .setBaseOptions(base)
                        .setRunningMode(streamMode ? RunningMode.VIDEO : RunningMode.IMAGE)
                        .setScoreThreshold(0.45f)
                        .setMaxResults(8)
                        .build();
                objectDetector = ObjectDetector.createFromOptions(context.getApplicationContext(), options);
            } catch (Throwable ignored) {
                objectDetector = null;
            }
        }
    }

    public void process(Bitmap bitmap, Callback callback) { process(bitmap, 0, callback); }

    public void process(Bitmap bitmap, int rotationDegrees, Callback callback) {
        if (callback == null) return;
        if (bitmap == null || closed) {
            callback.onDetected(Collections.<DetectedItem>emptyList(), true);
            return;
        }
        Bitmap oriented = rotate(bitmap, rotationDegrees);
        // rotate() allocates a new bitmap for non-zero rotations. The camera/gallery
        // caller owns the source bitmap, so this detector must release only its copy.
        Bitmap ownedRotatedBitmap = oriented != bitmap ? oriented : null;
        List<DetectedItem> boxes = Collections.emptyList();
        synchronized (inferenceLock) {
            if (closed) {
                recycleOwnedBitmap(ownedRotatedBitmap);
                callback.onDetected(Collections.<DetectedItem>emptyList(), true);
                return;
            }
            ObjectDetector detector = objectDetector;
            if (detector != null) {
                try {
                    MPImage image = new BitmapImageBuilder(oriented).build();
                    ObjectDetectorResult result = streamMode
                            ? detector.detectForVideo(image, nextTimestamp())
                            : detector.detect(image);
                    boxes = fromDetections(result.detections(), oriented.getWidth(), oriented.getHeight());
                } catch (Throwable ignored) {
                    // Keep the label-only fallback if native inference fails.
                }
            }
        }
        if (!boxes.isEmpty()) {
            recycleOwnedBitmap(ownedRotatedBitmap);
            callback.onDetected(boxes, false);
            return;
        }
        try {
            // ML Kit consumes the bitmap asynchronously. Transfer ownership until its
            // completion callback; recycling immediately here can invalidate inference.
            processFallback(InputImage.fromBitmap(oriented, 0), callback, ownedRotatedBitmap);
            ownedRotatedBitmap = null;
        } finally {
            recycleOwnedBitmap(ownedRotatedBitmap);
        }
    }

    /** Existing callers with ML Kit InputImage remain supported as label-only fallback. */
    public void process(InputImage image, Callback callback) {
        if (callback == null) return;
        if (image == null || closed) {
            callback.onDetected(Collections.<DetectedItem>emptyList(), true);
            return;
        }
        processFallback(image, callback);
    }

    static String classifyPosition(float normalizedCenterX) {
        if (Float.isNaN(normalizedCenterX) || Float.isInfinite(normalizedCenterX)
                || normalizedCenterX < 0f || normalizedCenterX > 1f) return null;
        if (normalizedCenterX < 0.38f) return "kiri";
        if (normalizedCenterX > 0.62f) return "kanan";
        return "tengah";
    }

    private long nextTimestamp() {
        synchronized (lifecycleLock) {
            lastTimestampMs = Math.max(SystemClock.uptimeMillis(), lastTimestampMs + 1L);
            return lastTimestampMs;
        }
    }

    private static Bitmap rotate(Bitmap source, int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        if (normalized == 0) return source;
        Matrix matrix = new Matrix();
        matrix.postRotate(normalized);
        try {
            return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        } catch (RuntimeException ignored) {
            return source;
        }
    }

    private static List<DetectedItem> fromDetections(List<Detection> detections, int width, int height) {
        List<DetectedItem> result = new ArrayList<>();
        if (detections == null) return result;
        for (Detection detection : detections) {
            if (detection == null || detection.boundingBox() == null
                    || detection.categories() == null || detection.categories().isEmpty()) continue;
            Category category = detection.categories().get(0);
            String label = category.displayName();
            if (label == null || label.trim().isEmpty()) label = category.categoryName();
            if (label == null || label.trim().isEmpty()) continue;
            RectF box = detection.boundingBox();
            int left = Math.max(0, Math.min(width, (int) Math.floor(box.left)));
            int top = Math.max(0, Math.min(height, (int) Math.floor(box.top)));
            int right = Math.max(left, Math.min(width, (int) Math.ceil(box.right)));
            int bottom = Math.max(top, Math.min(height, (int) Math.ceil(box.bottom)));
            if (right <= left || bottom <= top) continue;
            result.add(new DetectedItem(translateLabel(label), new Rect(left, top, right, bottom),
                    category.score(), width, height));
        }
        result.sort(new Comparator<DetectedItem>() {
            @Override public int compare(DetectedItem a, DetectedItem b) {
                return Float.compare(b.confidence, a.confidence);
            }
        });
        return result;
    }

    private ImageLabeler getImageLabeler() {
        synchronized (lifecycleLock) {
            if (closed) return null;
            if (imageLabeler == null) {
                imageLabeler = ImageLabeling.getClient(
                        new ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build());
            }
            return imageLabeler;
        }
    }

    private void processFallback(InputImage image, Callback callback) {
        processFallback(image, callback, null);
    }

    private void processFallback(InputImage image, Callback callback, Bitmap ownedBitmap) {
        ImageLabeler labeler = getImageLabeler();
        if (labeler == null) {
            try {
                callback.onDetected(Collections.<DetectedItem>emptyList(), true);
            } finally {
                recycleOwnedBitmap(ownedBitmap);
            }
            return;
        }
        try {
            labeler.process(image).addOnCompleteListener(task -> {
                List<DetectedItem> items = new ArrayList<>();
                if (task.isSuccessful() && task.getResult() != null) {
                    for (ImageLabel label : task.getResult()) {
                        items.add(new DetectedItem(
                                translateLabel(label.getText()), null, label.getConfidence()));
                    }
                    items.sort((a, b) -> Float.compare(b.confidence, a.confidence));
                }
                try {
                    callback.onDetected(items, true);
                } finally {
                    recycleOwnedBitmap(ownedBitmap);
                }
            });
        } catch (Throwable ignored) {
            try {
                callback.onDetected(Collections.<DetectedItem>emptyList(), true);
            } finally {
                recycleOwnedBitmap(ownedBitmap);
            }
        }
    }

    private static void recycleOwnedBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return;
        try {
            bitmap.recycle();
        } catch (RuntimeException ignored) {
            // Recycling is best-effort; never crash the assistive camera flow.
        }
    }

    private static String translateLabel(String label) {
        String key = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        switch (key) {
            case "person": return "orang";
            case "bicycle": return "sepeda";
            case "car": return "mobil";
            case "motorcycle": return "sepeda motor";
            case "airplane": return "pesawat";
            case "bus": return "bus";
            case "train": return "kereta";
            case "truck": return "truk";
            case "boat": return "perahu";
            case "traffic light": return "lampu lalu lintas";
            case "stop sign": return "rambu berhenti";
            case "bench": return "bangku";
            case "bird": return "burung";
            case "cat": return "kucing";
            case "dog": return "anjing";
            case "horse": return "kuda";
            case "sheep": return "domba";
            case "cow": return "sapi";
            case "elephant": return "gajah";
            case "bear": return "beruang";
            case "zebra": return "zebra";
            case "giraffe": return "jerapah";
            case "backpack": return "tas ransel";
            case "umbrella": return "payung";
            case "handbag": return "tas tangan";
            case "suitcase": return "koper";
            case "sports ball": return "bola";
            case "kite": return "layang-layang";
            case "bottle": return "botol";
            case "wine glass": return "gelas";
            case "cup": return "cangkir";
            case "fork": return "garpu";
            case "knife": return "pisau";
            case "spoon": return "sendok";
            case "bowl": return "mangkuk";
            case "banana": return "pisang";
            case "apple": return "apel";
            case "sandwich": return "roti lapis";
            case "orange": return "jeruk";
            case "broccoli": return "brokoli";
            case "carrot": return "wortel";
            case "pizza": return "pizza";
            case "donut": return "donat";
            case "cake": return "kue";
            case "chair": return "kursi";
            case "couch": return "sofa";
            case "potted plant": return "tanaman pot";
            case "bed": return "tempat tidur";
            case "dining table": return "meja makan";
            case "toilet": return "toilet";
            case "tv": return "televisi";
            case "laptop": return "laptop";
            case "mouse": return "mouse";
            case "remote": return "remote";
            case "keyboard": return "keyboard";
            case "cell phone": return "ponsel";
            case "microwave": return "microwave";
            case "oven": return "oven";
            case "sink": return "wastafel";
            case "refrigerator": return "kulkas";
            case "book": return "buku";
            case "clock": return "jam";
            case "vase": return "vas";
            case "scissors": return "gunting";
            case "teddy bear": return "boneka beruang";
            case "hair drier": return "pengering rambut";
            case "toothbrush": return "sikat gigi";
            default: return label;
        }
    }

    @Override public void close() {
        synchronized (inferenceLock) {
            synchronized (lifecycleLock) {
                if (closed) return;
                closed = true;
                if (objectDetector != null) {
                    try { objectDetector.close(); } catch (Exception ignored) { }
                    objectDetector = null;
                }
                if (imageLabeler != null) {
                    try { imageLabeler.close(); } catch (Exception ignored) { }
                    imageLabeler = null;
                }
            }
        }
    }
}
