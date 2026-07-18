#!/usr/bin/env python3
"""Generate the crop-layer textures (blocks + items) as 16x16 pixel art.

Every sprite is hand-authored as a character grid (one char per pixel, palette
below), in the style of the vanilla farmland crops: rows of small plants anchored
to the soil line for the farmland crops, ragged multi-value foliage for bushes,
bold outlined silhouettes for items. Deterministic: no randomness, byte-identical
output on every run.

Re-run to regenerate identical files:
python3 crops_art.py

Writes 24 block textures and 9 item textures into
src/main/resources/assets/fallow/textures/{block,item}/.
"""

import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TEXTURES = ROOT / "src/main/resources/assets/fallow/textures"

# Shared palette. '.' is transparent; letters are grouped by hue ramp.
PALETTE = {
    ".": (0, 0, 0, 0),
    # green ramp (foliage)
    "g": (31, 54, 26, 255),    # outline / deepest
    "G": (54, 90, 35, 255),    # deep
    "m": (80, 124, 46, 255),   # mid
    "M": (112, 160, 61, 255),  # light
    "h": (147, 192, 82, 255),  # highlight
    # chive / allium ramp (onion tops, cooler green)
    "c": (43, 82, 51, 255),
    "C": (70, 120, 72, 255),
    "d": (101, 153, 97, 255),
    # berry / fruit red ramp
    "r": (99, 23, 26, 255),
    "R": (158, 34, 36, 255),
    "e": (205, 64, 52, 255),
    "E": (237, 129, 105, 255),
    # turnip purple
    "p": (94, 52, 110, 255),
    "P": (137, 82, 152, 255),
    # cream / white (turnip belly, blossoms)
    "w": (214, 205, 185, 255),
    "W": (240, 236, 224, 255),
    # onion amber
    "a": (139, 84, 33, 255),
    "A": (191, 126, 52, 255),
    "y": (223, 169, 89, 255),
    # yellow (flower centres, strawberry seeds)
    "Y": (233, 208, 96, 255),
    # trellis wood
    "k": (66, 44, 26, 255),
    "K": (104, 72, 42, 255),
    "L": (141, 103, 62, 255),
    # withered / dry browns
    "b": (82, 58, 32, 255),
    "B": (129, 97, 55, 255),
    "T": (174, 141, 91, 255),
    # pea pod greens
    "v": (72, 112, 44, 255),
    "V": (129, 172, 77, 255),
    "n": (170, 205, 110, 255),
}

SPRITES = {}


def sprite(name, rows):
    assert len(rows) == 16, f"{name}: {len(rows)} rows"
    for i, row in enumerate(rows):
        assert len(row) == 16, f"{name} row {i}: {len(row)} chars"
        for ch in row:
            assert ch in PALETTE, f"{name} row {i}: unknown char {ch!r}"
    SPRITES[name] = rows


# --- turnip (farmland crop, beetroot layout: a row of plants, soil at y=15) ---

sprite("block/turnip_crop_stage0", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "...M........M...",
    "..gm...M...gm...",
    "...G..gm....G...",
    "................",
])

sprite("block/turnip_crop_stage1", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "..M.......M.....",
    "..mh...M..mh..M.",
    "..Gm..gmh..Gm.gm",
    "...G...G...G...G",
    "...G...G....G..G",
    "................",
    "................",
])

sprite("block/turnip_crop_stage2", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "..h.......h.....",
    ".mMh..h..mMh..h.",
    ".GmM.mMh.GmM.mMh",
    "..Gm..Gm..Gm..Gm",
    "..GG..gG..GG..gG",
    "...G...G...G...G",
    "..wW..GG..wW..GG",
    "..ww..wW..ww..wW",
    "................",
    "................",
])

sprite("block/turnip_crop_stage3", [
    "................",
    "................",
    "................",
    "..h....h....h...",
    ".hMm..hMm..hMm..",
    ".mMGm.mMGm.mMGm.",
    ".GmMG.GmMG.GmMG.",
    "..GG...GG...GG..",
    "..gG...gG...gG..",
    ".PppP.PppP.PppP.",
    ".pWWp.pWWp.pWWp.",
    ".wWWw.wWWw.wWWw.",
    ".wWww.wWww.wWww.",
    "..ww...ww...ww..",
    "................",
    "................",
])

