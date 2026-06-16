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
}
