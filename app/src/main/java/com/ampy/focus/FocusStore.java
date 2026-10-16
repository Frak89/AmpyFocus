package com.ampy.focus;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Preferences intentionally remain on-device: no accounts or telemetry. */
public final class FocusStore {
    private FocusStore() {}

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("ampyfocus", Context.MODE_PRIVATE);
    }

    public static String goal(Context c) {
        return prefs(c).getString("goal", "Concentrati su ciò che conta");
    }

    public static Set<String> blocked(Context c) {
        return new HashSet<>(prefs(c).getStringSet("blocked_packages", Collections.emptySet()));
    }

    public static void setBlocked(Context c, Set<String> packages) {
        prefs(c).edit().putStringSet("blocked_packages", new HashSet<>(packages)).apply();
    }

    public static boolean scheduleEnabled(Context c) {
        return prefs(c).getBoolean("schedule_enabled", false);
    }

    public static int startMinute(Context c) {
        return prefs(c).getInt("start_minute", 9 * 60);
    }

    public static int endMinute(Context c) {
        return prefs(c).getInt("end_minute", 12 * 60);
    }

    public static void updateSchedule(Context c, int start, int end, boolean enabled) {
        prefs(c).edit()
            .putInt("start_minute", start)
            .putInt("end_minute", end)
            .putBoolean("schedule_enabled", enabled)
            .apply();
    }
}
