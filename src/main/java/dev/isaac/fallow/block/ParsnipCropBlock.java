package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.level.ItemLike;

/** Parsnip crop, ages 0-3, planted by {@code fallow:parsnip_seeds}. */
public final class ParsnipCropBlock extends FallowCropBlock {
    public static final MapCodec<ParsnipCropBlock> CODEC = simpleCodec(ParsnipCropBlock::new);

    public ParsnipCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<ParsnipCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return FallowItems.PARSNIP_SEEDS;
    }

    @Override
    protected String blockId() {
        return "fallow:parsnip_crop";
    }
}
