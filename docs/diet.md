# Fallow - diet (design proposal)

Status: **design, not built.** This document scopes the diet mechanic that the crop layer's
`fallow:diet/*` item tags were shipped to feed ([crops.md](crops.md) section 7.5). Nothing here
ships until it is implemented behind its own config module (`diet.enabled`, default `false`,
under the master `enabled` switch).

The one-sentence pitch: **eating a varied diet earns a small, visible bonus; eating one thing
does not hurt you.** Fallow rewards engaging with the seasonal food the crop layer creates; it
never punishes ignoring it. A player who eats nothing but bread plays exactly vanilla.

---

## 1. Design pillars

- **Carrot, not stick.** Vanilla hunger is untouched. Variety adds a bonus on top; monotony
  just means no bonus. This keeps the mechanic honest with Fallow's "vanilla when neutral"
  promise and avoids the resentment that punishing diet mods generate.
- **Data-driven by tags.** The six groups are exactly the shipped item tags (`diet/grain`,
  `diet/vegetable`, `diet/fruit`, `diet/protein`, `diet/fungi`, `diet/sugar_oil`). Vanilla
  food, Fallow crops, and any third-party food a pack tags all count with zero hardcoding.
- **Seasons supply the drama.** In summer and autumn, variety is easy - fresh food everywhere.
  In winter, fresh variety collapses by design (winter kill, dormant forage), so keeping the
  bonus through winter requires the agricultural rhythm the crop layer teaches: store the
  storables, save the seeds, and later (preservation phase) put up preserves.
- **Fully removable.** Unlike crop blocks, the diet layer is pure per-player state and
  effects. Removing the mod or disabling the module leaves a plain vanilla player.

---

## 2. The mechanic

**Rolling meal window.** Each player has a rolling list of their last `windowSize` meals
(default 12). Eating any food appends its diet groups (an item can carry several tags; each
counts). The oldest meal falls off the end. Measured in meals, not days, so season length and
day-cycle settings do not change the tuning.

**Variety score.** The score is the number of *distinct* groups present in the window (0-6).

**The bonus.** Two tiers, deliberately modest and visible:

| distinct groups | bonus |
|---|---|
| 4+ | `wellFedMinor`: one extra row-half of absorption hearts (2 points), refreshed while the score holds |
| 6 | `wellFedFull`: four absorption points and a touch of extra saturation efficiency |

Absorption is the right lever: it is visible on the HUD without new UI, it decays naturally if
the score lapses, and it never interferes with hunger math. Exact values are config knobs; the
tier thresholds are too.

**Feedback.** No new HUD. `/fallow diet` prints the window: which groups are covered, which
are missing, and the current tier. On first covering a new group, a one-line actionbar note
("Fruit joins your diet") teaches the system in passing.

---

## 3. Implementation sketch

- **Eat detection**: the one open mechanism question. Preferred: a Fabric API hook on food
  consumption if 26.1 exposes one; fallback: a small mixin at the point vanilla applies food
  (the `FoodData`/eat path), which fits the "mixins as last resort" rule the way the
  temperature lever did. Decide at build time against the actual API surface.
- **State**: per-player persistent storage following the repo's SavedData precedent
  (`TrailData`, `NoticeData`): a codec-serialized map of player UUID to a small ring buffer of
  meal entries (group set per meal). Synced to the owning client only for `/fallow diet`
  output; no broadcast.
- **Effect application**: a once-per-second player tick pass (piggybacking the existing
  service tick pattern, not the ecology scheduler) recomputes the score lazily when the window
  changed and applies/refreshes the absorption effect.
- **Config** (`diet` section): `enabled` (false), `windowSize` (12, clamp 4-64), thresholds
  and bonus magnitudes per tier, and `announceNewGroups` (true).
- **Tag hygiene**: vanilla foods are already distributed across the six tags; the audit that
  ships with this feature should verify every vanilla food item lands in at least one group
  and flag untagged modded foods at reload (log, not error).

---

## 4. Open decisions

- **Absorption vs. attribute hearts.** Absorption decays and self-communicates; a max-health
  attribute is stickier but reads as "the mod took my hearts" when it lapses. Leaning
  absorption.
- **Do meals expire by time as well as by count?** A pure meal-count window never decays for
  an idle player. Option: entries also expire after N in-game days (default off). Decide
  during playtesting.
- **Preserved food identity.** When the preservation phase lands (pickles, jam, raisins,
  dried mushrooms), preserved items keep their source group tags so winter variety is
  reachable - that is the point of preserving. No separate "preserved" group.
- **Third-party overlap.** As with crops: no detection of other diet mods; ship our own,
  tags keep the data composable.

---

## 5. Phasing

- **D1:** eat detection + window state + score + absorption tiers + `/fallow diet` + config.
- **D2:** the preservation layer from crops.md section 7.3 (pickling, drying, jam), designed
  in its own right but motivated here - winter variety through stores.
- **Later:** per-group micro-flavor (a fungi-heavy window slightly speeds mushroom spread
  near the player, etc.) only if D1 proves fun; explicitly out of scope for now.
