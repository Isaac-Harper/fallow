package dev.isaac.fallow.season;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.api.Season;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.WeatherData;

/**
 * Seasonal rain <em>frequency</em>. Minecraft has a single global weather timeline, so this can't
 * be per-biome - it nudges how often the overworld rains by season. At each weather transition it
 * scales the freshly-rolled duration: in a wet season rain lasts longer and clear spells are
 * shorter; in a dry season the reverse. We only touch the timers at transitions, so vanilla's own
 * weather ticking does the rest (and an admin's {@code /weather} still works between transitions).
 */
public final class WeatherService {
    /** Last observed rain state; null until the first observation each session. */
    private static Boolean lastRaining;

    private WeatherService() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> lastRaining = null);
        ServerTickEvents.END_SERVER_TICK.register(WeatherService::tick);
    }

    private static void tick(MinecraftServer server) {
        FallowConfig cfg = Fallow.CONFIG;
        if (!cfg.seasons.enabled || !cfg.precipitation.enabled || !cfg.precipitation.seasonalWeather) {
            return;
        }
        if (SeasonEvents.active() != SeasonEvents.Kind.NONE) {
            return; // a season event owns the weather while it runs - don't fight it
        }
        ServerLevel level = server.overworld();
        WeatherData weather = level.getWeatherData();
        boolean raining = weather.isRaining();
        if (lastRaining != null && raining == lastRaining) {
            return; // only adjust the duration of a *new* clear/rain period
        }
        lastRaining = raining;
        Season season = SeasonState.get(server).season();
        double wetness = Math.max(0.05, cfg.precipitation.rainfall(season));
        if (raining) {
            weather.setRainTime(scale(weather.getRainTime(), wetness)); // wetter season -> longer rain
        } else {
            weather.setClearWeatherTime(scale(weather.getClearWeatherTime(), 1.0 / wetness)); // wetter -> shorter clear
        }
    }

    private static int scale(int ticks, double factor) {
        long scaled = Math.round(ticks * factor);
        return (int) Math.max(20, Math.min(Integer.MAX_VALUE, scaled));
    }
}
