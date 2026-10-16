package com.ampy.focus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Actual selectable Android HOME replacement, deliberately separate from full settings.
 * Never pins/kiosks the screen without the user's separate permission.
 */
public final class MinimalHomeActivity extends Activity {
    private static final int BACKGROUND = 0xFF070B0C;
    private static final int FOREGROUND = 0xFFF1F7F5;
    private static final int ACCENT = 0xFF80E2CF;
    private static final int MUTED = 0xFF9CB5B1;
    private LinearLayout content;
    private TextView clock;
    private TextView timer;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable update = new Runnable() {
        @Override public void run() {
            if (clock != null) clock.setText(new SimpleDateFormat("HH:mm", Locale.ITALY).format(new Date()));
            if (timer != null) {
                long remaining = FocusManager.expectedEndMillis(MinimalHomeActivity.this)
                        - System.currentTimeMillis();
                timer.setText(GeoFenceController.shouldTrigger(MinimalHomeActivity.this)
                        && FocusManager.manualUntil(MinimalHomeActivity.this) <= System.currentTimeMillis()
                        ? "ZONA ATTIVA"
                        : remaining > 0
                        ? String.format(Locale.ITALY, "%02d:%02d:%02d",
                                remaining / 3600_000L,
                                (remaining / 60_000L) % 60,
                                (remaining / 1000L) % 60)
                        : "Focus");
                if (!FocusManager.isWanted(MinimalHomeActivity.this)) {
                    FocusManager.refresh(MinimalHomeActivity.this);
                    showHome();
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        showHome();
    }

    @Override protected void onResume() {
        super.onResume();
        GeoFenceController.refreshLastKnown(this);
        GeoFenceController.register(this);
        FocusManager.refresh(this);
        FocusScheduler.rearm(this);
        showHome();
        handler.removeCallbacks(update);
        handler.post(update);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(update);
        super.onPause();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private TextView text(String value, int sp, int color, boolean bold, int top) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        content.addView(t, p);
        return t;
    }

    private void action(String label, boolean primary, Runnable task) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(16);
        button.setTextColor(primary ? BACKGROUND : FOREGROUND);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? ACCENT : 0xFF182325);
        bg.setCornerRadius(dp(18));
        button.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(56));
        p.topMargin = dp(12);
        content.addView(button, p);
        button.setOnClickListener(v -> task.run());
    }

    private void showHome() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(28), dp(35), dp(28), dp(45));
        scroll.addView(content);
        setContentView(scroll);
        text("AMPYFOCUS  ·  HOME", 13, ACCENT, true, 6);
        clock = text("00:00", 76, FOREGROUND, true, 26);
        boolean focused = FocusManager.isWanted(this);
        text(FocusStore.goal(this), 21, FOREGROUND, false, 20);
        text(focused
                ? (FocusManager.isEnforced(this) ? "● CONCENTRAZIONE ATTIVA" : "⚠ BLOCCO NON OPERATIVO")
                : "TEMPO PER CIÒ CHE CONTA", 12, focused ? ACCENT : MUTED, true, 18);
        if (focused) {
            timer = text("--:--:--", 28, ACCENT, true, 14);
        } else {
            timer = null;
            action("Inizia 25 minuti di concentrazione", true, () -> {
                FocusManager.startManual(this, 25);
                showHome();
            });
        }

        text("ESSENZIALI", 12, MUTED, true, 29);
        action("Telefono", false, () -> {
            try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))); }
            catch (RuntimeException e) { showError(); }
        });
        action("Sveglia", false, () -> {
            try { startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS)); }
            catch (RuntimeException e) { showError(); }
        });
        Set<String> excluded = FocusManager.currentlyBlocked(this);
        List<FocusPackageCatalog.Entry> apps = FocusPackageCatalog.list(this);
        int count = 0;
        for (FocusPackageCatalog.Entry e : apps) {
            // In strict mode only explicitly whitelisted apps may appear.
            if (excluded.contains(e.pkg)) continue;
            if (focused && !FocusStore.prefs(this).getStringSet("essential_packages",
                    java.util.Collections.emptySet()).contains(e.pkg)) continue;
            if (++count > 4) break;
            action(e.label, false, () -> {
                Intent launch = getPackageManager().getLaunchIntentForPackage(e.pkg);
                if (launch != null) {
                    try { startActivity(launch); } catch (RuntimeException ignored) { showError(); }
                }
            });
        }
        action("Apri impostazioni AmpyFocus", false, () -> startActivity(
                new Intent(this, MainActivity.class)));

        if (focused) {
            Button end = new Button(this);
            end.setAllCaps(false);
            end.setText("Tieni premuto per interrompere");
            end.setTextColor(MUTED);
            end.setBackgroundColor(Color.TRANSPARENT);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(70));
            p.topMargin = dp(24);
            content.addView(end, p);
            end.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                    .setTitle("Interrompere Focus?")
                    .setMessage("Interrompe timer, pianificazione e geofence per ripristinare le app.")
                    .setNegativeButton("Continua", null)
                    .setPositiveButton("Termina", (d, w) -> {
                        FocusManager.endAll(this);
                        showHome();
                    }).show();
                return true;
            });
        }
    }

    private void showError() {
        Toast.makeText(this, "Funzione non disponibile su questo dispositivo.",
                Toast.LENGTH_SHORT).show();
    }
}
