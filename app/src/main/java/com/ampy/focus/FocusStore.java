package com.ampy.focus;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Preferences intentionally remain on-device: no accounts or telemetry. */
public final class FocusStore {
    private FocusStore() {}

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("ampyfocus", Context.MODE_PRIVATE);
    }

    public static String goal(Context c) {
        return prefs(c).getString("goal", "Concentrati su ciò che conta");
    }

    public static Set<String> blocked(Context c) {
        return new HashSet<>(prefs(c).getStringSet("blocked_packages", Collections.emptySet()));
    }

    public static void setBlocked(Context c, Set<String> packages) {
        prefs(c).edit().putStringSet("blocked_packages", new HashSet<>(packages)).apply();
    }

    public static boolean scheduleEnabled(Context c) {
        return prefs(c).getBoolean("schedule_enabled", false);
    }

    public static int startMinute(Context c) {
        return prefs(c).getInt("start_minute", 9 * 60);
    }

    public static int endMinute(Context c) {
        return prefs(c).getInt("end_minute", 12 * 60);
    }

    public static boolean geoEnabled(Context c) {
        return prefs(c).getBoolean("geo_enabled", false)
                && prefs(c).getBoolean("geo_center_configured", false);
    }

    /** 0=off, 1=inside, 2=outside; completely local. */
    public static int geoMode(Context c) {
        return prefs(c).getInt("geo_mode", 1);
    }

    public static int geoRadius(Context c) {
        return Math.max(50, Math.min(50000, prefs(c).getInt("geo_radius_m", 1000)));
    }

    public static double geoLatitude(Context c) {
        return Double.longBitsToDouble(prefs(c).getLong("geo_lat", 0L));
    }

    public static double geoLongitude(Context c) {
        return Double.longBitsToDouble(prefs(c).getLong("geo_lon", 0L));
    }

    public static void setGeoCenter(Context c, double latitude, double longitude) {
        if (!GeoFenceMath.validCoordinate(latitude, longitude))
            throw new IllegalArgumentException("Invalid coordinates");
        prefs(c).edit().putLong("geo_lat", Double.doubleToRawLongBits(latitude))
                .putLong("geo_lon", Double.doubleToRawLongBits(longitude))
                .putBoolean("geo_center_configured", true).putString("geo_state", "UNKNOWN")
                .putLong("geo_checked_wall", 0L).apply();
    }

    public static void clearGeo(Context c) {
        prefs(c).edit().putBoolean("geo_enabled", false)
                .remove("geo_lat").remove("geo_lon").remove("geo_checked_wall")
                .remove("geo_center_configured").remove("geo_state").apply();
    }

    public static void updateSchedule(Context c, int start, int end, boolean enabled) {
        prefs(c).edit()
            .putInt("start_minute", start)
            .putInt("end_minute", end)
            .putBoolean("schedule_enabled", enabled)
            .apply();
    }
}
