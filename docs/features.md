# Fallow - Features

Fallow turns the overworld into a slow, living world: four seasons cycle, days lengthen and
shorten, weather turns to snow and back, and vegetation grows, spreads, ages, and dies back over
many in-game days.

**Heads up: Fallow changes blocks in your world over time, which is effectively destructive, so it
ships DISABLED by default.** Nothing below happens until you turn it on (set `"enabled": true` in
`config/fallow.json` and run `/fallow reload`); back up worlds you care about first. The first time
you join a world with Fallow installed you get a one-time in-game notice of this. Once enabled, each
feature below is individually on by default and can be tuned or switched off.

This is the plain-language tour of *what* Fallow does. For knobs and defaults see
[configuration.md](configuration.md); for how it works under the hood see
[architecture.md](architecture.md).

---

## Seasons

- **Four seasons** - spring, summer, autumn, and winter cycle in order, forever.
- **Automatic progression** - seasons advance as in-game days pass; you never manage them.
- **Adjustable length** - set how many days each season lasts (default 10).
- **Season-aware world** - the current season changes how fast things grow, how hard things die
  back, how long the day is, and whether it rains or snows (all detailed below).
- **Check the season any time** - a command reports the current season, the day within it, how
  much faster/slower things are growing, and how much of the day is daylight.
- **Set the season** - operators can jump straight to any season for testing or events.
- **Fully optional** - seasons can be turned off, leaving a static, vanilla-paced world.

## Day & night

- **Seasonal day length** - summer days are long and nights short; winter nights are long and
  days short; spring and autumn are balanced (and identical to vanilla).
- **Same 24-hour clock** - a full day still takes the usual amount of real time; only the split
  between daylight and darkness shifts. Sleeping, time commands, and everything time-based keep
  working normally.
- **Smoothly driven** - the day-length shift rides Minecraft's own clock, so it's seamless and
  multiplayer-safe, with no stutter.
- **Optional** - the day/night shift can be disabled independently of the rest of seasons.

## Weather & precipitation

- **Rain in summer, snow in winter** - temperate places (plains, forests, meadows...) get rain in
  the warm seasons and real **snowfall** in winter.
- **Snow piles up while it snows - as deep as the biome is cold** - during snowfall, snow builds
  into layers on the ground (deep drifts in snowy and boreal biomes, a thin dusting in temperate
  ones); it never appears on a clear day, and it melts away as spring warms up.
- **Water freezes and thaws** - ponds and exposed water freeze to ice in the cold of winter and
  melt back when it warms.
- **The tropics never freeze** - jungles and other hot, wet biomes stay rainy year-round and
  never see snow.
- **Cold places stay white** - snowy and boreal biomes keep their snow through most of the year.
- **Deserts stay dry** - true deserts and badlands get no rain or snow at all, in any season.
- **Savanna gets a wet season** - savannas, normally bone-dry, gain a warm rainy season.
- **Altitude matters** - higher ground turns to snow sooner, so the snow line rises and falls
  with the seasons.
- **Looks right everywhere** - falling rain vs. snow always matches what's settling on the
  ground, in singleplayer and multiplayer alike.
- **Snow depth varies by biome** - snowy and boreal biomes pile deep drifts over winter; temperate
  biomes get a dusting. Drifts melt and ponds thaw when the season turns.
- **Wetter and drier seasons** - it rains more often in some seasons (summer wettest, winter
  driest by default). This is world-wide - Minecraft has a single weather timeline.
- **Seasonal weather events** - occasional, season-appropriate extremes: **blizzards** in winter
  (heavy, fast-piling snow), **heatwaves** in summer (clear, hot, growth stalls and snow melts),
  and **storms** in spring and autumn (rain and thunder). Each runs for a while, then passes.
- **Tunable per biome** - you can decide which biomes get precipitation and how cold each season
  runs; the whole system can be turned off.

## Plant life & growth

- **Grass fills in** - bare grass blocks slowly sprout short grass over time.
- **Grass grows tall** - short grass occasionally grows into tall grass.
- **Flowers bloom** - flowers appear using each biome's own natural flower selection, so every
  biome blooms with the right flowers (including biomes added by datapacks).
