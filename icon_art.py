#!/usr/bin/env python3
"""Generate the Fallow project/mod icon: a 128x128 disc split into four seasonal quadrants
(spring N, summer E, autumn S, winter W) with a dark rim, echoing the Season Clock's four-season
motif. Re-run after editing:  python3 icon_art.py

Writes icon.png (repo root, for the Modrinth/GitHub page) and a copy under
src/main/resources/assets/fallow/icon.png (referenced by fabric.mod.json, shown in the mods list).
"""
import math
import struct
import zlib

W = H = 128
CX = CY = 63.5
R = 60.0          # disc radius
RIM = 5.0         # dark rim thickness
DIVIDER = 1.6     # half-width of the dark diagonal dividers between quadrants

TRANSPARENT = (0, 0, 0, 0)
RIM_COLOR = (38, 33, 30, 255)
DIVIDER_COLOR = (38, 33, 30, 255)
# Seasonal quadrant colors (vivid enough to read at icon size), in N, E, S, W order.
SPRING = (104, 178, 80, 255)
SUMMER = (240, 198, 72, 255)
AUTUMN = (206, 102, 44, 255)
WINTER = (176, 202, 222, 255)


def quadrant(dx, dy):
    # dy is screen-down; "top" is dy < 0. Split on the diagonals so quadrants face N/E/S/W.
    if abs(abs(dx) - abs(dy)) <= DIVIDER:
        return DIVIDER_COLOR
    if abs(dy) > abs(dx):
        return SPRING if dy < 0 else AUTUMN
    return SUMMER if dx > 0 else WINTER


def render():
    px = [TRANSPARENT] * (W * H)
    for y in range(H):
        for x in range(W):
            dx, dy = x - CX, y - CY
            d = math.hypot(dx, dy)
            if d > R:
                continue
            px[y * W + x] = RIM_COLOR if d > R - RIM else quadrant(dx, dy)
    return px


def write_png(path, px):
    raw = bytearray()
    for y in range(H):
        raw.append(0)
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


def main():
    px = render()
    for path in ("icon.png", "src/main/resources/assets/fallow/icon.png"):
        write_png(path, px)
    print("wrote icon.png + src/main/resources/assets/fallow/icon.png (128x128)")


if __name__ == "__main__":
    main()
