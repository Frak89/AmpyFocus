package com.ampy.focus;

import org.junit.Test;
import static org.junit.Assert.*;

public class WavExporterTest {
    @Test public void twentyFourBitHeaderCorrect() {
        byte[] h = WavExporter.header(48000);
        assertEquals(44, h.length);
        assertEquals('R', h[0]);
        assertEquals('W', h[8]);
        assertEquals(24, h[34] & 255);
        assertEquals(0x80, h[40] & 255); // 144000 bytes little endian
        assertEquals(0x32, h[41] & 255);
        assertEquals(0x02, h[42] & 255);
    }
    @Test public void noClippingGeneratedNoise() {
        AudioSynthesis a = new AudioSynthesis(42);
        for (int m = 0; m <= 2; m++) {
            for (int n = 0; n < 10000; n++) assertTrue(Math.abs(a.next(m)) <= 0.7);
        }
    }
}
