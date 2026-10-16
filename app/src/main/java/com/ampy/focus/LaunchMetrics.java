package com.ampy.focus;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/** Actual app foreground events; this cannot count denied launch attempts. */
public final class LaunchMetrics {
    private LaunchMetrics() {}
    public static Map<String, Integer> today(Context c) {
        Map<String, Integer> out = new HashMap<>();
        if (!UsageQuota.hasAccess(c)) return out;
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return out;
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        UsageEvents events = usm.queryEvents(start.getTimeInMillis(), System.currentTimeMillis());
        if (events == null) return out;
        UsageEvents.Event e = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                out.put(e.getPackageName(), out.getOrDefault(e.getPackageName(), 0) + 1);
            }
        }
        return out;
    }
}
