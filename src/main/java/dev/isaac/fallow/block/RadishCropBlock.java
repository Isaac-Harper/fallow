package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.level.ItemLike;

/** Radish crop, ages 0-3, planted by {@code fallow:radish_seeds}. */
public final class RadishCropBlock extends FallowCropBlock {
    public static final MapCodec<RadishCropBlock> CODEC = simpleCodec(RadishCropBlock::new);

    public RadishCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<RadishCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return FallowItems.RADISH_SEEDS;
    }

    @Override
    protected String blockId() {
        return "fallow:radish_crop";
    }
}
