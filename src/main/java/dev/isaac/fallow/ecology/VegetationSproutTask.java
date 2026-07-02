package dev.isaac.fallow.ecology;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.growth.BiomeTuning;
import dev.isaac.fallow.growth.GrowthChannel;
import dev.isaac.fallow.growth.GrowthRateProvider;
import dev.isaac.fallow.season.SeasonClock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Phase 1 ecology task: vegetation slowly fills in on grass.
 *
 * <ul>
 *   <li>Grass block + air above -> short grass (most likely)</li>
 *   <li>Existing short grass -> tall grass (less likely)</li>
 *   <li>Grass block + air above -> biome flower (rarer) - the flower comes from the biome's
 *       bonemeal feature list, the exact source vanilla bonemeal uses, so datapacked biomes
 *       (Arboria, Tectonic, ...) supply their own palette</li>
 *   <li>Grass block + air above -> bush copied from one growing nearby (rarest) - nearby-copy
 *       is the biome-validity rule: a bush can only creep from where worldgen already put one</li>
 * </ul>
 *
 * <p>All placements pass vanilla {@code canSurvive} checks; light is gated at the same level
 * vanilla grass spread uses (9, configurable). Flowers and bushes respect a density guard so
 * meadows don't saturate over time. Probabilities come exclusively from the
 * {@link GrowthRateProvider} (config x season).
 */
public final class VegetationSproutTask implements EcologyTask {
    /** Bushes eligible for nearby-copy. Dead bushes excluded: they don't "grow". */
    private static final Set<Block> BUSHES = Set.of(Blocks.BUSH, Blocks.FIREFLY_BUSH, Blocks.SWEET_BERRY_BUSH);
    /** Extra draws from a biome's flower provider when hunting for an in-season flower. */
    private static final int FLOWER_SEASON_SAMPLES = 8;

    private final GrowthRateProvider rates;

    public VegetationSproutTask(GrowthRateProvider rates) {
        this.rates = rates;
    }

    @Override
    public String id() {
        return "vegetation_sprout";
    }

    @Override
    public boolean enabled(FallowConfig config) {
        return config.vegetation.enabled;
    }

