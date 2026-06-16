# Fallow - Phase 0 research

Date: 2026-06-12. Target: Minecraft **26.1.2**, Fabric.

## 1. Gaia's Breath source study

Modrinth page: https://modrinth.com/mod/gaas-breath -> source link
https://github.com/MAT1B1/gaias-breath (**MIT**, so cloning for study is fine; we still
write all Fallow code from scratch). Clone lives in `reference/gaias-breath` (gitignored).
It targets 1.21.9 Fabric, Yarn mappings, ~660 lines total. Three systems (moss, soil wear,
vegetation) + two small mixins (crops grow on rain, fire drops charcoal).

### 1.1 Tick budgeting

- **Hook**: `ServerTickEvents.END_WORLD_TICK` (Fabric), overworld only. Not random ticks,
  not a block mixin - its own scheduler, same direction we want.
- **Cadence**: a global tick counter; every 10th tick it runs exactly **one** system,
  round-robin (`rouletteIndex`) over the three systems. So each system runs every 30 ticks.
- **Chunk selection**: `ChunkTracker` keeps a `HashSet<ChunkPos>` maintained from
  `ServerChunkEvents.CHUNK_LOAD/UNLOAD`. Each system run copies the whole set into an
  `ArrayList`, `Collections.shuffle`s it with a fresh `java.util.Random(System.nanoTime())`,
  and takes the first `GROWTH_MAX_CHUNK_PER_TICK` (default 20).
- **Block selection**: per selected chunk, `GROWTH_BLOCKS_PER_CHUNK` (default **100**)
  random columns; each column resolved with
  `getTopPosition(Heightmap.MOTION_BLOCKING_NO_LEAVES)`. No full-chunk scans, no stored
  candidate positions - purely stochastic sampling, stateless between runs.
- **Per-candidate work**: light check, then chance rolls. Flower/bush/sapling/mushroom
  rolls call `findNearbyBlock`, which iterates `BlockPos.iterateOutwards` radius 4-5
  (~250-700 block reads) *per candidate that passes the roll*.

**Assessment** (what we keep / fix):
- Keep: own scheduler on world tick, loaded-chunk tracking from load/unload events,
  random column sampling via heightmap (no scans), round-robin between task types.
- Fix: the shuffle allocates and shuffles the *entire* loaded-chunk list every run
  (thousands of entries on a busy server) - replace with a persistent cursor
  (round-robin over a snapshot array). The `HashSet<ChunkPos>` is keyed globally, not
  per-world (a multiworld bug) - key per `ServerLevel`. Defaults (20 chunks x 100
  columns = 2 000 heightmap+state lookups per run) are aggressive - we go much lower and
  add a wall-clock nano budget as a hard stop. No `shouldTickBlocksAt` check - GB can
  grow vegetation in border chunks that vanilla wouldn't random-tick; we add it.

### 1.2 Path system (soil wear) - study only, not building yet

- **Movement tracking**: every system tick, for each player: take `blockPos.below()`;
  a `Map<UUID, BlockPos>` of last positions dedupes "standing still" (same pos -> mark
  `blockedMap` and skip). On a *new* position, if the block is grass/coarse dirt/dirt,
  increment a wear counter.
- **State**: one `Map<Long, Integer>` (`BlockPos.asLong()` -> step count) inside a single
  `PersistentState` (world saved data, codec-serialized as string->int map). Plus two
  transient maps: last player positions and `blockedMap` (positions stepped on this cycle,
  protected from decay). So: per-block counters, but in **one** saved-data record - no
  per-block NBT, no chunk attachments.
- **Thresholds**: grass -> coarse dirt at 30 steps, coarse dirt -> dirt path at 80.
- **Decay/recovery**: every `DECAY_INTERVAL` (1200 ticks) it walks the whole wear map,
  decrements each entry by `RECOVERY_RATE` (1), reverts path -> coarse at <=30 and
  coarse -> dirt at 0, then evicts the entry. Entries whose block is no longer a
  path-family block are evicted.
- **Weaknesses noted**: `markDirty()` is called every tick (forces save serialization of
  the whole map); the map is unbounded (no cap/LRU); decay walks the full map at once
  (spike proportional to map size); counts "new block position" rather than actual
  footsteps (swimming/riding over grass counts).

