package dev.isaac.fallow;

import dev.isaac.fallow.ecology.SaplingNames;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaplingNamesTest {
    @Test
    void plainLogs() {
        assertEquals(List.of("oak_sapling", "oak_propagule"), SaplingNames.saplingCandidates("oak_log"));
        assertEquals(List.of("cherry_sapling", "cherry_propagule"), SaplingNames.saplingCandidates("cherry_log"));
    }

    @Test
    void strippedAndWoodVariants() {
        assertEquals("birch_sapling", SaplingNames.saplingCandidates("stripped_birch_log").get(0));
        assertEquals("spruce_sapling", SaplingNames.saplingCandidates("spruce_wood").get(0));
        assertEquals("jungle_sapling", SaplingNames.saplingCandidates("stripped_jungle_wood").get(0));
    }

    @Test
    void mangroveResolvesViaPropaguleFallback() {
        // The registry lookup tries _sapling first, then _propagule; mangrove only has the latter.
        assertTrue(SaplingNames.saplingCandidates("mangrove_log").contains("mangrove_propagule"));
    }

    @Test
    void netherStemsDeriveButWontResolve() {
        // crimson_sapling / crimson_propagule are not registered blocks: the task skips them.
        assertEquals(List.of("crimson_sapling", "crimson_propagule"), SaplingNames.saplingCandidates("crimson_stem"));
    }

    @Test
    void nonLogsYieldNothing() {
        assertTrue(SaplingNames.saplingCandidates("smooth_stone").isEmpty());
        assertTrue(SaplingNames.saplingCandidates("logjam").isEmpty(), "suffix must match whole word segment");
    }

    @Test
    void moddedNamesKeepTheirConvention() {
        assertEquals("redwood_sapling", SaplingNames.saplingCandidates("redwood_log").get(0));
    }
}
