package com.asisten.bantu;

/** Pure distance gate for pedestrian-route arrival announcements. */
public final class PedestrianRouteProgress {
    private static final float ARRIVAL_RADIUS_METERS = 20f;

    private PedestrianRouteProgress() { }

    public static boolean isDestinationReached(float distanceMeters) {
        return !Float.isNaN(distanceMeters)
                && !Float.isInfinite(distanceMeters)
                && distanceMeters >= 0f
                && distanceMeters <= ARRIVAL_RADIUS_METERS;
    }
}
