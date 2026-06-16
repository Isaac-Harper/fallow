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
 * Phase 3 follow-up: shorelines live. Waterline-adjacent ground occasionally sprouts sugar
 * cane; shallow water floors occasionally sprout seagrass. Both placements are validated by
 * the blocks' own vanilla {@code canSurvive} (sugar cane's soil + water-adjacency rule,
 * seagrass's solid-floor rule), so biome/datapack quirks are handled for free. Stateless.
 */
public final class ShorelineCreepTask implements EcologyTask {
    private final GrowthRateProvider rates;

    public ShorelineCreepTask(GrowthRateProvider rates) {
        this.rates = rates;
    }

    @Override
    public String id() {
        return "shoreline_creep";
    }

    @Override
    public boolean enabled(FallowConfig config) {
        return config.shoreline.enabled;
    }

    @Override
    public int visitChunk(ServerLevel level, LevelChunk chunk, RandomSource random, int samples) {
        FallowConfig.Shoreline cfg = Fallow.CONFIG.shoreline;
        ChunkPos chunkPos = chunk.getPos();
        int placed = 0;
        for (int i = 0; i < samples; i++) {
            int x = chunkPos.getMinBlockX() + random.nextInt(16);
            int z = chunkPos.getMinBlockZ() + random.nextInt(16);
            placed += visitColumn(level, x, z, random, cfg);
        }
        return placed;
    }

    private int visitColumn(ServerLevel level, int x, int z, RandomSource random, FallowConfig.Shoreline cfg) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (surfaceY <= level.getMinY() || surfaceY > level.getMaxY()) {
            return 0;
        }
        BlockPos surface = new BlockPos(x, surfaceY, z);

        // Sugar cane: dry-land position whose vanilla rules (soil + horizontal water neighbor
        // below) are entirely enforced by canSurvive - we only roll and ask. Note the surface
        // position is air above BOTH dry land and water columns, so no early return here.
        if (level.getBlockState(surface).isAir()
            && random.nextFloat() < rates.chance(GrowthChannel.SUGAR_CANE, level, surface)
            && Blocks.SUGAR_CANE.defaultBlockState().canSurvive(level, surface)
            && countNearby(level, surface, Blocks.SUGAR_CANE, 6, cfg.maxCaneNearby) < cfg.maxCaneNearby) {
            level.setBlock(surface, Blocks.SUGAR_CANE.defaultBlockState(), Block.UPDATE_ALL);
            return 1;
        }

        // Seagrass: if this column holds water, the fluid-ignoring heightmap sits below the
        // motion-blocking one by exactly the water depth (equal on dry land -> depth 0 -> skip).
        int floorY = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
        BlockPos waterPos = new BlockPos(x, floorY, z);
        int depth = surfaceY - floorY;
        if (depth < 1 || depth > cfg.maxSeagrassDepth) {
            return 0;
        }
        BlockState at = level.getBlockState(waterPos);
        if (!at.is(Blocks.WATER) || !level.getFluidState(waterPos).isSource()) {
            return 0;
        }
        if (random.nextFloat() >= rates.chance(GrowthChannel.SEAGRASS, level, waterPos)) {
            return 0;
        }
        BlockState seagrass = Blocks.SEAGRASS.defaultBlockState();
        if (!seagrass.canSurvive(level, waterPos)
            || countNearby(level, waterPos, Blocks.SEAGRASS, 4, cfg.maxSeagrassNearby) >= cfg.maxSeagrassNearby) {
            return 0;
        }
        level.setBlock(waterPos, seagrass, Block.UPDATE_ALL);
        return 1;
    }

    /** Box extent (+/-r horizontal, +/-2 vertical), early-exiting at {@code max} like the other tasks. */
    private static int countNearby(ServerLevel level, BlockPos pos, Block block, int r, int max) {
        int count = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -2, -r), pos.offset(r, 2, r))) {
            if (level.getBlockState(p).is(block)) {
                if (++count >= max) {
                    return count;
                }
            }
        }
        return count;
    }
}
