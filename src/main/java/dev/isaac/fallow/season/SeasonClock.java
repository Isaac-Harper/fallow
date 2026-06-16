package dev.isaac.fallow.season;

import dev.isaac.fallow.api.Season;

/**
 * Per-tick cache of the current season and in-game day, refreshed once at the start of each server
 * tick ({@code START_SERVER_TICK}, before the ecology scheduler runs on {@code END_LEVEL_TICK}).
 * The season is constant within a tick, so the many hot-path readers - {@code SeasonalGrowthRates}
 * (once per sampled candidate!) and each ecology task - read this volatile instead of doing a
 * {@code SeasonState.get(server)} saved-data lookup every time. {@link SeasonState} stays the
 * authoritative source (advanced by {@link SeasonService}); this is just a fast read-copy, at most
 * one tick stale (irrelevant - the season changes once every several in-game days).
 */
public final class SeasonClock {
    private static volatile Season season = Season.SPRING;
    private static volatile long day = 0L;

    private SeasonClock() {
    }

    public static Season season() {
        return season;
    }

    public static long day() {
        return day;
    }

    static void set(Season season, long day) {
        SeasonClock.season = season;
        SeasonClock.day = day;
    }
}
