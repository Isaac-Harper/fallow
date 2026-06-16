# Fallow

Seasons with a living ecology, for Fabric on Minecraft 26.1.x.

The world slowly fills in while you play: grass sprouts on bare ground and grows tall,
biome-appropriate flowers bloom (drawn from the same per-biome list bonemeal uses, so
datapacked biomes work), and bushes creep outward from where worldgen planted them. Forests
creep too: real trees - and only real trees, never log builds - seed matching saplings
nearby, old-growth canopy develops podzol forest floors, and shorelines sprout sugar cane
and seagrass. Vegetation sealed away from light decays back to dirt, and well-walked routes
wear into visible trails (grass -> coarse dirt -> path) that recover when abandoned. Four
seasons cycle on configurable length: spring boosts growth, winter nearly stops it and
accelerates dieback, the day/night split shifts with the season - long summer days, long
winter nights - while the full cycle stays exactly 24000 ticks, and (client-side) the world
itself turns: vivid spring greens, straw-and-orange autumn, pale winter, day by day.

The cycle has teeth: grass that packs too tight thins out - tall grass back to short, short
grass gone - hardest in winter, and flowers bloom in spring then wilt away through fall.

Tunable per biome and per tree: both density caps *and* growth rates scale by biome id or
tag (lush jungles fill in faster and fuller than deserts), and each tree type has its own
sapling spread rate and radius - modded trees follow their registry names automatically.

Built for invisibility while you watch: changes are noticeable when you come back to a
place, never jarring while you stand in it, and growth pauses at night like vanilla grass
spread. The whole simulation runs on a tick-budgeted scheduler measured in tens of
microseconds per tick at defaults, with hard caps no matter the config.

## Highlights

- **Server-side gameplay, zero gameplay mixins.** All simulation is Fabric lifecycle events +
  vanilla APIs. Day length uses the vanilla 26.1 world-clock rate (the `/time set rate`
  mechanism), so it's multiplayer-safe with native client sync, and beds and `/time set` just
  work. The mod's *only* mixin is a client-side cosmetic one for the seasonal foliage tint.
- **Existing worlds welcome.** No new blocks, no world reset. The entire persistent footprint
  is two small saved-data records (season state + per-dimension trail wear); remove the mod
  and nothing breaks.
- **Client optional.** A vanilla client on a Fallow server plays fine; install the mod
  client-side for the seasonal foliage tint and the Mod Menu config screen.
- **Configurable.** `config/fallow.json` (every knob - see
  [docs/configuration.md](docs/configuration.md)), Mod Menu screen (the common toggles),
  `/fallow reload` on live servers. Every probability, season multiplier, and day portion is
  independent; a `dayNight.enabled` kill switch keeps growth seasons while leaving vanilla
  time untouched, and 50/50 portions are bit-identical to vanilla. Per-task toggles, per-biome
  growth/density, and per-tree-species sapling tuning.

## Features

Each is an independently toggleable ecology task on a shared tick-budgeted scheduler
(except trails, which is event-driven), with probabilities scaled by season and - for growth -
by biome:

- **Vegetation sprouting** - short grass, tall grass, biome flowers (bonemeal list), bushes.
- **Sapling propagation** - verified natural trees seed matching saplings; per-species rate,
  radius, and canopy density.
- **Leaf litter** - `leaf_litter` ground cover + podzol floors under dense natural canopy.
- **Shoreline creep** - sugar cane on banks, seagrass in shallow water.
- **Bamboo spread** - bamboo creeps clonally from existing stands, so jungle groves expand.
- **Vegetation dieback** - plants sealed in darkness revert toward dirt.
- **Grass overcrowding** - packed grass thins (tall -> short -> gone), hardest in winter.
- **Flower lifecycle** - flowers bloom in spring, wilt away through fall.
- **Trails** - footsteps wear grass -> coarse dirt -> path; recover when abandoned.
- **Seasons** - four-season cycle; per-season growth/decay multipliers; a public read API.
- **Seasonal day length** - daylight share shifts by season; cycle stays 24000 ticks.
- **Seasonal foliage tint** (client) - grass/leaves recolor by season.
- **Season Clock** (item) - a copper-and-redstone clock that shows the current season instead of
  the time of day: four pips at the compass points with a redstone hand that sweeps round to the
  active season. Crafted like a clock but with copper.

## Commands

- `/fallow season` - current season, day, growth multiplier, daylight share
- `/fallow season set <season> [day]` (op) - for testing and admins
- `/fallow stats` - ecology scheduler timings and placement counts since last query
- `/fallow trails reset` (op) - clear this dimension's trail wear
- `/fallow reload` (op) - re-read the JSON config

## API

`dev.isaac.fallow.api` - read-only season surface for other mods:

- `FallowSeasons.get(server)` -> `SeasonInfo` (a record: `season()`, `dayInSeason()`,
  `daysPerSeason()`); plus `season(server)`, `enabled()`, `growthMultiplier(server)`.
- `Season` - enum `SPRING`/`SUMMER`/`AUTUMN`/`WINTER`, with a `Codec`, `next()`, `id()`, and
  `byId(String)`.

## Documentation

- [docs/configuration.md](docs/configuration.md) - every config field, default, and range.
- [docs/architecture.md](docs/architecture.md) - how every system works internally.
- [docs/research.md](docs/research.md) - Phase 0 research (Gaia's Breath, 26.1 world clocks).

## Development

JDK 25. `./gradlew build` (compiles + runs the JUnit suite), `runClient`, `runServer`.
Stonecutter single-node (26.1.2), Fabric Loom via loom-back-compat, split `main`/`client`
source sets. See [docs/architecture.md](docs/architecture.md).
