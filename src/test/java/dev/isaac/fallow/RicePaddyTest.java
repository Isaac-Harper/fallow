package dev.isaac.fallow;

import dev.isaac.fallow.block.RicePaddy;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic coverage for the rice paddy water helper. Exercises the (2*range+1) horizontal scan
 * over the farmland's own Y and one below, using a fake water predicate. No Minecraft bootstrap.
 */
class RicePaddyTest {
    private static final BlockPos FARMLAND = new BlockPos(0, 64, 0);

    @Test
    void waterAtSameLayerWithinRangeIsFound() {
        Set<BlockPos> water = Set.of(new BlockPos(3, 64, 0));
        assertTrue(RicePaddy.hasWaterWithin(FARMLAND, 4, water::contains));
    }

    @Test
    void waterOneBelowIsFound() {
        Set<BlockPos> water = Set.of(new BlockPos(-2, 63, 1));
        assertTrue(RicePaddy.hasWaterWithin(FARMLAND, 4, water::contains));
    }

    @Test
    void waterBeyondRangeIsNotFound() {
        Set<BlockPos> water = Set.of(new BlockPos(5, 64, 0));
        assertFalse(RicePaddy.hasWaterWithin(FARMLAND, 4, water::contains));
    }

    @Test
    void waterTwoBelowIsNotFound() {
        // Only the farmland Y and one below count; deeper water does not qualify.
        Set<BlockPos> water = Set.of(new BlockPos(0, 62, 0));
        assertFalse(RicePaddy.hasWaterWithin(FARMLAND, 4, water::contains));
    }

    @Test
    void waterAboveIsNotFound() {
        Set<BlockPos> water = Set.of(new BlockPos(0, 65, 0));
        assertFalse(RicePaddy.hasWaterWithin(FARMLAND, 4, water::contains));
    }

    @Test
    void noWaterAnywhereIsFalse() {
        assertFalse(RicePaddy.hasWaterWithin(FARMLAND, 4, p -> false));
    }

    @Test
    void rangeZeroChecksOnlyTheColumnAndBelow() {
        // range 0: only the farmland column at its Y and one below.
        assertTrue(RicePaddy.hasWaterWithin(FARMLAND, 0,
            Set.of(new BlockPos(0, 63, 0))::contains));
        assertFalse(RicePaddy.hasWaterWithin(FARMLAND, 0,
            Set.of(new BlockPos(1, 64, 0))::contains));
    }
}
