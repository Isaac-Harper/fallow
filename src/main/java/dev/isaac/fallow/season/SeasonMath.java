package dev.isaac.fallow.season;

import dev.isaac.fallow.api.Season;

/** Pure season-advance logic, split from {@link SeasonState} for unit testing. */
public final class SeasonMath {
    private SeasonMath() {
    }

    public record Advance(Season season, int dayInSeason, long lastDay, boolean changed) {
    }

    /**
     * Advance from the last observed day to {@code nowDay}.
     *
     * <ul>
     *   <li>{@code lastDay < 0} (fresh state) or time moving backwards: re-anchor, no advance.</li>
     *   <li>Multi-day jumps ({@code /time add 48000}, sleeping) advance multiple days at once.</li>
     *   <li>{@code dayInSeason} beyond a shrunken {@code daysPerSeason} normalizes by rolling
     *       seasons forward, so config changes mid-world never wedge the state.</li>
     * </ul>
     */
    public static Advance advance(Season season, int dayInSeason, long lastDay, long nowDay, int daysPerSeason) {
        if (lastDay < 0 || nowDay < lastDay) {
            return new Advance(season, dayInSeason, nowDay, lastDay != nowDay);
        }
        long delta = nowDay - lastDay;
        if (delta == 0 && dayInSeason < daysPerSeason) {
            return new Advance(season, dayInSeason, lastDay, false);
        }
        long day = (long) dayInSeason + delta;
        Season s = season;
        while (day >= daysPerSeason) {
            day -= daysPerSeason;
            s = s.next();
        }
        return new Advance(s, (int) day, nowDay, true);
    }
}
