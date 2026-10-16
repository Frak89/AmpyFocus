package com.ampy.focus;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.telecom.TelecomManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Visible user-launchable applications only; avoid protected system roles. */
public final class FocusPackageCatalog {
    public static final class Entry {
        public final String pkg;
        public final String label;
        public Entry(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }
    private FocusPackageCatalog() {}

    public static List<Entry> list(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);
        Map<String, Entry> unique = new HashMap<>();
        TelecomManager telecom = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
        String dialer = telecom != null ? telecom.getDefaultDialerPackage() : "";
        for (ResolveInfo info : apps) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || pkg.equals(dialer) || protectedPackage(pkg)) continue;
            String label = String.valueOf(info.loadLabel(pm));
            unique.put(pkg, new Entry(pkg, label));
        }
        ArrayList<Entry> entries = new ArrayList<>(unique.values());
        Collections.sort(entries, Comparator.comparing(e -> e.label.toLowerCase(java.util.Locale.ROOT)));
        return entries;
    }

    private static boolean protectedPackage(String pkg) {
        return pkg.equals("com.android.settings")
                || pkg.equals("com.android.systemui")
                || pkg.equals("com.android.phone")
                || pkg.equals("com.android.permissioncontroller")
                || pkg.equals("com.google.android.permissioncontroller")
                || pkg.equals("com.android.deskclock")
                || pkg.equals("com.google.android.deskclock")
                || pkg.equals("com.android.dialer")
                || pkg.equals("com.google.android.dialer");
    }
}
