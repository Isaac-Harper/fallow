package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.tags.BlockTags;

/**
 * Decorative lattice block. Right-clicking with a climber's planting item converts it to that
 * crop at age 0, consuming one item: {@code fallow:pea_seeds} -> pea, {@code fallow:cucumber_seeds}
 * -> cucumber, {@code fallow:grapes} -> grape, {@code fallow:hop_cones} -> hops. No age, no drops
 * from the trellis itself (loot is handled in each crop's loot table by the resources agent).
 *
 * <p>Survives on dirt family / grass / farmland (same ground rule as the crops).
 */
public final class TrellisBlock extends BushBlock {
    @SuppressWarnings("unchecked")
    public static final MapCodec<BushBlock> CODEC =
        (MapCodec<BushBlock>) (MapCodec<?>) simpleCodec(TrellisBlock::new);

    public TrellisBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.GRASS_BLOCK)
            || state.is(BlockTags.DIRT)
            || state.is(Blocks.FARMLAND);
    }

    /**
     * Using a climber's planting item on the trellis converts it to that crop at age 0, consuming
     * one item. The crop-plant sound plays; the interaction is consumed.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        Block crop = cropFor(stack);
        if (crop == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos,
                crop.defaultBlockState().setValue(TrellisCropBlock.AGE, 0),
                Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.CROP_PLANTED,
                SoundSource.BLOCKS, 1.0f, 1.0f);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** The trellis crop a planting item starts, or null if the item plants nothing. */
    private static Block cropFor(ItemStack stack) {
        if (stack.is(FallowItems.PEA_SEEDS)) {
            return FallowBlocks.PEA_CROP;
        }
        if (stack.is(FallowItems.CUCUMBER_SEEDS)) {
            return FallowBlocks.CUCUMBER_CROP;
        }
        if (stack.is(FallowItems.GRAPES)) {
            return FallowBlocks.GRAPE_CROP;
        }
        if (stack.is(FallowItems.HOP_CONES)) {
            return FallowBlocks.HOPS_CROP;
        }
        return null;
    }
}
