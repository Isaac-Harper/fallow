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
 * Seasonal rain <em>frequency</em>. Minecraft keeps both the current rain spell and the current
 * clear spell in one countdown ({@code rainTime}); when it reaches zero the weather flips and
 * vanilla rolls a fresh duration (12000-24000 ticks of rain, 12000-180000 of clear). We watch for
 * that fresh roll and scale it by season: a wet season stretches rain and shortens clear, a dry
 * season does the reverse. Only the freshly-rolled value is touched, so vanilla's own ticking does
 * the rest. A {@code /weather rain|thunder <duration>} that raises the countdown is
 * indistinguishable from a fresh roll and gets the same seasonal scaling: the commanded weather
 * still arrives, its length just follows the season. ({@code clearWeatherTime} is deliberately
 * left alone - it's a command-driven "lock
 * clear" timer, 0 in normal play; writing a small value into it forces vanilla into a clear-lock
 * that pins {@code rainTime} to 1 and then immediately rebounds into rain, which flickers the
 * weather on and off.)
 */
public final class WeatherService {
    /**
     * Below this, a {@code rainTime} value isn't a freshly-rolled spell - it's vanilla pinning the
     * timer to 1 during a clear-weather lock, or our own scaled result. Real rolls are >= 12000.
     */
    private static final int FRESH_ROLL_MIN = 6000;

    /** Last observed rainTime; -1 until the first observation each session. */
    private static int lastRainTime = -1;

    private WeatherService() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> lastRainTime = -1);
        ServerTickEvents.END_SERVER_TICK.register(WeatherService::tick);
    }

    private static void tick(MinecraftServer server) {
        FallowConfig cfg = Fallow.CONFIG;
        if (!cfg.enabled || !cfg.seasons.enabled || !cfg.precipitation.enabled || !cfg.precipitation.seasonalWeather
            || SeasonEvents.active() != SeasonEvents.Kind.NONE) {
            // Gated off (config, or a season event owns the weather while it runs). Re-prime:
            // whatever the timers do while unwatched (event pinning, /fallow reload) must not be
            // compared against a stale observation and rescaled as a "fresh roll" later.
            lastRainTime = -1;
            return;
        }
        ServerLevel level = server.overworld();
        WeatherData weather = level.getWeatherData();
        int rainTime = weather.getRainTime();
        if (lastRainTime < 0) {
            lastRainTime = rainTime; // prime: don't rescale a spell already running at load
            return;
        }
        // Vanilla just rolled a new spell: the countdown jumped up to a full duration. During a
        // spell rainTime only decreases, so this fires exactly once per spell, on the roll tick.
        if (rainTime > lastRainTime && rainTime >= FRESH_ROLL_MIN) {
            Season season = SeasonState.get(server).season();
            weather.setRainTime(scaledSpell(rainTime, weather.isRaining(), cfg.precipitation.rainfall(season)));
        }
        lastRainTime = weather.getRainTime();
    }

    /**
     * Scale a freshly-rolled spell length for the season. A rain spell grows longer as the season's
     * {@code wetness} rises; a clear spell grows shorter (wetter seasons rain more often, drier
     * seasons less). {@code wetness} is floored at 0.05 so the clear branch never divides by zero.
     * Pure, so the seasonal intent is unit-testable without a running server.
     */
    static int scaledSpell(int rolledRainTime, boolean raining, double wetness) {
        double safeWetness = Math.max(0.05, wetness);
        double factor = raining ? safeWetness : 1.0 / safeWetness;
        return scale(rolledRainTime, factor);
    }

    private static int scale(int ticks, double factor) {
        long scaled = Math.round(ticks * factor);
        return (int) Math.max(20, Math.min(Integer.MAX_VALUE, scaled));
    }
}
