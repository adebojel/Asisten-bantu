package com.asisten.bantu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import java.util.Arrays;
import org.junit.Test;

public class BRouterOfflineEngineTest {
    @Test public void calculatesBrouterSegmentNamesFromSouthWestTileCorner() {
        assertEquals("E5_N45.rd5", BRouterOfflineEngine.segmentFileName(47.2, 7.5));
        assertEquals("W50_N35.rd5", BRouterOfflineEngine.segmentFileName(37.2, -48.5));
        assertEquals("E105_S10.rd5", BRouterOfflineEngine.segmentFileName(-6.3, 106.7));
    }

    @Test public void listsAllSegmentsAcrossFiveDegreeBoundaries() {
        assertEquals(Arrays.asList("E100_S10.rd5", "E105_S10.rd5"),
                BRouterOfflineEngine.segmentNamesForBounds(-7.2, 104.8, -6.0, 106.9));
    }

    @Test public void allowsACompactFourSegmentArea() {
        assertEquals(4, BRouterOfflineEngine.segmentNamesForBounds(-6.0, 100.0, -1.0, 106.0).size());
    }

    @Test public void rejectsInvalidOrOversizedBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> BRouterOfflineEngine.segmentNamesForBounds(10, 10, -10, 20));
        assertThrows(IllegalArgumentException.class,
                () -> BRouterOfflineEngine.segmentNamesForBounds(-20, -20, 20, 20));
    }
}
