package dev.isaac.fallow.season;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherScaleTest {
    /** A representative freshly-rolled spell length (vanilla rolls are always >= 12000). */
    private static final int ROLL = 12000;

    @Test
    void wetSeasonLengthensRainAndShortensClear() {
        double wet = 1.5;
        assertTrue(WeatherService.scaledSpell(ROLL, true, wet) > ROLL, "wet season: rain spells run longer");
        assertTrue(WeatherService.scaledSpell(ROLL, false, wet) < ROLL, "wet season: clear spells run shorter");
    }

    @Test
    void drySeasonShortensRainAndLengthensClear() {
        double dry = 0.6;
        assertTrue(WeatherService.scaledSpell(ROLL, true, dry) < ROLL, "dry season: rain spells run shorter");
        assertTrue(WeatherService.scaledSpell(ROLL, false, dry) > ROLL, "dry season: clear spells run longer");
    }

    @Test
    void neutralSeasonLeavesSpellsUnchanged() {
        assertEquals(ROLL, WeatherService.scaledSpell(ROLL, true, 1.0));
        assertEquals(ROLL, WeatherService.scaledSpell(ROLL, false, 1.0));
    }

    @Test
    void rainAndClearScaleByReciprocalFactors() {
        double wet = 1.5;
        // Rain is stretched by wetness; clear is shrunk by the same factor.
        assertEquals(ROLL, WeatherService.scaledSpell(ROLL, true, wet) / wet, 1.0);
        assertEquals(ROLL, WeatherService.scaledSpell(ROLL, false, wet) * wet, 1.0);
    }

    @Test
    void zeroWetnessIsClampedNotDividedByZero() {
        // wetness 0 would divide by zero on the clear branch; it's floored to 0.05 first.
        int clear = WeatherService.scaledSpell(ROLL, false, 0.0);
        int rain = WeatherService.scaledSpell(ROLL, true, 0.0);
        assertTrue(clear > 0 && clear < Integer.MAX_VALUE, "clear spell stays a sane finite length");
        assertTrue(rain >= 20, "rain spell never goes below the floor");
    }
}
