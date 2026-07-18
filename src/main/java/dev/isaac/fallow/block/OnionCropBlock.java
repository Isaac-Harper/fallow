package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.level.ItemLike;

/**
 * Onion crop, ages 0-3. The onion item itself is the planter (carrot-style): it is a
 * {@code BlockItem} pointing to this block, so right-clicking farmland with an onion plants the
 * crop without a separate seeds item.
 */
public final class OnionCropBlock extends FallowCropBlock {
    public static final MapCodec<OnionCropBlock> CODEC = simpleCodec(OnionCropBlock::new);

    public OnionCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<OnionCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        // Carrot-style: the crop drops the onion item itself.
        return FallowItems.ONION;
    }

    @Override
    protected String blockId() {
        return "fallow:onion_crop";
    }
}
