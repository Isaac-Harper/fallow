package dev.isaac.fallow.season;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.api.Season;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.WeatherData;

/**
 * Rolls a season-appropriate weather event once per in-game day (blizzard in winter, heatwave in
 * summer, storm in spring/autumn), forces its weather for a random duration, and publishes the
 * transient modifiers through {@link SeasonEvents}. Registered before {@link SeasonService} so a
 * heatwave's temperature bonus is in place when the season service computes and syncs the offset.
 */
public final class SeasonEventService {
    private static int ticksRemaining;
    private static long lastDay = Long.MIN_VALUE;

    private SeasonEventService() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ticksRemaining = 0;
            lastDay = Long.MIN_VALUE;
            SeasonEvents.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(SeasonEventService::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> SeasonEvents.clear());
    }

    private static void tick(MinecraftServer server) {
        FallowConfig.Events cfg = Fallow.CONFIG.events;
        if (!cfg.enabled || !Fallow.CONFIG.seasons.enabled) {
            if (SeasonEvents.active() != SeasonEvents.Kind.NONE) {
                SeasonEvents.clear();
                ticksRemaining = 0;
            }
            return;
        }
        ServerLevel level = server.overworld();
        if (ticksRemaining > 0) {
            upkeep(level);
            if (--ticksRemaining <= 0) {
                SeasonEvents.clear();
            }
            return;
        }
        long day = SeasonClock.day();
        if (day == lastDay) {
            return; // one roll per in-game day
        }
        boolean firstObservation = lastDay == Long.MIN_VALUE;
        lastDay = day;
        if (firstObservation) {
            return; // don't fire an event on the very first tick after load/join
        }
        RandomSource random = level.getRandom();
        if (random.nextFloat() >= cfg.chancePerDay) {
            return;
        }
        start(level, pick(SeasonClock.season()), cfg, random);
    }

    private static void start(ServerLevel level, SeasonEvents.Kind kind, FallowConfig.Events cfg, RandomSource random) {
        int span = Math.max(1, cfg.maxDurationTicks - cfg.minDurationTicks + 1);
        ticksRemaining = cfg.minDurationTicks + random.nextInt(span);
        switch (kind) {
            case BLIZZARD -> SeasonEvents.begin(kind, cfg.blizzardSnowMultiplier, 1.0, 0.0f);
            case HEATWAVE -> SeasonEvents.begin(kind, 1.0, cfg.heatwaveGrowthMultiplier, (float) cfg.heatwaveTempBonus);
            case STORM -> SeasonEvents.begin(kind, 1.0, 1.0, 0.0f);
            case NONE -> {
                ticksRemaining = 0;
                return;
            }
        }
        upkeep(level);
        Fallow.LOGGER.info("[fallow] season event: {} for {} ticks", kind, ticksRemaining);
    }

    /** Hold the event's weather each tick so vanilla's cycle doesn't drift it away mid-event. */
    private static void upkeep(ServerLevel level) {
        WeatherData weather = level.getWeatherData();
        switch (SeasonEvents.active()) {
            case BLIZZARD -> {
                weather.setRaining(true);
                weather.setRainTime(Math.max(weather.getRainTime(), 200));
            }
            case STORM -> {
                weather.setRaining(true);
                weather.setThundering(true);
                weather.setRainTime(Math.max(weather.getRainTime(), 200));
                weather.setThunderTime(Math.max(weather.getThunderTime(), 200));
            }
            case HEATWAVE -> {
                weather.setRaining(false);
                weather.setThundering(false);
                weather.setClearWeatherTime(Math.max(weather.getClearWeatherTime(), 200));
            }
            case NONE -> {
            }
        }
    }

    private static SeasonEvents.Kind pick(Season season) {
        return switch (season) {
            case WINTER -> SeasonEvents.Kind.BLIZZARD;
            case SUMMER -> SeasonEvents.Kind.HEATWAVE;
            case SPRING, AUTUMN -> SeasonEvents.Kind.STORM;
        };
    }
}