# --- cabbage (farmland crop, round layered heads) ---

sprite("block/cabbage_crop_stage0", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "...m....M...m...",
    "..gM...gm..gM...",
    "................",
    "................",
])

sprite("block/cabbage_crop_stage1", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "..Mh......Mh....",
    ".gmM..Mh.gmM..M.",
    ".GmG.gmM.GmG.gmM",
    "..G...G...G...G.",
    "................",
    "................",
])

sprite("block/cabbage_crop_stage2", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "..hh......hh....",
    ".mMMg....mMMg...",
    ".MmGg.hh.MmGg.hh",
    ".gGGg.MMg.gGGg.M",
    "..gg..Gmg..gg..G",
    ".m..m.gg..m..m.g",
    "................",
    "................",
])

sprite("block/cabbage_crop_stage3", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "...hh......hh...",
    "..hMMg....hMMg..",
    ".hMmMGg..hMmMGg.",
    ".MmGGGg..MmGGGg.",
    ".mGGgGg..mGGgGg.",
    ".gGGGg....gGGGg.",
    "..ggg......ggg..",
    ".m...mM..m...mM.",
    ".Gm..gG..Gm..gG.",
    "................",
    "................",
])

# --- onion (farmland crop, chive spears + amber bulbs at soil) ---

sprite("block/onion_crop_stage0", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "..d....d....d...",
    "..C....C....C...",
    "..c....c....c...",
    "................",
])

sprite("block/onion_crop_stage1", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "..d.........d...",
    "..C..d.d....C.d.",
    ".dC..C.C...dC.C.",
    ".CC..C.Cd..CC.C.",
    ".cC..c.CC..cC.c.",
    ".cc..c.cc..cc.c.",
    "................",
    "................",
])

sprite("block/onion_crop_stage2", [
    "................",
    "................",
    "................",
    "................",
    "...d......d.....",
    "...C..d...C..d..",
    ".d.C..C.d.C..C..",
    ".C.Cd.C.C.Cd.C.d",
    ".CdCC.CdC.CC.CdC",
    ".cCcC.cCC.cC.cCC",
    ".cCc..cCc.cc.ccC",
    ".ccc..ccc.cc..cc",
    "..c....c...c...c",
    "..y....y...y...y",
    "................",
    "................",
])

sprite("block/onion_crop_stage3", [
    "................",
    "................",
    "...d......d.....",
    "...C..d...C..d..",
    ".d.C..C...C..C..",
    ".C.Cd.C.d.Cd.C.d",
    ".CdCC.CdC.CC.CdC",
    ".CcCC.CcC.cC.CcC",
    ".cCcC.cCC.cC.cCC",
    ".ccc..ccc.cc.ccc",
    "..c....c...c..c.",
    ".yAy..yAy.yAy.yA",
    ".AAa..AAa.AAa.AA",
    "..a....a...a...a",
    "................",
    "................",
])

# --- strawberry bush (ragged foliage mound; flowers then berries) ---

sprite("block/strawberry_bush_stage0", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "......Mh........",
    "..M..gmM...M....",
    ".gmh..Gg..gmh...",
    "..G............G",
    "................",
])

sprite("block/strawberry_bush_stage1", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "......h..M......",
    "..M..mMhgmh.....",
    ".gmM.GmG.Gm..M..",
    ".GmG..gG..G.gmh.",
    "..Gg.m..m.G..Gg.",
    ".m.gGm.gGmg.m...",
    ".Gm..G...G..Gm..",
    "................",
    "................",
])

