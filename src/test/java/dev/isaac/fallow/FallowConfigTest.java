package dev.isaac.fallow;

import dev.isaac.fallow.api.Season;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallowConfigTest {
    @Test
    void defaultChanceOrdering() {
        // The spec'd relative ordering: short grass > tall grass upgrade > flower > bush.
        var veg = new FallowConfig().vegetation;
        assertTrue(veg.shortGrassChance > veg.tallGrassChance);
        assertTrue(veg.tallGrassChance > veg.flowerChance);
        assertTrue(veg.flowerChance > veg.bushChance);
    }

    @Test
    void defaultSeasonMultiplierOrdering() {
        var seasons = new FallowConfig().seasons;
        assertTrue(seasons.springMultiplier > seasons.summerMultiplier);
        assertTrue(seasons.summerMultiplier > seasons.autumnMultiplier);
        assertTrue(seasons.autumnMultiplier > seasons.winterMultiplier);
        assertTrue(seasons.winterMultiplier > 0, "winter is near-zero, not zero");
    }

    @Test
    void decayMultipliersRunOppositeToGrowth() {
        var seasons = new FallowConfig().seasons;
        assertTrue(seasons.winterDecayMultiplier > seasons.autumnDecayMultiplier);
        assertTrue(seasons.autumnDecayMultiplier > seasons.springDecayMultiplier);
        assertTrue(seasons.springDecayMultiplier > seasons.summerDecayMultiplier);
    }

    @Test
    void flowerBloomArcNetsPositiveInSpringNegativeInWinter() {
        // The real invariant for "bloom in spring, gone by winter": compare EFFECTIVE rates
        // (base x season multiplier), not just the multipliers - the base-rate mismatch is
        // what the adversarial review caught.
        var cfg = new FallowConfig();
        double sproutBase = cfg.vegetation.flowerChance;
        double wiltBase = cfg.flowerWilt.chance;
        var s = cfg.seasons;
        double springSprout = sproutBase * s.springMultiplier;
        double springWilt = wiltBase * s.springDecayMultiplier;
        double winterSprout = sproutBase * s.winterMultiplier;
        double winterWilt = wiltBase * s.winterDecayMultiplier;
        assertTrue(springSprout >= springWilt, "spring must net-accumulate flowers");
        assertTrue(winterWilt > winterSprout * 10, "winter must net-remove flowers hard");
    }

    @Test
    void biomeGrowthDefaultsFavorLushOverArid() {
        var bg = new FallowConfig().vegetation.biomeGrowth;
        assertTrue(bg.get("#minecraft:is_jungle") > bg.get("minecraft:plains"));
        assertTrue(bg.get("minecraft:plains") > bg.get("minecraft:desert"));
        assertTrue(bg.get("minecraft:desert") < 1.0, "desert grows slower than baseline");
    }

    @Test
    void precipitationOffsetsSnowTemperateWintersNotTropics() {
        var p = new FallowConfig().precipitation;
        // Colder in winter, warmer in summer; the season accessor maps correctly.
        assertTrue(p.winterTempOffset < 0, "winter is colder");
        assertTrue(p.summerTempOffset > 0, "summer is warmer");
        assertTrue(p.winterTempOffset < p.autumnTempOffset);
        assertTrue(p.autumnTempOffset < p.summerTempOffset);
        assertEquals(p.winterTempOffset, p.tempOffset(Season.WINTER));
        assertEquals(p.summerTempOffset, p.tempOffset(Season.SUMMER));
        // Vanilla snows below ~0.15 effective temperature. A temperate base (plains ~ 0.8) crosses
        // it in winter but not summer; a warm base (jungle ~ 0.95) never does.
        double snowThreshold = 0.15;
        assertTrue(0.8 + p.winterTempOffset < snowThreshold, "temperate winter snows");
        assertTrue(0.8 + p.summerTempOffset > snowThreshold, "temperate summer rains");
        assertTrue(0.95 + p.winterTempOffset > snowThreshold, "jungle stays rain even in winter");
    }

    @Test
    void savannaIsPrecipitationEnabledByDefault() {
        var bp = new FallowConfig().precipitation.biomePrecip;
        assertEquals(Boolean.TRUE, bp.get("#minecraft:is_savanna"), "savanna gains a rainy season");
    }

    @Test
    void snowDepthDefaultsDeeperInColderBiomes() {
        var sd = new FallowConfig().precipitation.snowDepth;
        assertTrue(sd.get("#minecraft:is_snowy") > sd.get("#minecraft:is_taiga"), "snowy piles deeper than taiga");
        assertTrue(sd.get("#minecraft:is_taiga") > 1.0, "boreal builds more than the temperate dusting");
        assertTrue(sd.get("#minecraft:is_snowy") <= 8.0, "within the 1-8 snow-layer range");
    }

    @Test
    void biomeSeasonalityDefaultsRunTropicalToBoreal() {
        var bs = new FallowConfig().vegetation.biomeSeasonality;
        // Tropical biomes are nearly aseasonal (< temperate baseline 1.0); boreal exceed it.
        assertTrue(bs.get("#minecraft:is_jungle") < 1.0, "jungle is near-aseasonal");
        assertTrue(bs.get("#minecraft:is_taiga") > 1.0, "boreal amplifies into a no-growth winter");
        assertTrue(bs.get("#minecraft:is_snowy") >= bs.get("#minecraft:is_taiga"), "snowy at least as harsh as taiga");
        assertTrue(bs.get("#minecraft:is_jungle") < bs.get("#minecraft:is_savanna"));
        // An amplified winter must actually reach zero growth at the boreal default.
        double winter = new FallowConfig().seasons.winterMultiplier;
        assertEquals(0.0, dev.isaac.fallow.biome.BiomeTuning.applySeasonality(bs.get("#minecraft:is_taiga"), winter), 1e-9);
    }

    @Test
    void bushSchedulesPutEachVanillaBushOnItsOwnSeason() {
        var veg = new FallowConfig().vegetation;
        var s = new FallowConfig().seasons;
        // Each vanilla bush peaks (weight 1.0) in a different season.
        assertEquals(1.0, veg.bushSeasonWeight("minecraft:bush", Season.SPRING, s), 1e-9);
        assertEquals(1.0, veg.bushSeasonWeight("minecraft:firefly_bush", Season.SUMMER, s), 1e-9);
        assertEquals(1.0, veg.bushSeasonWeight("minecraft:sweet_berry_bush", Season.AUTUMN, s), 1e-9);
        // Boreal sweet berry is dormant in winter.
        assertEquals(0.0, veg.bushSeasonWeight("minecraft:sweet_berry_bush", Season.WINTER, s), 1e-9);
    }

    @Test
    void unlistedBushFallsBackToSharedCurve() {
        var veg = new FallowConfig().vegetation;
        var s = new FallowConfig().seasons;
        for (Season season : Season.values()) {
            assertEquals(s.multiplier(season), veg.bushSeasonWeight("modid:weird_bush", season, s), 1e-9,
                "unlisted bush should track the shared curve in " + season);
        }
    }

    @Test
    void perSpeciesSaplingDefaultsReflectEcology() {
        var t = new FallowConfig().saplings;
        var birch = t.types.get("minecraft:birch_sapling");
        var oak = t.types.get("minecraft:oak_sapling");
        var acacia = t.types.get("minecraft:acacia_sapling");
        var jungle = t.types.get("minecraft:jungle_sapling");
        var darkOak = t.types.get("minecraft:dark_oak_sapling");
        var paleOak = t.types.get("minecraft:pale_oak_sapling");
        // Pioneers seed more readily than climax/relict species.
        assertTrue(birch.rate > oak.rate);
        assertTrue(oak.rate > paleOak.rate);
        // Wind/animal dispersers reach further than heavy-nut oaks.
        assertTrue(birch.radius > oak.radius);
        assertTrue(acacia.radius > oak.radius);
        // Closed-canopy species pack denser than sparse savanna.
        assertTrue(jungle.density > acacia.density);
        assertTrue(darkOak.density > acacia.density);
        // Every per-type radius is reachable within the log-search radius.
        for (var type : t.types.values()) {
            assertTrue(type.radius <= t.logSearchRadius, "radius must be within logSearchRadius");
        }
    }

    @Test
    void perSpeciesDensityClampSurvivesTinyLogRadius() {
        var cfg = new FallowConfig();
        cfg.saplings.logSearchRadius = 4;
        cfg.clamp();
        for (var type : cfg.saplings.types.values()) {
            assertTrue(type.radius <= 4, "radius clamps down to logSearchRadius");
        }
    }

    @Test
    void grassDensityTargetVariesBySeasonAndBiome() {
        var o = new FallowConfig().overcrowding;
        var bd = new FallowConfig().vegetation.biomeDensity;
        // Per season: the density target is higher in summer than winter.
        assertTrue(o.densityFactor(Season.SUMMER) > o.densityFactor(Season.WINTER));
        assertTrue(o.densityFactor(Season.SUMMER) >= o.densityFactor(Season.SPRING));
        // Per biome x season (same math the task applies, minus the round/clamp): a lush biome in
        // summer holds far denser grass than an arid one in winter.
        double lushSummer = o.neighborThreshold * bd.get("#minecraft:is_jungle") * o.densityFactor(Season.SUMMER);
        double aridWinter = o.neighborThreshold * bd.get("minecraft:desert") * o.densityFactor(Season.WINTER);
        assertTrue(lushSummer > aridWinter + 3.0, "jungle-summer grass far denser than desert-winter");
    }

    @Test
    void newSeasonalSystemsHaveSaneDefaults() {
        var cfg = new FallowConfig();
        // Phase offset: savanna's growth peaks in summer (shifted).
        assertEquals(3.0, cfg.vegetation.biomeSeasonPhase.get("#minecraft:is_savanna"));
        // Weather frequency: wetter summers than winters.
        assertTrue(cfg.precipitation.summerRainfall > cfg.precipitation.winterRainfall);
        // Leaf fall peaks in autumn (the curve drives both litter accumulation and particles).
        assertTrue(cfg.leafLitter.autumnFall > cfg.leafLitter.summerFall);
        assertTrue(cfg.leafLitter.autumnFall > cfg.leafLitter.winterFall);
        // Fruiting: oaks drop apples in autumn.
        var oak = cfg.fruiting.types.get("minecraft:oak_leaves");
        assertEquals("minecraft:apple", oak.item);
        assertEquals("autumn", oak.season);
        // Events on by default.
        assertTrue(cfg.events.enabled);
    }

    @Test
    void seasonShiftWrapsAroundTheCycle() {
        assertEquals(Season.SUMMER, Season.SPRING.shifted(1));
        assertEquals(Season.SPRING, Season.SUMMER.shifted(3)); // savanna: summer reads as spring (peak)
        assertEquals(Season.WINTER, Season.SPRING.shifted(-1));
        assertEquals(Season.SPRING, Season.SPRING.shifted(0));
        assertEquals(Season.SPRING, Season.SPRING.shifted(4)); // full wrap
    }

    @Test
    void overcrowdingThresholdClampsToNeighborhood() {
        var cfg = new FallowConfig();
        cfg.overcrowding.radius = 1;
        cfg.overcrowding.neighborThreshold = 999; // nonsense
        cfg.clamp();
        assertEquals(8, cfg.overcrowding.neighborThreshold, "radius-1 neighborhood has 8 cells");
    }

    @Test
    void trailThresholdOrderingSurvivesClamp() {
        var cfg = new FallowConfig();
        cfg.trails.stepsToCoarse = 50;
        cfg.trails.stepsToPath = 10; // nonsense: path before coarse
        cfg.clamp();
        assertTrue(cfg.trails.stepsToPath > cfg.trails.stepsToCoarse);
    }

    @Test
    void clampRepairsHandEditedValues() {
        var cfg = new FallowConfig();
        cfg.vegetation.shortGrassChance = 7.0;
        cfg.dayNight.winterDayPortion = 0.0;
        cfg.scheduler.tickBudgetMicros = -5;
        cfg.seasons.daysPerSeason = 0;
        cfg.clamp();
        assertEquals(1.0, cfg.vegetation.shortGrassChance);
        assertEquals(0.05, cfg.dayNight.winterDayPortion);
        assertEquals(10, cfg.scheduler.tickBudgetMicros);
        assertEquals(1, cfg.seasons.daysPerSeason);
    }

    @Test
    void vanillaNeutralDefaults() {
        var dn = new FallowConfig().dayNight;
        assertEquals(0.5, dn.springDayPortion, "spring is the vanilla-neutral season");
        assertEquals(0.5, dn.autumnDayPortion, "autumn is the vanilla-neutral season");
    }
}
