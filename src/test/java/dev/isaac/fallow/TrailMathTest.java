package dev.isaac.fallow;

import dev.isaac.fallow.trail.TrailMath;
import dev.isaac.fallow.trail.TrailMath.Convert;
import dev.isaac.fallow.trail.TrailMath.Surface;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailMathTest {
    private static final int TO_COARSE = 30;
    private static final int TO_PATH = 80;

    @Test
    void grassWearsToCoarseAtThreshold() {
        var s = TrailMath.step(Surface.GRASS, TO_COARSE - 2, TO_COARSE, TO_PATH);
        assertNull(s.convert());
        s = TrailMath.step(Surface.GRASS, TO_COARSE - 1, TO_COARSE, TO_PATH);
        assertEquals(Convert.TO_COARSE_DIRT, s.convert());
        assertEquals(TO_COARSE, s.newWear());
    }

    @Test
    void coarseWearsToPathAtThreshold() {
        var s = TrailMath.step(Surface.COARSE_DIRT, TO_PATH - 1, TO_COARSE, TO_PATH);
        assertEquals(Convert.TO_PATH, s.convert());
    }

    @Test
    void wearCapsSoRecoveryIsBounded() {
        var s = TrailMath.step(Surface.PATH, TO_PATH * 2, TO_COARSE, TO_PATH);
        assertEquals(TO_PATH * 2, s.newWear());
        assertNull(s.convert());
    }

    @Test
    void pathRecoversToCoarseThenDirt() {
        // Path with wear just above the coarse threshold steps down and converts.
        var r = TrailMath.recover(Surface.PATH, TO_COARSE + 1, 1, TO_COARSE);
        assertEquals(Convert.TO_COARSE_DIRT, r.convert());
        assertFalse(r.evict());
        // Coarse dirt at the bottom recovers to plain dirt and the entry is dropped.
        r = TrailMath.recover(Surface.COARSE_DIRT, 1, 1, TO_COARSE);
        assertEquals(Convert.TO_DIRT, r.convert());
        assertTrue(r.evict());
    }

    @Test
    void grassEntryEvictsAtZeroWithoutConversion() {
        var r = TrailMath.recover(Surface.GRASS, 1, 1, TO_COARSE);
        assertNull(r.convert());
        assertTrue(r.evict());
    }

    @Test
    void externallyChangedBlockEvicts() {
        var r = TrailMath.recover(Surface.OTHER, 50, 1, TO_COARSE);
        assertTrue(r.evict());
        assertNull(r.convert());
    }

    @Test
    void fullLifecycle() {
        // Walk a grass block all the way to path, then let it recover fully.
        int wear = 0;
        Surface surface = Surface.GRASS;
        for (int i = 0; i < 200; i++) {
            var s = TrailMath.step(surface, wear, TO_COARSE, TO_PATH);
            wear = s.newWear();
            if (s.convert() == Convert.TO_COARSE_DIRT) surface = Surface.COARSE_DIRT;
            if (s.convert() == Convert.TO_PATH) surface = Surface.PATH;
        }
        assertEquals(Surface.PATH, surface);
        assertEquals(TO_PATH * 2, wear);

        int passes = 0;
        boolean evicted = false;
        while (!evicted && passes++ < 1000) {
            var r = TrailMath.recover(surface, wear, 1, TO_COARSE);
            wear = r.newWear();
            if (r.convert() == Convert.TO_COARSE_DIRT) surface = Surface.COARSE_DIRT;
            if (r.convert() == Convert.TO_DIRT) evicted = r.evict();
            else if (r.evict()) evicted = true;
        }
        assertTrue(evicted, "trail must fully recover");
        assertTrue(passes < 1000);
    }
}
