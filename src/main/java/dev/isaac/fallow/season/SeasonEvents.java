package dev.isaac.fallow.season;

/**
 * Side-agnostic holder for the currently active seasonal event and the transient modifiers it
 * applies, read on hot paths by the precipitation/growth tasks. {@code NONE} with neutral
 * modifiers (1.0 / 0.0) is the normal state. Driven by {@link SeasonEventService}; volatiles so
 * worker threads read lock-free.
 */
public final class SeasonEvents {
    public enum Kind {
        NONE, BLIZZARD, HEATWAVE, STORM
    }

    private static volatile Kind active = Kind.NONE;
    private static volatile double snowMultiplier = 1.0;
    private static volatile double growthMultiplier = 1.0;
    private static volatile float tempBonus = 0.0f;

    private SeasonEvents() {
    }

    public static Kind active() {
        return active;
    }

    /** Snow-accumulation multiplier (blizzard piles snow faster). */
    public static double snowMultiplier() {
        return snowMultiplier;
    }

    /** Growth multiplier (heatwave stalls growth). */
    public static double growthMultiplier() {
        return growthMultiplier;
    }

    /** Temperature bonus added to the seasonal offset (heatwave warms -> melts/dries). */
    public static float tempBonus() {
        return tempBonus;
    }

    static void begin(Kind kind, double snow, double growth, float temp) {
        snowMultiplier = snow;
        growthMultiplier = growth;
        tempBonus = temp;
        active = kind;
    }

    static void clear() {
        active = Kind.NONE;
        snowMultiplier = 1.0;
        growthMultiplier = 1.0;
        tempBonus = 0.0f;
    }
}
