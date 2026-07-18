package dev.isaac.fallow.biome;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Per-biome tuning resolution. Config keys are biome ids ("minecraft:plains") or biome tags
 * ("#minecraft:is_forest"); an exact id match wins over tag matches; the first matching tag
 * (config iteration order) wins among tags; unlisted biomes resolve to the default.
 *
 * <p>All biomes are resolved <em>eagerly</em> into one {@code Biome -> Factors} map (built from the
 * registry at server start / client join / {@code /fallow reload} via {@link #rebuild}), the single
 * source of truth for every per-biome value. Tasks read it with {@code (level, pos)}; consumers
 * that hold only a bare {@code Biome} with no level - notably the temperature mixin - read the same
 * map by instance ({@link #seasonality(Biome)}), which the old lazy {@code (level, pos)} cache
 * couldn't serve.
 */
public final class BiomeTuning {
    /** Per-biome resolved tuning (density cap, growth rate, seasonality, snow depth, phase). */
    private record Factors(double density, double growth, double seasonality, double snowDepth, double seasonPhase) {
    }

    /** Neutral profile for biomes absent from the map (e.g. modded biomes loaded after a build). */
    private static final Factors DEFAULT = new Factors(1.0, 1.0, 1.0, 1.0, 0.0);

    /** Whole-registry snapshot, swapped atomically on {@link #rebuild}; reads are a volatile load. */
    private static volatile Map<Biome, Factors> profiles = Map.of();

    private BiomeTuning() {
    }

    /** Re-resolve every biome's tuning from the current config + registry. Idempotent. */
    public static void rebuild(RegistryAccess access) {
        FallowConfig cfg = Fallow.CONFIG;
        Map<Biome, Factors> next = new IdentityHashMap<>();
        access.lookupOrThrow(Registries.BIOME).listElements()
            .forEach(holder -> next.put(holder.value(), resolveProfile(holder, cfg)));
        profiles = next;
    }

    private static Factors resolveProfile(Holder<Biome> holder, FallowConfig cfg) {
        return new Factors(
            resolve(holder, cfg.vegetation.biomeDensity),
            resolve(holder, cfg.vegetation.biomeGrowth),
            resolve(holder, cfg.vegetation.biomeSeasonality),
            resolve(holder, cfg.precipitation.snowDepth),
            resolve(holder, cfg.vegetation.biomeSeasonPhase, 0.0));
    }

    private static Factors of(Biome biome) {
        Factors f = profiles.get(biome);
        return f != null ? f : DEFAULT;
    }

    /** Multiplier applied to density caps (vegetation decorations, saplings) in this biome. */
    public static double densityMultiplier(ServerLevel level, BlockPos pos) {
        return of(level.getBiome(pos).value()).density();
    }

    /** Multiplier applied to growth-channel probabilities (grass, flowers, saplings...) here. */
    public static double growthMultiplier(ServerLevel level, BlockPos pos) {
        return of(level.getBiome(pos).value()).growth();
    }

    /** How strongly the shared season curve applies in this biome (amplitude scalar; 1.0 = full). */
    public static double seasonalityMultiplier(ServerLevel level, BlockPos pos) {
        return of(level.getBiome(pos).value()).seasonality();
    }

    /** Seasonality (amplitude) for a bare {@code Biome} - used by the temperature mixin (no level). */
    public static double seasonality(Biome biome) {
        return of(biome).seasonality();
    }

    /** Maximum snow depth (layers) this biome accumulates in its snow season; 1.0 = a dusting. */
    public static double snowDepth(ServerLevel level, BlockPos pos) {
        return of(level.getBiome(pos).value()).snowDepth();
    }

    /** Season-phase offset (steps) shifting which season this biome's growth curve peaks in. */
    public static int seasonPhase(ServerLevel level, BlockPos pos) {
        return (int) Math.round(of(level.getBiome(pos).value()).seasonPhase());
    }

    private static double resolve(Holder<Biome> holder, Map<String, Double> rules) {
        return resolve(holder, rules, 1.0);
    }

    private static double resolve(Holder<Biome> holder, Map<String, Double> rules, double dflt) {
        if (rules == null || rules.isEmpty()) {
            return dflt;
        }
        String id = holder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
        Set<String> tags = holder.tags()
            .map(t -> "#" + t.location().toString())
            .collect(Collectors.toSet());
        return resolveKeys(id, tags, rules, dflt);
    }

    /**
     * Resolved density cap for a base count and biome multiplier. Callers MUST use this same
     * value both as the counter's early-exit limit and as the comparison threshold - counting
     * only up to a smaller number than the threshold silently disables the guard (the
     * sapling-carpet bug). Pure; unit-tested.
     */
    public static int resolveCap(int base, double multiplier) {
        return Math.max(0, (int) Math.round(base * multiplier));
    }

    /**
     * Biome-scaled seasonal factor: scales the shared season multiplier's amplitude around the
     * 1.0 summer baseline by {@code k} - 0 = flat/aseasonal (tropical), 1 = the curve unchanged
     * (temperate), &gt;1 = amplified - clamped at zero so an amplified winter becomes a true
     * no-growth season. Pure; unit-tested.
     */
    public static double applySeasonality(double k, double baseMultiplier) {
        return Math.max(0.0, 1.0 + k * (baseMultiplier - 1.0));
    }

    /** Pure matching (unit-tested): exact id first, then first matching tag in rule order. */
    public static double resolveKeys(String biomeId, Set<String> biomeTags, Map<String, Double> rules, double dflt) {
        Double exact = rules.get(biomeId);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, Double> rule : rules.entrySet()) {
            if (rule.getKey().startsWith("#") && biomeTags.contains(rule.getKey())) {
                return rule.getValue();
            }
        }
        return dflt;
    }
}
