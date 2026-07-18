package dev.isaac.fallow;

import dev.isaac.fallow.biome.BiomeTuning;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BiomeTuningTest {
    private static final Map<String, Double> RULES = new LinkedHashMap<>();

    static {
        RULES.put("minecraft:plains", 1.25);
        RULES.put("#minecraft:is_forest", 1.5);
        RULES.put("#minecraft:is_badlands", 0.3);
    }

    @Test
    void exactIdWins() {
        assertEquals(1.25, BiomeTuning.resolveKeys(
            "minecraft:plains", Set.of("#minecraft:is_forest"), RULES, 1.0));
    }

    @Test
    void tagMatchInRuleOrder() {
        assertEquals(1.5, BiomeTuning.resolveKeys(
            "minecraft:dark_forest", Set.of("#minecraft:is_forest", "#minecraft:is_badlands"), RULES, 1.0));
    }

    @Test
    void unlistedBiomeUsesDefault() {
        assertEquals(1.0, BiomeTuning.resolveKeys("minecraft:desert", Set.of(), RULES, 1.0));
    }

    @Test
    void moddedNamespacesWork() {
        Map<String, Double> rules = Map.of("terralith:lavender_forest", 2.0);
        assertEquals(2.0, BiomeTuning.resolveKeys("terralith:lavender_forest", Set.of(), rules, 1.0));
    }

    @Test
    void resolveCapRoundsAndFloorsAtZero() {
        // The plains case that exposed the sapling-carpet bug: 2 * 1.25 = 2.5 -> 3.
        assertEquals(3, BiomeTuning.resolveCap(2, 1.25));
        assertEquals(2, BiomeTuning.resolveCap(2, 1.0));
        assertEquals(15, BiomeTuning.resolveCap(10, 1.5));
        assertEquals(0, BiomeTuning.resolveCap(2, 0.0), "zero-multiplier biome allows none");
        assertEquals(0, BiomeTuning.resolveCap(5, -1.0), "never negative");
    }

    @Test
    void applySeasonalityScalesAmplitudeAndFloorsAtZero() {
        double winter = 0.05; // shared-curve winter growth multiplier
        double spring = 1.5;  // shared-curve spring growth multiplier
        // k = 0: aseasonal - always the summer baseline, regardless of the curve.
        assertEquals(1.0, BiomeTuning.applySeasonality(0.0, winter), 1e-9);
        assertEquals(1.0, BiomeTuning.applySeasonality(0.0, spring), 1e-9);
        // k = 1: the curve unchanged (temperate baseline).
        assertEquals(winter, BiomeTuning.applySeasonality(1.0, winter), 1e-9);
        assertEquals(spring, BiomeTuning.applySeasonality(1.0, spring), 1e-9);
        // Tropical (k = 0.2): winter still grows; spring barely bumped.
        assertEquals(0.81, BiomeTuning.applySeasonality(0.2, winter), 1e-9);
        // Boreal (k > ~1.05): winter clamps to a true no-growth season; spring booms past 1.5.
        assertEquals(0.0, BiomeTuning.applySeasonality(1.15, winter), 1e-9, "amplified winter = no growth");
        assertEquals(1.575, BiomeTuning.applySeasonality(1.15, spring), 1e-9, "amplified spring boom");
    }
}
