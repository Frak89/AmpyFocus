package com.ampy.focus;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Calendar;

/** Central reconciliation between desired session state and actual platform policy. */
public final class FocusManager {
    private FocusManager() {}

    public static long manualUntil(Context c) {
        return FocusStore.prefs(c).getLong("manual_until", 0);
    }

    public static void startManual(Context c, int minutes) {
        if (minutes <= 0 || minutes > 24 * 60) {
            throw new IllegalArgumentException("Invalid duration");
        }
        long end = System.currentTimeMillis() + minutes * 60_000L;
        FocusStore.prefs(c).edit().putLong("manual_until", end).apply();
        refresh(c);
        FocusScheduler.rearm(c);
    }

    public static void endAll(Context c) {
        FocusStore.prefs(c).edit().putLong("manual_until", 0).apply();
        FocusStore.updateSchedule(c, FocusStore.startMinute(c), FocusStore.endMinute(c), false);
        refresh(c);
        FocusScheduler.rearm(c);
    }

    public static boolean isWanted(Context c) {
        if (manualUntil(c) > System.currentTimeMillis()) return true;
        if (!FocusStore.scheduleEnabled(c)) return false;
        Calendar now = Calendar.getInstance();
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        return FocusWindow.contains(minute, FocusStore.startMinute(c), FocusStore.endMinute(c));
    }

    public static boolean isEnforced(Context c) {
        return isWanted(c) && FocusStore.prefs(c).getBoolean("policy_enforced", false);
    }

    public static String lastMessage(Context c) {
        return FocusStore.prefs(c).getString("policy_message", "");
    }

    public static synchronized void refresh(Context c) {
        Context context = c.getApplicationContext();
        SharedPreferences p = FocusStore.prefs(context);
        boolean wanted = isWanted(context);
        FocusPolicy backend = EditionPolicy.create();
        boolean wasApplied = p.getBoolean("policy_enforced", false);

        // Do not claim protection when Device Owner/FocusBridge is missing.
        if (wanted && !backend.isAvailable(context)) {
            p.edit().putBoolean("policy_enforced", false)
                .putString("policy_message", backend.availabilityMessage(context)).apply();
            return;
        }
        if (!wanted && !wasApplied && !backend.isAvailable(context)) {
            p.edit().putString("policy_message", backend.availabilityMessage(context)).apply();
            return;
        }
        try {
            FocusPolicy.Result result = backend.apply(context, wanted, FocusStore.blocked(context));
            p.edit().putBoolean("policy_enforced", wanted && result.success)
                .putString("policy_message", result.message).apply();
        } catch (RuntimeException e) {
            p.edit().putBoolean("policy_enforced", false)
                .putString("policy_message", "Errore policy: " + e.getClass().getSimpleName()).apply();
        }
    }

    public static long expectedEndMillis(Context c) {
        long now = System.currentTimeMillis();
        long end = manualUntil(c) > now ? manualUntil(c) : 0;
        if (FocusStore.scheduleEnabled(c)) {
            Calendar current = Calendar.getInstance();
            int minute = current.get(Calendar.HOUR_OF_DAY) * 60 + current.get(Calendar.MINUTE);
            if (FocusWindow.contains(minute, FocusStore.startMinute(c), FocusStore.endMinute(c))) {
                Calendar scheduleEnd = Calendar.getInstance();
                int m = FocusStore.endMinute(c);
                scheduleEnd.set(Calendar.HOUR_OF_DAY, m / 60);
                scheduleEnd.set(Calendar.MINUTE, m % 60);
                scheduleEnd.set(Calendar.SECOND, 0);
                scheduleEnd.set(Calendar.MILLISECOND, 0);
                if (scheduleEnd.getTimeInMillis() <= now) scheduleEnd.add(Calendar.DAY_OF_YEAR, 1);
                end = Math.max(end, scheduleEnd.getTimeInMillis());
            }
        }
        return end;
    }
}
