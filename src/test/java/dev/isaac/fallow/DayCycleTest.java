package dev.isaac.fallow;

import dev.isaac.fallow.season.DayCycle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DayCycleTest {
    @Test
    void fiftyFiftyIsExactlyVanilla() {
        // Bit-exact 1.0 in both phases: the kill-switch-by-config requirement.
        assertEquals(1.0f, DayCycle.rateFor(0, 0.5));
        assertEquals(1.0f, DayCycle.rateFor(11_999, 0.5));
        assertEquals(1.0f, DayCycle.rateFor(12_000, 0.5));
        assertEquals(1.0f, DayCycle.rateFor(23_999, 0.5));
    }

    @Test
    void summerSlowsDaysAndSpeedsNights() {
        // dayPortion 0.625 -> 15000 real ticks of day, 9000 of night.
        assertEquals(0.8f, DayCycle.rateFor(0, 0.625), 1e-6f);
        assertEquals(4.0f / 3.0f, DayCycle.rateFor(12_000, 0.625), 1e-6f);
    }

    @Test
    void cycleAlwaysCostsExactly24000RealTicks() {
        for (double p = 0.05; p <= 0.95; p += 0.01) {
            double dayRate = DayCycle.rateFor(0, p);
            double nightRate = DayCycle.rateFor(12_000, p);
            double realTicks = DayCycle.DAY_PHASE_TICKS / dayRate + DayCycle.DAY_PHASE_TICKS / nightRate;
            assertEquals(24_000.0, realTicks, 0.01, "portion " + p);
        }
    }

    @Test
    void phaseDetection() {
        assertTrue(DayCycle.isDayPhase(0));
        assertTrue(DayCycle.isDayPhase(11_999));
        assertFalse(DayCycle.isDayPhase(12_000));
        assertFalse(DayCycle.isDayPhase(23_999));
        assertTrue(DayCycle.isDayPhase(24_000));
        // Multi-day times and negative times stay well-defined.
        assertTrue(DayCycle.isDayPhase(24_000 * 100 + 500));
        assertEquals(-1, DayCycle.dayOf(-1));
    }

    @Test
    void extremePortionsAreClamped() {
        // Even absurd hand-edited values keep the rate finite and positive.
        assertTrue(DayCycle.rateFor(0, 0.0) > 0);
        assertTrue(Float.isFinite(DayCycle.rateFor(12_000, 1.0)));
    }
}
