package dev.isaac.fallow.block;

import dev.isaac.fallow.Fallow;
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
 * Shared base for the sweet-berry-style bushes (strawberry, raspberry, blackberry). Ages 0-3 with
 * a right-click harvest and no contact damage (unlike sweet berries). Survives on grass block,
 * dirt, or farmland. The berry item itself plants the bush.
 *
 * <ul>
 *   <li>Age 0-1: sapling / small plant, not harvestable.</li>
 *   <li>Age 2: drop 1 berry, reset to age 1.</li>
 *   <li>Age 3: drop 2-3 berries, reset to age 1.</li>
 * </ul>
 *
 * <p>Season gating mirrors the farmland crops: when crops.seasonGating is active, a per-crop weight
 * gates randomTick growth; at WINTER with weight &lt;=0 the bush stalls (no winter-kill for bushes).
 * Subclasses supply the block id and berry item.
 */
public abstract class BerryBushBlock extends VegetationBlock implements BonemealableBlock {
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

    private static final VoxelShape SHAPE_SAPLING =
        Block.box(3.0, 0.0, 3.0, 13.0, 8.0, 13.0);
    private static final VoxelShape SHAPE_GROWING =
        Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);

    protected BerryBushBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    /** Block id string used to look up the per-crop season weight. */
    protected abstract String blockId();

    /** The berry item dropped on harvest and returned on pick-block. */
    protected abstract Item berryItem();

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return state.getValue(AGE) == 0 ? SHAPE_SAPLING : SHAPE_GROWING;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < MAX_AGE;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos,
            RandomSource random) {
        int age = state.getValue(AGE);
        if (age >= MAX_AGE) {
            return;
        }
        if (shouldGateGrowth()) {
            double w = Fallow.CONFIG.crops.cropSeasonWeight(blockId(), SeasonClock.season());
            if (w <= 0.0) {
                return; // stall - bushes do not winter-kill, just stall
            }
            if (random.nextFloat() >= w) {
                return;
            }
        }
        // Light gate mirrors the vanilla crop analogues (SweetBerryBushBlock / CropBlock): growth
        // needs raw brightness >= 9 at the block.
        if (level.getRawBrightness(pos, 0) < 9) {
            return;
        }
        level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_ALL);
    }

    /**
     * Right-click harvests when age >= 2: drop berries, play pick sound, reset to age 1.
     * No item is needed in hand (vanilla sweet-berry style useWithoutItem). Drop uses
     * {@link Block#popResource} which is server-only internally, preventing ghost item entities
     * on the client.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        int age = state.getValue(AGE);
        if (age < 2) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            int count = (age == MAX_AGE) ? 2 + serverLevel.getRandom().nextInt(2) : 1;
            Block.popResource(serverLevel, pos, new ItemStack(berryItem(), count));
            serverLevel.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                SoundSource.BLOCKS, 1.0f, 0.8f + serverLevel.getRandom().nextFloat() * 0.4f);
            BlockState newState = state.setValue(AGE, 1);
            serverLevel.setBlock(pos, newState, Block.UPDATE_ALL);
            serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, pos,
                GameEvent.Context.of(player, newState));
        }
        return InteractionResult.SUCCESS;
    }

    /** Pick-block returns the berry item, which replants the bush (vanilla sweet-berry rule). */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state,
            boolean includeData) {
        return new ItemStack(berryItem());
    }

    /** Survives on grass block, any dirt-tag block, or farmland. */
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
            level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_ALL);
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
