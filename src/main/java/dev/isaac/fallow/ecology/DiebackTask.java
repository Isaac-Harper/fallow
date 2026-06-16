package dev.isaac.fallow.ecology;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.growth.GrowthChannel;
import dev.isaac.fallow.growth.GrowthRateProvider;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Phase 3 task: vegetation in <em>sustained</em> enclosed darkness decays, one step per
 * trigger - tall grass reverts to short grass, short grass disappears, a bare grass block
 * reverts to dirt.
 *
 * <p>"Sustained" without per-block history storage: a compact in-memory map
 * (position -> consecutive-dark-visit count). A dark visit that passes the {@code DIEBACK}
 * probability roll increments the counter; any bright visit evicts the entry; the decay step
 * fires only at {@code requiredVisits} consecutive marks. The map is hard-capped and cleared
 * on overflow, and is deliberately not persisted - a restart merely delays dieback (the
 * accepted cost; see docs/architecture.md).
 *
 * <p>Darkness is judged by {@code max(raw skylight, blocklight)}, which is independent of
 * time of day: only genuinely roofed/enclosed spots ever qualify, ordinary night never does.
 * Torch-lit interiors stay alive. Candidates are found by heightmap with a bounded downward
 * probe through roofs, so multi-story builds and overhangs are reachable.
 *
 * <p>Seasonality runs inverted through the shared {@link GrowthRateProvider}: the
 * {@code DIEBACK} channel is a decay channel, so winter raises it (default 3x) while
 * simultaneously suppressing regrowth.
 */
public final class DiebackTask implements EcologyTask {
    private final GrowthRateProvider rates;
    private final Map<ServerLevel, Long2ByteOpenHashMap> counters = new IdentityHashMap<>();

    public DiebackTask(GrowthRateProvider rates) {
        this.rates = rates;
        ServerLevelEvents.UNLOAD.register((server, level) -> counters.remove(level));
    }

    @Override
    public String id() {
        return "dieback";
    }

    @Override
    public boolean enabled(FallowConfig config) {
        return config.dieback.enabled;
    }

    @Override
    public int visitChunk(ServerLevel level, LevelChunk chunk, RandomSource random, int samples) {
        FallowConfig.Dieback cfg = Fallow.CONFIG.dieback;
        ChunkPos chunkPos = chunk.getPos();
        int decayed = 0;
        for (int i = 0; i < samples; i++) {
            int x = chunkPos.getMinBlockX() + random.nextInt(16);
            int z = chunkPos.getMinBlockZ() + random.nextInt(16);
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (top <= level.getMinY()) {
                continue;
            }
            BlockPos candidate = findCandidate(level, x, Math.min(top, level.getMaxY()), z, cfg.probeDepth);
            if (candidate != null) {
                decayed += visitCandidate(level, candidate, random, cfg);
            }
        }
        return decayed;
    }

    /**
     * Find the topmost decayable surface at this column: the heightmap position handles open
     * ground and under-canopy (leaves are skipped); when the heightmap lands on a roof, probe
     * downward through it for an interior plant/grass surface (bounded by {@code probeDepth}).
     */
    private static BlockPos findCandidate(ServerLevel level, int x, int top, int z, int probeDepth) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        boolean airAbove = true; // the heightmap position itself is free
        for (int steps = 0; steps <= probeDepth && pos.getY() > level.getMinY(); steps++) {
            pos.move(0, -1, 0);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                airAbove = true;
                continue;
            }
            if (state.is(Blocks.TALL_GRASS)) {
                // Scanning down meets the upper half first; normalize to the lower half.
                BlockPos below = pos.below();
                return level.getBlockState(below).is(Blocks.TALL_GRASS) ? below : pos.immutable();
            }
            if (state.is(Blocks.SHORT_GRASS)) {
                return pos.immutable();
            }
            if (state.is(Blocks.GRASS_BLOCK) && airAbove) {
                return pos.immutable();
            }
            airAbove = false; // solid non-target (roof/floor): keep probing below it
        }
        return null;
    }

    private int visitCandidate(ServerLevel level, BlockPos pos, RandomSource random, FallowConfig.Dieback cfg) {
        BlockState state = level.getBlockState(pos);
        // Judge light where the plant lives (above the block for bare grass blocks).
        BlockPos lightPos = state.is(Blocks.GRASS_BLOCK) ? pos.above() : pos;
        int light = Math.max(
            level.getBrightness(LightLayer.SKY, lightPos),
            level.getBrightness(LightLayer.BLOCK, lightPos));

        Long2ByteOpenHashMap map = counters.computeIfAbsent(level, l -> new Long2ByteOpenHashMap());
        long key = pos.asLong();
        if (light >= cfg.lightLevel) {
            map.remove(key);
            return 0;
        }
        if (random.nextFloat() >= rates.chance(GrowthChannel.DIEBACK, level, pos)) {
            return 0;
        }
        int count = map.get(key) + 1;
        if (count < cfg.requiredVisits) {
            if (map.size() >= cfg.maxTracked) {
                map.clear(); // overflow: forget everything; dieback is delayed, never unbounded
            }
            map.put(key, (byte) count);
            return 0;
        }
        map.remove(key);
        return applyDecayStep(level, pos, state);
    }

    private static int applyDecayStep(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.TALL_GRASS)) {
            // Replacing the lower half silently collapses the upper half via its shape update.
            level.setBlock(pos, Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_ALL);
            return 1;
        }
        if (state.is(Blocks.SHORT_GRASS)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return 1;
        }
        if (state.is(Blocks.GRASS_BLOCK)) {
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
            return 1;
        }
        return 0;
    }
}
