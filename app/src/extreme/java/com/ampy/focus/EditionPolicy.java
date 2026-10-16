package com.ampy.focus;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/** Extreme flavor: communicates ONLY with authenticated AmpybiOS FocusBridge. */
public final class EditionPolicy implements FocusPolicy {
    private static final String PERMISSION = "com.ampybios.permission.MANAGE_FOCUS";
    private static final Uri BRIDGE = Uri.parse("content://com.ampybios.focusbridge.policy");

    public static FocusPolicy create() { return new EditionPolicy(); }

    @Override
    public boolean isAvailable(Context context) {
        PackageManager pm = context.getPackageManager();
        return context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
                && pm.resolveContentProvider(BRIDGE.getAuthority(), 0) != null;
    }

    @Override
    public String availabilityMessage(Context context) {
        return isAvailable(context)
                ? "FocusBridge AmpybiOS rilevato (verifica stato policy all'avvio)."
                : "Bridge AmpybiOS non disponibile: APK standalone non può applicare blocchi di sistema.";
    }

    @Override
    public Result apply(Context context, boolean active, Set<String> selected) {
        if (!isAvailable(context)) return new Result(false, availabilityMessage(context));
        Bundle request = new Bundle();
        request.putInt("protocol", 2);
        request.putBoolean("active", active);
        request.putStringArrayList("blocked_packages", new ArrayList<>(selected));
        request.putString("request_id", UUID.randomUUID().toString());
        request.putBoolean("family_dns", FocusStore.prefs(context).getBoolean("family_dns_enabled", false));
        request.putBoolean("silent_mode", FocusStore.prefs(context).getBoolean("silence_enabled", false));
        request.putBoolean("location_enabled", FocusStore.geoEnabled(context));
        request.putInt("location_mode", FocusStore.geoMode(context));
        request.putInt("location_radius_m", FocusStore.geoRadius(context));
        if (FocusStore.geoEnabled(context)) {
            request.putDouble("location_lat", FocusStore.geoLatitude(context));
            request.putDouble("location_lon", FocusStore.geoLongitude(context));
        }
        request.putInt("daily_quota_min", FocusStore.prefs(context).getInt("quota_minutes", 15));
        request.putBoolean("daily_quota_enabled", FocusStore.prefs(context).getBoolean("quota_enabled", false));
        try {
            Bundle response = context.getContentResolver().call(BRIDGE, "set_focus", null, request);
            if (response == null) return new Result(false, "FocusBridge non ha risposto.");
            return new Result(response.getBoolean("applied", false),
                    response.getString("reason", "Risposta FocusBridge ricevuta."));
        } catch (RuntimeException e) {
            return new Result(false, "Errore FocusBridge: " + e.getClass().getSimpleName());
        }
    }
}
