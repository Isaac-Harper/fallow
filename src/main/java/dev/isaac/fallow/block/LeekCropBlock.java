package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.level.ItemLike;

/** Leek crop, ages 0-3, planted by {@code fallow:leek_seeds}. */
public final class LeekCropBlock extends FallowCropBlock {
    public static final MapCodec<LeekCropBlock> CODEC = simpleCodec(LeekCropBlock::new);

    public LeekCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<LeekCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return FallowItems.LEEK_SEEDS;
    }

    @Override
    protected String blockId() {
        return "fallow:leek_crop";
    }
}
