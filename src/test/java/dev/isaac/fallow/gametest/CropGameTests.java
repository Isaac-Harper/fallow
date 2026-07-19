package dev.isaac.fallow.gametest;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.block.FallowBlocks;
import dev.isaac.fallow.ecology.ForageSpreadTask;
import dev.isaac.fallow.ecology.FruitDropTask;
import dev.isaac.fallow.item.FallowItems;
import dev.isaac.fallow.season.SeasonClock;
import dev.isaac.fallow.season.SeasonService;
import dev.isaac.fallow.season.SeasonState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * In-world integration coverage for the crop layer. Each test uses the empty structure and builds
 * its own setup programmatically. Growth is driven deterministically by calling
 * {@link BlockState#randomTick(ServerLevel, BlockPos, RandomSource)} rather than waiting on real
 * random ticks, and per-crop season weights are forced through {@link Fallow#CONFIG} so a single
 * tick either grows or kills with certainty.
 *
 * <p>Because {@code Fallow.CONFIG} is a shared mutable static, every test sets every field it
 * depends on at the start rather than trusting defaults left by a prior test.
 */
public class CropGameTests {
    private static final BlockPos GROUND = new BlockPos(1, 1, 1);

    /** The crop position sitting one block above {@link #GROUND}. */
    private static final BlockPos CROP = GROUND.above();

    /**
     * Reset the whole config to defaults, flip the master + crop switches on, and enable season
     * gating. Individual tests then override only the weights they care about.
     */
    private static void baseCropConfig() {
        Fallow.CONFIG = new FallowConfig();
        Fallow.CONFIG.enabled = true;
        Fallow.CONFIG.crops.enabled = true;
        Fallow.CONFIG.crops.seasonGating = true;
        Fallow.CONFIG.crops.winterKill = true;
        Fallow.CONFIG.seasons.enabled = true;
    }

    /**
     * Set the authoritative season the same way {@code /fallow season set} does, then run the body
     * one tick later so {@link SeasonClock} has refreshed from the {@link SeasonState}.
     */
    private static void withSeason(GameTestHelper helper, Season season, Runnable body) {
        SeasonState.get(helper.getLevel().getServer()).set(season, 0);
        SeasonService.invalidate();
        helper.runAfterDelay(1, body);
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

    // 1. Winter kills cabbage (winter weight 0) into a withered husk.
    @GameTest(environment = "fallow:winter")
    public void winterKillsCabbage(GameTestHelper helper) {
        baseCropConfig();
        setCropWeight("fallow:cabbage_crop", 1.0, 0.3, 1.0, 0.0);
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(CROP, FallowBlocks.CABBAGE_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 2));

        withSeason(helper, Season.WINTER, () -> {
            BlockPos cropAbs = helper.absolutePos(CROP);
            level.getBlockState(cropAbs).randomTick(level, cropAbs, level.getRandom());
            helper.assertBlockPresent(FallowBlocks.WITHERED_CROP, CROP);
            helper.succeed();
        });
    }

    // 2. Winter spares turnip (winter weight 0.25 > 0: stalls but never kills).
    @GameTest(environment = "fallow:winter")
    public void winterSparesTurnip(GameTestHelper helper) {
        baseCropConfig();
        setCropWeight("fallow:turnip_crop", 0.6, 0.8, 1.0, 0.25);
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(CROP, FallowBlocks.TURNIP_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 2));

        withSeason(helper, Season.WINTER, () -> {
            BlockPos cropAbs = helper.absolutePos(CROP);
            level.getBlockState(cropAbs).randomTick(level, cropAbs, level.getRandom());
            helper.assertBlockPresent(FallowBlocks.TURNIP_CROP, CROP);
            helper.succeed();
        });
    }

    // 3. Winter reverts a pea vine (winter weight 0) back to the bare trellis.
    @GameTest(environment = "fallow:winter")
    public void winterRevertsPeaVineToTrellis(GameTestHelper helper) {
        baseCropConfig();
        setCropWeight("fallow:pea_crop", 1.0, 0.6, 0.3, 0.0);
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.DIRT);
        helper.setBlock(CROP, FallowBlocks.PEA_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 2));

        withSeason(helper, Season.WINTER, () -> {
            BlockPos cropAbs = helper.absolutePos(CROP);
            level.getBlockState(cropAbs).randomTick(level, cropAbs, level.getRandom());
            helper.assertBlockPresent(FallowBlocks.TRELLIS, CROP);
            helper.succeed();
        });
    }

    // 4. Right-clicking a trellis with pea seeds plants a pea crop at age 0.
    @GameTest(environment = "fallow:growing")
    public void peaSeedsPlantOnTrellis(GameTestHelper helper) {
        baseCropConfig();
        helper.setBlock(GROUND, Blocks.DIRT);
        helper.setBlock(CROP, FallowBlocks.TRELLIS);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FallowItems.PEA_SEEDS));
        helper.useBlock(CROP, player);

        helper.assertBlockPresent(FallowBlocks.PEA_CROP, CROP);
        helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3, 0);
        helper.succeed();
    }

    // 5. Rice will not advance without water in paddy range; adding water unblocks growth.
    @GameTest(environment = "fallow:growing", maxTicks = 200)
    public void ricePaddyGateBlocksGrowth(GameTestHelper helper) {
        baseCropConfig();
        // Force the rice roll to always pass so the only gate under test is the water check.
        setCropWeight("fallow:rice_crop", 1.0, 1.0, 1.0, 1.0);
        Fallow.CONFIG.crops.paddy.range = 4;
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(CROP, FallowBlocks.RICE_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 0));

        withSeason(helper, Season.SUMMER, () -> {
            BlockPos cropAbs = helper.absolutePos(CROP);
            RandomSource random = level.getRandom();
            // No water anywhere: many ticks must not advance the age.
            for (int i = 0; i < 40; i++) {
                level.getBlockState(cropAbs).randomTick(level, cropAbs, random);
            }
            helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3, 0);

            // Place water within range, then a tick can advance the age.
            BlockPos waterRel = new BlockPos(3, 1, 1);
            helper.setBlock(waterRel, Blocks.WATER);
            for (int i = 0; i < 40; i++) {
                BlockState state = level.getBlockState(cropAbs);
                if (state.getValue(BlockStateProperties.AGE_3) > 0) {
                    break;
                }
                state.randomTick(level, cropAbs, random);
            }
            helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3,
                age -> age > 0, net.minecraft.network.chat.Component.literal("rice should grow once watered"));
            helper.succeed();
        });
    }

    // 6. Corn grows its upper half once the lower half reaches the double-block age.
    @GameTest(environment = "fallow:growing", maxTicks = 200)
    public void cornGrowsSecondBlock(GameTestHelper helper) {
        baseCropConfig();
        setCropWeight("fallow:corn_crop", 1.0, 1.0, 1.0, 1.0);
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(CROP, FallowBlocks.CORN_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 1)
            .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));

        withSeason(helper, Season.SUMMER, () -> {
            BlockPos cropAbs = helper.absolutePos(CROP);
            RandomSource random = level.getRandom();
            for (int i = 0; i < 60; i++) {
                BlockState state = level.getBlockState(cropAbs);
                if (state.getValue(BlockStateProperties.AGE_3) >= 2) {
                    break;
                }
                state.randomTick(level, cropAbs, random);
            }
            BlockPos upper = CROP.above();
            helper.assertBlockPresent(FallowBlocks.CORN_CROP, upper);
            helper.assertBlockProperty(upper, BlockStateProperties.DOUBLE_BLOCK_HALF,
                DoubleBlockHalf.UPPER);
            int lowerAge = level.getBlockState(cropAbs).getValue(BlockStateProperties.AGE_3);
            helper.assertBlockProperty(upper, BlockStateProperties.AGE_3, lowerAge);
            helper.succeed();
        });
    }

    // 7. A pea vine reaching maturity fixes nitrogen in exactly one adjacent coarse dirt.
    @GameTest(environment = "fallow:growing", maxTicks = 200)
    public void nitrogenFixingConvertsCoarseDirt(GameTestHelper helper) {
        baseCropConfig();
        setCropWeight("fallow:pea_crop", 1.0, 1.0, 1.0, 1.0);
        Fallow.CONFIG.crops.legumes.fixNitrogen = true;
        Fallow.CONFIG.crops.legumes.fixRadius = 1;
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.DIRT);
        helper.setBlock(CROP, FallowBlocks.PEA_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 2));
        // Two candidate coarse dirt blocks in the ground plane next to the crop's soil.
        BlockPos coarseA = GROUND.offset(1, 0, 0);
        BlockPos coarseB = GROUND.offset(-1, 0, 0);
        helper.setBlock(coarseA, Blocks.COARSE_DIRT);
        helper.setBlock(coarseB, Blocks.COARSE_DIRT);

        withSeason(helper, Season.SUMMER, () -> {
            BlockPos cropAbs = helper.absolutePos(CROP);
            RandomSource random = level.getRandom();
            for (int i = 0; i < 60; i++) {
                BlockState state = level.getBlockState(cropAbs);
                if (state.getValue(BlockStateProperties.AGE_3) >= 3) {
                    break;
                }
                state.randomTick(level, cropAbs, random);
            }
            boolean aFixed = level.getBlockState(helper.absolutePos(coarseA)).is(Blocks.DIRT);
            boolean bFixed = level.getBlockState(helper.absolutePos(coarseB)).is(Blocks.DIRT);
            helper.assertTrue(aFixed ^ bFixed, "exactly one coarse dirt should be converted to dirt");
            helper.succeed();
        });
    }

    // 8. The forage task places a wild plant on eligible grass.
    @GameTest(environment = "fallow:noseason", maxTicks = 100, skyAccess = true)
    public void forageTaskPlacesWildPlant(GameTestHelper helper) {
        Fallow.CONFIG = new FallowConfig();
        Fallow.CONFIG.enabled = true;
        Fallow.CONFIG.crops.enabled = true;
        Fallow.CONFIG.crops.wild.enabled = true;
        Fallow.CONFIG.crops.wild.forageChance = 1.0;
        Fallow.CONFIG.vegetation.minLightLevel = 0;
        // Seasons off keeps the FORAGE channel at its raw config chance (no seasonal scaling).
        Fallow.CONFIG.seasons.enabled = false;
        ServerLevel level = helper.getLevel();

        // A flat grass platform across the structure so any sampled column is a candidate.
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
            }
        }

        // Home the wild onion in this world's actual biome, read at runtime.
        BlockPos sampleAbs = helper.absolutePos(new BlockPos(1, 2, 1));
        String biomeId = level.getBiome(sampleAbs).unwrapKey()
            .map(k -> k.identifier().toString()).orElse("minecraft:plains");
        Fallow.CONFIG.crops.wild.homes.clear();
        Fallow.CONFIG.crops.wild.homes.put("fallow:wild_onion", java.util.List.of(biomeId));

        LevelChunk chunk = level.getChunkAt(sampleAbs);
        ForageSpreadTask task = new ForageSpreadTask(Fallow.GROWTH_RATES);
        int placed = task.visitChunk(level, chunk, level.getRandom(), 2000);

        helper.assertTrue(placed > 0,
            "forage task should have placed at least one wild plant on the grass platform "
            + "[biome=" + biomeId + "]");
        helper.assertTrue(scanFor(helper, level, FallowBlocks.WILD_ONION),
            "a placed wild plant should be a wild onion in range");
        helper.succeed();
    }

    // 9. Non-persistent cherry leaves drop cherries in spring.
    @GameTest(environment = "fallow:spring", maxTicks = 100, skyAccess = true)
    public void cherryLeavesDropCherries(GameTestHelper helper) {
        Fallow.CONFIG = new FallowConfig();
        Fallow.CONFIG.enabled = true;
        Fallow.CONFIG.crops.enabled = true;
        Fallow.CONFIG.fruiting.enabled = true;
        Fallow.CONFIG.seasons.enabled = true;
        Fallow.CONFIG.fruiting.types.get("minecraft:cherry_leaves").chance = 1.0;
        ServerLevel level = helper.getLevel();

        helper.setBlock(GROUND, Blocks.DIRT);
        // Natural (non-persistent) cherry leaves a few blocks up, with open air beneath them so
        // the task has a spot to drop into. skyAccess keeps this column's heightmap on the dirt.
        helper.setBlock(new BlockPos(1, 4, 1), Blocks.CHERRY_LEAVES.defaultBlockState()
            .setValue(BlockStateProperties.PERSISTENT, false));

        withSeason(helper, Season.SPRING, () -> {
            LevelChunk chunk = level.getChunkAt(helper.absolutePos(GROUND));
            FruitDropTask task = new FruitDropTask(Fallow.GROWTH_RATES);
            // The provider stack (biome growth multiplier) can scale the effective chance below 1.0,
            // so loop until a drop lands rather than asserting on a single probabilistic call.
            int dropped = 0;
            for (int attempt = 0; attempt < 50 && dropped == 0; attempt++) {
                dropped += task.visitChunk(level, chunk, level.getRandom(), 2000);
            }
            helper.assertTrue(dropped > 0, "cherry leaves should have dropped fruit");
            helper.assertItemEntityPresent(FallowItems.CHERRIES);
            helper.succeed();
        });
    }

    // 10. A strawberry bush stalls (not withers) in winter: age unchanged, still a bush.
    @GameTest(environment = "fallow:winter")
    public void strawberryStallsInWinter(GameTestHelper helper) {
        baseCropConfig();
        setCropWeight("fallow:strawberry_bush", 1.0, 1.0, 1.0, 0.0);
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(CROP, FallowBlocks.STRAWBERRY_BUSH.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 2));

        withSeason(helper, Season.WINTER, () -> {
            BlockPos cropAbs = helper.absolutePos(CROP);
            level.getBlockState(cropAbs).randomTick(level, cropAbs, level.getRandom());
            // Bushes never winter-kill: the block stays a bush and the age does not move.
            helper.assertBlockPresent(FallowBlocks.STRAWBERRY_BUSH, CROP);
            helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3, 2);
            helper.succeed();
        });
    }

    // 11. Right-clicking a ripe strawberry bush drops berries and resets it to age 1.
    @GameTest(environment = "fallow:growing")
    public void bushHarvestDropsAndResets(GameTestHelper helper) {
        baseCropConfig();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(CROP, FallowBlocks.STRAWBERRY_BUSH.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 3));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(CROP, player);

        helper.assertItemEntityPresent(FallowItems.STRAWBERRIES);
        helper.assertBlockPresent(FallowBlocks.STRAWBERRY_BUSH, CROP);
        helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3, 1);
        helper.succeed();
    }

    // 12. Right-clicking a ripe grape climber drops grapes and resets it to age 1.
    @GameTest(environment = "fallow:growing")
    public void climberHarvestDropsAndResets(GameTestHelper helper) {
        baseCropConfig();
        helper.setBlock(GROUND, Blocks.DIRT);
        helper.setBlock(CROP, FallowBlocks.GRAPE_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 3));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(CROP, player);

        helper.assertItemEntityPresent(FallowItems.GRAPES);
        helper.assertBlockPresent(FallowBlocks.GRAPE_CROP, CROP);
        helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3, 1);
        helper.succeed();
    }

    // 13. Corn is a double plant: breaking either half clears the other (vanilla DoublePlantBlock).
    @GameTest(environment = "fallow:noseason")
    public void cornBreaksAsOne(GameTestHelper helper) {
        baseCropConfig();
        ServerLevel level = helper.getLevel();

        // Left column: destroy the lower half and confirm the upper half goes with it.
        BlockPos lowerGround = new BlockPos(1, 1, 1);
        BlockPos lower = lowerGround.above();
        BlockPos upper = lower.above();
        helper.setBlock(lowerGround, Blocks.FARMLAND);
        helper.setBlock(lower, FallowBlocks.CORN_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 3)
            .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(upper, FallowBlocks.CORN_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 3)
            .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));

        level.destroyBlock(helper.absolutePos(lower), false);
        helper.assertBlockPresent(Blocks.AIR, lower);
        helper.assertBlockPresent(Blocks.AIR, upper);

        // Right column: the reverse direction, destroy the upper half and confirm the lower clears.
        BlockPos lowerGround2 = new BlockPos(4, 1, 1);
        BlockPos lower2 = lowerGround2.above();
        BlockPos upper2 = lower2.above();
        helper.setBlock(lowerGround2, Blocks.FARMLAND);
        helper.setBlock(lower2, FallowBlocks.CORN_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 3)
            .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(upper2, FallowBlocks.CORN_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 3)
            .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));

        level.destroyBlock(helper.absolutePos(upper2), false);
        helper.assertBlockPresent(Blocks.AIR, upper2);
        helper.assertBlockPresent(Blocks.AIR, lower2);
        helper.succeed();
    }

    // 14. The forage density guard caps same-type wild plants within the density radius.
    @GameTest(environment = "fallow:noseason", maxTicks = 100, skyAccess = true)
    public void forageDensityGuardCaps(GameTestHelper helper) {
        Fallow.CONFIG = new FallowConfig();
        Fallow.CONFIG.enabled = true;
        Fallow.CONFIG.crops.enabled = true;
        Fallow.CONFIG.crops.wild.enabled = true;
        Fallow.CONFIG.crops.wild.forageChance = 1.0;
        Fallow.CONFIG.vegetation.minLightLevel = 0;
        Fallow.CONFIG.seasons.enabled = false;
        ServerLevel level = helper.getLevel();

        // A flat grass platform: every column on it is within the density radius (8) of the
        // centre, so any candidate placement sees the two pre-placed onions and is skipped.
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
            }
        }

        BlockPos sampleAbs = helper.absolutePos(new BlockPos(1, 2, 1));
        String biomeId = level.getBiome(sampleAbs).unwrapKey()
            .map(k -> k.identifier().toString()).orElse("minecraft:plains");
        Fallow.CONFIG.crops.wild.homes.clear();
        Fallow.CONFIG.crops.wild.homes.put("fallow:wild_onion", java.util.List.of(biomeId));

        // Pre-seed exactly two wild onions near the platform centre - already at the guard cap.
        BlockPos onionA = new BlockPos(3, 2, 3);
        BlockPos onionB = new BlockPos(4, 2, 4);
        helper.setBlock(onionA, FallowBlocks.WILD_ONION);
        helper.setBlock(onionB, FallowBlocks.WILD_ONION);

        LevelChunk chunk = level.getChunkAt(sampleAbs);
        ForageSpreadTask task = new ForageSpreadTask(Fallow.GROWTH_RATES);
        task.visitChunk(level, chunk, level.getRandom(), 4000);

        // Count onions within the density radius (8, +/-2 in Y) of the platform centre; the guard
        // must have kept it at the two we placed.
        int count = countWildOnions(helper, level, new BlockPos(3, 2, 3));
        helper.assertTrue(count <= 2,
            "density guard should cap wild onions within radius at 2, found " + count);
        helper.succeed();
    }

    // 15. A squash stem winter-kills into a withered husk.
    @GameTest(environment = "fallow:winter")
    public void squashStemWithersInWinter(GameTestHelper helper) {
        baseCropConfig();
        setCropWeight("fallow:squash_stem", 1.0, 1.0, 1.0, 0.0);
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(CROP, FallowBlocks.SQUASH_STEM.defaultBlockState()
            .setValue(BlockStateProperties.AGE_7, 5));

        withSeason(helper, Season.WINTER, () -> {
            BlockPos cropAbs = helper.absolutePos(CROP);
            level.getBlockState(cropAbs).randomTick(level, cropAbs, level.getRandom());
            helper.assertBlockPresent(FallowBlocks.WITHERED_CROP, CROP);
            helper.succeed();
        });
    }

    // 16. A mature squash stem grows a squash on adjacent ground and turns into an attached stem.
    @GameTest(environment = "fallow:growing", maxTicks = 400)
    public void squashStemGrowsAndAttaches(GameTestHelper helper) {
        baseCropConfig();
        // Weight 1.0 so the season gate always passes; vanilla StemBlock then rolls its own chance.
        setCropWeight("fallow:squash_stem", 1.0, 1.0, 1.0, 1.0);
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(CROP, FallowBlocks.SQUASH_STEM.defaultBlockState()
            .setValue(BlockStateProperties.AGE_7, 7));
        // Free farmland on all four sides with air above, so whichever direction vanilla picks the
        // squash can grow there.
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            helper.setBlock(GROUND.relative(dir), Blocks.FARMLAND);
            helper.setBlock(CROP.relative(dir), Blocks.AIR);
        }

        withSeason(helper, Season.SUMMER, () -> {
            BlockPos cropAbs = helper.absolutePos(CROP);
            RandomSource random = level.getRandom();
            // Vanilla stem growth is probabilistic per tick; loop until it attaches (bounded).
            for (int i = 0; i < 300; i++) {
                if (level.getBlockState(cropAbs).is(FallowBlocks.ATTACHED_SQUASH_STEM)) {
                    break;
                }
                level.getBlockState(cropAbs).randomTick(level, cropAbs, random);
            }
            helper.assertBlockPresent(FallowBlocks.ATTACHED_SQUASH_STEM, CROP);
            net.minecraft.core.Direction facing = level.getBlockState(cropAbs)
                .getValue(BlockStateProperties.HORIZONTAL_FACING);
            helper.assertBlockPresent(FallowBlocks.SQUASH, CROP.relative(facing));
            helper.succeed();
        });
    }

    /** Count wild onions within the forage density radius (8 in XZ, +/-2 in Y) of a centre. */
    private static int countWildOnions(GameTestHelper helper, ServerLevel level, BlockPos centre) {
        int count = 0;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    BlockPos rel = centre.offset(dx, dy, dz);
                    if (level.getBlockState(helper.absolutePos(rel)).is(FallowBlocks.WILD_ONION)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** Scan the structure volume for a placed block of the given type. */
    private static boolean scanFor(GameTestHelper helper, ServerLevel level,
            net.minecraft.world.level.block.Block block) {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 6; y++) {
                for (int z = 0; z < 8; z++) {
                    if (level.getBlockState(helper.absolutePos(new BlockPos(x, y, z))).is(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
