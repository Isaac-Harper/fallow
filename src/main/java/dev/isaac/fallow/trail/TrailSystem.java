package dev.isaac.fallow.trail;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 3, the first event-driven ecology system: repeated footsteps wear grass into coarse
 * dirt and finally into a dirt path; untrodden trails recover (path -> coarse dirt -> dirt,
 * then regrowth re-grasses it). The design follows the Gaia's Breath soil-wear study
 * (docs/research.md section 1.2) with its weaknesses fixed: per-world capped maps instead of one
 * global unbounded one, no save-marking every tick, decay skips unloaded chunks instead of
 * force-loading them, and flying/spectating players never wear trails.
 *
 * <p>Wear counters persist in {@link TrailData} (one small SavedData map per dimension).
 * Transient per-session state (who stood where, what was trodden since the last decay pass)
 * is in-memory only.
 */
public final class TrailSystem {
    private static final Map<UUID, Long> lastStepped = new HashMap<>();
    private static final Map<ServerLevel, Transient> transients = new IdentityHashMap<>();

    // Session stats for /fallow stats.
    private static long steps;
    private static long conversions;
    private static long recoveries;

    private TrailSystem() {
    }

    public static void register() {
        ServerTickEvents.END_LEVEL_TICK.register(TrailSystem::tick);
        ServerLevelEvents.UNLOAD.register((server, level) -> transients.remove(level));
        ServerPlayConnectionEvents.DISCONNECT.register(
            (handler, server) -> lastStepped.remove(handler.player.getUUID()));
    }

    public static String statsLine() {
        return String.format("trails: %d steps, %d conversions, %d recoveries this session",
            steps, conversions, recoveries);
    }

    private static void tick(ServerLevel level) {
        FallowConfig cfg = Fallow.CONFIG;
        if (!cfg.trails.enabled || !cfg.scheduler.dimensions.contains(level.dimension().identifier().toString())) {
            return;
        }
        Transient state = transients.computeIfAbsent(level, l -> new Transient());
        TrailData data = TrailData.get(level);

        for (ServerPlayer player : level.players()) {
            stepPlayer(level, player, data, state, cfg.trails);
        }

        if (++state.decayTicks >= cfg.trails.decayIntervalTicks) {
            state.decayTicks = 0;
            decay(level, data, state, cfg.trails);
            state.stepped.clear();
        }
    }

    private static void stepPlayer(ServerLevel level, ServerPlayer player, TrailData data,
                                   Transient state, FallowConfig.Trails cfg) {
        if (player.isSpectator() || player.getAbilities().flying || !player.onGround()) {
            return;
        }
        BlockPos under = player.blockPosition().below();
        long key = under.asLong();
        Long last = lastStepped.get(player.getUUID());
        if (last != null && last == key) {
            state.stepped.add(key); // standing still: protected from decay, but no extra wear
            return;
        }
        lastStepped.put(player.getUUID(), key);

        TrailMath.Surface surface = surfaceOf(level.getBlockState(under));
        if (surface == TrailMath.Surface.OTHER) {
            return;
        }
        state.stepped.add(key);
        int wear = data.wear.get(key);
        if (wear == 0 && data.wear.size() >= cfg.maxTracked) {
            prune(data, cfg.maxTracked);
        }
        TrailMath.Step step = TrailMath.step(surface, wear, cfg.stepsToCoarse, cfg.stepsToPath);
        data.wear.put(key, step.newWear());
        data.setDirty();
        steps++;
        if (step.convert() != null) {
            apply(level, under, step.convert());
            conversions++;
        }
    }

    private static void decay(ServerLevel level, TrailData data, Transient state, FallowConfig.Trails cfg) {
        if (data.wear.isEmpty()) {
            return;
        }
        var iterator = data.wear.long2IntEntrySet().fastIterator();
        boolean dirty = false;
        while (iterator.hasNext()) {
            Long2IntMap.Entry entry = iterator.next();
            long key = entry.getLongKey();
            if (state.stepped.contains(key)) {
                continue;
            }
            BlockPos pos = BlockPos.of(key);
            if (!level.hasChunkAt(pos)) {
                continue; // unloaded: neither wears nor recovers; never force-load chunks
            }
            TrailMath.Surface surface = surfaceOf(level.getBlockState(pos));
            TrailMath.Recovery recovery =
                TrailMath.recover(surface, entry.getIntValue(), cfg.recoveryAmount, cfg.stepsToCoarse);
            if (recovery.convert() != null) {
                apply(level, pos, recovery.convert());
                recoveries++;
                dirty = true;
            }
            if (recovery.evict()) {
                iterator.remove();
                dirty = true;
            } else if (recovery.newWear() != entry.getIntValue()) {
                entry.setValue(recovery.newWear());
                dirty = true;
            }
        }
        if (dirty) {
            data.setDirty();
        }
    }

    /**
     * Drop the lowest-wear quarter so fresh trails can always start. Rare (cap-sized) event;
     * a fixed histogram pass finds the cutoff in O(n) with constant allocation (no sort).
     */
    private static void prune(TrailData data, int maxTracked) {
        int maxWear = Fallow.CONFIG.trails.stepsToPath * 2;
        int[] histogram = new int[65];
        for (int v : data.wear.values()) {
            histogram[Math.min(64, v * 64 / Math.max(1, maxWear))]++;
        }
        int toDrop = data.wear.size() / 4;
        int seen = 0;
        int cutoffBucket = 0;
        while (cutoffBucket < 64 && seen + histogram[cutoffBucket] <= toDrop) {
            seen += histogram[cutoffBucket++];
        }
        // Inclusive of the bucket the threshold lands in, so at least one bucket always drops
        // (a uniform map must still shrink, or prune would re-fire on every fresh step).
        int cutoff = (cutoffBucket + 1) * maxWear / 64;
        int target = maxTracked * 3 / 4;
        var iterator = data.wear.long2IntEntrySet().fastIterator();
        while (iterator.hasNext() && data.wear.size() > target) {
            if (iterator.next().getIntValue() <= cutoff) {
                iterator.remove();
            }
        }
        Fallow.LOGGER.debug("[trails] pruned wear map to {} entries", data.wear.size());
    }

    private static TrailMath.Surface surfaceOf(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK)) return TrailMath.Surface.GRASS;
        if (state.is(Blocks.COARSE_DIRT)) return TrailMath.Surface.COARSE_DIRT;
        if (state.is(Blocks.DIRT_PATH)) return TrailMath.Surface.PATH;
        return TrailMath.Surface.OTHER;
    }

    private static void apply(ServerLevel level, BlockPos pos, TrailMath.Convert convert) {
        BlockState state = switch (convert) {
            case TO_COARSE_DIRT -> Blocks.COARSE_DIRT.defaultBlockState();
            case TO_PATH -> Blocks.DIRT_PATH.defaultBlockState();
            case TO_DIRT -> Blocks.DIRT.defaultBlockState();
        };
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static final class Transient {
        final LongOpenHashSet stepped = new LongOpenHashSet();
        int decayTicks;
    }
}
