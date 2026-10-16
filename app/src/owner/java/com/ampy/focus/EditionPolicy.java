package com.ampy.focus;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Owner flavor: Android-approved package suspension on a provisioned device. */
public final class EditionPolicy implements FocusPolicy {
    public static FocusPolicy create() { return new EditionPolicy(); }

    @Override
    public boolean isAvailable(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isDeviceOwnerApp(context.getPackageName());
    }

    @Override
    public String availabilityMessage(Context context) {
        return isAvailable(context) ? "Device Owner configurato"
                : "Device Owner NON configurato: servono provisioning ufficiale e dispositivo di test.";
    }

    @Override
    public Result apply(Context context, boolean active, Set<String> selected) {
        if (!isAvailable(context)) return new Result(false, availabilityMessage(context));
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, FocusAdminReceiver.class);
        SharedPreferences prefs = FocusStore.prefs(context);
        Set<String> applied = new HashSet<>(prefs.getStringSet("owner_applied_packages", Collections.emptySet()));

        Set<String> desired = new HashSet<>();
        if (active) {
            // Defense in depth: do not allow the app to suspend itself.
            for (String pkg : selected) {
                if (!pkg.equals(context.getPackageName())) desired.add(pkg);
            }
        }

        Set<String> remove = new HashSet<>(applied);
        remove.removeAll(desired);
        Set<String> add = new HashSet<>(desired);
        add.removeAll(applied);
        boolean ok = true;
        if (!remove.isEmpty()) {
            try {
                Set<String> failed = new HashSet<>(Arrays.asList(
                        dpm.setPackagesSuspended(admin, remove.toArray(new String[0]), false)));
                for (String pkg : remove) if (!failed.contains(pkg)) applied.remove(pkg);
                if (!failed.isEmpty()) ok = false;
            } catch (RuntimeException error) { ok = false; }
        }
        if (!add.isEmpty()) {
            try {
                Set<String> failed = new HashSet<>(Arrays.asList(
                        dpm.setPackagesSuspended(admin, add.toArray(new String[0]), true)));
                for (String pkg : add) if (!failed.contains(pkg)) applied.add(pkg);
                if (!failed.isEmpty()) ok = false;
            } catch (RuntimeException error) { ok = false; }
        }

        prefs.edit().putStringSet("owner_applied_packages", new HashSet<>(applied)).apply();
        if (!ok) return new Result(false,
                "Alcune app non possono essere sospese/ripristinate: protezioni Android o errore policy.");
        return new Result(true, active
                ? "Blocco Device Owner attivo su " + applied.size() + " app selezionate."
                : "Limitazioni Device Owner ripristinate.");
    }
}
