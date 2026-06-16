package dev.isaac.fallow.ecology;

import dev.isaac.fallow.FallowConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * One ecology behavior, scheduled by {@link EcologyScheduler}. Tasks are visit-driven: the
 * scheduler hands them an already-loaded, block-ticking chunk and a sample budget; the task
 * picks its own candidate blocks (Phase 1 vegetation samples heightmap columns; future tasks
 * may probe under canopy, along waterlines, etc. - candidate strategy belongs to the task).
 *
 * <p>Tasks must be cheap and bounded: no chunk scans, no IO, no unbounded searches. The
 * scheduler enforces a wall-clock budget between chunk visits, not within one, so a single
 * visit should stay in the tens of microseconds.
 */
public interface EcologyTask {
    /** Stable id for stats and logs. */
    String id();

    /** Live-config toggle; checked every visit so /fallow reload applies immediately. */
    boolean enabled(FallowConfig config);

    /**
     * Visit one chunk with a budget of {@code samples} candidate attempts.
     *
     * @return number of blocks changed (for stats)
     */
    int visitChunk(ServerLevel level, LevelChunk chunk, RandomSource random, int samples);
}
