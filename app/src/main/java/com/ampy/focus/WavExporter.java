package com.ampy.focus;

import java.io.IOException;
import java.io.OutputStream;

/** Writes 24-bit mono PCM WAV at 48 kHz, streamed in chunks without large buffers. */
public final class WavExporter {
    public static final int RATE = 48000;
    public static final int SECONDS = 60;
    private WavExporter() {}

    public static void write(OutputStream out, int color) throws IOException {
        writeHeader(out, RATE * SECONDS);
        AudioSynthesis source = new AudioSynthesis(System.nanoTime());
        byte[] buffer = new byte[8192 * 3];
        int frames = RATE * SECONDS;
        while (frames > 0) {
            int chunk = Math.min(frames, 8192);
            for (int i = 0; i < chunk; i++) {
                int sample = (int) Math.round(source.next(color) * 8388607d);
                buffer[i * 3] = (byte) sample;
                buffer[i * 3 + 1] = (byte) (sample >>> 8);
                buffer[i * 3 + 2] = (byte) (sample >>> 16);
            }
            out.write(buffer, 0, chunk * 3);
            frames -= chunk;
        }
        out.flush();
    }

    public static byte[] header(int frames) {
        long payload = (long) frames * 3;
        if (frames < 0 || payload > Integer.MAX_VALUE - 44L) {
            throw new IllegalArgumentException("WAV too large");
        }
        byte[] h = new byte[44];
        ascii(h, 0, "RIFF");
        put32(h, 4, (int) payload + 36);
        ascii(h, 8, "WAVE");
        ascii(h, 12, "fmt ");
        put32(h, 16, 16);
        put16(h, 20, 1); // PCM
        put16(h, 22, 1); // mono
        put32(h, 24, RATE);
        put32(h, 28, RATE * 3);
        put16(h, 32, 3);
        put16(h, 34, 24);
        ascii(h, 36, "data");
        put32(h, 40, (int) payload);
        return h;
    }

    private static void writeHeader(OutputStream o, int frames) throws IOException {
        o.write(header(frames));
    }

    private static void ascii(byte[] dest, int offset, String s) {
        for (int i = 0; i < s.length(); i++) dest[offset + i] = (byte) s.charAt(i);
    }
    private static void put16(byte[] d, int off, int n) {
        d[off] = (byte) n;
        d[off + 1] = (byte) (n >>> 8);
    }
    private static void put32(byte[] d, int off, int n) {
        for (int i = 0; i < 4; i++) d[off + i] = (byte) (n >>> (i * 8));
    }
}
