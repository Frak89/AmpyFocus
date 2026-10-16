package com.ampy.focus;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.provider.Settings;
import android.text.InputType;
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
    private static final int GEO_PERMISSION_REQUEST = 505;
    private static final int AUDIO_WAV_REQUEST = 507;
    private int pendingExportColor = AudioSynthesis.BROWN;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean noisePlaying = false;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (countdown != null) {
                long remaining = FocusManager.expectedEndMillis(MainActivity.this) - System.currentTimeMillis();
                if (GeoFenceController.shouldTrigger(MainActivity.this)
                        && FocusManager.manualUntil(MainActivity.this) <= System.currentTimeMillis()) {
                    countdown.setText("ZONA FOCUS");
                } else if (remaining <= 0) {
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
        GeoFenceController.refreshLastKnown(this);
        GeoFenceController.register(this);
        FocusManager.refresh(this);
        FocusScheduler.rearm(this);
        noisePlaying = BrownNoiseService.isPlaying;
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
            countdown.setText(GeoFenceController.shouldTrigger(this)
                && FocusManager.manualUntil(this) <= System.currentTimeMillis()
                ? "ZONA FOCUS" : remaining > 0 ? duration(remaining) : "--:--:--");
            addLabel(FocusStore.goal(this), 22, TEXT, true, 35);
            addLabel(FocusManager.lastMessage(this), 12, MUTED, false, 20);
            addLabel("APP ESSENZIALI", 12, ACCENT, true, 22);
            showEssentialTiles();
            addButton("Tutte le app consentite", false, () -> showLauncher());
            addButton(noisePlaying ? "Ferma suoni" : "Ascolta Brown Noise", false,
                    () -> toggleNoise());
            addButton("Suono White / Pink / Brown", false, () -> chooseSound());
            addButton("Esporta WAV 24-bit / 48 kHz (60 secondi)", false, () -> chooseWavExport());
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
        addButton(FocusStore.prefs(this).getBoolean("strict_allowlist", false)
                ? "Extreme Lockdown: SOLO ESSENZIALI" : "Extreme Lockdown: disattivato",
                false, () -> toggleStrict());
        addButton("Scegli le app ESSENZIALI autorizzate", false, () -> chooseEssential());
        addButton("Apri Launcher minimalista", false,
                () -> startActivity(new Intent(this, MinimalHomeActivity.class)));
        addButton("Launcher essenziale", false, () -> showLauncher());
        addButton("Imposta come schermata Home", false, () -> {
            try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
            catch (RuntimeException e) {
                Toast.makeText(this, "Seleziona il launcher nelle impostazioni Android.", Toast.LENGTH_LONG).show();
            }
        });
        addButton(FocusStore.prefs(this).getBoolean("quota_enabled", false)
                ? "Disattiva limite app quotidiano" : "Attiva limite app quotidiano", false,
                () -> toggleQuota());
        addButton("Aperture di oggi (statistiche locali)", false, () -> showLaunchMetrics());
        addButton("Minuti giornalieri per app  •  "
                + FocusStore.prefs(this).getInt("quota_minutes", 15),
                false, () -> configureQuota());
        addButton(android.os.Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                ? "Avvisi fine sessione: consentiti" : "Autorizza avviso fine Focus",
                false, () -> {
                    if (android.os.Build.VERSION.SDK_INT >= 33)
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 506);
                });
        addButton(UsageQuota.hasAccess(this) ? "Accesso statistiche: concesso"
                : "Autorizza accesso alle statistiche delle app", false,
                () -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        addLabel("AMBIENTE E RETE", 12, ACCENT, true, 28);
        addButton(FocusStore.prefs(this).getBoolean("silence_enabled", false)
                ? "Modalità silenziosa: SÌ" : "Modalità silenziosa: NO", false,
                () -> toggleSilence());
        addButton(SilenceController.hasAccess(this) ? "Permesso Non disturbare: OK"
                : "Autorizza Non disturbare", false, () ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        addButton(FocusStore.prefs(this).getBoolean("family_dns_enabled", false)
                ? "Filtro DNS famiglia: SÌ" : "Filtro DNS famiglia: NO", false,
                () -> toggleFamilyDns());
        addLabel(FocusStore.prefs(this).getString("family_dns_status",
                "Filtro opzionale Cloudflare Family (siti adulti/malware, non completo)."),
                12, MUTED, false, 8);
        addButton("Siti personalizzati (Extreme AmpybiOS)", false, () -> editDomains());
        addLabel("BLOCCO PER LOCALITÀ", 12, ACCENT, true, 28);
        addButton("Configura zona GPS / rete mobile", false, () -> configureGeo());
        addLabel(GeoFenceController.status(this), 12, MUTED, false, 8);

        addLabel("AUDIO OFFLINE", 12, ACCENT, true, 28);
        addButton("Suoni sintetizzati • White / Pink / Brown", false, () -> chooseSound());
        addButton("Esporta WAV 24-bit / 48 kHz (60 secondi)", false, () -> chooseWavExport());
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
        addLabel("v0.2: quote app approssimative (fino a 15 min di ritardo), DNS Family opzionale; GPS/rete dipende dai permessi e dalla precisione. Extreme richiede un bridge firmato.",
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


    private void toggleStrict() {
        boolean old = FocusStore.prefs(this).getBoolean("strict_allowlist", false);
        new AlertDialog.Builder(this).setTitle("Blocco di tutte le app non essenziali")
            .setMessage("Durante una sessione, le app avviabili non presenti nella lista ESSEnZIALI saranno sospese tramite Device Owner (o FocusBridge). Telefono, sveglia, impostazioni e servizi Android critici restano disponibili. Potrai interrompere con una pressione prolungata.")
            .setNegativeButton("Annulla", null)
            .setPositiveButton(old ? "Disattiva" : "Attiva", (d, n) -> {
                FocusStore.prefs(this).edit().putBoolean("strict_allowlist", !old).apply();
                FocusManager.refresh(this);
                render();
            }).show();
    }

    private void chooseEssential() {
        List<FocusPackageCatalog.Entry> apps = FocusPackageCatalog.list(this);
        Set<String> essentials = FocusStore.prefs(this).getStringSet("essential_packages",
                java.util.Collections.emptySet());
        String[] names = new String[apps.size()];
        boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            names[i] = apps.get(i).label + "  ·  " + apps.get(i).pkg;
            checked[i] = essentials.contains(apps.get(i).pkg);
        }
        new AlertDialog.Builder(this).setTitle("App essenziali autorizzate")
            .setMultiChoiceItems(names, checked, (d, which, value) -> checked[which] = value)
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Salva", (d, n) -> {
                Set<String> chosen = new HashSet<>();
                for (int i = 0; i < apps.size(); i++) if (checked[i]) chosen.add(apps.get(i).pkg);
                FocusStore.prefs(this).edit().putStringSet("essential_packages", chosen).apply();
                FocusManager.refresh(this);
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
        Set<String> blocked = FocusManager.currentlyBlocked(this);
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
                Intent audio = new Intent(this, BrownNoiseService.class)
                        .putExtra("color", BrownNoiseService.selectedColor);
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(audio);
                else startService(audio);
                noisePlaying = true;
            } catch (RuntimeException e) {
                Toast.makeText(this, "Riproduzione non disponibile.", Toast.LENGTH_LONG).show();
            }
        }
        render();
    }


    private void chooseSound() {
        String[] names = {"Brown noise", "Pink noise", "White noise", "Ferma riproduzione"};
        new AlertDialog.Builder(this).setTitle("Suoni di concentrazione")
            .setItems(names, (dialog, which) -> {
                if (which == 3) {
                    stopService(new Intent(this, BrownNoiseService.class));
                    noisePlaying = false;
                } else {
                    BrownNoiseService.selectedColor = which;
                    try {
                        Intent audio = new Intent(this, BrownNoiseService.class)
                                .putExtra("color", which);
                        startForegroundService(audio);
                        noisePlaying = true;
                    } catch (RuntimeException e) {
                        Toast.makeText(this, "Audio non avviabile.", Toast.LENGTH_LONG).show();
                    }
                }
                render();
            }).show();
    }

    private void chooseWavExport() {
        final String[] names = {"Brown Noise", "Pink Noise", "White Noise"};
        new AlertDialog.Builder(this).setTitle("Esporta una traccia WAV")
            .setItems(names, (dialog, which) -> {
                pendingExportColor = which;
                Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                create.addCategory(Intent.CATEGORY_OPENABLE);
                create.setType("audio/wav");
                create.putExtra(Intent.EXTRA_TITLE, "AmpyFocus-" + names[which].replace(' ', '-')
                        + "-48kHz-24bit.wav");
                startActivityForResult(create, AUDIO_WAV_REQUEST);
            }).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != AUDIO_WAV_REQUEST || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        int color = pendingExportColor;
        Toast.makeText(this, "Generazione WAV in corso…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean succeeded = false;
            try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out != null) {
                    WavExporter.write(out, color);
                    succeeded = true;
                }
            } catch (java.io.IOException | RuntimeException ignored) { }
            final boolean ok = succeeded;
            runOnUiThread(() -> Toast.makeText(this,
                    ok ? "WAV esportato: 48 kHz / PCM 24 bit, 60 s."
                       : "Impossibile completare l'esportazione WAV.",
                    Toast.LENGTH_LONG).show());
        }, "AmpyFocus-WAV-export").start();
    }

    private void callEmergency() {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")));
        } catch (RuntimeException e) {
            Toast.makeText(this, "Apri il dialer di emergenza del dispositivo.", Toast.LENGTH_LONG).show();
        }
    }


    /** Essential launcher icons intentionally include phone, clock and a few user-permitted apps. */
    private void showEssentialTiles() {
        addButton("Telefono · chiamate", false, () -> {
            try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))); }
            catch (RuntimeException e) { callEmergency(); }
        });
        addButton("Sveglia / orologio", false, () -> {
            try {
                startActivity(new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS));
            } catch (RuntimeException e) {
                Toast.makeText(this, "App orologio non disponibile.", Toast.LENGTH_SHORT).show();
            }
        });
        int count = 0;
        Set<String> blocked = FocusManager.currentlyBlocked(this);
        boolean inFocus = FocusManager.isWanted(this);
        Set<String> essentials = FocusStore.prefs(this).getStringSet("essential_packages",
                java.util.Collections.emptySet());
        for (FocusPackageCatalog.Entry entry : FocusPackageCatalog.list(this)) {
            if (blocked.contains(entry.pkg)) continue;
            if (inFocus && !essentials.contains(entry.pkg)) continue;
            if (count++ >= 3) break;
            addButton(entry.label, false, () -> {
                Intent launch = getPackageManager().getLaunchIntentForPackage(entry.pkg);
                if (launch != null) {
                    try { startActivity(launch); }
                    catch (RuntimeException e) {
                        Toast.makeText(this, "App non avviabile.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }


    private void showLaunchMetrics() {
        if (!UsageQuota.hasAccess(this)) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            return;
        }
        java.util.Map<String, Integer> counts = LaunchMetrics.today(this);
        java.util.List<FocusPackageCatalog.Entry> entries = FocusPackageCatalog.list(this);
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (FocusPackageCatalog.Entry e : entries) {
            Integer count = counts.get(e.pkg);
            if (count != null && count > 0) lines.add(e.label + " — " + count);
        }
        java.util.Collections.sort(lines);
        new AlertDialog.Builder(this).setTitle("Aperture app di oggi")
            .setMessage(lines.isEmpty() ? "Non sono presenti dati di avvio oggi."
                : android.text.TextUtils.join("\\n", lines)
                + "\\n\\nQuesti eventi sono aperture effettive, non tentativi bloccati.")
            .setPositiveButton("Chiudi", null).show();
    }

    private void toggleQuota() {
        if (!UsageQuota.hasAccess(this)) {
            new AlertDialog.Builder(this).setTitle("Permesso utilizzo app")
                .setMessage("Per calcolare il consumo giornaliero serve il permesso 'Accesso ai dati di utilizzo'. Le statistiche rimangono locali; il controllo non è al secondo.")
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Apri impostazioni", (d, n) ->
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))).show();
            return;
        }
        boolean old = FocusStore.prefs(this).getBoolean("quota_enabled", false);
        FocusStore.prefs(this).edit().putBoolean("quota_enabled", !old).apply();
        FocusManager.refresh(this);
        FocusScheduler.rearm(this);
        render();
    }

    private void configureQuota() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(FocusStore.prefs(this).getInt("quota_minutes", 15)));
        new AlertDialog.Builder(this).setTitle("Minuti massimi per ogni app scelta")
            .setMessage("Da 1 a 240 minuti al giorno; blocco approssimativo verificato periodicamente. Le app selezionate vengono comunque bloccate subito durante Focus.")
            .setView(input).setNegativeButton("Annulla", null)
            .setPositiveButton("Salva", (d, n) -> {
                try {
                    int minutes = Integer.parseInt(input.getText().toString().trim());
                    if (minutes < 1 || minutes > 240) throw new NumberFormatException();
                    FocusStore.prefs(this).edit().putInt("quota_minutes", minutes).apply();
                    FocusManager.refresh(this);
                    FocusScheduler.rearm(this);
                    render();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Inserisci 1–240 minuti.", Toast.LENGTH_LONG).show();
                }
            }).show();
    }

    private void toggleSilence() {
        if (!SilenceController.hasAccess(this)) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            return;
        }
        boolean old = FocusStore.prefs(this).getBoolean("silence_enabled", false);
        FocusStore.prefs(this).edit().putBoolean("silence_enabled", !old).apply();
        FocusManager.refresh(this);
        render();
    }

    private void toggleFamilyDns() {
        boolean active = FocusStore.prefs(this).getBoolean("family_dns_enabled", false);
        if (active) {
            FocusStore.prefs(this).edit().putBoolean("family_dns_enabled", false).apply();
            FocusManager.refresh(this);
            render();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Filtro DNS famiglia (facoltativo)")
            .setMessage("Durante Focus utilizza Cloudflare Family via DNS-over-TLS. Cloudflare riceve le richieste DNS. Non blocca necessariamente app, DNS integrati, VPN o tutte le immagini esplicite. Nella variante Extreme è richiesta un'integrazione AmpybiOS. Continuare?")
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Attiva", (d, n) -> {
                FocusStore.prefs(this).edit().putBoolean("family_dns_enabled", true).apply();
                FocusManager.refresh(this);
                render();
            }).show();
    }

    private void editDomains() {
        EditText domains = new EditText(this);
        domains.setMinLines(3);
        domains.setText(FocusStore.prefs(this).getString("custom_domains", ""));
        new AlertDialog.Builder(this).setTitle("Domini da bloccare: Extreme")
            .setMessage("Uno per riga o separati da virgola. Questi domini verranno inviati al FocusBridge AmpybiOS v2: NON sono filtrati dall'edizione Device Owner.")
            .setView(domains).setNegativeButton("Annulla", null)
            .setPositiveButton("Salva", (d, n) -> {
                Set<String> normalized = DomainRules.parse(domains.getText().toString());
                String saved = android.text.TextUtils.join(", ", normalized);
                FocusStore.prefs(this).edit().putString("custom_domains", saved).apply();
                FocusManager.refresh(this);
                render();
            }).show();
    }

    private void configureGeo() {
        final String[] options = {
            "Usa posizione corrente come centro",
            "Raggio in metri",
            "Raggio in chilometri",
            "Attiva blocco DENTRO la zona",
            "Attiva blocco FUORI dalla zona",
            "Spegni geofence",
            "Concedi posizione in background",
            "Elimina coordinate salvate"
        };
        new AlertDialog.Builder(this).setTitle("GPS + localizzazione rete cellulare / Wi-Fi")
                .setMessage("La posizione di rete è stimata: con raggi piccoli potrebbe essere imprecisa. Quando la posizione è incerta o scaduta il solo geofence non blocca nulla.")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: captureGeoCenter(); break;
                        case 1: configureRadius(false); break;
                        case 2: configureRadius(true); break;
                        case 3:
                        case 4:
                            if (!FocusStore.prefs(this).getBoolean("geo_center_configured", false)) {
                                Toast.makeText(this, "Prima salva il centro della zona.", Toast.LENGTH_LONG).show();
                                captureGeoCenter();
                                return;
                            }
                            if (!GeoFenceController.hasForegroundPermission(this)) {
                                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION}, GEO_PERMISSION_REQUEST);
                                return;
                            }
                            if (!GeoFenceController.hasBackgroundPermission(this)) {
                                Toast.makeText(this, "Concedi 'Consenti sempre' nelle impostazioni dell'app.",
                                        Toast.LENGTH_LONG).show();
                                showLocationAppSettings();
                                return;
                            }
                            FocusStore.prefs(this).edit().putBoolean("geo_enabled", true)
                                    .putInt("geo_mode", which == 3 ? 1 : 2).apply();
                            GeoFenceController.register(this);
                            FocusManager.refresh(this);
                            render();
                            break;
                        case 5:
                            FocusStore.prefs(this).edit().putBoolean("geo_enabled", false).apply();
                            GeoFenceController.register(this);
                            FocusManager.refresh(this);
                            render();
                            break;
                        case 6: showLocationAppSettings(); break;
                        case 7:
                            FocusStore.clearGeo(this);
                            GeoFenceController.register(this);
                            FocusManager.refresh(this);
                            render();
                            break;
                        default: break;
                    }
                }).show();
    }

    private void configureRadius(boolean kilometers) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(kilometers
                ? String.valueOf(FocusStore.geoRadius(this) / 1000d)
                : String.valueOf(FocusStore.geoRadius(this)));
        new AlertDialog.Builder(this).setTitle(kilometers ? "Raggio (km)" : "Raggio (metri)")
            .setMessage("Distanza ammessa: da 50 metri a 50 km. La precisione GPS/rete deve essere sufficiente per una decisione affidabile.")
            .setView(input).setNegativeButton("Annulla", null)
            .setPositiveButton("Salva", (d, n) -> {
                try {
                    double amount = Double.parseDouble(input.getText().toString().trim().replace(',', '.'));
                    double meters = kilometers ? amount * 1000d : amount;
                    if (!Double.isFinite(meters) || meters < 50 || meters > 50000) throw new NumberFormatException();
                    FocusStore.prefs(this).edit().putInt("geo_radius_m", (int) Math.round(meters))
                            .putString("geo_state", "UNKNOWN").putLong("geo_checked_wall", 0L).apply();
                    GeoFenceController.register(this);
                    FocusManager.refresh(this);
                    render();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Valore non valido (50 m–50 km).", Toast.LENGTH_LONG).show();
                }
            }).show();
    }

    private void captureGeoCenter() {
        if (!GeoFenceController.hasForegroundPermission(this)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, GEO_PERMISSION_REQUEST);
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;
        try {
            String provider = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, getMainExecutor(), location -> saveGeoCenter(location));
            } else {
                saveGeoCenter(lm.getLastKnownLocation(provider));
            }
        } catch (SecurityException | IllegalArgumentException e) {
            Toast.makeText(this, "Posizione non disponibile.", Toast.LENGTH_LONG).show();
        }
    }

    private void saveGeoCenter(Location location) {
        if (location == null || !location.hasAccuracy()) {
            Toast.makeText(this, "Nessun fix GPS/rete: attiva la posizione e riprova.", Toast.LENGTH_LONG).show();
            return;
        }
        FocusStore.setGeoCenter(this, location.getLatitude(), location.getLongitude());
        Toast.makeText(this, "Centro memorizzato localmente · precisione ±"
                + Math.round(location.getAccuracy()) + " m", Toast.LENGTH_LONG).show();
        render();
    }

    private void showLocationAppSettings() {
        new AlertDialog.Builder(this).setTitle("Accesso alla posizione in background")
            .setMessage("Concedi 'Consenti sempre' nelle impostazioni Android per attivare le zone anche a schermo spento. Non inviamo coordinate a server.")
            .setNegativeButton("Annulla", null)
            .setPositiveButton("Apri impostazioni", (d, n) -> {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }).show();
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