    @Override
    public int visitChunk(ServerLevel level, LevelChunk chunk, RandomSource random, int samples) {
        FallowConfig.Vegetation cfg = Fallow.CONFIG.vegetation;
        ChunkPos chunkPos = chunk.getPos();
        int placed = 0;
        for (int i = 0; i < samples; i++) {
            int x = chunkPos.getMinBlockX() + random.nextInt(16);
            int z = chunkPos.getMinBlockZ() + random.nextInt(16);
            // First free spot above the topmost motion-blocking block; plants don't block
            // motion, so this lands on the air gap or an existing plant, with ground below.
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinY() || y > level.getMaxY()) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            BlockState at = level.getBlockState(pos);

            if (at.is(Blocks.SHORT_GRASS)) {
                placed += tryUpgradeToTall(level, pos, random);
            } else if (at.isAir() && level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)) {
                placed += trySprout(level, pos, random, cfg);
            }
        }
        return placed;
    }

    private int tryUpgradeToTall(ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() >= rates.chance(GrowthChannel.TALL_GRASS, level, pos)) {
            return 0;
        }
        BlockPos above = pos.above();
        if (!level.isInsideBuildHeight(above) || !level.getBlockState(above).isAir()) {
            return 0;
        }
        DoublePlantBlock.placeAt(level, Blocks.TALL_GRASS.defaultBlockState(), pos, Block.UPDATE_ALL);
        return 1;
    }

    private int trySprout(ServerLevel level, BlockPos pos, RandomSource random, FallowConfig.Vegetation cfg) {
        if (level.getMaxLocalRawBrightness(pos) < cfg.minLightLevel) {
            return 0;
        }

        if (random.nextFloat() < rates.chance(GrowthChannel.SHORT_GRASS, level, pos)) {
            BlockState grass = Blocks.SHORT_GRASS.defaultBlockState();
            if (grass.canSurvive(level, pos)) {
                level.setBlock(pos, grass, Block.UPDATE_ALL);
                return 1;
            }
            return 0;
        }

        if (random.nextFloat() < rates.chance(GrowthChannel.FLOWER, level, pos)) {
            return trySproutFlower(level, pos, random, cfg);
        }

        if (random.nextFloat() < rates.chance(GrowthChannel.BUSH, level, pos)) {
            return trySproutBush(level, pos, random, cfg);
        }

        return 0;
    }

    /**
     * Sprout one bloom from the biome's bonemeal flower list. Vanilla 26.1 flower features are
     * {@code simple_block} + a state provider, so we can usually draw a single state from the
     * provider; exotic modded features fall back to placing the feature itself (bounded: that
     * is exactly what one bonemeal application does).
     */
    private int trySproutFlower(ServerLevel level, BlockPos pos, RandomSource random, FallowConfig.Vegetation cfg) {
        if (isTooDense(level, pos, cfg)) {
            return 0;
        }
        List<ConfiguredFeature<?, ?>> features =
            level.getBiome(pos).value().getGenerationSettings().getBoneMealFeatures();
        if (features.isEmpty()) {
            return 0;
        }
        ConfiguredFeature<?, ?> feature = Util.getRandom(features, random);
        if (feature.config() instanceof SimpleBlockConfiguration simple) {
            return place(level, pos, pickSeasonalFlower(level, pos, random, simple, cfg));
        }
        return feature.place(level, level.getChunkSource().getGenerator(), random, pos) ? 1 : 0;
    }

    /**
     * Vanilla draws one flower from the biome's bonemeal provider - but that provider usually holds
     * the biome's <em>whole</em> flower set, so a single draw can't be filtered by season. We draw
     * normally, and if the draw isn't in-season, resample the provider a few times (jittered, so
     * position-noise providers vary too) for an in-season flower the biome actually offers, falling
     * back to the original draw. Stays biome-correct - only flowers this biome can produce.
     */
    private static BlockState pickSeasonalFlower(ServerLevel level, BlockPos pos, RandomSource random,
            SimpleBlockConfiguration simple, FallowConfig.Vegetation cfg) {
        BlockState first = simple.toPlace().getState(level, random, pos);
        if (!Fallow.CONFIG.seasons.enabled) {
            return first;
        }
        List<String> preferred = cfg.flowersFor(SeasonClock.season());
        if (preferred.isEmpty() || preferred.contains(flowerId(first))) {
            return first;
        }
        for (int i = 0; i < FLOWER_SEASON_SAMPLES; i++) {
            BlockState candidate = simple.toPlace()
                .getState(level, random, pos.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3));
            if (preferred.contains(flowerId(candidate))) {
                return candidate;
            }
        }
        return first; // biome offers no in-season flower here -> keep the vanilla draw
    }

    private static String flowerId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /** Bushes creep: copy a bush already growing nearby (worldgen-validated by construction). */
    private int trySproutBush(ServerLevel level, BlockPos pos, RandomSource random, FallowConfig.Vegetation cfg) {
        if (isTooDense(level, pos, cfg)) {
            return 0;
        }
        List<Block> nearby = new ArrayList<>(4);
        int r = cfg.bushSearchRadius;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -2, -r), pos.offset(r, 2, r))) {
            Block block = level.getBlockState(p).getBlock();
            if (BUSHES.contains(block)) {
                nearby.add(block);
                if (nearby.size() >= 4) {
                    break;
                }
            }
        }
        if (nearby.isEmpty()) {
            return 0;
        }
        Block chosen = nearby.get(random.nextInt(nearby.size()));
        // Per-bush seasonal schedule: sweet berry peaks in autumn, firefly bush in summer, a
        // generic bush in spring. Like saplings, bush seasonality is per-species, so the BUSH
        // channel is left unscaled in SeasonalGrowthRates and applied here instead (unlisted
        // bushes fall back to the shared curve). Weight > 1 (shared-curve spring) always passes.
        if (Fallow.CONFIG.seasons.enabled) {
            Season season = SeasonClock.season();
            String id = BuiltInRegistries.BLOCK.getKey(chosen).toString();
            if (random.nextFloat() >= cfg.bushSeasonWeight(id, season, Fallow.CONFIG.seasons)) {
                return 0;
            }
        }
        return place(level, pos, chosen.defaultBlockState());
    }

    private static int place(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.canSurvive(level, pos)) {
            return 0;
        }
        if (state.getBlock() instanceof DoublePlantBlock) {
            BlockPos above = pos.above();
            if (!level.isInsideBuildHeight(above) || !level.getBlockState(above).isAir()) {
                return 0;
            }
            DoublePlantBlock.placeAt(level, state, pos, Block.UPDATE_ALL);
            return 1;
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        return 1;
    }

    /**
     * True when the local area already holds enough flowers/bushes; early-exits past the cap.
     * The cap scales per biome (lush biomes saturate fuller, arid ones sparser).
     */
    private static boolean isTooDense(ServerLevel level, BlockPos pos, FallowConfig.Vegetation cfg) {
        int cap = BiomeTuning.resolveCap(cfg.densityMaxPlants, BiomeTuning.densityMultiplier(level, pos));
        if (cap <= 0) {
            return true;
        }
        int r = cfg.densityRadius;
        int count = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -2, -r), pos.offset(r, 2, r))) {
            BlockState state = level.getBlockState(p);
            if (FlowerWiltTask.isSeasonalFlower(state) || BUSHES.contains(state.getBlock())) {
                if (++count >= cap) {
                    return true;
                }
            }
        }
        return false;
    }
}
