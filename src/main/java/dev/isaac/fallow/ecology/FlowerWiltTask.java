package dev.isaac.fallow.ecology;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.growth.GrowthChannel;
import dev.isaac.fallow.growth.GrowthRateProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Set;

/**
 * Seasonal flower lifecycle: flowers wilt away through autumn and winter. The
 * {@code FLOWER_WILT} decay channel is near-zero in spring and high in autumn/winter, so
 * paired with the spring-boosted flower sprouting in {@link VegetationSproutTask} the net
 * effect over a year is: flowers bloom in spring, persist through summer, fade in fall, and
 * are gone by deep winter. Stateless and visit-driven.
 */
public final class FlowerWiltTask implements EcologyTask {
    /**
     * Garden flowers beyond {@code #minecraft:small_flowers} that live and die with the seasons.
     * Deliberately not {@code #minecraft:flowers}: that is the bee-attraction tag, and it also
     * holds mangrove propagules (which {@link SaplingSpreadTask} seeds), cherry and flowering
     * azalea <em>leaf</em> blocks, chorus flowers, spore blossoms, and cactus flowers - none of
     * which should wilt away.
     */
    private static final Set<Block> TALL_AND_BED_FLOWERS = Set.of(
        Blocks.SUNFLOWER, Blocks.LILAC, Blocks.PEONY, Blocks.ROSE_BUSH,
        Blocks.PITCHER_PLANT, Blocks.PINK_PETALS, Blocks.WILDFLOWERS);

    private final GrowthRateProvider rates;

    public FlowerWiltTask(GrowthRateProvider rates) {
        this.rates = rates;
    }

    @Override
    public String id() {
        return "flower_wilt";
    }

    @Override
    public boolean enabled(FallowConfig config) {
        return config.flowerWilt.enabled;
    }

    @Override
    public int visitChunk(ServerLevel level, LevelChunk chunk, RandomSource random, int samples) {
        ChunkPos chunkPos = chunk.getPos();
        int wilted = 0;
        for (int i = 0; i < samples; i++) {
            int x = chunkPos.getMinBlockX() + random.nextInt(16);
            int z = chunkPos.getMinBlockZ() + random.nextInt(16);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinY() || y > level.getMaxY()) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!isSeasonalFlower(state)) {
                continue;
            }
            // A tall flower's heightmap position is its lower half; remove from the lower half
            // so the upper half collapses with it.
            if (state.getBlock() instanceof DoublePlantBlock
                && state.hasProperty(DoublePlantBlock.HALF)
                && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
                pos = pos.below();
            }
            if (random.nextFloat() >= rates.chance(GrowthChannel.FLOWER_WILT, level, pos)) {
                continue;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            wilted++;
        }
        return wilted;
    }

    /** The flowers this task wilts - the same set {@link VegetationSproutTask} counts as density. */
    static boolean isSeasonalFlower(BlockState state) {
        return state.is(BlockTags.SMALL_FLOWERS) || TALL_AND_BED_FLOWERS.contains(state.getBlock());
    }
}
