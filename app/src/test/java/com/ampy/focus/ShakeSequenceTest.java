package com.ampy.focus;
import static org.junit.Assert.*;
import org.junit.Test;

public class ShakeSequenceTest {
    @Test public void ignoresStillPhoneAndOrdinaryMovement() {
        ShakeSequence detector = new ShakeSequence();
        assertFalse(detector.sample(0, 0, 0, 9.81));
        for (int i = 1; i < 200; i++) {
            assertFalse(detector.sample(i * 25L, Math.sin(i / 7d) * 2, 0, 9.81));
        }
    }
    @Test public void fourAlternatingStrongPeaksCauseSingleExit() {
        ShakeSequence detector = new ShakeSequence();
        assertFalse(detector.sample(0, 0, 0, 9.81));
        boolean triggered = false;
        for (int i = 0; i < 4; i++) {
            long at = 150 + i * 280L;
            triggered |= detector.sample(at, (i % 2 == 0 ? 34 : -34), 0, 9.81);
            // Let gravity filter settle before the next opposing acceleration peak.
            for (int j = 1; j <= 4; j++) {
                triggered |= detector.sample(at + j * 35L, 0, 0, 9.81);
            }
        }
        assertTrue("Four intentional alternating shakes should be recognized", triggered);
        assertFalse(detector.sample(1200, 35, 0, 9.81));
    }
}
