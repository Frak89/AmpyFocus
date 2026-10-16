package com.ampy.focus;

/**
 * Detect a deliberately strong left-right shake, not ordinary walking.
 * Consumes TYPE_ACCELEROMETER including gravity, sampled at SENSOR_DELAY_GAME.
 * Four alternating, high-pass peaks must occur within two seconds.
 * Zero Android dependencies; unit-testable.
 */
public final class ShakeSequence {
    private static final double SMOOTH = 0.82d;
    private static final double THRESHOLD = 14.0d; // m/s^2, gravity-compensated
    private static final long PEAK_GAP_MS = 110L;
    private static final long WINDOW_MS = 2100L;
    private static final long REFRACTORY_MS = 3000L;
    private double gx, gy, gz;
    private boolean initialized;
    private long firstPeakMs, lastPeakMs, lastTriggerMs = -REFRACTORY_MS;
    private int peakCount, lastDirection;

    public boolean sample(long timestampMs, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        if (!initialized) {
            gx = x; gy = y; gz = z;
            initialized = true;
            return false;
        }
        gx = SMOOTH * gx + (1d - SMOOTH) * x;
        gy = SMOOTH * gy + (1d - SMOOTH) * y;
        gz = SMOOTH * gz + (1d - SMOOTH) * z;
        double ax = x - gx, ay = y - gy, az = z - gz;
        double magnitude = Math.sqrt(ax * ax + ay * ay + az * az);
        if (magnitude < THRESHOLD || timestampMs - lastTriggerMs < REFRACTORY_MS) return false;
        if (timestampMs - lastPeakMs < PEAK_GAP_MS) return false;

        // Only count opposing peaks on the same dominant physical axis.
        double projection = Math.abs(ax) >= Math.abs(ay) && Math.abs(ax) >= Math.abs(az) ? ax
                : Math.abs(ay) >= Math.abs(az) ? ay : az;
        int direction = projection >= 0 ? 1 : -1;
        if (peakCount > 0 && direction == lastDirection) return false;

        if (peakCount == 0 || timestampMs - firstPeakMs > WINDOW_MS) {
            firstPeakMs = timestampMs;
            peakCount = 0;
        }
        peakCount++;
        lastDirection = direction;
        lastPeakMs = timestampMs;

        if (peakCount >= 4) {
            lastTriggerMs = timestampMs;
            peakCount = 0;
            lastDirection = 0;
            return true;
        }
        return false;
    }
}
