package com.ampy.focus;

import java.util.Random;

/** Original real-time procedural noise (no loops, clipping or third-party assets). */
public final class AudioSynthesis {
    public static final int BROWN = 0;
    public static final int PINK = 1;
    public static final int WHITE = 2;
    private final Random random;
    private double brown, pink;

    public AudioSynthesis(long seed) { random = new Random(seed); }

    public double next(int color) {
        double white = random.nextDouble() * 2d - 1d;
        double sample;
        switch (color) {
            case WHITE:
                sample = white * 0.22d;
                break;
            case PINK:
                // Lightweight recursive low-frequency weighted noise (pink-like).
                pink = 0.985d * pink + 0.015d * white;
                sample = pink * 0.65d;
                break;
            case BROWN:
            default:
                brown = (brown + white * 0.015d) * 0.998d;
                sample = brown * 0.3d;
                break;
        }
        // Leaves headroom to avoid clipping at normal volume.
        return Math.max(-0.7d, Math.min(0.7d, sample));
    }
}