sprite("block/strawberry_bush_stage2", [
    "................",
    "................",
    "................",
    "................",
    "......W..h......",
    "..h..hYWmMh.....",
    ".mMh.mMG.Gm..W..",
    ".GmM..gG..G.mYW.",
    "..GmG.m.hm.G.Gm.",
    ".m.gGmhgMmg.m.g.",
    ".GmM.GmG.GmGmh..",
    "..Gg..gG..gG.Gg.",
    ".m..m.g..m..m...",
    ".Gm.gG..gGm.Gm..",
    "................",
    "................",
])

sprite("block/strawberry_bush_stage3", [
    "................",
    "................",
    "................",
    "................",
    "......h..h......",
    "..h..hMmmMh.....",
    ".mMh.mMG.Gm..h..",
    ".GmM..gG..G.mMh.",
    ".EeGmG.m.hm.GGm.",
    ".eRrgGmhgMmg.m..",
    ".rR.M.GmG.EeGm..",
    "..GgmEe.gG.eRrg.",
    ".m..eRr..m.rR...",
    ".Gm..rR.gGm..m..",
    "................",
    "................",
])

# --- wild onion (forage: chive spears, allium pom, half-buried bulb) ---

sprite("block/wild_onion", [
    "................",
    "................",
    "....WW..........",
    "...WdWW.........",
    "....WW..........",
    ".....C..........",
    ".....C...d......",
    "..d..C...C......",
    "..C..Cd..C..d...",
    "..Cd.CC..C..C...",
    "..cC.cC.dC..C...",
    "...c.cc.Cc.cC...",
    "...c..c.c..c....",
    "....c.c.c.c.....",
    ".....wWw........",
    ".....www........",
])

# --- withered crop (winter-kill husk: drooping dead stalks) ---

sprite("block/withered_crop", [
    "................",
    "................",
    "................",
    "....Tb..........",
    "...bB.b....TB...",
    "...B...b..Bb.b..",
    "..TB....b.B...b.",
    "..Bb.T..bB....b.",
    "..B..bB..B...b..",
    ".TB...B..B..Bb..",
    ".Bb...Bb.Bb.B...",
    ".B..T..B..B.B...",
    ".B.bB..B..BbB...",
    ".b..b..b...b....",
    ".b..b..b...b....",
    "................",
])

# --- trellis (slender wooden diamond lattice) ---

sprite("block/trellis", [
    ".K.....KK.....K.",
    ".LK...KLK...KL..",
    "..LK.KL..K.KL...",
    "...LKL....KL....",
    "...KLK....LK....",
    "..KL..K..L..K...",
    ".KL....KL....K..",
    ".K.....LK.....K.",
    ".LK...KL.K...KL.",
    "..LK.KL...K.KL..",
    "...LKL.....KL...",
    "...KLK.....LK...",
    "..KL..K...L..K..",
    ".KL....K.L....K.",
    ".k......k......k",
    ".k......k......k",
])

# --- pea crop (vine climbing the trellis laths) ---

sprite("block/pea_crop_stage0", [
    ".K.....KK.....K.",
    ".LK...KLK...KL..",
    "..LK.KL..K.KL...",
    "...LKL....KL....",
    "...KLK....LK....",
    "..KL..K..L..K...",
    ".KL....KL....K..",
    ".K.....LK.....K.",
    ".LK...KL.K...KL.",
    "..LK.KL...K.KL..",
    "...LKL.....KL...",
    "...KLK..M..LK...",
    "..KL..K.mM.L.K..",
    ".KL....KGm....K.",
    ".k......gG.....k",
    ".k......g......k",
])

sprite("block/pea_crop_stage1", [
    ".K.....KK.....K.",
    ".LK...KLK...KL..",
    "..LK.KL..K.KL...",
    "...LKL....KL....",
    "...KLK....LK....",
    "..KL..K..L..K...",
    ".KL....KL....K..",
    ".K.....LK.....K.",
    ".LK...KLMK...KL.",
    "..LK.KLmM.K.KL..",
    "..MLKL.Gm..KL...",
    "..mMKG.G...LK...",
    "..KLGmKGM..M.K..",
    ".KL..G.GmM.mM.K.",
    ".k....g.Gg.G...k",
    ".k......g..g...k",
])

