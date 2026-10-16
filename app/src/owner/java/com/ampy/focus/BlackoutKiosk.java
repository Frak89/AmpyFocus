package com.ampy.focus;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.telecom.TelecomManager;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Opt-in true kiosk only on a provisioned Device Owner handset. */
public final class BlackoutKiosk {
    private static final String OLD_ALLOWLIST = "kiosk_old_allowlist";
    private static final String MANAGED = "kiosk_managed_by_ampy";
    private BlackoutKiosk() {}

    public static boolean supported(Context c) {
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isDeviceOwnerApp(c.getPackageName());
    }

    public static boolean enter(Activity a) {
        if (!FocusStore.prefs(a).getBoolean("blackout_kiosk_enabled", false)) return false;
        DevicePolicyManager dpm = (DevicePolicyManager) a.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null || !dpm.isDeviceOwnerApp(a.getPackageName())) return false;
        ComponentName admin = new ComponentName(a, FocusAdminReceiver.class);
        try {
            SharedPreferences p = FocusStore.prefs(a);
            Set<String> previous = new HashSet<>(Arrays.asList(dpm.getLockTaskPackages(admin)));
            Set<String> allowed = new HashSet<>(previous);
            allowed.add(a.getPackageName());
            TelecomManager telecom = (TelecomManager) a.getSystemService(Context.TELECOM_SERVICE);
            if (telecom != null && telecom.getDefaultDialerPackage() != null) {
                allowed.add(telecom.getDefaultDialerPackage()); // emergency dialer stays available
            }
            if (!p.getBoolean(MANAGED, false)) {
                // Persist for cleanup after process death, time expiry, or reboot.
                p.edit().putStringSet(OLD_ALLOWLIST, previous)
                        .putBoolean(MANAGED, true).commit();
            }
            dpm.setLockTaskPackages(admin, allowed.toArray(new String[0]));
            if (!dpm.isLockTaskPermitted(a.getPackageName())) {
                restore(a);
                return false;
            }
            a.startLockTask();
            return true;
        } catch (RuntimeException e) {
            restore(a);
            return false;
        }
    }

    public static void exit(Activity a) {
        try { a.stopLockTask(); } catch (RuntimeException ignored) { }
        restore(a);
    }

    public static void restore(Context c) {
        SharedPreferences p = FocusStore.prefs(c);
        if (!p.getBoolean(MANAGED, false)) return;
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null || !dpm.isDeviceOwnerApp(c.getPackageName())) return;
        try {
            ComponentName admin = new ComponentName(c, FocusAdminReceiver.class);
            Set<String> original = p.getStringSet(OLD_ALLOWLIST, java.util.Collections.emptySet());
            // Only remove entries we added; preserve unrelated DPC policies if changed.
            Set<String> current = new HashSet<>(Arrays.asList(dpm.getLockTaskPackages(admin)));
            current.remove(c.getPackageName());
            current.addAll(original);
            dpm.setLockTaskPackages(admin, current.toArray(new String[0]));
            p.edit().putBoolean(MANAGED, false).remove(OLD_ALLOWLIST).apply();
        } catch (RuntimeException ignored) {
            // Keep record for next reconciliation; do not claim restored.
        }
    }
}
