#!/usr/bin/env python3
"""Generate the Fallow logo: a pixel-art scene of one tree whose canopy runs through the four
seasons left to right (spring, summer, autumn, winter snow) over a small sky-and-ground square.
Pixel art keeps it on-theme for a Minecraft mod and simple to edit: change the LOGICAL grid below
and re-run. Output is nearest-neighbour upscaled so the blocks stay crisp.

    python3 icon_art.py

Writes icon.png (repo root, Modrinth/GitHub page) and src/main/resources/assets/fallow/icon.png
(referenced by fabric.mod.json, shown in the mods list).
"""
import struct
import zlib

N = 32            # logical grid (NxN), then scaled up
SCALE = 8         # -> 256x256 output

# palette ------------------------------------------------------------------------------------------
SKY_TOP = (124, 178, 214)
SKY_BOT = (180, 212, 234)
GRASS = (108, 170, 80)
GRASS_DK = (84, 138, 62)
DIRT = (110, 80, 56)
DIRT_DK = (88, 62, 42)
TRUNK = (112, 78, 50)
TRUNK_DK = (78, 52, 32)
SPRING = (126, 198, 76)
SUMMER = (72, 152, 50)
AUTUMN = (220, 142, 44)
WINTER = (228, 238, 245)
OUTLINE = (44, 58, 40)        # canopy/trunk edge

# canopy geometry
CX, CY, RX, RY = 15.5, 11.0, 12.0, 9.0


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def in_canopy(x, y):
    return ((x - CX) / RX) ** 2 + ((y - CY) / RY) ** 2 <= 1.0


def season_color(x):
    if x < 10:
        return SPRING
    if x < 16:
        return SUMMER
    if x < 22:
        return AUTUMN
    return WINTER


def render():
    grid = [[None] * N for _ in range(N)]

    # sky
    for y in range(N):
        sky = lerp(SKY_TOP, SKY_BOT, y / (N - 1))
        for x in range(N):
            grid[y][x] = sky

    # ground: two grass rows then dirt, with a darker speckle row for texture
    for y in range(N):
        for x in range(N):
            if y >= 27:
                grid[y][x] = (DIRT_DK if (x * 7 + y) % 5 == 0 else DIRT) + (255,)
            elif y >= 25:
                grid[y][x] = (GRASS_DK if (x + y) % 3 == 0 else GRASS) + (255,)

    # trunk (rises from the grass into the canopy base)
    for y in range(18, 27):
        for x in range(15, 18):
            grid[y][x] = (TRUNK_DK + (255,)) if x == 17 else (TRUNK + (255,))

    # canopy: four seasonal strips, with a subtle darker lower third for form
    for y in range(N):
        for x in range(N):
            if in_canopy(x, y):
                c = season_color(x)
                if y > CY + RY * 0.35:
                    c = tuple(round(v * 0.86) for v in c)
                grid[y][x] = c + (255,)

    # 1px dark outline around the canopy + trunk silhouette
    solid = [[grid[y][x] is not None and grid[y][x][:3] not in
              (SKY_TOP, SKY_BOT, GRASS, GRASS_DK, DIRT, DIRT_DK) and
              (in_canopy(x, y) or (15 <= x <= 17 and 18 <= y < 27))
              for x in range(N)] for y in range(N)]
    out = [row[:] for row in grid]
    for y in range(N):
        for x in range(N):
            if not solid[y][x]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < N and 0 <= ny < N and not solid[ny][nx] and ny < 27:
                    out[y][x] = OUTLINE + (255,)
                    break
    return out


def scale(grid):
    px = []
    for y in range(N):
        row = []
        for x in range(N):
            row.append(grid[y][x])
        for _ in range(SCALE):
            for c in row:
                px.extend([c] * SCALE)
    return px


def write_png(path, px, w, h):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw += bytes(px[y * w + x])

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff))

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(bytes(raw), 9)))
        f.write(chunk(b"IEND", b""))


def preview():
    # geometry-based, so it shows the silhouette regardless of shading
    lines = []
    for y in range(N):
        line = ""
        for x in range(N):
            if in_canopy(x, y):
                line += "sSaw"[0 if x < 10 else 1 if x < 16 else 2 if x < 22 else 3]
            elif 15 <= x <= 17 and 18 <= y < 27:
                line += "|"
            elif y >= 27:
                line += "="
            elif y >= 25:
                line += "."
            else:
                line += " "
        lines.append(line)
    return "\n".join(lines)


def main():
    grid = render()
    print(preview())
    out = N * SCALE
    px = scale(grid)
    for path in ("icon.png", "src/main/resources/assets/fallow/icon.png"):
        write_png(path, px, out, out)
    print(f"\nwrote {out}x{out} icon.png + assets copy")


if __name__ == "__main__":
    main()
