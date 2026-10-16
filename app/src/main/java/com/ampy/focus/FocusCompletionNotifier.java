package com.ampy.focus;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** Separate completion channel; user chooses channel sound and bypass settings. */
public final class FocusCompletionNotifier {
    private static final String CHANNEL = "focus_completed";
    private FocusCompletionNotifier() {}
    public static void notifyEnd(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Fine sessione Focus",
                NotificationManager.IMPORTANCE_HIGH));
        PendingIntent open = PendingIntent.getActivity(c, 0,
                new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification complete = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("AmpyFocus · sessione conclusa")
                .setContentText("Hai completato il tuo tempo di concentrazione.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        nm.notify(908, complete);
    }
}
