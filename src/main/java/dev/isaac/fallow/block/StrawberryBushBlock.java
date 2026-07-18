package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.world.item.Item;

/**
 * Strawberry bush. Ages 0-3; the strawberries item plants it and drops from the harvest. Shares
 * the sweet-berry-style lifecycle (stall-only winter, clone stack) via {@link BerryBushBlock}.
 */
public final class StrawberryBushBlock extends BerryBushBlock {
    public static final MapCodec<StrawberryBushBlock> CODEC = simpleCodec(StrawberryBushBlock::new);

    public StrawberryBushBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<StrawberryBushBlock> codec() {
        return CODEC;
    }

    @Override
    protected String blockId() {
        return "fallow:strawberry_bush";
    }

    @Override
    protected Item berryItem() {
        return FallowItems.STRAWBERRIES;
    }
}
