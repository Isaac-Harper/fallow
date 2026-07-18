package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.level.ItemLike;

/**
 * Garlic crop, ages 0-3. Carrot-style: the garlic item itself is the planter (a {@code BlockItem}
 * pointing to this block), so right-clicking farmland with garlic plants the crop without a
 * separate seeds item.
 */
public final class GarlicCropBlock extends FallowCropBlock {
    public static final MapCodec<GarlicCropBlock> CODEC = simpleCodec(GarlicCropBlock::new);

    public GarlicCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<GarlicCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        // Carrot-style: the crop drops the garlic item itself.
        return FallowItems.GARLIC;
    }

    @Override
    protected String blockId() {
        return "fallow:garlic_crop";
    }
}
