package com.asisten.bantu;

import java.util.List;

/** Pure, accuracy-aware distance and hysteresis gates for pedestrian-route progress. */
public final class PedestrianRouteProgress {
    private static final float ARRIVAL_RADIUS_METERS = 20f;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final float OFF_ROUTE_BASE_METERS = 45f;
    private static final float MAX_OFF_ROUTE_ACCURACY_METERS = 25f;
    private static final int REQUIRED_OFF_ROUTE_FIXES = 3;
    private static final long REROUTE_COOLDOWN_MS = 30_000L;

    private PedestrianRouteProgress() { }

    public static boolean isDestinationReached(float distanceMeters) {
        return !Float.isNaN(distanceMeters)
                && !Float.isInfinite(distanceMeters)
                && distanceMeters >= 0f
                && distanceMeters <= ARRIVAL_RADIUS_METERS;
    }

    /**
     * Returns the shortest horizontal distance from a GPS fix to a route polyline.
     * Coordinates are projected into a local equirectangular plane; this is intended
     * for short pedestrian routes, not geodesic surveying.
     */
    public static float distanceToPolylineMeters(
            double latitude, double longitude, double[] routeLatitudes, double[] routeLongitudes) {
        if (!validCoordinate(latitude, longitude) || routeLatitudes == null || routeLongitudes == null
                || routeLatitudes.length < 2 || routeLatitudes.length != routeLongitudes.length) {
            return Float.NaN;
        }
        double minimum = Double.POSITIVE_INFINITY;
        for (int i = 0; i < routeLatitudes.length - 1; i++) {
            double latA = routeLatitudes[i];
            double lonA = routeLongitudes[i];
            double latB = routeLatitudes[i + 1];
            double lonB = routeLongitudes[i + 1];
            if (!validCoordinate(latA, lonA) || !validCoordinate(latB, lonB)) return Float.NaN;

            double meanLatitude = Math.toRadians((latitude + latA + latB) / 3.0);
            double xScale = EARTH_RADIUS_METERS * Math.cos(meanLatitude);
            double ax = Math.toRadians(lonA - longitude) * xScale;
            double ay = Math.toRadians(latA - latitude) * EARTH_RADIUS_METERS;
            double bx = Math.toRadians(lonB - longitude) * xScale;
            double by = Math.toRadians(latB - latitude) * EARTH_RADIUS_METERS;
            double dx = bx - ax;
            double dy = by - ay;
            double lengthSquared = dx * dx + dy * dy;
            double t = lengthSquared == 0.0 ? 0.0 : -(ax * dx + ay * dy) / lengthSquared;
            t = Math.max(0.0, Math.min(1.0, t));
            double nearestX = ax + t * dx;
            double nearestY = ay + t * dy;
            minimum = Math.min(minimum, Math.hypot(nearestX, nearestY));
        }
        return Double.isFinite(minimum) ? (float) minimum : Float.NaN;
    }

    private static boolean validCoordinate(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0;
    }

    /** Requires three accurate, consecutive off-route fixes and enforces a cooldown. */
    public static final class OffRouteTracker {
        private int consecutiveOffRouteFixes;
        private long lastRerouteAtMs = -1L;

        public boolean shouldTriggerReroute(float distanceMeters, float accuracyMeters, long nowMs) {
            if (!Float.isFinite(distanceMeters) || distanceMeters < 0f
                    || !Float.isFinite(accuracyMeters) || accuracyMeters < 0f
                    || accuracyMeters > MAX_OFF_ROUTE_ACCURACY_METERS || nowMs < 0L) {
                consecutiveOffRouteFixes = 0;
                return false;
            }
            float threshold = Math.max(OFF_ROUTE_BASE_METERS, accuracyMeters * 1.5f);
            if (distanceMeters <= threshold) {
                consecutiveOffRouteFixes = 0;
                return false;
            }
            consecutiveOffRouteFixes = Math.min(
                    REQUIRED_OFF_ROUTE_FIXES, consecutiveOffRouteFixes + 1);
            if (consecutiveOffRouteFixes < REQUIRED_OFF_ROUTE_FIXES) return false;
            if (lastRerouteAtMs >= 0L && nowMs - lastRerouteAtMs < REROUTE_COOLDOWN_MS) {
                consecutiveOffRouteFixes = REQUIRED_OFF_ROUTE_FIXES - 1;
                return false;
            }
            lastRerouteAtMs = nowMs;
            consecutiveOffRouteFixes = 0;
            return true;
        }

        /** Clears the consecutive-fix window without bypassing the reroute cooldown. */
        public void reset() {
            consecutiveOffRouteFixes = 0;
        }
    }
}
