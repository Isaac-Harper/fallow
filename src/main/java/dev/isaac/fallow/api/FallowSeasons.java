package dev.isaac.fallow.api;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.season.SeasonState;
import net.minecraft.server.MinecraftServer;

/**
 * Read-only season API for other systems and addons. Stable surface: this class and
 * {@link Season}. Mutation goes through the {@code /fallow season set} command.
 */
public final class FallowSeasons {
    private FallowSeasons() {
    }

    /** Snapshot of the current season state. */
    public record SeasonInfo(Season season, int dayInSeason, int daysPerSeason) {
    }

    public static SeasonInfo get(MinecraftServer server) {
        SeasonState state = SeasonState.get(server);
        return new SeasonInfo(state.season(), state.dayInSeason(), Fallow.CONFIG.seasons.daysPerSeason);
    }

    public static Season season(MinecraftServer server) {
        return SeasonState.get(server).season();
    }

    /** False = season cycle frozen and all seasonal modifiers neutral. */
    public static boolean enabled() {
        return Fallow.CONFIG.seasons.enabled;
    }

    /** The growth multiplier currently applied on top of base config rates. */
    public static double growthMultiplier(MinecraftServer server) {
        return enabled() ? Fallow.CONFIG.seasons.multiplier(season(server)) : 1.0;
    }
}
