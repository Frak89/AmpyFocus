package com.ampy.focus;

/** Small, testable mathematical core. No cell-tower distances are inferred here. */
public final class GeoFenceMath {
    private static final double EARTH_RADIUS_METERS = 6371008.8d;
    private GeoFenceMath() {}

    public enum Position { INSIDE, OUTSIDE, UNKNOWN }

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2d), 2d)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(dLon / 2d), 2d);
        return 2d * EARTH_RADIUS_METERS * Math.asin(Math.min(1d, Math.sqrt(Math.max(0d, a))));
    }

    /** Only mark a boundary crossed when GPS/network uncertainty does not overlap it. */
    public static Position classify(double distanceMeters, double accuracyMeters, double radiusMeters) {
        if (!Double.isFinite(distanceMeters) || !Double.isFinite(accuracyMeters)
                || !Double.isFinite(radiusMeters) || accuracyMeters < 0 || radiusMeters <= 0) {
            return Position.UNKNOWN;
        }
        if (distanceMeters + accuracyMeters <= radiusMeters) return Position.INSIDE;
        if (distanceMeters - accuracyMeters > radiusMeters) return Position.OUTSIDE;
        return Position.UNKNOWN;
    }

    public static boolean validCoordinate(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon) && lat >= -90 && lat <= 90
                && lon >= -180 && lon <= 180;
    }
}
