package dev.isaac.fallow.growth;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.season.SeasonClock;
import dev.isaac.fallow.season.SeasonEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.EnumSet;
import java.util.Set;

/**
 * Multiplies the base config probability by the current season's factor. Growth channels get
 * the growth multipliers (spring boost ... winter near-zero); decay channels get the decay
 * multipliers, which run the other way (winter is the season of dieback, crowding cull, and
 * flower wilt). Pass-through while seasons are disabled. The factor's amplitude is
 * biome-modulated through {@link BiomeTuning#applySeasonality} - tropical biomes flatten the
 * curve toward year-round growth, boreal biomes amplify it into a true no-growth winter.
 *
 * <p>SAPLING, BUSH, FRUIT, LEAF_LITTER, and CROWDING are exempt from the shared curve: each
 * carries its own seasonal term in its task ({@code SaplingSpreadTask}'s per-species
 * {@code phenology}, {@code VegetationSproutTask}'s {@code bushSeasons}, {@code FruitDropTask}'s
 * per-type season, {@code LeafLitterTask}'s {@code leafFallWeight}, and {@code OvercrowdingTask}'s
 * per-season density <em>target</em>), so scaling them here too would count the season twice.
 * The heatwave stall still applies to every growth channel, exempt or not - a heatwave is a
 * transient event, not part of any per-species curve.
 */
public final class SeasonalGrowthRates implements GrowthRateProvider {
    /** Channels whose seasonal term lives in their task (see class doc); the curve skips them. */
    private static final Set<GrowthChannel> PER_SPECIES = EnumSet.of(
        GrowthChannel.SAPLING, GrowthChannel.BUSH, GrowthChannel.FRUIT,
        GrowthChannel.LEAF_LITTER, GrowthChannel.CROWDING);

    private final GrowthRateProvider base;

    public SeasonalGrowthRates(GrowthRateProvider base) {
        this.base = base;
    }

    @Override
    public float chance(GrowthChannel channel, ServerLevel level, BlockPos pos) {
        float chance = base.chance(channel, level, pos);
        if (!Fallow.CONFIG.seasons.enabled) {
            return chance;
        }
        double factor = 1.0;
        if (!PER_SPECIES.contains(channel)) {
            // Per-biome season phase: a biome can peak in a different season (e.g. savanna's wet
            // season is summer), so it reads the curve at a shifted season index. Season comes from
            // the per-tick cache (SeasonClock), not a SavedData lookup per candidate.
            Season season = SeasonClock.season().shifted(BiomeTuning.seasonPhase(level, pos));
            double baseMultiplier = channel.kind() == GrowthChannel.Kind.DECAY
                ? Fallow.CONFIG.seasons.decayMultiplier(season)
                : Fallow.CONFIG.seasons.multiplier(season);
            // Biome-modulated amplitude: tropical biomes flatten the curve (no dead season), boreal
            // biomes amplify it (true no-growth winter, booming spring). 1.0 (unlisted) = as-is.
            factor = BiomeTuning.applySeasonality(
                BiomeTuning.seasonalityMultiplier(level, pos), baseMultiplier);
        }
        // An active heatwave stalls growth (no effect on decay).
        if (channel.kind() == GrowthChannel.Kind.GROWTH) {
            factor *= SeasonEvents.growthMultiplier();
        }
        return (float) Math.min(1.0, chance * factor);
    }
}
