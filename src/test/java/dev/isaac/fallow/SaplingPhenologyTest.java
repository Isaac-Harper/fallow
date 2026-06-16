package dev.isaac.fallow;

import dev.isaac.fallow.FallowConfig.SeasonWeights;
import dev.isaac.fallow.FallowConfig.Saplings.TreeType;
import dev.isaac.fallow.FallowConfig.Seasons;
import dev.isaac.fallow.api.Season;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-logic coverage for per-species sapling seasonality: the {@code phenology} override and its
 * fallback to the shared seasonal curve. No Minecraft bootstrap - exercises config math only,
 * mirroring the unit-test boundary of {@code SeasonMath} / config defaults.
 */
class SaplingPhenologyTest {
    private static final double EPS = 1e-9;

    @Test
    void phenologyOverrideReplacesSharedCurve() {
        Seasons global = new Seasons(); // shared curve: spring 1.5, summer 1.0, autumn 0.5, winter 0.05
        // White-oak-flavored oak spreads in autumn ONLY (acorns germinate on dropping in fall, no
        // dormancy). The override's zeros mean no spread in any other season - the shared curve is
        // bypassed entirely, so spring stays 0 even though the shared curve would boost it to 1.5.
        TreeType oak = new TreeType(0.6, 9, 4, new SeasonWeights(0.0, 0.0, 1.0, 0.0));
        assertEquals(0.0, oak.seasonWeight(Season.SPRING, global), EPS);
        assertEquals(0.0, oak.seasonWeight(Season.SUMMER, global), EPS);
        assertEquals(1.0, oak.seasonWeight(Season.AUTUMN, global), EPS);
        assertEquals(0.0, oak.seasonWeight(Season.WINTER, global), EPS);
    }

    @Test
    void noPhenologyFallsBackToSharedCurve() {
        Seasons global = new Seasons();
        TreeType birch = new TreeType(1.0, 16, 4); // no override
        for (Season s : Season.values()) {
            assertEquals(global.multiplier(s), birch.seasonWeight(s, global), EPS,
                "unoverridden species should track the shared curve in " + s);
        }
        assertEquals(1.5, birch.seasonWeight(Season.SPRING, global), EPS);
    }

    @Test
    void seasonWeightsDefaultToFlatAseasonal() {
        SeasonWeights flat = new SeasonWeights(); // default ctor = jungle's aseasonal profile
        for (Season s : Season.values()) {
            assertEquals(1.0, flat.weight(s), EPS, "default phenology should be flat in " + s);
        }
    }

    @Test
    void overrideIsIndependentOfGlobalWinter() {
        // Spruce keeps a real winter trickle where the shared curve would all but zero it out.
        Seasons global = new Seasons();
        TreeType spruce = new TreeType(0.5, 14, 5, new SeasonWeights(0.25, 0.35, 1.0, 0.45));
        assertEquals(0.45, spruce.seasonWeight(Season.WINTER, global), EPS);
        assertEquals(0.05, global.multiplier(Season.WINTER), EPS, "shared-curve winter it bypasses");
    }
}
