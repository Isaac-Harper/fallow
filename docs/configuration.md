# Fallow - configuration reference

Every option in `config/fallow.json`, with its default and valid range. The file is written
with defaults on first launch. Edit it by hand, through the **Mod Menu** screen (the master
switch + per-feature on/off toggles; numeric values are file-only), or on a live server with
`/fallow reload`. Values are **clamped on
load** to the ranges below - out-of-range hand edits are silently corrected, never rejected.

- **Server-side.** The simulation runs on the server; the config lives with the server/world.
  In singleplayer the Mod Menu screen edits take effect immediately; on a dedicated server,
  edit the file and run `/fallow reload`.
- **Probabilities** are per *sampled candidate* (the scheduler samples a bounded number of
  blocks per tick - see `scheduler`), so the effective real-time rate also depends on how
  often a spot is visited. They are further scaled by season and (for growth) biome.
- **"chance" fields** clamp to `[0.0, 1.0]`; **season multipliers** to `[0.0, 10.0]`;
  **day portions** to `[0.05, 0.95]`.

Top-level fields: `enabled` (the master switch, below), then the objects `scheduler`,
`vegetation`, `dieback`, `saplings`, `trails`, `leafLitter`, `overcrowding`, `flowerWilt`,
`shoreline`, `bamboo`, `seasons`, `dayNight`, `visuals`, `precipitation`, `events`, `fruiting`.

---

## enabled - master switch

| field | default | range | meaning |
|---|---|---|---|
| `enabled` | `false` | bool | Master on/off. Fallow modifies blocks over time (effectively destructive), so it ships **off**: while `false` nothing touches the world, the day length, or temperature, regardless of the per-system toggles below. Set `true` and `/fallow reload` to turn the mod on. Players get a one-time in-game notice on first join either way. |

---

## scheduler - the tick-budgeted ecology engine

Drives every *visit-based* ecology task (all of them except trails, which is event-based).

| field | default | range | meaning |
|---|---|---|---|
| `enabled` | `true` | bool | Master switch for all visit-based ecology tasks. |
| `chunksPerTick` | `8` | 0-256 | Loaded ticking chunks visited per world tick (round-robin). |
| `samplesPerChunk` | `4` | 0-64 | Random surface columns each task samples per visited chunk. |
| `tickBudgetMicros` | `200` | 10-10000 | Wall-clock budget per world tick (us); the loop stops early past this (checked between chunk visits). |
| `logTimings` | `false` | bool | Log scheduler timings every ~30s (same data as `/fallow stats`). |
| `dimensions` | `["minecraft:overworld"]` | list of ids | Dimensions the scheduler (and trails) run in. |

## vegetation - sprouting (grass, flowers, bushes)

Grass blocks with air above sprout short grass; short grass upgrades to tall grass; flowers
come from the biome's bonemeal feature list (datapack-correct); bushes copy from one growing
nearby. All placements pass vanilla `canSurvive` + light checks.

| field | default | range | meaning |
|---|---|---|---|
| `enabled` | `true` | bool | Toggle the sprouting task. |
| `shortGrassChance` | `0.02` | 0-1 | Bare grass block -> short grass (highest of the four by design). |
| `tallGrassChance` | `0.008` | 0-1 | Existing short grass -> tall grass. |
| `flowerChance` | `0.004` | 0-1 | Bare grass block -> biome flower. |
| `bushChance` | `0.002` | 0-1 | Bare grass block -> bush copied from one within `bushSearchRadius`. |
| `minLightLevel` | `9` | 0-15 | Minimum light to sprout (vanilla grass-spread threshold; growth pauses at night). |
| `densityRadius` | `8` | 1-16 | Box radius for the flower/bush density guard. |
| `densityMaxPlants` | `10` | 0-256 | Skip flower/bush when this many already grow within `densityRadius` (xbiome density). |
| `bushSearchRadius` | `6` | 1-12 | How far to look for an existing bush to copy. |
| `biomeDensity` | see below | id/tag -> 0-10 | Per-biome multiplier on density caps (flowers/bushes **and** saplings). |
| `biomeGrowth` | see below | id/tag -> 0-10 | Per-biome multiplier on growth-channel *rates* (grass/flowers/bushes/saplings/cane/seagrass). |
| `biomeSeasonality` | see below | id/tag -> 0-3 | Per-biome season-amplitude scalar `k` (0 = aseasonal, 1 = temperate, >1 = boreal no-growth winter). **Also scales the seasonal temperature swing**, so it decides which biomes snow: low `k` (tropics, mangrove, swamp) never snow; `k>=1` snows in winter. |
| `bushSeasons` | see below | bush id -> 0-10 x4 | Per-bush spread schedule `{spring, summer, autumn, winter}` (when each bush creeps); unlisted -> shared curve. |

