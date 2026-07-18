package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.item.FallowItems;
import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.season.SeasonClock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.tags.BlockTags;

import java.util.ArrayList;
import java.util.List;

/**
 * Pea climber growing on a trellis. Ages 0-3. Right-click at age 3 harvests 2-3 peas and
 * resets to age 1. When broken, the loot table (provided by the resources agent) returns the
 * trellis; this class does not override getLootTable.
 *
 * <p>Nitrogen fixing: on reaching age 3 in {@link #randomTick}, and on each right-click harvest,
 * if {@code crops.legumes.fixNitrogen} is on: scan the box (fixRadius horizontal, y-1..0)
 * around the ground block for coarse or rooted dirt; convert one randomly selected candidate
 * to minecraft:dirt.
 *
 * <p>Season gating: same as farmland crops. At WINTER with w&lt;=0 and winterKill: block reverts
 * to the trellis. Without kill, stalls.
 *
 * <p>{@link #isRandomlyTicking} always returns true so that a mature crop left standing over
 * winter still receives the winter-kill tick. The growth increment is guarded by an explicit
 * age check so max-age blocks never increment.
 */
public final class PeaCropBlock extends VegetationBlock implements BonemealableBlock {
    public static final MapCodec<PeaCropBlock> CODEC = simpleCodec(PeaCropBlock::new);

    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

    public PeaCropBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    public MapCodec<PeaCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    // Always ticking: a mature crop must still receive winter-kill ticks.
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos,
            RandomSource random) {
        int age = state.getValue(AGE);
        // Season gate and winter-kill run before the age check so mature crops can die in winter.
        if (shouldGateGrowth()) {
            double w = Fallow.CONFIG.crops.cropSeasonWeight("fallow:pea_crop",
                SeasonClock.season());
            if (SeasonClock.season() == Season.WINTER && w <= 0.0 && Fallow.CONFIG.crops.winterKill) {
                // Winter-kill: pea reverts to a bare trellis.
                level.setBlock(pos, FallowBlocks.TRELLIS.defaultBlockState(), Block.UPDATE_ALL);
                return;
            }
            if (w <= 0.0) {
                return;
            }
            if (random.nextFloat() >= w) {
                return;
            }
        }
        // Growth step: only advance when not at max age.
        if (age >= MAX_AGE) {
            return;
        }
        int newAge = age + 1;
        level.setBlock(pos, state.setValue(AGE, newAge), Block.UPDATE_ALL);
        if (newAge == MAX_AGE && Fallow.CONFIG.crops.legumes.fixNitrogen) {
            tryFixNitrogen(level, pos, random);
        }
    }

    /**
     * Right-click harvest at age 3: drops 2-3 peas, resets to age 1, plays pick sound.
     * Also triggers nitrogen fixing if configured. Drop uses {@link Block#popResource} which
     * is server-only internally, preventing ghost item entities on the client.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        int age = state.getValue(AGE);
        if (age < MAX_AGE) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            int count = 2 + serverLevel.getRandom().nextInt(2);
            Block.popResource(serverLevel, pos, new ItemStack(FallowItems.PEAS, count));
            serverLevel.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                SoundSource.BLOCKS, 1.0f, 0.8f + serverLevel.getRandom().nextFloat() * 0.4f);
            BlockState newState = state.setValue(AGE, 1);
            serverLevel.setBlock(pos, newState, Block.UPDATE_ALL);
            serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, pos,
                GameEvent.Context.of(player, newState));
            if (Fallow.CONFIG.crops.legumes.fixNitrogen) {
                tryFixNitrogen(serverLevel, pos, serverLevel.getRandom());
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Pick-block returns pea seeds, the item that plants the vine on a trellis. */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state,
            boolean includeData) {
        return new ItemStack(FallowItems.PEA_SEEDS);
    }

    /** Survives on any block that can support a trellis (same ground rule). */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.GRASS_BLOCK)
            || state.is(BlockTags.DIRT)
            || state.is(Blocks.FARMLAND);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(AGE) < MAX_AGE;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos,
            BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos,
            BlockState state) {
        int age = state.getValue(AGE);
        if (age < MAX_AGE) {
            int newAge = age + 1;
            level.setBlock(pos, state.setValue(AGE, newAge), Block.UPDATE_ALL);
            if (newAge == MAX_AGE && Fallow.CONFIG.crops.legumes.fixNitrogen) {
                tryFixNitrogen(level, pos, random);
            }
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

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    private static boolean shouldGateGrowth() {
        return Fallow.CONFIG.enabled
            && Fallow.CONFIG.crops.enabled
            && Fallow.CONFIG.crops.seasonGating
            && Fallow.CONFIG.seasons.enabled;
    }
}
