package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.level.ItemLike;

/** Flax crop, ages 0-3, planted by {@code fallow:flax_seeds}. Drops flax seeds plus string (loot table). */
public final class FlaxCropBlock extends FallowCropBlock {
    public static final MapCodec<FlaxCropBlock> CODEC = simpleCodec(FlaxCropBlock::new);

    public FlaxCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<FlaxCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return FallowItems.FLAX_SEEDS;
    }

    @Override
    protected String blockId() {
        return "fallow:flax_crop";
    }
}
