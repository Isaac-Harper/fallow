package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;

/**
 * Dead crop husk left by winter kill. Instabreaks (instabreak() in properties), no drops.
 * Survives on farmland and dirt-family blocks.
 */
public final class WitheredCropBlock extends BushBlock {
    @SuppressWarnings("unchecked")
    public static final MapCodec<BushBlock> CODEC =
        (MapCodec<BushBlock>) (MapCodec<?>) simpleCodec(WitheredCropBlock::new);

    public WitheredCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.FARMLAND)
            || state.is(BlockTags.DIRT)
            || state.is(Blocks.GRASS_BLOCK);
    }
}