sprite("block/pea_crop_stage2", [
    ".K.....KK.....K.",
    ".LK...KLK...KL..",
    "..LK.KL.MK.KL...",
    "...LKL.mM.KL....",
    ".WW.KLKMGm.LK...",
    ".YWL.mMK.L..K...",
    ".KL...GKLMWW.K..",
    ".K.mM..GKmYW..K.",
    ".LK.GmKLG.Mm.KL.",
    "..LKmGL.Gm.K.KL.",
    ".WWLKL..GmMKL...",
    ".YWMKG..G.MLK...",
    "..KLGmKGmM.m.K..",
    ".KL..G.GmM.mM.K.",
    ".k....g.Gg.G...k",
    ".k......g..g...k",
])

sprite("block/pea_crop_stage3", [
    ".K.....KK.....K.",
    ".LK...KLK...KL..",
    "..LK.KL.MK.KL...",
    "...LKL.mM.KL....",
    ".nn.KLKMGm.LK...",
    ".nV.LmMK.L..K...",
    ".vV...GKLMnn.K..",
    ".Kv.M..GKmnV..K.",
    ".LK.GmKLG.vV.KL.",
    "..LKmGL.Gm.vKKL.",
    ".nnLKL..GmMKL...",
    ".nVMKG..G.MLK...",
    ".vVLGmKGmM.m.K..",
    ".Kv..G.GmM.mM.K.",
    ".k....g.Gg.G...k",
    ".k......g..g...k",
])

# --- Phase C2 art (textures only - the blocks/items are not registered yet) ------
#
# Drawn ahead of the C2 implementation so the art library is ready. Shapes match
# the models each crop will use:
#   rice_crop_stage*          crop model (4 crossed planes), soil/water at y=15
#   corn_crop_bottom_stage*   cross model, lower block of a 2-block stalk
#   corn_crop_top_stage2/3    cross model, upper block (only exists at ages 2-3);
#                             the stalk must meet the texture edge at y=15 (top)
#                             and y=0 (bottom) so the two blocks join seamlessly
#   tomato_crop_stage*        cross model, staked vine
#   cucumber_crop_stage*      cross model, trellis climber (same lattice as peas)

sprite("block/rice_crop_stage0", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "..d...d...d..d..",
    "..C...C...C..C..",
    "..C.d.C.d.C..C.d",
    "..c.C.c.C.c..c.C",
    "..c.c.c.c.c..c.c",
    "................",
])

sprite("block/rice_crop_stage1", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "..d...d....d....",
    "..C.d.C..d.C..d.",
    "..C.C.C..C.C..C.",
    ".dC.C.Cd.C.Cd.C.",
    ".CC.c.CC.c.CC.c.",
    ".Cc.c.Cc.c.Cc.c.",
    ".cc.c.cc.c.cc.c.",
    ".cc...cc...cc...",
    ".cc...cc...cc...",
    "................",
    "................",
])

sprite("block/rice_crop_stage2", [
    "................",
    "................",
    "..d....d....d...",
    "..Cd...Cd...Cd..",
    ".dCC..dCC..dCC..",
    ".CCC..CCC..CCC..",
    ".CCCd.CCCd.CCCd.",
    ".CcCC.CcCC.CcCC.",
    ".cCcC.cCcC.cCcC.",
    ".cCcC.cCcC.cCcC.",
    ".ccc..ccc..ccc..",
    ".ccc..ccc..ccc..",
    ".cc...cc...cc...",
    ".cc...cc...cc...",
    "................",
    "................",
])

sprite("block/rice_crop_stage3", [
    "................",
    "...Y....Y....Y..",
    "..YyY..YyY..YyY.",
    "..yYy..yYy..yYy.",
    "..Yy...Yy...Yy..",
    ".dyY..dyY..dyY..",
    ".CCy..CCy..CCy..",
    ".CCCd.CCCd.CCCd.",
    ".CcCC.CcCC.CcCC.",
    ".cCcC.cCcC.cCcC.",
    ".cCcC.cCcC.cCcC.",
    ".ccc..ccc..ccc..",
    ".ccc..ccc..ccc..",
    ".cc...cc...cc...",
    "................",
    "................",
])

