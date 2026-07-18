package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.item.Item;

/** Cucumber climber on a trellis. Ages 0-3; harvest at age 3 drops 1-2 cucumber, resets to age 1. Planted with {@code fallow:cucumber_seeds}. */
public final class CucumberCropBlock extends TrellisCropBlock {
    public static final MapCodec<CucumberCropBlock> CODEC = simpleCodec(CucumberCropBlock::new);

    public CucumberCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<CucumberCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected String blockId() {
        return "fallow:cucumber_crop";
    }

    @Override
    protected Item harvestItem() {
        return FallowItems.CUCUMBER;
    }

    @Override
    protected int minHarvest() {
        return 1;
    }

    @Override
    protected int maxHarvest() {
        return 2;
    }

    @Override
    protected Item cloneItem() {
        return FallowItems.CUCUMBER_SEEDS;
    }
}
