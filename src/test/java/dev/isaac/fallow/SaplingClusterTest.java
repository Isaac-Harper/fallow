package dev.isaac.fallow;

import dev.isaac.fallow.ecology.SaplingCluster;
import dev.isaac.fallow.ecology.SaplingCluster.CellTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-geometry coverage for the 2x2 clustering nudge used by dark/pale oak. No Minecraft bootstrap
 * - the world reads are abstracted behind {@link CellTest}, mirroring the unit-test boundary of
 * {@code SaplingNames} / {@code SaplingPhenology}.
 */
class SaplingClusterTest {
    private static final CellTest NONE = (x, z) -> false;
    private static final CellTest ALL = (x, z) -> true;

    /** A cell test backed by an explicit set of (x, z) pairs. */
    private static CellTest cells(int... xz) {
        Set<String> set = new HashSet<>();
        for (int i = 0; i < xz.length; i += 2) {
            set.add(xz[i] + "," + xz[i + 1]);
        }
        return (x, z) -> set.contains(x + "," + z);
    }

    @Test
    void foundsAtRolledSpotWhenNoNeighbors() {
        // Nothing nearby to build on: place a lone founder exactly where the roll landed.
        assertArrayEquals(new int[] {0, 0}, SaplingCluster.chooseTarget(0, 0, 1, NONE, ALL));
    }

    @Test
    void completesThreeCornerClusterByRelocating() {
        // Three corners of the square with min (1,1) are filled; its missing corner (1,1) sits one
        // block from the rolled spot (0,0), so the seed snaps there and the 2x2 is finished.
        CellTest occ = cells(2, 1, 1, 2, 2, 2);
        assertArrayEquals(new int[] {1, 1}, SaplingCluster.chooseTarget(0, 0, 1, occ, ALL));
    }

    @Test
    void relocatesToExtendTowardTheStrongerSquare() {
        // A two-corner partial (2,1)+(2,2) is reachable from (1,1) but not from the rolled spot;
        // the seed shifts to (1,1), taking that square from 2 toward 3.
        CellTest occ = cells(2, 1, 2, 2);
        assertArrayEquals(new int[] {1, 1}, SaplingCluster.chooseTarget(0, 0, 1, occ, ALL));
    }

    @Test
    void prefersRolledSpotWhenItAlreadyAdvancesACluster() {
        // The rolled spot is itself adjacent to a sapling, and no neighbor scores higher: don't move.
        CellTest occ = cells(1, 1);
        assertArrayEquals(new int[] {0, 0}, SaplingCluster.chooseTarget(0, 0, 1, occ, ALL));
    }

    @Test
    void snapRadiusZeroNeverRelocates() {
        // Off switch: even with a completable cluster one block away, radius 0 only ever uses the
        // rolled spot (which here advances nothing), so it founders in place.
        CellTest occ = cells(2, 1, 1, 2, 2, 2);
        assertArrayEquals(new int[] {0, 0}, SaplingCluster.chooseTarget(0, 0, 0, occ, ALL));
    }

    @Test
    void placesEvenWhenRolledSpotItselfIsBlocked() {
        // The rolled spot is unplantable, but the cluster's missing corner (1,1) is open and within
        // reach: relocation doesn't require the rolled spot to be valid.
        CellTest occ = cells(2, 1, 1, 2, 2, 2);
        CellTest placeable = (x, z) -> x == 1 && z == 1;
        assertArrayEquals(new int[] {1, 1}, SaplingCluster.chooseTarget(0, 0, 1, occ, placeable));
    }

    @Test
    void returnsNullWhenNothingIsPlaceable() {
        assertNull(SaplingCluster.chooseTarget(0, 0, 1, NONE, NONE));
    }

    @Test
    void neverChoosesACellBeyondSnapRadius() {
        // A cluster whose missing corner is two cells away can't be completed at radius 1; whatever
        // the helper returns must still lie within the relocation budget.
        CellTest occ = cells(3, 1, 2, 2, 3, 2);
        int[] chosen = SaplingCluster.chooseTarget(0, 0, 1, occ, ALL);
        assertTrue(chosen != null
            && Math.max(Math.abs(chosen[0]), Math.abs(chosen[1])) <= 1, "stayed within snap radius");
    }
}
