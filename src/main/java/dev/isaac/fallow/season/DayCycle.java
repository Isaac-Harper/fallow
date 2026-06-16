package dev.isaac.fallow.season;

/**
 * Pure math for the seasonal day/night split.
 *
 * <p>The 26.1 world clock advances {@code totalTicks} by {@code rate} per real tick (with a
 * fractional accumulator, vanilla-side). We slow the clock during the day phase and speed it up
 * at night (or vice versa) so the daylight share of the cycle moves while one full cycle still
 * costs exactly 24000 real ticks: with day portion {@code p}, the day phase (12000 clock ticks)
 * runs at {@code 0.5/p} and the night phase at {@code 0.5/(1-p)}, and
 * {@code 12000/(0.5/p) + 12000/(0.5/(1-p)) = 24000p + 24000(1-p) = 24000}.
 *
 * <p>{@code p = 0.5} yields exactly 1.0 in both phases - bit-identical to vanilla.
 */
public final class DayCycle {
    public static final long CYCLE_TICKS = 24000L;
    public static final long DAY_PHASE_TICKS = 12000L;

    private DayCycle() {
    }

    /** True if the clock time is in the daylight half of the cycle. */
    public static boolean isDayPhase(long clockTicks) {
        return phaseOf(clockTicks) < DAY_PHASE_TICKS;
    }

    /** Clock ticks within the current cycle, safe for negative time values. */
    public static long phaseOf(long clockTicks) {
        return Math.floorMod(clockTicks, CYCLE_TICKS);
    }

    /** Current day index, safe for negative time values. */
    public static long dayOf(long clockTicks) {
        return Math.floorDiv(clockTicks, CYCLE_TICKS);
    }

    /**
     * Clock rate for the current phase given the season's day portion. The portion is expected
     * pre-clamped to [0.05, 0.95] by config loading; clamped again here for safety.
     */
    public static float rateFor(long clockTicks, double dayPortion) {
        double p = Math.max(0.05, Math.min(0.95, dayPortion));
        double rate = isDayPhase(clockTicks) ? 0.5 / p : 0.5 / (1.0 - p);
        return (float) rate;
    }
}
