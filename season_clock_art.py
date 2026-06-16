#!/usr/bin/env python3
"""Generate the Season Clock item art + model JSON (a smooth, continuous dial).

Like the vanilla clock (64 day-time frames), the season hand moves through many frames rather
than snapping: N frames sweep the redstone hand once around the dial over a full year, four
seasonal pips sit at the compass points (spring N, summer E, autumn S, winter W), and the pip
of the season the hand is currently in is brightened. The model is driven by the custom
`fallow:season` property, which returns a continuous fraction-through-the-year in [0, 1)
(see SeasonClockModelProperty / FallowClientSeasons.yearFraction).

Re-run after editing:  python3 season_clock_art.py
Writes textures, per-frame item models, and the range_dispatch item definition under
src/main/resources/assets/fallow/.
"""
import glob
import math
import os
import struct
import zlib

W = H = 16
N = 32  # frames around the dial - fine enough that adjacent hand angles differ by ~1px at the tip

ASSETS = "src/main/resources/assets/fallow"
TEX = f"{ASSETS}/textures/item"
MODELS = f"{ASSETS}/models/item"
ITEMS = f"{ASSETS}/items"

# palette (r, g, b, a)
TRANSPARENT = (0, 0, 0, 0)
# Copper rim - vanilla copper_ingot palette, light top-left -> dark bottom-right, with a dark edge
# outline (the convention that makes vanilla items read as objects, cf. the clock's #752802 rim).
COPPER_SPEC = (252, 195, 182, 255)    # brightest specular, extreme top-left only
COPPER_HI = (252, 153, 130, 255)      # #fc9982
COPPER_LIGHT = (231, 124, 86, 255)    # #e77c56
COPPER_MID = (193, 90, 54, 255)       # #c15a36 (the copper base tone)
COPPER_DARK = (156, 69, 41, 255)      # #9c4529
COPPER_EDGE = (109, 52, 33, 255)      # #6d3421 - outline ring
# Dark copper-case interior so the pips and redstone hand read clearly.
FACE = (58, 44, 37, 255)
FACE_SHADE = (43, 32, 27, 255)        # thin shadow ring under the rim
# Redstone, muted to vanilla redstone reds.
HUB = (138, 26, 23, 255)
HUB_LIT = (255, 106, 82, 255)
HAND = (192, 41, 31, 255)
HAND_TIP = (255, 106, 82, 255)

# Seasonal pips at the compass points, in cycle order (spring N, summer E, autumn S, winter W).
# Muted, vanilla-weight tones; the active season's pip is shown full-bright, the rest dimmed.
PIPS = [
    ((106, 154, 78, 255), (7, 3)),    # spring  - top    (muted leaf green)
    ((214, 167, 62, 255), (11, 7)),   # summer  - right  (muted gold)
    ((179, 90, 42, 255), (7, 11)),    # autumn  - bottom (rust)
    ((184, 198, 207, 255), (3, 7)),   # winter  - left   (pale slate)
]

CX = CY = 7.5  # dial center


def dim(color, f):
    r, g, b, a = color
    return (int(r * f), int(g * f), int(b * f), a)


