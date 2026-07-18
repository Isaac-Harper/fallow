# Fallow - crops (design proposal)

Status: **Phases C1, C2, C3 extended roster, and C3 preservation layer implemented** (2026-07-18).
The full crop roster and preservation layer are shipped and live behind `crops.enabled` (default `false`):

- **C1:** Turnip, cabbage, onion, cherry, strawberry, peas; the trellis block; `ForageSpreadTask`
  with wild onion and strawberry bush; season-gated farmland growth with winter kill; legume
  nitrogen fixing; and the `diet/*` item tags.
- **C2:** Rice (paddy mechanic, `RicePaddy`), corn (double-height `CornCropBlock`), tomato,
  cucumber on the trellis.
- **C3 roster:** Leek, barley, rye, oat, garlic, radish, parsnip, pepper, flax; grapes and hops
  on the trellis; raspberry and blackberry bushes; squash (stem crop); plum (fruiting.types);
  eleven wild forage plants (wild rice, wild grape vine, wild hops, chanterelle, mint, sage,
  thyme, ramsons, sorrel, plus existing wild onion and strawberry bush).
- **C3 preservation layer:** Jam (fruit group, crafted with `fallow:jam_fruits` + sugar +
  glass bottle), pickles (vegetable group, crafted with cucumbers + glass bottle), raisins
  (fruit group, grapes dried by furnace/smoker/campfire), and dried chanterelles (fungi group,
  chanterelles dried by furnace/smoker/campfire). Jar items (jam, pickles) return a glass
  bottle on eating via `usingConvertsTo`; dried items eat fast like dried kelp.

The diet mechanic that consumes the `fallow:diet/*` tags is also implemented ([diet.md](diet.md)).
Still future: the diet micro-flavor phase and masting (bumper seed years for oaks).

