package dev.isaac.fallow.ecology;

/**
 * Pure 2x2 placement targeting for the mega-only saplings (dark oak, pale oak), split out so the
 * geometry is unit-testable without a world. These species have no single-tree grower: a lone
 * sapling never grows, but four sharing a flat 2x2 footprint do (vanilla grows them on random
 * tick). So when Fallow seeds one, we nudge it up to {@code snapRadius} blocks onto the cell that
 * best advances a nearby partial 2x2 - completing a three-corner cluster when one is within reach,
 * otherwise extending a smaller one, otherwise founding a fresh cluster at the rolled spot. One
 * sapling per event; the rarity of a rolled candidate landing within reach of a partial is what
 * keeps the spread slow.
 *
 * <p>{@code snapRadius} is a <em>relocation</em> distance, not a scan radius: at radius 1 the
 * sapling may shift one block, which lets it complete a cluster whose missing corner sits up to two
 * cells from the rolled spot. (A pure scan radius of 1 would be a no-op - every 2x2 that fits a 3x3
 * window already contains the rolled spot, so "best square" always resolves back to it.)
 */
public final class SaplingCluster {
    /** A cheap (x, z) test so the geometry can run over a world or a plain test grid. */
    @FunctionalInterface
    public interface CellTest {
        boolean test(int x, int z);
    }

    private SaplingCluster() {
    }

    /**
     * Pick where to place one sapling near the rolled spot ({@code px}, {@code pz}).
     *
     * @param snapRadius max Chebyshev distance the sapling may be nudged from the rolled spot
     *                   (0 = never relocate; the bias is off)
     * @param occupied   coplanar same-species saplings already standing at a cell
     * @param placeable  whether a cell is a valid empty spot for this sapling
     * @return {@code {x, z}} of the chosen cell, or {@code null} to place nothing this event
     */
    public static int[] chooseTarget(int px, int pz, int snapRadius, CellTest occupied, CellTest placeable) {
        // A relocation only wins if it actually advances a cluster (score >= 1); otherwise we fall
        // back to a lone founder at the rolled spot, and only if that spot is itself placeable.
        int[] founder = placeable.test(px, pz) ? new int[] {px, pz} : null;
        int[] best = null;
        int bestScore = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int dz = -snapRadius; dz <= snapRadius; dz++) {
            for (int dx = -snapRadius; dx <= snapRadius; dx++) {
                int x = px + dx;
                int z = pz + dz;
                if (!placeable.test(x, z)) {
                    continue;
                }
                int score = scoreAt(x, z, occupied);
                if (score < 1) {
                    continue;
                }
                // Highest-completion square wins; ties go to the cell nearest the rolled spot (so
                // dist 0 = the rolled spot is preferred), then to scan order. All deterministic.
                int dist = Math.max(Math.abs(dx), Math.abs(dz));
                if (score > bestScore || (score == bestScore && dist < bestDist)) {
                    best = new int[] {x, z};
                    bestScore = score;
                    bestDist = dist;
                }
            }
        }
        return best != null ? best : founder;
    }

    /**
     * Highest same-species occupancy among the four 2x2 squares that include this (empty) cell -
     * i.e. how close placing here comes to completing a 2x2 (3 = the last corner, grows next tick).
     */
    private static int scoreAt(int x, int z, CellTest occupied) {
        int best = 0;
        for (int ox = -1; ox <= 0; ox++) {
            for (int oz = -1; oz <= 0; oz++) {
                // Square with min corner (x + ox, z + oz): count its three cells other than (x, z).
                int count = 0;
                for (int cx = 0; cx <= 1; cx++) {
                    for (int cz = 0; cz <= 1; cz++) {
                        int sx = x + ox + cx;
                        int sz = z + oz + cz;
                        if ((sx != x || sz != z) && occupied.test(sx, sz)) {
                            count++;
                        }
                    }
                }
                best = Math.max(best, count);
            }
        }
        return best;
    }
}
