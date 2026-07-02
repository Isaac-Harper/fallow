package dev.isaac.fallow.season;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.growth.BiomeTuning;
import dev.isaac.fallow.network.SeasonSyncPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ClockState;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;

import java.util.Optional;

/**
 * Drives the season cycle and the seasonal day/night split off the vanilla 26.1 overworld
 * world-clock. No mixins:
 *
 * <ul>
 *   <li>Day rollover is observed by comparing the clock's day index against the persisted
 *       {@link SeasonState} (handles {@code /time add}, bed skips, and backwards jumps).</li>
 *   <li>Day length is shifted through {@code ServerClockManager#setRate} - the same vanilla
 *       mechanism behind {@code /time set rate}. Vanilla broadcasts the rate to clients, which
 *       interpolate natively; beds and {@code /time set} act on totalTicks and are unaffected.</li>
 * </ul>
 *
 * <p>Rate hygiene: we only ever write a rate when the seasonal value differs from what we last
 * wrote, restore 1.0 on server stop, and adopt any non-1.0 rate found at startup (a crashed
 * session's leftover) so it gets managed back to the seasonal value. If Fallow is removed
 * after a crash mid-season, {@code /time set rate 1} (vanilla) restores stock behavior.
 */
public final class SeasonService {
    /** Last rate this session wrote to the clock; NaN = nothing written/adopted yet. */
    private static float appliedRate = Float.NaN;
    /** Last state synced to clients; null = nothing sent yet this session. */
    private static SeasonSyncPayload lastSynced;

    private SeasonService() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(SeasonService::onStarted);
        // Refresh the per-tick season/day cache before the level ticks (and thus before the
        // ecology scheduler runs), so hot-path readers avoid a SavedData lookup per candidate.
        ServerTickEvents.START_SERVER_TICK.register(SeasonService::cacheClock);
        ServerTickEvents.END_SERVER_TICK.register(SeasonService::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(SeasonService::onStopping);
        // New joiners get the current season immediately (drives client foliage tinting) - but only
        // while Fallow is active, so a disabled mod leaves clients fully vanilla.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (Fallow.CONFIG.enabled) {
                sender.sendPacket(currentSync(server));
            }
        });
    }

    /** Force the next tick to re-evaluate and re-apply the rate (config reload, season set). */
    public static void invalidate() {
        if (!Float.isNaN(appliedRate)) {
            // Keep managing: drop only the "current value" memory so the next tick rewrites.
            appliedRate = -1.0f;
        }
    }

    private static void onStarted(MinecraftServer server) {
        appliedRate = Float.NaN;
        lastSynced = null;
        SeasonalTemperature.set(0.0f);
        // Resolve every biome's tuning from this world's registry before anything reads it.
        BiomeTuning.rebuild(server.registryAccess());
        // Adopt a leftover non-vanilla rate (crash mid-season) so it gets corrected, not orphaned.
        overworldClock(server).ifPresent(clock -> {
            ClockState state = server.clockManager().packState().clocks().get(clock);
            if (state != null && state.rate() != 1.0f) {
                appliedRate = state.rate();
                Fallow.LOGGER.info("Adopting leftover clock rate {} from a previous session", state.rate());
            }
        });
    }

    private static void onStopping(MinecraftServer server) {
        if (!Float.isNaN(appliedRate) && appliedRate != 1.0f) {
            overworldClock(server).ifPresent(clock -> server.clockManager().setRate(clock, 1.0f));
        }
        // Singleplayer returns to the same JVM: clear the offset so it can't leak into the next world.
        SeasonalTemperature.set(0.0f);
    }

    private static void tick(MinecraftServer server) {
        FallowConfig cfg = Fallow.CONFIG;
        if (!cfg.enabled) {
            // Master switch off: keep the world fully vanilla - no seasonal temperature shift (so
            // vanilla never snows/freezes on our account) and restore the vanilla day length if we
            // ever changed it. Already-connected clients must go vanilla too (drop the tint and
            // their copy of the offset): the disabled payload below is deduped by lastSynced, so a
            // mid-session disable broadcasts exactly once.
            SeasonalTemperature.set(0.0f);
            SeasonState state = SeasonState.get(server);
            sendIfChanged(server, new SeasonSyncPayload(
                state.season().ordinal(), state.dayInSeason(), cfg.seasons.daysPerSeason, false, 0.0f));
            if (!Float.isNaN(appliedRate) && appliedRate != 1.0f) {
                overworldClock(server).ifPresent(c -> server.clockManager().setRate(c, 1.0f));
                appliedRate = 1.0f;
            }
            return;
        }
        Optional<Holder.Reference<WorldClock>> clock = overworldClock(server);
        if (clock.isEmpty()) {
            return;
        }
        long ticks = server.clockManager().getTotalTicks(clock.get());
        SeasonState state = SeasonState.get(server);
        if (cfg.seasons.enabled) {
            state.observeDay(DayCycle.dayOf(ticks), cfg.seasons.daysPerSeason);
        }

        SeasonSyncPayload sync = currentSync(server);
        // Drive the server-side seasonal temperature (snow/freeze logic reads it via the mixin);
        // the same value rides the payload so clients render matching rain/snow particles.
        SeasonalTemperature.set(sync.tempOffset());
        sendIfChanged(server, sync);

        float desired = cfg.seasons.enabled && cfg.dayNight.enabled
            ? DayCycle.rateFor(ticks, cfg.dayNight.dayPortion(state.season()))
            : 1.0f;
        if (Float.isNaN(appliedRate) && desired == 1.0f) {
            // Vanilla wants 1.0 and we have never touched the clock: stay hands-off so an
            // admin's manual /time set rate isn't stomped while our features are neutral.
            return;
        }
        if (desired != appliedRate) {
            server.clockManager().setRate(clock.get(), desired);
            appliedRate = desired;
        }
    }

    /** Refresh {@link SeasonClock} once per tick from the authoritative state + world clock. */
    private static void cacheClock(MinecraftServer server) {
        long day = overworldClock(server)
            .map(clock -> DayCycle.dayOf(server.clockManager().getTotalTicks(clock)))
            .orElse(SeasonClock.day());
        SeasonClock.set(SeasonState.get(server).season(), day);
    }

    /** Broadcast the payload to every connected player, skipping if it matches the last send. */
    private static void sendIfChanged(MinecraftServer server, SeasonSyncPayload sync) {
        if (sync.equals(lastSynced)) {
            return;
        }
        lastSynced = sync;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, sync);
        }
    }

    private static SeasonSyncPayload currentSync(MinecraftServer server) {
        SeasonState state = SeasonState.get(server);
        FallowConfig cfg = Fallow.CONFIG;
        float tempOffset = cfg.seasons.enabled && cfg.precipitation.enabled
            ? (float) cfg.precipitation.tempOffset(state.season()) + SeasonEvents.tempBonus()
            : 0.0f;
        return new SeasonSyncPayload(
            state.season().ordinal(),
            state.dayInSeason(),
            cfg.seasons.daysPerSeason,
            cfg.seasons.enabled,
            tempOffset);
    }

    private static Optional<Holder.Reference<WorldClock>> overworldClock(MinecraftServer server) {
        return server.registryAccess().get(WorldClocks.OVERWORLD);
    }
}
