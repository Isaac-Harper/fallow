package dev.isaac.fallow.ecology;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic tick-budgeted engine for ecology tasks (modeled on Gaia's Breath's scheduler, with
 * its hot spots fixed - see docs/architecture.md for the cost analysis).
 *
 * <p>Per world tick: visit at most {@code chunksPerTick} chunks, round-robin over a snapshot
 * ring of that world's loaded chunks, skipping chunks vanilla wouldn't block-tick. Every
 * enabled task gets {@code samplesPerChunk} candidate attempts per visited chunk. A wall-clock
 * budget ({@code tickBudgetMicros}) stops the loop early no matter what. There are no chunk
 * scans and no per-block bookkeeping; selection is purely stochastic, so the engine carries
 * zero persistent state and is removable without a trace.
 *
 * <p>The chunk set is maintained from load/unload events (cheap O(1) updates); the iteration
 * ring is re-snapshotted at most once per second when the set changed. A stale ring is safe:
 * unloaded chunks fail {@code getChunkNow} and are skipped.
 */
public final class EcologyScheduler {
    private static final List<EcologyTask> TASKS = new ArrayList<>();
    private static final Map<ServerLevel, WorldState> WORLDS = new IdentityHashMap<>();
    private static final int RING_REFRESH_TICKS = 20;

    private EcologyScheduler() {
    }

    /** Phase 3 task types register here (and add their config toggle + GrowthChannel). */
    public static void registerTask(EcologyTask task) {
        TASKS.add(task);
    }

    public static void register() {
        ServerLevelEvents.LOAD.register((server, level) -> WORLDS.put(level, new WorldState(level)));
        ServerLevelEvents.UNLOAD.register((server, level) -> WORLDS.remove(level));
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> {
            WorldState ws = WORLDS.get(level);
            if (ws != null) {
                ws.chunks.add(chunk.getPos().pack());
                ws.ringDirty = true;
            }
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            WorldState ws = WORLDS.get(level);
            if (ws != null) {
                ws.chunks.remove(chunk.getPos().pack());
                ws.ringDirty = true;
            }
        });
        ServerTickEvents.END_LEVEL_TICK.register(EcologyScheduler::tick);
    }

    private static void tick(ServerLevel level) {
        FallowConfig cfg = Fallow.CONFIG;
        WorldState ws = WORLDS.get(level);
        if (ws == null || !cfg.enabled || !cfg.scheduler.enabled || TASKS.isEmpty()) {
            return;
        }
        if (!cfg.scheduler.dimensions.contains(ws.dimensionId)) {
            return;
        }

        long start = System.nanoTime();
        long budgetNanos = cfg.scheduler.tickBudgetMicros * 1000L;

        ws.maybeRefreshRing();
        long[] ring = ws.ring;
        if (ring.length == 0) {
            return;
        }

        int visited = 0;
        int placed = 0;
        int attempts = Math.min(cfg.scheduler.chunksPerTick, ring.length);
        for (int i = 0; i < attempts; i++) {
            ws.cursor = (ws.cursor + 1) % ring.length;
            long packed = ring[ws.cursor];
            // Match vanilla's random-tick gate so we never grow vegetation in border chunks.
            if (!level.shouldTickBlocksAt(packed)) {
                continue;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
            if (chunk == null) {
                continue;
            }
            visited++;
            for (EcologyTask task : TASKS) {
                if (task.enabled(cfg)) {
                    placed += task.visitChunk(level, chunk, level.getRandom(), cfg.scheduler.samplesPerChunk);
                }
            }
            if (System.nanoTime() - start >= budgetNanos) {
                break;
            }
        }

        long elapsed = System.nanoTime() - start;
        ws.stats.record(elapsed, visited, placed);
        if (cfg.scheduler.logTimings && ws.stats.ticks >= 600) {
            Fallow.LOGGER.info("[ecology] {}: {}", ws.dimensionId, ws.stats.summary());
        }
    }

    /** Stats for /fallow stats; one line per currently loaded world. */
    public static List<String> statsLines() {
        List<String> lines = new ArrayList<>();
        for (WorldState ws : WORLDS.values()) {
            lines.add(ws.dimensionId + ": " + ws.stats.summary());
        }
        return lines;
    }

    private static final class WorldState {
        final String dimensionId;
        final LongOpenHashSet chunks = new LongOpenHashSet();
        long[] ring = new long[0];
        boolean ringDirty = true;
        int cursor = 0;
        int ringAge = 0;
        final Stats stats = new Stats();

        WorldState(ServerLevel level) {
            this.dimensionId = level.dimension().identifier().toString();
        }

        void maybeRefreshRing() {
            ringAge++;
            if (ringDirty && (ringAge >= RING_REFRESH_TICKS || ring.length == 0)) {
                ring = chunks.toLongArray();
                ringDirty = false;
                ringAge = 0;
                if (ring.length > 0) {
                    cursor %= ring.length;
                }
            }
        }
    }

    private static final class Stats {
        long ticks;
        long nanosTotal;
        long nanosMax;
        long chunksVisited;
        long blocksPlaced;

        void record(long nanos, int visited, int placed) {
            ticks++;
            nanosTotal += nanos;
            nanosMax = Math.max(nanosMax, nanos);
            chunksVisited += visited;
            blocksPlaced += placed;
        }

        /** Windowed: reports the period since the previous report (command or periodic log), then resets. */
        String summary() {
            if (ticks == 0) {
                return "no ticks since last report";
            }
            String line = String.format(
                "avg %.1fus/tick, max %.1fus, %d chunk visits, %d blocks placed over %d ticks",
                nanosTotal / 1000.0 / ticks, nanosMax / 1000.0, chunksVisited, blocksPlaced, ticks);
            ticks = nanosTotal = nanosMax = chunksVisited = blocksPlaced = 0;
            return line;
        }
    }
}
