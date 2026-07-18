package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.level.ItemLike;

/** Oat crop, ages 0-3, planted by {@code fallow:oat_seeds}. */
public final class OatCropBlock extends FallowCropBlock {
    public static final MapCodec<OatCropBlock> CODEC = simpleCodec(OatCropBlock::new);

    public OatCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<OatCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return FallowItems.OAT_SEEDS;
    }

    @Override
    protected String blockId() {
        return "fallow:oat_crop";
    }
}
