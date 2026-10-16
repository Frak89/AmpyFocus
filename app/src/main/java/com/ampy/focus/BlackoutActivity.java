package com.ampy.focus;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * OLED-first immersive focus. Kiosk restrictions only work with provisioned
 * Device Owner. The low-brightness mode is NOT a privileged true AOD service.
 */
public final class BlackoutActivity extends Activity implements SensorEventListener {
    private static final int BG = Color.BLACK;
    private static final int WHITE = 0xFFE8F2F0;
    private static final int CYAN = 0xFF81E0D5;
    private static final int VIOLET = 0xFF8E52F4;
    private static final int BLUE = 0xFF12245D;
    private static final long BACKUP_HOLD_MS = 8000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ShakeSequence shake = new ShakeSequence();
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private FrameLayout root;
    private LinearLayout body;
    private TextView date, clock, battery, remaining, processSummary;
    private LinearLayout controls;
    private FrameLayout processBox;
    private CyberBorderView cyberBorder;
    private GlitchLines glitch;
    private ValueAnimator glitchAnimator;
    private boolean waitingForAnswer, leaving, sleeping, sensorRegistered;
    private Runnable glowHold, rescueHold;
    private float originalBrightness;
    private long lastClockMinute = -1, lastBatteryMinute = -1;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        originalBrightness = getWindow().getAttributes().screenBrightness;
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        buildScreen();
        hideSystemBars();
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setFontFeatureSettings("tnum");
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        return t;
    }

    private Button action(String s, Runnable runnable) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(12f);
        b.setTextColor(WHITE);
        b.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xFF091016);
        shape.setStroke(dp(1), BLUE);
        shape.setCornerRadius(dp(13));
        b.setBackground(shape);
        b.setOnClickListener(v -> runnable.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        controls.addView(b, lp);
        return b;
    }

    private void buildScreen() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);

        body = new LinearLayout(this);
        body.setGravity(Gravity.CENTER);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(body, new FrameLayout.LayoutParams(-1, -1));

        date = text("--", 15, WHITE);
        clock = text("--:--", 65, WHITE);
        battery = text("--%", 13, CYAN);
        remaining = text("00:00:00", 28, CYAN);
        LinearLayout.LayoutParams datelp = new LinearLayout.LayoutParams(-1, -2);
        datelp.bottomMargin = dp(9);
        body.addView(date, datelp);
        body.addView(clock);
        LinearLayout.LayoutParams battlp = new LinearLayout.LayoutParams(-1, -2);
        battlp.topMargin = dp(9);
        body.addView(battery, battlp);

        LinearLayout.LayoutParams timelp = new LinearLayout.LayoutParams(-1, -2);
        timelp.topMargin = dp(22);
        body.addView(remaining, timelp);

        processBox = new FrameLayout(this);
        cyberBorder = new CyberBorderView(this);
        processBox.addView(cyberBorder, new FrameLayout.LayoutParams(-1, -1));
        processSummary = text("PROCESSI · —", 12, WHITE);
        processBox.addView(processSummary, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams boxlp = new LinearLayout.LayoutParams(-1, dp(68));
        boxlp.topMargin = dp(22);
        body.addView(processBox, boxlp);

        controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ctrlp = new LinearLayout.LayoutParams(-1, -2);
        ctrlp.topMargin = dp(18);
        body.addView(controls, ctrlp);

        action("♪ Brown", () -> toggleNoise(AudioSynthesis.BROWN));
        action("♪ White", () -> toggleNoise(AudioSynthesis.WHITE));
        action("♪ Pink", () -> toggleNoise(AudioSynthesis.PINK));
        LinearLayout sleepRow = new LinearLayout(this);
        sleepRow.setOrientation(LinearLayout.HORIZONTAL);
        sleepRow.setGravity(Gravity.CENTER);
        body.addView(sleepRow, new LinearLayout.LayoutParams(-1, dp(66)));
        Button sleepButton = new Button(this);
        sleepButton.setText("⏻  SLEEP ON / OFF");
        sleepButton.setTextSize(12);
        sleepButton.setAllCaps(false);
        sleepButton.setTextColor(CYAN);
        sleepButton.setBackgroundColor(Color.TRANSPARENT);
        sleepButton.setOnClickListener(v -> setSleeping(!sleeping));
        sleepRow.addView(sleepButton);
        sleepRow.setTag("sleepRow");

        // The timer is still touchable in sleep for waking or a silent fallback.
        View.OnTouchListener indicator = (v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    glowHold = () -> playGlitch(false);
                    rescueHold = this::showExitConfirmation;
                    handler.postDelayed(glowHold, 750L);
                    handler.postDelayed(rescueHold, BACKUP_HOLD_MS);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (sleeping && rescueHold != null) {
                        // Light touch wakes, long hold confirms exit.
                        setSleeping(false);
                    }
                case MotionEvent.ACTION_CANCEL:
                    cancelIndicatorHold();
                    return true;
                default:
                    return true;
            }
        };
        for (TextView t : new TextView[]{date, clock, battery, remaining}) {
            t.setOnTouchListener(indicator);
        }

        glitch = new GlitchLines(this);
        glitch.setVisibility(View.GONE);
        glitch.setClickable(false);
        root.addView(glitch, new FrameLayout.LayoutParams(-1, -1));
        updateTimeAndBattery(true);
        showMode();
    }

    private void cancelIndicatorHold() {
        if (glowHold != null) handler.removeCallbacks(glowHold);
        if (rescueHold != null) handler.removeCallbacks(rescueHold);
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private void setSleeping(boolean value) {
        sleeping = value;
        // Extra-dim is per-window and needs no special Android privilege.
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = sleeping ? 0.01f : originalBrightness;
        getWindow().setAttributes(lp);
        if (sleeping) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        showMode();
        if (cyberBorder != null) cyberBorder.setAnimating(!sleeping);
        // Retain only the minimum lit text pixels in Sleep Mode.
        if (!sleeping) updateTimeAndBattery(true);
    }

    private void showMode() {
        date.setVisibility(sleeping ? View.GONE : View.VISIBLE);
        clock.setVisibility(sleeping ? View.GONE : View.VISIBLE);
        battery.setVisibility(sleeping ? View.GONE : View.VISIBLE);
        processBox.setVisibility(sleeping ? View.GONE : View.VISIBLE);
        controls.setVisibility(sleeping ? View.GONE : View.VISIBLE);
        View sleepRow = body.findViewWithTag("sleepRow");
        if (sleepRow != null) sleepRow.setVisibility(sleeping ? View.GONE : View.VISIBLE);
        remaining.setTextSize(sleeping ? 22 : 28);
        remaining.setTextColor(sleeping ? 0xFF545E69 : CYAN);
        // In Sleep the single countdown has both tap-to-wake and long-hold exit.
        remaining.setContentDescription(sleeping
            ? "Sleep: tocca per riattivare, tieni premuto otto secondi per uscire"
            : "Tempo rimanente: tieni premuto otto secondi per uscire");
    }

    private void toggleNoise(int color) {
        if (BrownNoiseService.isPlaying && BrownNoiseService.selectedColor == color) {
            stopService(new Intent(this, BrownNoiseService.class));
            return;
        }
        try {
            Intent service = new Intent(this, BrownNoiseService.class)
                    .putExtra("color", color);
            startForegroundService(service);
        } catch (RuntimeException ignored) {
            new AlertDialog.Builder(this).setTitle("Audio non disponibile")
                    .setMessage("Verifica i permessi e lo stato del dispositivo.")
                    .setPositiveButton("OK", null).show();
        }
    }

    private static String formatRemaining(long time) {
        long s = Math.max(0, time / 1000L);
        return String.format(Locale.ITALY, "%02d:%02d:%02d",
                s / 3600L, (s % 3600L) / 60L, s % 60L);
    }

    private String remainingLabel() {
        if (GeoFenceController.shouldTrigger(this)
                && FocusManager.manualUntil(this) <= System.currentTimeMillis()) {
            return "ZONA FOCUS";
        }
        long until = FocusManager.expectedEndMillis(this);
        return until > System.currentTimeMillis()
                ? formatRemaining(until - System.currentTimeMillis())
                : "00:00:00";
    }

    private void updateTimeAndBattery(boolean force) {
        long now = System.currentTimeMillis();
        remaining.setText(remainingLabel());
        long minute = now / 60000L;
        if (!sleeping && (force || lastClockMinute != minute)) {
            Date d = new Date(now);
            date.setText(new SimpleDateFormat("EEEE d MMMM yyyy", Locale.ITALY).format(d));
            clock.setText(new SimpleDateFormat("HH:mm", Locale.ITALY).format(d));
            lastClockMinute = minute;
        }
        if (!sleeping && (force || lastBatteryMinute != minute)) {
            Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int value = -1;
            if (i != null) {
                int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (level >= 0 && scale > 0) value = Math.round(level * 100f / scale);
            }
            battery.setText(value >= 0 ? "▰ " + value + "%" : "▰ --%");
            processSummary.setText("PROCESSI / POLICY: " + (FocusManager.isEnforced(this) ? "ATTIVA" : "NON OPERATIVA")
                    + "   •   APP LIMITATE: " + FocusManager.currentlyBlocked(this).size()
                    + "   •   " + (BlackoutKiosk.supported(this) ? "DPC" : "NO DPC"));
            lastBatteryMinute = minute;
        }
        if (sleeping && (force || minute != lastClockMinute)) {
            // Slight drift every minute to mitigate prolonged OLED burn-in.
            remaining.setTranslationX(dp((int) ((minute % 5) - 2) * 5));
            remaining.setTranslationY(dp((int) (((minute / 5) % 5) - 2) * 5));
            lastClockMinute = minute;
        } else if (!sleeping && (force
                || remaining.getTranslationX() != 0 || remaining.getTranslationY() != 0)) {
            remaining.setTranslationX(0);
            remaining.setTranslationY(0);
        }
    }

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (leaving) return;
            if (!FocusManager.isWanted(BlackoutActivity.this)) {
                FocusManager.refresh(BlackoutActivity.this);
                finishWithoutEnding();
                return;
            }
            updateTimeAndBattery(false);
            long align = 1000L - (SystemClock.uptimeMillis() % 1000L);
            handler.postDelayed(this, align);
        }
    };

    @Override protected void onResume() {
        super.onResume();
        if (!FocusManager.isWanted(this)) {
            finish();
            return;
        }
        hideSystemBars();
        if (sensorManager != null && accelerometer != null) {
            sensorRegistered = sensorManager.registerListener(this, accelerometer,
                    sleeping ? SensorManager.SENSOR_DELAY_NORMAL : SensorManager.SENSOR_DELAY_GAME);
        }
        if (BlackoutKiosk.supported(this)) BlackoutKiosk.enter(this);
        cyberBorder.setAnimating(!sleeping);
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
    }

    @Override protected void onPause() {
        cancelIndicatorHold();
        handler.removeCallbacks(clockTick);
        if (cyberBorder != null) cyberBorder.setAnimating(false);
        if (sensorManager != null && sensorRegistered) {
            sensorManager.unregisterListener(this);
            sensorRegistered = false;
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        cancelIndicatorHold();
        handler.removeCallbacks(clockTick);
        if (glitchAnimator != null) glitchAnimator.cancel();
        if (cyberBorder != null) cyberBorder.setAnimating(false);
        if (isFinishing()) BlackoutKiosk.exit(this);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }

    @Override public void onSensorChanged(SensorEvent e) {
        if (leaving || waitingForAnswer || e.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        if (e.values.length < 3) return;
        if (shake.sample(e.timestamp / 1000000L, e.values[0], e.values[1], e.values[2])) {
            vibrateZigZag();
            playGlitch(true);
            handler.postDelayed(this::showExitConfirmation, 610L);
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int accuracy) { }

    private void vibrateZigZag() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(
                    new long[]{0, 45, 30, 70, 35, 95, 45, 105},
                    new int[]{0, 170, 0, 250, 0, 150, 0, 240}, -1));
            }
        } catch (RuntimeException ignored) { }
    }

    private void playGlitch(boolean shakeTriggered) {
        if (sleeping) setSleeping(false);
        if (glitchAnimator != null) glitchAnimator.cancel();
        glitch.setVisibility(View.VISIBLE);
        glitchAnimator = ValueAnimator.ofInt(0, 12);
        glitchAnimator.setDuration(shakeTriggered ? 560 : 360);
        glitchAnimator.addUpdateListener(anim -> {
            int frame = (int) anim.getAnimatedValue();
            float offset = (frame % 2 == 0 ? 1f : -1f)
                    * dp(shakeTriggered ? 20 : 8) * (1f - frame / 13f);
            body.setTranslationX(offset);
            clock.setTextColor(frame % 3 == 0 ? VIOLET : frame % 2 == 0 ? CYAN : WHITE);
            glitch.setPhase(frame);
            if (frame >= 12) {
                body.setTranslationX(0);
                clock.setTextColor(WHITE);
                glitch.setVisibility(View.GONE);
            }
        });
        glitchAnimator.start();
    }

    private void showExitConfirmation() {
        if (leaving || waitingForAnswer) return;
        waitingForAnswer = true;
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Sei sicuro di uscire dall'applicazione?")
                .setMessage("")
                .setNegativeButton("No", (d, which) -> waitingForAnswer = false)
                .setPositiveButton("Sì", (d, which) -> {
                    leaving = true;
                    FocusManager.endAll(this);
                    BlackoutKiosk.exit(this);
                    finish();
                })
                .create();
        final Runnable[] update = new Runnable[1];
        update[0] = () -> {
            if (!dialog.isShowing() || leaving) return;
            dialog.setMessage("Tempo rimanente: " + remainingLabel()
                    + "\\n\\nLa sessione terminerà soltanto se scegli Sì.");
            handler.postDelayed(update[0], 1000L);
        };
        dialog.setOnDismissListener(d -> {
            waitingForAnswer = false;
            handler.removeCallbacks(update[0]);
        });
        dialog.show();
        handler.post(update[0]);
    }

    private void finishWithoutEnding() {
        if (leaving) return;
        leaving = true;
        BlackoutKiosk.exit(this);
        finish();
    }

    @Override public void onBackPressed() {
        // Normal Back does not exit. System emergency and Android recovery
        // remain outside the app's control on every device.
        playGlitch(false);
    }

    /** Alternating violet/blue segments moving along a rounded cyberpunk border. */
    private static final class CyberBorderView extends View {
        private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Path segment = new Path();
        private final PathMeasure measure = new PathMeasure();
        private final Handler borderHandler = new Handler(Looper.getMainLooper());
        private boolean running;
        private final Runnable repaint = new Runnable() {
            @Override public void run() {
                if (running && getVisibility() == VISIBLE) {
                    invalidate();
                    borderHandler.postDelayed(this, 80L); // 12.5 fps, not 120 Hz
                }
            }
        };

        CyberBorderView(Context ctx) {
            super(ctx);
            pen.setStyle(Paint.Style.STROKE);
            pen.setStrokeWidth(getResources().getDisplayMetrics().density * 2f);
            pen.setStrokeCap(Paint.Cap.ROUND);
        }

        void setAnimating(boolean enabled) {
            if (running == enabled) return;
            running = enabled;
            borderHandler.removeCallbacks(repaint);
            if (running) borderHandler.post(repaint);
        }

        @Override protected void onDraw(Canvas canvas) {
            if (getWidth() <= 0 || getHeight() <= 0) return;
            float d = getResources().getDisplayMetrics().density;
            path.reset();
            path.addRoundRect(new RectF(3 * d, 3 * d,
                    getWidth() - 3 * d, getHeight() - 3 * d),
                    14 * d, 14 * d, Path.Direction.CW);
            measure.setPath(path, true);
            float length = measure.getLength();
            float scroll = (SystemClock.uptimeMillis() % 3600L) / 3600f * length;
            for (int i = 0; i < 8; i++) {
                float start = (scroll + i * length / 8) % length;
                float end = start + length / 11f;
                pen.setColor(i % 2 == 0 ? VIOLET : BLUE);
                segment.reset();
                if (end <= length) {
                    measure.getSegment(start, end, segment, true);
                } else {
                    measure.getSegment(start, length, segment, true);
                    measure.getSegment(0, end - length, segment, true);
                }
                canvas.drawPath(segment, pen);
            }
        }

        @Override protected void onDetachedFromWindow() {
            setAnimating(false);
            super.onDetachedFromWindow();
        }
    }

    private static final class GlitchLines extends View {
        private final Paint pen = new Paint();
        private final Random random = new Random();
        private int phase;
        GlitchLines(Context ctx) { super(ctx); }
        void setPhase(int value) { phase = value; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            if (phase >= 12) return;
            for (int i = 0; i < 7; i++) {
                int y = random.nextInt(Math.max(1, getHeight()));
                int x = random.nextInt(Math.max(1, getWidth()));
                pen.setColor(i % 2 == 0 ? 0xA08052FF : 0xBB142B74);
                c.drawRect(Math.max(0, x - getWidth() / 6), y,
                        Math.min(getWidth(), x + getWidth() / 5), y + 3, pen);
            }
        }
    }
}