def render(frame):
    """One frame: hand at angle frame/N around the dial (clockwise from north)."""
    angle = (frame / N) * 2.0 * math.pi          # 0 = north, increasing clockwise
    season = int((frame / N) * 4) % 4            # which seasonal quadrant the hand is in
    px = [TRANSPARENT] * (W * H)

    def put(x, y, c):
        if 0 <= x < W and 0 <= y < H:
            px[y * W + x] = c

    # copper case: dark outline ring, beveled copper rim, thin inner shadow, dark face
    for y in range(H):
        for x in range(W):
            dx, dy = x - CX, y - CY
            d = (dx * dx + dy * dy) ** 0.5
            if d > 7.4:
                continue
            if d > 6.6:
                put(x, y, COPPER_EDGE)                       # dark outline ring
            elif d > 5.4:
                bevel = (dx + dy) / d                        # ~ -1.4 top-left ... +1.4 bottom-right
                if bevel <= -1.3:
                    put(x, y, COPPER_SPEC)                   # specular catch - corner only
                elif bevel <= -0.7:
                    put(x, y, COPPER_HI)
                elif bevel <= 0.0:
                    put(x, y, COPPER_LIGHT)
                elif bevel <= 0.8:
                    put(x, y, COPPER_MID)
                else:
                    put(x, y, COPPER_DARK)
            elif d > 4.8:
                put(x, y, FACE_SHADE)                        # shadow ring under the rim
            else:
                put(x, y, FACE)

    # seasonal pips (the one for the current quadrant full-bright, the rest dimmed)
    for i, (color, (ax, ay)) in enumerate(PIPS):
        shown = color if i == season else dim(color, 0.6)
        for oy in range(2):
            for ox in range(2):
                put(ax + ox, ay + oy, shown)

    # redstone needle: sweep from the hub out toward the pips, tip brightened. Clockwise from north,
    # so x = sin(angle), y = -cos(angle).
    sin_a, cos_a = math.sin(angle), math.cos(angle)
    r = 1.3
    while r <= 4.4:
        x = round(CX + r * sin_a)
        y = round(CY - r * cos_a)
        put(x, y, HAND_TIP if r >= 3.9 else HAND)
        r += 0.4

    # redstone hub (2x2) with one lit corner, drawn last so the needle tucks under it
    for oy in range(2):
        for ox in range(2):
            put(7 + ox, 7 + oy, HUB)
    put(7, 7, HUB_LIT)
    return px


def write_png(path, px):
    raw = bytearray()
    for y in range(H):
        raw.append(0)  # filter: none
        for x in range(W):
            raw += bytes(px[y * W + x])

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff))

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(bytes(raw), 9)))
        f.write(chunk(b"IEND", b""))


def preview(px):
    glyph = {COPPER_SPEC: "@", COPPER_HI: "C", COPPER_LIGHT: "c", COPPER_MID: "o",
             COPPER_DARK: "x", COPPER_EDGE: "#", FACE: ".", FACE_SHADE: ",",
             HUB: "U", HUB_LIT: "O", HAND: "h", HAND_TIP: "H"}
    return "\n".join(
        "".join(" " if px[y * W + x] == TRANSPARENT else glyph.get(px[y * W + x], "*")
                for x in range(W))
        for y in range(H))


def main():
    for d in (TEX, MODELS, ITEMS):
        os.makedirs(d, exist_ok=True)
    # clean any previous frames (incl. the old named spring/summer/autumn/winter set)
    for old in glob.glob(f"{TEX}/season_clock_*.png") + glob.glob(f"{MODELS}/season_clock_*.json"):
        os.remove(old)

    entries = []
    for i in range(N):
        name = f"season_clock_{i:02d}"
        write_png(f"{TEX}/{name}.png", render(i))
        with open(f"{MODELS}/{name}.json", "w") as f:
            f.write('{\n  "parent": "minecraft:item/generated",\n'
                    '  "textures": {\n    "layer0": "fallow:item/%s"\n  }\n}\n' % name)
        entries.append('      { "threshold": %.5f, "model": '
                       '{ "type": "minecraft:model", "model": "fallow:item/%s" } }'
                       % (i / N, name))

    item_def = (
        '{\n  "model": {\n    "type": "minecraft:range_dispatch",\n'
        '    "property": "fallow:season",\n    "entries": [\n'
        + ",\n".join(entries)
        + '\n    ],\n    "fallback": { "type": "minecraft:model", '
          '"model": "fallow:item/season_clock_00" }\n  }\n}\n')
    with open(f"{ITEMS}/season_clock.json", "w") as f:
        f.write(item_def)

    for i in (0, 4, 8, 12, 16, 20, 24, 28):  # spot-check the sweep (N, NE, E, SE, S, SW, W, NW)
        print(f"=== frame {i:02d} (angle {i / N * 360:5.1f}°) ===")
        print(preview(render(i)))
    print(f"\nwrote {N} frames + models + season_clock.json under {ASSETS}/")


if __name__ == "__main__":
    main()
