package com.ampy.focus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;

public final class GeoFenceReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !GeoFenceController.hasForegroundPermission(context)) return;
        try {
            Location location = intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED);
            if (location == null) return;
            GeoFenceController.accept(context, location);
            FocusManager.refresh(context);
        } catch (RuntimeException ignored) { }
    }
}
