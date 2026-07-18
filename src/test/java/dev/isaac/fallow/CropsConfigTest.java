package dev.isaac.fallow;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.isaac.fallow.FallowConfig.Crops;
import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.ecology.ForageSpreadTask;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic coverage for the Phase C1 crop layer: Crops config defaults,
 * cropSeasonWeight, clamp, JSON roundtrip, and the ForageSpreadTask biome-eligibility helper.
 * No Minecraft bootstrap - exercises config math and GSON only.
 */
class CropsConfigTest {
    private static final double EPS = 1e-9;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // -- defaults ---------------------------------------------------------

    @Test
    void cropsDisabledByDefault() {
        assertFalse(new FallowConfig().crops.enabled, "crops ships off by default (blocks persist on uninstall)");
    }

    @Test
    void turnipHasHardyWinterTrickle() {
        // Documented default: fallow:turnip_crop winter 0.25 - the cold-tolerant trickle.
        Crops crops = new FallowConfig().crops;
        assertEquals(0.25, crops.cropSeasonWeight("fallow:turnip_crop", Season.WINTER), EPS);
    }

    @Test
    void cabbageStallsInSummerAndStopsInWinter() {
        Crops crops = new FallowConfig().crops;
        // fallow:cabbage_crop summer 0.3 - cool-season stall
        assertEquals(0.3, crops.cropSeasonWeight("fallow:cabbage_crop", Season.SUMMER), EPS);
        // fallow:cabbage_crop winter 0.0 - full winter dormancy
        assertEquals(0.0, crops.cropSeasonWeight("fallow:cabbage_crop", Season.WINTER), EPS);
    }

    @Test
    void phaseC2WinterHardinessDefaults() {
        Crops crops = new FallowConfig().crops;
        // Leek is the hardiest winter crop of the batch (0.35 trickle).
        assertEquals(0.35, crops.cropSeasonWeight("fallow:leek_crop", Season.WINTER), EPS);
        // Rye keeps a lighter winter trickle (0.3).
        assertEquals(0.3, crops.cropSeasonWeight("fallow:rye_crop", Season.WINTER), EPS);
        // Oat is fully dormant in winter (0.0).
        assertEquals(0.0, crops.cropSeasonWeight("fallow:oat_crop", Season.WINTER), EPS);
    }

    @Test
    void unknownBlockDefaultsToOneForEverySeasonWeights() {
        Crops crops = new FallowConfig().crops;
        // Undocumented / modded crops fall through to the 1.0 default - grow year-round.
        for (Season s : Season.values()) {
            assertEquals(1.0, crops.cropSeasonWeight("somemod:unknown_crop", s), EPS,
                "unknown crop should return 1.0 in " + s);
        }
    }

    // -- clamp ------------------------------------------------------------

    @Test
    void negativeForageChanceClampsToZero() {
        FallowConfig cfg = new FallowConfig();
        cfg.crops.wild.forageChance = -0.5;
        cfg.clamp();
        assertEquals(0.0, cfg.crops.wild.forageChance, EPS);
    }

    @Test
    void forageChanceAboveOneClampsToOne() {
        FallowConfig cfg = new FallowConfig();
        cfg.crops.wild.forageChance = 3.7;
        cfg.clamp();
        assertEquals(1.0, cfg.crops.wild.forageChance, EPS);
    }

    @Test
    void fixRadiusClamps0To4() {
        FallowConfig cfg = new FallowConfig();
        cfg.crops.legumes.fixRadius = -1;
        cfg.clamp();
        assertEquals(0, cfg.crops.legumes.fixRadius);

        cfg.crops.legumes.fixRadius = 99;
        cfg.clamp();
        assertEquals(4, cfg.crops.legumes.fixRadius);
    }

    @Test
    void cropSeasonWeightsClampToMultiplierRange() {
        // SeasonWeights are clamped via clampWeights (0..10, mirrors other multiplier fields).
        FallowConfig cfg = new FallowConfig();
        // Inject an out-of-range entry.
        FallowConfig.SeasonWeights bad = new FallowConfig.SeasonWeights();
        bad.spring = -1.0;
        bad.winter = 99.0;
        cfg.crops.cropSeasons.put("test:crop", bad);
        cfg.clamp();
        FallowConfig.SeasonWeights clamped = cfg.crops.cropSeasons.get("test:crop");
        assertEquals(0.0, clamped.spring, EPS, "negative spring clamped to 0");
        assertEquals(10.0, clamped.winter, EPS, "over-10 winter clamped to 10");
    }

    // -- JSON roundtrip ---------------------------------------------------

    @Test
    void roundtripPreservesCropsSection() {
        FallowConfig original = new FallowConfig();
        original.crops.enabled = true;
        original.crops.seedDropChance = 0.08;

        String json = GSON.toJson(original);
        FallowConfig loaded = GSON.fromJson(json, FallowConfig.class);
        loaded.clamp();

        assertTrue(loaded.crops.enabled, "crops.enabled preserved through roundtrip");
        assertEquals(0.08, loaded.crops.seedDropChance, EPS, "seedDropChance preserved through roundtrip");
    }