**Architecture implication for Fallow** (so we don't preclude it): the ecology scheduler
must allow *event-driven* tasks (player movement) in addition to *visit-driven* tasks
(chunk sampling), and the persistence story must allow one compact saved-data record per
task. Both are noted in `architecture.md`; nothing in Phase 1/2 blocks this.

### 1.3 Config approach

- GSON-serialized POJO of public fields at `config/gaiasbreath.json`; loaded once in
  `onInitialize`, written back with defaults if missing. **No live file reload** - the
  ModMenu screen mutates the static `CONFIG` instance in memory and calls `save()`, which
  is the only "reload" path. Screen is hand-rolled vanilla widgets.
- This matches the Shulker Pocket pattern almost exactly (GSON POJO in `config/<id>.json`
  + hand-rolled vanilla-widget ModMenu screen, commit-on-Done). Fallow follows the
  Shulker Pocket version of it; **no Cloth Config** (Shulker Pocket doesn't use it).
  Fallow adds `/fallow reload` to re-read the JSON on a live server, which GB lacks.

### 1.4 Sapling spread heuristic (Phase 3 input)

GB places a sapling if **any log block is within radius 5** of a grass candidate
(`BlockTags.LOGS`), mapping log tag -> sapling. There is **no tree-vs-player-build
distinction** - a log cabin sprouts saplings around it. Confirms the open design problem
is real and unsolved in GB; our leaves-attached + `persistent=false` heuristic (see
architecture doc) is strictly better and no more expensive.

## 2. Toolchain for 26.1.2 (checked 2026-06-12)

| Component | Shulker Pocket pins | Current upstream | Verdict |
|---|---|---|---|
| Minecraft | 26.1.2 node | 26.1.2 = **latest stable** (26.2 in RC) | target 26.1.2 |
| Fabric Loader | 0.19.2 | **0.19.3** (stable, meta.fabricmc.net) | bump for Fallow |
| Fabric API | 0.149.1+26.1.2 | **0.151.0+26.1.2** (Modrinth) | bump for Fallow |
| ModMenu | 18.0.0-beta.1 | 18.0.0-beta.1 (latest for 26.1) | same |
| fabric-loom | 1.16-SNAPSHOT via loom-back-compat 0.3 | loom **1.17.11** release | see below |
| Gradle | 9.4.1 wrapper | works | keep wrapper |
| JDK | 25 (Homebrew, keg-only) | 26.1 requires Java 25 | same |

Shulker Pocket's `gradle.properties` itself needs no bumps to keep working, but if its
pins are refreshed: loader 0.19.2->0.19.3, fabric_api 0.149.1->0.151.0 for the 26.1.2 node.

**Loom choice for Fallow**: single-version mod targeting only 26.1.x. We reuse the
Shulker Pocket scaffolding (Stonecutter + `loom-back-compat`) with a **single version
node** `26.1.2`. Rationale: it is the proven, already-cached setup on this machine for
this exact MC version, and adding older MC versions later is config-only (the same path
Shulker Pocket took). On 26.1+ loom-back-compat applies the plain no-remap fabric-loom;
`loomx.applyMojangMappings()` is a no-op because **26.1 ships unobfuscated** - official
Mojang names, no mappings, no remapping anywhere.

## 3. Variable day length - mechanism research

### 3.1 How existing mods do it (pre-26.1 Minecraft)

Two families:

1. **Own-counter mods** (TimeControl, studied in `reference/timecontrol`): force
   `doDaylightCycle` off, keep a custom time counter, compute
   `worldtime = f(customtime, multiplier)` and `setDayTime()` every tick; custom S2C
   packets keep client-side multipliers in sync; sleep handled by temporarily re-enabling
   the gamerule; `/time` intercepted via command events. Heavy, fights vanilla.
2. **Increment-modifier mods** (Better Days et al.): mixin into `ServerLevel#tickTime`'s
   `setDayTime(dayTime + 1)` and scale the increment with a fractional accumulator
   (`acc += rate; advance floor(acc)`). Lighter, but pre-26.1 clients tick `+1` locally
   between the every-20-tick time sync packets, so vanilla clients see a small snap each
   second unless the mod is also client-side.

### 3.2 What 26.1 changed - and why we need (almost) none of that

**26.1 replaced the `dayTime` long with a first-class, data-driven world-clock system**
(verified by decompiling `minecraft-common.jar` 26.1.2 with Vineflower,
`reference/mc-src/`):

- `net.minecraft.world.clock.ServerClockManager` (a `SavedData`, id
  `minecraft:world_clocks`) holds per-clock instances. Registry `Registries.WORLD_CLOCK`
  with data files `data/minecraft/world_clock/{overworld,the_end}.json`;
  `WorldClocks.OVERWORLD` is the overworld key. `ServerLevel#clockManager()` exposes it.
- Each `ClockInstance` is `{totalTicks, partialTick, rate, paused}` and ticks:
  `partialTick += rate; totalTicks += floor(partialTick)` - **vanilla implements the
  fractional-accumulator pattern natively, with a public `setRate(clock, float)`**.
  There is even a vanilla command: `/time set rate <float>`.
- `setRate`/`setTotalTicks`/`addTicks` broadcast `ClientboundSetTimePacket` carrying
  `ClockNetworkState{totalTicks, partialTick, rate}` - **clients advance their clocks at
  the synced rate natively**. No client mod, no judder, multiplayer-safe by construction.
- Sleep: `ServerLevel` calls `clockManager().moveToTimeMarker(clock, WAKE_UP_FROM_SLEEP)`
  (markers are data-driven via `data/minecraft/timeline/day.json`, `period_ticks: 24000`,
  `wake_up_from_sleep: 0`) - moves `totalTicks` to the next period multiple. Orthogonal
  to rate.
- `/time set day|noon|...` -> `setTotalTicks`; `/time query` reads `totalTicks`.
  Orthogonal to rate.
- Gamerule: `ADVANCE_TIME` (global) replaces `doDaylightCycle`; rate is ignored while
  it's off (network state reports rate 0 when paused).
- Villager schedules, bee hive times, sieges, firefly sounds etc. are keyframed *in clock
  ticks* on the `day` timeline - changing the **rate** stretches them in real time while
  keeping every tick-indexed relationship intact. The 24000-tick period is untouched.

### 3.3 Chosen mechanism for Fallow

A server tick handler (no mixin):

1. Compute the desired rate from the overworld clock phase and the current season:
   day phase `totalTicks % 24000  in  [0, 12000)` -> `rate = 0.5 / dayPortion(season)`;
   night phase -> `rate = 0.5 / (1 - dayPortion(season))`.
   With `dayPortion = 0.5` both rates are exactly `1.0` -> bit-identical vanilla.
   `1/r_day + 1/r_night = 2` always => one full cycle costs exactly 24000 real ticks.
2. Call `clockManager().setRate(OVERWORLD, rate)` **only when the desired value changes**
   (phase boundary or season change => ~2 writes per in-game day; each write costs one
   broadcast packet).
3. Kill switch: config `dayNight.enabled=false` -> restore rate 1.0 once and stand down.
4. Removability: rate persists in vanilla saved data, so on `SERVER_STOPPING` Fallow
   restores rate 1.0. If the server crashes mid-season, the leftover rate is fixable
   without Fallow: `/time set rate 1` (vanilla). No save corruption possible - we only
   ever write vanilla state through vanilla APIs.

**Failure modes / conflicts**: any other mod or datapack that sets clock rate would
last-writer-win against us at phase boundaries (same as two time mods pre-26.1; document
as incompatible-by-nature). Client render mods (Sodium etc.) read the synced clock and
are unaffected. Mods reading "time of day" use `Level#getOverworldClockTime()` /
`getDefaultClockTime()` and see a monotonic counter whose rate varies - same contract as
vanilla `/time set rate`, which any 26.1-compatible mod must already tolerate.
No conflict with the stated modlist philosophy -> proceeding without discussion.

## 4. Misc 26.1 API notes (verified against the decompiled jar)

- Bonemeal flower source: `GrassBlock#performBonemeal` ->
  `biome.getGenerationSettings().getBoneMealFeatures()` ->
  `Util.getRandom(features, random).place(level, generator, random, pos)`. This is the
  per-biome, datapack-respecting flower list Fallow uses for flower sprouting.
- Tall-grass upgrade: `DoublePlantBlock.placeAt(LevelAccessor, BlockState, BlockPos, int)`.
- `Level#getOverworldClockTime()` is the old `getDayTime()`; current day index =
  `totalTicks / 24000`.
- Saved data: `SavedData` + `SavedDataType` (codec-based), per-dimension storage via
  `ServerLevel#getDataStorage()` - pattern confirmed by `ServerClockManager.TYPE` itself.

## 5. Per-species sapling seasonality (Phase 3+ research)

Date: 2026-06-13. Question: should tree spread be gated to a season *per species* rather than
riding the one shared growth curve (spring 1.5 ... winter 0.05) that every other channel uses?
Multi-source study (USDA Woody Plant Seed Manual, USDA *Silvics of North America*, USDA FEIS,
plus peer-reviewed phenology journals); 25 claims adversarially verified, none refuted.

### 5.1 The governing principle - disperse in autumn, establish in spring

Most **temperate** trees **disperse seed in autumn but germinate/establish the following
spring**, after winter cold-moist stratification breaks seed dormancy. Verbatim for birch
(USDA *Silvics*, paper birch: "the bulk of paper birch regeneration becomes established during
the first growing season from seeds that fell the previous fall and winter"; "Germination
normally takes place in the spring following dispersal") and for the red/black oak group
(USDA WPSM: stratification "4 to 12 weeks at temperatures of 2 to 5 °C" before spring sowing).

This decouples two events. Fallow renders spread as a **sapling** - an *established seedling*,
not a fallen seed - so peaking the event in **spring (establishment)** is the faithful default
for temperate species, and the mod's existing shared spring-peaked curve is *already right* for
the majority. The work is therefore not "fix everything" but "carve out the species whose real
timing breaks the temperate pattern."

### 5.2 The documented exceptions (each independently sourced)

- **White-oak group** (Fallow's `oak`): acorns "germinate in the fall soon after dropping,
  requiring no pretreatment" (USDA *Silvics*, white oak) - dispersal **and** establishment both
  in **autumn**, no dormancy. The one temperate species whose visible event is autumn. (Red/black
  oaks keep the autumn-disperse/spring-establish split; sessile/`dark_oak` treated as red-oak-like.)
- **Spruce** (boreal *Picea*): cones "open to shed seeds during autumn and winter... released on
  warm days in late autumn and winter" (USDA WPSM/FEIS). Autumn-peaked but the **one
  winter-tolerant** conifer - a real winter trickle, not the shared curve's near-zero.
- **Red mangrove** (`mangrove_propagule`): **viviparous** - "produces seeds that germinate on
  the parent plant... The dispersal unit, a viviparous seedling, is called a propagule";
  "Germination without pretreatment may exceed 90 percent." No dormancy, no spring lag; propagules
  peak **Aug-Oct**. A warm-coastal species that does **not** experience a temperate winter -> a
  late-summer peak that is never winter-dead.
- **Jungle** (kapok/dipterocarp): everwet rainforest reproduces by **irregular supra-annual
  masting** ("flowering by the mast-fruiting Dipterocarpaceae... triggered by ENSO events such that
  seeds are dispersed at the end of ENSO droughts", Williamson & Ickes 2002) - **aseasonal** on a
  4-season calendar; a spring peak is actively wrong -> flat year-round (or, for a seasonal-tropics
  reading, a wet-season peak).
- **Savanna acacia**: dispersal is animal-gut-mediated (endozoochory) and germination tracks the
  **onset of rains**, not gravity seed-fall (Miller 1996). The in-game rain/growth season ~ spring,
  so it keeps the shared curve - but for that reason, not establishment-after-stratification.
  (Gut passage is *facilitative*, not obligate - un-ingested seeds still germinate ~19-40 %.)

### 5.3 Decision -> `saplings.types[].phenology`

Per-species seasonal gating is worth modeling. Implemented as an optional four-value vector
`{spring, summer, autumn, winter}` per tree type; absent => the species follows the shared curve.
Because seasonal multipliers > 1 only boost a probability that has headroom, the SAPLING channel
is **exempted from `SeasonalGrowthRates`** (applying the curve there *and* at the per-species rate
gate would saturate the boost) and the weight is applied in `SaplingSpreadTask` on the prolificacy
roll instead; override vectors peak at 1.0 so there is no clamp loss. Tightly-seasonal trees zero
their off-seasons (they simply do not spread then - like flowers not blooming in winter); the
genuinely year-round species (mangrove, jungle) stay non-zero in every season, since 0 there would
be ecologically wrong. Only the four breakers carry overrides - everything else inherits the
spring-peaked curve:

| sapling | dispersal | establishment | in-game peak | phenology {sp, su, au, wi} | conf. |
|---|---|---|---|---|---|
| oak (white-oak) | autumn | **autumn** (no dormancy) | **autumn** | 0 / 0 / 1.0 / 0 | high |
| spruce | autumn->winter | spring | **autumn + winter** | 0 / 0 / 1.0 / 0.45 | high |
| mangrove | yr-round, Aug-Oct | immediate (viviparous) | **late summer** | 0.55 / 1.0 / 0.9 / 0.5 | high |
| jungle | aseasonal mast | wet-season | **none (flat)** | 1.0 / 1.0 / 1.0 / 1.0 | high |
| birch | autumn | spring | spring (shared) | - | high |
| dark oak | autumn | spring | spring (shared) | - | low[1] |
| cherry | summer-autumn | spring | spring (shared) | - | low[1] |
| acacia | dry-season | onset of rains | spring (shared)[2] | - | med |
| pale oak | autumn | spring + masting | spring (shared) | - | low[1] |

[1] Not directly sourced; reasoned analogy from genus-level oak + the general temperate pattern.
[2] Maps to spring as "rains = growth season," not via cold-stratification.

Open / deferred: **masting** (irregular bumper years for oaks/`pale_oak`) is real (USDA WPSM:
"good crops... 1 year out of 3 or 4") but unmodeled - a candidate later enhancement (occasional
high-yield years vs a constant rate). Confirming the **sessile-oak (`dark_oak`) germination group**
(white-oak-like fall vs red-oak-like spring) is the one cheap fact that would upgrade a low-row.

### 5.4 Related invariant - dead bushes do not spread

Confirmed by audit while here: `minecraft:dead_bush` is excluded from the bush-creep copy set
(`VegetationSproutTask.BUSHES`), is never a dieback decay product (`DiebackTask` only steps plants
*down*: tall->short->air, grass_block->dirt), and is not among any block the ecology tasks place.
Dead bushes are *dead* - they never propagate. The only `dead_bush` blocks in the project are
static test-world decoration (`islands.py`). Kept as an explicit invariant.

### 5.5 Sources (verified)

- USDA WPSM, *Quercus*: <https://www.fs.usda.gov/nsl/Wpsm%202008/Q&R%20genera.pdf>
- USDA WPSM, *Picea*: <https://www.fs.usda.gov/nsl/Wpsm/Picea.pdf>
- USDA *Silvics*, white oak: <https://research.fs.usda.gov/silvics/white-oak>
- USDA *Silvics*, paper birch: <https://research.fs.usda.gov/silvics/paper-birch>
- USDA PSW, red mangrove (Allen): <https://www.fs.usda.gov/psw/publications/allen/psw_2002_allen009.pdf>
- Williamson & Ickes 2002 (*Oikos*), dipterocarp ENSO masting: <https://nsojournals.onlinelibrary.wiley.com/doi/abs/10.1034/j.1600-0706.2002.970317.x>
- Kurten, Bunyavejchewin & Davies 2018 (*J. Ecology*), seasonal-tropics phenology: <https://besjournals.onlinelibrary.wiley.com/doi/10.1111/1365-2745.12858>
- Miller 1996 (*J. Tropical Ecology*), acacia endozoochory: <https://www.cambridge.org/core/journals/journal-of-tropical-ecology/article/abs/dispersal-of-acacia-seeds-by-ungulates-and-ostriches-in-an-african-savanna/4941148A8BCBEB27B465A6B238EAD158>

## 6. Per-biome seasonality amplitude (Phase 3+ design)

The same climate gradient that makes jungle trees aseasonal (section 5.2: everwet tropics reproduce on
irregular supra-annual cues, not an annual calendar) generalizes to *all* vegetation growth, so
the season curve's strength is biome-scaled by one scalar `k` (`vegetation.biomeSeasonality`;
mechanism in architecture.md / configuration.md):

- **Tropics (k ~ 0.2)** - wet tropical forest has near-continuous growth and no temperate winter;
  growth and litterfall vary little across the year. Jungle/mangrove/warm-swamp never get a dead
  season.
- **Subtropics / savanna / arid (k ~ 0.3-0.6)** - productivity tracks a wet/dry cycle rather than
  a cold winter; modest seasonal swing, no hard freeze.
- **Temperate (k = 1.0, the baseline)** - the documented disperse-autumn/grow-spring/dormant-
  winter rhythm of section 5.1.
- **Boreal / alpine / snowy (k ~ 1.15-1.3)** - short, intense growing season bracketed by a long
  dormant winter where growth genuinely stops and dieback peaks; the amplified curve drives winter
  growth to zero and the spring flush above the temperate peak, matching boreal phenology.

This is a design parametrization of a well-established climate->phenology gradient, not a
per-species literature claim, so it ships uncited beyond the section 5 tropical anchor.

## 7. Per-bush schedules (Phase 3+ design)

Bush-creep copies a *specific* nearby bush block (the species is known at copy time, unlike
flowers, whose species the biome bonemeal palette picks), so the three vanilla bushes each get
their own schedule (`vegetation.bushSeasons`), mechanically identical to saplings' `phenology`
(shared `SeasonWeights`; BUSH channel left season-exempt to avoid double-counting):

- **`sweet_berry_bush` -> autumn** `{0.3, 0.6, 1.0, 0.0}`. A boreal/subarctic berry (the
  lingonberry/bog-bilberry niche, *Vaccinium*): fruit ripens late-summer into autumn, and the
  plant is dormant under a frozen winter - so spread builds to an autumn peak and zeroes in winter.
- **`firefly_bush` -> summer** `{0.4, 1.0, 0.5, 0.15}`. No real counterpart; grounded in the
  block's Minecraft flavor (fireflies are a warm-evening/summer phenomenon, and it generates in
  warm swamp/mangrove edges), so it peaks in summer and never hard-freezes.
- **`bush` -> spring** `{1.0, 0.7, 0.3, 0.0}`. A generic temperate deciduous shrub: spring
  leaf-out/establishment peak, tapering to a dormant winter.

Flowers are deliberately **not** put on per-species schedules: the mod doesn't choose the flower
(it draws from the biome's vanilla bonemeal feature list, which keeps it datapack-proof and
biome-correct), the species is only sometimes extractable pre-placement, and flowers already have
a seasonal lifecycle (FlowerWilt) plus the section 6 biome-seasonality. The cost/benefit doesn't justify
intercepting the biome palette.

## 8. Seasonal precipitation - the temperature lever (Phase 3+ design)

Real precipitation *type* is a function of temperature: water falls as snow below ~0 °C, rain
above. Minecraft already encodes this - a biome's temperature drives rain-vs-snow, snow cover, and
ice. So seasonal precipitation needs only a seasonal *temperature*: shift every biome's effective
temperature down in winter and up in summer and vanilla's own snow/rain/freeze logic follows (the
"Serene Seasons lever"). Fallow does it with one common mixin on `Biome.getTemperature` - the only
gameplay mixin (vanilla exposes no event/setter for a per-call temperature); mechanism in
architecture.md section Seasonal precipitation.

Why a *uniform* offset suffices: biomes already differ by base temperature, so one seasonal delta
lands each in the right band - a winter -0.7 snows temperate biomes (plains ~0.8 -> 0.1) but not
the tropics (jungle ~0.95 -> 0.25), mirroring the real latitude gradient (temperate zones freeze in
winter; the wet tropics don't). Hot arid biomes carry `has_precipitation=false` and never
precipitate on temperature alone - correct for deserts, but savanna has a real monsoon, so Fallow
flips its precipitation flag on (a load-time biome modification) for a warm rainy season. The
default offsets (winter -0.7, summer +0.2) are tuned to vanilla's 0.15 snow threshold, not a cited
climate dataset.
