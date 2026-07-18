package dev.isaac.fallow;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.isaac.fallow.api.Season;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loaded from {@code config/fallow.json}. Editable in-game via the Mod Menu config screen, by
 * hand, or re-read on a live server with {@code /fallow reload}. Defaults are written out on
 * first launch if the file is missing.
 *
 * <p>Server-side config (the simulation runs on the server); the client screen edits the same
 * file, which takes effect immediately in singleplayer and on next reload on a dedicated server.
 */
public final class FallowConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Master switch. Fallow modifies blocks in the world over time (vegetation, dirt paths, trees,
     * bamboo, snow, plant decay), which is effectively destructive, so it ships <b>off</b>: nothing
     * touches the world, the day length, or temperature until this is set true. Players still get a
     * one-time in-game notice on first join either way.
     */
    public boolean enabled = false;

    public Scheduler scheduler = new Scheduler();
    public Vegetation vegetation = new Vegetation();
    public Dieback dieback = new Dieback();
    public Saplings saplings = new Saplings();
    public Trails trails = new Trails();
    public LeafLitter leafLitter = new LeafLitter();
    public Overcrowding overcrowding = new Overcrowding();
    public FlowerWilt flowerWilt = new FlowerWilt();
    public Shoreline shoreline = new Shoreline();
    public Bamboo bamboo = new Bamboo();
    public Seasons seasons = new Seasons();
    public DayNight dayNight = new DayNight();
    public Visuals visuals = new Visuals();
    public Precipitation precipitation = new Precipitation();
    public Events events = new Events();
    public Fruiting fruiting = new Fruiting();
    public Crops crops = new Crops();

    /** Tick-budget knobs for the ecology scheduler. */
    public static final class Scheduler {
        public boolean enabled = true;
        /** Chunks visited per world tick (hard cap; round-robin over loaded ticking chunks). */
        public int chunksPerTick = 8;
        /** Random surface columns sampled per chunk visit. */
        public int samplesPerChunk = 4;
        /** Wall-clock budget per world tick, microseconds. The scheduler stops early past this. */
        public int tickBudgetMicros = 200;
        /** Periodically log scheduler timings (also visible anytime via /fallow stats). */
        public boolean logTimings = false;
        /** Dimensions the scheduler runs in. */
        public List<String> dimensions = defaultDimensions();

        private static List<String> defaultDimensions() {
            List<String> dims = new ArrayList<>();
            dims.add("minecraft:overworld");
            return dims;
        }
    }

    /** Vegetation sprouting task. Chances are per sampled candidate column, before season scaling. */
    public static final class Vegetation {
        public boolean enabled = true;
        /** Grass block + air above sprouts short grass. */
        public double shortGrassChance = 0.02;
        /** Existing short grass upgrades to tall grass. */
        public double tallGrassChance = 0.008;
        /** Grass block + air above sprouts a biome flower (the bonemeal flower list). */
        public double flowerChance = 0.004;
        /** Grass block + air above sprouts a bush copied from one growing nearby. */
        public double bushChance = 0.002;
        /** Minimum light at the sprout position (vanilla grass spread uses 9). */
        public int minLightLevel = 9;
        /** Density guard: skip flower/bush sprouting when the local area is already decorated. */
        public int densityRadius = 8;
        public int densityMaxPlants = 10;
        /** How far to look for an existing bush to copy. */
        public int bushSearchRadius = 6;
        /**
         * Per-biome multiplier on density caps (flowers/bushes here, saplings too). Keys are
         * biome ids ("minecraft:plains") or biome tags ("#minecraft:is_forest"); exact id wins
         * over tags; unlisted biomes use 1.0. Lush biomes saturate fuller, arid ones sparser.
         */
        public Map<String, Double> biomeDensity = defaultBiomeDensity();

        /**
         * Per-biome multiplier on growth <em>rate</em> (how fast grass/flowers/bushes/saplings
         * sprout), same key format as {@link #biomeDensity}. Lush biomes fill in faster, arid
         * ones slower; decay (dieback/wilt/crowding) is unaffected.
         */
        public Map<String, Double> biomeGrowth = defaultBiomeGrowth();

        /**
         * Per-biome <em>seasonality</em> - how strongly the shared season curve applies here,
         * same key format as {@link #biomeGrowth}. It scales the curve's amplitude around the
         * summer (1.0) baseline: {@code 0} = no seasons (flat year-round - tropical biomes never
         * get a dead season), {@code 1} = the full shared curve (temperate baseline), {@code >1}
         * = amplified so winter growth clamps to zero (a true no-growth season) and spring booms
         * (boreal/snowy). Applies to vegetation growth <em>and</em> decay alike - a tropical biome
         * has muted winter dieback too. Saplings are unaffected (they run their own per-species
         * {@code phenology}). Unlisted biomes use 1.0.
         */
        public Map<String, Double> biomeSeasonality = defaultBiomeSeasonality();

        /**
         * Per-biome season <em>phase</em> offset (0-3), same key format as {@link #biomeGrowth}.
         * Shifts which season a biome's growth/dieback curve peaks in: 0 = the standard
         * spring-peaked temperate calendar; 3 (= -1) makes a biome treat summer as its peak, e.g.
         * a savanna whose wet/growth season is summer. Affects growth and decay only - snow type
         * and foliage tint stay on the global calendar. Unlisted biomes use 0.
         */
        public Map<String, Double> biomeSeasonPhase = defaultBiomeSeasonPhase();

        /**
         * Season -> preferred flower block ids. When sprouting a flower, Fallow biases toward
         * in-season flowers <em>that the biome's own palette already offers</em> (so it stays
         * biome-correct), falling back to the biome's random pick. Bounded by vanilla's small
         * flower roster - spring vs summer differ nicely, autumn is sparse, winter empty.
         */
        public Map<String, List<String>> flowerSeasons = defaultFlowerSeasons();

        public List<String> flowersFor(Season season) {
            if (flowerSeasons == null) {
                return List.of();
            }
            List<String> list = flowerSeasons.get(season.id());
            return list == null ? List.of() : list;
        }

        /**
         * Optional per-bush spread schedule, keyed by bush block id (e.g.
         * {@code minecraft:sweet_berry_bush}). Like saplings' {@code phenology}, the four
         * multipliers set <em>when</em> that bush creeps; a listed bush ignores the shared season
         * curve (the BUSH channel is left unscaled in {@code SeasonalGrowthRates} for this reason),
         * an unlisted one follows it. Bush-creep already knows which bush it copies, so the
         * schedule is per-species. See docs/research.md section 7.
         */
        public Map<String, SeasonWeights> bushSeasons = defaultBushSeasons();

        /** This bush's spread multiplier for {@code season}: its own schedule, else the shared curve. */
        public double bushSeasonWeight(String bushId, Season season, Seasons seasons) {
            SeasonWeights w = bushSeasons == null ? null : bushSeasons.get(bushId);
            return w != null ? w.weight(season) : seasons.multiplier(season);
        }

        private static Map<String, Double> defaultBiomeDensity() {
            Map<String, Double> map = new LinkedHashMap<>();
            map.put("#minecraft:is_forest", 1.5);
            map.put("#minecraft:is_jungle", 1.75);
            map.put("minecraft:plains", 1.25);
            map.put("minecraft:meadow", 1.5);
            map.put("#minecraft:is_savanna", 0.6);
            map.put("#minecraft:is_badlands", 0.3);
            map.put("minecraft:desert", 0.2);
            return map;
        }

        private static Map<String, Double> defaultBiomeGrowth() {
            Map<String, Double> map = new LinkedHashMap<>();
            map.put("#minecraft:is_jungle", 1.6);
            map.put("minecraft:mangrove_swamp", 1.5);
            map.put("minecraft:swamp", 1.4);
            map.put("#minecraft:is_forest", 1.3);
            map.put("minecraft:meadow", 1.3);
            map.put("minecraft:plains", 1.1);
            map.put("#minecraft:is_savanna", 0.6);
            map.put("#minecraft:is_taiga", 0.7);
            map.put("#minecraft:is_badlands", 0.25);
            map.put("minecraft:desert", 0.15);
            map.put("#minecraft:is_snowy", 0.5);
            return map;
        }

        private static Map<String, Double> defaultBiomeSeasonality() {
            Map<String, Double> map = new LinkedHashMap<>();
            // 0 = aseasonal (tropical, year-round) ... 1 = temperate baseline ... >1 = boreal
            // (true no-growth winter). Exact ids win; among tags the first listed wins, so the
            // harsher #is_snowy precedes #is_taiga (a snowy_taiga reads as snowy, not taiga).
            map.put("#minecraft:is_jungle", 0.2);      // tropical: grows ~year-round
            map.put("minecraft:mangrove_swamp", 0.3);  // warm coast, mild seasons
            map.put("minecraft:swamp", 0.5);
            map.put("#minecraft:is_savanna", 0.6);     // wet/dry rhythm, not a temperate winter
            map.put("minecraft:desert", 0.3);          // arid year-round (no real winter)
            map.put("#minecraft:is_badlands", 0.4);
            map.put("minecraft:grove", 1.3);           // snowy mountain: long hard winter
            map.put("#minecraft:is_snowy", 1.3);       // snowy variants: true no-growth winter
            map.put("#minecraft:is_taiga", 1.15);      // boreal: zero-growth winter, booming spring
            return map;
        }

        private static Map<String, Double> defaultBiomeSeasonPhase() {
            Map<String, Double> map = new LinkedHashMap<>();
            map.put("#minecraft:is_savanna", 3.0); // wet/growth season is summer (peak shifted -1)
            return map;
        }

        private static Map<String, List<String>> defaultFlowerSeasons() {
            Map<String, List<String>> map = new LinkedHashMap<>();
            map.put("spring", List.of("minecraft:dandelion", "minecraft:blue_orchid", "minecraft:allium",
                "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:orange_tulip",
                "minecraft:white_tulip", "minecraft:pink_tulip", "minecraft:lily_of_the_valley"));
            map.put("summer", List.of("minecraft:poppy", "minecraft:cornflower",
                "minecraft:oxeye_daisy", "minecraft:sunflower"));
            map.put("autumn", List.of("minecraft:poppy", "minecraft:dandelion"));
            map.put("winter", List.of());
            return map;
        }

        private static Map<String, SeasonWeights> defaultBushSeasons() {
            Map<String, SeasonWeights> map = new LinkedHashMap<>();
            // Each vanilla bush on its own schedule (peak 1.0). Spread = an established bush, so the
            // peak tracks when each is actually growing/fruiting; cold-biome bushes zero out winter.
            map.put("minecraft:bush", new SeasonWeights(1.0, 0.7, 0.3, 0.0));             // generic temperate shrub: spring leaf-out, dormant winter
            map.put("minecraft:firefly_bush", new SeasonWeights(0.4, 1.0, 0.5, 0.15));    // warm-season (fireflies = summer), mild winter
            map.put("minecraft:sweet_berry_bush", new SeasonWeights(0.3, 0.6, 1.0, 0.0)); // boreal berry: ripens late summer->autumn, frozen winter
            return map;
        }
    }

    /**
     * Vegetation dieback task: plants in *sustained* enclosed darkness decay. "Sustained" is
     * approximated with an in-memory evicting counter map (see DiebackTask) - no persistence,
     * by design. Light is judged by max(raw skylight, blocklight), which is time-of-day
     * independent: only genuinely roofed/enclosed spots qualify, never ordinary night.
     */
    public static final class Dieback {
        public boolean enabled = true;
        /** Chance per visit that a qualifying dark candidate accrues a decay mark. */
        public double chance = 0.15;
        /** Below this light (skylight ignoring time, or blocklight) a spot counts as dark. */
        public int lightLevel = 7;
        /** Consecutive dark marks before one decay step fires. */
        public int requiredVisits = 3;
        /** How far to probe down through roofs for an interior surface. */
        public int probeDepth = 24;
        /** Evicting-map hard cap; overflowing clears the map (delayed dieback, never memory). */
        public int maxTracked = 4096;
    }

    /** Sapling propagation: verified nearby trees seed matching saplings on valid ground. */
    public static final class Saplings {
        public boolean enabled = true;
        /**
         * Chance per sampled candidate column, before season scaling. Deliberately the lowest
         * growth rate in the mod: each sapling becomes a whole tree, so it's far higher-impact
         * than a flower or grass tuft. Forests should creep over many in-game days, not fill in.
         */
        public double chance = 0.0008;
        /**
         * How far to look for a tree's logs around a candidate. Caps the per-type radii below,
         * so it must be >= the largest of them (16 for wind/animal dispersers like birch/acacia).
         * The post-roll scan grows with this; it runs only after the (rare) chance roll passes.
         */
        public int logSearchRadius = 16;
        /** Max log-column height walked when validating a tree. */
        public int maxColumnHeight = 24;
        /**
         * Skip when this many saplings already grow within the radius. This is the canopy-density
         * lever: 3 lets stands thicken into a closing canopy as they spread (lush biomes x1.5 -> ~5,
         * arid x0.2 -> 0). Saplings that mature into trees stop counting, so the canopy keeps
         * filling. Lower it toward 1 for sparse scattered lone trees instead of a closed canopy.
         */
        public int densityRadius = 10;
        public int maxSaplingsNearby = 3;
        /**
         * Mega-only species (dark/pale oak) can't grow as a lone sapling - four must align on a flat
         * 2x2, which vanilla then grows. This is how far (Chebyshev) such a seed may be nudged from
         * its rolled spot onto the cell that best advances a nearby partial 2x2: 1 keeps the spread
         * very slow (a candidate must land within a block of a partial), higher completes clusters
         * faster, 0 turns the nudge off (those species then align only by chance). Only affects
         * {@code twoByTwo} types; see {@link dev.isaac.fallow.ecology.SaplingCluster}.
         */
        public int clusterRadius = 1;
        /**
         * Per-tree tuning, keyed by sapling id. {@code rate} is an extra probability gate
         * (0..1) on top of the global chance; {@code radius} caps how far from the parent
         * tree's logs this type seeds (effective radius = min(radius, logSearchRadius));
         * {@code phenology} optionally overrides the shared seasonal curve with this species'
         * own per-season timing (see {@link TreeType#phenology}). Unlisted types use rate 1.0,
         * the global radius, and the shared seasonal curve - modded trees work untouched.
         */
        public Map<String, TreeType> types = defaultTypes();

        public static final class TreeType {
            /** Spread prolificacy gate (0..1) on the global chance: pioneers high, climax low. */
            public double rate = 1.0;
            /** Seed-dispersal distance from the parent (capped by logSearchRadius). */
            public int radius = 8;
            /** Canopy closure: max saplings within densityRadius before this species stops seeding. */
            public int density = 3;
            /**
             * Mega-only species (dark/pale oak) that ONLY grow as a flat 2x2 - a lone sapling never
             * matures. When true, seeds are nudged to build 2x2 clusters (see {@code clusterRadius}
             * and {@link dev.isaac.fallow.ecology.SaplingCluster}); when false (the default, and all
             * species with a single-tree form) a seed grows the small tree on its own.
             */
            public boolean twoByTwo = false;
            /**
             * Optional per-species seasonal timing of spread, grounded in the species' real seed
             * phenology (see docs/research.md section 5). When {@code null} (the common case) the species
             * follows the shared seasonal growth curve in {@link Seasons}; when set, these four
             * multipliers replace it. Saplings are the one growth channel whose seasonality is
             * per-species rather than global - real trees disperse and establish at very different
             * times of year (white oak germinates in autumn, spruce sheds into winter, mangrove
             * never stops, jungle is aseasonal) - so {@link dev.isaac.fallow.growth.SeasonalGrowthRates}
             * leaves the SAPLING channel unscaled and the weighting is applied in
             * {@code SaplingSpreadTask}.
             */
            public SeasonWeights phenology = null;

            public TreeType() {
            }

            TreeType(double rate, int radius, int density) {
                this.rate = rate;
                this.radius = radius;
                this.density = density;
            }

            TreeType(double rate, int radius, int density, SeasonWeights phenology) {
                this(rate, radius, density);
                this.phenology = phenology;
            }

            TreeType(double rate, int radius, int density, boolean twoByTwo) {
                this(rate, radius, density);
                this.twoByTwo = twoByTwo;
            }

            TreeType(double rate, int radius, int density, boolean twoByTwo, SeasonWeights phenology) {
                this(rate, radius, density, twoByTwo);
                this.phenology = phenology;
            }

            /**
             * This species' spread multiplier for {@code season}: its own {@link #phenology} when
             * set, otherwise the shared seasonal growth curve. Pure logic (no Minecraft types) so
             * it is unit-tested directly.
             */
            public double seasonWeight(Season season, Seasons globalSeasons) {
                return phenology != null ? phenology.weight(season) : globalSeasons.multiplier(season);
            }
        }

        private static Map<String, TreeType> defaultTypes() {
            Map<String, TreeType> map = new LinkedHashMap<>();
            // rate, radius, density [, phenology]. Grounded in each species' real ecology (see
            // docs/research.md section 5): rate/radius/density encode dispersal *mode* (wide dispersers +
            // closed canopies up; sparse savanna / relict species down); the optional phenology
            // vector {spring, summer, autumn, winter} encodes dispersal *timing*. Most temperate
            // trees disperse in autumn but establish in spring, so they keep the shared spring-
            // peaked curve (= establishment). Only the species whose real timing breaks that
            // pattern carry an override; the peak is 1.0. Tightly-seasonal trees (oak, spruce)
            // zero their off-seasons - they simply do not spread then, the way flowers don't bloom
            // in winter - while genuinely year-round species (mangrove, jungle) stay non-zero in
            // every season because they really do disperse all year:
            map.put("minecraft:birch_sapling", new TreeType(1.0, 16, 4));      // autumn-shed, spring-establish -> shared curve
            map.put("minecraft:acacia_sapling", new TreeType(0.7, 16, 2));     // savanna; germinates at the rains ~ in-game spring -> shared curve
            map.put("minecraft:spruce_sapling", new TreeType(0.5, 14, 5,       // boreal conifer: sheds viable seed in autumn and on into winter only - the one winter-tolerant tree
                new SeasonWeights(0.0, 0.0, 1.0, 0.45)));
            map.put("minecraft:cherry_sapling", new TreeType(0.9, 13, 3));     // bird-spread drupes, cold-strat -> spring establishment -> shared curve
            map.put("minecraft:mangrove_propagule", new TreeType(0.7, 12, 5,   // viviparous tropical-coast: propagules year-round, peak late summer; never winter-dead -> all seasons non-zero
                new SeasonWeights(0.55, 1.0, 0.9, 0.5)));
            map.put("minecraft:jungle_sapling", new TreeType(1.0, 11, 6,       // everwet rainforest: aseasonal (irregular supra-annual mast) -> flat year-round, all seasons 1.0
                new SeasonWeights(1.0, 1.0, 1.0, 1.0)));
            map.put("minecraft:oak_sapling", new TreeType(0.6, 9, 4,           // white-oak group: acorns germinate in fall on dropping, no dormancy -> autumn ONLY
                new SeasonWeights(0.0, 0.0, 1.0, 0.0)));
            // 2x2-only growers: no single-tree form, so seeds build clusters (twoByTwo=true). Caps
            // raised so one finished cluster (4 saplings) doesn't self-lock the density box mid-build.
            map.put("minecraft:dark_oak_sapling", new TreeType(0.5, 8, 10, true));   // jay-cached acorns -> spring establishment -> shared curve
            map.put("minecraft:pale_oak_sapling", new TreeType(0.35, 8, 8, true));   // relict; spring establishment + masting -> shared curve
            return map;
        }
    }

    /**
     * Per-season spread multipliers for one plant species - shared by saplings' {@code phenology}
     * and bushes' {@code bushSeasons}. Each value scales that species' spread chance in one season
     * (0 = does not spread then). Pure data + a {@link #weight} lookup; unit-tested.
     */
    public static final class SeasonWeights {
        public double spring = 1.0;
        public double summer = 1.0;
        public double autumn = 1.0;
        public double winter = 1.0;

        public SeasonWeights() {
        }

        SeasonWeights(double spring, double summer, double autumn, double winter) {
            this.spring = spring;
            this.summer = summer;
            this.autumn = autumn;
            this.winter = winter;
        }

        public double weight(Season season) {
            return switch (season) {
                case SPRING -> spring;
                case SUMMER -> summer;
                case AUTUMN -> autumn;
                case WINTER -> winter;
            };
        }
    }

    /**
     * Trails: repeated footsteps wear grass into coarse dirt, then into a dirt path; unused
     * trails recover. Per-block wear counters live in one capped SavedData map per dimension.
     */
    public static final class Trails {
        public boolean enabled = true;
        /** Footsteps on a grass block before it wears to coarse dirt. */
        public int stepsToCoarse = 30;
        /** Total footsteps before coarse dirt wears to a dirt path. */
        public int stepsToPath = 80;
        /** Wear removed from every untrodden entry per decay pass. */
        public int recoveryAmount = 1;
        /** Ticks between decay passes (1200 = one minute). */
        public int decayIntervalTicks = 1200;
        /** Hard cap on tracked positions per dimension; lowest-wear entries are pruned. */
        public int maxTracked = 4096;
    }

    /**
     * Leaf litter: forest floors develop under dense natural canopy - grass blocks slowly
     * convert to podzol (mostly) or rooted dirt. Autumn/winter accelerate it (decay channel).
     */
    public static final class LeafLitter {
        public boolean enabled = true;
        /** Chance per sampled under-canopy candidate, before season scaling. */
        public double chance = 0.02;
        /** Non-persistent leaf layers required overhead to count as dense canopy. */
        public int minCanopyLayers = 2;
        /** How far up to look for canopy. */
        public int canopyScanHeight = 20;
        /** Share of triggers that mature the soil to podzol instead of scattering leaf cover. */
        public double podzolShare = 0.25;
        /**
         * Per-season "leaf fall" weight, peaking in autumn. This single curve drives BOTH the rate
         * this ground litter accumulates AND the client's falling-leaf particle rate, so the leaves
         * you see drifting down match the litter building up. Replaces the generic decay-channel
         * seasonality for litter (which peaked in winter); leaves fall in autumn, not deep winter.
         */
        public double springFall = 0.4;
        public double summerFall = 0.3;
        public double autumnFall = 1.5;
        public double winterFall = 0.5;

        public double leafFallWeight(Season season) {
            return bySeason(season, springFall, summerFall, autumnFall, winterFall);
        }
    }

    /**
     * Grass overcrowding: where short/tall grass is packed tighter than a neighbor threshold,
     * it thins out - tall grass reverts to short, short grass disappears. A decay channel, so
     * winter culls hardest (spring/summer barely touch it); this is the seasonal "die off".
     */
    public static final class Overcrowding {
        public boolean enabled = true;
        /** Base chance per crowded candidate, before season decay scaling (winter x3). */
        public double chance = 0.35;
        /**
         * Grass-family neighbors (within radius) above which a plant is "crowded" and may thin -
         * effectively the <em>grass-density target</em>: grass fills in and is thinned back toward
         * this. The effective target is scaled per biome (by {@code vegetation.biomeDensity}, so
         * lush biomes hold denser grass and arid ones thin to sparse) and per season (by the
         * factors below, so meadows are thick in summer and recede in winter), then clamped to the
         * neighborhood size. 3 of 8 cells by default. Raise toward 7 to only cull the densest mats.
         */
        public int neighborThreshold = 3;
        /** Horizontal radius for the neighbor count. */
        public int radius = 1;
        /** Per-season multipliers on the density target: thicker grass in the growing seasons. */
        public double springDensity = 1.1;
        public double summerDensity = 1.3;
        public double autumnDensity = 0.8;
        public double winterDensity = 0.4;

        public double densityFactor(Season season) {
            return bySeason(season, springDensity, summerDensity, autumnDensity, winterDensity);
        }
    }

    /**
     * Flowers bloom in spring (the spring growth boost) and wilt away through autumn and winter
     * (this decay channel: near-zero in spring, high in autumn/winter). Net effect over a year:
     * flowers appear in spring, persist through summer, fade in fall, and are gone by deep winter.
     */
    public static final class FlowerWilt {
        public boolean enabled = true;
        /**
         * Base chance per flower candidate, before season decay scaling. Tuned against the
         * flower sprout rate so spring nets positive (sprout 0.004 x 1.5 = 0.006 >= wilt
         * 0.006 x 0.75 = 0.0045) and autumn/winter net strongly negative (wilt x 1.5 / x 3.0
         * far exceeds the season-suppressed sprout) - flowers bloom in spring, fade by winter.
         */
        public double chance = 0.006;
    }

    /** Shoreline creep: sugar cane on waterline-adjacent ground, seagrass on shallow floors. */
    public static final class Shoreline {
        public boolean enabled = true;
        public double sugarCaneChance = 0.004;
        public double seagrassChance = 0.01;
        /** Max water depth (blocks) for seagrass. */
        public int maxSeagrassDepth = 4;
        /** Skip when this many of the same plant grow nearby (box extent: +/-r horizontal, +/-2 vertical). */
        public int maxCaneNearby = 4;
        public int maxSeagrassNearby = 8;
    }

    /**
     * Bamboo spreads clonally (rhizome runners): open, plantable ground next to an existing
     * bamboo stand occasionally sends up a new shoot, so groves creep outward and thicken to a
     * cap. Tropical - routed through the normal seasonal scaling, so jungle's low
     * {@code biomeSeasonality} keeps it near-aseasonal while a temperate planting would slow in
     * winter. {@code searchRadius} is how far to look for a parent stand; {@code maxNearby} caps
     * how many bamboo stalks may stand within that radius before a grove stops thickening.
     */
    public static final class Bamboo {
        public boolean enabled = true;
        public double chance = 0.004;
        public int searchRadius = 4;
        public int maxNearby = 12;
    }

    /**
     * Client-side seasonal foliage/grass tinting (needs Fallow on the client; pure visuals,
     * zero gameplay effect; vanilla clients simply see normal colors). Strength scales the
     * per-season tints in {@code SeasonTint}; transitions blend day by day when smooth.
     */
    public static final class Visuals {
        public boolean enabled = true;
        public double strength = 1.0;
        public boolean smoothTransitions = true;
    }

    /** Season cycle + per-season growth/decay multipliers (applied by SeasonalGrowthRates). */
    public static final class Seasons {
        public boolean enabled = true;
        public int daysPerSeason = 10;
        public double springMultiplier = 1.5;
        public double summerMultiplier = 1.0;
        public double autumnMultiplier = 0.5;
        public double winterMultiplier = 0.05;
        /** Decay channels scale the other way: winter is the season of dieback. */
        public double springDecayMultiplier = 0.75;
        public double summerDecayMultiplier = 0.5;
        public double autumnDecayMultiplier = 1.5;
        public double winterDecayMultiplier = 3.0;

        public double multiplier(Season season) {
            return bySeason(season, springMultiplier, summerMultiplier, autumnMultiplier, winterMultiplier);
        }

        public double decayMultiplier(Season season) {
            return bySeason(season,
                springDecayMultiplier, summerDecayMultiplier, autumnDecayMultiplier, winterDecayMultiplier);
        }
    }

    /**
     * Seasonal day/night split. The 24000-tick cycle length never changes; the portion of it
     * spent in daylight does, via the vanilla 26.1 world-clock rate (no mixins). 0.5 everywhere
     * is bit-identical to vanilla. {@code enabled=false} is the kill switch: Fallow stops
     * touching the clock rate entirely (after stepping it back to 1.0 if it had set one).
     */
    public static final class DayNight {
        public boolean enabled = true;
        public double springDayPortion = 0.50;
        public double summerDayPortion = 0.625;
        public double autumnDayPortion = 0.50;
        public double winterDayPortion = 0.375;

        public double dayPortion(Season season) {
            return bySeason(season, springDayPortion, summerDayPortion, autumnDayPortion, winterDayPortion);
        }
    }

    /**
     * Seasonal precipitation. The {@code *TempOffset} values are added to every biome's effective
     * temperature each season (via {@code mixin.BiomeTemperatureMixin}); vanilla derives
     * rain-vs-snow, snow accumulation, and water freeze/thaw from that one number, so a single
     * offset makes all of them seasonal. Base biome temperature still differentiates - a winter
     * offset that snows the plains leaves the warm jungle raining and never makes a
     * no-precipitation desert snow. {@code biomePrecip} (Layer 1, applied once at world load via
     * Fabric biome modification) forces a biome's has-precipitation flag, so e.g. savanna can have
     * a (warm) rainy season; changing it needs a restart, not {@code /fallow reload}.
     */
    public static final class Precipitation {
        public boolean enabled = true;
        public double springTempOffset = 0.0;
        public double summerTempOffset = 0.2;
        public double autumnTempOffset = -0.1;
        public double winterTempOffset = -0.7;
        public Map<String, Boolean> biomePrecip = defaultBiomePrecip();

        /** Per-visit chance to add a snow layer where it's snow-season and sky-exposed. */
        public double snowAccumulateChance = 0.1;
        /**
         * Per-visit melt chance <em>at the reference warmth</em> ({@link #meltReferenceWarmth}).
         * The actual chance scales with temperature: {@code snowMeltChance * warmth /
         * meltReferenceWarmth}, where {@code warmth} is how far the (seasonal, per-biome) temperature
         * sits above the 0.15 thaw point. So snow barely melts just above freezing and clears fast
         * in high summer or a hot biome. Set {@code meltReferenceWarmth} high to mute the scaling.
         */
        public double snowMeltChance = 0.3;
        /**
         * Temperature above the 0.15 freeze/thaw point at which {@link #snowMeltChance} is reached.
         * Lower = melt ramps up sooner (snow clears faster across the board); higher = melt stays
         * gentle until it's genuinely hot. Melt chance is clamped to 1.0, so very hot biomes melt
         * every visit regardless.
         */
        public double meltReferenceWarmth = 0.5;
        /** Whether the warm-season thaw also melts ice back to water (frozen ponds reopen). */
        public boolean thawIce = true;
        /**
         * Bias how often it rains by season (global - Minecraft has one weather timeline). >1 =
         * wetter (longer rain, shorter clear), <1 = drier. Applied at weather transitions.
         */
        public boolean seasonalWeather = true;
        public double springRainfall = 1.3;
        public double summerRainfall = 1.5;
        public double autumnRainfall = 1.0;
        public double winterRainfall = 0.6;

        public double rainfall(Season season) {
            return bySeason(season, springRainfall, summerRainfall, autumnRainfall, winterRainfall);
        }
        /**
         * Per-biome maximum snow depth (layers, 1-8), same key format as {@code biomePrecip}.
         * This is the per-biome precipitation <em>intensity</em>: snowy/boreal biomes pile deep
         * drifts, temperate biomes get a thin dusting. Unlisted biomes use 1. Vanilla still places
         * the first layer during snowfall; this task builds it up to the per-biome target through
         * the cold season and melts it back when the season turns.
         */
        public Map<String, Double> snowDepth = defaultSnowDepth();

        public double tempOffset(Season season) {
            return bySeason(season, springTempOffset, summerTempOffset, autumnTempOffset, winterTempOffset);
        }

        private static Map<String, Boolean> defaultBiomePrecip() {
            Map<String, Boolean> map = new LinkedHashMap<>();
            map.put("#minecraft:is_savanna", true); // savanna gains a warm rainy season
            return map;
        }

        private static Map<String, Double> defaultSnowDepth() {
            Map<String, Double> map = new LinkedHashMap<>();
            map.put("#minecraft:is_snowy", 6.0);  // deep drifts
            map.put("minecraft:grove", 5.0);      // snowy mountain
            map.put("#minecraft:is_taiga", 3.0);  // boreal: moderate
            return map;                            // unlisted (temperate) = 1.0 dusting
        }
    }

    /**
     * Occasional punctuated weather events, rolled once per in-game day and biased by season:
     * blizzards (winter - heavy snow accumulation), heatwaves (summer - clear, hot, growth
     * stalls and snow melts), and storms (spring/autumn - rain + thunder). Each forces its
     * weather for a random duration and applies transient modifiers read by the tasks/providers.
     */
    public static final class Events {
        public boolean enabled = true;
        /** Chance each in-game day to start a season-appropriate event. */
        public double chancePerDay = 0.15;
        public int minDurationTicks = 2400;  // 2 min
        public int maxDurationTicks = 6000;  // 5 min
        /** Blizzard: snow-accumulation multiplier while active. */
        public double blizzardSnowMultiplier = 3.0;
        /** Heatwave: growth multiplier (suppresses) and a temperature bonus (dries/melts). */
        public double heatwaveGrowthMultiplier = 0.5;
        public double heatwaveTempBonus = 0.4;
    }

    /**
     * Seasonal fruiting: in its season, a natural tree occasionally drops a fruit item on the
     * ground beneath its canopy. Keyed by leaf block id (non-persistent leaves only, so player
     * leaf builds never fruit). Defaults: oak/dark-oak drop apples in autumn (vanilla's only tree
     * fruit); the map is open for modded fruit.
     */
    public static final class Fruiting {
        public boolean enabled = true;
        /** How far up to look for a fruiting canopy over a candidate spot. */
        public int scanHeight = 12;
        public Map<String, FruitType> types = defaultTypes();

        public static final class FruitType {
            public String item = "minecraft:apple";
            public String season = "autumn";
            public double chance = 0.01;

            public FruitType() {
            }

            FruitType(String item, String season, double chance) {
                this.item = item;
                this.season = season;
                this.chance = chance;
            }
        }

        private static Map<String, FruitType> defaultTypes() {
            Map<String, FruitType> map = new LinkedHashMap<>();
            map.put("minecraft:oak_leaves", new FruitType("minecraft:apple", "autumn", 0.01));
            map.put("minecraft:dark_oak_leaves", new FruitType("minecraft:apple", "autumn", 0.01));
            // Cherry leaves drop fallow:cherries in spring - only active when the crop layer is on.
            map.put("minecraft:cherry_leaves", new FruitType("fallow:cherries", "spring", 0.01));
            // Flowering azalea leaves drop fallow:plum in autumn - crop layer only.
            map.put("minecraft:flowering_azalea_leaves", new FruitType("fallow:plum", "autumn", 0.008));
            return map;
        }
    }

    /**
     * Phase C1 crop layer: real fallow:* blocks placed in the world. Ships disabled because
     * planted blocks persist across uninstall; see {@link #removalNote}.
     */
    public static final class Crops {
        /**
         * Crops place real fallow:* blocks in the world; unlike the rest of Fallow they do not
         * uninstall cleanly once planted.
         */
        public String removalNote = "Crops place real fallow:* blocks in the world; unlike the "
            + "rest of Fallow they do not uninstall cleanly once planted.";

        /** Master switch for the crop layer. Off by default (same reason as Fallow.enabled). */
        public boolean enabled = false;
        /** When true, per-crop season weights gate random-tick growth. */
        public boolean seasonGating = true;
        /** When true, crops in WINTER with weight &lt;=0 are replaced by a dead husk. */
        public boolean winterKill = true;
        /** Chance per short/tall grass break to drop one of the crop seeds. */
        public double seedDropChance = 0.05;

        /**
         * Per-crop seasonal spread weights, keyed by block id. The four values are
         * {spring, summer, autumn, winter}. Missing entries default to 1.0 in all seasons
         * (grow normally). See {@link #cropSeasonWeight}.
         */
        public Map<String, SeasonWeights> cropSeasons = defaultCropSeasons();

        /**
         * This crop's growth multiplier for {@code season}: its own schedule when configured,
         * 1.0 when absent. Pure logic; unit-testable.
         */
        public double cropSeasonWeight(String blockId, dev.isaac.fallow.api.Season season) {
            SeasonWeights w = cropSeasons == null ? null : cropSeasons.get(blockId);
            return w != null ? w.weight(season) : 1.0;
        }

        private static Map<String, SeasonWeights> defaultCropSeasons() {
            Map<String, SeasonWeights> map = new LinkedHashMap<>();
            map.put("fallow:turnip_crop",     new SeasonWeights(0.6, 0.8, 1.0, 0.25));
            map.put("fallow:cabbage_crop",    new SeasonWeights(1.0, 0.3, 1.0, 0.0));
            map.put("fallow:onion_crop",      new SeasonWeights(1.0, 0.7, 1.0, 0.0));
            map.put("fallow:strawberry_bush", new SeasonWeights(1.0, 0.5, 0.2, 0.0));
            map.put("fallow:pea_crop",        new SeasonWeights(1.0, 0.6, 0.3, 0.0));
            // Phase C2/C3 crops.
            map.put("fallow:leek_crop",       new SeasonWeights(0.8, 0.6, 1.0, 0.35));
            map.put("fallow:barley_crop",     new SeasonWeights(1.0, 0.7, 0.9, 0.15));
            map.put("fallow:rye_crop",        new SeasonWeights(0.9, 0.5, 1.0, 0.3));
            map.put("fallow:oat_crop",        new SeasonWeights(1.0, 0.7, 0.8, 0.0));
            map.put("fallow:garlic_crop",     new SeasonWeights(0.9, 0.4, 1.0, 0.2));
            map.put("fallow:radish_crop",     new SeasonWeights(1.0, 0.6, 1.0, 0.0));
            map.put("fallow:parsnip_crop",    new SeasonWeights(0.8, 0.6, 1.0, 0.25));
            map.put("fallow:pepper_crop",     new SeasonWeights(0.6, 1.0, 0.7, 0.0));
            map.put("fallow:flax_crop",       new SeasonWeights(1.0, 0.8, 0.5, 0.0));
            map.put("fallow:tomato_crop",     new SeasonWeights(0.7, 1.0, 0.6, 0.0));
            map.put("fallow:rice_crop",       new SeasonWeights(0.5, 1.0, 0.8, 0.0));
            map.put("fallow:corn_crop",       new SeasonWeights(0.7, 1.0, 0.8, 0.0));
            map.put("fallow:cucumber_crop",   new SeasonWeights(0.8, 1.0, 0.6, 0.0));
            map.put("fallow:grape_crop",      new SeasonWeights(0.8, 1.0, 0.9, 0.0));
            map.put("fallow:hops_crop",       new SeasonWeights(0.9, 1.0, 0.8, 0.0));
            map.put("fallow:raspberry_bush",  new SeasonWeights(1.0, 1.0, 0.4, 0.0));
            map.put("fallow:blackberry_bush", new SeasonWeights(0.9, 1.0, 0.5, 0.0));
            map.put("fallow:squash_stem",     new SeasonWeights(0.8, 1.0, 1.0, 0.0));
            return map;
        }

        /** Wild forage plants placed by the forage spread task. */
        public Wild wild = new Wild();

        /** Legume nitrogen-fixing behaviour. */
        public Legumes legumes = new Legumes();

        /** Rice paddy behaviour: rice needs nearby water to grow. */
        public Paddy paddy = new Paddy();

        public static final class Wild {
            public boolean enabled = true;
            /** Chance per sampled candidate column that a wild plant spreads. */
            public double forageChance = 0.004;
            /**
             * Biome homes for each wild plant: list of biome ids or biome tags ("#tag") where
             * the plant may appear. Absent entries are never placed.
             */
            public Map<String, List<String>> homes = defaultHomes();

            private static Map<String, List<String>> defaultHomes() {
                Map<String, List<String>> map = new LinkedHashMap<>();
                map.put("fallow:wild_onion",
                    List.of("#minecraft:is_forest"));
                map.put("fallow:strawberry_bush",
                    List.of("minecraft:meadow", "minecraft:plains", "#minecraft:is_forest"));
                // Phase C3 wild forage plants.
                map.put("fallow:wild_rice",
                    List.of("minecraft:swamp", "minecraft:mangrove_swamp", "#minecraft:is_river"));
                map.put("fallow:wild_grape_vine",
                    List.of("#minecraft:is_savanna", "minecraft:meadow", "minecraft:sunflower_plains"));
                map.put("fallow:wild_hops",
                    List.of("#minecraft:is_forest", "minecraft:river"));
                map.put("fallow:chanterelle",
                    List.of("#minecraft:is_forest", "#minecraft:is_taiga"));
                map.put("fallow:mint",
                    List.of("minecraft:river", "minecraft:meadow", "minecraft:swamp"));
                map.put("fallow:sage",
                    List.of("#minecraft:is_savanna", "minecraft:plains"));
                map.put("fallow:thyme",
                    List.of("minecraft:plains", "#minecraft:is_savanna"));
                map.put("fallow:ramsons",
                    List.of("#minecraft:is_forest"));
                map.put("fallow:sorrel",
                    List.of("minecraft:plains", "minecraft:meadow", "#minecraft:is_forest"));
                return map;
            }
        }

        public static final class Legumes {
            /** When true, pea crops convert one coarse/rooted dirt block to dirt on harvest. */
            public boolean fixNitrogen = true;
            /**
             * Horizontal radius of the nitrogen-fix scan around the pea's ground block
             * (y-1..0). Clamped 0-4.
             */
            public int fixRadius = 1;
        }

        public static final class Paddy {
            /**
             * Horizontal radius (blocks) within which rice must find water to grow. The scan
             * checks the farmland's own Y and one below. Clamped 0-8.
             */
            public int range = 4;
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("fallow.json");
    }

    public static FallowConfig load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                FallowConfig loaded = GSON.fromJson(reader, FallowConfig.class);
                if (loaded != null) {
                    loaded.clamp();
                    return loaded;
                }
                backupBroken(path); // Gson returns null for an empty/truncated file
            } catch (Exception e) {
                Fallow.LOGGER.warn("Failed to read config, using defaults", e);
                backupBroken(path);
            }
        }
        FallowConfig fresh = new FallowConfig();
        fresh.save(path);
        return fresh;
    }

    /**
     * A hand-tuned config with one JSON typo must not be destroyed by the defaults rewrite below:
     * keep the unreadable file next to the fresh one so the user's per-biome maps and tree tables
     * survive the mistake.
     */
    private static void backupBroken(Path path) {
        Path backup = path.resolveSibling(path.getFileName() + ".broken");
        try {
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
            Fallow.LOGGER.warn("Kept the unreadable config at {}; writing defaults to {}", backup, path);
        } catch (IOException e) {
            Fallow.LOGGER.warn("Could not back up the unreadable config", e);
        }
    }

    /** Persist to the standard config path (used by the in-game config screen). */
    public void save() {
        save(configPath());
    }

    public void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            Fallow.LOGGER.warn("Failed to write config", e);
        }
    }

    /** Keep hand-edited values inside safe ranges; called after every load. */
    public void clamp() {
        scheduler.chunksPerTick = clampInt(scheduler.chunksPerTick, 0, 256);
        scheduler.samplesPerChunk = clampInt(scheduler.samplesPerChunk, 0, 64);
        scheduler.tickBudgetMicros = clampInt(scheduler.tickBudgetMicros, 10, 10_000);
        vegetation.shortGrassChance = clampChance(vegetation.shortGrassChance);
        vegetation.tallGrassChance = clampChance(vegetation.tallGrassChance);
        vegetation.flowerChance = clampChance(vegetation.flowerChance);
        vegetation.bushChance = clampChance(vegetation.bushChance);
        vegetation.minLightLevel = clampInt(vegetation.minLightLevel, 0, 15);
        vegetation.densityRadius = clampInt(vegetation.densityRadius, 1, 16);
        vegetation.densityMaxPlants = clampInt(vegetation.densityMaxPlants, 0, 256);
        vegetation.bushSearchRadius = clampInt(vegetation.bushSearchRadius, 1, 12);
        dieback.chance = clampChance(dieback.chance);
        dieback.lightLevel = clampInt(dieback.lightLevel, 0, 15);
        dieback.requiredVisits = clampInt(dieback.requiredVisits, 1, 100);
        dieback.probeDepth = clampInt(dieback.probeDepth, 0, 64);
        dieback.maxTracked = clampInt(dieback.maxTracked, 64, 1 << 20);
        saplings.chance = clampChance(saplings.chance);
        saplings.logSearchRadius = clampInt(saplings.logSearchRadius, 1, 16);
        saplings.maxColumnHeight = clampInt(saplings.maxColumnHeight, 2, 64);
        saplings.densityRadius = clampInt(saplings.densityRadius, 1, 16);
        saplings.maxSaplingsNearby = clampInt(saplings.maxSaplingsNearby, 0, 64);
        saplings.clusterRadius = clampInt(saplings.clusterRadius, 0, 8);
        leafLitter.chance = clampChance(leafLitter.chance);
        leafLitter.podzolShare = clampChance(leafLitter.podzolShare);
        leafLitter.minCanopyLayers = clampInt(leafLitter.minCanopyLayers, 1, 16);
        leafLitter.canopyScanHeight = clampInt(leafLitter.canopyScanHeight, 4, 64);
        leafLitter.springFall = clampMult(leafLitter.springFall);
        leafLitter.summerFall = clampMult(leafLitter.summerFall);
        leafLitter.autumnFall = clampMult(leafLitter.autumnFall);
        leafLitter.winterFall = clampMult(leafLitter.winterFall);
        shoreline.sugarCaneChance = clampChance(shoreline.sugarCaneChance);
        shoreline.seagrassChance = clampChance(shoreline.seagrassChance);
        shoreline.maxSeagrassDepth = clampInt(shoreline.maxSeagrassDepth, 1, 16);
        shoreline.maxCaneNearby = clampInt(shoreline.maxCaneNearby, 0, 64);
        shoreline.maxSeagrassNearby = clampInt(shoreline.maxSeagrassNearby, 0, 64);
        bamboo.chance = clampChance(bamboo.chance);
        bamboo.searchRadius = clampInt(bamboo.searchRadius, 1, 16);
        bamboo.maxNearby = clampInt(bamboo.maxNearby, 1, 256);
        visuals.strength = clampChance(visuals.strength);
        if (vegetation.biomeDensity != null) {
            vegetation.biomeDensity.replaceAll((k, v) -> Math.max(0.0, Math.min(10.0, v)));
        }
        if (vegetation.biomeGrowth != null) {
            vegetation.biomeGrowth.replaceAll((k, v) -> Math.max(0.0, Math.min(10.0, v)));
        }
        if (vegetation.biomeSeasonality != null) {
            vegetation.biomeSeasonality.replaceAll((k, v) -> Math.max(0.0, Math.min(3.0, v)));
        }
        if (vegetation.biomeSeasonPhase != null) {
            vegetation.biomeSeasonPhase.replaceAll((k, v) -> (double) Math.max(0, Math.min(3, (int) Math.round(v))));
        }
        if (vegetation.bushSeasons != null) {
            vegetation.bushSeasons.values().forEach(FallowConfig::clampWeights);
        }
        overcrowding.chance = clampChance(overcrowding.chance);
        overcrowding.radius = clampInt(overcrowding.radius, 1, 4);
        int maxNeighbors = (2 * overcrowding.radius + 1) * (2 * overcrowding.radius + 1) - 1;
        overcrowding.neighborThreshold = clampInt(overcrowding.neighborThreshold, 0, maxNeighbors);
        overcrowding.springDensity = clampMult(overcrowding.springDensity);
        overcrowding.summerDensity = clampMult(overcrowding.summerDensity);
        overcrowding.autumnDensity = clampMult(overcrowding.autumnDensity);
        overcrowding.winterDensity = clampMult(overcrowding.winterDensity);
        flowerWilt.chance = clampChance(flowerWilt.chance);
        if (saplings.types != null) {
            for (Saplings.TreeType t : saplings.types.values()) {
                t.rate = clampChance(t.rate);
                t.radius = clampInt(t.radius, 1, saplings.logSearchRadius);
                t.density = clampInt(t.density, 0, 256);
                clampWeights(t.phenology);
            }
        }
        trails.stepsToCoarse = clampInt(trails.stepsToCoarse, 1, 10_000);
        trails.stepsToPath = clampInt(trails.stepsToPath, trails.stepsToCoarse + 1, 20_000);
        trails.recoveryAmount = clampInt(trails.recoveryAmount, 0, 1000);
        trails.decayIntervalTicks = clampInt(trails.decayIntervalTicks, 20, 24_000 * 7);
        trails.maxTracked = clampInt(trails.maxTracked, 64, 1 << 20);
        seasons.daysPerSeason = clampInt(seasons.daysPerSeason, 1, 1000);
        seasons.springMultiplier = clampMult(seasons.springMultiplier);
        seasons.summerMultiplier = clampMult(seasons.summerMultiplier);
        seasons.autumnMultiplier = clampMult(seasons.autumnMultiplier);
        seasons.winterMultiplier = clampMult(seasons.winterMultiplier);
        seasons.springDecayMultiplier = clampMult(seasons.springDecayMultiplier);
        seasons.summerDecayMultiplier = clampMult(seasons.summerDecayMultiplier);
        seasons.autumnDecayMultiplier = clampMult(seasons.autumnDecayMultiplier);
        seasons.winterDecayMultiplier = clampMult(seasons.winterDecayMultiplier);
        dayNight.springDayPortion = clampPortion(dayNight.springDayPortion);
        dayNight.summerDayPortion = clampPortion(dayNight.summerDayPortion);
        dayNight.autumnDayPortion = clampPortion(dayNight.autumnDayPortion);
        dayNight.winterDayPortion = clampPortion(dayNight.winterDayPortion);
        precipitation.springTempOffset = clampTempOffset(precipitation.springTempOffset);
        precipitation.summerTempOffset = clampTempOffset(precipitation.summerTempOffset);
        precipitation.autumnTempOffset = clampTempOffset(precipitation.autumnTempOffset);
        precipitation.winterTempOffset = clampTempOffset(precipitation.winterTempOffset);
        precipitation.snowAccumulateChance = clampChance(precipitation.snowAccumulateChance);
        precipitation.snowMeltChance = clampChance(precipitation.snowMeltChance);
        precipitation.meltReferenceWarmth = Math.max(0.05, Math.min(4.0, precipitation.meltReferenceWarmth));
        precipitation.springRainfall = clampMult(precipitation.springRainfall);
        precipitation.summerRainfall = clampMult(precipitation.summerRainfall);
        precipitation.autumnRainfall = clampMult(precipitation.autumnRainfall);
        precipitation.winterRainfall = clampMult(precipitation.winterRainfall);
        events.chancePerDay = clampChance(events.chancePerDay);
        events.minDurationTicks = clampInt(events.minDurationTicks, 20, 24_000);
        events.maxDurationTicks = clampInt(events.maxDurationTicks, events.minDurationTicks, 24_000 * 7);
        events.blizzardSnowMultiplier = clampMult(events.blizzardSnowMultiplier);
        events.heatwaveGrowthMultiplier = clampMult(events.heatwaveGrowthMultiplier);
        events.heatwaveTempBonus = clampTempOffset(events.heatwaveTempBonus);
        fruiting.scanHeight = clampInt(fruiting.scanHeight, 1, 32);
        if (fruiting.types != null) {
            fruiting.types.forEach((leaf, t) -> {
                t.chance = clampChance(t.chance);
                // A typo'd season would silently skip the gate (byId -> null reads as "any
                // season"); make that explicit and say so once, at load.
                if (t.season != null && Season.byId(t.season) == null) {
                    Fallow.LOGGER.warn("fruiting.types[{}].season \"{}\" is not a season "
                        + "(spring/summer/autumn/winter); treating it as every season", leaf, t.season);
                    t.season = null;
                }
            });
        }
        if (precipitation.snowDepth != null) {
            precipitation.snowDepth.replaceAll((k, v) -> Math.max(1.0, Math.min(8.0, v)));
        }
        crops.seedDropChance = clampChance(crops.seedDropChance);
        crops.legumes.fixRadius = clampInt(crops.legumes.fixRadius, 0, 4);
        crops.paddy.range = clampInt(crops.paddy.range, 0, 8);
        crops.wild.forageChance = clampChance(crops.wild.forageChance);
        if (crops.cropSeasons != null) {
            crops.cropSeasons.values().forEach(FallowConfig::clampWeights);
        }
    }

    /**
     * Pick the value for {@code season} from four flat per-season fields. Sections keep the four
     * fields flat (not a nested {@link SeasonWeights}) so existing hand-tuned JSON keeps working;
     * this is the one switch over them, so a season can't be miswired in any single section.
     */
    static double bySeason(Season season, double spring, double summer, double autumn, double winter) {
        return switch (season) {
            case SPRING -> spring;
            case SUMMER -> summer;
            case AUTUMN -> autumn;
            case WINTER -> winter;
        };
    }

    /** Seasonal temperature offsets stay within a sane band (+/-2.0 spans any biome's class). */
    private static double clampTempOffset(double v) {
        return Math.max(-2.0, Math.min(2.0, v));
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double clampChance(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double clampMult(double v) {
        return Math.max(0.0, Math.min(10.0, v));
    }

    /** Clamp a per-species seasonal weight vector (saplings' phenology, bushes' schedule). */
    private static void clampWeights(SeasonWeights w) {
        if (w == null) {
            return;
        }
        w.spring = clampMult(w.spring);
        w.summer = clampMult(w.summer);
        w.autumn = clampMult(w.autumn);
        w.winter = clampMult(w.winter);
    }

    /** Day portions outside [0.05, 0.95] would mean a >40x clock rate; keep it sane. */
    private static double clampPortion(double v) {
        return Math.max(0.05, Math.min(0.95, v));
    }
}
