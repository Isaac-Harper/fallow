package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.level.ItemLike;

/** Cabbage crop, ages 0-3, planted by {@code fallow:cabbage_seeds}. */
public final class CabbageCropBlock extends FallowCropBlock {
    public static final MapCodec<CabbageCropBlock> CODEC = simpleCodec(CabbageCropBlock::new);

    public CabbageCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<CabbageCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return FallowItems.CABBAGE_SEEDS;
    }

    @Override
    protected String blockId() {
        return "fallow:cabbage_crop";
    }
}