sprite("block/corn_crop_bottom_stage0", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "....M.....M.....",
    "...gm..M..gm....",
    "...Gm.gm..Gm....",
    "...G...G...G....",
    "..gG..gG..gG....",
    "................",
    "................",
])

sprite("block/corn_crop_bottom_stage1", [
    "................",
    "................",
    "................",
    "....M......M....",
    "...mM..M...mM...",
    "..m.G.mM..m.G...",
    ".gm.G.G...gm.G..",
    "..gGGmG.m..gGGm.",
    "...GgG.gm...GgG.",
    "...gG.mG....gG..",
    "..M.G.G...M.G...",
    ".gm.GmG..gm.Gm..",
    "..gmGgG...gmGg..",
    "...gG.G....gG.G.",
    "................",
    "................",
])

sprite("block/corn_crop_bottom_stage2", [
    "..gG.gG...gG.g..",
    "..gG..G...gG..G.",
    "..MG.mG...MG.m..",
    ".mMGmG...mMGmG..",
    ".m.GgG...m.GgG..",
    "...gG.M....gG.M.",
    "..m.G.mM..m.G.m.",
    ".gm.GmG..gm.GmG.",
    "..gmGgG...gmGgG.",
    "...GgG.m...GgG.m",
    "...gG.gm...gG.gm",
    "..M.G.G...M.G.G.",
    ".mM.GmG..mM.GmG.",
    "..gmGgG...gmGgG.",
    "...gG.G....gG.G.",
    "...gG......gG...",
])

sprite("block/corn_crop_bottom_stage3", [
    "..gG.gG...gG.g..",
    "..gG..G...gG..G.",
    "..MG.mG...MG.m..",
    ".mMGmG...mMGmG..",
    ".m.GgG...m.GgG..",
    "...gG.M....gG.M.",
    "..myGmM...myG.m.",
    ".gmYyG...gmYyGG.",
    "..gYyGg...gYyGg.",
    "..mYyG.m..mYyG.m",
    "...gG.gm...gG.gm",
    "..M.G.G...M.G.G.",
    ".mM.GmG..mM.GmG.",
    "..gmGgG...gmGgG.",
    "...gG.G....gG.G.",
    "...gG......gG...",
])

sprite("block/corn_crop_top_stage2", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "....M......M....",
    "...mM.m....mM.m.",
    "..m.GmM...m.GmM.",
    ".gm.GgG..gm.GgG.",
    "..gmG.G...gmG.G.",
    "...GgG.m...GgG.m",
    "...gG.gm...gG.gm",
    "..M.G.G...M.G.G.",
    ".mM.GmG..mM.GmG.",
    "..gG..G...gG..G.",
    "..gG.gG...gG.g..",
])

sprite("block/corn_crop_top_stage3", [
    "...T.......T....",
    "..TB.T....TB.T..",
    "..bB.B....bB.B..",
    "...BB......BB...",
    "...gB.m....gB.m.",
    "..m.GmM...m.GmM.",
    ".gm.GgG..gm.GgG.",
    "..gmG.G...gmG.G.",
    "...GgG.m...GgG.m",
    "...gG.gm...gG.gm",
    "..M.G.G...M.G.G.",
    ".mM.GmG..mM.GmG.",
    "..gmGgG...gmGgG.",
    "...GgG.....GgG..",
    "..gG..G...gG..G.",
    "..gG.gG...gG.g..",
])

sprite("block/tomato_crop_stage0", [
    "................",
    "....K...........",
    "....K...........",
    "....K...........",
    "....K...........",
    "....K...........",
    "....K...........",
    "....K...........",
    "....K...........",
    "....K...........",
    "....K...........",
    "....K..M........",
    "....K.gm........",
    "...MmG.G........",
    "..gmK...........",
    "....k...........",
])

