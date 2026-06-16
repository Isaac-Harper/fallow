package dev.isaac.fallow.season;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import net.fabricmc.fabric.api.biome.v1.BiomeModification;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Layer 1 of seasonal precipitation: a load-time Fabric biome modification that forces a biome's
 * has-precipitation flag from {@code precipitation.biomePrecip}. This is what lets an otherwise
 * dry biome actually rain - e.g. savanna gains a (warm) rainy season; its temperature keeps it
 * rain, not snow. Registered once at init (biome-registry load), so it applies to existing worlds,
 * but a config change needs a restart, not {@code /fallow reload}. The seasonal rain<->snow
 * <em>type</em> is the runtime mixin's job ({@link SeasonalTemperature}); this only decides
 * whether a biome precipitates at all - the one thing temperature alone can't unlock.
 */
public final class PrecipitationBiomes {
    private PrecipitationBiomes() {
    }

    public static void register() {
        FallowConfig.Precipitation cfg = Fallow.CONFIG.precipitation;
        if (!cfg.enabled || cfg.biomePrecip == null || cfg.biomePrecip.isEmpty()) {
            return;
        }
        BiomeModification mod =
            BiomeModifications.create(Identifier.fromNamespaceAndPath(Fallow.MOD_ID, "precipitation"));
        for (Map.Entry<String, Boolean> entry : cfg.biomePrecip.entrySet()) {
            Predicate<BiomeSelectionContext> selector = selectorFor(entry.getKey());
            if (selector == null) {
                continue;
            }
            boolean precipitation = Boolean.TRUE.equals(entry.getValue());
            mod.add(ModificationPhase.POST_PROCESSING, selector,
                ctx -> ctx.getWeather().setPrecipitation(precipitation));
        }
    }

    /** Build a biome selector from a config key: {@code "#namespace:tag"} or {@code "namespace:id"}. */
    private static Predicate<BiomeSelectionContext> selectorFor(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (key.startsWith("#")) {
            return BiomeSelectors.tag(TagKey.create(Registries.BIOME, id(key.substring(1))));
        }
        return BiomeSelectors.includeByKey(ResourceKey.create(Registries.BIOME, id(key)));
    }

    private static Identifier id(String s) {
        int colon = s.indexOf(':');
        return colon < 0
            ? Identifier.fromNamespaceAndPath("minecraft", s)
            : Identifier.fromNamespaceAndPath(s.substring(0, colon), s.substring(colon + 1));
    }
}
