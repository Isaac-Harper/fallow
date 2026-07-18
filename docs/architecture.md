# Fallow - architecture

Target: Minecraft 26.1.x, Fabric. Gameplay runs on Fabric lifecycle events + vanilla APIs,
which keeps it compatible and removable. There are **seven mixins**: five client-side and purely
cosmetic - `BiomeMixin` (seasonal foliage tint; see "Visual seasons"), `FixedFoliageTintMixin`
(routes the fixed birch/spruce/lily-pad colors through the same seasonal tint), `LeafFallMixin`
(seasonal falling-leaf particle rate on `LeavesBlock`; see "Seasonal weather..."),
`CherryLeafParticleMixin` (matches the cherry falling-petal color to the recolored
autumn/winter canopy), and `RangeSelectItemModelPropertiesMixin` (registers the season_clock's
`fallow:season` item-model property; see "Items") - and two common gameplay mixins,
`BiomeTemperatureMixin` (the seasonal-temperature precipitation lever; see "Seasonal
precipitation") and `LivingEntityEatMixin` (eat detection for the diet mechanic; see "Diet").
Each gameplay mixin is taken only because no Fabric API hook covers the need: no event or setter
for per-call biome temperature, no finish-eating event in Fabric API 0.151 (only a use-start
callback), and the item-model-property registry is private with no Fabric hook.

```
Fabric events                 vanilla APIs
-------------                --------------
END_LEVEL_TICK --> EcologyScheduler --> EcologyTask(s) --> setBlock / feature.place
CHUNK_LOAD/UNLOAD -+   (tick budget)         |
                                             v
                                      GrowthRateProvider  (config x season)
END_SERVER_TICK --> SeasonService --> SeasonState (SavedData, one record)
                          |
                          +--------> ServerClockManager.setRate (day/night split)
```

## Master switch and first-join notice

Fallow edits vanilla blocks over time, so it is opt-in: the top-level config `enabled` defaults to
`false`, and every system that writes blocks (or drives vanilla into writing them) checks it. When
off: `EcologyScheduler` and `TrailSystem` early-return their tick; `SeasonService` holds
`SeasonalTemperature` at 0 (so the `BiomeTemperatureMixin` never nudges vanilla into snowing or
freezing) and restores the vanilla clock rate; `SeasonEventService` and `WeatherService` stand
down (no forced weather, no spell scaling); `PrecipitationBiomes` skips its biome edits; and
clients render fully vanilla - joiners get no season payload, and a mid-session disable
broadcasts one disabled payload so already-connected clients drop the tint and temperature
offset too. Flip `enabled` true + `/fallow reload` to activate - except the
`PrecipitationBiomes` layer, which is baked at registry load and needs a restart (its own
section notes this).

Regardless of the switch, `notice/FirstJoinNotice` sends each player a one-time chat notice on
first join that the mod alters blocks and is destructive, with the current on/off state and how to
change it. "Seen" is tracked per player UUID in `notice/NoticeData` (one small overworld SavedData),
so it shows exactly once. Plain literal text, so it renders on vanilla clients too.

## Ecology scheduler

`ecology/EcologyScheduler` is a generic engine; behaviors are `EcologyTask`s registered at
init (Phase 1 ships `VegetationSproutTask`). Tasks are individually config-toggled and the
toggle is re-read every visit, so `/fallow reload` applies live.

**Selection.** Per world, a `LongOpenHashSet` of loaded chunk positions is maintained from
`CHUNK_LOAD/UNLOAD` events (O(1) per event, no per-tick allocation). Iteration walks a
snapshot `long[]` ring with a persistent cursor - uniform coverage of all loaded chunks over
time with zero shuffling (Gaia's Breath shuffles the full chunk list per run; that and its
global-not-per-world chunk set are the two things we deliberately fixed). The ring is
re-snapshotted at most once per second when dirty; a stale ring is safe because unloaded
chunks fail `getChunkNow` and are skipped.

**Budget.** Three independent caps, all config (`scheduler` section):

- `chunksPerTick` (default 8) - hard cap on chunk visits per world tick.
- `samplesPerChunk` (default 4) - candidate attempts per task per visited chunk.
- `tickBudgetMicros` (default 200) - wall-clock stop, checked between chunk visits.

Chunks that vanilla wouldn't random-tick (`shouldTickBlocksAt` false - border chunks) are
skipped, so we never grow vegetation where vanilla couldn't.

**Cost.** Default steady state: <= 8 chunk visits x 4 samples = 32 candidates per tick. A
candidate is one heightmap read + 2-3 block-state reads + (rarely) a probability roll's
follow-up work. Measured on a 26.1.2 dev dedicated server (600-tick windows, default
config): **avg 14-35 us per tick, max ~ 1.3 ms** - against a 50 ms tick that's three orders
of magnitude of headroom. Note the wall-clock budget is checked *between* chunk visits, so a
single pathological visit can overshoot it (observed worst case ~1.3 ms on a cold chunk);
the per-visit work is itself bounded (<= samples x task work), so overshoot is bounded too.
With the deliberately absurd stress config (64 chunks x 16 samples, 10-50x chances) it
held 130-140 us avg with a 2 ms budget after warm-up; the one-time worst spike (~200 ms)
happened only when 25 freshly force-loaded chunks were visited on their very first tick
(cold light/heightmap caches). The expensive paths (density guard ~ 1.4k state reads, bush
search ~ 0.3k) run only after a successful flower/bush roll: at default chances that's ~0.2
times per second, amortized noise. Coverage math: with ~440 loaded chunks (one player,
simulation distance 10), every chunk is visited every ~55 ticks (~3 s); each surface column
gets sampled about every 3.5 minutes, so at the 2 % short-grass chance a given column
sprouts roughly every 3 hours - visible regrowth in 10-20 minutes per area, never a wave
you can watch (Gaia's Breath's design goal, adopted).

**Stats.** `/fallow stats` prints per-world avg/max us per tick, chunk visits, and blocks
placed; `scheduler.logTimings` logs the same every 30 s for soak testing.

## Growth rates

`growth/GrowthRateProvider` is the only source of probabilities; call sites never read config
chances directly. One channel per behavior (`GrowthChannel`). Wiring:

```
SeasonalGrowthRates(ConfigGrowthRates)   // seasonal multiplier x config base, min(1.0)
```

The seasonal wrapper is live (Phase 2): per-season multipliers from config (spring 1.5,
summer 1.0, autumn 0.5, winter 0.05 by default), pass-through when `seasons.enabled=false`.
Future modifiers (weather, biome temperature, dieback's winter-raised decay) compose here -
a Phase 3 dieback task gets a `DIEBACK` channel whose seasonal factor *rises* in winter
(the provider decides per channel; the interface already passes the channel and level).

## Vegetation sprouting (Phase 1 task)

Candidates are random heightmap columns (`MOTION_BLOCKING_NO_LEAVES` - plants don't block
motion, so the position lands on the air gap / existing plant above ground). Behaviors, in
roll order (one success per candidate):

1. Existing short grass -> tall grass (`DoublePlantBlock.placeAt`).
2. Grass block + air -> short grass.
3. Grass block + air -> flower from `biome.getGenerationSettings().getBoneMealFeatures()` -
   **the exact list vanilla bonemeal uses**, so datapacked biomes (Arboria/Tectonic) supply
   their own palettes. 26.1 flower features are `simple_block` + state provider; we draw one
   state for a single bloom. Non-`simple_block` (exotic modded) features fall back to
   `feature.place()` - bounded, it's what one bonemeal does.
4. Grass block + air -> bush **copied from a bush growing within `bushSearchRadius`**.
   Nearby-copy *is* the biome-validity rule: bushes only creep from where worldgen put them.
   (No vanilla per-biome bush list exists; this is also datapack-proof, and it's the same
   philosophy as the planned sapling task.) The copy set is the *living* bushes only
   (`BUSH`, `FIREFLY_BUSH`, `SWEET_BERRY_BUSH`) - **`dead_bush` is excluded by invariant: it is
   dead and never propagates**, and no task ever produces one (dieback only steps plants down).
   Each living bush creeps on its own seasonal schedule (`vegetation.bushSeasons`, applied here
   since the BUSH channel is season-exempt like SAPLING - see Growth-rate provider stack).

Guards: vanilla `canSurvive` on every placement (support/biome rules), light >= 9 at the
sprout position (the vanilla grass-spread threshold; configurable), and a density guard for
flowers/bushes (skip when >= `densityMaxPlants` decorations within `densityRadius`, early-exit
counting) so meadows saturate to a ceiling, not forever.

Two verified emergent behaviors worth knowing: the light check uses
`getMaxLocalRawBrightness`, which is sky-darken-adjusted, so **growth pauses at night**
(vanilla grass spread does the same - intended, and it reinforces the "changes while you're
away or asleep" feel); and a saturated area reaches equilibrium (everything short -> tall,
flowers at the density ceiling) after which placement counts drop to ~zero - the system is
self-limiting, verified by soaking a test platform to saturation.

## Seasons

**State** - `season/SeasonState`, a single `SavedData` record on the overworld
(`data/fallow_seasons.dat`): `{season, day_in_season, last_day}`. That is the mod's entire
persistent footprint; no per-block/per-chunk state exists anywhere, so removing the mod
leaves only this orphaned, harmless file.

**Advance** - `SeasonService` on `END_SERVER_TICK` compares the overworld clock's day index
(`totalTicks / 24000`) with `last_day` (pure logic in `SeasonMath`, unit-tested): handles
multi-day jumps (`/time add`, beds), re-anchors on backwards time, normalizes when
`daysPerSeason` shrinks mid-world. `seasons.enabled=false` freezes the cycle and neutralizes
all seasonal modifiers.

**API** - `dev.isaac.fallow.api.FallowSeasons` (+ `Season`): read-only snapshot
`{season, dayInSeason, daysPerSeason}`, enabled flag, current growth multiplier. Stable
surface for addons. Mutation is the op-only `/fallow season set <season> [day]`.

## Seasonal day length

26.1 made this almost free: the world clock (`ServerClockManager`) natively supports a
fractional **rate** (`/time set rate` is vanilla), persists it, and broadcasts it - clients
interpolate at the synced rate with no client-side mod and no judder. Full mechanism analysis
in [research.md](research.md) section 3.

`SeasonService` computes the desired rate each tick from the clock phase and season's
`dayPortion p`: day phase runs at `0.5/p`, night at `0.5/(1-p)`, so one full cycle costs
exactly 24000 real ticks always (unit-tested invariant) - clocks, beds, and cycle-length
assumptions hold; only the split moves. It writes via `setRate` **only when the value
changes** (~2 packets per in-game day).

Correctness properties:

- `p = 0.5` -> rate exactly 1.0 in both phases -> bit-identical vanilla (default for
  spring/autumn; setting all four to 0.5 degrades the whole feature to vanilla).
- Kill switch `dayNight.enabled=false`: Fallow stops writing rates entirely (after stepping
  back to 1.0 if it had written one this session).
- Beds (`moveToTimeMarker`), `/time set/add` (`setTotalTicks`), and `ADVANCE_TIME` act on
  other clock fields; orthogonal to rate. Mods reading time-of-day see the same monotonic
  counter contract as vanilla's own `/time set rate`.
- Hygiene: restore 1.0 on `SERVER_STOPPING`; adopt a leftover non-1.0 rate at startup (crash
  recovery); if Fallow is removed after a crash, vanilla `/time set rate 1` fixes the world.

**Failure modes**: another mod/datapack writing clock rates would last-writer-win against us
at phase boundaries - the usual two-time-mods conflict, documented as incompatible. While
Fallow's day-length feature is enabled, a manual `/time set rate` gets overridden at the next
phase boundary (by design: Fallow owns the rate when the feature is on). Client render mods
(Sodium et al.) read the synced clock; unaffected.

## Config

GSON POJO at `config/fallow.json` (Shulker Pocket pattern), **clamped on load** (out-of-range
hand edits are corrected, not rejected), live-swapped by `/fallow reload` (read `Fallow.CONFIG`
fresh, never cache fields). Sections: `scheduler`, `vegetation`, `dieback`, `saplings`, `trails`, `leafLitter`,
`overcrowding`, `flowerWilt`, `shoreline`, `bamboo`, `seasons`, `dayNight`, `visuals`,
`precipitation`, `events`, `fruiting`, `crops`, `diet`. Mod Menu screen (vanilla widgets, no Cloth Config - matching Shulker Pocket) edits
the master switch + per-feature on/off toggles only, kept toggles-only so it stays padded and
fits any GUI scale; JSON holds everything else (all numeric rates, per-biome maps, per-tree
types). Singleplayer screen edits apply immediately (same JVM); dedicated servers need
`/fallow reload`. **Full field-by-field reference with defaults and ranges:**
[configuration.md](configuration.md).

## Commands

- `/fallow season` - query current season, day, growth multiplier, daylight share.
- `/fallow season set <season> [day]` *(op)* - set the season (testing/admin); re-applies the
  clock rate immediately.
- `/fallow stats` - per-world ecology scheduler timings + placement counts since last query,
  plus trail stats (windowed: each query resets the window).
- `/fallow trails reset` *(op)* - clear the current dimension's trail wear map.
- `/fallow reload` *(op)* - re-read `config/fallow.json` on a live server.
- `/fallow diet` - print the calling player's current diet window: groups covered, groups
  missing, meal count, and active tier (requires a player context - not available from console).

## Public API

`dev.isaac.fallow.api` (stable surface for addons): `Season` (enum + `Codec` + `next()`/
`byId()`/`id()`) and `FallowSeasons` - `get(server)` -> `SeasonInfo{season, dayInSeason,
daysPerSeason}`, `season(server)`, `enabled()`, `growthMultiplier(server)`. Season state is
also broadcast to clients via the `fallow:season` S2C payload (drives the visual tint);
mutation is the op-only command.

## Items

- **`season_clock`** (`item/FallowItems`) - a copper-and-redstone analogue of the vanilla clock
  whose face shows the current Fallow **season** instead of the time of day. The item is a plain
  `Item`; the season display is entirely client-side and works exactly like the vanilla clock's
  time dial: the item-model definition (`assets/fallow/items/season_clock.json`) is a
  `range_dispatch` over a custom property, `fallow:season`, which returns a **continuous fraction
  through the year** in `[0, 1)` so the model picks one of **32 frames** per render - the hand
  sweeps smoothly all the way round once per year instead of snapping between four. Four pips sit at
  N/E/S/W and the pip of the current season is brightened. (Like vanilla's clock, "continuous" is
  just fine-grained frames: 32 ~ a 1px tip step between adjacent angles at this resolution.)
  - The property (`client/SeasonClockModelProperty`) reads `FallowClientSeasons.yearFraction(level)` -
    `season + (dayInSeason + intra-day clock time) / daysPerSeason`, all over 4 - the same synced
    season the tint uses (intra-day from the overworld clock so it always creeps). When seasons are
    frozen the hand holds on the current season. It's registered into vanilla's private property registry
    by `RangeSelectItemModelPropertiesMixin` (a `TAIL` inject on `RangeSelectItemModelProperties.bootstrap`)
    because no Fabric API exposes that registry - a deliberate mixin, same "no other hook
    exists" bar as the rest (see the mixin inventory at the top).
  - Registered into the **Tools &amp; Utilities** creative tab via `CreativeModeTabEvents`; crafted
    like the clock but with copper: four `copper_ingot` in a plus around one `redstone`
    (`data/fallow/recipe/season_clock.json`). All the art is generated by `season_clock_art.py`
    (repo root): the 32 frame PNGs, their per-frame item models, and the `range_dispatch`
    `season_clock.json` (kept in sync) - change `N` and re-run to re-pixel the whole dial.

## Phase 3 (implemented: dieback, saplings, trails)

Each ecology behavior is an `EcologyTask` + a `GrowthChannel` + a config section; candidate
strategy belongs to the task, so heightmap sampling (Phase 1) doesn't constrain under-canopy
work. All three landed exactly on the designed contracts; deviations and discoveries below.

1. **Vegetation dieback** (`ecology/DiebackTask`, channel `DIEBACK`). Plants in *sustained*
   enclosed darkness decay one step per trigger: tall grass -> short -> gone, bare grass block
   -> dirt. "Sustained" is the designed in-memory `Long2ByteOpenHashMap` (pos ->
   consecutive-dark-visit count): a dark visit that passes the probability roll increments;
   **any bright visit evicts**; the step fires at `requiredVisits`. Hard cap, cleared on
   overflow, never persisted (restart delays dieback - accepted). Candidates come from the
   heightmap plus a bounded downward probe through roofs/floors, so interiors and
   multi-story builds are reachable. Two implementation decisions worth recording:
   darkness is judged by `max(raw skylight, blocklight)` via `getBrightness(LightLayer...)`
   - **not** `getMaxLocalRawBrightness`, which is sky-darken-adjusted and would have made
   every outdoor night "dark"; and the seasonal factor runs through the same
   `GrowthRateProvider` with the channel's `Kind.DECAY` selecting the inverted multiplier
   set (winter x3 decay by default). Verified in the test world's sealed dark room: deep
   corners decay first because skylight leaking through the doorway keeps door-adjacent
   spots above threshold - an emergent light gradient, not a bug.
2. **Sapling propagation** (`ecology/SaplingSpreadTask`, channel `SAPLING`).
   Worldgen-datapack-safe as designed: no biome tree features are read; logs found near the
   candidate are mapped log type -> sapling by registry name (`SaplingNames`, with the
   `_propagule` fallback for mangrove; nether stems derive names that don't resolve and are
   skipped). Tree-vs-build heuristic as designed: (a) vertical log column rooted in
   dirt-family ground  and  (b) non-persistent leaves in the canopy box above the column top.
   **26.1 gotcha that the adversarial review caught:** the `minecraft:dirt` block tag no
   longer contains grass blocks or podzol - the correct "dirt-family ground" tag is
   `SUBSTRATE_OVERWORLD` (#dirt + #grass_blocks + #mud + #moss_blocks). The log search is a
   thin y-band box (+/-2/+4 around candidate height) capped at 4 anchor validations, and runs
   only after the rare roll. Verified: 10 saplings seeded around a placed oak in 2.5 min
   (cranked), zero around a log cabin and a stone-rooted log column with persistent leaves.
3. **Trails** (`trail/TrailSystem` + `TrailData` + pure `TrailMath`): the first event-driven
   system - a per-tick player-position listener feeds wear counters instead of chunk visits
   feeding rolls. Wear: grass -> coarse dirt (`stepsToCoarse`) -> dirt path (`stepsToPath`),
   counter capped at 2x so recovery is bounded; standing still doesn't wear; spectators,
   creative flight, and airborne players don't wear. Recovery: a decay pass every
   `decayIntervalTicks` steps untrodden entries down (path -> coarse -> dirt -> entry evicted;
   vanilla grass spread re-greens the dirt). State: one capped `SavedData` map per dimension
   (`fallow:trails`), exactly the Gaia's Breath shape with its weaknesses fixed - per-world
   not global, `setDirty` only on actual change, decay skips unloaded chunks rather than
   force-loading them, and overflow prunes the lowest-wear quartile via an O(n) histogram
   cutoff (no sort, constant allocation). `/fallow trails reset` (op) clears a dimension's
   wear for testing.

4. **Leaf litter** (`ecology/LeafLitterTask`, channel `LEAF_LITTER`, decay kind - autumn and
   winter build forest floor fastest). Grass under >=N layers of *non-persistent* leaves
   (same player-build immunity as saplings) converts to podzol (80 %) or rooted dirt (20 %),
   stateless and visit-driven, early-exiting the canopy scan at the threshold.
5. **Shoreline creep** (`ecology/ShorelineCreepTask`, channels `SUGAR_CANE`/`SEAGRASS`).
   One column visit serves both: a dry surface rolls sugar cane, whose entire placement rule
   (soil + horizontal water neighbor) is vanilla `canSurvive`; a water column derives depth
   from the two heightmaps (`MOTION_BLOCKING_NO_LEAVES` includes fluids, `OCEAN_FLOOR`
   ignores them - their difference *is* the water depth) and rolls seagrass into shallow
   source water. Both density-capped with early-exit counts (box extent, like all tasks).
6. **Bamboo spread** (`ecology/BambooSpreadTask`, channel `BAMBOO`). Clonal, like real rhizome
   runners: an open candidate (air above the surface; bamboo is motion-blocking, so existing
   stalks aren't sampled - only the gaps and edges are) rolls the `BAMBOO` chance, checks vanilla
   `canSurvive` (the `bamboo_plantable_on` soil rule), then a single box scan does double duty -
   it must find >=1 nearby stalk (the clonal/validity gate: bamboo only appears where bamboo grows,
   so no biome check) and fewer than `bamboo.maxNearby` (the grove cap). Stalks are counted at
   their base (a bamboo block whose neighbour below isn't bamboo) so a tall stalk reads as one.
   Vanilla random-ticks each shoot tall. Unlike the SAPLING/BUSH channels, `BAMBOO` is *not*
   season-exempt - it rides the normal seasonal scaling, and jungle's low `k` flattens that to
   near-aseasonal automatically (a temperate planting would slow in winter), so no per-task season
   code is needed.

## Growth-rate provider stack

Probabilities flow through one layered `GrowthRateProvider` (outermost-last), each layer a
pass-through when its feature is neutral:

```
BiomeGrowthRates( SeasonalGrowthRates( ConfigGrowthRates ) )
   per-biome x     season (biome-modulated) x   config base
```

`chance(channel, level, pos)` carries the candidate position so the position-dependent layers
(biome growth, and the season layer's biome modulation) resolve. Channels carry a
`Kind` (GROWTH/DECAY): season applies growth multipliers to growth channels and the inverted
decay multipliers to decay channels. The season factor's **amplitude is biome-modulated**
(`vegetation.biomeSeasonality`, via `BiomeTuning.applySeasonality`): tropical biomes flatten the
curve toward year-round growth, boreal biomes amplify it into a *true no-growth winter* (and a
booming spring) - applied to growth and decay alike, so tropical biomes also have muted winter
dieback. The flat biome *growth* multiplier applies to growth channels only (winter dieback
shouldn't depend on biome fertility). The season is also **phase-shifted per biome**
(`vegetation.biomeSeasonPhase` via `BiomeTuning.seasonPhase`): a biome reads the curve at a
shifted season index, so e.g. savanna (offset 3 = -1) peaks in its summer wet season instead of
spring - and `SaplingSpreadTask` and `OvercrowdingTask` apply the same shift, so a biome's whole
ecology (growth, thinning, sapling spread) stays on one calendar. **SAPLING, BUSH, FRUIT,
LEAF_LITTER, and CROWDING are exceptions** to seasonal *scaling* - each runs its own seasonal
term in-task: `SaplingSpreadTask` via `saplings.types[].phenology`, `VegetationSproutTask` via
`vegetation.bushSeasons` (sweet berry -> autumn, firefly -> summer, generic bush -> spring),
`FruitDropTask` via its per-type season gate, `LeafLitterTask` via an autumn-peaked
`leafLitter.leafFallWeight` (shared with the client leaf-particle rate, below), and
`OvercrowdingTask` via its per-season density target (so the cull rate stays flat - season isn't
counted in both the target and the rate). An active **heatwave** multiplies every growth channel
by `SeasonEvents.growthMultiplier()`, exempt or not - the stall is a transient event, not part
of any per-species curve. The current season is read from a
**per-tick cache** (`SeasonClock`, refreshed at `START_SERVER_TICK`) rather than a SavedData lookup
per sampled candidate; `SeasonState` remains the authoritative source advanced by `SeasonService`. Flowers stay on the biome-modulated shared curve (the mod
delegates flower *choice* to the biome bonemeal palette), but the *pick* is season-biased toward
`vegetation.flowerSeasons` entries the palette already contains.

## Per-biome and per-tree tuning

- **`biome/BiomeTuning`**: three config maps of biome id or `#tag` -> multiplier -
  `vegetation.biomeDensity` (scales density caps for flowers/bushes/saplings),
  `vegetation.biomeGrowth` (scales growth-channel *rates*), and `vegetation.biomeSeasonality`
  (the season-amplitude scalar `k`: 0 = aseasonal/tropical, 1 = temperate baseline, >1 = boreal
  with a true no-growth winter - see the provider stack above). Exact id beats tags; first
  matching tag in config order wins (so `#is_snowy` precedes `#is_taiga` - a snowy_taiga reads
  as snowy); unlisted = 1.0. All five per-biome values (those three + `precipitation.snowDepth`
  and `vegetation.biomeSeasonPhase`) are resolved **eagerly into one `Biome -> Factors` map**, built
  by walking the biome registry at server start / client join / `/fallow reload` (`rebuild`). One
  source of truth: tasks read it with `(level, pos)`; the temperature mixin - which holds only a
  bare `Biome` with no level to resolve tags from - reads the *same* map by instance
  (`seasonality(Biome)`), which the old lazy `(level,pos)` cache couldn't serve. (That mixin lookup
  is why the seasonal-temperature swing is now scaled by the real `k` rather than a base-temp proxy
  - tropical biomes never snow.) Defaults ship lush forests/jungles/swamps up,
  savanna/taiga/snowy/badlands/desert down, with seasonality near-aseasonal in the tropics and
  amplified in the cold. Verified by
  A/B: density `plains: 0.0` stopped
  flowers+saplings on plains while forest quadrants kept going; growth `plains: 0.0` -> 0
  flowers vs forest (x2.5) -> 33 under identical spring soak.
- **Density-cap invariant** (`BiomeTuning.resolveCap`): the per-biome-scaled cap
  (`round(base x multiplier)`) must be used both as the nearby-count early-exit limit *and*
  the comparison threshold. A first cut counted only up to the un-scaled base while comparing
  against the scaled cap, so in any biome with multiplier > 1 (plains 1.25, forests 1.5) the
  `count >= cap` test never fired and saplings carpeted. Both density-guarded tasks now route
  through `resolveCap` (unit-tested). Sapling defaults are deliberately the sparsest in the
  mod - chance 0.0008 (lowest growth rate) and `maxSaplingsNearby` 1 (forests creep, not fill;
  x1.5 forest -> 2, x0.2 desert -> 0).
- **`saplings.types`**: per sapling id, three placement knobs grounded in the species' real-world
  ecology (researched per genus): `rate` (0..1 spread prolificacy - pioneer vs climax),
  `radius` (dispersal distance from the parent, Chebyshev, capped by `logSearchRadius`), and
  `density` (canopy closure - max saplings within `densityRadius` before the species stops
  seeding; the task resolves the parent species first, then applies *its* cap, so dense and
  sparse species coexist) - plus an optional fourth, `phenology`, a `{spring, summer, autumn,
  winter}` vector for dispersal *timing* (see the season column below and docs/research.md section 5).
  The first three are biome-scaled where relevant. Unlisted (modded) types use rate 1.0, the
  global radius, `maxSaplingsNearby`, and the shared seasonal curve.

  | sapling | real counterpart | dispersal | strategy | rate | radius | density | season peak |
  |---|---|---|---|--:|--:|--:|---|
  | birch | silver/paper birch | wind samaras | pioneer | 1.0 | 16 | 4 | spring (shared) |
  | acacia | savanna acacia | grazer gut, far | intermediate | 0.7 | 16 | 2 | spring (shared) |
  | spruce | boreal spruce | wind, slow regen | climax | 0.5 | 14 | 5 | autumn + winter |
  | cherry | Japanese cherry | bird-eaten drupes | pioneer | 0.9 | 13 | 3 | spring (shared) |
  | mangrove | red mangrove | buoyant propagule | intermediate | 0.7 | 12 | 5 | late summer |
  | jungle | kapok/dipterocarp | wind, fast gaps | pioneer | 1.0 | 11 | 6 | aseasonal (flat) |
  | oak | English/white oak | heavy acorns | climax | 0.6 | 9 | 4 | autumn |
  | dark oak | dense sessile oak | jay/squirrel cache | clumped | 0.5 | 8 | 6 | spring (shared) |
  | pale oak | relict old-growth oak | rare scatter-hoard | relict | 0.35 | 8 | 4 | spring (shared) |

  `logSearchRadius` is 16 to allow the wide wind/animal dispersers; the post-roll log scan
  grows with it but runs only after the rare chance roll. Verified by A/B: oak rate 0.0 ->
  zero saplings; per-species density under identical cranked conditions gave dark oak
  (cap 6) -> 21 saplings vs acacia (cap 2) -> 3 - a closed canopy vs a sparse scatter.
- **`phenology` (per-species season)**: real trees disperse and establish at very different
  times of year, so sapling spread is seasonal *per species* rather than via the shared growth
  curve. The governing fact: most temperate trees disperse in autumn but *establish* in spring
  (after cold stratification) - and since a Fallow sapling is an established seedling, the shared
  spring-peaked curve is the faithful default, so only the species that break that pattern carry
  an override: `oak` (white-oak group germinates in fall on dropping, no dormancy -> **autumn**),
  `spruce` (boreal conifer sheds viable seed into winter -> **autumn + a winter trickle**),
  `mangrove` (viviparous, warm-coastal, no temperate winter -> **late-summer** peak, never
  winter-dead), and `jungle` (everwet aseasonal supra-annual mast -> **flat** year-round).
  Override vectors peak at 1.0; the SAPLING channel is exempt from `SeasonalGrowthRates` so the
  weight - applied on the prolificacy roll in `SaplingSpreadTask` - isn't double-counted. Unit
  test: `SaplingPhenologyTest` (override vs shared-curve fallback). Sources + the full
  per-species rationale (incl. masting, deferred) are in docs/research.md section 5.

## Visual seasons (client)

The mod's cosmetic mixin, client-side: `BiomeMixin` injects at the returns of
`Biome.getGrassColor/getFoliageColor/getDryFoliageColor` - the choke point both vanilla and
Sodium resolve block tints through, and the only place that also catches biomes with fixed
color overrides (swamp, badlands, pale garden). It applies `visual/SeasonTint` params:
per-season target color + strength (summer is identity - vanilla bit-exact), smoothly
interpolated between season midpoints day by day (`smoothTransitions`), scaled by a global
`visuals.strength`, all pure math unit-tested in the main source set.

State arrives over `network/SeasonSyncPayload` (S2C; sent on join and on any season/day/
config change - a few packets per in-game day). The client recomputes an immutable tint
triple swapped through a single volatile (render thread never sees mixed seasons - review
finding), gates it to the **overworld only** via a per-tick dimension watch (Nether crimson
forests stay crimson - review finding), and triggers one full chunk-mesh rebuild
(`LevelRenderer.allChanged`, the resource-reload mechanism) only when the effective params
actually change. Vanilla clients, vanilla servers, `visuals.enabled=false`, or
`strength=0.0` are all exactly vanilla.

## Seasonal precipitation

Two layers make precipitation seasonal and per-biome without a server-side weather rewrite:

- **`BiomeTemperatureMixin` (common) - the seasonal-temperature lever.** It injects the current
  season's offset (`precipitation.*TempOffset`) into `Biome.getTemperature(BlockPos, int)` at
  RETURN. Vanilla derives *everything* from that one number - `getPrecipitationAt` (rain vs snow
  particles), `coldEnoughToSnow`/`warmEnoughToRain`, `shouldSnow`/`shouldFreeze`, and the
  `ServerLevel.tickPrecipitation` snow-layer/ice placement - so a single redirect makes rain<->snow,
  accumulation, and freeze/thaw all seasonal, with vanilla doing the block work. The per-position
  `temperatureCache` keeps the seasonless base (we add the offset on each return), so it stays
  correct. The offset is **scaled by the biome's `biomeSeasonality` k** - resolved per biome and read
  from the eager `BiomeTuning` map (`seasonality(Biome)`; the mixin has only the `Biome`, no level).
  Tropical biomes (k~0.2, *incl.* mangrove 0.3 and swamp 0.5) barely cool and never snow - even at
  altitude, where `getTemperature` is already lowered (the bug that made the y=100 test platform's
  jungle snow); temperate (k=1.0) snow in winter; boreal (k>1) snow hard. Deserts
  (`has_precipitation=false`) never precipitate regardless. So *which biomes snow* is the same
  per-biome knob that controls growth amplitude - fully tunable, no base-temp proxy. The offset is
  server-authoritative - `SeasonService` computes it and ships it in `SeasonSyncPayload` so the
  client's particle type matches the server's placement; `SeasonalTemperature` is the
  side-agnostic volatile both read. Offset 0 (out of season / disabled / vanilla server) is
  bit-identical to vanilla.
- **`PrecipitationBiomes` (load-time, no mixin) - the static palette.** A Fabric biome
  modification that forces `has_precipitation` per `precipitation.biomePrecip`. Temperature only
  flips rain<->snow for biomes that precipitate *at all* - deserts/savanna are gated by a flag, not
  temperature - so this is the only way to let savanna rain (default `#is_savanna` -> true; it
  stays warm, so it rains, not snows). Applied at biome-registry load: affects existing worlds,
  but a change needs a restart, not `/fallow reload`.
- **`PrecipitationTask` (scheduler) - per-biome snow depth + thaw.** Vanilla lays the first snow
  layer; this builds it toward a per-biome maximum (`precipitation.snowDepth` - snowy/boreal deep,
  temperate a dusting) **only while it's actually snowing** - `cold && level.isRaining()` (real
  vanilla snowfall), never on a clear winter day - and actively melts snow and thaws ice when it's
  *warm* (not cold). A clear cold day is a no-op (snow just sits). "Cold" is weather-independent
  (`Biome.getPrecipitationAt`, season-shifted by the mixin) - distinct from "snowing" (`isRaining`),
  which is the bug the earlier code conflated. Sky-exposed surfaces only. The per-biome precipitation
  *intensity* knob; ice thaw only melts ice sitting on water (frozen ponds, not player ice on land).
  **Melt rate is temperature-tied**: the per-visit chance is `snowMeltChance x warmth /
  meltReferenceWarmth` (capped at 1), where `warmth` is how far the biome's seasonal temperature
  sits above the 0.15 thaw point. `Biome.getTemperature` is private, so warmth is rebuilt from the
  public base (`getBaseTemperature`) plus the *same* k-scaled offset the mixin adds
  (`SeasonalTemperature.offset() x biomeSeasonality`) - no new accessor, no per-biome melt table, and
  altitude isn't needed in the term because the precip-type gate already held thaw off until it was
  warm enough to rain. So snow lingers in a cool early spring and clears fast in high summer or a hot
  biome.

Together: a temperate forest rains in summer and snows (real flakes, per-biome drift depth, frozen
ponds) in winter, then thaws in spring; savanna gains a warm rainy season; deserts stay bone-dry.
The remaining limit is *per-biome* rain frequency: Minecraft has a single global weather timeline,
so true per-biome rain *rates* would need localized weather (custom sim + render - out of scope).
The seasonal *global* frequency mood is `season/WeatherService` (next section). Snowy biomes also
stay white through summer at the default offsets (raise `summerTempOffset` for a thaw).

## Seasonal weather, events, fruiting & leaf fall

- **Weather frequency (`season/WeatherService`)** - watches the overworld's single rain countdown
  and, when vanilla rolls a fresh spell (the countdown jumps up to a full duration), scales that
  roll by the season's `precipitation.*Rainfall` weight (wetter season -> longer rain, shorter
  clear) through `WeatherData` public setters. Global - one weather timeline. Only the fresh roll
  is touched, so vanilla's own ticking does the rest; a `/weather` duration that raises the
  countdown is indistinguishable from a roll and is scaled the same way. While a season event owns
  the weather the service stands down and re-primes afterwards, so it never rescales a spell it
  didn't watch start.
- **Seasonal events (`season/SeasonEventService` + `SeasonEvents`)** - rolls once per in-game day,
  biased by season: blizzard (winter), heatwave (summer), storm (spring/autumn). For a random
  duration it forces the event's weather (`WeatherData`), pinning the weather timers to the
  event's own clock so the pre-event countdown (a clear spell can be 180000 ticks) can't survive
  as the forced spell's duration, and publishes transient modifiers through
  the `SeasonEvents` volatile holder: a snow-accumulation multiplier (read by `PrecipitationTask`),
  a growth multiplier (read by `SeasonalGrowthRates`, growth channels only), and a temperature
  bonus (folded into the synced offset by `SeasonService`, so a heatwave warms/melts and the client
  matches). Registered before `SeasonService` so the bonus is set before the offset is computed.
- **Fruiting (`ecology/FruitDropTask`)** - in its configured season a natural tree drops a fruit
  `ItemEntity` on open ground under its canopy (oak/dark-oak -> apple in autumn by default). The
  tree is identified by a non-persistent leaf block overhead - same player-build immunity as the
  litter/sapling heuristics. The `fruiting.types` map is open for modded fruit. Each type's
  `chance` is multiplied by the provider stack's `FRUIT` channel (biome growth + heatwave stall;
  the shared seasonal curve is skipped because the per-type season gate *is* the curve). With
  seasons disabled every type is in season; an unknown season string is nulled with a warning at
  config load.
- **Leaf fall** - a single autumn-peaked `leafLitter.leafFallWeight` drives two things so they
  match visually: the **ground litter** accumulation rate (`LeafLitterTask`, which is why
  LEAF_LITTER is season-exempt in the provider stack) and the **client falling-leaf particles**
  (`client.mixin.LeafFallMixin` scales vanilla's per-leaf `leafParticleChance` by the synced
  season's weight). Heavy flurry in autumn, light the rest of the year - the third mixin, cosmetic
  and client-only like the foliage tint.

## Seasonal vegetation lifecycle (overcrowding + flower wilt)

Two decay tasks give vegetation a yearly rhythm, both scaled by the season decay multiplier
(spring 0.75 ... winter 3.0):

- **`ecology/OvercrowdingTask`** (channel `CROWDING`): grass is thinned back toward a
  **density target** - a sampled short/tall grass with more grass-family neighbors (within
  `radius`, tall grass counted once via its LOWER half) than the target has a chance to thin
  (tall grass -> short grass, short grass -> gone). The target is the base `neighborThreshold`
  scaled **per biome** (`BiomeTuning.densityMultiplier`, i.e. `vegetation.biomeDensity` - lush
  biomes hold thicker grass, arid thin to sparse) and **per season** (`overcrowding.*Density` -
  thick in summer, sparse in winter), clamped to the neighborhood size. The thinning *rate* is
  **flat** - `CROWDING` is exempt from `SeasonalGrowthRates` so the season lives only in the target,
  not double-counted in target *and* rate (an audit finding). Net: jungle-summer grass is lush,
  desert-winter grass nearly bare, and packed meadows open up in winter and refill in spring (the
  seasonal grass-sprout rate completes the cycle). Verified earlier: a 625-block
  carpet thinned to 361 over a winter soak. A defensive UPPER-half guard (review finding) handles
  the abnormal case where the heightmap lands on a tall-grass top.
- **`ecology/FlowerWiltTask`** (channel `FLOWER_WILT`): a sampled flower (small or tall;
  tall flowers normalized to their LOWER half) has a chance to wilt away. The target set is
  `#minecraft:small_flowers` plus the tall garden and bed flowers - deliberately **not**
  `#minecraft:flowers`, which is the bee-attraction tag and includes mangrove propagules (what
  `SaplingSpreadTask` seeds), cherry/flowering-azalea *leaf* blocks, chorus flowers, spore
  blossoms, and cactus flowers; `VegetationSproutTask`'s density guard counts the same set. The base rate is
  tuned against the flower sprout rate so the **effective** spring sprout (0.004 x 1.5 =
  0.006) >= spring wilt (0.006 x 0.75 = 0.0045) - flowers net-accumulate in spring - while
  autumn/winter wilt (x1.5 / x3.0) far outpaces the season-suppressed sprout. The adversarial
  review caught the first cut here: a base wilt of 0.04 (10x the sprout base) made flowers
  wilt faster than they bloomed in every season; lowering it to 0.006 fixed the arc, now
  pinned by a unit test comparing *effective* rates, not raw multipliers. Verified: a plains
  patch bloomed to 139 flowers in spring and wilted to 0 in winter.

Still future: **vines/moss aging on shaded stone** - possible task type; noted, deliberately
given no design weight. **Mushroom spread excluded**: vanilla already does it.

## Crops

The crop layer adds the first real new blocks Fallow ships. Two growth paths are used, each
where it fits the goal:

**Player-planted crops on the vanilla random tick.** `FallowCropBlock` (the base for all
standard farmland crops: turnip, cabbage, onion, leek, barley, rye, oat, garlic, radish,
parsnip, pepper, flax, tomato, rice) subclasses `CropBlock` with `BeetrootBlock`'s four-age
pattern (0-3) and overrides `randomTick`. Before delegating to vanilla growth it applies the
following logic (active only when `crops.enabled && crops.seasonGating && seasons.enabled`):

1. Per-crop seasonal weight via `crops.cropSeasonWeight(blockId, season)`, which reads the
   `cropSeasons` map. Missing entries default to 1.0. A weight of 0.0 in winter with
   `crops.winterKill=true` converts the block to `fallow:withered_crop`; without kill, or for a
   non-zero weight, the tick simply returns. Otherwise the weight is a probability gate on the
   random float: a weight of 0.5 lets roughly half the ticks through.
2. After the season gate, `canGrowHere(level, pos)` is called. The default returns true; rice
   overrides this with the paddy rule (see below) to stall without killing.
3. The shared seasonal curve does not apply again here - the per-crop weight is the sole seasonal
   term, for the same double-counting reason as `bushSeasons` and `phenology`.

**Rice paddy rule.** `RiceCropBlock` overrides `canGrowHere` to call `RicePaddy.hasWaterWithin`:
a pure geometry scan over a flat (2*range+1) square on the farmland's Y and one block below,
checking each position against a water predicate. If no water is found within `crops.paddy.range`
blocks (default 4), growth stalls without a winter-kill. `RicePaddy` is a stateless utility class
with no Minecraft types, making it directly unit-testable.

**Corn - double-height stalk.** `CornCropBlock` subclasses `DoublePlantBlock` (like vanilla
pitcher crop). Ages 0-3; the lower half drives ticks, the upper half appears at age 2 and is kept
in sync. Below the double threshold the block is a single low block and `updateShape` does not
invoke `DoublePlantBlock`'s paired-half bookkeeping. Above it, breaking either half clears both
via the vanilla inherited behavior. Winter-kill clears the upper half to air and replaces the
lower half with the dead husk.

**Trellis climbers.** `TrellisCropBlock` is the shared abstract base for pea, cucumber, grape,
and hops. Ages 0-3. Right-clicking at age 3 harvests the crop item and resets to age 1; the
trellis structure persists. On winter-kill the climber reverts to a bare `fallow:trellis` block
rather than a dead husk. Subclasses supply the block id, harvest item, min/max harvest count, and
clone item. A `onReachedMaxAge` / `onHarvest` hook lets the pea subclass add nitrogen fixing
without duplicating the shared flow.

**Berry bushes.** `BerryBushBlock` is the shared abstract base for strawberry, raspberry, and
blackberry. Sweet-berry idiom: ages 0-3, right-click at age 2+ harvests and resets to age 1, no
contact damage. Bushes stall in winter but are never winter-killed; only the age check (not a
kill path) prevents growth at 0 weight.

**Squash stem.** `SquashStemBlock` subclasses vanilla `StemBlock`, reusing its grow/fruit
lifecycle (fruit: `fallow:squash`, attached stem: `fallow:attached_squash_stem`). It rides
vanilla's pumpkin support tags (`#supports_pumpkin_stem`, `#supports_pumpkin_stem_fruit`) so no
new block tags are needed. The one addition over vanilla is a `randomTick` override that applies
the season gate (key `fallow:squash_stem`) before delegating to `StemBlock.randomTick`.

**Onion** is carrot-style: `FallowItems.ONION` is a `BlockItem` pointing to `OnionCropBlock`,
so right-clicking farmland with an onion plants it directly, and the mature crop drops onions.

**Wild forage through `ForageSpreadTask`.** The task implements `EcologyTask` and registers on
the scheduler. It is column-sampling like `VegetationSproutTask`: random heightmap surface,
grass-block ground, light check, biome eligibility against `crops.wild.homes`, density guard
(max 2 of the same plant within radius 8), then `canSurvive` + `setBlock`. Biome matching
supports exact ids and `#tag` entries. The task runs through the `FORAGE` growth channel, which
follows the shared seasonal curve (not per-species-exempt): wild spread slows naturally in
winter alongside the rest of the ecosystem, unlike player crops which use per-crop weights.
Eleven plants have configured homes: wild onion, strawberry bush, wild rice, wild grape vine,
wild hops, chanterelle, mint, sage, thyme, ramsons, and sorrel.

**Trellis state machine.** `TrellisBlock` (a `BushBlock`) carries no age property. Right-clicking
it with the appropriate seed item converts it to the climber crop at age 0, consuming one seed.
The climber grows age 0-3 on random ticks and stays harvestable at age 3 until right-clicked.
Winter kill reverts the crop to a bare trellis. Breaking the trellis block itself yields the
trellis item (handled by its loot table).

**Nitrogen fixing.** When a pea crop reaches age 3 or is right-click harvested, `tryFixNitrogen`
scans the box `(fixRadius horizontal, y-1..0)` around the ground block for coarse dirt or rooted
dirt and converts one randomly chosen candidate to plain dirt. Gated by
`crops.legumes.fixNitrogen`. Triggered both from `randomTick` (on advancing to max age) and from
`useWithoutItem` (on harvest).

**`CropsEnabledCondition` and grass loot injection.** `CropsEnabledCondition` is a parameter-free
`LootItemCondition` registered as `fallow:crops_enabled` that passes when both `enabled` and
`crops.enabled` are true. `GrassSeedDrops` injects an extra loot pool into vanilla's
`blocks/short_grass` and `blocks/tall_grass` tables via `LootTableEvents.MODIFY`: one roll,
conditions `[crops_enabled, random_chance(seedDropChance)]`, one-of 15 crop seeds at equal weight
(turnip, cabbage, pea, barley, rye, oat, radish, parsnip, leek, flax, pepper, squash, tomato,
cucumber, corn - rice is excluded, obtained from wild rice only). The `seedDropChance` constant
is baked from the config at registration time (server start or `/reload`), not re-read per-break.

**`FruitDropTask` fallow-namespace gate.** Cherry and plum are added to the `fruiting.types`
defaults (`minecraft:cherry_leaves` -> `fallow:cherries`, spring; `minecraft:flowering_azalea_leaves`
-> `fallow:plum`, autumn, chance 0.008). `FruitDropTask` guards any entry whose `item` starts
with `fallow:` behind `crops.enabled`: if the crop layer is off the drop is silently skipped, so
visits never produce items that have no gameplay registration context.

**Registration order.** `FallowBlocks.register()` must be called before `FallowItems.register()`
because the seed `BlockItem` constructors take a reference to the already-registered block
instances. Both happen in `Fallow.onInitialize()`.

**Client render layers.** Not documented here: the rendering for crop blocks was not resolved at
implementation time. Omit from any player-facing rendering discussion until resolved.

## Diet

The diet mechanic (D1) runs as a lightweight server-side service alongside the ecology
scheduler, sharing no state with it.

**Eat detection.** `LivingEntityEatMixin` injects at `HEAD` of `LivingEntity.completeUsingItem`.
This is the seventh mixin and the second common (gameplay) mixin in the codebase. It is a
deliberate fallback: Fabric API 0.151 ships no finish-eating event - the only exposed hook,
`UseItemCallback`, fires at use-start before any food is consumed, making it unsuitable for
recording a completed meal. The `HEAD` inject reads `getUseItem()` before vanilla consumes the
stack, then exits early for client-side calls and non-player entities.

**Group lookup.** `DietGroup` (an enum of six values: `GRAIN`, `VEGETABLE`, `FRUIT`, `PROTEIN`,
`FUNGI`, `SUGAR_OIL`) maps each value to its `fallow:diet/<id>` item tag and provides a static
`groupsOf(stack, level)` method that returns the set of groups the eaten item belongs to. An
item may belong to multiple groups. Untagged items return an empty set and are silently ignored
by `DietService.recordMeal` - drinks and other non-food use-items pass through the mixin but
produce no window entry.

**Window state.** `DietWindow` is a pure Java class (no Minecraft or codec imports): an
`ArrayList<Meal>` where each `Meal` records a `Set<String>` of group ids and the in-game day.
`push` appends and trims to `windowSize`; `prune` removes entries whose day is at least
`mealExpiryDays` behind the current day (0 disables). `distinctGroups` returns the union of all
group sets currently in the window; `newGroups(previousGroups)` returns the delta for the
announce-on-first-cover notification. The class is directly unit-testable with no game context.

**Persistence.** `DietData` is a `SavedData` on the overworld (key `fallow:diet`), storing a
`Map<UUID, DietWindow>` serialized as a string-keyed NBT map. One record covers all players on
the server. `DietData.get(server)` uses `computeIfAbsent` for lazy creation.

**Effect application.** `DietService` registers on `END_SERVER_TICK`. A `tickCounter` gate lets
the actual work run every 20 ticks (once per second) rather than every tick. Each pass iterates
online players, prunes their window for time expiry, then calls `applyEffects`: score >= 6 ->
`MobEffects.ABSORPTION` at `tierTwoAmplifier` (default 1 = Absorption II, four hearts); score
>= `tierOneGroups` (default 4) -> Absorption at `tierOneAmplifier` (default 0 = Absorption I,
two hearts); below tier one, no effect is applied and any existing Absorption decays naturally.
Effects are given a 35-second duration so they lap the 20-tick refresh comfortably without
requiring a long-term potion entry in the player's effect list. `applyEffects` is `public` so
gametests can drive it directly with a known window state without waiting for the poll cycle.

**Announce-on-first-cover.** When `diet.announceNewGroups` is true, `recordMeal` sends a
`sendOverlayMessage` (actionbar) for each group that entered the window for the first time in
this push. The lang key is `fallow.diet.group_added`.

**`/fallow diet` command.** `queryDiet` in `FallowCommands` uses `getPlayerOrException` (must
be called by a player, not from console). It reads the live `DietWindow`, computes covered and
missing groups against the full `DietGroup` enum set, determines the tier, and sends five
translatable chat lines via `fallow.diet.status.*` lang keys: header (score/6), covered groups,
missing groups, tier label, and meal count.

**No client sync.** The diet window and score are server-authoritative. The `/fallow diet`
command output reaches the player over normal chat packets; no dedicated S2C payload is
needed. Absorption is a standard potion effect and syncs via vanilla's effect-update packet.

## Preservation (D2)

The preservation layer adds four items with no new mechanics: all work is pure items, recipes, and
tags. Jam and pickles are crafted shapeless recipes whose result carries `usingConvertsTo(Items.GLASS_BOTTLE)`,
the same `Consumable` + property that honey bottles use to return the bottle on eating; both use
the `DRINK` animation and stack to 16. Raisins and dried chanterelles are standard cooking recipes
(`smelting`, `smoking`, and `campfire_cooking` variants) and use a fast 0.8-second `consumeSeconds`
on their `Consumable`. Diet group membership is declared purely through item tags: jam and raisins
join `fallow:diet/fruit`; pickles join `fallow:diet/vegetable`; dried chanterelles join
`fallow:diet/fungi`. The `fallow:jam_fruits` tag lists all valid jam inputs and is open for
datapack extension.

## Future work (explicitly out of v1)

- Snowy-biome summer thaw tuning; masting (irregular bumper seed years for oaks/pale_oak) - noted.
- Diet: per-group micro-flavor bonuses - later, see docs/diet.md for phasing.
