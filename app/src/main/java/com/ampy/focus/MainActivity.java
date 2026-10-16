package com.ampy.focus;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Small, dependency-free minimal home/dashboard for both flavors. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(8, 13, 14);
    private static final int TEXT = Color.rgb(240, 250, 247);
    private static final int MUTED = Color.rgb(152, 179, 174);
    private static final int ACCENT = Color.rgb(120, 224, 206);
    private LinearLayout column;
    private TextView countdown;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean noisePlaying = false;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (countdown != null) {
                long remaining = FocusManager.expectedEndMillis(MainActivity.this) - System.currentTimeMillis();
                if (remaining <= 0 && FocusManager.isWanted(MainActivity.this)) {
                    FocusManager.refresh(MainActivity.this);
                    FocusScheduler.rearm(MainActivity.this);
                    render();
                } else {
                    countdown.setText(remaining > 0 ? duration(remaining) : "--:--:--");
                }
            }
            handler.postDelayed(this, 1_000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        FocusManager.refresh(this);
        FocusScheduler.rearm(this);
        render();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        return view;
    }

    private void addLabel(String content, float size, int color, boolean bold, int top) {
        TextView view = label(content, size, color, bold);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        column.addView(view, lp);
    }

    private void addButton(String text, boolean prominent, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(prominent ? BG : TEXT);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(prominent ? ACCENT : Color.rgb(21, 34, 36));
        shape.setCornerRadius(dp(16));
        if (!prominent) shape.setStroke(dp(1), Color.rgb(51, 81, 77));
        button.setBackground(shape);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.topMargin = dp(10);
        column.addView(button, lp);
    }

    private void render() {
        countdown = null;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(26), dp(32), dp(26), dp(44));
        scroll.addView(column);
        setContentView(scroll);

        addLabel("A M P Y F O C U S", 16, ACCENT, true, 10);
        addLabel(BuildConfig.EDITION + "  •  v" + BuildConfig.VERSION_NAME, 12, MUTED, false, 8);

        boolean wanted = FocusManager.isWanted(this);
        boolean enforced = FocusManager.isEnforced(this);
        if (wanted) {
            addLabel(enforced ? "● FOCUS ATTIVO" : "○ BLOCCO NON OPERATIVO",
                    13, enforced ? ACCENT : Color.rgb(252, 177, 105), true, 44);
            countdown = label("00:00:00", 48, TEXT, true);
            LinearLayout.LayoutParams timerLp = new LinearLayout.LayoutParams(-1, -2);
            timerLp.topMargin = dp(30);
            column.addView(countdown, timerLp);
            long remaining = FocusManager.expectedEndMillis(this) - System.currentTimeMillis();
            countdown.setText(remaining > 0 ? duration(remaining) : "--:--:--");
            addLabel(FocusStore.goal(this), 22, TEXT, true, 35);
            addLabel(FocusManager.lastMessage(this), 12, MUTED, false, 20);
            addButton("App consentite", false, () -> showLauncher());
            addButton(noisePlaying ? "Ferma Brown Noise" : "Ascolta Brown Noise", false,
                    () -> toggleNoise());
            addButton("Chiama emergenza (112)", false, () -> callEmergency());

            Button finish = new Button(this);
            finish.setText("Tieni premuto per terminare il Focus");
            finish.setAllCaps(false);
            finish.setTextColor(MUTED);
            finish.setBackgroundColor(Color.TRANSPARENT);
            finish.setOnClickListener(v -> Toast.makeText(this,
                    "Tieni premuto per interrompere volontariamente.", Toast.LENGTH_SHORT).show());
            finish.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setTitle("Interrompere la sessione?")
                        .setMessage("La sessione e la fascia programmata saranno interrotte. Le app sospese saranno ripristinate.")
                        .setNegativeButton("Continua Focus", null)
                        .setPositiveButton("Termina", (dialog, which) -> {
                            FocusManager.endAll(this);
                            render();
                        }).show();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(64));
            lp.topMargin = dp(36);
            column.addView(finish, lp);
            return;
        }

        addLabel("UNO SCOPO. ZERO DISTRAZIONI.", 23, TEXT, true, 52);
        addLabel(FocusStore.goal(this), 16, MUTED, false, 16);
        addButton("Modifica obiettivo", false, () -> editGoal());
        addLabel("AVVIO RAPIDO", 12, ACCENT, true, 32);
        addButton("Focus • 25 minuti", true, () -> start(25));
        addButton("Focus • 60 minuti", false, () -> start(60));
        addButton("Focus • 120 minuti", false, () -> start(120));

        addLabel("RESTRIZIONI", 12, ACCENT, true, 28);
        addButton("Scegli app da bloccare (" + FocusStore.blocked(this).size() + ")",
                false, () -> chooseBlocked());
        addButton("Apri un'app", false, () -> showLauncher());

        addLabel("PIANIFICAZIONE", 12, ACCENT, true, 28);
        String schedule = pretty(FocusStore.startMinute(this)) + " – " + pretty(FocusStore.endMinute(this));
        addButton("Imposta orari  •  " + schedule, false, () -> pickStart());
        addButton(FocusStore.scheduleEnabled(this)
                ? "Disattiva fascia oraria" : "Attiva fascia oraria", false, () -> {
            FocusStore.updateSchedule(this, FocusStore.startMinute(this),
                    FocusStore.endMinute(this), !FocusStore.scheduleEnabled(this));
            FocusManager.refresh(this);
            FocusScheduler.rearm(this);
            render();
        });

        addLabel("STATO DEL MOTORE", 12, ACCENT, true, 28);
        addLabel(EditionPolicy.create().availabilityMessage(this), 13, MUTED, false, 12);
        addLabel("Anteprima 0.1: blocco app selezionate; filtro web, processi e quote per-app in sviluppo.",
                12, MUTED, false, 20);
    }

    private void start(int minutes) {
        FocusManager.startManual(this, minutes);
        render();
    }

    private void editGoal() {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setText(FocusStore.goal(this));
        new AlertDialog.Builder(this).setTitle("Il tuo obiettivo")
            .setView(edit)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Salva", (dialog, which) -> {
                String goal = edit.getText().toString().trim();
                if (!goal.isEmpty()) FocusStore.prefs(this).edit().putString("goal", goal).apply();
                render();
            }).show();
    }

    private void chooseBlocked() {
        List<FocusPackageCatalog.Entry> apps = FocusPackageCatalog.list(this);
        if (apps.isEmpty()) {
            Toast.makeText(this, "Nessuna app visibile al selettore.", Toast.LENGTH_LONG).show();
            return;
        }
        Set<String> checked = FocusStore.blocked(this);
        String[] titles = new String[apps.size()];
        boolean[] selections = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            FocusPackageCatalog.Entry e = apps.get(i);
            titles[i] = e.label + "  ·  " + e.pkg;
            selections[i] = checked.contains(e.pkg);
        }
        new AlertDialog.Builder(this).setTitle("Blocca soltanto le app selezionate")
            .setMultiChoiceItems(titles, selections, (dialog, which, value) -> selections[which] = value)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Salva", (dialog, which) -> {
                Set<String> result = new HashSet<>();
                for (int i = 0; i < apps.size(); i++) if (selections[i]) result.add(apps.get(i).pkg);
                FocusStore.setBlocked(this, result);
                FocusManager.refresh(this);
                render();
            }).show();
    }

    private void showLauncher() {
        List<FocusPackageCatalog.Entry> apps = FocusPackageCatalog.list(this);
        Set<String> blocked = FocusStore.blocked(this);
        boolean focus = FocusManager.isWanted(this);
        List<FocusPackageCatalog.Entry> allowed = new ArrayList<>();
        for (FocusPackageCatalog.Entry entry : apps) {
            if (!focus || !blocked.contains(entry.pkg)) allowed.add(entry);
        }
        if (allowed.isEmpty()) {
            Toast.makeText(this, "Non ci sono app consentite in questa sessione.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[allowed.size()];
        for (int i = 0; i < items.length; i++) items[i] = allowed.get(i).label;
        new AlertDialog.Builder(this).setTitle("App consentite")
            .setItems(items, (dialog, which) -> {
                Intent launch = getPackageManager().getLaunchIntentForPackage(allowed.get(which).pkg);
                if (launch != null) {
                    try {
                        startActivity(launch);
                    } catch (RuntimeException e) {
                        Toast.makeText(this, "App non avviabile.", Toast.LENGTH_SHORT).show();
                    }
                }
            }).setNegativeButton("Chiudi", null).show();
    }

    private void pickStart() {
        int initial = FocusStore.startMinute(this);
        new TimePickerDialog(this, (view, h, minute) -> {
            int start = h * 60 + minute;
            int previousEnd = FocusStore.endMinute(this);
            new TimePickerDialog(this, (endView, eh, em) -> {
                int end = eh * 60 + em;
                if (start == end) {
                    Toast.makeText(this, "Inizio e fine devono essere diversi.", Toast.LENGTH_LONG).show();
                    return;
                }
                FocusStore.updateSchedule(this, start, end, true);
                FocusManager.refresh(this);
                FocusScheduler.rearm(this);
                render();
            }, previousEnd / 60, previousEnd % 60, true).show();
        }, initial / 60, initial % 60, true).show();
    }

    private void toggleNoise() {
        if (noisePlaying) {
            stopService(new Intent(this, BrownNoiseService.class));
            noisePlaying = false;
        } else {
            try {
                Intent audio = new Intent(this, BrownNoiseService.class);
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(audio);
                else startService(audio);
                noisePlaying = true;
            } catch (RuntimeException e) {
                Toast.makeText(this, "Riproduzione non disponibile.", Toast.LENGTH_LONG).show();
            }
        }
        render();
    }

    private void callEmergency() {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")));
        } catch (RuntimeException e) {
            Toast.makeText(this, "Apri il dialer di emergenza del dispositivo.", Toast.LENGTH_LONG).show();
        }
    }

    private static String pretty(int minute) {
        return String.format(Locale.ITALY, "%02d:%02d", minute / 60, minute % 60);
    }

    private static String duration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(Locale.ITALY, "%02d:%02d:%02d",
                seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }
}
