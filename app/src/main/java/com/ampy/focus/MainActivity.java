package com.ampy.focus;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
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
        if (FocusManager.isWanted(this) && FocusStore.prefs(this)
                .getBoolean("blackout_auto_enabled", false)) {
            startActivity(new Intent(this, BlackoutActivity.class));
        }
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
            addButton("⬛ SCHERMO NERO • ZERO DISTRAZIONI", true,
                    () -> startActivity(new Intent(this, BlackoutActivity.class)));
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
        addLabel("SCHERMO NERO AMPYBIOS", 12, ACCENT, true, 28);
        addButton("Blackout di prova • 1 minuto", true, () -> {
            FocusManager.startManual(this, 1);
            startActivity(new Intent(this, BlackoutActivity.class));
        });
        addButton(FocusStore.prefs(this).getBoolean("blackout_auto_enabled", false)
                ? "Blackout automatico: SÌ" : "Blackout automatico: NO", false, () -> {
            boolean old = FocusStore.prefs(this)
                    .getBoolean("blackout_auto_enabled", false);
            FocusStore.prefs(this).edit().putBoolean("blackout_auto_enabled", !old).apply();
            render();
        });
        addButton(FocusStore.prefs(this).getBoolean("blackout_kiosk_enabled", false)
                ? "Kiosk Device Owner: ATTIVO" : "Kiosk Device Owner: NON ATTIVO",
                false, () -> configureBlackoutKiosk());

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

        addLabel("KIOSK E AUTORIZZAZIONI", 12, ACCENT, true, 28);
        addLabel(deviceOwnerState(), 13, MUTED, false, 10);
        addButton("Richiedi Amministratore dispositivo Android", false,
                () -> requestDeviceAdmin());
        addButton("Configurazione Device Owner (telefono di test)", false,
                () -> explainOwnerProvisioning());
        addLabel("Lock Task si attiva soltanto con Device Owner; "
                + "l'amministratore ordinario NON concede questo ruolo.",
                12, MUTED, false, 12);
        addLabel("STATO DEL MOTORE", 12, ACCENT, true, 28);
        addLabel(EditionPolicy.create().availabilityMessage(this), 13, MUTED, false, 12);
        addLabel("v0.2: quote app approssimative (fino a 15 min di ritardo), DNS Family opzionale; GPS/rete dipende dai permessi e dalla precisione. Extreme richiede un bridge firmato.",
                12, MUTED, false, 20);
    }

    private void start(int minutes) {
        FocusManager.startManual(this, minutes);
        if (FocusStore.prefs(this).getBoolean("blackout_auto_enabled", false)) {
            startActivity(new Intent(this, BlackoutActivity.class));
        } else {
            render();
        }
    }

    private String deviceOwnerState() {
        DevicePolicyManager dpm = (DevicePolicyManager)
                getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null) return "Gestore policy Android non disponibile";
        if (dpm.isDeviceOwnerApp(getPackageName()))
            return "✓ DEVICE OWNER ATTIVO · Kiosk disponibile";
        boolean admin = dpm.isAdminActive(new ComponentName(this, FocusAdminReceiver.class));
        return admin ? "Amministratore attivo · NON Device Owner"
                : "Nessun ruolo Device Owner · kiosk non operativo";
    }

    private void requestDeviceAdmin() {
        DevicePolicyManager dpm = (DevicePolicyManager)
                getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null) return;
        if (dpm.isDeviceOwnerApp(getPackageName())) {
            Toast.makeText(this, "Device Owner già configurato.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Autorizzazione amministratore")
                .setMessage("Android può chiederti se vuoi attivare l'amministratore dispositivo. "
                        + "Questa autorizzazione NON consente da sola il kiosk Lock Task. "
                        + "Device Owner richiede provisioning Android Enterprise su dispositivo idoneo.")
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Apri richiesta Android", (d, n) -> {
                    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            new ComponentName(this, FocusAdminReceiver.class));
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "L'amministratore NON concede automaticamente Device Owner.");
                    try { startActivity(intent); }
                    catch (RuntimeException e) {
                        Toast.makeText(this, "Richiesta non supportata.", Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void explainOwnerProvisioning() {
        new AlertDialog.Builder(this)
            .setTitle("Come attivare Device Owner")
            .setMessage("Android NON permette di concedere Device Owner tramite "
                + "un semplice dialogo di permessi. Serve provisioning ufficiale, "
                + "generalmente su telefono appena inizializzato, con backup. "
                + "Su un dispositivo di test idoneo, con APK installato:\n\n"
                + "adb shell dpm set-device-owner "
                + getPackageName() + "/com.ampy.focus.FocusAdminReceiver\n\n"
                + "L'operazione può fallire sui telefoni già configurati. "
                + "Non usare il telefono principale per il primo esperimento.")
            .setPositiveButton("Capito", null).show();
    }

    private void configureBlackoutKiosk() {
        if (!BlackoutKiosk.supported(this)) {
            new AlertDialog.Builder(this).setTitle("Richiede Device Owner")
                .setMessage("Il kiosk ufficiale richiede Android Device Owner su un dispositivo di prova. "
                        + "Senza tale privilegio i gesti Android possono uscire dal fullscreen.")
                .setPositiveButton("Capito", null).show();
            return;
        }
        boolean old = FocusStore.prefs(this).getBoolean("blackout_kiosk_enabled", false);
        new AlertDialog.Builder(this).setTitle("Kiosk Android durante Blackout")
            .setMessage("In Device Owner limita Home e altre app con Lock Task. "
                    + "Esci con una sequenza di scosse forti alternate, oppure "
                    + "tieni premuto il timer per 8 secondi per lo sblocco di sicurezza. "
                    + "Non provare per la prima volta sul telefono principale.")
            .setNegativeButton("Annulla", null)
            .setPositiveButton(old ? "Disabilita" : "Abilita", (d, n) -> {
                FocusStore.prefs(this).edit().putBoolean("blackout_kiosk_enabled", !old).apply();
                render();
            }).show();
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

    private static String pretty(int minute) {
        return String.format(Locale.ITALY, "%02d:%02d", minute / 60, minute % 60);
    }

    private static String duration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(Locale.ITALY, "%02d:%02d:%02d",
                seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }
}