sprite("block/tomato_crop_stage1", [
    "................",
    "....K...........",
    "....K...........",
    "....K...........",
    "....K...........",
    "....K...........",
    "....K..M........",
    "....KMgm........",
    "...MmG.G........",
    "..gmKG..........",
    "...GKgm.M.......",
    "..m.KG.gm.......",
    ".gmMKgG.G.......",
    "...GK.G.........",
    "..gmK...........",
    "....k...........",
])

sprite("block/tomato_crop_stage2", [
    "................",
    "....K...........",
    "....K.W.........",
    "...MKYW.........",
    "..gmKG..........",
    "...GKgm.W.......",
    "....KG.WY.......",
    "..W.KMgm........",
    ".WYMmG.G........",
    "..gmKG..M.......",
    "...GKgm.mM......",
    "..m.KG.gG.......",
    ".gmMKgG.G.......",
    "...GK.G.........",
    "..gmK...........",
    "....k...........",
])

sprite("block/tomato_crop_stage3", [
    "................",
    "....K...........",
    "....K.M.........",
    "...MKgm.........",
    "..gmKG.rr.......",
    "...GKg.rEe......",
    "..rrKG.rRe......",
    ".rEeKMgmrr......",
    ".rReMmG.G.......",
    ".Prr.KG..M......",
    "...GKgm.mM......",
    "..m.KGrr.G......",
    ".gmMKrEe........",
    "...GKrRe.G......",
    "..gmK.rr........",
    "....k...........",
])

sprite("block/cucumber_crop_stage0", [
    ".K.....KK.....K.",
    ".LK...KLK...KL..",
    "..LK.KL..K.KL...",
    "...LKL....KL....",
    "...KLK....LK....",
    "..KL..K..L..K...",
    ".KL....KL....K..",
    ".K.....LK.....K.",
    ".LK...KL.K...KL.",
    "..LK.KL...K.KL..",
    "...LKL.....KL...",
    "...KLK.h...LK...",
    "..KL..KMm..L.K..",
    ".KL....KmG....K.",
    ".k......gG.....k",
    ".k......g......k",
])

sprite("block/cucumber_crop_stage1", [
    ".K.....KK.....K.",
    ".LK...KLK...KL..",
    "..LK.KL..K.KL...",
    "...LKL....KL....",
    "...KLK....LK....",
    "..KL..K..L..K...",
    ".KL....KL....K..",
    ".K.....LK.....K.",
    ".LK...KLhK...KL.",
    "..LK.KLMm.K.KL..",
    "..hLKL.Gm..KL...",
    "..MmKG.G...LK...",
    "..KLGmKGh..h.K..",
    ".KL..G.GmM.Mm.K.",
    ".k....g.Gg.G...k",
    ".k......g..g...k",
])

sprite("block/cucumber_crop_stage2", [
    ".K.....KK.....K.",
    ".LK...KLK...KL..",
    "..LK.KL.hK.KL...",
    "...LKL.Mm.KL....",
    ".YY.KLKhGm.LK...",
    ".yYL.MmK.L..K...",
    ".KL...GKLhYY.K..",
    ".K.Mm..GKmyY..K.",
    ".LK.GmKLG.Mm.KL.",
    "..LKmGL.Gm.K.KL.",
    ".YYLKL..GmMKL...",
    ".yYMKG..G.hLK...",
    "..KLGmKGmM.m.K..",
    ".KL..G.GmM.Mm.K.",
    ".k....g.Gg.G...k",
    ".k......g..g...k",
])

sprite("block/cucumber_crop_stage3", [
    ".K.....KK.....K.",
    ".LK...KLK...KL..",
    "..LK.KL.hK.KL...",
    "...LKL.Mm.KL....",
    ".gG.KLKhGm.LK...",
    ".gGvLMmK.L..K...",
    ".gGv..GKLhgG.K..",
    ".KgV...GKmgGv.K.",
    ".LK.GmKLG.gGvKL.",
    "..LKmGL.Gm.gVKL.",
    ".gGLKL..GmMKL...",
    ".gGvKG..G.hLK...",
    ".gGvGmKGmM.m.K..",
    ".KgV.G.GmM.Mm.K.",
    ".k....g.Gg.G...k",
    ".k......g..g...k",
])

