package com.ampy.focus;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;
import java.util.concurrent.TimeUnit;

/** GPS plus NETWORK_PROVIDER (cell/Wi-Fi estimated location), processed fully on-device. */
public final class GeoFenceController {
    private static final int GEO_ID = 590;
    private static final long MAX_AGE_MS = TimeUnit.MINUTES.toMillis(10);
    private static final String[] PROVIDERS = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER};
    private GeoFenceController() {}

    public static boolean hasForegroundPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasBackgroundPermission(Context c) {
        return Build.VERSION.SDK_INT < 29
                || c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, GeoFenceReceiver.class).setAction("com.ampy.focus.GEO_FIX");
        return PendingIntent.getBroadcast(c, GEO_ID, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    public static void register(Context c) {
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;
        PendingIntent pending = pending(c);
        try { lm.removeUpdates(pending); } catch (RuntimeException ignored) { }
        if (!FocusStore.geoEnabled(c) || !hasForegroundPermission(c) || !hasBackgroundPermission(c)) return;
        long minDistance = Math.max(30, Math.min(1000, FocusStore.geoRadius(c) / 3));
        for (String provider : PROVIDERS) {
            try {
                if (lm.isProviderEnabled(provider)) {
                    lm.requestLocationUpdates(provider, 60_000L, minDistance, pending);
                }
            } catch (SecurityException | IllegalArgumentException ignored) {
                // A permission or provider can disappear at any time.
            }
        }
        refreshLastKnown(c);
    }

    public static void refreshLastKnown(Context c) {
        if (!hasForegroundPermission(c) || !FocusStore.geoEnabled(c)) return;
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;
        for (String provider : PROVIDERS) {
            try {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null) accept(c, l);
            } catch (SecurityException | IllegalArgumentException ignored) { }
        }
    }

    public static void accept(Context c, Location location) {
        if (!FocusStore.geoEnabled(c) || !location.hasAccuracy()) return;
        if (!GeoFenceMath.validCoordinate(location.getLatitude(), location.getLongitude())) return;
        long ageMs = TimeUnit.NANOSECONDS.toMillis(
                Math.max(0L, SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos()));
        if (ageMs > MAX_AGE_MS || location.isFromMockProvider()) return;
        double distance = GeoFenceMath.distanceMeters(FocusStore.geoLatitude(c),
                FocusStore.geoLongitude(c), location.getLatitude(), location.getLongitude());
        GeoFenceMath.Position position = GeoFenceMath.classify(distance, location.getAccuracy(),
                FocusStore.geoRadius(c));
        // Boundary uncertainty deliberately becomes UNKNOWN. Do not keep a stale "inside" state.
        FocusStore.prefs(c).edit().putString("geo_state", position.name())
                .putLong("geo_checked_wall", System.currentTimeMillis())
                .putFloat("geo_distance_m", (float) distance)
                .putFloat("geo_accuracy_m", location.getAccuracy()).apply();
    }

    public static boolean shouldTrigger(Context c) {
        if (!FocusStore.geoEnabled(c) || !hasForegroundPermission(c)
                || !hasBackgroundPermission(c)) return false;
        long checked = FocusStore.prefs(c).getLong("geo_checked_wall", 0L);
        if (checked <= 0 || Math.abs(System.currentTimeMillis() - checked) > MAX_AGE_MS) return false;
        String state = FocusStore.prefs(c).getString("geo_state", "UNKNOWN");
        return FocusStore.geoMode(c) == 1 ? "INSIDE".equals(state)
                : FocusStore.geoMode(c) == 2 && "OUTSIDE".equals(state);
    }

    public static String status(Context c) {
        if (!FocusStore.geoEnabled(c)) return "Geofence disattivato";
        if (!hasForegroundPermission(c)) return "Concedi accesso alla posizione";
        if (!hasBackgroundPermission(c)) return "Concedi posizione sempre attiva per le fasce geografiche";
        long checked = FocusStore.prefs(c).getLong("geo_checked_wall", 0L);
        if (checked == 0 || Math.abs(System.currentTimeMillis() - checked) > MAX_AGE_MS) {
            return "Posizione non disponibile o scaduta: geofence sospeso";
        }
        String state = FocusStore.prefs(c).getString("geo_state", "UNKNOWN");
        float distance = FocusStore.prefs(c).getFloat("geo_distance_m", 0);
        float accuracy = FocusStore.prefs(c).getFloat("geo_accuracy_m", 0);
        return String.format(java.util.Locale.ITALY,
                "%s • distanza %.0f m • precisione ±%.0f m (GPS/rete)", state, distance, accuracy);
    }
}
