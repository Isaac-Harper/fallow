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
 * Decorative lattice block. Right-clicking with {@code fallow:pea_seeds} converts it to
 * {@code fallow:pea_crop} at age 0, consuming one seed. No age, no drops from the trellis
 * itself (loot is handled in the pea_crop loot table by the resources agent).
 *
 * <p>Survives on dirt family / grass / farmland (same ground rule as pea_crop).
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
     * Using pea seeds on the trellis converts it to a pea crop at age 0, consuming one seed.
     * The crop-plant sound plays; the interaction is consumed.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!stack.is(FallowItems.PEA_SEEDS)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos,
                FallowBlocks.PEA_CROP.defaultBlockState()
                    .setValue(PeaCropBlock.AGE, 0),
                Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.CROP_PLANTED,
                SoundSource.BLOCKS, 1.0f, 1.0f);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
