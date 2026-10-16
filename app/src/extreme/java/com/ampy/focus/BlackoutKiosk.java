package com.ampy.focus;

import android.app.Activity;
import android.content.Context;

/**
 * Extreme APK does not gain lock-task privileges without a real platform
 * integration. Blackout remains a full-screen activity on stock Android.
 */
public final class BlackoutKiosk {
    private BlackoutKiosk() {}
    public static boolean supported(Context c) { return false; }
    public static boolean enter(Activity a) { return false; }
    public static void exit(Activity a) { }
    public static void restore(Context c) { }
}