- **Bushes spread** - bushes creep outward from existing bushes nearby.
- **Bamboo groves expand** - bamboo creeps outward from existing stands the way real bamboo runs
  on roots, filling gaps and edging into open ground, then thickening to a natural grove density.
- **Natural limits** - areas fill in to a sensible density and then stop, so meadows look full,
  not carpeted wall-to-wall.
- **Needs light** - plants only grow in adequate light, and growth pauses at night.
- **Seasonal pace** - growth surges in spring, holds steady in summer, tapers in autumn, and goes
  dormant in winter.

## The seasonal lifecycle (bloom & dieback)

- **Flowers follow the year** - they bloom in spring, last through summer, fade in autumn, and are
  gone by deep winter, then return the next spring.
- **The flower mix shifts with the season** - within a biome's own flowers, spring favors tulips,
  allium, and lily of the valley while summer brings out cornflowers, daisies, and sunflowers.
  (Bounded by Minecraft's small flower set, so the spring/summer difference is the clearest.)
- **Grass thickens and thins with the season and the biome** - grass density tracks both: it's
  lush in spring and summer and recedes toward winter, and lush biomes (jungle, forest, meadow)
  hold thick grass while arid ones (savanna, desert) stay sparse. Packed meadows open up in the
  cold and fill back in come spring.
- **Decay in the dark** - plants left in genuinely enclosed darkness (sealed, roofed, or buried
  rooms) slowly die: tall grass shrinks to short grass, short grass disappears, and bare grass
  blocks turn to dirt.
- **Ordinary night is safe** - only truly dark, enclosed spots decay; normal nighttime and
  torch-lit interiors never do.
- **Winter bites hardest** - dieback, wilting, and thinning all intensify in winter.

## Trees & forests

- **Forests creep outward** - mature trees occasionally drop a matching sapling on suitable
  nearby ground, so woods slowly expand on their own.
- **Your builds are safe** - log cabins, fences, and lumber piles are never mistaken for trees
  and won't sprout saplings around them.
- **Every tree species behaves differently** - how readily a tree spreads, how far its seeds
  reach, and how tightly its saplings pack are all set per species (pioneers spread fast and
  wide; dense-canopy trees pack tight; relict species barely spread).
- **Seasonal seeding, grounded in real ecology** - each tree spreads in the season it really
  does:
  - **Oak** seeds in **autumn** only (acorns sprout where they drop).
  - **Spruce** seeds in **autumn and on through winter** - the one tree that keeps going in the
    cold.
  - **Mangrove** seeds in **late summer** and never shuts down (it's tropical-coastal).
  - **Jungle trees** seed **year-round** (the rainforest has no real off-season).
  - **Birch, dark oak, cherry, acacia, pale oak** lean on **spring**, when seedlings establish.
- **Modded trees just work** - trees added by other mods spread automatically, on a sensible
  default schedule.
- **Trees drop fruit in season** - in autumn, oaks (and dark oaks) drop apples on the ground
  beneath their canopy. Only real trees fruit - your leaf builds never do. The fruit list is
  configurable, so packs can map any tree to any item and season.

## Bushes & berries

- **Bushes keep their own calendar** - each bush type spreads in its own season:
  - **Sweet berry bushes** in **autumn** (and stay dormant through a frozen winter).
  - **Firefly bushes** in **summer**.
  - **Plain bushes** in **spring**.
- **Dead bushes stay dead** - dead bushes never spread or multiply; they're not alive.

## Crops & forage

The crop layer is a second opt-in on top of the master switch: set `crops.enabled` to `true` in
`config/fallow.json` (default `false`). With it off no wild plants appear, no crop blocks are
placed, and the ecology sim stays exactly as it is. With it on:

- **Farmland crops (fourteen)** - planted from seeds on farmland, each tied to a seasonal window:
  - **Turnip** (autumn, winter trickle - weight 0.25) and **leek** (autumn, cold-hardier trickle -
    weight 0.35) are the two crops that keep going through winter at a reduced rate instead of dying.
  - **Cabbage** (spring and autumn, stalls in summer heat and winter).
  - **Onion** (spring/autumn) - carrot-style: the onion food item is the planter.
  - **Garlic** and **parsnip** (autumn, parsnip trickles at 0.25 in winter).
  - **Radish** (spring and autumn fast root).
  - **Grains - barley, rye, and oats** (cool season). Barley and rye carry a winter trickle weight
    (0.15 and 0.3 respectively); oat zeros out in winter.
  - **Pepper** (summer, warm-season nightshade).
  - **Flax** (spring/summer fiber crop).
  - **Tomato** (summer).
  - **Rice** (summer) - a paddy crop: it only grows when water sits within `crops.paddy.range`
    blocks (default 4, range 0-8) of the farmland. Grow on flooded paddies; the water check
    stalls growth without killing the plant.

- **Double-height crop** - **corn** grows two blocks tall (pitcher-crop style): the lower half
  drives growth and the upper half appears at age 2. Breaking either half clears both.

- **Trellis climbers (four)** - planted on a crafted trellis block (four sticks in an X pattern,
  yields two), placed on dirt-family ground or farmland. Right-clicking the trellis with seeds
  starts the vine at age 0; right-clicking the ripe vine (age 3) harvests the crop and resets to
  age 1, so the trellis persists season to season.
  - **Peas** (spring) - the legume; see Nitrogen fixing below.
  - **Cucumber** (summer) - preservation feedstock; pickles into a winter storable.
  - **Grapes** (summer/autumn) - fruit and brewing feedstock.
  - **Hops** (summer/autumn) - brewing companion to barley.
  - When winter-kill fires on a climber, it reverts to a bare trellis rather than a dead husk,
    so the structure stays in place.

- **Berry bushes (three)** - sweet-berry idiom: right-click to harvest ripe berries (age 2+), no
  contact thorns. The berry item itself plants the bush.
  - **Strawberry** (spring) - appears wild in meadows, plains, and forests.
  - **Raspberry** (summer peak) and **blackberry** (summer) - spread via the forage engine.
  - Bushes stall in winter but do not die from winter-kill.

- **Squash** - a stem crop in the pumpkin/melon pattern. The stem grows on farmland and fruits out
  onto adjacent ground. Reuses vanilla's pumpkin support tags; the season gate is applied by
  overriding the stem's `randomTick`, exactly like the other crops.

- **Tree fruits (two)** - no new growth block; the existing `FruitDropTask` drops these beneath
  natural canopy in season:
  - **Cherry** (spring) - drops from `minecraft:cherry_leaves`.
  - **Plum** (autumn) - drops from `minecraft:flowering_azalea_leaves`.

- **Forage layer** - eleven wild plants spread via the ecology scheduler (`crops.wild.homes`).
  Three are crop bootstraps (breaking them yields seeds to start the farmland crop):
  - **Wild rice** (swamp, mangrove swamp, rivers) - the only way to obtain rice seeds.
  - **Wild grape vine** (savanna, meadow, sunflower plains).
  - **Wild hops** (forests, rivers).
  - The rest are gathered wild plants:
  - **Wild onion** (forest) - the oldest forage entry; yields onion seeds.
  - **Strawberry bush** also spreads wild in meadows, plains, and forests (see Berry bushes above).
  - **Ramsons** (forest) - wild garlic; occasionally yields a garlic item on harvest.
  - **Chanterelle** (forests, taiga) - fungi, diet group fungi.
  - **Four herbs** spread in their biome homes: mint (river, meadow, swamp), sage (savanna, plains),
    thyme (plains, savanna), sorrel (plains, meadow, forest).

- **How seeds are obtained** - fifteen crop seeds drop from breaking short or tall grass when the
  crop layer is on (5 % chance per break, equal weight; config: `crops.seedDropChance`). Rice is
  deliberately excluded from the grass pool: it is found only as wild rice in wetland biomes.

- **Season-gated growth** - crops grow on the vanilla random tick but each has its own seasonal
  weight. A crop planted out of season stalls instead of growing; all the seasonal tuning lives
  in `crops.cropSeasons`.

- **Winter kill** - when winter arrives, any standing crop whose seasonal weight is zero withers
  into a dead, unharvestable `fallow:withered_crop` block on its next random tick. Saving seed in
  a chest before the season turns is the intended counter. Turnip, leek, barley, rye, garlic, and
  parsnip carry non-zero winter weights so they trickle instead of dying. Bushes stall rather than
  die. Trellis climbers revert to a bare trellis. Toggle with `crops.winterKill` (default `true`).

- **Pea nitrogen fixing** - when a pea vine reaches maturity or is harvested, the soil around it
  improves: one coarse dirt or rooted dirt block within `crops.legumes.fixRadius` (default 1
  block) converts back to plain dirt. Toggle with `crops.legumes.fixNitrogen` (default `true`).

- **Wild forage spread** - the `ForageSpreadTask` places wild plants in biome-appropriate
  locations via the ecology scheduler. Wild spread follows the shared seasonal curve (slowing in
  winter with the rest of the ecosystem), unlike player-planted crops which use per-crop weights.
  Biome homes are config-overridable via `crops.wild.homes`.

- **Diet item tags** - six tags (`fallow:diet/grain`, `/vegetable`, `/fruit`, `/protein`,
  `/fungi`, `/sugar_oil`) are shipped with their current vanilla and Fallow-crop members. They
  feed the diet mechanic described below.

**Uninstall warning.** Unlike everything else in Fallow, placed crop blocks are real new blocks
(`fallow:turnip_crop`, `fallow:pea_crop`, etc.). If the mod is removed after crops have been
planted, those blocks become unknown blocks in the world - they do not vanish cleanly. Ecology
edits (grass, flowers, trails, trees) are reversible; planted crops are not. This is normal for
any content mod but it contradicts Fallow's usual "removes cleanly" promise, which is why the
crop layer is a separate opt-in.

## Diet

The diet layer is a third opt-in, gated by `diet.enabled` (default `false`) under the master
switch. With it off the diet window is never updated and no absorption is ever applied; the world
is untouched either way.

- **Six food groups** - grain, vegetable, fruit, protein, fungi, and sugar & oil. Every vanilla
  food and every Fallow crop already lands in at least one group. Third-party foods count too if
  a datapack tags them under the six `fallow:diet/*` item tags; nothing is hardcoded.
- **A varied recent diet earns extra absorption hearts** - eating across the groups gives a
  refreshed bonus on top of vanilla hunger, never a penalty for monotony:
  - **Four or more groups:** Absorption I (two extra hearts).
  - **All six groups:** Absorption II (four extra hearts).
  - Eating nothing but bread plays exactly vanilla, no punishment.
- **Your window is your last twelve meals** - the mod tracks the most recent twelve food items
  you ate. Meals older than a few in-game days also age out, so the bonus describes a current diet
  rather than one frozen from last week. Untagged food and drinks are ignored: they neither help
  nor harm the window.
- **Feedback without new UI** - `/fallow diet` prints which groups your window currently covers,
  which are missing, how many meals are in the window, and your current tier. On first covering a
  new group, a brief actionbar note ("Fruit joins your diet") teaches the system in passing
  (`diet.announceNewGroups`, on by default).
- **Off by default** - turn it on with `diet.enabled = true` in `config/fallow.json`.
- **Fully removable** - unlike the crop layer, the diet layer places no new blocks. Disabling
  the module or removing the mod leaves a plain vanilla player; per-player state is stored in one
  small overworld saved-data record and is simply orphaned on removal.

## Forest floor & ground cover

- **Leaf litter builds up** - under dense tree canopy, the ground slowly turns to podzol and
  rooted dirt, giving mature forests a proper forest floor.
- **Autumn and winter speed it up** - the forest floor develops fastest in the leaf-fall seasons.

## Water & shorelines

- **Sugar cane spreads** - cane appears along the water's edge on suitable banks.
- **Seagrass spreads** - seagrass fills in on shallow underwater floors.
- **Sensible limits** - both stop once an area is reasonably planted.

## Paths & trails

- **Walking wears the ground** - repeatedly walking the same route wears grass into coarse dirt,
  then into a packed dirt path, so well-traveled routes show up naturally.
- **Unused paths heal** - trails you stop using slowly recover back toward grass.
- **Resettable** - a command clears all recorded trail wear.

## Per-biome character

- **Lush vs. arid** - jungles, forests, and swamps grow faster and fuller; savanna, taiga,
  badlands, and desert grow slower and sparser.
- **Strong vs. mild seasons** - the tropics barely notice the seasons (near year-round growth,
  no dead winter); temperate biomes have the classic seasonal swing; boreal and snowy biomes have
  exaggerated seasons - a true no-growth winter and an explosive spring.
- **Biomes can have their own season timing** - a biome's growth peak can be shifted, so a savanna
  thrives in its summer wet season rather than spring; temperate biomes keep the usual spring peak.
- **All adjustable** - every per-biome behavior (growth speed, how full things get, how strong
  the seasons feel, when it peaks, whether it precipitates) can be tuned, including for biomes
  added by other packs.

## Visuals

- **Seasonal color** - grass and foliage tint shifts with the season, so the world reads as green
  in spring/summer, warm and orange in autumn, and muted in winter.
- **Recolored autumn leaves** - because tinting can only darken the grey leaf textures (a yellow
  tint just reads as olive), the strongest autumn species swap to dedicated recolored textures
  while it is autumn: birch turns golden, oak orange, dark oak deep russet, and cherry deep
  crimson (its drifting leaf particles turn crimson to match, instead of the pink blossom petal).
  Birch also goes a bare, muted brown in winter. Every other season stays vanilla.
- **Spruce frost-muting** - spruce stays evergreen all year, with only a subtle cold-muted shift
  in deep winter.
- **Lily pads turn too** - lily pads green up in spring, yellow-brown in autumn, and fade to brown
  in winter.
- **Falling leaves in autumn** - leaves drift down in a flurry through autumn and only lightly the
  rest of the year, timed to match the leaf litter building up on the forest floor.
- **Smooth transitions** - colors drift day by day between seasons rather than snapping.
- **Overworld only** - the Nether and End keep their normal look.
- **Adjustable strength** - dial the effect up or down; summer is exactly vanilla.
- **Renderer-friendly** - works with the vanilla renderer and with Sodium; purely cosmetic, with
  no effect on gameplay. Players without the mod installed simply see normal colors.

## Items

- **Season Clock** - a copper-and-redstone pocket clock that tells the **season** instead of the
  time of day. Its face has four pips at the compass points (spring, summer, autumn, winter) and a
  redstone hand that sweeps round to point at the season you're in - so a glance tells you where you
  are in the year, even underground.
- **Crafted like a clock** - four copper ingots in a plus around a single redstone, the copper
  echo of the vanilla gold clock. Found in the Tools &amp; Utilities creative tab.
- **Works in the hand or a frame** - the face updates live as the season turns; with a vanilla
  client it's still a normal craftable item, just without the animated face.

## Controls & configuration

- **In-game settings screen** - configure everything through a Mod Menu screen, no file editing
  required.
- **Editable config file** - or hand-edit a simple settings file.
- **Live reload** - apply most setting changes on a running server without a restart.
- **Everything is toggleable** - every system (growth, dieback, trees, bushes, trails, leaf
  litter, shorelines, seasons, day/night, precipitation, visuals, crops, diet) can be turned on
  or off on its own.
- **Everything is tunable** - rates, limits, distances, per-biome behavior, season length, and
  per-season strength are all adjustable.
- **Performance-bounded** - the whole simulation runs within a strict per-tick budget, so it
  spreads its work out and won't hitch the game; a stats command shows what it's doing.

## Compatibility & safety

- **Multiplayer-ready** - everything is server-driven and syncs correctly to clients.
- **Datapack- and mod-friendly** - respects custom biomes and their flowers, and handles modded
  trees automatically.
- **Safe to add or remove** - Fallow only uses the game's own systems and saved data, so adding
  or removing it won't corrupt a world; left-over effects clear themselves. **Exception:** if the
  opt-in crop layer (`crops.enabled`) has been used, planted crop blocks become unknown blocks
  on uninstall - see the [Crops & forage](#crops--forage) section.
- **Vanilla when neutral** - out of season, with a feature disabled, or without the mod on the
  client, the relevant behavior is exactly vanilla.

## For pack makers & modders

- **Stable season API** - other mods can read the current season, the day within it, season
  length, and the current growth multiplier to build their own season-aware features.
- **Season broadcast to clients** - the current season is sent to clients, so client-side add-ons
  can react to it too.

## Exploring it

- **Built-in showcase world** - the project ships a test world whose labeled platforms
  demonstrate each feature, plus a 20-biome "archipelago" where you can compare how every biome
  grows, blooms, and weathers, and a button-driven kiosk to jump between seasons and reset things
  on demand.
