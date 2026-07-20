package dev.isaac.fallow.gametest;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.block.FallowBlocks;
import dev.isaac.fallow.season.SeasonService;
import dev.isaac.fallow.season.SeasonState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * In-world coverage for the raw-brightness growth gate the crops share with the vanilla crop
 * analogues (growth needs {@code getRawBrightness(pos, 0) >= 9}). These tests drive growth
 * deterministically by calling {@link BlockState#randomTick(ServerLevel, BlockPos, RandomSource)}
 * with a season weight of 1.0 so the only remaining lever is the light gate itself.
 *
 * <p>A dark test builds an enclosing roof so the block sits below the threshold; a light test uses
 * {@code skyAccess = true} so the column reaches full brightness. Without the roof / sky control a
 * growth assertion would pass for the wrong reason.
 *
 * <p>As with the other suites, {@code Fallow.CONFIG} is a shared mutable static, so every test sets
 * every field it depends on at the start rather than trusting defaults left by a prior test.
 */
public class LightGateGameTests {
    private static final BlockPos GROUND = new BlockPos(1, 1, 1);

    /** The crop position sitting one block above {@link #GROUND}. */
    private static final BlockPos CROP = GROUND.above();

    private static void baseCropConfig() {
        Fallow.CONFIG = new FallowConfig();
        Fallow.CONFIG.enabled = true;
        Fallow.CONFIG.crops.enabled = true;
        Fallow.CONFIG.crops.seasonGating = true;
        Fallow.CONFIG.crops.winterKill = true;
        Fallow.CONFIG.seasons.enabled = true;
    }

    private static void withSeason(GameTestHelper helper, Season season, Runnable body) {
        withSeasonAfter(helper, season, 1, body);
    }

    /**
     * Like {@link #withSeason} but defers the body by {@code delay} ticks. The dark tests seal the
     * crop in a stone shell and need the lighting engine to finish propagating the darkness before
     * {@code getRawBrightness} is read, which takes more than one tick on some game versions.
     */
    private static void withSeasonAfter(GameTestHelper helper, Season season, int delay,
            Runnable body) {
        SeasonState.get(helper.getLevel().getServer()).set(season, 0);
        SeasonService.invalidate();
        helper.runAfterDelay(delay, body);
    }

    private static void setCropWeight(String blockId, double spring, double summer,
            double autumn, double winter) {
        FallowConfig.SeasonWeights w = new FallowConfig.SeasonWeights();
        w.spring = spring;
        w.summer = summer;
        w.autumn = autumn;
        w.winter = winter;
        Fallow.CONFIG.crops.cropSeasons.put(blockId, w);
    }

    /**
     * Fully enclose the crop in a solid stone box so no sky or block light reaches it and
     * {@code getRawBrightness} stays below 9. Fills the 3x3x3 shell around the crop (floor ring one
     * block below, walls at the crop's level, and a full roof one block above), leaving only the
     * crop itself and the soil directly beneath it. This seals the floor and diagonal leaks a bare
     * roof would miss.
     */
    private static void encloseInDark(GameTestHelper helper, BlockPos crop) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    // Keep the crop (0,0,0) and its soil directly below (0,-1,0) clear.
                    if (dx == 0 && dz == 0 && dy <= 0) {
                        continue;
                    }
                    helper.setBlock(crop.offset(dx, dy, dz), Blocks.STONE);
                }
            }
        }
    }

    // 1a. A trellis climber in the dark cannot advance: many weight-1.0 ticks leave the age at 0.
    @GameTest(environment = "fallow:dark", maxTicks = 100)
    public void lightGateBlocksGrowthInDark(GameTestHelper helper) {
        baseCropConfig();
        setCropWeight("fallow:pea_crop", 1.0, 1.0, 1.0, 1.0);
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.DIRT);
        helper.setBlock(CROP, FallowBlocks.PEA_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 0));
        // No skyAccess plus a solid shell keeps this column dark (raw brightness < 9).
        encloseInDark(helper, CROP);

        withSeasonAfter(helper, Season.SUMMER, 5, () -> {
            BlockPos cropAbs = helper.absolutePos(CROP);
            RandomSource random = level.getRandom();
            for (int i = 0; i < 40; i++) {
                level.getBlockState(cropAbs).randomTick(level, cropAbs, random);
            }
            helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3, 0);
            helper.succeed();
        });
    }

    // 1b. A strawberry bush in the dark cannot advance either: age stays at 0.
    @GameTest(environment = "fallow:dark", maxTicks = 100)
    public void lightGateBlocksBushGrowthInDark(GameTestHelper helper) {
        baseCropConfig();
        setCropWeight("fallow:strawberry_bush", 1.0, 1.0, 1.0, 1.0);
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(CROP, FallowBlocks.STRAWBERRY_BUSH.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 0));
        encloseInDark(helper, CROP);

        withSeasonAfter(helper, Season.SUMMER, 5, () -> {
            BlockPos cropAbs = helper.absolutePos(CROP);
            RandomSource random = level.getRandom();
            for (int i = 0; i < 40; i++) {
                level.getBlockState(cropAbs).randomTick(level, cropAbs, random);
            }
            helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3, 0);
            helper.succeed();
        });
    }

    // 2. The same climber under open sky does advance: the gate is a gate, not a wall.
    @GameTest(environment = "fallow:growing", maxTicks = 100, skyAccess = true)
    public void lightGateAllowsGrowthInLight(GameTestHelper helper) {
        baseCropConfig();
        setCropWeight("fallow:pea_crop", 1.0, 1.0, 1.0, 1.0);
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.DIRT);
        helper.setBlock(CROP, FallowBlocks.PEA_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 0));

        withSeason(helper, Season.SUMMER, () -> {
            BlockPos cropAbs = helper.absolutePos(CROP);
            RandomSource random = level.getRandom();
            for (int i = 0; i < 40; i++) {
                BlockState state = level.getBlockState(cropAbs);
                if (state.getValue(BlockStateProperties.AGE_3) > 0) {
                    break;
                }
                state.randomTick(level, cropAbs, random);
            }
            helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3,
                age -> age > 0, net.minecraft.network.chat.Component.literal(
                    "pea crop should grow under open sky"));
            helper.succeed();
        });
    }

    // 16. Corn at age 1 with a solid block where its upper half would go stalls at age 1: growing to
    // age 2 makes it a double, and the blocked space above fails canGrowInto. skyAccess rules out
    // the light gate as the reason for the stall.
    @GameTest(environment = "fallow:growing", maxTicks = 200, skyAccess = true)
    public void cornStallsWhenObstructedAbove(GameTestHelper helper) {
        baseCropConfig();
        setCropWeight("fallow:corn_crop", 1.0, 1.0, 1.0, 1.0);
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(CROP, FallowBlocks.CORN_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 1)
            .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        // A solid block directly above the lower half occupies the upper-half slot.
        helper.setBlock(CROP.above(), Blocks.STONE);

        withSeason(helper, Season.SUMMER, () -> {
            BlockPos cropAbs = helper.absolutePos(CROP);
            RandomSource random = level.getRandom();
            for (int i = 0; i < 80; i++) {
                level.getBlockState(cropAbs).randomTick(level, cropAbs, random);
            }
            helper.assertBlockPresent(FallowBlocks.CORN_CROP, CROP);
            helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3, 1);
            helper.assertBlockPresent(Blocks.STONE, CROP.above());
            helper.succeed();
        });
    }

    // 6. End-state contract: after a winter-kill randomTick on a mature corn plant, the lower half
    // becomes a withered_crop and the upper half becomes air. This verifies the combined outcome of
    // the winter-kill path, not any specific internal code line; vanilla DoublePlantBlock clears the
    // upper half when the lower is overwritten, so the two assertions together form the full contract.
    @GameTest(environment = "fallow:winter")
    public void cornWinterKillClearsUpperHalf(GameTestHelper helper) {
        baseCropConfig();
        setCropWeight("fallow:corn_crop", 1.0, 1.0, 1.0, 0.0);
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        BlockPos upper = CROP.above();
        helper.setBlock(CROP, FallowBlocks.CORN_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 3)
            .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(upper, FallowBlocks.CORN_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 3)
            .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));

        withSeason(helper, Season.WINTER, () -> {
            BlockPos cropAbs = helper.absolutePos(CROP);
            level.getBlockState(cropAbs).randomTick(level, cropAbs, level.getRandom());
            helper.assertBlockPresent(FallowBlocks.WITHERED_CROP, CROP);
            helper.assertBlockPresent(Blocks.AIR, upper);
            helper.succeed();
        });
    }
}
