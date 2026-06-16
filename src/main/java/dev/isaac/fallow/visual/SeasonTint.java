package dev.isaac.fallow.visual;

import dev.isaac.fallow.api.Season;

/**
 * Pure seasonal tint math (main source set so it's unit-testable; only the client uses it).
 *
 * <p>Each season defines a target color and blend strength per surface kind; the season's
 * look is its midpoint, and with smooth transitions the parameters interpolate linearly
 * between adjacent season midpoints as days pass. Summer is identity (strength 0), so the
 * default look of the game is untouched for a quarter of the year, and disabling visuals or
 * seasons yields exactly vanilla colors everywhere.
 */
public final class SeasonTint {
    /** What is being tinted; foliage shifts harder than grass, dry foliage least. */
    public enum Kind {
        GRASS,
        FOLIAGE,
        DRY_FOLIAGE,
    }

    /** Blend parameters: {@code strength} 0..1 toward {@code targetRgb}. */
    public record Params(float strength, int targetRgb) {
        public static final Params NONE = new Params(0.0f, 0);
    }

    private SeasonTint() {
    }

    /**
     * The season's midpoint look for a surface kind. Strengths and targets are tuned to read
     * clearly at a glance (the subtle first pass was nearly invisible on grass): summer is
     * identity, the others are bold. The unit tests pin only the directional properties
     * (summer = vanilla, autumn adds red / removes green), so these values are free to tune.
     */
    public static Params seasonParams(Season season, Kind kind) {
        return switch (season) {
            case SPRING -> switch (kind) {
                case GRASS -> new Params(0.35f, 0x6FE32C);      // vivid fresh yellow-green
                case FOLIAGE -> new Params(0.32f, 0x63DA28);
                case DRY_FOLIAGE -> new Params(0.20f, 0x8FD24A);
            };
            case SUMMER -> Params.NONE;                          // identity: vanilla look
            case AUTUMN -> switch (kind) {
                case GRASS -> new Params(0.65f, 0xC8902A);      // golden, clearly turned
                case FOLIAGE -> new Params(0.80f, 0xCE5A12);    // strong orange
                case DRY_FOLIAGE -> new Params(0.55f, 0xB05418);
            };
            case WINTER -> switch (kind) {
                case GRASS -> new Params(0.62f, 0xAEB29A);      // pale dormant grey-tan
                case FOLIAGE -> new Params(0.60f, 0x9E9778);    // drab faded olive
                case DRY_FOLIAGE -> new Params(0.45f, 0x9C8F76);
            };
        };
    }

    /**
     * Effective parameters for a given day. Smooth mode interpolates between adjacent season
     * midpoints: the first half of a season blends from the previous season's look, the
     * second half toward the next season's.
     */
    public static Params forDay(Season season, int dayInSeason, int daysPerSeason,
                                Kind kind, boolean smooth, double globalStrength) {
        Params current = seasonParams(season, kind);
        Params effective;
        if (!smooth || daysPerSeason <= 1) {
            effective = current;
        } else {
            float progress = (dayInSeason + 0.5f) / daysPerSeason; // 0..1 through the season
            if (progress < 0.5f) {
                Params prev = seasonParams(previous(season), kind);
                effective = lerp(prev, current, progress + 0.5f);
            } else {
                Params next = seasonParams(season.next(), kind);
                effective = lerp(current, next, progress - 0.5f);
            }
        }
        return new Params((float) (effective.strength() * globalStrength), effective.targetRgb());
    }

    /** Blend a packed RGB color toward the params' target. Fast path: strength 0 is identity. */
    public static int apply(int color, Params params) {
        float s = params.strength();
        if (s <= 0.0f) {
            return color;
        }
        int target = params.targetRgb();
        int r = blendChannel((color >> 16) & 0xFF, (target >> 16) & 0xFF, s);
        int g = blendChannel((color >> 8) & 0xFF, (target >> 8) & 0xFF, s);
        int b = blendChannel(color & 0xFF, target & 0xFF, s);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    public static Params lerp(Params a, Params b, float t) {
        float strength = a.strength() + (b.strength() - a.strength()) * t;
        // An identity endpoint (summer) has a meaningless target color: keep the other side's
        // hue and let the fading strength do the work, instead of dragging through black.
        int target;
        if (a.strength() <= 0.0f) {
            target = b.targetRgb();
        } else if (b.strength() <= 0.0f) {
            target = a.targetRgb();
        } else {
            target = lerpRgb(a.targetRgb(), b.targetRgb(), t);
        }
        return new Params(strength, target);
    }

    private static int blendChannel(int from, int to, float t) {
        int v = Math.round(from + (to - from) * t);
        return Math.max(0, Math.min(255, v));
    }

    private static int lerpRgb(int a, int b, float t) {
        int r = blendChannel((a >> 16) & 0xFF, (b >> 16) & 0xFF, t);
        int g = blendChannel((a >> 8) & 0xFF, (b >> 8) & 0xFF, t);
        int bl = blendChannel(a & 0xFF, b & 0xFF, t);
        return (r << 16) | (g << 8) | bl;
    }

    private static Season previous(Season season) {
        Season[] values = Season.values();
        return values[(season.ordinal() + values.length - 1) % values.length];
    }
}
