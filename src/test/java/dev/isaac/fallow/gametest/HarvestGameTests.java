package dev.isaac.fallow.gametest;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.block.FallowBlocks;
import dev.isaac.fallow.item.FallowItems;
import dev.isaac.fallow.season.SeasonService;
import dev.isaac.fallow.season.SeasonState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * In-world coverage for the harvest-time behaviours: bush drop counts, the legume nitrogen fix that
 * fires on right-click harvest, carrot-style onion planting, the pickle bottle remainder, the
 * rice-seed exclusion from the grass seed pool, and the raspberry / blackberry winter stall.
 *
 * <p>As with the other suites, {@code Fallow.CONFIG} is a shared mutable static, so every test sets
 * every field it depends on at the start rather than trusting defaults left by a prior test.
 */
public class HarvestGameTests {
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

    // 9. A strawberry bush harvested at age 2 drops exactly one berry and resets to age 1.
    @GameTest(environment = "fallow:growing")
    public void bushHarvestAtAge2DropsOne(GameTestHelper helper) {
        baseCropConfig();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(CROP, FallowBlocks.STRAWBERRY_BUSH.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 2));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(CROP, player);

        helper.assertItemEntityCountIs(FallowItems.STRAWBERRIES, CROP, 2.0, 1);
        helper.assertBlockPresent(FallowBlocks.STRAWBERRY_BUSH, CROP);
        helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3, 1);
        helper.succeed();
    }

    // 11. Harvesting a mature pea vine fixes nitrogen in an adjacent coarse dirt (-> dirt).
    @GameTest(environment = "fallow:growing")
    public void nitrogenFixOnHarvest(GameTestHelper helper) {
        baseCropConfig();
        Fallow.CONFIG.crops.legumes.fixNitrogen = true;
        Fallow.CONFIG.crops.legumes.fixRadius = 1;
        helper.setBlock(GROUND, Blocks.DIRT);
        helper.setBlock(CROP, FallowBlocks.PEA_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 3));
        // Exactly one candidate soil in the fix box so the conversion target is deterministic.
        BlockPos coarse = GROUND.offset(1, 0, 0);
        helper.setBlock(coarse, Blocks.COARSE_DIRT);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(CROP, player);

        helper.assertBlockPresent(Blocks.DIRT, coarse);
        helper.succeed();
    }

    // 12. The same nitrogen fix converts rooted dirt as well as coarse dirt.
    @GameTest(environment = "fallow:growing")
    public void nitrogenFixConvertsRootedDirt(GameTestHelper helper) {
        baseCropConfig();
        Fallow.CONFIG.crops.legumes.fixNitrogen = true;
        Fallow.CONFIG.crops.legumes.fixRadius = 1;
        helper.setBlock(GROUND, Blocks.DIRT);
        helper.setBlock(CROP, FallowBlocks.PEA_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 3));
        BlockPos rooted = GROUND.offset(1, 0, 0);
        helper.setBlock(rooted, Blocks.ROOTED_DIRT);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(CROP, player);

        helper.assertBlockPresent(Blocks.DIRT, rooted);
        helper.succeed();
    }

    // 15. Right-clicking farmland with an onion plants an onion crop at age 0 (carrot-style).
    @GameTest(environment = "fallow:growing")
    public void onionPlantsCarrotStyle(GameTestHelper helper) {
        baseCropConfig();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(CROP, Blocks.AIR);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FallowItems.ONION));
        // Click the top face of the farmland so the onion BlockItem places the crop in the air above.
        BlockPos groundAbs = helper.absolutePos(GROUND);
        BlockHitResult hit = new BlockHitResult(
            Vec3.atCenterOf(groundAbs).add(0.0, 0.5, 0.0), Direction.UP, groundAbs, false);
        helper.useBlock(GROUND, player, hit);

        helper.assertBlockPresent(FallowBlocks.ONION_CROP, CROP);
        helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3, 0);
        helper.succeed();
    }

    // 10. Pickles carry a use-remainder that returns a glass bottle when consumed (mirrors jam).
    @GameTest(environment = "fallow:noseason")
    public void picklesReturnBottle(GameTestHelper helper) {
        Fallow.CONFIG = new FallowConfig();
        Fallow.CONFIG.enabled = true;

        ItemStack pickles = new ItemStack(FallowItems.PICKLES);
        UseRemainder remainder = pickles.get(DataComponents.USE_REMAINDER);
        helper.assertTrue(remainder != null, "pickles should carry a use-remainder component");
        ItemStack returned = remainder.convertInto().create();
        helper.assertTrue(returned.is(Items.GLASS_BOTTLE),
            "eating pickles should return a glass bottle");
        helper.succeed();
    }

    // 17. Rice seeds are excluded from the grass seed pool: no roll of short grass ever drops them.
    @GameTest(environment = "fallow:noseason")
    public void riceExcludedFromGrassSeedPool(GameTestHelper helper) {
        Fallow.CONFIG = new FallowConfig();
        Fallow.CONFIG.enabled = true;
        Fallow.CONFIG.crops.enabled = true;
        ServerLevel level = helper.getLevel();

        ResourceKey<LootTable> shortGrass = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.withDefaultNamespace("blocks/short_grass"));
        LootTable table = level.getServer().reloadableRegistries().getLootTable(shortGrass);

        BlockPos originAbs = helper.absolutePos(GROUND);
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(originAbs))
            .withParameter(LootContextParams.BLOCK_STATE, Blocks.SHORT_GRASS.defaultBlockState())
            .withParameter(LootContextParams.TOOL, new ItemStack(Items.IRON_HOE))
            .create(LootContextParamSets.BLOCK);

        // Rice grows only in paddies, so it must never appear in the general grass seed pool.
        for (int i = 0; i < 2000; i++) {
            for (ItemStack stack : table.getRandomItems(params, i)) {
                helper.assertFalse(stack.is(FallowItems.RICE_SEEDS),
                    "rice seeds must never drop from the grass seed pool");
            }
        }
        helper.succeed();
    }

    // 19a. A raspberry bush stalls (not withers) in winter: age unchanged after many ticks.
    // skyAccess = true ensures darkness is NOT a backstop; the winter weight (0.0) is the sole lever.
    @GameTest(environment = "fallow:winter", maxTicks = 200, skyAccess = true)
    public void raspberryStallsInWinter(GameTestHelper helper) {
        baseCropConfig();
        setCropWeight("fallow:raspberry_bush", 1.0, 1.0, 1.0, 0.0);
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(CROP, FallowBlocks.RASPBERRY_BUSH.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 2));

        withSeason(helper, Season.WINTER, () -> {
            BlockPos cropAbs = helper.absolutePos(CROP);
            // 60 ticks under open sky: a non-stalled bush would almost certainly advance at least
            // once (weight 1.0 summer passes every tick). Winter weight 0.0 must hold it at age 2.
            for (int i = 0; i < 60; i++) {
                level.getBlockState(cropAbs).randomTick(level, cropAbs, level.getRandom());
            }
            helper.assertBlockPresent(FallowBlocks.RASPBERRY_BUSH, CROP);
            helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3, 2);
            helper.succeed();
        });
    }

    // 19a-positive. Same raspberry setup in summer (weight 1.0): proves the rig can grow, so the
    // winter stall above is meaningful and not hiding a broken randomTick path.
    @GameTest(environment = "fallow:growing", maxTicks = 200, skyAccess = true)
    public void raspberryGrowsInSummer(GameTestHelper helper) {
        baseCropConfig();
        setCropWeight("fallow:raspberry_bush", 1.0, 1.0, 1.0, 0.0);
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(CROP, FallowBlocks.RASPBERRY_BUSH.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 2));

        withSeason(helper, Season.SUMMER, () -> {
            BlockPos cropAbs = helper.absolutePos(CROP);
            for (int i = 0; i < 60; i++) {
                BlockState state = level.getBlockState(cropAbs);
                if (state.getValue(BlockStateProperties.AGE_3) > 2) {
                    break;
                }
                state.randomTick(level, cropAbs, level.getRandom());
            }
            helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3,
                age -> age > 2,
                net.minecraft.network.chat.Component.literal(
                    "raspberry bush should advance past age 2 in summer under open sky"));
            helper.succeed();
        });
    }

    // 19b. A blackberry bush stalls (not withers) in winter: age unchanged after many ticks.
    // skyAccess = true ensures darkness is NOT a backstop; the winter weight (0.0) is the sole lever.
    @GameTest(environment = "fallow:winter", maxTicks = 200, skyAccess = true)
    public void blackberryStallsInWinter(GameTestHelper helper) {
        baseCropConfig();
        setCropWeight("fallow:blackberry_bush", 1.0, 1.0, 1.0, 0.0);
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(CROP, FallowBlocks.BLACKBERRY_BUSH.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 2));

        withSeason(helper, Season.WINTER, () -> {
            BlockPos cropAbs = helper.absolutePos(CROP);
            // 60 ticks under open sky: a non-stalled bush would almost certainly advance. Winter
            // weight 0.0 must hold it at age 2.
            for (int i = 0; i < 60; i++) {
                level.getBlockState(cropAbs).randomTick(level, cropAbs, level.getRandom());
            }
            helper.assertBlockPresent(FallowBlocks.BLACKBERRY_BUSH, CROP);
            helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3, 2);
            helper.succeed();
        });
    }

    // 19b-positive. Same blackberry setup in summer (weight 1.0): proves the rig can grow, so the
    // winter stall above is meaningful and not hiding a broken randomTick path.
    @GameTest(environment = "fallow:growing", maxTicks = 200, skyAccess = true)
    public void blackberryGrowsInSummer(GameTestHelper helper) {
        baseCropConfig();
        setCropWeight("fallow:blackberry_bush", 1.0, 1.0, 1.0, 0.0);
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(CROP, FallowBlocks.BLACKBERRY_BUSH.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 2));

        withSeason(helper, Season.SUMMER, () -> {
            BlockPos cropAbs = helper.absolutePos(CROP);
            for (int i = 0; i < 60; i++) {
                BlockState state = level.getBlockState(cropAbs);
                if (state.getValue(BlockStateProperties.AGE_3) > 2) {
                    break;
                }
                state.randomTick(level, cropAbs, level.getRandom());
            }
            helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3,
                age -> age > 2,
                net.minecraft.network.chat.Component.literal(
                    "blackberry bush should advance past age 2 in summer under open sky"));
            helper.succeed();
        });
    }
}
