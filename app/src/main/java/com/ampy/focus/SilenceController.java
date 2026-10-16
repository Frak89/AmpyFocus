package com.ampy.focus;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

/** Optional DND, user must explicitly grant notification policy access. */
public final class SilenceController {
    private SilenceController() {}
    public static boolean hasAccess(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }
    public static void apply(Context c, boolean active) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || !nm.isNotificationPolicyAccessGranted()) return;
        SharedPreferences p = FocusStore.prefs(c);
        boolean applied = p.getBoolean("dnd_applied", false);
        try {
            if (active && !applied) {
                int old = nm.getCurrentInterruptionFilter();
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                p.edit().putInt("previous_interruption_filter", old)
                    .putBoolean("dnd_applied", true).apply();
            } else if (!active && applied) {
                nm.setInterruptionFilter(p.getInt("previous_interruption_filter",
                        NotificationManager.INTERRUPTION_FILTER_ALL));
                p.edit().putBoolean("dnd_applied", false).apply();
            }
        } catch (RuntimeException ignored) { }
    }
}
