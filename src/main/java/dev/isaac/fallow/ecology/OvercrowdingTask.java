package dev.isaac.fallow.ecology;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.growth.BiomeTuning;
import dev.isaac.fallow.growth.GrowthChannel;
import dev.isaac.fallow.growth.GrowthRateProvider;
import dev.isaac.fallow.season.SeasonClock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Grass overcrowding: where short/tall grass exceeds the density <em>target</em> it thins out -
 * tall grass reverts to short grass, short grass disappears. The target is the base
 * {@code neighborThreshold} scaled per biome ({@code biomeDensity}) and per (phase-shifted)
 * season ({@code densityFactor}) - so meadows are thick in summer / lush biomes and recede in
 * winter / arid biomes. The cull <em>rate</em> ({@code CROWDING} chance) is flat: the channel is
 * exempt from {@code SeasonalGrowthRates} so the season isn't counted twice (target + rate).
 * Density-driven and stateless: the neighbor count vs. the target is the gate.
 */
public final class OvercrowdingTask implements EcologyTask {
    private final GrowthRateProvider rates;

    public OvercrowdingTask(GrowthRateProvider rates) {
        this.rates = rates;
    }

    @Override
    public String id() {
        return "overcrowding";
    }

    @Override
    public boolean enabled(FallowConfig config) {
        return config.overcrowding.enabled;
    }

    @Override
    public int visitChunk(ServerLevel level, LevelChunk chunk, RandomSource random, int samples) {
        FallowConfig.Overcrowding cfg = Fallow.CONFIG.overcrowding;
        ChunkPos chunkPos = chunk.getPos();
        int culled = 0;
        // The density target is per season (global) and per biome (per position): grass fills in
        // and is thinned back toward it, so meadows are thick in lush biomes / summer and recede
        // in arid biomes / winter.
        int maxNeighbors = (2 * cfg.radius + 1) * (2 * cfg.radius + 1) - 1;
        Season globalSeason = Fallow.CONFIG.seasons.enabled ? SeasonClock.season() : null;
        for (int i = 0; i < samples; i++) {
            int x = chunkPos.getMinBlockX() + random.nextInt(16);
            int z = chunkPos.getMinBlockZ() + random.nextInt(16);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinY() || y > level.getMaxY()) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            boolean tall = state.is(Blocks.TALL_GRASS);
            boolean shortGrass = state.is(Blocks.SHORT_GRASS);
            if (!tall && !shortGrass) {
                continue;
            }
            // Defensive: if the sample landed on a tall-grass UPPER half, drop to the LOWER
            // half so the replacement collapses the pair correctly (mirrors FlowerWiltTask).
            if (tall && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
                pos = pos.below();
            }
            // Density target uses the biome's phase-shifted season, matching the (also phase-shifted)
            // growth/cull rate - so e.g. a savanna's grass target peaks in its summer wet season.
            double seasonFactor = globalSeason == null ? 1.0
                : cfg.densityFactor(globalSeason.shifted(BiomeTuning.seasonPhase(level, pos)));
            int threshold = Math.max(0, Math.min(maxNeighbors,
                (int) Math.round(cfg.neighborThreshold * BiomeTuning.densityMultiplier(level, pos) * seasonFactor)));
            if (countGrassNeighbors(level, pos, cfg.radius) <= threshold) {
                continue;
            }
            if (random.nextFloat() >= rates.chance(GrowthChannel.CROWDING, level, pos)) {
                continue;
            }
            if (tall) {
                // Replacing the lower half collapses the upper half via its shape update.
                level.setBlock(pos, Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_ALL);
            } else {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            culled++;
        }
        return culled;
    }

    /**
     * Count grass-family plants in the horizontal neighborhood (excluding the center). Tall
     * grass is counted once via its lower half so a single double-plant isn't double-counted.
     */
    private static int countGrassNeighbors(ServerLevel level, BlockPos center, int r) {
        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                cursor.set(center.getX() + dx, center.getY(), center.getZ() + dz);
                BlockState s = level.getBlockState(cursor);
                if (s.is(Blocks.SHORT_GRASS)) {
                    count++;
                } else if (s.is(Blocks.TALL_GRASS) && s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
                    count++;
                }
            }
        }
        return count;
    }
}