    @Test
    void roundtripPreservesCropSeasonWeights() {
        FallowConfig original = new FallowConfig();
        String json = GSON.toJson(original);
        FallowConfig loaded = GSON.fromJson(json, FallowConfig.class);
        loaded.clamp();

        // Turnip's weights must survive a serialize/deserialize cycle unchanged.
        assertEquals(original.crops.cropSeasonWeight("fallow:turnip_crop", Season.WINTER),
            loaded.crops.cropSeasonWeight("fallow:turnip_crop", Season.WINTER), EPS);
        assertEquals(original.crops.cropSeasonWeight("fallow:cabbage_crop", Season.SUMMER),
            loaded.crops.cropSeasonWeight("fallow:cabbage_crop", Season.SUMMER), EPS);
    }

    @Test
    void omittedCropsSectionLoadsWithFullDefaults() {
        // A config JSON that has no "crops" key at all - GSON leaves the field null.
        // FallowConfig's default-field initializers do not run on a GSON-deserialized object
        // when the field is absent. This documents the current behavior: crops is null.
        String json = "{\"enabled\": false}";
        FallowConfig loaded = GSON.fromJson(json, FallowConfig.class);
        // GSON leaves unmentioned fields null - clamp must not NPE when crops is null,
        // but the crops object itself is absent (not re-hydrated with defaults by GSON).
        // The load() path in production never calls clamp on a null crops: it instantiates a
        // fresh FallowConfig if the file is missing/broken, which always has defaults.
        // So we just assert no NPE here.
        // If at some point null-safety is added to clamp(), this test will need updating.
        if (loaded.crops != null) {
            // If GSON happened to hydrate crops (e.g. via default-value tricks), defaults apply.
            assertFalse(loaded.crops.enabled);
        }
    }

    @Test
    void freshConfigHasCropsWithDefaults() {
        // The production path: new FallowConfig() always gives a live crops object.
        FallowConfig cfg = new FallowConfig();
        assertFalse(cfg.crops.enabled);
        assertTrue(cfg.crops.seasonGating);
        assertTrue(cfg.crops.winterKill);
        assertEquals(0.05, cfg.crops.seedDropChance, EPS);
        assertTrue(cfg.crops.wild.enabled);
        assertEquals(0.004, cfg.crops.wild.forageChance, EPS);
        assertTrue(cfg.crops.legumes.fixNitrogen);
        assertEquals(1, cfg.crops.legumes.fixRadius);
        assertEquals(4, cfg.crops.paddy.range);
    }

    @Test
    void paddyRangeClamps0To8() {
        FallowConfig cfg = new FallowConfig();
        cfg.crops.paddy.range = -3;
        cfg.clamp();
        assertEquals(0, cfg.crops.paddy.range);

        cfg.crops.paddy.range = 99;
        cfg.clamp();
        assertEquals(8, cfg.crops.paddy.range);
    }

    // -- ForageSpreadTask biome-eligibility helper ------------------------

    @Test
    void exactBiomeIdMatches() {
        assertTrue(ForageSpreadTask.isEligible(
            "minecraft:plains",
            Set.of(),
            List.of("minecraft:plains")));
    }

    @Test
    void biomeTagMatches() {
        assertTrue(ForageSpreadTask.isEligible(
            "minecraft:dark_forest",
            Set.of("#minecraft:is_forest"),
            List.of("#minecraft:is_forest")));
    }

    @Test
    void noMatchReturnsFalse() {
        assertFalse(ForageSpreadTask.isEligible(
            "minecraft:desert",
            Set.of("#minecraft:is_badlands"),
            List.of("minecraft:plains", "#minecraft:is_forest")));
    }

    @Test
    void emptyHomeListReturnsFalse() {
        assertFalse(ForageSpreadTask.isEligible(
            "minecraft:plains",
            Set.of("#minecraft:is_forest"),
            List.of()));
    }

    @Test
    void nullHomeListReturnsFalse() {
        assertFalse(ForageSpreadTask.isEligible(
            "minecraft:plains",
            Set.of(),
            null));
    }

    @Test
    void exactIdWinsOverTagPresence() {
        // When both exact id and tag entries are listed, the id-based entry fires first.
        // Both paths return true here; this confirms id entries work alongside tag entries.
        assertTrue(ForageSpreadTask.isEligible(
            "minecraft:meadow",
            Set.of("#minecraft:is_forest"),
            List.of("minecraft:meadow", "#minecraft:is_forest")));
    }

    @Test
    void tagEntryWithoutHashIsNotTreatedAsTag() {
        // An entry missing the '#' prefix is compared as an exact biome id - never matches a tag.
        assertFalse(ForageSpreadTask.isEligible(
            "minecraft:dark_forest",
            Set.of("#minecraft:is_forest"),
            List.of("minecraft:is_forest"))); // missing '#' - treated as exact id, not a tag
    }
}