# --- items ---

sprite("item/turnip", [
    "................",
    "................",
    "......gm........",
    "....m.gG.m......",
    "....GggGgG......",
    ".....gGGg.......",
    "....PpppP.......",
    "...PpWWWpP......",
    "..pWWWWWWwp.....",
    "..pWWWWwWwp.....",
    "..pWwWWWwwp.....",
    "...wWWwwww......",
    "....wwwww.......",
    ".....www........",
    "......w.........",
    "................",
])

sprite("item/cabbage", [
    "................",
    "................",
    "....ghhg........",
    "..ghhMMhhg......",
    ".ghMMmMMMhg.....",
    ".gMMmGGmMMg.....",
    "gMMmGGGGmMMg....",
    "gMmGGgGGGmMg....",
    "gMmGGGgGGmMg....",
    "gmGGgGGGgGmg....",
    ".gGGGgGGGGg.....",
    ".mgGGGGGGgm.....",
    "Gmg.gggg.gmG....",
    ".gG......Gg.....",
    "................",
    "................",
])

sprite("item/onion", [
    "................",
    "................",
    "......d.........",
    "......C.........",
    ".....aa.........",
    "....ayAya.......",
    "...ayyAyAa......",
    "..aAyyAyAAa.....",
    "..aAyAyAyAa.....",
    "..aAyAyAAAa.....",
    "..aAAyAyAaa.....",
    "...aAyAAaa......",
    "....aaAaa.......",
    ".....w.w........",
    "....w..w........",
    "................",
])

sprite("item/cherries", [
    "................",
    "......gk........",
    ".....k..k.......",
    "....k...gm......",
    "...k....gMm.....",
    "...k.....gm.....",
    "..rr......k.....",
    ".rEer...rrk.....",
    "rREeer.rEerr....",
    "rRREer.rREeer...",
    "rRRRer.rRREer...",
    ".rRRr..rRRRr....",
    "..rr...rRRr.....",
    "........rr......",
    "................",
    "................",
])

sprite("item/strawberries", [
    "................",
    "................",
    ".....mGm........",
    "...GmgGgmG......",
    "....gGgGg.......",
    "...rEeRer.......",
    "..rEeRYRer......",
    "..rEeRRRRr......",
    "..rReYRYRr......",
    "..rRRRRRr.......",
    "...rRYRr........",
    "...rRRRr........",
    "....rRr.........",
    ".....r..........",
    "................",
    "................",
])

sprite("item/peas", [
    "................",
    "................",
    "............gm..",
    "...........Gg...",
    "..........vV....",
    ".......vvVVv....",
    ".....vVnnVVv....",
    "...vVnnVnnVv....",
    "..vVnVVVnnv.....",
    ".vVnnVVnnVv.....",
    ".vVVnnVVVv......",
    ".vvVVVVvv.......",
    "..vvvvvv........",
    "................",
    "................",
    "................",
])

sprite("item/turnip_seeds", [
    "................",
    "................",
    "................",
    "....ay..........",
    "....aa...ay.....",
    ".........aa.....",
    "..ay............",
    "..aa....ay......",
    "........aa......",
    ".....ay.........",
    ".....aa...ay....",
    "..........aa....",
    "...ay...........",
    "...aa...........",
    "................",
    "................",
])

sprite("item/cabbage_seeds", [
    "................",
    "................",
    "................",
    "....bT..........",
    "....bb...bT.....",
    ".........bb.....",
    "..bT............",
    "..bb....bT......",
    "........bb......",
    ".....bT.........",
    ".....bb...bT....",
    "..........bb....",
    "...bT...........",
    "...bb...........",
    "................",
    "................",
])

sprite("item/pea_seeds", [
    "................",
    "................",
    "................",
    "....Vn..........",
    "...vVV...Vn.....",
    "....vv..vVV.....",
    ".........vv.....",
    "..Vn............",
    ".vVV....Vn......",
    "..vv...vVV......",
    "........vv......",
    ".....Vn.........",
    "....vVV..Vn.....",
    ".....vv.vVV.....",
    ".........vv.....",
    "................",
])