**`biomeDensity` defaults** (exact biome id beats `#tag`; first matching tag in file order
wins; unlisted = 1.0): `#minecraft:is_forest` 1.5, `#minecraft:is_jungle` 1.75,
`minecraft:plains` 1.25, `minecraft:meadow` 1.5, `#minecraft:is_savanna` 0.6,
`#minecraft:is_badlands` 0.3, `minecraft:desert` 0.2.

**`biomeGrowth` defaults**: `#minecraft:is_jungle` 1.6, `minecraft:mangrove_swamp` 1.5,
`minecraft:swamp` 1.4, `#minecraft:is_forest` 1.3, `minecraft:meadow` 1.3,
`minecraft:plains` 1.1, `#minecraft:is_savanna` 0.6, `#minecraft:is_taiga` 0.7,
`#minecraft:is_badlands` 0.25, `minecraft:desert` 0.15, `#minecraft:is_snowy` 0.5.
(The flat `biomeGrowth` is **not** applied to decay channels - winter dieback shouldn't depend
on fertility.)

**`biomeSeasonality` defaults** (unlisted = 1.0 = temperate baseline): `#minecraft:is_jungle`
0.2, `minecraft:mangrove_swamp` 0.3, `minecraft:swamp` 0.5, `#minecraft:is_savanna` 0.6,
`minecraft:desert` 0.3, `#minecraft:is_badlands` 0.4, `minecraft:grove` 1.3,
`#minecraft:is_snowy` 1.3, `#minecraft:is_taiga` 1.15. The scalar warps the shared season curve
as `1 + k*(curve - 1)` (clamped >= 0), applied to growth **and** decay: `k=0` flattens to
year-round growth (tropical never freezes), `k=1` is the curve as-is, `k>1` drives winter growth
to **zero** and amplifies the spring boom (boreal). Saplings ignore it - they use per-species
`phenology` (under `saplings`). Resulting winter growth: jungle ~ 0.81 (year-round), temperate
0.05, taiga/snowy 0 (no growth). The `is_snowy` rule precedes `is_taiga` so a snowy_taiga reads
as snowy.

