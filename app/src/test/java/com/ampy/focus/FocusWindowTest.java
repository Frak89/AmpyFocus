package com.ampy.focus;

import static org.junit.Assert.*;
import org.junit.Test;

public class FocusWindowTest {
    @Test public void daytimeSchedule() {
        assertFalse(FocusWindow.contains(8 * 60, 9 * 60, 17 * 60));
        assertTrue(FocusWindow.contains(9 * 60, 9 * 60, 17 * 60));
        assertTrue(FocusWindow.contains(16 * 60 + 59, 9 * 60, 17 * 60));
        assertFalse(FocusWindow.contains(17 * 60, 9 * 60, 17 * 60));
    }
    @Test public void crossingMidnight() {
        assertTrue(FocusWindow.contains(23 * 60, 22 * 60, 6 * 60));
        assertTrue(FocusWindow.contains(0, 22 * 60, 6 * 60));
        assertFalse(FocusWindow.contains(6 * 60, 22 * 60, 6 * 60));
        assertFalse(FocusWindow.contains(13 * 60, 22 * 60, 6 * 60));
    }
    @Test public void equalTimesAndInvalidInputsDisableSchedule() {
        assertFalse(FocusWindow.contains(100, 200, 200));
        assertFalse(FocusWindow.contains(1440, 200, 400));
        assertFalse(FocusWindow.contains(30, -1, 400));
    }
}