The document below is the design record as written; see [features.md](features.md) for the
plain-language tour and [configuration.md](configuration.md) for the full config reference.
The open questions this proposal shipped with are resolved ([section 10](#10-decisions), 2026-07-18).

Fallow today adds no blocks (only the `season_clock` item) and leaves no trace if removed. Crops
break that promise: they are the first real *content* Fallow ships. That is a deliberate shift, so
the whole layer is gated behind `crops.enabled` (default `false`), sits under the existing master
`enabled` switch, and is documented as content that does not fully uninstall cleanly (planted crops
become unknown blocks if the mod is pulled). See [Philosophy](#2-where-crops-sit-in-fallow).

For what Fallow does today see [features.md](features.md); for the engine see
[architecture.md](architecture.md); for knobs see [configuration.md](configuration.md).

---

## 1. Design pillars

A candidate crop earns a slot only if it satisfies most of these. They are what make a crop feel
like it belongs in Fallow rather than in a generic farming mod bolted alongside it.

- **Seasonal, not year-round.** Every crop has a growing window and a harvest window driven by the
  existing `Season` cycle. Plant out of season and it stalls. This is the differentiator no other
  crop mod has: crops that respect the calendar.
- **Biome-native.** Crops belong to specific biomes through the same tag-driven tuning Fallow
  already uses for grass, flowers, and trees (`BiomeTuning`). A crop should have a home.
- **Vanilla-faithful growth idiom.** Reuse existing block behaviors (farmland crop, berry bush,
  leaf-drop, stem, tall stalk) so crops look like they shipped with the game and stay cheap to
  build. See the [idiom catalog](#5-growth-idioms).
- **Emergent where possible.** Fallow's core magic is "the world fills in while you play." The best
  crops appear first as *wild* plants spread by the ecology engine, then get domesticated on
  farmland. This unifies farming with the sim instead of importing a farming mod.
- **Fills a real gap** versus vanilla, and ideally lands in a thin diet group ([the gap
  map](#3-the-vanilla-food-gap)).

---

## 2. Where crops sit in Fallow

**Opt-in, twice.** The master `enabled` flag gates all of Fallow. Crops add a second gate,
`crops.enabled`, so a player who wants only the ecology sim keeps the "no new blocks, removes
clean" guarantee. When `crops.enabled` is false: no forage tasks tick, no wild crops appear, and
the crop items/blocks are still registered (so existing worlds do not corrupt) but nothing places
them.

**The removability caveat.** Ecology edits are reversible: pull Fallow and the world is vanilla
blocks. Crop blocks are not: a planted turnip is a `fallow:turnip` block that becomes "unknown" if
the mod is removed. This is normal for any content mod, but it must be stated plainly in
`features.md` because it contradicts Fallow's current selling point. Decided: crops are simply part
of the mod, not a separately-advertised add-on, and there is no second first-join notice (the
existing one-time notice already covers Fallow altering the world). The caveat lives in a dedicated
`features.md` note and a comment beside `crops.enabled` in the config.

**Two growth paths, on purpose.** Fallow's engine is built for *ambient, unattended* world change.
Player farming is the opposite: deliberate and predictable. So crops use both mechanisms, each
where it fits:

- **Player-planted crops grow on the vanilla random tick**, like wheat, but **season-gated** (see
  [season-gated growth](#82-season-gated-farmland-growth)). Predictable, player-facing, no scheduler
  load.
- **Wild crops emerge through a new `EcologyTask`** (`ForageSpreadTask`), using the exact
  `setBlock` idiom `VegetationSproutTask` already uses. Ambient, budgeted, biome-and-season aware.

---

## 3. The vanilla food gap

Vanilla food, grouped, is lopsided. New crops should aim at the thin groups.

| Group | Vanilla has | Missing (opportunity) |
|---|---|---|
| **Grain** | wheat | rice, maize, barley, rye/oats |
| **Vegetable** | carrot, potato, beetroot, pumpkin | leafy greens, alliums (onion/garlic/leek), turnip/radish, nightshades (tomato/pepper), squash, cucumber |
| **Fruit** | apple (oak), melon, sweet + glow berry | stone fruit (cherry), strawberry, grape |
| **Plant protein** | *nothing* | legumes (peas, beans) |
| **Fungi** | red + brown mushroom | forest mushrooms (chanterelle) |
| **Sugar / oil** | cane, honey, beetroot | sunflower seed/oil |

The four biggest holes are **plant protein (legumes), leafy greens, alliums, and non-wheat grains.**
Aim there first.

---

## 4. The seasonal calendar (the spine)

This is the structural idea the whole roster hangs on. Organize crops so each season has a signature
harvest, which makes "eat seasonally" legible and gives the seasons real stakes for a future diet
feature. Seasons come from `FallowSeasons.season(server)`; each crop declares a `plantSeason` and a
`harvestSeason` window.

| Season | Signature crops | Character |
|---|---|---|
| **Spring** | peas, leafy greens, radish, strawberry, cherry (bloom sets fruit) | fresh, fast, early |
| **Summer** | rice, maize, tomato, beans, cucumber, pepper | warm-season fruiting and grain |
| **Autumn** | turnip, onion, garlic, squash, grapes, sunflower, pumpkin (vanilla) | the harvest, the storables |
| **Winter** | leek and turnip trickle; every other crop left standing dies | scarcity: live on stores, preserves, and saved seed |

Winter is the point, not a gap. Because Fallow already nearly stops growth and accelerates dieback
in winter (`SeasonalGrowthRates`, winter growth 0.05, decay 3.0), winter naturally starves fresh
variety. That is exactly where a diet feature earns drama: variety in winter has to come from
**preserved** food ([preservation](#73-winter-scarcity-and-preservation)). And winter is not just a
stall: non-hardy crops left standing die in the field ([winter kill](#74-winter-kill-and-seed-saving)),
so seed has to be saved and stores laid in before the season turns.

---

## 5. Growth idioms

The reusable behaviors. Each crop picks one. Idioms A-F are cheap (reuse vanilla block classes or a
single `EcologyTask`); G-H are the two that need genuinely new mechanics.

| Idiom | Vanilla basis | New work | Used by |
|---|---|---|---|
| **A. Farmland crop** | `CropBlock` (wheat/carrot), age 0-7 | block + model + season gate | turnip, onion, garlic, leek, cabbage, lettuce, radish, tomato, pepper, barley |
| **B. Berry bush** | `SweetBerryBushBlock`, age 0-3, right-click | block + model | strawberry, (raspberry/blackberry if wanted) |
| **C. Leaf-drop fruit** | Fallow's own `FruitDropTask` (oak apples in autumn) | `fruiting.types` entry + fruit item | cherry, plum |
| **D. Tall stalk** | double `TallGrassBlock` / sugar cane vertical | 2-block crop block | maize |
| **E. Stem crop** | `StemBlock` (pumpkin/melon) | stem + fruit block | squash, gourd |
| **F. Paddy** | farmland + water proximity | growth gate on adjacent water | rice |
| **G. Trellis climber** | none (new) | trellis block + climbing crop | beans, peas, hops, grapes, cucumber |
| **H. Vanilla-plant extension** | loot/event tweak, no new growth | tag + loot/event | sunflower seeds, oak apples, wild-grain from tall grass |

**Implementation note.** Idiom A crops subclass the vanilla crop block and grow on the **vanilla
random tick**, then gate on season (a crop tick that returns early when
`FallowSeasons.growthMultiplier(server)` is below a per-crop threshold, or scales its grow chance by
it). This keeps farming predictable and off the `EcologyScheduler`. Idioms C and H, and all *wild*
spread, run as `EcologyTask`s on the scheduler because they are ambient world edits.

---

## 6. Crop roster

Each crop lists: **idiom** / **diet group** / **plant to harvest season** / **home biomes (tags)** /
**outputs** / **hook**. Biome homes reuse `BiomeTuning` id-or-tag resolution, so they are config
overridable and default to neutral in unlisted biomes.

### 6.1 Tier 1 (recommended v1)

Cheap, vanilla-faithful, and they cover every diet group plus a signature per season.

**Turnip** - the thematic centerpiece (fallow fields historically grew turnips in crop rotation, so
it is literally on-name).
- Idiom A (farmland crop). Diet: vegetable. Autumn crop, cold-hardy (grows down into early winter at
  reduced rate).
- Home: `#minecraft:is_taiga`, cold plains, meadow.
- Outputs: turnip (food), turnip seeds. Sowable on coarse dirt and rooted dirt, not just farmland
  (ties to the "poor-ground rotation" identity).
- Hook: the crop that reads Fallow's fallow-ground theme. Pairs with legumes for a rotation story.

**Cabbage (leafy green)** - fills the biggest vegetable void.
- Idiom A. Diet: vegetable. Spring and autumn (cool-season, stalls in high summer heat and winter).
- Home: plains, meadow, forest clearings.
- Outputs: cabbage head. Composes with Farmer's Delight Refabricated, which already uses cabbage, if
  the player runs both.
- Hook: cool-season stall gives summer a real downside for one crop, reinforcing seasonality.

**Onion** - universal allium and cooking base.
- Idiom A. Diet: vegetable. Cool season (spring-planted, autumn harvest).
- Home: plains, savanna.
- Outputs: onion. Ships a **wild onion** forage variant that spreads in forests via
  `ForageSpreadTask` and can be replanted (the wild-to-domesticated entry, [see 7.1](#71-wild-to-domesticated)).
- Hook: first crop to demonstrate the forage pipeline.

**Cherry** - stone fruit, and the most vanilla-faithful crop possible.
- Idiom C (leaf-drop). Diet: fruit. Blossom in spring, fruit ripens late spring to early summer.
- Home: cherry grove (the biome exists and its trees currently drop nothing).
- Outputs: cherries. Zero new growth code, literally: the existing fruiting system
  (`FruitDropTask`) already drops apples on open ground under natural oak canopy in autumn, and its
  `fruiting.types` map is open. Cherry is one new entry (`minecraft:cherry_leaves` ->
  `fallow:cherries`, season `spring`, the calendar's spring signature fruit) plus the cherry item.
  Decided over a fruiting-leaf blockstate: ripe fruit drops beneath the canopy in season, like
  apples.
- Hook: extends an existing vanilla tree that is currently purely decorative. Strong flagship.

**Strawberry** - bush fruit, spreads itself.
- Idiom B (berry bush, no walk-through damage). Diet: fruit. Spring.
- Home: meadow, forest, plains.
- Outputs: strawberries. Spreads as a wild plant through the same bush logic Fallow already runs, so
  it appears naturally and is then transplantable.
- Hook: second forage demonstrator, and a spring counterpart to vanilla's autumn sweet berries.

**Peas (legume)** - the mechanically richest crop, and the only plant protein.
- Idiom G (climbs a trellis; the trellis block ships in v1 alongside it). Diet: **protein**.
  Spring.
- Home: plains, forest edges.
- Outputs: peas, pea seeds.
- Hook: **nitrogen fixing.** A harvested/mature legume enriches the soil under and beside it (coarse
  dirt to dirt, or a temporary growth bonus to adjacent crops), tying directly into Fallow's soil
  sim. This is the one crop that touches the ecology engine mechanically, and it makes the
  turnip-plus-legume rotation fantasy real. [See 7.2](#72-legume-nitrogen-fixing).

### 6.2 Tier 2 (v2, strong identity, more work)

**Rice** - wetland grain, fills the grain gap with a distinct mechanic.
- Idiom F (paddy). Diet: grain. Summer.
- Home: swamp, mangrove, river, warm wetlands.
- Outputs: rice, rice seeds. Grows only on farmland with water within N blocks (config `paddyRange`).
- Hook: the only crop tied to water, gives wetlands an agricultural purpose.

**Maize (corn)** - iconic silhouette, second grain.
- Idiom D (2-block tall stalk). Diet: grain. Summer.
- Home: plains, savanna, sunflower plains.
- Outputs: corn, corn seeds. Enables cornbread and animal feed later.
- Hook: tall crop reads clearly across a field; a strong visual season marker.

**Tomato** - warm-season nightshade.
- Idiom A (with a support/stake preferred). Diet: vegetable. Summer.
- Home: savanna, warm and arid edges.
- Outputs: tomato. Composes with FD if present.

**Cucumber** - climbing summer vegetable, preservation feedstock.
- Idiom G (trellis, shipped in v1 by peas). Diet: vegetable. Summer.
- Home: plains, warm edges.
- Outputs: cucumber, which pickles for winter variety ([preservation](#73-winter-scarcity-and-preservation)).

### 6.3 Extended roster (the "and some more")

Additional candidates worth having on the board, grouped by what they add. Pull from here to round
out seasons, biomes, or a brewing/preservation sub-theme.

**More grains and brewing**
- **Barley** - Idiom A, grain, cool-season, cold-hardy. Home: plains, cold plains, taiga edges.
  Anchors a **brewing line** (malt, later beer) alongside hops. Storable dry grain.
- **Rye / oats** - Idiom A, grain, cold-climate filler. Home: taiga, cold plains. Cheap variety,
  extends grain into biomes rice and maize avoid.
- **Hops** - Idiom G (tall trellis climber), not a food, brewing companion to barley. Home: forest
  edges, river. Autumn harvest.

**More alliums and roots**
- **Garlic** - Idiom A, vegetable, cold-season. Optional fun hook: a hung garlic block or eating it
  briefly wards a specific mob type (design toggle, off by default to stay vanilla-faithful).
- **Leek** - Idiom A, vegetable, very cold-hardy (latest autumn harvest). Home: taiga, cold plains.
- **Radish / parsnip** - Idiom A, fast cold-season roots, spring and autumn. Trivial variety filler.

**More fruit**
- **Grapes** - Idiom G (trellis), fruit, autumn. Home: savanna slopes, meadow, warm hills. Sets up a
  preservation and (later) wine line (raisins, must).
- **Apple** - already shipped: `FruitDropTask` drops apples under natural oak and dark oak in
  autumn. Listed only because it anchors idiom C; no new work.
- **Plum** - Idiom C, stone fruit on flowering/oak-adjacent trees, autumn. Optional second leaf-drop
  fruit if cherry lands well.
- **Raspberry / blackberry** - Idiom B, fruit, summer. Ships on its own merits regardless of what
  else is installed; shared item tags let it compose with More Berries rather than conflict.

**More vegetables and gourds**
- **Squash / gourd** - Idiom E (stem crop, like pumpkin), vegetable, autumn. Winter squash is a
  natural **storable** (long shelf life feeds winter variety without pickling).
- **Chili / pepper** - Idiom A, vegetable, summer, warm biomes. Optional: eating grants a brief
  cold-resistance flavor effect (pairs with a future temperature/thirst layer or Homeostatic).

**Oil, fiber, foraged**
- **Sunflower (vanilla extension)** - Idiom H. Vanilla already has the sunflower plant; extend it to
  drop **sunflower seeds** (food, and an oil/fat line later). Late summer. Faithful, like cherry, no
  new growth block.
- **Flax** - Idiom A, not food. Fiber crop (yields string/linen), blue-flowering, spreads via the
  flower engine. Medieval-agriculture flavor; gives farming a non-food reason and a textile line.
- **Forest mushrooms (chanterelle etc.)** - Idiom H forage, fungi, autumn. Spread in forests via
  `ForageSpreadTask`, extending vanilla's two mushrooms with a seasonal gathered one.
- **Wild herbs** (mint, sage, thyme, wild garlic/ramsons, sorrel) - pure foragables spread by the
  flower engine, seasonal, minor diet weight as seasonings/teas. Mint enables a tea/drink hook if a
  thirst layer ever lands.

---

## 7. Cross-cutting systems

The mechanics that make the roster more than a pile of blocks. Design these in from the start even
if only some crops use them at first.

### 7.1 Wild to domesticated

The single biggest thing that makes crops feel native to Fallow rather than imported. Do **not** ship
a farming mod. Let the strongest crops appear first as **wild plants** placed by a new
`ForageSpreadTask implements EcologyTask`, using the same candidate-sampling and `setBlock` idiom as
`VegetationSproutTask`:

- The task samples surface positions in loaded chunks (heightmap), checks biome (`level.getBiome`)
  against the crop's home tags, checks season, rolls a `GrowthChannel.FORAGE` chance through the
  layered `GrowthRateProvider` (config x season x biome), and places the wild variant.
- Wild variants (wild onion, wild strawberry, wild grain in tall grass, forest mushrooms, herbs) are
  harvestable in the world and yield seeds/starts.
- The player transplants seeds onto farmland to get the domesticated, higher-yield crop.

Entry point crops for v1: **wild onion** and **strawberry**. Both already have a home idiom.

### 7.2 Legume nitrogen fixing

The one crop-to-ecology mechanical hook. When a legume (peas, beans) reaches maturity or is
harvested, it enriches soil in a small radius:

- Coarse dirt / rooted dirt under and adjacent converts back toward dirt (reusing the same
  `setBlock` mutation style as `LeafLitterTask`), or
- Adjacent farmland gets a short-lived "enriched" tag that raises the next crop's grow chance.

This makes the historical **turnip-plus-legume rotation** a real, rewarding loop and gives Fallow's
soil sim a reason to care about what the player plants. Config-gated (`crops.legumes.fixNitrogen`).

### 7.3 Winter scarcity and preservation

Implemented 2026-07-18. Because winter nearly stops growth, fresh variety collapses in winter by design.
That is the hook for the **diet** feature, and the **preservation** layer is what lets players carry
diet variety through it:

- Storables (winter squash, dried grain, onions, garlic) keep their diet value through winter
  as-is.
- Preserved goods crafted in warm seasons and consumed in winter:
  - **Jam** (fruit group) - two `fallow:jam_fruits` items (strawberries, cherries, raspberries,
    blackberries, plum, grapes, sweet berries, apple, or glow berries) + sugar + glass bottle.
    Eating the jar returns the glass bottle via `usingConvertsTo`; uses the drink animation;
    stacks to 16.
  - **Pickles** (vegetable group) - two cucumbers + glass bottle. Same jar-return mechanism.
  - **Raisins** (fruit group) - grapes dried in a furnace, smoker, or over a campfire.
    Fast-eat (0.8 s), like dried kelp.
  - **Dried chanterelles** (fungi group) - chanterelles dried in a furnace, smoker, or over a
    campfire. Same fast-eat.
- This turns autumn into a "put up food for winter" phase, which is exactly the agricultural rhythm
  Fallow's seasons imply. The `fallow:jam_fruits` tag is open for datapack extension.

### 7.4 Winter kill and seed saving

Winter is a hard stop with named exceptions, not a general slowdown. Decided so autumn has real
stakes:

- **Non-hardy crops die in the field.** When winter arrives, a standing crop that is not cold-hardy
  withers on its next random tick: the block turns to a dead, unharvestable state and the yield is
  gone. Merely stalling would let a player park a field over winter and harvest in spring; killing
  it makes the autumn harvest a deadline.
- **Cold-hardy crops trickle.** Turnip and leek (and later rye/barley as overwinter grains) keep
  growing into winter at a heavily reduced rate - the only fresh food the season offers.
- **Seed saving matters.** Harvest returns more seed than was planted, and seeds are items, safe in
  a chest. Losing a field to winter costs the crop, not the line - but only if seed was kept back.
  Plant in season, harvest before the freeze, save seed for spring: the loop the whole calendar
  teaches.
- Config: `crops.winterKill` (default `true`) and a per-crop `hardy` flag.

### 7.5 Diet tags (feeding a future diet feature)

Crops declare their diet group by **item tag**, mirroring how Fallow already resolves biomes by tag.
This keeps the diet feature (whenever it lands) fully data-driven and lets vanilla, these crops, and
third-party food (FD, Ube's, More Berries) all count with zero hardcoding.

```
fallow:diet/grain      wheat, bread, rice, corn, barley, ...
fallow:diet/vegetable  carrot, potato, beetroot, turnip, onion, cabbage, tomato, ...
fallow:diet/fruit      apple, melon, sweet_berries, cherry, strawberry, grapes, ...
fallow:diet/protein    (meats + eggs via vanilla tags) + peas, beans
fallow:diet/fungi      red_mushroom, brown_mushroom, chanterelle, ...
fallow:diet/sugar_oil  sugar, honey, sunflower_seeds, ...
```

A diet feature would track which groups a player has eaten recently and grant/withhold a small
bonus based on variety. The crop layer only ships the tags and the food items; the mechanic is a
separate module.

---

## 8. Integration with Fallow architecture

Concrete wiring against the classes that exist today.

### 8.1 New ecology tasks

Register in `Fallow.onInitialize()` next to `VegetationSproutTask`, each config-gated so
`/fallow reload` applies live:

```java
EcologyScheduler.registerTask(new ForageSpreadTask(GROWTH_RATES));   // wild crops emerge
// cherry/plum fruit drops ride the existing FruitDropTask via fruiting.types - no new task
// legume soil enrichment can be folded into crop block ticks rather than a scheduler task
```

Each implements `EcologyTask`:

```java
String id();                                                 // "fallow:forage_spread"
boolean enabled(FallowConfig config);                        // config.crops.enabled && config.crops.wild.enabled
int visitChunk(ServerLevel level, LevelChunk chunk, RandomSource random, int samples);
```

`visitChunk` follows the established pattern: heightmap surface pick, `canSurvive` guard, light
check, biome fetch, `rates.chance(GrowthChannel.FORAGE, level, pos)`, `level.setBlock(pos, state,
Block.UPDATE_ALL)`, return count changed. It inherits the scheduler's budget for free.

### 8.2 Season-gated farmland growth

Player-planted Idiom-A/B/D/E/F crops grow on the **vanilla random tick** (override the crop block's
`randomTick`), then gate on season so out-of-season planting stalls:

```java
// inside the crop block's randomTick, before the vanilla age-increment
float m = FallowSeasons.growthMultiplier(level.getServer());     // 1.5 spring ... 0.05 winter
if (isWinter && !this.hardy) { wither(level, pos, state); return; } // winter kill (see 7.4)
if (m < this.minSeasonMultiplier) return;                        // cool-season crop won't grow in winter
if (random.nextFloat() > m) return;                              // otherwise scale grow chance by season
```

`minSeasonMultiplier`, the `hardy` flag, and any warm/cool bias are per-crop. This is the mechanism that makes seasons
matter to farming, and it reuses the exact multiplier `SeasonalGrowthRates` already computes.
Fallow's optional client visuals (leaf tint, etc.) are untouched.

### 8.3 Config schema (new `crops` section)

Add a `Crops` inner class to `FallowConfig` mirroring the `Vegetation` pattern (declare fields,
clamp in `clamp()`, read live via `Fallow.CONFIG.crops.*`). Reuse the biome-map idiom
(`Map<String, Double>`) for per-biome forage density and growth, resolved by `BiomeTuning`.

### 8.4 Biome tuning reuse

Wild forage density and growth read straight through `BiomeTuning.growthMultiplier(level, pos)` and
new per-crop biome maps, so deserts stay sparse and wetlands favor rice with the same knobs that
already tune grass and flowers. No new resolution logic.

### 8.5 New growth channels

Add to `GrowthChannel`: `FORAGE` (wild crop spread), flowing through the existing layered providers
(`ConfigGrowthRates` x `SeasonalGrowthRates` x `BiomeGrowthRates`). No `FRUITING` channel is needed:
cherry and plum drops ride the existing `FRUIT` channel through `FruitDropTask`, whose per-type
season gate already replaces the shared curve.

### 8.6 Registration and assets

New content Fallow does not currently ship, all under `dev.isaac.fallow`:

- **Blocks**: crop blocks (per idiom), trellis (Idiom G, ships in v1), wild variants. Registered in
  a new `FallowBlocks` alongside the existing `FallowItems`.
- **Items**: crop foods, seeds, and byproducts, with `fallow:diet/*` tags.
- **Loot tables**: crop drops, leaf-drop fruit, wild-plant drops.
- **Models / textures**: vanilla-faithful, matching the game's crop art density.
- **Worldgen**: none required for player farming; wild crops are placed by `ForageSpreadTask` at
  runtime, not by feature/placed-feature worldgen, keeping them tied to the sim.
- **Tags**: diet groups plus biome-home tags where a vanilla tag does not already fit.

Mixins: none expected. Everything uses Fabric lifecycle events, `EcologyTask`, and vanilla block
overrides, keeping parity with Fallow's "compatible and removable" stance for the code paths (the
placed crop blocks are the only non-removable artifact).

---

## 9. Config sketch

Illustrative shape for `config/fallow.json`, `crops` section. Values are placeholders for tuning.

```json
{
  "crops": {
    "enabled": false,
    "seasonGating": true,
    "wild": {
      "enabled": true,
      "forageChance": 0.004,
      "biomeDensity": { "#minecraft:is_forest": 1.3, "minecraft:desert": 0.1 },
      "biomeGrowth":  { "minecraft:swamp": 1.6, "minecraft:cherry_grove": 1.4 }
    },
    "winterKill": true,
    "legumes": { "fixNitrogen": true, "fixRadius": 1 },
    "paddy":   { "range": 4 }
  }
}
```

Cherry and plum drops are not configured here: they are entries in the existing top-level
`fruiting.types` map. All of it lives under the master `enabled` switch and is re-read on
`/fallow reload`, matching every other Fallow system.

---

## 10. Decisions

The open questions this proposal shipped with, resolved 2026-07-18:

- **Removability messaging.** No new first-join message: crops are part of the mod, and the
  existing one-time notice already covers Fallow altering the world. The caveat is a dedicated note
  in `features.md` and a comment beside `crops.enabled` in the config.
- **Trellis for climbers (Idiom G).** The trellis block ships in the first round; peas climb it
  from v1 rather than starting as a farmland bush. Cucumber, hops, and grapes reuse it later.
- **Cherry harvest method.** Decay drop: ripe cherries fall to the ground beneath the canopy in
  season via the existing `FruitDropTask`, exactly like autumn oak apples. No fruiting-leaf
  blockstate.
- **How hard winter bites.** Hard, with named exceptions: leek and turnip trickle, every other
  standing crop dies in the field, and seed saving is the intended answer
  ([7.4](#74-winter-kill-and-seed-saving)).
- **Overlap policy with FD / More Berries.** Ship our own, always; no detection of or deferral to
  other mods. Shared item tags keep the crops composing instead of conflicting.
- **Diet feature coupling.** Crops land first and ship the `diet/*` tags with them; the diet
  mechanic is a separate later doc and module that consumes the tags.

---

## 11. Phasing

- **Phase C1 (implemented):** Tier 1 crops (turnip, cabbage, onion, cherry, strawberry, peas),
  the trellis block, `ForageSpreadTask` with wild onion and strawberry bush, season-gated farmland
  growth with winter kill, legume nitrogen fixing, and the `diet/*` tags.
- **Phase C2 (implemented):** Tier 2 - rice with the paddy mechanic (`RicePaddy`,
  `crops.paddy.range`), corn as a double-height stalk (`CornCropBlock`), tomato, and cucumber on
  the existing trellis.
- **Phase C3 roster (implemented):** Leek, barley, rye, oat, garlic, radish, parsnip, pepper,
  flax; grapes and hops on the trellis; raspberry and blackberry bushes; squash stem crop; plum
  via `fruiting.types`; and eleven wild forage plants spread by `ForageSpreadTask`.
- **C3 preservation layer (implemented 2026-07-18):** Jam (crafted, jar-return via
  `usingConvertsTo`), pickles (crafted, jar-return), raisins (furnace/smoker/campfire drying),
  and dried chanterelles (furnace/smoker/campfire drying). All four keep their source diet group.
- **Later, separate:** The diet mechanic itself, consuming the `fallow:diet/*` tags this layer
  ships. Per-group micro-flavor bonuses and masting are also future.
