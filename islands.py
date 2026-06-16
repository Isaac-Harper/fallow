#!/usr/bin/env python3
"""Generate the biome-island archipelago for the Fallow test world.

Emits two datapack functions:
  fallow_test:build_islands  - lays all 20 islands (fills, fillbiome, trees, flora, signs),
                               then releases the archipelago force-load. Deterministic
                               (fixed seed) so a reset always restores the same layout.
  fallow_test:reset_islands  - force-loads the archipelago, waits 3s for chunks to load,
                               then schedules build_islands. This is what the kiosk button
                               and `/function fallow_test:reset_islands` call.

A one-tick function can't force-load and build in the same tick (chunks load over later
ticks), which is why reset is a force-load + scheduled build rather than a direct call.
Run this script (python3 islands.py) to regenerate the functions, then in-game:
  /reload   then   /function fallow_test:reset_islands
"""
import random, os

random.seed(1337)

GRASS = ("grass_block", "dirt")
ISLANDS = [
    dict(biome="plains", ground=GRASS, trees=[("oak", 2), ("trees_plains", 1)],
         plants=[("short_grass", 60), ("poppy", 6), ("dandelion", 6), ("oxeye_daisy", 4)],
         note=["PLAINS", "grow x1.1", "dens x1.25"]),
    dict(biome="sunflower_plains", ground=GRASS, trees=[("oak", 1)],
         plants=[("sunflower", 20), ("short_grass", 40), ("dandelion", 10)],
         note=["SUNFLOWER", "PLAINS", "grow x1.1"]),
    dict(biome="meadow", ground=GRASS, trees=[("oak", 1)],
         plants=[("short_grass", 30), ("azure_bluet", 8), ("oxeye_daisy", 8), ("cornflower", 8),
                 ("allium", 6), ("dandelion", 6), ("poppy", 6)],
         note=["MEADOW", "grow x1.3", "dens x1.5"]),
    dict(biome="forest", ground=GRASS, trees=[("oak", 4), ("birch", 2)],
         plants=[("short_grass", 40), ("poppy", 8), ("dandelion", 8)],
         note=["FOREST", "grow x1.3", "dens x1.5"]),
    dict(biome="flower_forest", ground=GRASS, trees=[("oak", 2), ("birch", 2)],
         plants=[("allium", 8), ("azure_bluet", 8), ("red_tulip", 6), ("orange_tulip", 6),
                 ("white_tulip", 6), ("pink_tulip", 6), ("oxeye_daisy", 8), ("cornflower", 8),
                 ("lilac", 4), ("rose_bush", 4), ("peony", 4), ("short_grass", 20)],
         note=["FLOWER", "FOREST", "every bloom"]),
    dict(biome="birch_forest", ground=GRASS, trees=[("birch", 4), ("birch_tall", 1)],
         plants=[("short_grass", 40), ("lily_of_the_valley", 8)],
         note=["BIRCH", "FOREST"]),
    dict(biome="dark_forest", ground=GRASS, trees=[("dark_oak", 4)],
         plants=[("short_grass", 20), ("red_mushroom", 6), ("brown_mushroom", 6), ("rose_bush", 2)],
         note=["DARK", "FOREST", "dark oak"]),
    dict(biome="taiga", ground=GRASS, trees=[("spruce", 4), ("pine", 1)],
         plants=[("fern", 30), ("large_fern", 8), ("sweet_berry_bush", 5)],
         note=["TAIGA", "grow x0.7", "spruce"]),
    dict(biome="old_growth_pine_taiga", ground=("podzol", "dirt"), trees=[("mega_pine", 1), ("pine", 2), ("spruce", 2)],
         plants=[("fern", 30), ("large_fern", 10)],
         note=["OLD GROWTH", "PINE TAIGA"]),
    dict(biome="snowy_taiga", ground=GRASS, special="snow", trees=[("spruce", 4)],
         plants=[("dead_bush", 3)],
         note=["SNOWY", "TAIGA", "grow x0.5"]),
    dict(biome="grove", ground=("snow_block", "dirt"), special="snow", trees=[("spruce", 4)],
         plants=[],
         note=["GROVE", "grow x0.5", "snowy"]),
    dict(biome="jungle", ground=GRASS, trees=[("jungle_tree", 2)],
         plants=[("fern", 30), ("short_grass", 20), ("melon", 4)], bamboo=8,
         note=["JUNGLE", "grow x1.6", "jungle trees"]),
    dict(biome="bamboo_jungle", ground=GRASS, trees=[("jungle_tree", 3)],
         plants=[("melon", 3), ("fern", 10)], bamboo=24,
         note=["BAMBOO", "JUNGLE", "grow x1.6"]),
    dict(biome="savanna", ground=GRASS, trees=[("acacia", 4)],
         plants=[("short_grass", 50), ("dead_bush", 2)],
         note=["SAVANNA", "grow x0.6", "acacia"]),
    dict(biome="desert", ground=("sand", "sandstone"), trees=[],
         plants=[("dead_bush", 14)], cactus=8,
         note=["DESERT", "grow x0.15", "arid"]),
    dict(biome="badlands", ground=("red_sand", "terracotta"), trees=[],
         plants=[("dead_bush", 16)], cactus=4,
         note=["BADLANDS", "grow x0.25"]),
    dict(biome="swamp", ground=GRASS, special="water", trees=[("swamp_oak", 2)],
         plants=[("blue_orchid", 5), ("brown_mushroom", 6), ("short_grass", 20)],
         note=["SWAMP", "grow x1.4"]),
    dict(biome="mangrove_swamp", ground=("mud", "mud"), special="water", trees=[], manual_mangroves=4,
         plants=[("short_grass", 6)],
         note=["MANGROVE", "SWAMP", "grow x1.5"]),
    dict(biome="cherry_grove", ground=GRASS, trees=[("cherry", 4)],
         plants=[("pink_petals", 24), ("short_grass", 20), ("allium", 6)],
         note=["CHERRY", "GROVE", "cherry trees"]),
    dict(biome="pale_garden", ground=GRASS, trees=[("pale_oak", 3)],
         plants=[("pale_moss_carpet", 40), ("closed_eyeblossom", 6), ("short_grass", 10)],
         note=["PALE", "GARDEN", "pale oak"]),
]
TALL = {"sunflower", "lilac", "rose_bush", "peony", "large_fern", "tall_grass"}

