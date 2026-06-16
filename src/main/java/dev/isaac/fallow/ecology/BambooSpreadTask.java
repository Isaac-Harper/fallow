package dev.isaac.fallow.ecology;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.growth.GrowthChannel;
import dev.isaac.fallow.growth.GrowthRateProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Bamboo spreads clonally, the way real bamboo runs on rhizomes: a patch of open, plantable ground
 * next to an existing bamboo stand occasionally sends up a new shoot, so groves creep outward and
 * thicken to a cap. The "bamboo nearby" gate is the validity rule - bamboo only appears where
 * bamboo already grows, so no biome check is needed - and vanilla {@code canSurvive} enforces the
 * soil (the {@code bamboo_plantable_on} tag); vanilla then random-ticks the shoot tall on its own.
 * Tropical, so it stays near-aseasonal through the BAMBOO channel's biome-modulated seasonality
 * (jungle's low {@code k} flattens the seasonal curve). Stateless.
 */
public final class BambooSpreadTask implements EcologyTask {
    private final GrowthRateProvider rates;

    public BambooSpreadTask(GrowthRateProvider rates) {
        this.rates = rates;
    }

    @Override
    public String id() {
        return "bamboo_spread";
    }

    @Override
    public boolean enabled(FallowConfig config) {
        return config.bamboo.enabled;
    }

    @Override
    public int visitChunk(ServerLevel level, LevelChunk chunk, RandomSource random, int samples) {
        FallowConfig.Bamboo cfg = Fallow.CONFIG.bamboo;
        ChunkPos chunkPos = chunk.getPos();
        int placed = 0;
        for (int i = 0; i < samples; i++) {
            int x = chunkPos.getMinBlockX() + random.nextInt(16);
            int z = chunkPos.getMinBlockZ() + random.nextInt(16);
            // The free spot above the topmost motion-blocking block: bamboo is motion-blocking, so
            // existing stalks aren't sampled - only the open ground around and between them is.
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinY() || y > level.getMaxY()) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir()
                || random.nextFloat() >= rates.chance(GrowthChannel.BAMBOO, level, pos)) {
                continue;
            }
            BlockState bamboo = Blocks.BAMBOO.defaultBlockState();
            if (!bamboo.canSurvive(level, pos)) {
                continue;
            }
            // Clonal: spread only next to an existing stand, and only until the grove fills its cap.
            int nearby = countStalks(level, pos, cfg.searchRadius, cfg.maxNearby);
            if (nearby == 0 || nearby >= cfg.maxNearby) {
                continue;
            }
            level.setBlock(pos, bamboo, Block.UPDATE_ALL);
            placed++;
        }
        return placed;
    }

    /**
     * Count bamboo <em>stalks</em> (each counted once, at its base - a bamboo block whose neighbour
     * below is not bamboo) in a box around {@code pos} (+/-r horizontal, +/-2 vertical), early-exiting
     * at {@code max}. Counting bases rather than segments keeps a tall stalk from reading as many.
     */
    private static int countStalks(ServerLevel level, BlockPos pos, int r, int max) {
        int count = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -2, -r), pos.offset(r, 2, r))) {
            if (level.getBlockState(p).is(Blocks.BAMBOO)
                && !level.getBlockState(p.below()).is(Blocks.BAMBOO)) {
                if (++count >= max) {
                    return count;
                }
            }
        }
        return count;
    }
}
