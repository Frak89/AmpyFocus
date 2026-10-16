package com.ampybios.focusbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** OS-owned lease expiration; never depend only on the user APK to restore packages. */
public final class FocusLeaseReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        FocusBridgeProvider.reconcileLease(context);
    }
}
