package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.item.Item;

/** Hops climber on a trellis. Ages 0-3; harvest at age 3 drops 2-3 hop cones, resets to age 1. Planted with {@code fallow:hop_cones}. */
public final class HopsCropBlock extends TrellisCropBlock {
    public static final MapCodec<HopsCropBlock> CODEC = simpleCodec(HopsCropBlock::new);

    public HopsCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<HopsCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected String blockId() {
        return "fallow:hops_crop";
    }

    @Override
    protected Item harvestItem() {
        return FallowItems.HOP_CONES;
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
        return FallowItems.HOP_CONES;
    }
}
