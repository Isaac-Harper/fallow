package dev.isaac.fallow.season;

/**
 * Side-agnostic holder for the current seasonal temperature offset, read by the common
 * {@code mixin.BiomeTemperatureMixin} on every {@code Biome.getTemperature} call - i.e. by both
 * the server's snow/freeze logic and the client's rain/snow particle rendering. The server
 * computes it from config + season each tick and ships it in {@code SeasonSyncPayload} so the
 * client matches exactly. {@code 0} = neutral (vanilla temperatures): out of season, when
 * precipitation is disabled, on a vanilla server, or after disconnect. One volatile - render and
 * worker threads read it lock-free.
 */
public final class SeasonalTemperature {
    private static volatile float offset = 0.0f;

    private SeasonalTemperature() {
    }

    public static float offset() {
        return offset;
    }

    public static void set(float value) {
        offset = value;
    }
}
