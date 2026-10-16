package com.ampy.focus;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Daily foreground totals. Not a hard second-precise timer; can be delayed by Doze. */
public final class UsageQuota {
    private UsageQuota() {}
    public static boolean hasAccess(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), c.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static Set<String> expired(Context c) {
        Set<String> expired = new HashSet<>();
        if (!FocusStore.prefs(c).getBoolean("quota_enabled", false) || !hasAccess(c)) return expired;
        int limit = FocusStore.prefs(c).getInt("quota_minutes", 15);
        if (limit <= 0) return expired;
        UsageStatsManager manager = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) return expired;
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        Map<String, UsageStats> usage = manager.queryAndAggregateUsageStats(
                midnight.getTimeInMillis(), System.currentTimeMillis());
        if (usage == null) return expired;
        for (String pkg : FocusStore.blocked(c)) {
            UsageStats stat = usage.get(pkg);
            if (stat != null && stat.getTotalTimeInForeground() >= limit * 60_000L) {
                expired.add(pkg);
            }
        }
        return expired;
    }
}
