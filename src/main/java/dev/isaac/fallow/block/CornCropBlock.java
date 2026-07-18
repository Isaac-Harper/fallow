package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.item.FallowItems;
import dev.isaac.fallow.season.SeasonClock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jspecify.annotations.Nullable;

/**
 * Corn, a double-height farmland crop modeled on vanilla PitcherCropBlock. Ages 0-3 with a
 * {@link DoubleBlockHalf}; only the lower half receives ticks and drives growth. Reaching age 2
 * places (or keeps age-synced) the upper half, which needs air above the lower - if that space is
 * blocked, growth stalls at age 1. Both halves always carry the same age.
 *
 * <p>Season gating mirrors {@link FallowCropBlock} (weight key {@code fallow:corn_crop}): at WINTER
 * with weight &lt;=0 and winterKill on, the lower half becomes a dead husk and the upper half is
 * cleared to air. Breaking either half breaks both (inherited from {@link DoublePlantBlock}).
 * Loot is handled by the loot table (this block does not override it).
 */
public final class CornCropBlock extends DoublePlantBlock implements BonemealableBlock {
    public static final MapCodec<CornCropBlock> CODEC = simpleCodec(CornCropBlock::new);

    public static final int MAX_AGE = 3;
    /** Age at (and above) which the upper half exists. */
    private static final int DOUBLE_AGE = 2;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    public static final EnumProperty<DoubleBlockHalf> HALF = DoublePlantBlock.HALF;

    public CornCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<CornCropBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Plant only the lower half at age 0; the upper half grows in once the crop reaches age 2.
        return defaultBlockState();
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.FARMLAND);
    }

    /**
     * Override DoublePlantBlock's default (which would place the upper half immediately): corn is
     * planted as a single low block and grows its top half in at age 2, like the pitcher crop.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity by, ItemStack itemStack) {
    }

    /**
     * At ages below the double threshold the crop is a single low block, so a neighbour change
     * above must not trigger DoublePlantBlock's paired-half bookkeeping. Once double, defer to
     * vanilla so a broken half clears its partner (the break-both rule).
     */
    @Override
    public BlockState updateShape(BlockState state, LevelReader level,
            ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos,
            BlockState neighbourState, RandomSource random) {
        if (isDouble(state.getValue(AGE))) {
            return super.updateShape(state, level, ticks, pos, directionToNeighbour,
                neighbourPos, neighbourState, random);
        }
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    /** Only the growing lower half needs ticks; the upper half rides along. */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Only the lower half drives growth (isRandomlyTicking already filters, but be defensive).
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return;
        }
        // Season gate and winter-kill run before the age check so mature corn can die in winter.
        if (shouldGateGrowth()) {
            double w = Fallow.CONFIG.crops.cropSeasonWeight("fallow:corn_crop", SeasonClock.season());
            if (SeasonClock.season() == Season.WINTER && w <= 0.0 && Fallow.CONFIG.crops.winterKill) {
                killInWinter(level, pos);
                return;
            }
            if (w <= 0.0) {
                return;
            }
            if (random.nextFloat() >= w) {
                return;
            }
        }
        int age = state.getValue(AGE);
        if (age >= MAX_AGE) {
            return;
        }
        // Advance one age per qualifying tick (same increment idiom as the other Fallow climbers
        // and bushes; the season weight above is the pacing lever).
        grow(level, state, pos, 1);
    }

    /** Advance the lower half by {@code increase} and keep the upper half in sync, if it can grow. */
    private void grow(ServerLevel level, BlockState lowerState, BlockPos lowerPos, int increase) {
        int newAge = Math.min(lowerState.getValue(AGE) + increase, MAX_AGE);
        if (!canGrow(level, lowerPos, newAge)) {
            return;
        }
        BlockState newLower = lowerState.setValue(AGE, newAge);
        level.setBlock(lowerPos, newLower, Block.UPDATE_CLIENTS);
        if (isDouble(newAge)) {
            level.setBlock(lowerPos.above(),
                newLower.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        }
    }

    /** Winter-kill: lower half becomes the dead husk, upper half (if any) clears to air. */
    private static void killInWinter(ServerLevel level, BlockPos lowerPos) {
        level.setBlock(lowerPos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(lowerPos, FallowBlocks.WITHERED_CROP.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static boolean isDouble(int age) {
        return age >= DOUBLE_AGE;
    }

    private static boolean canGrowInto(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(FallowBlocks.CORN_CROP);
    }

    /** True when the lower half may advance to {@code newAge}: below max, and (if it becomes a
     * double) the space above is clear. */
    private boolean canGrow(LevelReader level, BlockPos lowerPos, int newAge) {
        return newAge <= MAX_AGE
            && level.isInsideBuildHeight(lowerPos.above())
            && (!isDouble(newAge) || canGrowInto(level, lowerPos.above()));
    }

    @Nullable
    private static BlockPos lowerHalf(LevelReader level, BlockPos pos, BlockState state) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return pos;
        }
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        return belowState.is(FallowBlocks.CORN_CROP)
            && belowState.getValue(HALF) == DoubleBlockHalf.LOWER ? below : null;
    }

    /** Pick-block returns corn seeds, the item that plants the lower half. */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state,
            boolean includeData) {
        return new ItemStack(FallowItems.CORN_SEEDS);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        BlockPos lower = lowerHalf(level, pos, state);
        if (lower == null) {
            return false;
        }
        BlockState lowerState = level.getBlockState(lower);
        return canGrow(level, lower, lowerState.getValue(AGE) + 1);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random,
            BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos,
            BlockState state) {
        BlockPos lower = lowerHalf(level, pos, state);
        if (lower != null) {
            grow(level, level.getBlockState(lower), lower, 1);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
        super.createBlockStateDefinition(builder);
    }

    private static boolean shouldGateGrowth() {
        return Fallow.CONFIG.enabled
            && Fallow.CONFIG.crops.enabled
            && Fallow.CONFIG.crops.seasonGating
            && Fallow.CONFIG.seasons.enabled;
    }
}
