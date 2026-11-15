package com.asisten.bantu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ObjectDetectionStabilityTest {

    private SafeObjectDetector.DetectedItem item(String label, float confidence) {
        return new SafeObjectDetector.DetectedItem(label, null, confidence);
    }

    @Test
    public void waitsForThreeConsecutiveFrames() {
        ObjectDetectionStability stability = new ObjectDetectionStability();
        List<SafeObjectDetector.DetectedItem> frame = Collections.singletonList(item("botol", 0.9f));

        assertTrue(stability.filter(frame).isEmpty());
        assertTrue(stability.filter(frame).isEmpty());
        assertEquals(1, stability.filter(frame).size());
    }

    @Test
    public void missingFrameResetsTheConfirmationWindow() {
        ObjectDetectionStability stability = new ObjectDetectionStability();
        List<SafeObjectDetector.DetectedItem> frame = Collections.singletonList(item("kursi", 0.9f));

        stability.filter(frame);
        stability.filter(frame);
        assertTrue(stability.filter(Collections.<SafeObjectDetector.DetectedItem>emptyList()).isEmpty());
        assertTrue(stability.filter(frame).isEmpty());
        assertTrue(stability.filter(frame).isEmpty());
        assertEquals(1, stability.filter(frame).size());
    }

    @Test
    public void animalLabelsRequireHigherConfidence() {
        ObjectDetectionStability stability = new ObjectDetectionStability();
        List<SafeObjectDetector.DetectedItem> low = Collections.singletonList(item("cat", 0.58f));
        assertTrue(stability.filter(low).isEmpty());
        assertTrue(stability.filter(low).isEmpty());
        assertTrue(stability.filter(low).isEmpty());

        List<SafeObjectDetector.DetectedItem> high = Collections.singletonList(item("cat", 0.72f));
        assertTrue(stability.filter(high).isEmpty());
        assertTrue(stability.filter(high).isEmpty());
        assertEquals(1, stability.filter(high).size());
    }

    @Test
    public void duplicateLabelsInOneFrameCountAsOneSighting() {
        ObjectDetectionStability stability = new ObjectDetectionStability();
        List<SafeObjectDetector.DetectedItem> duplicateFrame = Arrays.asList(
                item("botol", 0.61f), item("botol", 0.91f));
        assertTrue(stability.filter(duplicateFrame).isEmpty());
        assertTrue(stability.filter(duplicateFrame).isEmpty());
        List<SafeObjectDetector.DetectedItem> confirmed = stability.filter(duplicateFrame);
        assertEquals(1, confirmed.size());
        assertEquals(0.91f, confirmed.get(0).confidence, 0.0001f);
    }
}
