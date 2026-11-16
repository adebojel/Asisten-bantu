package com.asisten.bantu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PedestrianRouteProgressTest {
    @Test public void acceptsDistancesInsideArrivalRadius() {
        assertTrue(PedestrianRouteProgress.isDestinationReached(0f));
        assertTrue(PedestrianRouteProgress.isDestinationReached(12.5f));
        assertTrue(PedestrianRouteProgress.isDestinationReached(20f));
    }

    @Test public void rejectsDistancesOutsideOrInvalid() {
        assertFalse(PedestrianRouteProgress.isDestinationReached(20.01f));
        assertFalse(PedestrianRouteProgress.isDestinationReached(100f));
        assertFalse(PedestrianRouteProgress.isDestinationReached(-1f));
        assertFalse(PedestrianRouteProgress.isDestinationReached(Float.NaN));
        assertFalse(PedestrianRouteProgress.isDestinationReached(Float.POSITIVE_INFINITY));
    }

    @Test public void distanceToPolylineMeasuresNearestSegmentNotOnlyVertices() {
        float distance = PedestrianRouteProgress.distanceToPolylineMeters(
                0.0001, 0.0005,
                new double[]{0.0, 0.0}, new double[]{0.0, 0.001});
        assertEquals(11.12f, distance, 1.0f);
    }

    @Test public void rejectsMalformedRouteGeometry() {
        assertTrue(Float.isNaN(PedestrianRouteProgress.distanceToPolylineMeters(
                0, 0, new double[]{0}, new double[]{0})));
        assertTrue(Float.isNaN(PedestrianRouteProgress.distanceToPolylineMeters(
                91, 0, new double[]{0, 0}, new double[]{0, 1})));
        assertTrue(Float.isNaN(PedestrianRouteProgress.distanceToPolylineMeters(
                0, 0, new double[]{0, Double.NaN}, new double[]{0, 1})));
    }

    @Test public void offRouteNeedsThreeAccurateConsecutiveFixes() {
        PedestrianRouteProgress.OffRouteTracker tracker =
                new PedestrianRouteProgress.OffRouteTracker();
        assertFalse(tracker.shouldTriggerReroute(80f, 8f, 1_000L));
        assertFalse(tracker.shouldTriggerReroute(82f, 9f, 3_000L));
        assertTrue(tracker.shouldTriggerReroute(85f, 10f, 5_000L));
    }

    @Test public void poorAccuracyAndNearRouteFixesResetOffRouteSequence() {
        PedestrianRouteProgress.OffRouteTracker tracker =
                new PedestrianRouteProgress.OffRouteTracker();
        assertFalse(tracker.shouldTriggerReroute(80f, 8f, 1_000L));
        assertFalse(tracker.shouldTriggerReroute(80f, 40f, 2_000L));
        assertFalse(tracker.shouldTriggerReroute(80f, 8f, 3_000L));
        assertFalse(tracker.shouldTriggerReroute(20f, 8f, 4_000L));
        assertFalse(tracker.shouldTriggerReroute(80f, 8f, 5_000L));
    }

    @Test public void rerouteCooldownPreventsRepeatedAutomaticRecalculation() {
        PedestrianRouteProgress.OffRouteTracker tracker =
                new PedestrianRouteProgress.OffRouteTracker();
        assertFalse(tracker.shouldTriggerReroute(80f, 8f, 1_000L));
        assertFalse(tracker.shouldTriggerReroute(80f, 8f, 2_000L));
        assertTrue(tracker.shouldTriggerReroute(80f, 8f, 3_000L));
        assertFalse(tracker.shouldTriggerReroute(80f, 8f, 10_000L));
        assertFalse(tracker.shouldTriggerReroute(80f, 8f, 11_000L));
        assertFalse(tracker.shouldTriggerReroute(80f, 8f, 12_000L));
        assertFalse(tracker.shouldTriggerReroute(80f, 8f, 33_000L));
        assertFalse(tracker.shouldTriggerReroute(80f, 8f, 34_000L));
        assertTrue(tracker.shouldTriggerReroute(80f, 8f, 35_000L));
    }
}
