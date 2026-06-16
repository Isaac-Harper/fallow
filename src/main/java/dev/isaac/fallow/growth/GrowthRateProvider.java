package dev.isaac.fallow.growth;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The single source of growth probabilities. Ecology tasks roll against this and never read
 * chances from the config directly, so every modifier composes in one place:
 * config base -> seasonal scaling -> per-biome scaling (and any future: weather, moon phase...).
 *
 * <p>The position lets position-dependent modifiers (per-biome rates) resolve; modifiers that
 * don't need it (season) simply ignore it.
 */
public interface GrowthRateProvider {
    /** Probability in [0, 1] for one attempt of the given behavior at this position. */
    float chance(GrowthChannel channel, ServerLevel level, BlockPos pos);
}
