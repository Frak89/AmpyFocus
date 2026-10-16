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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * Self-contained full-screen black focus surface, not a SYSTEM_ALERT_WINDOW.
 * Outside provisioned Device Owner lock-task mode, Android system navigation
 * can still override immersive mode. Strong alternating shake is the intended
 * exit; an eight-second clock hold permits emergency recovery.
 */
public final class BlackoutActivity extends Activity implements SensorEventListener {
    private static final int WHITE = 0xFFE6ECE9;
    private static final int CYAN = 0xFF83E8D7;
    private static final long EMERGENCY_HOLD_MS = 8000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ShakeSequence shake = new ShakeSequence();
    private SensorManager sensors;
    private Sensor accelerometer;
    private FrameLayout base;
    private LinearLayout face;
    private TextView clock, date, battery;
    private GlitchLines glitch;
    private boolean exiting;
    private boolean showingRecovery;
    private ValueAnimator glitchAnimator;
    private Runnable previewHold, recoveryHold;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }
        buildDisplay();
        sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        enableImmersion();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private TextView label(String value, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setFontFeatureSettings("tnum");
        t.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        return t;
    }

    private void buildDisplay() {
        base = new FrameLayout(this);
        base.setBackgroundColor(Color.BLACK);
        base.setFocusableInTouchMode(true);
        setContentView(base);

        face = new LinearLayout(this);
        face.setGravity(Gravity.CENTER);
        face.setOrientation(LinearLayout.VERTICAL);
        face.setPadding(dp(22), 0, dp(22), 0);
        base.addView(face, new FrameLayout.LayoutParams(-1, -1));

        // Only DATE, TIME and BATTERY exist in this mode: no app icons or buttons.
        date = label("--", 18, WHITE);
        clock = label("--:--", 77, WHITE);
        battery = label("▰ --%", 14, CYAN);

        LinearLayout.LayoutParams dateLayout = new LinearLayout.LayoutParams(-1, -2);
        dateLayout.bottomMargin = dp(8);
        face.addView(date, dateLayout);
        face.addView(clock, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams batteryLayout = new LinearLayout.LayoutParams(-1, -2);
        batteryLayout.topMargin = dp(12);
        face.addView(battery, batteryLayout);

        View.OnTouchListener deliberateHold = (v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    previewHold = () -> playGlitch(false);
                    recoveryHold = this::showRecovery;
                    handler.postDelayed(previewHold, 700L);
                    handler.postDelayed(recoveryHold, EMERGENCY_HOLD_MS);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    cancelHold();
                    return true;
                default:
                    return true;
            }
        };
        date.setOnTouchListener(deliberateHold);
        clock.setOnTouchListener(deliberateHold);
        battery.setOnTouchListener(deliberateHold);

        glitch = new GlitchLines(this);
        glitch.setVisibility(View.GONE);
        base.addView(glitch, new FrameLayout.LayoutParams(-1, -1));
        // Do not intercept emergency long-press interactions.
        glitch.setClickable(false);
        glitch.setFocusable(false);
        updateDisplay();
    }

    private void cancelHold() {
        if (previewHold != null) handler.removeCallbacks(previewHold);
        if (recoveryHold != null) handler.removeCallbacks(recoveryHold);
    }

    private void enableImmersion() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            base.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!exiting) {
                if (!FocusManager.isWanted(BlackoutActivity.this)) {
                    endWithoutCancelingFocus();
                    return;
                }
                updateDisplay();
                handler.postDelayed(this, 1000L);
            }
        }
    };

    private void updateDisplay() {
        Date now = new Date();
        date.setText(new SimpleDateFormat("EEEE d MMMM yyyy", Locale.ITALY).format(now));
        clock.setText(new SimpleDateFormat("HH:mm", Locale.ITALY).format(now));
        Intent state = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int percent = -1;
        if (state != null) {
            int level = state.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = state.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) percent = Math.round(level * 100f / scale);
        }
        battery.setText(percent >= 0 ? "▰ " + percent + "%" : "▰ --%");
    }

    @Override protected void onResume() {
        super.onResume();
        if (!FocusManager.isWanted(this)) { finish(); return; }
        enableImmersion();
        if (sensors != null && accelerometer != null) {
            sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        // This is truly enforced only on a provisioned Device Owner with opt-in kiosk.
        BlackoutKiosk.enter(this);
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override protected void onPause() {
        cancelHold();
        handler.removeCallbacks(tick);
        if (sensors != null) sensors.unregisterListener(this);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (glitchAnimator != null) glitchAnimator.cancel();
        if (isFinishing()) BlackoutKiosk.exit(this);
        super.onDestroy();
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (exiting || showingRecovery || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        if (event.values.length < 3) return;
        if (shake.sample(event.timestamp / 1_000_000L,
                event.values[0], event.values[1], event.values[2])) {
            exiting = true;
            vibrateZigZag();
            playGlitch(true);
            handler.postDelayed(() -> {
                FocusManager.endAll(this);
                BlackoutKiosk.exit(this);
                finish();
            }, 780L);
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private void vibrateZigZag() {
        Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vib == null || !vib.hasVibrator()) return;
        try {
            long[] durations = {0L, 48L, 35L, 70L, 35L, 100L, 40L, 110L};
            int[] amplitudes = {0, 180, 0, 255, 0, 190, 0, 245};
            if (Build.VERSION.SDK_INT >= 26) {
                vib.vibrate(VibrationEffect.createWaveform(durations, amplitudes, -1));
            }
        } catch (RuntimeException ignored) { }
    }

    private void playGlitch(boolean exitingShake) {
        if (glitchAnimator != null) glitchAnimator.cancel();
        glitch.setVisibility(View.VISIBLE);
        final float distance = dp(exitingShake ? 22 : 9);
        glitchAnimator = ValueAnimator.ofInt(0, 12);
        glitchAnimator.setDuration(exitingShake ? 660L : 480L);
        glitchAnimator.addUpdateListener(animation -> {
            int frame = (Integer) animation.getAnimatedValue();
            float offset = (frame % 2 == 0 ? 1f : -1f)
                    * distance * (1f - frame / 13f);
            face.setTranslationX(offset);
            clock.setTextColor(frame % 3 == 0 ? 0xFFF6C879
                    : frame % 2 == 0 ? CYAN : WHITE);
            date.setTextColor(frame % 2 == 0 ? WHITE : CYAN);
            glitch.setPhase(frame);
            if (frame >= 12) {
                face.setTranslationX(0f);
                clock.setTextColor(WHITE);
                date.setTextColor(WHITE);
                glitch.setVisibility(View.GONE);
            }
        });
        glitchAnimator.start();
    }

    private void showRecovery() {
        if (showingRecovery || exiting) return;
        showingRecovery = true;
        playGlitch(false);
        new AlertDialog.Builder(this)
                .setTitle("Recupero AmpyFocus")
                .setMessage("Sblocco di sicurezza: disponibile se il sensore non funziona. "
                        + "La procedura principale resta una forte scossa del telefono.")
                .setNeutralButton("Emergenza 112", (d, n) -> {
                    showingRecovery = false;
                    try {
                        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")));
                    } catch (RuntimeException ignored) { }
                })
                .setNegativeButton("Continua Focus", (d, n) -> showingRecovery = false)
                .setPositiveButton("Termina Focus", (d, n) -> {
                    exiting = true;
                    FocusManager.endAll(this);
                    BlackoutKiosk.exit(this);
                    finish();
                })
                .setOnCancelListener(d -> showingRecovery = false)
                .show();
    }

    private void endWithoutCancelingFocus() {
        if (exiting) return;
        exiting = true;
        BlackoutKiosk.exit(this);
        finish();
    }

    @Override public void onBackPressed() {
        // Intentionally no Back exit. Android system gestures may still escape
        // immersive mode on non-Device-Owner Android; never claim otherwise.
        playGlitch(false);
    }

    /** Faint transient horizontal scanline artifacts. Nothing touches other apps. */
    private static final class GlitchLines extends View {
        private final Paint pen = new Paint();
        private final Random random = new Random();
        private int phase;

        GlitchLines(Context context) { super(context); }
        void setPhase(int phase) { this.phase = phase; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            if (phase >= 12) return;
            int width = getWidth(), height = getHeight();
            for (int i = 0; i < 9; i++) {
                int y = random.nextInt(Math.max(1, height));
                int x = random.nextInt(Math.max(1, width));
                pen.setColor(i % 2 == 0 ? 0x997DE3D0 : 0x88CC824F);
                canvas.drawRect(Math.max(0, x - width / 5), y,
                        Math.min(width, x + width / 4), y + 2 + random.nextInt(5), pen);
            }
        }
    }
}
