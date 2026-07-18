package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.level.ItemLike;

/** Barley crop, ages 0-3, planted by {@code fallow:barley_seeds}. */
public final class BarleyCropBlock extends FallowCropBlock {
    public static final MapCodec<BarleyCropBlock> CODEC = simpleCodec(BarleyCropBlock::new);

    public BarleyCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<BarleyCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return FallowItems.BARLEY_SEEDS;
    }

    @Override
    protected String blockId() {
        return "fallow:barley_crop";
    }
}