# Phase C2 items (not yet registered).

sprite("item/rice", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "......wW........",
    "....wWWwW.......",
    "...wWwWWWw......",
    "..wWWwWwWWw.....",
    ".wWwWWWWwWWw....",
    ".wwWwWWwWWww....",
    "..wwwwWwwww.....",
    "................",
    "................",
    "................",
    "................",
])

sprite("item/rice_seeds", [
    "................",
    "................",
    "................",
    "....yY..........",
    "....yy...yY.....",
    ".........yy.....",
    "..yY............",
    "..yy....yY......",
    "........yy......",
    ".....yY.........",
    ".....yy...yY....",
    "..........yy....",
    "...yY...........",
    "...yy...........",
    "................",
    "................",
])

sprite("item/corn", [
    "................",
    "..........Mm....",
    ".........MmG....",
    "........YyM.....",
    ".......yYYy.....",
    "......YyYym.....",
    ".....yYYyYG.....",
    "....YyYyYy......",
    "...yYYyYym......",
    "...YyYyYG.......",
    "..myYYyy........",
    "..GmYym.........",
    ".gGmMm..........",
    "..gGm...........",
    "................",
    "................",
])

sprite("item/corn_seeds", [
    "................",
    "................",
    "................",
    "....Yy..........",
    "....yy...Yy.....",
    ".........yy.....",
    "..Yy............",
    "..yy....Yy......",
    "........yy......",
    ".....Yy.........",
    ".....yy...Yy....",
    "..........yy....",
    "...Yy...........",
    "...yy...........",
    "................",
    "................",
])

sprite("item/tomato", [
    "................",
    "................",
    "......m.g.......",
    ".....gmgm.......",
    "....rgmgr.......",
    "...rEegRer......",
    "..rEeRRRRer.....",
    "..rERRRRRRr.....",
    "..rReRRRRRr.....",
    "..rRRRRRRer.....",
    "...rRRRRer......",
    "....rRRer.......",
    ".....rrr........",
    "................",
    "................",
    "................",
])

sprite("item/tomato_seeds", [
    "................",
    "................",
    "................",
    "....wT..........",
    "....ww...wT.....",
    ".........ww.....",
    "..wT............",
    "..ww....wT......",
    "........ww......",
    ".....wT.........",
    ".....ww...wT....",
    "..........ww....",
    "...wT...........",
    "...ww...........",
    "................",
    "................",
])

sprite("item/cucumber", [
    "................",
    "................",
    "...........gm...",
    "..........gG....",
    ".........vGg....",
    "........nVGg....",
    ".......nVvGg....",
    "......nVvGg.....",
    ".....nVvGg......",
    "....nVvGg.......",
    "...nVvGg........",
    "...vVGg.........",
    "..gvGg..........",
    "...gg...........",
    "................",
    "................",
])

sprite("item/cucumber_seeds", [
    "................",
    "................",
    "................",
    "....nV..........",
    "....vv...nV.....",
    ".........vv.....",
    "..nV............",
    "..vv....nV......",
    "........vv......",
    ".....nV.........",
    ".....vv...nV....",
    "..........vv....",
    "...nV...........",
    "...vv...........",
    "................",
    "................",
])

# ---------------------------------------------------------------------------


def write_png(path, px):
    W = H = 16

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff))

    raw = bytearray()
    for y in range(H):
        raw.append(0)
        for x in range(W):
            raw += bytes(px[y][x])
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(bytes(raw), 9)))
        f.write(chunk(b"IEND", b""))


def main():
    (TEXTURES / "block").mkdir(parents=True, exist_ok=True)
    (TEXTURES / "item").mkdir(parents=True, exist_ok=True)
    for name, rows in SPRITES.items():
        px = [[PALETTE[ch] for ch in row] for row in rows]
        write_png(TEXTURES / f"{name}.png", px)
    print(f"wrote {len(SPRITES)} textures")


if __name__ == "__main__":
    main()
