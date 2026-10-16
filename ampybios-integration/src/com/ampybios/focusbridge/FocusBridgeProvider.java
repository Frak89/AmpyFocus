package com.ampybios.focusbridge;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.SystemClock;
import android.telecom.TelecomManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * AmpybiOS-only, platform-signed provider built with AOSP platform_apis.
 * Implements PACKAGE SUSPENSION ONLY. Full firewall, process restrictions,
 * app usage quota accounting and domain filtering require further OS hooks.
 *
 * Fails closed to the CALLER (applied=false) for every unsupported option,
 * and fails open for user/device recovery by expiring stale suspension leases.
 */
public final class FocusBridgeProvider extends ContentProvider {
    private static final String PERMISSION = "com.ampybios.permission.MANAGE_FOCUS";
    private static final String PREFS = "focus_lease";
    private static final int ALARM_ID = 10089;
    private static final long MAX_LEASE_MS = 20L * 60_000L;
    private static final String KEY_MANAGED = "managed_packages";
    private static final String KEY_LEASE = "lease_wall_expiration";

    @Override public boolean onCreate() {
        Context c = getContext();
        if (c != null) reconcileLease(c);
        return true;
    }

    @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        if (context == null) return reply(false, "Bridge context unavailable");
        context.enforceCallingPermission(PERMISSION, "Requires AmpybiOS signature permission");
        if (!"set_focus".equals(method) || extras == null || extras.getInt("protocol") != 2) {
            return reply(false, "Unsupported FocusBridge protocol or operation");
        }
        // Android's provider permission also restricts all exported entry points.
        if (extras.getBoolean("family_dns") ||
                extras.getBoolean("metered_enabled") ||
                (extras.getStringArrayList("blocked_domains") != null
                    && !extras.getStringArrayList("blocked_domains").isEmpty())) {
            return reply(false, "Firewall, custom DNS and metered data integration not installed in system image");
        }
        ArrayList<String> requested = extras.getStringArrayList("blocked_packages");
        if (requested == null || requested.size() > 300) {
            return reply(false, "Invalid blocked packages list");
        }
        HashSet<String> desired = new HashSet<>();
        for (String pkg : requested) {
            if (pkg == null || pkg.isEmpty() || isProtected(context, pkg)) {
                return reply(false, "A protected or invalid package was selected");
            }
            desired.add(pkg);
        }
        boolean active = extras.getBoolean("active", false);
        if (!active) desired.clear();
        try {
            synchronized (FocusBridgeProvider.class) {
                SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                HashSet<String> managed = new HashSet<>(p.getStringSet(KEY_MANAGED, Collections.emptySet()));
                HashSet<String> remove = new HashSet<>(managed);
                remove.removeAll(desired);
                HashSet<String> add = new HashSet<>(desired);
                add.removeAll(managed);
                PackageManager pm = context.getPackageManager();
                if (!remove.isEmpty()) {
                    String[] failed = pm.setPackagesSuspended(remove.toArray(new String[0]), false,
                            null, null, null);
                    remove.removeAll(Arrays.asList(failed == null ? new String[0] : failed));
                    managed.removeAll(remove);
                }
                if (!add.isEmpty()) {
                    String[] failed = pm.setPackagesSuspended(add.toArray(new String[0]), true,
                            null, null, null);
                    add.removeAll(Arrays.asList(failed == null ? new String[0] : failed));
                    managed.addAll(add);
                }
                boolean matches = managed.equals(desired);
                long expiry = active ? System.currentTimeMillis() + MAX_LEASE_MS : 0L;
                p.edit().putStringSet(KEY_MANAGED, managed)
                        .putLong(KEY_LEASE, expiry).commit();
                scheduleExpiry(context, expiry);
                return reply(matches, matches
                        ? "AmpybiOS privileged package suspension active; OS lease max 20 min"
                        : "Some packages could not be suspended/released");
            }
        } catch (RuntimeException error) {
            return reply(false, "Platform suspension failed: " + error.getClass().getSimpleName());
        }
    }

    private static boolean isProtected(Context c, String pkg) {
        if (pkg.equals(c.getPackageName()) || pkg.equals("com.android.systemui")
                || pkg.equals("com.android.settings")
                || pkg.equals("com.android.phone")
                || pkg.equals("com.android.permissioncontroller")
                || pkg.equals("com.google.android.permissioncontroller")
                || pkg.equals("com.android.dialer")
                || pkg.equals("com.google.android.dialer")
                || pkg.equals("com.android.deskclock")
                || pkg.equals("com.google.android.deskclock")
                || pkg.startsWith("com.ampy.focus.")) return true;
        TelecomManager telecom = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
        return telecom != null && pkg.equals(telecom.getDefaultDialerPackage());
    }

    public static void reconcileLease(Context c) {
        synchronized (FocusBridgeProvider.class) {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long expiry = p.getLong(KEY_LEASE, 0);
            if (expiry > System.currentTimeMillis()) {
                scheduleExpiry(c, expiry);
                return;
            }
            Set<String> managed = p.getStringSet(KEY_MANAGED, Collections.emptySet());
            if (!managed.isEmpty()) {
                try {
                    String[] failed = c.getPackageManager().setPackagesSuspended(
                        managed.toArray(new String[0]), false, null, null, null);
                    HashSet<String> pending = new HashSet<>(Arrays.asList(
                        failed == null ? new String[0] : failed));
                    p.edit().putStringSet(KEY_MANAGED, pending).putLong(KEY_LEASE, 0).commit();
                } catch (RuntimeException ignored) {
                    scheduleExpiry(c, System.currentTimeMillis() + 60_000L);
                }
            }
        }
    }

    private static void scheduleExpiry(Context c, long expiry) {
        AlarmManager alarms = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        Intent action = new Intent(c, FocusLeaseReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(c, ALARM_ID, action,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        alarms.cancel(pending);
        if (expiry > 0) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, expiry, pending);
    }

    private static Bundle reply(boolean applied, String reason) {
        Bundle result = new Bundle();
        result.putBoolean("applied", applied);
        result.putString("reason", reason);
        return result;
    }

    @Override public Cursor query(Uri u, String[] projection, String selection,
            String[] args, String order) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
