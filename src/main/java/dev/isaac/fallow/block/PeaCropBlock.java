package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.item.FallowItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Pea climber growing on a trellis. Ages 0-3; right-click at age 3 harvests 2-3 peas and resets to
 * age 1. Shares its lifecycle with the other trellis climbers via {@link TrellisCropBlock}.
 *
 * <p>Nitrogen fixing is the pea's own addition on top of the shared base: on reaching age 3 (via
 * random tick or bonemeal) and on each right-click harvest, if {@code crops.legumes.fixNitrogen} is
 * on, scan the box (fixRadius horizontal, y-1..0) around the ground block for coarse or rooted
 * dirt and convert one randomly selected candidate to {@code minecraft:dirt}.
 */
public final class PeaCropBlock extends TrellisCropBlock {
    public static final MapCodec<PeaCropBlock> CODEC = simpleCodec(PeaCropBlock::new);

    public PeaCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<PeaCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected String blockId() {
        return "fallow:pea_crop";
    }

    @Override
    protected Item harvestItem() {
        return FallowItems.PEAS;
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
        return FallowItems.PEA_SEEDS;
    }

    @Override
    protected void onReachedMaxAge(ServerLevel level, BlockPos pos, RandomSource random) {
        if (Fallow.CONFIG.crops.legumes.fixNitrogen) {
            tryFixNitrogen(level, pos, random);
        }
    }

    @Override
    protected void onHarvest(ServerLevel level, BlockPos pos, RandomSource random) {
        if (Fallow.CONFIG.crops.legumes.fixNitrogen) {
            tryFixNitrogen(level, pos, random);
        }
    }

    /**
     * Nitrogen fixing: scan the box (fixRadius horizontal, y-1..0) around the ground block
     * (pos.below()) for coarse dirt or rooted dirt; convert one randomly chosen candidate to
     * {@code minecraft:dirt}.
     */
    private static void tryFixNitrogen(ServerLevel level, BlockPos cropPos, RandomSource random) {
        BlockPos ground = cropPos.below();
        int r = Fallow.CONFIG.crops.legumes.fixRadius;
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(
                ground.offset(-r, -1, -r), ground.offset(r, 0, r))) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.COARSE_DIRT) || s.is(Blocks.ROOTED_DIRT)) {
                candidates.add(p.immutable());
            }
        }
        if (!candidates.isEmpty()) {
            BlockPos target = candidates.get(random.nextInt(candidates.size()));
            level.setBlock(target, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
