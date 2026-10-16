package com.ampy.focus;
import org.junit.Test;
import static org.junit.Assert.*;

public class GeoFenceMathTest {
    @Test public void geodesicDistances() {
        assertEquals(0, GeoFenceMath.distanceMeters(45.67, 11.92, 45.67, 11.92), .001);
        assertEquals(111195, GeoFenceMath.distanceMeters(0, 0, 1, 0), 30);
        assertEquals(0, GeoFenceMath.distanceMeters(0, 179, 0, 179), .001);
    }
    @Test public void conservativeAccuracy() {
        assertEquals(GeoFenceMath.Position.INSIDE, GeoFenceMath.classify(100, 20, 150));
        assertEquals(GeoFenceMath.Position.OUTSIDE, GeoFenceMath.classify(200, 20, 150));
        assertEquals(GeoFenceMath.Position.UNKNOWN, GeoFenceMath.classify(145, 20, 150));
        assertEquals(GeoFenceMath.Position.UNKNOWN, GeoFenceMath.classify(60, 200, 100));
        assertEquals(GeoFenceMath.Position.UNKNOWN, GeoFenceMath.classify(0, 0, -1));
    }
    @Test public void coordinatesMustBeFiniteAndInRange() {
        assertFalse(GeoFenceMath.validCoordinate(Double.NaN, 0));
        assertFalse(GeoFenceMath.validCoordinate(91, 0));
        assertTrue(GeoFenceMath.validCoordinate(45.68, 11.93));
    }
}
