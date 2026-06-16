package dev.isaac.fallow.trail;

/**
 * Pure wear/recovery transition logic for trails (split out for unit testing).
 *
 * <p>Wear path: grass block -> coarse dirt (at {@code stepsToCoarse}) -> dirt path (at
 * {@code stepsToPath}). Wear is capped at {@code 2 * stepsToPath} so an over-trodden trail
 * still recovers in bounded time. Recovery path: dirt path -> coarse dirt -> plain dirt, after
 * which vanilla grass spread (or Fallow's own regrowth) takes over.
 */
public final class TrailMath {
    private TrailMath() {
    }

    /** What kind of surface a tracked position currently is. */
    public enum Surface {
        GRASS,
        COARSE_DIRT,
        PATH,
        /** Anything else: the block was changed externally; the entry should be dropped. */
        OTHER,
    }

    /** Conversion to apply, or null for none. */
    public enum Convert {
        TO_COARSE_DIRT,
        TO_PATH,
        TO_DIRT,
    }

    public record Step(int newWear, Convert convert) {
    }

    public record Recovery(int newWear, Convert convert, boolean evict) {
    }

    /** One footstep on a tracked (or about-to-be-tracked) surface. */
    public static Step step(Surface surface, int wear, int stepsToCoarse, int stepsToPath) {
        int w = Math.min(wear + 1, stepsToPath * 2);
        Convert convert = null;
        if (surface == Surface.GRASS && w >= stepsToCoarse) {
            convert = Convert.TO_COARSE_DIRT;
        } else if (surface == Surface.COARSE_DIRT && w >= stepsToPath) {
            convert = Convert.TO_PATH;
        }
        return new Step(w, convert);
    }

    /** One decay pass over an untrodden entry. */
    public static Recovery recover(Surface surface, int wear, int recoveryAmount, int stepsToCoarse) {
        if (surface == Surface.OTHER) {
            return new Recovery(0, null, true);
        }
        int w = Math.max(0, wear - recoveryAmount);
        if (surface == Surface.PATH && w <= stepsToCoarse) {
            return new Recovery(w, Convert.TO_COARSE_DIRT, false);
        }
        if (surface == Surface.COARSE_DIRT && w <= 0) {
            return new Recovery(0, Convert.TO_DIRT, true);
        }
        if (w <= 0) {
            return new Recovery(0, null, true);
        }
        return new Recovery(w, null, false);
    }
}
