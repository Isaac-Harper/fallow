package dev.isaac.fallow.block;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.season.SeasonClock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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

/**
 * Shared base for climbers growing on a trellis (pea, cucumber, grape, hops). Ages 0-3.
 * Right-click at age 3 harvests the crop's item and resets to age 1. When broken, the loot table
 * (provided by the resources agent) returns the trellis; this class does not override getLootTable.
 *
 * <p>Season gating mirrors the farmland crops. At WINTER with weight &lt;=0 and winterKill on, the
 * block reverts to a bare trellis; otherwise it stalls. {@link #isRandomlyTicking} always returns
 * true so a mature crop left standing over winter still receives the winter-kill tick; the growth
 * increment is guarded by an explicit age check so max-age blocks never increment.
 *
 * <p>Subclasses supply the crop's block id, harvest item, harvest count, and clone item. Hooks
 * {@link #onReachedMaxAge} and {@link #onHarvest} let a subclass add per-crop side effects (e.g.
 * the pea's nitrogen fixing) without duplicating the shared flow.
 */
public abstract class TrellisCropBlock extends VegetationBlock implements BonemealableBlock {
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);

    protected TrellisCropBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    /** Block id string used to look up the per-crop season weight. */
    protected abstract String blockId();

    /** Item dropped on harvest. */
    protected abstract Item harvestItem();

    /** Inclusive minimum harvest count. */
    protected abstract int minHarvest();

    /** Inclusive maximum harvest count. */
    protected abstract int maxHarvest();

    /** Item this block hands over on pick-block (the planting item). */
    protected abstract Item cloneItem();

    /** Called when growth reaches max age (both randomTick and bonemeal). Default no-op. */
    protected void onReachedMaxAge(ServerLevel level, BlockPos pos, RandomSource random) {
    }

    /** Called after a successful right-click harvest. Default no-op. */
    protected void onHarvest(ServerLevel level, BlockPos pos, RandomSource random) {
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
            double w = Fallow.CONFIG.crops.cropSeasonWeight(blockId(), SeasonClock.season());
            if (SeasonClock.season() == Season.WINTER && w <= 0.0 && Fallow.CONFIG.crops.winterKill) {
                // Winter-kill: the climber reverts to a bare trellis.
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
        // Light gate mirrors the vanilla crop analogues (SweetBerryBushBlock / CropBlock): growth
        // needs raw brightness >= 9 at the block. Growth only; the winter-kill tick above still runs.
        if (level.getRawBrightness(pos, 0) < 9) {
            return;
        }
        int newAge = age + 1;
        level.setBlock(pos, state.setValue(AGE, newAge), Block.UPDATE_ALL);
        if (newAge == MAX_AGE) {
            onReachedMaxAge(level, pos, random);
        }
    }

    /**
     * Right-click harvest at age 3: drops the crop's item, resets to age 1, plays pick sound.
     * Drop uses {@link Block#popResource} which is server-only internally, preventing ghost item
     * entities on the client.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        int age = state.getValue(AGE);
        if (age < MAX_AGE) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            int span = maxHarvest() - minHarvest();
            int count = minHarvest() + (span > 0 ? serverLevel.getRandom().nextInt(span + 1) : 0);
            Block.popResource(serverLevel, pos, new ItemStack(harvestItem(), count));
            serverLevel.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                SoundSource.BLOCKS, 1.0f, 0.8f + serverLevel.getRandom().nextFloat() * 0.4f);
            BlockState newState = state.setValue(AGE, 1);
            serverLevel.setBlock(pos, newState, Block.UPDATE_ALL);
            serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, pos,
                GameEvent.Context.of(player, newState));
            onHarvest(serverLevel, pos, serverLevel.getRandom());
        }
        return InteractionResult.SUCCESS;
    }

    /** Pick-block returns the planting item, which starts the vine on a trellis. */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state,
            boolean includeData) {
        return new ItemStack(cloneItem());
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
            if (newAge == MAX_AGE) {
                onReachedMaxAge(level, pos, random);
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    protected static boolean shouldGateGrowth() {
        return Fallow.CONFIG.enabled
            && Fallow.CONFIG.crops.enabled
            && Fallow.CONFIG.crops.seasonGating
            && Fallow.CONFIG.seasons.enabled;
    }
}
