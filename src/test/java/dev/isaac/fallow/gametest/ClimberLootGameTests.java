package dev.isaac.fallow.gametest;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.block.FallowBlocks;
import dev.isaac.fallow.item.FallowItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * In-world coverage for the trellis-climber loot fallback and the planting-item dispatch. Breaking
 * an immature climber (below the harvest age) drops its planting item plus the trellis it grew on,
 * evaluated through the real loot table via {@link ServerLevel#destroyBlock(BlockPos, boolean)}
 * (setting the block to air would bypass loot entirely). Right-clicking a bare trellis with each
 * planting item starts the matching crop at age 0.
 *
 * <p>As with the other suites, {@code Fallow.CONFIG} is a shared mutable static, so every test sets
 * every field it depends on at the start rather than trusting defaults left by a prior test.
 */
public class ClimberLootGameTests {
    private static final BlockPos GROUND = new BlockPos(1, 1, 1);

    /** The crop position sitting one block above {@link #GROUND}. */
    private static final BlockPos CROP = GROUND.above();

    private static void baseConfig() {
        Fallow.CONFIG = new FallowConfig();
        Fallow.CONFIG.enabled = true;
        Fallow.CONFIG.crops.enabled = true;
    }

    // 5a. Breaking an immature pea vine drops pea seeds and the trellis (age 2 < harvest age 3).
    @GameTest(environment = "fallow:noseason")
    public void immatureClimberBreakReturnsPlantingItem(GameTestHelper helper) {
        baseConfig();
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.DIRT);
        helper.setBlock(CROP, FallowBlocks.PEA_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 2));

        level.destroyBlock(helper.absolutePos(CROP), true);
        helper.assertItemEntityPresent(FallowItems.PEA_SEEDS);
        helper.assertItemEntityPresent(FallowItems.TRELLIS);
        helper.succeed();
    }

    // 5b. Breaking an immature grape vine drops grapes (its low-age item) and the trellis.
    @GameTest(environment = "fallow:noseason")
    public void immatureGrapeBreakReturnsGrapes(GameTestHelper helper) {
        baseConfig();
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.DIRT);
        helper.setBlock(CROP, FallowBlocks.GRAPE_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 1));

        level.destroyBlock(helper.absolutePos(CROP), true);
        helper.assertItemEntityPresent(FallowItems.GRAPES);
        helper.assertItemEntityPresent(FallowItems.TRELLIS);
        helper.succeed();
    }

    // 5c. Breaking an immature cucumber vine drops cucumber seeds and the trellis.
    @GameTest(environment = "fallow:noseason")
    public void immatureCucumberBreakReturnsSeeds(GameTestHelper helper) {
        baseConfig();
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.DIRT);
        helper.setBlock(CROP, FallowBlocks.CUCUMBER_CROP.defaultBlockState()
            .setValue(BlockStateProperties.AGE_3, 1));

        level.destroyBlock(helper.absolutePos(CROP), true);
        helper.assertItemEntityPresent(FallowItems.CUCUMBER_SEEDS);
        helper.assertItemEntityPresent(FallowItems.TRELLIS);
        helper.succeed();
    }

    // 18a. Cucumber seeds plant a cucumber crop on a trellis at age 0.
    @GameTest(environment = "fallow:noseason")
    public void cucumberSeedsPlantOnTrellis(GameTestHelper helper) {
        baseConfig();
        helper.setBlock(GROUND, Blocks.DIRT);
        helper.setBlock(CROP, FallowBlocks.TRELLIS);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FallowItems.CUCUMBER_SEEDS));
        helper.useBlock(CROP, player);

        helper.assertBlockPresent(FallowBlocks.CUCUMBER_CROP, CROP);
        helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3, 0);
        helper.succeed();
    }

    // 18b. Grapes plant a grape crop on a trellis at age 0 (the food item is also the planter).
    @GameTest(environment = "fallow:noseason")
    public void grapesPlantOnTrellis(GameTestHelper helper) {
        baseConfig();
        helper.setBlock(GROUND, Blocks.DIRT);
        helper.setBlock(CROP, FallowBlocks.TRELLIS);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FallowItems.GRAPES));
        helper.useBlock(CROP, player);

        helper.assertBlockPresent(FallowBlocks.GRAPE_CROP, CROP);
        helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3, 0);
        helper.succeed();
    }

    // 18c. Hop cones plant a hops crop on a trellis at age 0.
    @GameTest(environment = "fallow:noseason")
    public void hopConesPlantOnTrellis(GameTestHelper helper) {
        baseConfig();
        helper.setBlock(GROUND, Blocks.DIRT);
        helper.setBlock(CROP, FallowBlocks.TRELLIS);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FallowItems.HOP_CONES));
        helper.useBlock(CROP, player);

        helper.assertBlockPresent(FallowBlocks.HOPS_CROP, CROP);
        helper.assertBlockProperty(CROP, BlockStateProperties.AGE_3, 0);
        helper.succeed();
    }
}
