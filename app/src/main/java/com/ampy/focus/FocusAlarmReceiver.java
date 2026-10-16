package com.ampy.focus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class FocusAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && !intent.getAction().startsWith("com.ampy.focus.WAKE.")) {
            GeoFenceController.register(context);
        }
        FocusManager.refresh(context);
        FocusScheduler.rearm(context);
    }
}