**`bushSeasons` defaults** (keyed by bush block id; same `{spring, summer, autumn, winter}`
vector as saplings' `phenology`; unlisted bushes -> shared curve): `minecraft:bush`
1.0/0.7/0.3/0.0 (generic temperate shrub - spring leaf-out, dormant winter),
`minecraft:firefly_bush` 0.4/1.0/0.5/0.15 (warm season - fireflies = summer),
`minecraft:sweet_berry_bush` 0.3/0.6/1.0/0.0 (boreal berry - ripens late-summer->autumn, frozen
winter). Bush-creep copies a specific nearby bush, so it knows the species; the BUSH channel is
left unscaled by season *and* biome-seasonality (like SAPLING) so the schedule isn't
double-counted. Flowers stay grouped on the biome-modulated shared curve (the mod doesn't pick
the flower species - the biome's bonemeal palette does).

## dieback - decay in sustained darkness *(decay channel)*

Vegetation kept in genuinely enclosed darkness (roofed/buried - judged by max of raw skylight
and blocklight, so ordinary night never counts) decays: tall grass -> short -> gone, bare grass
block -> dirt. "Sustained" is approximated by an in-memory evicting counter (not persisted; a
restart just delays dieback).

| field | default | range | meaning |
|---|---|---|---|
| `enabled` | `true` | bool | Toggle dieback. |
| `chance` | `0.15` | 0-1 | Chance a dark candidate accrues a decay mark per visit. |
| `lightLevel` | `7` | 0-15 | At/under this light a spot counts as dark. |
| `requiredVisits` | `3` | 1-100 | Consecutive dark marks before one decay step fires (any bright visit evicts the entry). |
| `probeDepth` | `24` | 0-64 | How far to probe down through roofs for an interior surface. |
| `maxTracked` | `4096` | 64-1048576 | Counter-map cap; overflow clears the map (delayed dieback, never unbounded memory). |

## saplings - tree propagation *(growth channel)*

Verified natural trees (a log column rooted in dirt-family ground **with non-persistent
leaves** above - player log builds never qualify) seed matching saplings nearby. Log type ->
sapling by registry name, so modded trees work automatically.

| field | default | range | meaning |
|---|---|---|---|
| `enabled` | `true` | bool | Toggle sapling spread. |
| `chance` | `0.0008` | 0-1 | Base chance per candidate (deliberately the lowest rate - each sapling becomes a tree). |
| `logSearchRadius` | `16` | 1-16 | How far to search for a parent tree (caps per-type `radius`). |
| `maxColumnHeight` | `24` | 2-64 | Max log-column height walked when validating a tree. |
| `densityRadius` | `10` | 1-16 | Box radius for the per-species density count. |
| `maxSaplingsNearby` | `3` | 0-64 | Fallback density cap for tree types **not** in `types` (modded). |
| `types` | see below | per sapling id | Per-species `rate` (0-1), `radius` (1-`logSearchRadius`), `density` (0-256), and optional `phenology`. |

**`types` defaults** - grounded in each species' real dispersal/regeneration ecology
(`rate` = prolificacy, `radius` = dispersal distance, `density` = canopy closure; all
xbiome density). The optional **`phenology`** object `{spring, summer, autumn, winter}`
(each 0-10) sets *when* the species spreads: unlike every other channel, sapling seasonality
is per-species. Omit it and the species follows the shared seasonal curve (spring-peaked =
establishment, right for most temperate trees); only the species whose real timing breaks that
pattern carry one. See docs/research.md section 5 for the per-species reasoning and sources.

| sapling id | rate | radius | density | phenology {sp, su, au, wi} |
|---|--:|--:|--:|---|
| `minecraft:birch_sapling` | 1.0 | 16 | 4 | - (shared: spring) |
| `minecraft:acacia_sapling` | 0.7 | 16 | 2 | - (shared: spring) |
| `minecraft:spruce_sapling` | 0.5 | 14 | 5 | 0 / 0 / 1.0 / 0.45 (autumn + winter only) |
| `minecraft:cherry_sapling` | 0.9 | 13 | 3 | - (shared: spring) |
| `minecraft:mangrove_propagule` | 0.7 | 12 | 5 | 0.55 / 1.0 / 0.9 / 0.5 (year-round, late-summer peak) |
| `minecraft:jungle_sapling` | 1.0 | 11 | 6 | 1.0 / 1.0 / 1.0 / 1.0 (aseasonal) |
| `minecraft:oak_sapling` | 0.6 | 9 | 4 | 0 / 0 / 1.0 / 0 (autumn only) |
| `minecraft:dark_oak_sapling` | 0.5 | 8 | 6 | - (shared: spring) |
| `minecraft:pale_oak_sapling` | 0.35 | 8 | 4 | - (shared: spring) |

## trails - footstep wear *(event-based, not scheduler-driven)*

Repeated footsteps wear grass -> coarse dirt -> dirt path; untrodden trails recover. Wear
counters persist in one compact `SavedData` map per dimension (`fallow:trails`). Spectators,
creative flight, and airborne players don't wear; standing still doesn't add wear.

| field | default | range | meaning |
|---|---|---|---|
| `enabled` | `true` | bool | Toggle trails. |
| `stepsToCoarse` | `30` | 1-10000 | Footsteps on grass before it wears to coarse dirt. |
| `stepsToPath` | `80` | (stepsToCoarse+1)-20000 | Total footsteps before coarse dirt wears to a dirt path. |
| `recoveryAmount` | `1` | 0-1000 | Wear removed from each untrodden entry per decay pass. |
| `decayIntervalTicks` | `1200` | 20-168000 | Ticks between decay passes (1200 = 1 minute). |
| `maxTracked` | `4096` | 64-1048576 | Per-dimension wear-map cap (lowest-wear entries pruned on overflow). |

Clear a dimension's wear with `/fallow trails reset`.

## leafLitter - forest floor under canopy *(decay channel)*

Under >=`minCanopyLayers` of non-persistent leaves, grass scatters `minecraft:leaf_litter`
ground cover; a minority of triggers mature the soil to podzol/rooted dirt. Autumn/winter
build it fastest.

| field | default | range | meaning |
|---|---|---|---|
| `enabled` | `true` | bool | Toggle leaf litter. |
| `chance` | `0.02` | 0-1 | Chance per under-canopy candidate. |
| `minCanopyLayers` | `2` | 1-16 | Non-persistent leaf layers overhead required to count as canopy. |
| `canopyScanHeight` | `20` | 4-64 | How far up to look for canopy. |
| `podzolShare` | `0.25` | 0-1 | Share of triggers that mature the soil to podzol instead of scattering litter. |

## overcrowding - grass density (per season + per biome) *(decay channel)*

Grass fills in and is thinned back toward a **density target**: short/tall grass with more than
the (scaled) `neighborThreshold` grass neighbors may thin - tall grass -> short, short grass ->
gone. The target is scaled **per biome** (by `vegetation.biomeDensity` - lush biomes hold thicker
grass, arid biomes thin to sparse) and **per season** (by the factors below - meadows are thick in
summer, recede in winter). The cull *rate* (`chance`) is flat - season lives only in the target, so
it isn't counted twice. So jungle-in-summer grass is lush, desert-in-winter grass is nearly bare.

| field | default | range | meaning |
|---|---|---|---|
| `enabled` | `true` | bool | Toggle overcrowding cull. |
| `chance` | `0.35` | 0-1 | Flat cull chance per over-target candidate (CROWDING is season-exempt - the target carries the season). |
| `neighborThreshold` | `3` | 0-((2r+1)^2-1) | Base grass-density target (neighbors above which a plant is "crowded"); scaled per biome x season, then clamped. |
| `radius` | `1` | 1-4 | Horizontal radius for the neighbor count. |
| `springDensity` | `1.1` | 0-10 | Density-target multiplier in spring. |
| `summerDensity` | `1.3` | 0-10 | Summer - thickest grass. |
| `autumnDensity` | `0.8` | 0-10 | Autumn - thinning. |
| `winterDensity` | `0.4` | 0-10 | Winter - sparsest. |

The per-biome axis reuses `vegetation.biomeDensity` (the same map that caps flowers/bushes), so
tuning a biome's density affects its grass too.

## flowerWilt - seasonal flower lifecycle *(decay channel)*

Flowers wilt through autumn and winter and bloom back in spring (via the spring growth boost
on sprouting). Base rate is tuned so spring nets positive and winter strongly negative.

| field | default | range | meaning |
|---|---|---|---|
| `enabled` | `true` | bool | Toggle flower wilt. |
| `chance` | `0.006` | 0-1 | Base chance per flower candidate (before season decay scaling). |

## shoreline - sugar cane + seagrass *(growth channels)*

Waterline-adjacent ground sprouts sugar cane; shallow water floors sprout seagrass. Vanilla
placement rules (`canSurvive`) decide where.

| field | default | range | meaning |
|---|---|---|---|
| `enabled` | `true` | bool | Toggle shoreline creep. |
| `sugarCaneChance` | `0.004` | 0-1 | Chance per dry waterline candidate. |
| `seagrassChance` | `0.01` | 0-1 | Chance per shallow-water candidate. |
| `maxSeagrassDepth` | `4` | 1-16 | Maximum water depth for seagrass. |
| `maxCaneNearby` | `4` | 0-64 | Density cap for sugar cane (box: +/-r horizontal, +/-2 vertical). |
| `maxSeagrassNearby` | `8` | 0-64 | Density cap for seagrass. |

## bamboo - clonal bamboo spread *(growth channel)*

Open, plantable ground next to an existing bamboo stand occasionally sends up a new shoot, so
groves creep outward and thicken to a cap; vanilla grows each shoot tall. Tropical - routed
through the normal seasonal scaling, so jungle's low `biomeSeasonality` keeps it near-aseasonal.

| field | default | range | meaning |
|---|---|---|---|
| `enabled` | `true` | bool | Toggle bamboo spread. |
| `chance` | `0.004` | 0-1 | Chance per open-ground candidate (x season x per-biome growth). |
| `searchRadius` | `4` | 1-16 | How far to look for a parent stand to creep from. |
| `maxNearby` | `12` | 1-256 | Stalks allowed within `searchRadius` before a grove stops thickening. |

## seasons - the cycle + growth/decay multipliers

Four seasons cycle on `daysPerSeason` in-game days (read off the vanilla overworld clock).
Persisted in one small `SavedData` record. Growth channels use the growth multipliers; decay
channels use the decay multipliers (which run the opposite way - winter is the season of
decay). `enabled=false` freezes the season and neutralizes all seasonal scaling.

| field | default | range | meaning |
|---|---|---|---|
| `enabled` | `true` | bool | Toggle the season cycle + all seasonal scaling. |
| `daysPerSeason` | `10` | 1-1000 | In-game days per season. |
| `springMultiplier` | `1.5` | 0-10 | Growth-channel multiplier in spring. |
| `summerMultiplier` | `1.0` | 0-10 | Growth in summer. |
| `autumnMultiplier` | `0.5` | 0-10 | Growth in autumn. |
| `winterMultiplier` | `0.05` | 0-10 | Growth in winter (near-zero). |
| `springDecayMultiplier` | `0.75` | 0-10 | Decay-channel multiplier in spring. |
| `summerDecayMultiplier` | `0.5` | 0-10 | Decay in summer. |
| `autumnDecayMultiplier` | `1.5` | 0-10 | Decay in autumn. |
| `winterDecayMultiplier` | `3.0` | 0-10 | Decay in winter (drives all winter dieback/cull/wilt). |

## dayNight - seasonal day/night split

Shifts the daylight share of the 24000-tick cycle by season via the vanilla world-clock rate
(no mixins; multiplayer-safe). The cycle length never changes - only the day/night split.
`0.5` is bit-identical to vanilla. `enabled=false` is the kill switch (keeps growth seasons,
leaves vanilla time untouched).

| field | default | range | meaning |
|---|---|---|---|
| `enabled` | `true` | bool | Toggle the seasonal day-length shift (growth seasons stay on). |
| `springDayPortion` | `0.50` | 0.05-0.95 | Daylight share of the cycle at midspring (0.5 = vanilla). |
| `summerDayPortion` | `0.625` | 0.05-0.95 | Long summer days. |
| `autumnDayPortion` | `0.50` | 0.05-0.95 | Vanilla-neutral autumn. |
| `winterDayPortion` | `0.375` | 0.05-0.95 | Long winter nights. |

## visuals - client-side seasonal foliage tint

Pure cosmetics (the mod's only mixin, client-side): grass/foliage tint shifts with the season
- vivid spring, vanilla summer, golden autumn, pale winter, blended day by day. Overworld
only. Vanilla clients, a vanilla server, or `enabled=false`/`strength=0` all render exactly
vanilla colors.

| field | default | range | meaning |
|---|---|---|---|
| `enabled` | `true` | bool | Toggle the seasonal tint. |
| `strength` | `1.0` | 0-1 | Global multiplier on the per-season tint strength. |
| `smoothTransitions` | `true` | bool | Blend tint between season midpoints day by day (vs. a hard per-season look). |

---

## precipitation - seasonal rain & snow

A common `Biome` mixin (`BiomeTemperatureMixin`) shifts every biome's effective temperature by the
season's offset, so vanilla makes temperate biomes snow in winter / rain in summer and
freezes/thaws water on its own - no custom snow task. A load-time Fabric biome modification
(`biomePrecip`) toggles a biome's has-precipitation flag so e.g. savanna can rain at all.

| field | default | range | meaning |
|---|---|---|---|
| `enabled` | `true` | bool | Toggle seasonal precipitation (offset -> 0; biome palette skipped). |
| `springTempOffset` | `0.0` | -2...2 | Temperature added to every biome in spring. |
| `summerTempOffset` | `0.2` | -2...2 | Summer - warmer, so even cool biomes rain. |
| `autumnTempOffset` | `-0.1` | -2...2 | Autumn - first frosts. |
| `winterTempOffset` | `-0.7` | -2...2 | Winter - cold enough to snow temperate biomes (plains 0.8 -> 0.1, under the ~0.15 snow threshold). |
| `biomePrecip` | see below | id/tag -> bool | Force a biome's has-precipitation flag (**load-time** - needs a restart, not `/fallow reload`). |
| `snowAccumulateChance` | `0.1` | 0-1 | Per-visit chance to add a snow layer where it's snow-season and sky-lit. |
| `snowMeltChance` | `0.3` | 0-1 | Per-visit melt chance **at the reference warmth**; the actual chance scales with temperature (`snowMeltChance x warmth / meltReferenceWarmth`, capped at 1). Snow barely melts just above freezing, clears fast when hot. |
| `meltReferenceWarmth` | `0.5` | 0.05-4 | Temperature above the 0.15 thaw point at which melt reaches `snowMeltChance`. Lower = melt ramps up sooner; higher = melt stays gentle until genuinely hot. |
| `thawIce` | `true` | bool | Warm-season thaw melts pond/lake ice (ice sitting on water) back to water; ice on land/builds is left alone. |
| `snowDepth` | see below | id/tag -> 1-8 | Per-biome **max snow depth** in layers - the precipitation *intensity* knob (deep drifts vs. a dusting). |
| `seasonalWeather` | `true` | bool | Bias how often it rains by season (global - one weather timeline). |
| `springRainfall` | `1.3` | 0-10 | Spring wetness (>1 = longer rain, shorter clear). |
| `summerRainfall` | `1.5` | 0-10 | Summer - wettest by default. |
| `autumnRainfall` | `1.0` | 0-10 | Autumn. |
| `winterRainfall` | `0.6` | 0-10 | Winter - driest. |

**`biomePrecip` defaults:** `#minecraft:is_savanna` -> `true` (savanna gains a warm rainy season;
it stays hot, so it rains, not snows). Deserts/badlands stay off (vanilla default -> dry). Use any
biome id (`minecraft:plains`) or `#tag` -> true/false.

**`snowDepth` defaults** (max layers; unlisted = 1, a dusting): `#minecraft:is_snowy` 6,
`minecraft:grove` 5, `#minecraft:is_taiga` 3. Snow builds toward this biome's max **only while it's
actually snowing** (cold + raining - never on a clear winter day) and melts back when it turns warm
(vanilla still lays the first layer during snowfall).
This is the only per-biome precipitation *amount* knob - rain *frequency* stays global (Minecraft
has one weather timeline; see architecture.md).

Notes: the offset is **uniform** yet per-biome-correct because base temperatures differ (jungle
0.95 never snows; plains snows in winter; no-precipitation deserts never snow regardless). It is
server-authoritative and synced, so client rain/snow particles match the server's placement.
Snowy biomes stay white through summer at the defaults - raise `summerTempOffset` for a thaw.
`/fallow season set winter` snows a temperate area; `set summer` melts it.

## events - seasonal weather events

Rolled once per in-game day, biased by season: blizzard (winter), heatwave (summer), storm
(spring/autumn). Each forces its weather for a random duration and applies transient modifiers.

| field | default | range | meaning |
|---|---|---|---|
| `enabled` | `true` | bool | Toggle seasonal events. |
| `chancePerDay` | `0.15` | 0-1 | Chance each in-game day to start a (season-appropriate) event. |
| `minDurationTicks` | `2400` | 20-24000 | Shortest event (2 min). |
| `maxDurationTicks` | `6000` | min-168000 | Longest event (5 min). |
| `blizzardSnowMultiplier` | `3.0` | 0-10 | Snow piles this much faster during a blizzard. |
| `heatwaveGrowthMultiplier` | `0.5` | 0-10 | Growth scaled by this during a heatwave (stalls it). |
| `heatwaveTempBonus` | `0.4` | -2...2 | Temperature added during a heatwave (dries/melts). |

## fruiting - trees drop fruit in season

A natural tree (non-persistent leaves overhead) drops a fruit item on open ground beneath it in
its season.

| field | default | range | meaning |
|---|---|---|---|
| `enabled` | `true` | bool | Toggle fruiting. |
| `scanHeight` | `12` | 1-32 | How far up to look for a fruiting canopy. |
| `types` | see below | leaf id -> entry | Per leaf: `{ item, season, chance }`. |

**`types` defaults:** `minecraft:oak_leaves` and `minecraft:dark_oak_leaves` -> `minecraft:apple`,
season `autumn`, chance `0.01` (vanilla's only tree fruit; the map is open for modded items).

Two more `vegetation` knobs (alongside `biomeDensity`/`biomeGrowth`/`biomeSeasonality`):
**`biomeSeasonPhase`** (id/tag -> 0-3) shifts which season a biome's growth peaks in - default
`#minecraft:is_savanna` -> 3 (summer wet season). **`flowerSeasons`** (season -> list of flower ids)
biases flower selection toward in-season flowers the biome already offers. And `leafLitter` gains
**`springFall`/`summerFall`/`autumnFall`/`winterFall`** (default `0.4/0.3/1.5/0.5`) - the
autumn-peaked curve driving both litter accumulation and the client falling-leaf particle rate.

## Quick recipes

- **Vanilla timing, seasonal growth only:** `dayNight.enabled = false`.
- **Growth seasons but no decay:** set the four `*DecayMultiplier` to 0, or disable the
  decay tasks (`dieback`, `leafLitter`, `overcrowding`, `flowerWilt`).
- **No client visuals:** `visuals.enabled = false` (or just don't install the mod
  client-side).
- **Faster forests:** raise `saplings.chance`; shape per species via `saplings.types`.
- **Lusher meadows in a biome:** raise its `vegetation.biomeGrowth` / `biomeDensity` entry.
- **Turn the whole simulation off:** `scheduler.enabled = false` stops every visit-based
  task; set `trails.enabled = false` and `seasons.enabled = false` for the rest.

See [architecture.md](architecture.md) for how each system works internally.
