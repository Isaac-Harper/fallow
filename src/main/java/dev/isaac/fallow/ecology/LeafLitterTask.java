package dev.isaac.fallow.ecology;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.growth.GrowthChannel;
import dev.isaac.fallow.growth.GrowthRateProvider;
import dev.isaac.fallow.season.SeasonClock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Phase 3 follow-up: old-growth forests develop a distinct floor. Under dense natural canopy,
 * fallen-leaf ground cover ({@code minecraft:leaf_litter}, the 1.21 segmented block) scatters
 * over the soil; less often the soil itself matures to podzol or rooted dirt.
 *
 * <p>Candidates come from the leaf-skipping heightmap, so under-canopy ground is sampled
 * directly; "dense canopy" = at least {@code minCanopyLayers} <em>non-persistent</em> leaf
 * blocks in the column above (player-placed leaves never make litter, consistent with the
 * sapling task's tree heuristic). Stateless and visit-driven. Runs on a decay channel:
 * autumn and winter build forest floor fastest.
 */
public final class LeafLitterTask implements EcologyTask {
    /** Ground that fallen-leaf cover can rest on (dirt family). */
    private final GrowthRateProvider rates;

    public LeafLitterTask(GrowthRateProvider rates) {
        this.rates = rates;
    }

    @Override
    public String id() {
        return "leaf_litter";
    }

    @Override
    public boolean enabled(FallowConfig config) {
        return config.leafLitter.enabled;
    }

    @Override
    public int visitChunk(ServerLevel level, LevelChunk chunk, RandomSource random, int samples) {
        FallowConfig.LeafLitter cfg = Fallow.CONFIG.leafLitter;
        ChunkPos chunkPos = chunk.getPos();
        int changed = 0;
        // Autumn-peaked leaf-fall weight - the same curve the client uses for falling-leaf
        // particles, so the litter building up matches the leaves you see coming down.
        double leafFall = Fallow.CONFIG.seasons.enabled
            ? cfg.leafFallWeight(SeasonClock.season())
            : 1.0;
        for (int i = 0; i < samples; i++) {
            int x = chunkPos.getMinBlockX() + random.nextInt(16);
            int z = chunkPos.getMinBlockZ() + random.nextInt(16);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinY() || y > level.getMaxY()) {
                continue;
            }
            BlockPos surface = new BlockPos(x, y, z);
            BlockPos ground = surface.below();
            if (!level.getBlockState(ground).is(BlockTags.DIRT) && !level.getBlockState(ground).is(Blocks.GRASS_BLOCK)) {
                continue;
            }
            if (random.nextFloat() >= rates.chance(GrowthChannel.LEAF_LITTER, level, surface) * leafFall) {
                continue;
            }
            if (canopyLayers(level, surface, cfg) < cfg.minCanopyLayers) {
                continue;
            }
            // Most triggers scatter visible leaf-litter cover on the soil; a minority mature the
            // soil itself into podzol/rooted dirt (the deeper old-growth floor).
            if (random.nextFloat() < cfg.podzolShare) {
                if (level.getBlockState(ground).is(Blocks.GRASS_BLOCK)) {
                    Block floor = random.nextFloat() < 0.8f ? Blocks.PODZOL : Blocks.ROOTED_DIRT;
                    level.setBlock(ground, floor.defaultBlockState(), Block.UPDATE_ALL);
                    changed++;
                }
            } else if (level.getBlockState(surface).isAir()) {
                BlockState litter = Blocks.LEAF_LITTER.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue(random.nextInt(4)))
                    .setValue(BlockStateProperties.SEGMENT_AMOUNT, 1 + random.nextInt(4));
                if (litter.canSurvive(level, surface)) {
                    level.setBlock(surface, litter, Block.UPDATE_ALL);
                    changed++;
                }
            }
        }
        return changed;
    }

    private static int canopyLayers(ServerLevel level, BlockPos surface, FallowConfig.LeafLitter cfg) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(surface.getX(), surface.getY(), surface.getZ());
        int layers = 0;
        int maxY = level.getMaxY();
        for (int i = 0; i < cfg.canopyScanHeight && cursor.getY() < maxY; i++) {
            cursor.move(0, 1, 0);
            BlockState state = level.getBlockState(cursor);
            if (state.is(BlockTags.LEAVES)
                && state.hasProperty(BlockStateProperties.PERSISTENT)
                && !state.getValue(BlockStateProperties.PERSISTENT)) {
                layers++;
                if (layers >= cfg.minCanopyLayers) {
                    return layers; // early exit: threshold met
                }
            }
        }
        return layers;
    }
}
