package dev.isaac.fallow;

import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.visual.SeasonTint;
import dev.isaac.fallow.visual.SeasonTint.Kind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonTintTest {
    private static final int VANILLA_GRASS = 0x79C05A;

    @Test
    void summerIsExactlyVanilla() {
        for (Kind kind : Kind.values()) {
            var p = SeasonTint.forDay(Season.SUMMER, 5, 10, kind, false, 1.0);
            assertEquals(VANILLA_GRASS, SeasonTint.apply(VANILLA_GRASS, p));
        }
    }

    @Test
    void zeroStrengthConfigIsVanillaEverywhere() {
        for (Season s : Season.values()) {
            var p = SeasonTint.forDay(s, 3, 10, Kind.FOLIAGE, true, 0.0);
            assertEquals(VANILLA_GRASS, SeasonTint.apply(VANILLA_GRASS, p));
        }
    }

    @Test
    void autumnShiftsFoliageTowardOrange() {
        var p = SeasonTint.forDay(Season.AUTUMN, 5, 10, Kind.FOLIAGE, false, 1.0);
        int tinted = SeasonTint.apply(VANILLA_GRASS, p);
        assertNotEquals(VANILLA_GRASS, tinted);
        int red = (tinted >> 16) & 0xFF;
        int green = (tinted >> 8) & 0xFF;
        assertTrue(red > ((VANILLA_GRASS >> 16) & 0xFF), "autumn adds red");
        assertTrue(green < ((VANILLA_GRASS >> 8) & 0xFF), "autumn removes green");
    }

    @Test
    void autumnTurnsBirchYellowNotOrange() {
        var birch = SeasonTint.forDay(Season.AUTUMN, 5, 10, Kind.BIRCH_FOLIAGE, false, 1.0);
        int tinted = SeasonTint.apply(VANILLA_GRASS, birch);
        int r = (tinted >> 16) & 0xFF;
        int g = (tinted >> 8) & 0xFF;
        int b = tinted & 0xFF;
        // Yellow: red and green both high, blue low; green stays comparable to red (orange would
        // sink green well below red).
        assertTrue(b < g && b < r, "autumn birch drops blue");
        assertTrue(g > r * 0.7, "autumn birch keeps green high (yellow, not orange)");
    }

    @Test
    void spruceStaysEvergreenExceptWinter() {
        // Coniferous: no spring/autumn turn, only a subtle winter frost.
        assertEquals(0f, SeasonTint.seasonParams(Season.SPRING, Kind.SPRUCE_FOLIAGE).strength());
        assertEquals(0f, SeasonTint.seasonParams(Season.AUTUMN, Kind.SPRUCE_FOLIAGE).strength());
        assertTrue(SeasonTint.seasonParams(Season.WINTER, Kind.SPRUCE_FOLIAGE).strength() > 0,
            "spruce frost-mutes in winter");
    }

    @Test
    void autumnShiftsLilyPadAwayFromGreen() {
        var p = SeasonTint.forDay(Season.AUTUMN, 5, 10, Kind.LILY_PAD, false, 1.0);
        int tinted = SeasonTint.apply(VANILLA_GRASS, p);
        assertNotEquals(VANILLA_GRASS, tinted);
        int red = (tinted >> 16) & 0xFF;
        int green = (tinted >> 8) & 0xFF;
        assertTrue(red > ((VANILLA_GRASS >> 16) & 0xFF), "autumn lily pad adds red");
        assertTrue(green < ((VANILLA_GRASS >> 8) & 0xFF), "autumn lily pad removes green");
    }

    @Test
    void alphaChannelPreserved() {
        var p = SeasonTint.forDay(Season.WINTER, 5, 10, Kind.GRASS, false, 1.0);
        int tinted = SeasonTint.apply(0xFF000000 | VANILLA_GRASS, p);
        assertEquals(0xFF000000, tinted & 0xFF000000);
    }

    @Test
    void smoothTransitionEndsApproachNeighborSeasons() {
        // Last day of summer should already lean toward autumn (nonzero strength).
        var lateSummer = SeasonTint.forDay(Season.SUMMER, 9, 10, Kind.FOLIAGE, true, 1.0);
        assertTrue(lateSummer.strength() > 0, "late summer blends toward autumn");
        // The exact season midpoint (day 5 of 11 -> progress 0.5) equals the hard params.
        var midAutumnSmooth = SeasonTint.forDay(Season.AUTUMN, 5, 11, Kind.FOLIAGE, true, 1.0);
        var midAutumnHard = SeasonTint.forDay(Season.AUTUMN, 5, 11, Kind.FOLIAGE, false, 1.0);
        assertEquals(midAutumnHard.targetRgb(), midAutumnSmooth.targetRgb());
        assertTrue(Math.abs(midAutumnHard.strength() - midAutumnSmooth.strength()) < 0.001);
    }

    @Test
    void lerpToIdentityKeepsHue() {
        var autumn = SeasonTint.seasonParams(Season.AUTUMN, Kind.FOLIAGE);
        var towardSummer = SeasonTint.lerp(autumn, SeasonTint.Params.NONE, 0.5f);
        assertEquals(autumn.targetRgb(), towardSummer.targetRgb(), "hue held while strength fades");
        assertTrue(towardSummer.strength() < autumn.strength());
    }
}
