package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.item.Item;

/** Grape climber on a trellis. Ages 0-3; harvest at age 3 drops 2-3 grapes, resets to age 1. Planted with {@code fallow:grapes}. */
public final class GrapeCropBlock extends TrellisCropBlock {
    public static final MapCodec<GrapeCropBlock> CODEC = simpleCodec(GrapeCropBlock::new);

    public GrapeCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<GrapeCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected String blockId() {
        return "fallow:grape_crop";
    }

    @Override
    protected Item harvestItem() {
        return FallowItems.GRAPES;
    }

    @Override
    protected int minHarvest() {
        return 2;
    }

    @Override
    protected int maxHarvest() {
        return 3;
    }

    @Override
    protected Item cloneItem() {
        return FallowItems.GRAPES;
    }
}
