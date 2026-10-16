package com.ampy.focus;

/** Independent of Android for repeatable pure-Java time-window tests. */
public final class FocusWindow {
    private FocusWindow() {}

    public static boolean contains(int currentMinute, int startMinute, int endMinute) {
        if (!valid(currentMinute) || !valid(startMinute) || !valid(endMinute)) {
            return false;
        }
        if (startMinute == endMinute) return false;
        if (startMinute < endMinute) {
            return currentMinute >= startMinute && currentMinute < endMinute;
        }
        return currentMinute >= startMinute || currentMinute < endMinute;
    }

    public static boolean valid(int minute) {
        return minute >= 0 && minute < 24 * 60;
    }
}
