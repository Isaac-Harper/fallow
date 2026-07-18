package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.level.ItemLike;

/** Pepper crop, ages 0-3, planted by {@code fallow:pepper_seeds}. */
public final class PepperCropBlock extends FallowCropBlock {
    public static final MapCodec<PepperCropBlock> CODEC = simpleCodec(PepperCropBlock::new);

    public PepperCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<PepperCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return FallowItems.PEPPER_SEEDS;
    }

    @Override
    protected String blockId() {
        return "fallow:pepper_crop";
    }
}
