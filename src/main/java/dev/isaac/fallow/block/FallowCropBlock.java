package dev.isaac.fallow.block;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.season.SeasonClock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Base for the three farmland crops (turnip, cabbage, onion). Follows the BeetrootBlock pattern:
 * four ages (0-3), max age 3. Subclasses supply the seed id via {@link #getBaseSeedId()}.
 *
 * <p>Season gating and winter kill are applied in {@link #randomTick} before delegating to
 * vanilla growth. The per-crop {@link dev.isaac.fallow.FallowConfig.Crops#cropSeasonWeight}
 * is the sole seasonal term; the shared curve does not apply again here (same double-counting
 * rule as bushSeasons and phenology). When all gating is inactive, the block behaves exactly
 * as vanilla CropBlock.
 *
 * <p>{@link #isRandomlyTicking} always returns true so that a mature crop standing through winter
 * can still receive the winter-kill tick. The growth step inside {@link #randomTick} is skipped
 * at max age (mirrors vanilla CropBlock behavior; see CropBlock.randomTick which guards
 * {@code if (age < this.getMaxAge())}). A mature crop with gating inactive exits immediately
 * with no work done, so the perpetual tick is negligible.
 */
public abstract class FallowCropBlock extends CropBlock {
    // Age 0-3, matching BeetrootBlock.
    public static final IntegerProperty AGE = BeetrootBlock.AGE;

    protected FallowCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return 3;
    }

    @Override
    protected int getBonemealAgeIncrease(net.minecraft.world.level.Level level) {
        // BeetrootBlock adds only 1 per bonemeal application (slower crop).
        return 1;
    }

    /** Block id string used to look up the per-crop season weight. */
    protected abstract String blockId();

    // Always ticking: a mature crop must still receive winter-kill ticks.
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (shouldGateGrowth()) {
            double w = Fallow.CONFIG.crops.cropSeasonWeight(blockId(), SeasonClock.season());
            if (SeasonClock.season() == Season.WINTER && w <= 0.0 && Fallow.CONFIG.crops.winterKill) {
                // Winter-kill: replace with a dead husk on the farmland.
                level.setBlock(pos, FallowBlocks.WITHERED_CROP.defaultBlockState(), Block.UPDATE_ALL);
                return;
            }
            if (w <= 0.0) {
                return; // stall - wrong season, no kill
            }
            if (random.nextFloat() >= w) {
                return; // probabilistic stall proportional to season weight
            }
        } else if (isMaxAge(state)) {
            // Gating inactive and already at max age: nothing to do (mirrors vanilla no-op).
            return;
        }
        // Delegate growth step; vanilla CropBlock.randomTick guards age < maxAge internally.
        super.randomTick(state, level, pos, random);
    }

    /**
     * True when all the required flags are set for seasonal gating to be active.
     * When false, randomTick delegates straight to vanilla (no wrapper overhead).
     */
    private static boolean shouldGateGrowth() {
        return Fallow.CONFIG.enabled
            && Fallow.CONFIG.crops.enabled
            && Fallow.CONFIG.crops.seasonGating
            && Fallow.CONFIG.seasons.enabled;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }
}
