package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;

/** Decorative wild thyme placed by the forage spread task. No age, no interaction. */
public final class ThymeBlock extends BushBlock {
    // BushBlock.codec() returns MapCodec<BushBlock>; the cast is safe because this CODEC
    // only ever constructs ThymeBlock instances.
    @SuppressWarnings("unchecked")
    public static final MapCodec<BushBlock> CODEC =
        (MapCodec<BushBlock>) (MapCodec<?>) simpleCodec(ThymeBlock::new);

    public ThymeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(BlockTags.DIRT);
    }

    /** Pick-block: the plant has no item form, so hand over the item it drops. */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state,
            boolean includeData) {
        return new ItemStack(FallowItems.THYME);
    }
}
