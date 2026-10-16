package com.ampy.focus;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Restrict chosen packages on metered networks during focus.
 * This includes mobile data and user-metered Wi-Fi; it is NOT an all-network firewall.
 */
public final class CellDataController {
    private CellDataController() {}

    public static void update(Context context, boolean focused) {
        SharedPreferences p = FocusStore.prefs(context);
        boolean active = focused && p.getBoolean("metered_enabled", false);
        boolean applied = p.getBoolean("metered_applied", false);
        if (!active && !applied) return;
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null || !dpm.isDeviceOwnerApp(context.getPackageName())) return;
        ComponentName admin = new ComponentName(context, FocusAdminReceiver.class);
        try {
            if (active) {
                if (!applied) {
                    List<String> previous = dpm.getMeteredDataDisabledPackages(admin);
                    p.edit().putStringSet("metered_previous",
                            new HashSet<>(previous)).putBoolean("metered_applied", true).apply();
                }
                Set<String> desired = new HashSet<>(p.getStringSet("metered_previous",
                        Collections.emptySet()));
                for (String pkg : p.getStringSet("metered_packages", Collections.emptySet())) {
                    if (!pkg.equals(context.getPackageName())) desired.add(pkg);
                }
                List<String> failed = dpm.setMeteredDataDisabledPackages(admin,
                        new ArrayList<>(desired));
                p.edit().putString("metered_status", failed.isEmpty()
                        ? "Rete a consumo limitata per " + desired.size() + " app."
                        : "Alcuni pacchetti non possono essere limitati su rete a consumo.").apply();
            } else {
                List<String> previous = new ArrayList<>(p.getStringSet("metered_previous",
                        Collections.emptySet()));
                List<String> failed = dpm.setMeteredDataDisabledPackages(admin, previous);
                if (failed.isEmpty()) {
                    p.edit().putBoolean("metered_applied", false)
                            .remove("metered_previous")
                            .putString("metered_status", "Limitazioni di rete ripristinate.").apply();
                }
            }
        } catch (RuntimeException e) {
            p.edit().putString("metered_status",
                    "Errore rete a consumo: " + e.getClass().getSimpleName()).apply();
        }
    }
}
