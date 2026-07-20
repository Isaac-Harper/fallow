package dev.isaac.fallow.client;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.network.SeasonSyncPayload;
import dev.isaac.fallow.season.SeasonalTemperature;
import dev.isaac.fallow.visual.SeasonTint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Client-side season state + the live tint parameters read by {@code BiomeMixin} on every
 * grass/foliage color lookup (hot path: one volatile read of an immutable triple, a branch,
 * three channel blends, no allocation).
 *
 * <p>State arrives via {@link SeasonSyncPayload} (join + every change). No payload - vanilla
 * server, or seasons/visuals disabled - means identity params, i.e. exactly vanilla colors.
 * The overworld gate is read <em>live</em> from the client level inside {@link #recompute}
 * (no tick-ordering dependency on the sync), so the tint applies the moment the join packet
 * lands; a per-tick dimension watch only re-triggers recompute when you cross to/from the
 * Nether/End so crimson forests never turn autumnal.
 *
 * <p>All three surface kinds swap atomically (single volatile {@link Tints} record), so a
 * chunk mesh never mixes old grass with new foliage parameters. When the effective
 * parameters change, chunk meshes rebuild once ({@code LevelRenderer.allChanged}, which also
 * flushes the biome tint caches) - a few times per in-game day at most.
 */
public final class FallowClientSeasons {
    private record Tints(SeasonTint.Params grass, SeasonTint.Params foliage, SeasonTint.Params dry,
                         SeasonTint.Params birch, SeasonTint.Params spruce, SeasonTint.Params lily) {
        static final Tints NONE = new Tints(SeasonTint.Params.NONE, SeasonTint.Params.NONE,
            SeasonTint.Params.NONE, SeasonTint.Params.NONE, SeasonTint.Params.NONE,
            SeasonTint.Params.NONE);
    }

    private static volatile Tints tints = Tints.NONE;
    /** Season whose recolored leaf models should show now, or null for none (read by {@link SeasonalLeafModels}). */
    private static volatile Season swapSeason;
    private static SeasonSyncPayload lastPayload;
    private static ResourceKey<Level> lastDim;
    private static boolean dimSeen;

    private FallowClientSeasons() {
    }

    public static void onSync(SeasonSyncPayload payload) {
        lastPayload = payload;
        // Server-authoritative: match its rain/snow particle type to its snow placement (read by
        // BiomeTemperatureMixin on this client too). Gameplay-driven, so it ignores visuals config.
        SeasonalTemperature.set(payload.enabled() ? payload.tempOffset() : 0.0f);
        recompute();
    }

    /** Disconnect: back to vanilla colors and vanilla temperatures. */
    public static void clear() {
        lastPayload = null;
        SeasonalTemperature.set(0.0f);
        recompute();
    }

    /** Config edits change the effective tint parameters without a new payload; recompute now. */
    public static void refresh() {
        recompute();
    }

    /** Called every client tick: re-trigger recompute when the dimension changes (overworld-only tint). */
    public static void tickDimensionWatch(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        ResourceKey<Level> key = level == null ? null : level.dimension();
        if (!dimSeen || key != lastDim) {
            dimSeen = true;
            lastDim = key;
            recompute();
        }
    }

    private static void recompute() {
        SeasonSyncPayload payload = lastPayload;
        ClientLevel level = Minecraft.getInstance().level;
        boolean overworld = level != null && level.dimension() == Level.OVERWORLD;
        Tints next = Tints.NONE;
        Season nextSwapSeason = null;
        var visuals = Fallow.CONFIG.visuals;
        if (payload != null && payload.enabled() && overworld && visuals.enabled && visuals.strength > 0) {
            Season season = Season.values()[Math.floorMod(payload.season(), Season.values().length)];
            nextSwapSeason = season;
            java.util.function.Function<SeasonTint.Kind, SeasonTint.Params> tint = kind ->
                SeasonTint.forDay(season, payload.dayInSeason(), payload.daysPerSeason(),
                    kind, visuals.smoothTransitions, visuals.strength);
            next = new Tints(
                tint.apply(SeasonTint.Kind.GRASS),
                tint.apply(SeasonTint.Kind.FOLIAGE),
                tint.apply(SeasonTint.Kind.DRY_FOLIAGE),
                tint.apply(SeasonTint.Kind.BIRCH_FOLIAGE),
                tint.apply(SeasonTint.Kind.SPRUCE_FOLIAGE),
                tint.apply(SeasonTint.Kind.LILY_PAD));
        }
        boolean changed = !next.equals(tints) || nextSwapSeason != swapSeason;
        tints = next;
        swapSeason = nextSwapSeason;
        if (changed) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.levelRenderer != null && minecraft.level != null) {
                // allChanged (clear tint caches + rebuild all sections) moved off LevelRenderer
                // onto the new LevelExtractor in 26.2; both forms do the same seasonal tint refresh.
                //? if >=26.2 {
                minecraft.execute(minecraft.levelExtractor::allChanged);
                //?} else {
                /*minecraft.execute(minecraft.levelRenderer::allChanged);*/
                //?}
            }
        }
    }

    /**
     * Current seasonal leaf-fall weight (autumn-peaked), read by {@code LeafFallMixin} to scale
     * vanilla's falling-leaf particle rate so it matches the ground litter. 1.0 = neutral
     * (no Fallow server / seasons off) -> vanilla particle rate.
     */
    public static float leafFallWeight() {
        SeasonSyncPayload payload = lastPayload;
        if (payload == null || !payload.enabled()) {
            return 1.0f;
        }
        Season season = Season.values()[Math.floorMod(payload.season(), Season.values().length)];
        return (float) Fallow.CONFIG.leafLitter.leafFallWeight(season);
    }

    /**
     * Continuous progress through the year in {@code [0, 1)} for the Season Clock's hand: the
     * season's quadrant plus how far into it we are (day-in-season + intra-day time), so the dial
     * sweeps smoothly instead of snapping at season boundaries. When seasons are frozen the hand
     * holds on the current season; on a vanilla server (no sync) it sits at the start of the year.
     */
    public static float yearFraction(ClientLevel level) {
        SeasonSyncPayload payload = lastPayload;
        if (payload == null) {
            return 0.0f;
        }
        int seasons = Season.values().length;
        int ordinal = Math.floorMod(payload.season(), seasons);
        if (!payload.enabled()) {
            return (float) ordinal / seasons;
        }
        double days = Math.max(1, payload.daysPerSeason());
        double intraDay = level != null
            ? Math.floorMod(level.getOverworldClockTime(), 24000L) / 24000.0 : 0.0;
        double seasonPos = ordinal + (payload.dayInSeason() + intraDay) / days; // [0, seasons)
        return (float) (seasonPos / seasons); // [0, 1)
    }

    public static int tintGrass(int color) {
        return SeasonTint.apply(color, tints.grass());
    }

    public static int tintFoliage(int color) {
        return SeasonTint.apply(color, tints.foliage());
    }

    public static int tintDryFoliage(int color) {
        return SeasonTint.apply(color, tints.dry());
    }

    /**
     * Birch leaves, spruce leaves and lily pads all use fixed (non-biome) colors in vanilla, so
     * they bypass {@code BiomeMixin}; {@code FixedFoliageTintMixin} routes their constant colors
     * through these. Birch goes golden yellow in autumn (vs. broadleaf orange), spruce only
     * frost-mutes in winter (evergreen), and lily pads green up / yellow / brown through the year.
     */
    public static int tintBirchFoliage(int color) {
        return SeasonTint.apply(color, tints.birch());
    }

    public static int tintSpruceFoliage(int color) {
        return SeasonTint.apply(color, tints.spruce());
    }

    public static int tintLilyPad(int color) {
        return SeasonTint.apply(color, tints.lily());
    }

    /** The season whose recolored leaf models should render now, or null if none should. */
    public static Season swapSeason() {
        return swapSeason;
    }
}
