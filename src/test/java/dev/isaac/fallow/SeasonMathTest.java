package dev.isaac.fallow;

import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.season.SeasonMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonMathTest {
    @Test
    void freshStateAnchorsWithoutAdvancing() {
        var r = SeasonMath.advance(Season.SPRING, 0, -1, 100, 10);
        assertEquals(Season.SPRING, r.season());
        assertEquals(0, r.dayInSeason());
        assertEquals(100, r.lastDay());
    }

    @Test
    void singleDayRollover() {
        var r = SeasonMath.advance(Season.SPRING, 9, 50, 51, 10);
        assertEquals(Season.SUMMER, r.season());
        assertEquals(0, r.dayInSeason());
    }

    @Test
    void multiDayJumpCrossesSeasons() {
        // /time add across 25 days from spring day 0 with 10-day seasons: spring 10 + summer 10 + 5.
        var r = SeasonMath.advance(Season.SPRING, 0, 0, 25, 10);
        assertEquals(Season.AUTUMN, r.season());
        assertEquals(5, r.dayInSeason());
    }

    @Test
    void fullYearWrapsAround() {
        var r = SeasonMath.advance(Season.WINTER, 3, 0, 40, 10);
        assertEquals(Season.WINTER, r.season());
        assertEquals(3, r.dayInSeason());
    }

    @Test
    void timeBackwardsReanchorsOnly() {
        var r = SeasonMath.advance(Season.AUTUMN, 7, 100, 2, 10);
        assertEquals(Season.AUTUMN, r.season());
        assertEquals(7, r.dayInSeason());
        assertEquals(2, r.lastDay());
        // ...and the following normal day advances exactly one day.
        var r2 = SeasonMath.advance(r.season(), r.dayInSeason(), r.lastDay(), 3, 10);
        assertEquals(8, r2.dayInSeason());
    }

    @Test
    void sameDayIsNoChange() {
        var r = SeasonMath.advance(Season.SUMMER, 4, 77, 77, 10);
        assertFalse(r.changed());
    }

    @Test
    void shrunkenSeasonLengthNormalizes() {
        // daysPerSeason reduced from 30 to 7 while dayInSeason was 20: rolls forward, never wedges.
        var r = SeasonMath.advance(Season.SPRING, 20, 77, 77, 7);
        assertTrue(r.changed());
        assertEquals(Season.AUTUMN, r.season());
        assertEquals(6, r.dayInSeason());
        assertTrue(r.dayInSeason() < 7);
    }

    // -- Season.shifted edge cases ----------------------------------------

    @Test
    void shiftedNegativeWraps() {
        // -1 from SPRING wraps to WINTER.
        assertEquals(Season.WINTER, Season.SPRING.shifted(-1));
        // -1 from SUMMER wraps to SPRING.
        assertEquals(Season.SPRING, Season.SUMMER.shifted(-1));
    }

    @Test
    void shiftedMoreThanFourWraps() {
        // +4 is a full cycle: identity.
        for (Season s : Season.values()) {
            assertEquals(s, s.shifted(4), "shifted(4) must be identity for " + s);
        }
        // +5 is the same as +1.
        assertEquals(Season.SUMMER, Season.SPRING.shifted(5));
        // +8 is the same as +0.
        assertEquals(Season.AUTUMN, Season.AUTUMN.shifted(8));
    }

    @Test
    void shiftedLargeNegativeWraps() {
        // -4 is also a full cycle: identity.
        for (Season s : Season.values()) {
            assertEquals(s, s.shifted(-4), "shifted(-4) must be identity for " + s);
        }
        // -5 is the same as -1.
        assertEquals(Season.WINTER, Season.SPRING.shifted(-5));
    }

    // -- SeasonMath year-wrap edge cases ----------------------------------

    @Test
    void dayZeroWithFreshStateAnchors() {
        // lastDay < 0 (fresh): no advance, lastDay becomes 0.
        var r = SeasonMath.advance(Season.SPRING, 0, -1, 0, 10);
        assertEquals(Season.SPRING, r.season());
        assertEquals(0, r.dayInSeason());
        assertEquals(0, r.lastDay());
        assertTrue(r.changed()); // lastDay changed from -1 to 0
    }

    @Test
    void lastDayOfWinterAdvancesToSpring() {
        // dayInSeason 9 (last day of 10-day season), one more day: wraps to SPRING day 0.
        var r = SeasonMath.advance(Season.WINTER, 9, 100, 101, 10);
        assertEquals(Season.SPRING, r.season());
        assertEquals(0, r.dayInSeason());
    }

    @Test
    void dayInSeasonAtExactBoundary() {
        // dayInSeason == daysPerSeason - 1 is still within the season.
        var r = SeasonMath.advance(Season.SUMMER, 9, 50, 50, 10);
        assertEquals(Season.SUMMER, r.season());
        assertEquals(9, r.dayInSeason());
    }

    @Test
    void exactlyOneDayPastLastDayRollsOver() {
        // dayInSeason 9, delta 1: advances to next season day 0.
        var r = SeasonMath.advance(Season.SPRING, 9, 10, 11, 10);
        assertEquals(Season.SUMMER, r.season());
        assertEquals(0, r.dayInSeason());
    }

    @Test
    void multiYearJumpLandsCorrectly() {
        // 2 full years (4 seasons * 10 days * 2 = 80 days) from SPRING day 0 -> same position.
        var r = SeasonMath.advance(Season.SPRING, 0, 0, 80, 10);
        assertEquals(Season.SPRING, r.season());
        assertEquals(0, r.dayInSeason());
    }
}
