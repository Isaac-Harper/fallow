package dev.isaac.fallow.gametest;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.block.FallowBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * In-world coverage that bone meal cannot duplicate the two inert, non-growable blocks that extend
 * {@link net.minecraft.world.level.block.BushBlock}: the withered crop husk and the trellis. Both
 * override {@code isValidBonemealTarget} to return false, so bone meal on them is a no-op and the
 * default BushBlock self-spread (which would clone them into a neighbour) never fires.
 *
 * <p>As with the other suites, {@code Fallow.CONFIG} is a shared mutable static, so every test sets
 * every field it depends on at the start rather than trusting defaults left by a prior test.
 */
public class BonemealGameTests {
    private static final BlockPos GROUND = new BlockPos(1, 1, 1);

    /** The target position sitting one block above {@link #GROUND}. */
    private static final BlockPos TARGET = GROUND.above();

    private static void baseConfig() {
        Fallow.CONFIG = new FallowConfig();
        Fallow.CONFIG.enabled = true;
        Fallow.CONFIG.crops.enabled = true;
    }

    /** True if any block in the ground plane around the target became the given inert block. */
    private static boolean anyNeighbourIs(GameTestHelper helper, ServerLevel level,
            BlockPos target, Block block) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos rel = target.relative(dir);
            if (level.getBlockState(helper.absolutePos(rel)).is(block)) {
                return true;
            }
        }
        return false;
    }

    // 3. Bonemealing a withered crop leaves it unchanged and spreads no husk to a neighbour.
    @GameTest(environment = "fallow:noseason")
    public void bonemealDoesNotDuplicateWitheredCrop(GameTestHelper helper) {
        baseConfig();
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(TARGET, FallowBlocks.WITHERED_CROP);
        // Grass floor around the crop so a stray self-spread would have somewhere to land.
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            helper.setBlock(GROUND.relative(dir), Blocks.GRASS_BLOCK);
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE_MEAL));
        helper.useBlock(TARGET, player);

        helper.assertBlockPresent(FallowBlocks.WITHERED_CROP, TARGET);
        helper.assertFalse(
            anyNeighbourIs(helper, level, TARGET, FallowBlocks.WITHERED_CROP),
            "bone meal must not spread a withered crop to a neighbour");
        helper.succeed();
    }

    // 4. Bonemealing a trellis leaves it unchanged and spreads no lattice to a neighbour.
    @GameTest(environment = "fallow:noseason")
    public void bonemealDoesNotDuplicateTrellis(GameTestHelper helper) {
        baseConfig();
        ServerLevel level = helper.getLevel();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(TARGET, FallowBlocks.TRELLIS);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            helper.setBlock(GROUND.relative(dir), Blocks.GRASS_BLOCK);
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE_MEAL));
        helper.useBlock(TARGET, player);

        helper.assertBlockPresent(FallowBlocks.TRELLIS, TARGET);
        helper.assertFalse(
            anyNeighbourIs(helper, level, TARGET, FallowBlocks.TRELLIS),
            "bone meal must not spread a trellis to a neighbour");
        helper.succeed();
    }
}
