package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.level.ItemLike;

/** Turnip crop, ages 0-3, planted by {@code fallow:turnip_seeds}. */
public final class TurnipCropBlock extends FallowCropBlock {
    public static final MapCodec<TurnipCropBlock> CODEC = simpleCodec(TurnipCropBlock::new);

    public TurnipCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<TurnipCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return FallowItems.TURNIP_SEEDS;
    }

    @Override
    protected String blockId() {
        return "fallow:turnip_crop";
    }
}
