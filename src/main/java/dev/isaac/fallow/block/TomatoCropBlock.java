package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.level.ItemLike;

/** Tomato crop, ages 0-3, planted by {@code fallow:tomato_seeds}. */
public final class TomatoCropBlock extends FallowCropBlock {
    public static final MapCodec<TomatoCropBlock> CODEC = simpleCodec(TomatoCropBlock::new);

    public TomatoCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<TomatoCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return FallowItems.TOMATO_SEEDS;
    }

    @Override
    protected String blockId() {
        return "fallow:tomato_crop";
    }
}
