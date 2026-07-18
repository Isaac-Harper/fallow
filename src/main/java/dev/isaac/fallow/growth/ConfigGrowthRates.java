package dev.isaac.fallow.growth;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Base rates straight from the live config; no seasonal or biome scaling. */
public final class ConfigGrowthRates implements GrowthRateProvider {
    @Override
    public float chance(GrowthChannel channel, ServerLevel level, BlockPos pos) {
        FallowConfig.Vegetation veg = Fallow.CONFIG.vegetation;
        return (float) switch (channel) {
            case SHORT_GRASS -> veg.shortGrassChance;
            case TALL_GRASS -> veg.tallGrassChance;
            case FLOWER -> veg.flowerChance;
            case BUSH -> veg.bushChance;
            case SAPLING -> Fallow.CONFIG.saplings.chance;
            case SUGAR_CANE -> Fallow.CONFIG.shoreline.sugarCaneChance;
            case SEAGRASS -> Fallow.CONFIG.shoreline.seagrassChance;
            case BAMBOO -> Fallow.CONFIG.bamboo.chance;
            // Fruiting has no single base chance: each fruiting.types entry carries its own,
            // applied in the task. The stack contributes only the biome/heatwave scaling.
            case FRUIT -> 1.0;
            case FORAGE -> Fallow.CONFIG.crops.wild.forageChance;
            case DIEBACK -> Fallow.CONFIG.dieback.chance;
            case LEAF_LITTER -> Fallow.CONFIG.leafLitter.chance;
            case CROWDING -> Fallow.CONFIG.overcrowding.chance;
            case FLOWER_WILT -> Fallow.CONFIG.flowerWilt.chance;
        };
    }
}
