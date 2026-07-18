# Fallow

[![Modrinth](https://img.shields.io/modrinth/v/fallow?logo=modrinth&color=00AF5C&label=Modrinth)](https://modrinth.com/mod/fallow)
[![Downloads](https://img.shields.io/modrinth/dt/fallow?logo=modrinth&color=00AF5C&label=downloads)](https://modrinth.com/mod/fallow)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

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

## Warning: this mod changes your world

Fallow is a slow, world-altering mod. While chunks are loaded it permanently edits vanilla
blocks over time: grass and flowers spread, dirt paths wear into the ground underfoot, trees
and bamboo spread, snow accumulates and melts, water freezes and thaws, and plants left in the
dark decay toward dirt. The base mod adds no new blocks and leaves no trace if removed, but the
changes it makes to existing blocks are permanent and can be considered destructive. The optional
crop layer (`crops.enabled`) is an exception: planted crop blocks are real new blocks that do not
vanish if the mod is removed. So:

- **Fallow ships DISABLED by default.** Nothing in your world changes until you turn it on.
- **Back up any world you care about before enabling it.**
- To enable: set `"enabled": true` in `config/fallow.json` and run `/fallow reload` (or relaunch).
- The first time you join a world with Fallow installed you will get a one-time in-game notice
  of this, whether the mod is on or off.

## Highlights

- **Server-side gameplay, mixins held to a "no other hook exists" bar.** All simulation is
  Fabric lifecycle events + vanilla APIs. Day length uses the vanilla 26.1 world-clock rate (the
  `/time set rate` mechanism), so it's multiplayer-safe with native client sync, and beds and
  `/time set` just work. One gameplay mixin exists (the seasonal-temperature lever that makes
  vanilla snow in winter and rain in summer); the other five are client-side cosmetics for the
  seasonal tints, particles, and the Season Clock's dial.
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
- **Crops & forage** (opt-in, `crops.enabled`, off by default) - 14 farmland crops (including a
  rice paddy mechanic and cold-hardy winter tricklers leek and turnip), corn as a double-height
  stalk, 4 trellis climbers (peas, cucumber, grapes, hops), 3 berry bushes, squash spreading from
  a stem like pumpkin, 2 tree fruits (cherry and plum) dropping beneath natural canopy in season,
  and 11 wild forage plants spreading via the ecology scheduler. 15 seeds drop from breaking
  grass. Note: planted crop blocks are real new blocks and do not uninstall cleanly if the mod is
  removed - see the warning in [docs/features.md](docs/features.md).

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
