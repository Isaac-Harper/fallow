package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.item.Item;

/** Blackberry bush. Ages 0-3; the blackberries item plants it and drops from the harvest. See {@link BerryBushBlock}. */
public final class BlackberryBushBlock extends BerryBushBlock {
    public static final MapCodec<BlackberryBushBlock> CODEC = simpleCodec(BlackberryBushBlock::new);

    public BlackberryBushBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<BlackberryBushBlock> codec() {
        return CODEC;
    }

    @Override
    protected String blockId() {
        return "fallow:blackberry_bush";
    }

    @Override
    protected Item berryItem() {
        return FallowItems.BLACKBERRIES;
    }
}
