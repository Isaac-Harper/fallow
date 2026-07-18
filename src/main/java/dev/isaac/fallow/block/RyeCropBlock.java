package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.level.ItemLike;

/** Rye crop, ages 0-3, planted by {@code fallow:rye_seeds}. */
public final class RyeCropBlock extends FallowCropBlock {
    public static final MapCodec<RyeCropBlock> CODEC = simpleCodec(RyeCropBlock::new);

    public RyeCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<RyeCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return FallowItems.RYE_SEEDS;
    }

    @Override
    protected String blockId() {
        return "fallow:rye_crop";
    }
}
