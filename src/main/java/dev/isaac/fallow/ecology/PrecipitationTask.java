package dev.isaac.fallow.ecology;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.growth.BiomeTuning;
import dev.isaac.fallow.season.SeasonEvents;
import dev.isaac.fallow.season.SeasonalTemperature;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Seasonal snow <em>depth</em> and thaw. The precipitation <em>type</em> (rain vs snow) and the
 * first snow layer are already vanilla's job, driven by {@code BiomeTemperatureMixin}; this task
 * adds the per-biome <em>intensity</em>: snowy/boreal biomes build deep drifts over the cold
 * season while temperate biomes keep a dusting (the per-biome max is
 * {@code precipitation.snowDepth}), and it actively thaws snow and ice when the season turns
 * (vanilla only melts those from block light, so a seasonal thaw needs a nudge). The thaw rate is
 * tied to temperature - melt scales with how far the biome's seasonal warmth (base temperature plus
 * the season's k-scaled offset) sits above the 0.15 thaw point - so snow lingers in a cool early
 * spring and clears fast in high summer or a hot biome, with no extra per-biome table.
 *
 * <p>"Is it snow season here" reuses vanilla's own seasonal answer, {@code precipitationAt(pos)} -
 * which already reflects the season because the mixin shifts biome temperature - so no climate
 * logic is duplicated. Sky-exposed surfaces only; {@code has_precipitation=false} biomes report
 * NONE and are left alone.
 */
public final class PrecipitationTask implements EcologyTask {
    /** Vanilla's rain-vs-snow / freeze cutoff: temperature below this is snow-season. */
    private static final float SNOW_THRESHOLD = 0.15f;

    @Override
    public String id() {
        return "precipitation";
    }

    @Override
    public boolean enabled(FallowConfig config) {
        return config.precipitation.enabled;
    }

    @Override
    public int visitChunk(ServerLevel level, LevelChunk chunk, RandomSource random, int samples) {
        FallowConfig.Precipitation cfg = Fallow.CONFIG.precipitation;
        ChunkPos chunkPos = chunk.getPos();
        int changed = 0;
        for (int i = 0; i < samples; i++) {
            int x = chunkPos.getMinBlockX() + random.nextInt(16);
            int z = chunkPos.getMinBlockZ() + random.nextInt(16);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (y <= level.getMinY() || y > level.getMaxY()) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.canSeeSky(pos)) {
                continue;
            }
            // "Cold" is weather-INDEPENDENT (temperature, via the mixin); snowfall is the vanilla
            // weather state. Snow only builds while it's actually snowing here - cold AND raining,
            // i.e. real vanilla snowfall - never on a clear winter day. When it's warm (not cold),
            // snow and ice thaw - faster the warmer it is. A clear cold day is a no-op: snow sits.
            Biome biome = level.getBiome(pos).value();
            boolean cold = biome.getPrecipitationAt(pos, level.getSeaLevel())
                == Biome.Precipitation.SNOW;
            if (cold) {
                if (level.isRaining()) {
                    changed += accumulate(level, pos, random, cfg);
                }
            } else {
                // Warmth for the melt rate. getTemperature is private, so rebuild the seasonal value
                // from the public base plus the same k-scaled offset the mixin adds elsewhere
                // (SeasonalTemperature.offset() x biomeSeasonality). Altitude isn't needed here - the
                // precip-type gate above already held thaw off until it was warm enough to rain.
                double temp = biome.getBaseTemperature()
                    + SeasonalTemperature.offset() * BiomeTuning.seasonality(biome);
                changed += thaw(level, pos, random, cfg, temp);
            }
        }
        return changed;
    }

    /** Build snow up to this biome's depth: raise an existing layer, or lay the first one. */
    private static int accumulate(ServerLevel level, BlockPos pos, RandomSource random, FallowConfig.Precipitation cfg) {
        // A blizzard piles snow much faster (SeasonEvents.snowMultiplier).
        if (random.nextFloat() >= cfg.snowAccumulateChance * SeasonEvents.snowMultiplier()) {
            return 0;
        }
        BlockState at = level.getBlockState(pos);
        if (at.is(Blocks.SNOW)) {
            return raise(level, pos, at);
        }
        // Deep snow can shift the heightmap a block up; catch the layer just below.
        BlockState below = level.getBlockState(pos.below());
        if (below.is(Blocks.SNOW)) {
            return raise(level, pos.below(), below);
        }
        if (at.isAir()) {
            BlockState snow = Blocks.SNOW.defaultBlockState();
            if (snow.canSurvive(level, pos)) {
                level.setBlock(pos, snow, Block.UPDATE_ALL);
                return 1;
            }
        }
        return 0;
    }

    private static int raise(ServerLevel level, BlockPos pos, BlockState snow) {
        int layers = snow.getValue(SnowLayerBlock.LAYERS);
        int target = Math.min(8, Math.max(1, (int) Math.round(BiomeTuning.snowDepth(level, pos))));
        if (layers >= target) {
            return 0;
        }
        level.setBlock(pos, snow.setValue(SnowLayerBlock.LAYERS, layers + 1), Block.UPDATE_ALL);
        return 1;
    }

    /**
     * Warm season: melt snow layers and (optionally) thaw ice back to water. The per-visit chance
     * scales with how far {@code temp} sits above the 0.15 thaw point, so snow lingers just above
     * freezing and clears fast when it's genuinely warm - reaching {@code snowMeltChance} at
     * {@code meltReferenceWarmth}, clamped to certain melt beyond that.
     */
    private static int thaw(ServerLevel level, BlockPos pos, RandomSource random,
                            FallowConfig.Precipitation cfg, double temp) {
        double warmth = Math.max(0.0, temp - SNOW_THRESHOLD);
        double meltChance = Math.min(1.0, cfg.snowMeltChance * warmth / cfg.meltReferenceWarmth);
        if (random.nextFloat() >= meltChance) {
            return 0;
        }
        BlockState at = level.getBlockState(pos);
        if (at.is(Blocks.SNOW)) {
            return lower(level, pos, at);
        }
        BlockState below = level.getBlockState(pos.below());
        if (below.is(Blocks.SNOW)) {
            return lower(level, pos.below(), below);
        }
        if (cfg.thawIce) {
            // Only thaw ice sitting on water (a frozen pond/lake surface), so exposed player-placed
            // ice on land/builds isn't melted out from under them.
            if (at.is(Blocks.ICE) && level.getBlockState(pos.below()).is(Blocks.WATER)) {
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                return 1;
            }
            if (below.is(Blocks.ICE) && level.getBlockState(pos.below(2)).is(Blocks.WATER)) {
                level.setBlock(pos.below(), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                return 1;
            }
        }
        return 0;
    }

    private static int lower(ServerLevel level, BlockPos pos, BlockState snow) {
        int layers = snow.getValue(SnowLayerBlock.LAYERS);
        if (layers <= 1) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } else {
            level.setBlock(pos, snow.setValue(SnowLayerBlock.LAYERS, layers - 1), Block.UPDATE_ALL);
        }
        return 1;
    }
}
