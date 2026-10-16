package com.ampy.focus;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.Calendar;

/** Best-effort one-shot wakeups. No exact-alarm permission is requested. */
public final class FocusScheduler {
    private static final int START_ID = 101;
    private static final int END_ID = 102;
    private static final int MANUAL_ID = 103;
    private static final int QUOTA_ID = 104;
    private static final int BRIDGE_HEARTBEAT_ID = 105;
    private FocusScheduler() {}

    public static void rearm(Context c) {
        AlarmManager alarm = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        cancel(alarm, c, START_ID);
        cancel(alarm, c, END_ID);
        cancel(alarm, c, MANUAL_ID);
        cancel(alarm, c, QUOTA_ID);
        cancel(alarm, c, BRIDGE_HEARTBEAT_ID);

        if (FocusStore.scheduleEnabled(c)
                && FocusStore.startMinute(c) != FocusStore.endMinute(c)) {
            schedule(alarm, c, START_ID, nextTime(FocusStore.startMinute(c)));
            schedule(alarm, c, END_ID, nextTime(FocusStore.endMinute(c)));
        }
        if (FocusManager.isWanted(c)) {
            // Keep the OS-owned 20-minute suspension lease alive in the Extreme edition.
            schedule(alarm, c, BRIDGE_HEARTBEAT_ID,
                    System.currentTimeMillis() + 10 * 60_000L);
        }
        if (FocusStore.prefs(c).getBoolean("quota_enabled", false)) {
            schedule(alarm, c, QUOTA_ID, System.currentTimeMillis() + 15 * 60_000L);
        }
        long deadline = FocusManager.manualUntil(c);
        if (deadline > System.currentTimeMillis()) {
            schedule(alarm, c, MANUAL_ID, deadline);
        }
    }

    private static long nextTime(int minute) {
        Calendar when = Calendar.getInstance();
        when.set(Calendar.HOUR_OF_DAY, minute / 60);
        when.set(Calendar.MINUTE, minute % 60);
        when.set(Calendar.SECOND, 0);
        when.set(Calendar.MILLISECOND, 0);
        if (when.getTimeInMillis() <= System.currentTimeMillis()) {
            when.add(Calendar.DAY_OF_YEAR, 1);
        }
        return when.getTimeInMillis();
    }

    private static PendingIntent pending(Context c, int id) {
        Intent intent = new Intent(c, FocusAlarmReceiver.class).setAction("com.ampy.focus.WAKE." + id);
        return PendingIntent.getBroadcast(c, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void schedule(AlarmManager alarm, Context c, int id, long when) {
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending(c, id));
    }

    private static void cancel(AlarmManager alarm, Context c, int id) {
        alarm.cancel(pending(c, id));
    }
}
