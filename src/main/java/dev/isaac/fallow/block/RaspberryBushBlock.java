package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.item.Item;

/** Raspberry bush. Ages 0-3; the raspberries item plants it and drops from the harvest. See {@link BerryBushBlock}. */
public final class RaspberryBushBlock extends BerryBushBlock {
    public static final MapCodec<RaspberryBushBlock> CODEC = simpleCodec(RaspberryBushBlock::new);

    public RaspberryBushBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<RaspberryBushBlock> codec() {
        return CODEC;
    }

    @Override
    protected String blockId() {
        return "fallow:raspberry_bush";
    }

    @Override
    protected Item berryItem() {
        return FallowItems.RASPBERRIES;
    }
}
