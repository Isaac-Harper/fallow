package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluids;

/**
 * Rice crop, ages 0-3, planted by {@code fallow:rice_seeds}. Beyond the shared season gate, rice
 * needs a paddy: water within {@code crops.paddy.range} blocks horizontally (same Y as the
 * farmland, or one below). Without nearby water the crop stalls (no wither); see {@link RicePaddy}.
 */
public final class RiceCropBlock extends FallowCropBlock {
    public static final MapCodec<RiceCropBlock> CODEC = simpleCodec(RiceCropBlock::new);

    public RiceCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<RiceCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return FallowItems.RICE_SEEDS;
    }

    @Override
    protected String blockId() {
        return "fallow:rice_crop";
    }

    /** Paddy rule: require water within range around the farmland below the crop. */
    @Override
    protected boolean canGrowHere(ServerLevel level, BlockPos pos) {
        int range = Fallow.CONFIG.crops.paddy.range;
        BlockPos farmland = pos.below();
        return RicePaddy.hasWaterWithin(farmland, range,
            p -> level.getFluidState(p).is(Fluids.WATER));
    }
}
