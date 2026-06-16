package dev.isaac.fallow.growth;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Scales <em>growth</em> channels by the candidate's per-biome growth multiplier (lush biomes
 * grow vegetation faster, arid ones slower). Decay channels are left untouched - winter
 * dieback shouldn't depend on how fertile the biome is. Outermost layer in the provider
 * stack, so it composes on top of config base x season.
 */
public final class BiomeGrowthRates implements GrowthRateProvider {
    private final GrowthRateProvider base;

    public BiomeGrowthRates(GrowthRateProvider base) {
        this.base = base;
    }

    @Override
    public float chance(GrowthChannel channel, ServerLevel level, BlockPos pos) {
        float chance = base.chance(channel, level, pos);
        if (channel.kind() != GrowthChannel.Kind.GROWTH) {
            return chance;
        }
        return (float) Math.min(1.0, chance * BiomeTuning.growthMultiplier(level, pos));
    }
}
