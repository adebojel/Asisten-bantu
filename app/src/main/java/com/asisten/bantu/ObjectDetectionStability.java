package com.asisten.bantu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Temporal confirmation for live object labels.
 *
 * Image-label classifiers can flicker between adjacent frames. A label is
 * announced only after it survives several consecutive frames above its
 * confidence floor. This reduces transient false announcements; it does not
 * turn image labels into bounding boxes, distance, or obstacle measurements.
 *
 * Adapted from the MIT-licensed SightBuddy detection-confirmation approach:
 * https://github.com/DrophouseLtd/SightBuddy
 */
public final class ObjectDetectionStability {
    private static final int REQUIRED_CONSECUTIVE_FRAMES = 3;
    private static final float DEFAULT_MIN_CONFIDENCE = 0.55f;
    private static final float ANIMAL_MIN_CONFIDENCE = 0.60f;

    private static final Set<String> OFTEN_MISCLASSIFIED = new HashSet<String>();
    static {
        String[] labels = {
            "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe"
        };
        for (String label : labels) OFTEN_MISCLASSIFIED.add(label);
    }

    private final Map<String, Integer> consecutiveFrames = new HashMap<String, Integer>();

    /**
     * Returns only labels confirmed in this frame. An empty/missing frame resets
     * the streak, so an intermittent detection cannot accumulate confidence.
     */
    public synchronized List<SafeObjectDetector.DetectedItem> filter(
            List<SafeObjectDetector.DetectedItem> detections) {
        Map<String, SafeObjectDetector.DetectedItem> current =
                new HashMap<String, SafeObjectDetector.DetectedItem>();

        if (detections != null) {
            for (SafeObjectDetector.DetectedItem item : detections) {
                if (item == null || item.label == null) continue;
                String key = item.label.trim().toLowerCase(Locale.ROOT);
                if (key.isEmpty() || item.confidence < minimumConfidence(key)) continue;
                SafeObjectDetector.DetectedItem previous = current.get(key);
                if (previous == null || item.confidence > previous.confidence) {
                    current.put(key, item);
                }
            }
        }

        // Consecutive means consecutive camera frames, not total sightings.
        consecutiveFrames.keySet().retainAll(current.keySet());
        for (String label : current.keySet()) {
            Integer old = consecutiveFrames.get(label);
            consecutiveFrames.put(label, old == null ? 1 : old + 1);
        }

        List<SafeObjectDetector.DetectedItem> confirmed =
                new ArrayList<SafeObjectDetector.DetectedItem>();
        for (Map.Entry<String, SafeObjectDetector.DetectedItem> entry : current.entrySet()) {
            if (consecutiveFrames.get(entry.getKey()) >= REQUIRED_CONSECUTIVE_FRAMES) {
                confirmed.add(entry.getValue());
            }
        }
        confirmed.sort(new Comparator<SafeObjectDetector.DetectedItem>() {
            @Override public int compare(
                    SafeObjectDetector.DetectedItem a,
                    SafeObjectDetector.DetectedItem b) {
                return Float.compare(b.confidence, a.confidence);
            }
        });
        return confirmed;
    }

    public synchronized void reset() {
        consecutiveFrames.clear();
    }

    private static float minimumConfidence(String label) {
        return OFTEN_MISCLASSIFIED.contains(label)
                ? ANIMAL_MIN_CONFIDENCE : DEFAULT_MIN_CONFIDENCE;
    }
}