SIZE = 40
COLS = [-120, -72, -24, 24]
ROWS = [176, 224, 272, 320, 368]
BX1, BZ1 = COLS[0], ROWS[0]
BX2, BZ2 = COLS[-1] + SIZE - 1, ROWS[-1] + SIZE - 1


def island_cmds(isl, cx, cz):
    top, sub = isl["ground"]
    x2, z2 = cx + SIZE - 1, cz + SIZE - 1
    c = []
    c.append("fill %d 97 %d %d 99 %d minecraft:%s" % (cx, cz, x2, z2, sub))
    c.append("fill %d 100 %d %d 100 %d minecraft:%s" % (cx, cz, x2, z2, top))
    c.append("fill %d 101 %d %d 120 %d minecraft:air" % (cx, cz, x2, z2))
    c.append("fillbiome %d 90 %d %d 116 %d minecraft:%s" % (cx, cz, x2, z2, isl["biome"]))
    if isl.get("special") == "water":
        wx, wz = cx + 8, cz + 8
        c.append("fill %d 99 %d %d 99 %d minecraft:%s" % (wx, wz, x2 - 8, z2 - 8, sub))
        c.append("fill %d 100 %d %d 100 %d minecraft:water" % (wx, wz, x2 - 8, z2 - 8))
        for _ in range(8):
            c.append("setblock %d 101 %d minecraft:lily_pad" % (random.randint(wx, x2 - 8), random.randint(wz, z2 - 8)))
        for _ in range(10):
            c.append("setblock %d 100 %d minecraft:seagrass" % (random.randint(wx, x2 - 8), random.randint(wz, z2 - 8)))
    for feat, count in isl["trees"]:
        for _ in range(count):
            c.append("place feature minecraft:%s %d 101 %d" % (feat, random.randint(cx + 4, x2 - 4), random.randint(cz + 4, z2 - 4)))
    for _ in range(isl.get("manual_mangroves", 0)):
        mx, mz = random.randint(cx + 4, x2 - 4), random.randint(cz + 4, z2 - 4)
        c.append("fill %d 101 %d %d 106 %d minecraft:mangrove_log" % (mx, mz, mx, mz))
        c.append("fill %d 105 %d %d 107 %d minecraft:mangrove_leaves[persistent=false]" % (mx - 2, mz - 2, mx + 2, mz + 2))
        c.append("fill %d 101 %d %d 106 %d minecraft:mangrove_log" % (mx, mz, mx, mz))
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            c.append("setblock %d 101 %d minecraft:mangrove_roots" % (mx + dx, mz + dz))
    for key, blk in (("bamboo", "bamboo"), ("cactus", "cactus")):
        for _ in range(isl.get(key, 0)):
            px, pz = random.randint(cx + 2, x2 - 2), random.randint(cz + 2, z2 - 2)
            h = random.randint(2, 4)
            c.append("fill %d 101 %d %d %d %d minecraft:%s" % (px, pz, px, 100 + h, pz, blk))
    for blk, count in isl.get("plants", []):
        for _ in range(count):
            px, pz = random.randint(cx, x2), random.randint(cz, z2)
            if blk in TALL:
                c.append("setblock %d 101 %d minecraft:%s[half=lower]" % (px, pz, blk))
                c.append("setblock %d 102 %d minecraft:%s[half=upper]" % (px, pz, blk))
            else:
                c.append("setblock %d 101 %d minecraft:%s" % (px, pz, blk))
    if isl.get("special") == "snow":
        for _ in range(120):
            c.append("setblock %d 101 %d minecraft:snow" % (random.randint(cx, x2), random.randint(cz, z2)))
    msgs = (isl["note"] + ["", "", "", ""])[:4]
    body = ",".join('"%s"' % m for m in msgs)
    c.append('setblock %d 101 %d minecraft:oak_sign{front_text:{messages:[%s]}}' % (cx + SIZE // 2, cz, body))
    return c


def main():
    funcdir = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                           "versions/26.1.2/run/fallow-test/datapacks/fallow-test-tools/data/fallow_test/function")
    build = []
    i = 0
    for cx in COLS:
        for cz in ROWS:
            if i >= len(ISLANDS):
                break
            build += island_cmds(ISLANDS[i], cx, cz)
            i += 1
    build.append('setblock 6 101 0 minecraft:oak_sign{front_text:{messages:["BIOME ISLANDS","--> SOUTH","z 176..400","20 biomes"]}}')
    build.append("forceload remove %d %d %d %d" % (BX1, BZ1, BX2, BZ2))
    build.append('tellraw @a {"text":"[fallow-test] biome archipelago rebuilt","color":"green"}')
    with open(os.path.join(funcdir, "build_islands.mcfunction"), "w") as f:
        f.write("\n".join(build) + "\n")

    reset = [
        "# Force-load the archipelago, then build 3s later once the chunks are live.",
        "forceload add %d %d %d %d" % (BX1, BZ1, BX2, BZ2),
        'tellraw @a {"text":"[fallow-test] rebuilding biome islands (~3s)...","color":"yellow"}',
        "schedule function fallow_test:build_islands 60t replace",
    ]
    with open(os.path.join(funcdir, "reset_islands.mcfunction"), "w") as f:
        f.write("\n".join(reset) + "\n")
    print("wrote build_islands (%d cmds) + reset_islands" % len(build))


main()
