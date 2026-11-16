package com.asisten.bantu;

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
}
