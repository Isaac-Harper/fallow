package dev.isaac.fallow.diet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for {@link DietService#shouldReapply}, the pure predicate that guards the
 * per-second Absorption refresh. Re-adding the effect resets absorption hearts to the tier
 * maximum (vanilla {@code onEffectStarted}), so a same-or-stronger instance with time left must
 * be left alone; only an absent, weaker, or nearly-expired instance may be re-applied.
 */
class DietServiceTest {

    // 35s grant = 700 ticks; the refresh window is the last 5s (100 ticks).
    private static final int FULL_GRANT = 20 * 35;

    @Test
    void reappliesWhenNoEffectPresent() {
        assertTrue(DietService.shouldReapply(false, 0, -1, 0),
            "an absent effect must always be (re)applied");
    }

    @Test
    void reappliesWhenExistingIsWeakerTier() {
        // Existing Absorption I with plenty of time left, but the player now qualifies for II.
        assertTrue(DietService.shouldReapply(true, FULL_GRANT, 0, 1),
            "a weaker tier must be upgraded even with duration remaining");
    }

    @Test
    void doesNotReapplySameTierWithTimeLeft() {
        // Same tier, fresh duration: leave it so combat-consumed hearts are not regenerated.
        assertFalse(DietService.shouldReapply(true, FULL_GRANT, 1, 1),
            "a same-tier effect with time left must not be refreshed");
    }

    @Test
    void doesNotReapplyStrongerTierWithTimeLeft() {
        // A stronger existing tier (e.g. a longer-lived grant) is never downgraded or refreshed.
        assertFalse(DietService.shouldReapply(true, FULL_GRANT, 2, 1),
            "a stronger existing tier with time left must not be touched");
    }

    @Test
    void reappliesSameTierWhenNearlyExpired() {
        // Within the last 5s of the grant: refresh so the bonus does not lapse between ticks.
        assertTrue(DietService.shouldReapply(true, 40, 1, 1),
            "a same-tier effect within the refresh window must be re-applied");
    }

    @Test
    void refreshWindowBoundaryIsInclusive() {
        assertTrue(DietService.shouldReapply(true, 20 * 5, 1, 1),
            "exactly at the refresh window edge (100 ticks) must re-apply");
        assertFalse(DietService.shouldReapply(true, 20 * 5 + 1, 1, 1),
            "one tick above the refresh window must not re-apply");
    }
}
