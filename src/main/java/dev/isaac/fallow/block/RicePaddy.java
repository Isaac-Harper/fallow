package dev.isaac.fallow.block;

import net.minecraft.core.BlockPos;

import java.util.function.Predicate;

/**
 * Rice paddy rule: rice only grows when water sits within {@code range} blocks horizontally of the
 * farmland, at the farmland's own Y or one block below. Pure geometry over a water predicate so it
 * is unit-testable without a live level.
 */
public final class RicePaddy {
    private RicePaddy() {
    }

    /**
     * True when any position within {@code range} horizontal blocks of {@code farmland} (same Y or
     * one below) satisfies {@code isWater}. Scans a flat (2*range+1) square on each of the two
     * layers. The farmland column itself is included in the scan.
     */
    public static boolean hasWaterWithin(BlockPos farmland, int range, Predicate<BlockPos> isWater) {
        for (int dy = 0; dy >= -1; dy--) {
            for (int dx = -range; dx <= range; dx++) {
                for (int dz = -range; dz <= range; dz++) {
                    if (isWater.test(farmland.offset(dx, dy, dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
