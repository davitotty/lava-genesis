#!/usr/bin/env python3
"""Lava Genesis - land module asset and data generator.

One content table for every land block and item. Generates:
  * 16x16 pixel-art textures (Pillow), animated strips + .png.mcmeta for the glowing lava blocks/items
  * blockstates, block models, item models and item model definitions
  * loot tables, recipes, block/item tags (vanilla-tag additions go into the built-in pack
    resourcepacks/land_tags so they never collide with another module's file), worldgen features
  * lang_parts/land.json (English)
and finally audits that every model / texture / translation that is referenced exists, and that every block and
item registered in the Java sources has its assets.

Run from anywhere:  python tools/gen_land.py
Only writes files owned by the land module.
"""
import json
import math
import os
import random
import re
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
NS = "lavagenesis"
ASSETS = os.path.join(RES, "assets", NS)
DATA = os.path.join(RES, "data", NS)
PACK = os.path.join(RES, "resourcepacks", "land_tags")
LANG = os.path.join(ROOT, "lang_parts", "land.json")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "lavagenesis")

WRITTEN = []


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")
    WRITTEN.append(path)


def nid(path):
    return f"{NS}:{path}"


# =====================================================================================================
# Texture toolkit
# =====================================================================================================
S = 16


class TileNoise:
    """Value noise that tiles seamlessly over a 16x16 texture (any coordinate wraps)."""

    def __init__(self, seed, cells):
        rnd = random.Random(seed)
        self.c = cells
        self.v = [[rnd.random() for _ in range(cells)] for _ in range(cells)]

    def __call__(self, x, y):
        fx = (x / S) * self.c
        fy = (y / S) * self.c
        x0, y0 = math.floor(fx), math.floor(fy)
        tx, ty = fx - x0, fy - y0
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        c = self.c
        a = self.v[y0 % c][x0 % c]
        b = self.v[y0 % c][(x0 + 1) % c]
        d = self.v[(y0 + 1) % c][x0 % c]
        e = self.v[(y0 + 1) % c][(x0 + 1) % c]
        return (a * (1 - tx) + b * tx) * (1 - ty) + (d * (1 - tx) + e * tx) * ty


class Fbm:
    def __init__(self, seed, octaves=((2, 0.5), (4, 0.3), (8, 0.2))):
        self.layers = [(TileNoise(seed * 31 + i, c), w) for i, (c, w) in enumerate(octaves)]

    def __call__(self, x, y):
        return sum(n(x, y) * w for n, w in self.layers) / sum(w for _, w in self.layers)


def clamp01(t):
    return 0.0 if t < 0 else 1.0 if t > 1 else t


def ramp(colors, t):
    """Pick a palette entry (pixel-art style quantisation) for t in 0..1."""
    i = int(clamp01(t) * len(colors))
    return colors[min(i, len(colors) - 1)]


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def scale(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c[:3]) + tuple(c[3:])


def new(w=S, h=S, fill=(0, 0, 0, 0)):
    return Image.new("RGBA", (w, h), fill)


def put(img, x, y, c, oy=0):
    if 0 <= x < S and 0 <= y < S:
        img.putpixel((int(x), int(y) + oy), c if len(c) == 4 else tuple(c) + (255,))


def wrapd(ax, ay, bx, by):
    dx = abs(ax - bx)
    dy = abs(ay - by)
    dx = min(dx, S - dx)
    dy = min(dy, S - dy)
    return math.hypot(dx, dy)


def voronoi(points, x, y):
    ds = sorted(wrapd(px, py, x + 0.5, y + 0.5) for px, py in points)
    return ds[0], ds[1]


def inside_poly(poly, x, y):
    c = False
    n = len(poly)
    for i in range(n):
        x1, y1 = poly[i]
        x2, y2 = poly[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
            c = not c
    return c


def outline(img, color, frames=1):
    """Dark 1px outline around opaque pixels of each frame (for items)."""
    for f in range(frames):
        oy = f * S
        src = [[img.getpixel((x, y + oy))[3] > 0 for x in range(S)] for y in range(S)]
        for y in range(S):
            for x in range(S):
                if src[y][x]:
                    continue
                if any(0 <= x + dx < S and 0 <= y + dy < S and src[y + dy][x + dx]
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    img.putpixel((x, y + oy), color + (255,))


TEXTURES = {}   # "block/x" or "item/x" -> (Image, mcmeta or None)


def tex(name, img, anim=None):
    TEXTURES[name] = (img, anim)


def strip(frames):
    img = new(S, S * len(frames))
    for i, f in enumerate(frames):
        img.paste(f, (0, i * S))
    return img


# ---- palettes (DESIGN.md: one shared look) ----
BASALT = [(18, 20, 26), (26, 29, 36), (35, 38, 47), (46, 50, 60), (60, 65, 76)]
GLOW = [(110, 20, 8), (165, 34, 10), (215, 66, 14), (247, 118, 22), (255, 170, 44), (255, 216, 96), (255, 246, 196)]
CRUST = [(40, 22, 20), (54, 28, 24), (70, 36, 28), (88, 46, 32)]
SAND = [(15, 15, 20), (21, 21, 27), (27, 27, 35), (35, 35, 45)]
OLIVINE = [(36, 64, 16), (66, 104, 28), (108, 150, 40), (158, 196, 66), (214, 238, 136)]
SULFUR = [(122, 92, 18), (176, 140, 26), (226, 196, 52), (246, 228, 104), (255, 248, 176)]
GLASS = [(8, 7, 12), (16, 13, 22), (26, 21, 36), (44, 34, 64), (80, 60, 122), (128, 100, 176)]
LUSH = [(30, 62, 26), (42, 84, 32), (58, 108, 40), (80, 134, 50), (112, 164, 64)]
ASH = [(82, 80, 86), (96, 94, 100), (110, 108, 114), (126, 124, 130), (144, 142, 148)]
SOIL = [(42, 28, 25), (54, 36, 29), (66, 44, 33), (80, 54, 38)]


def basalt_pixel(fb, fine, x, y, light=0.0):
    t = fb(x, y) * 0.75 + fine(x, y) * 0.25 + light
    return ramp(BASALT, (t - 0.28) * 2.1)


# ---- rock ----
def t_fresh_basalt(seed=1):
    img = new()
    fb, fine = Fbm(seed), TileNoise(seed + 5, 16)
    warp = TileNoise(seed + 9, 4)
    rnd = random.Random(seed)
    for y in range(S):
        for x in range(S):
            c = basalt_pixel(fb, fine, x, y)
            # ropy pahoehoe folds
            if math.sin((y + warp(x, y) * 6) * 1.25) > 0.88:
                c = ramp(BASALT, BASALT.index(c) / len(BASALT) + 0.25)
            put(img, x, y, c)
    for _ in range(7):  # gas vesicles
        x, y = rnd.randrange(S), rnd.randrange(S)
        put(img, x, y, (11, 12, 16))
        put(img, (x + 1) % S, (y + 1) % S, BASALT[3])
    return img


def t_pillow_lava():
    img = new()
    pillows = [(4.0, 3.5, 5.2, 3.6), (12.5, 4.5, 4.6, 3.4), (8.0, 11.0, 5.6, 3.9), (0.5, 12.0, 4.2, 3.3), (15.5, 12.5, 3.2, 3.0)]
    fine = TileNoise(41, 16)
    for y in range(S):
        for x in range(S):
            best, bdx, bdy = -9, 0, 0
            for cx, cy, rx, ry in pillows:
                dx = (x + 0.5 - cx + S / 2) % S - S / 2
                dy = (y + 0.5 - cy + S / 2) % S - S / 2
                v = 1 - (dx / rx) ** 2 - (dy / ry) ** 2
                if v > best:
                    best, bdx, bdy = v, dx / rx, dy / ry
            if best <= 0:
                c = (10, 10, 14)
            elif best < 0.2:
                c = (21, 21, 27)  # glassy rind
            else:
                light = best * 0.7 - (bdx + bdy) * 0.28 + (fine(x, y) - 0.5) * 0.25
                c = ramp([(24, 28, 30), (34, 39, 42), (46, 52, 56), (60, 67, 72), (80, 88, 92)], light)
            put(img, x, y, c)
    for cx, cy, rx, ry in pillows:  # glossy highlight
        put(img, int((cx - rx * 0.35) % S), int((cy - ry * 0.45) % S), (104, 112, 118))
    return img


def t_glassy_basalt():
    img = new()
    n, warp = TileNoise(51, 4), TileNoise(52, 8)
    rnd = random.Random(53)
    for y in range(S):
        for x in range(S):
            t = n(x, y)
            c = ramp(GLASS[:3], t)
            s = math.sin((x + y) * 0.62 + warp(x, y) * 5)
            if s > 0.9:
                c = GLASS[5]
            elif s > 0.72:
                c = GLASS[4]
            elif s > 0.5:
                c = GLASS[3]
            put(img, x, y, c)
    for _ in range(4):
        x, y = rnd.randrange(S), rnd.randrange(S)
        put(img, x, y, (206, 196, 236))
    return img


def t_olivine_ore():
    img = t_fresh_basalt(seed=61)
    clusters = [(3, 3), (11, 2), (6, 8), (13, 10), (2, 13), (9, 13)]
    rnd = random.Random(62)
    for cx, cy in clusters:
        pts = [(0, 0), (1, 0), (0, 1)] + rnd.sample([(1, 1), (-1, 0), (0, -1), (2, 0), (1, -1)], 2)
        for dx, dy in pts:
            put(img, (cx + dx) % S, (cy + dy) % S, OLIVINE[1 + (dx + dy + 1) % 3])
        put(img, cx, cy, OLIVINE[4])
        put(img, (cx + 1) % S, (cy + 1) % S, OLIVINE[0])
    return img


def t_basalt_bricks():
    img = new()
    fb, fine = Fbm(71), TileNoise(72, 16)
    for y in range(S):
        for x in range(S):
            row = y // 4
            off = 4 if row % 2 else 0
            bx, by = (x + off) % 8, y % 4
            if by == 3 or bx == 7:
                c = (13, 14, 18)
            else:
                light = 0.18 if (by == 0 or bx == 0) else -0.12 if (by == 2 or bx == 6) else 0.0
                c = basalt_pixel(fb, fine, x, y, light)
            put(img, x, y, c)
    return img


# ---- lava ----
COOL_GLOW = [
    [(240, 110, 20), (255, 170, 40), (255, 226, 110), (255, 250, 205)],
    [(170, 45, 12), (225, 90, 20), (255, 160, 35), (255, 215, 90)],
    [(110, 24, 10), (160, 40, 12), (220, 80, 18), (255, 150, 40)],
    [(70, 18, 12), (100, 22, 10), (150, 34, 12), (200, 60, 16)],
]
COOL_CRUST = [
    [(44, 22, 20), (58, 28, 24), (74, 34, 26), (92, 44, 30)],
    [(36, 22, 22), (48, 26, 24), (62, 32, 28), (78, 40, 32)],
    [(30, 26, 30), (40, 30, 32), (50, 36, 36), (64, 42, 40)],
    [(26, 27, 34), (34, 34, 42), (44, 42, 50), (56, 52, 58)],
]
COOL_W = [2.6, 1.8, 1.1, 0.6]


def t_cooling_lava(age, frames=4):
    rnd = random.Random(101)
    pts = [(rnd.uniform(0, S), rnd.uniform(0, S)) for _ in range(7)]
    wn, fb, phase = TileNoise(102, 4), Fbm(103), TileNoise(104, 4)
    out = []
    for f in range(frames):
        img = new()
        for y in range(S):
            for x in range(S):
                f1, f2 = voronoi(pts, x, y)
                e = f2 - f1
                w = COOL_W[age] * (0.75 + 0.5 * wn(x, y))
                pulse = 0.86 + 0.14 * math.sin(2 * math.pi * (f / frames + phase(x, y)))
                if e < w:
                    t = 1 - e / w
                    c = scale(ramp(COOL_GLOW[age], t), pulse)
                else:
                    c = ramp(COOL_CRUST[age], fb(x, y) * 1.3 - 0.15)
                    if e < w + 0.9:
                        c = mix(c, scale(COOL_GLOW[age][0], pulse), 0.45)
                    elif age <= 1 and fb(x * 2, y * 2) > 0.78:
                        c = scale(COOL_GLOW[age][1], pulse)
                put(img, x, y, c)
        out.append(img)
    return strip(out), {"animation": {"frametime": 10, "interpolate": True}}


def t_molten_rock(frames=8):
    n1, n2, crust = Fbm(111), Fbm(112, ((4, 0.6), (8, 0.4))), TileNoise(113, 8)
    out = []
    for f in range(frames):
        th = 2 * math.pi * f / frames
        img = new()
        for y in range(S):
            for x in range(S):
                v = n1(x + 3 * math.cos(th), y + 3 * math.sin(th)) * 0.6 + n2(x - 2 * math.sin(th), y + 2 * math.cos(th)) * 0.4
                t = (v - 0.3) * 2.0
                c = ramp(GLOW, t)
                if crust(x, y) > 0.76 and t < 0.55:
                    c = (56, 28, 22) if crust(x, y) > 0.84 else (84, 36, 24)
                put(img, x, y, c)
        out.append(img)
    return strip(out), {"animation": {"frametime": 5, "interpolate": True}}


# ---- sand, ash, soil ----
def t_grains(palette, seed, sparkle=None, flecks=None, clump=0.35):
    img = new()
    rnd = random.Random(seed)
    n = TileNoise(seed + 1, 8)
    for y in range(S):
        for x in range(S):
            t = rnd.random() * (1 - clump) + n(x, y) * clump
            put(img, x, y, ramp(palette, t))
    for colors, count in ((sparkle, 6), (flecks, 9)):
        if colors:
            for _ in range(count):
                put(img, rnd.randrange(S), rnd.randrange(S), rnd.choice(colors))
    return img


def t_volcanic_soil():
    img = t_grains(SOIL, 131, flecks=[(118, 46, 30), (136, 60, 36)], clump=0.5)
    rnd = random.Random(132)
    for _ in range(6):  # black cinders
        x, y = rnd.randrange(S), rnd.randrange(S)
        put(img, x, y, (20, 18, 20))
        put(img, (x + 1) % S, y, (30, 26, 28))
    return img


def t_lush_top():
    img = new()
    n, fine = Fbm(141), TileNoise(142, 16)
    rnd = random.Random(143)
    for y in range(S):
        for x in range(S):
            put(img, x, y, ramp(LUSH, n(x, y) * 0.7 + fine(x, y) * 0.3 + (rnd.random() - 0.5) * 0.18))
    for _ in range(5):  # little fern crowns
        x, y = rnd.randrange(S), rnd.randrange(S)
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            put(img, (x + dx) % S, (y + dy) % S, LUSH[4] if (dx, dy) == (0, 0) else LUSH[3])
    for _ in range(2):  # a fleck of red ohia petals
        put(img, rnd.randrange(S), rnd.randrange(S), (200, 40, 36))
    return img


def t_lush_side(soil):
    img = soil.copy()
    n = TileNoise(151, 8)
    rnd = random.Random(152)
    for x in range(S):
        h = 2 + int(n(x, 0) * 3.5)
        for y in range(h + 1):
            c = LUSH[3] if y == 0 else LUSH[2] if y < h else LUSH[0]
            put(img, x, y, c)
        if rnd.random() < 0.25:  # hanging root
            put(img, x, h + 1, LUSH[0])
    return img


# ---- fumarole ----
def t_fumarole_top():
    img = t_fresh_basalt(seed=161)
    rnd = random.Random(162)
    n = TileNoise(163, 8)
    for y in range(S):
        for x in range(S):
            r = math.hypot(x - 7.5, y - 7.5)
            if r < 1.9:
                put(img, x, y, (8, 8, 10) if r < 1.2 else (18, 16, 18))
            elif r < 2.9:
                put(img, x, y, (60, 48, 22))
            elif r < 5.6 and rnd.random() < 1.15 - r / 5.6 + n(x, y) * 0.3:
                put(img, x, y, ramp(SULFUR, n(x, y) * 0.8 + rnd.random() * 0.4))
    for _ in range(5):
        put(img, rnd.randrange(S), rnd.randrange(S), (226, 222, 206))
    return img


def t_fumarole_side():
    img = t_fresh_basalt(seed=171)
    rnd = random.Random(172)
    for x in range(S):
        put(img, x, 0, ramp(SULFUR, rnd.random() * 0.6 + 0.2))
        if rnd.random() < 0.45:
            length = rnd.randint(2, 8)
            for y in range(1, length):
                put(img, x, y, mix(ramp(SULFUR, 0.6), BASALT[2], y / length))
    return img


def t_sulfur_cluster(stage):
    img = new()
    prisms = [
        [(6, 2, 4), (9, 2, 3)],
        [(4, 2, 7), (11, 2, 6), (7, 3, 9)],
        [(2, 2, 9), (12, 2, 10), (5, 3, 13), (9, 3, 15), (7, 2, 6)],
    ][stage]
    for px, w, h in prisms:
        top = S - h
        for y in range(top, S):
            tip = y - top
            width = 1 if tip == 0 else w if tip > 1 else max(1, w - 1)
            for i in range(width):
                x = px + i
                c = SULFUR[4] if i == 0 else SULFUR[1] if i == width - 1 and width > 1 else SULFUR[3]
                if y > S - 3:
                    c = scale(c, 0.8)
                put(img, x, y, c)
        put(img, px, top + 1, (255, 255, 230))
    return img


# ---- glass ----
def t_black_sand_glass():
    img = new()
    for y in range(S):
        for x in range(S):
            if x in (0, 15) or y in (0, 15):
                put(img, x, y, (26, 24, 32, 235))
            elif (x + y) % 11 == 0 and 3 < x < 12:
                put(img, x, y, (96, 86, 128, 150))
            else:
                put(img, x, y, (40, 36, 52, 96))
    for x, y in ((2, 2), (3, 2), (2, 3), (13, 13)):
        put(img, x, y, (206, 198, 236, 210))
    return img


# ---- plants ----
def t_hapuu_fern():
    img = new()
    for y in range(11, 16):  # hairy tree-fern trunk
        for x in (7, 8):
            put(img, x, y, (98, 66, 38) if (x + y) % 2 else (124, 88, 50))
    put(img, 6, 12, (172, 132, 72))
    put(img, 9, 14, (172, 132, 72))
    for a_deg, length in ((-160, 7.5), (-128, 8), (-90, 8.5), (-52, 8), (-20, 7.5), (-108, 6), (-72, 6)):
        a = math.radians(a_deg)
        steps = int(length * 2)
        for i in range(steps + 1):
            t = i / steps
            x = 7.5 + math.cos(a) * length * t
            y = 11 + math.sin(a) * length * t + 4.5 * t * t * abs(math.cos(a))
            put(img, x, y, (46, 92, 34))
            nx, ny = -math.sin(a), math.cos(a)
            leaf = 2.2 * (1 - t) + 0.6
            for side in (-1, 1):
                for k in range(1, int(leaf) + 1):
                    c = (96, 160, 56) if k == 1 else (66, 128, 44)
                    put(img, x + nx * k * side, y + ny * k * side - 0.4 * k, c)
    return img


def t_silversword():
    """A ball of curved silver sword-leaves (the 'ahinahina of Haleakala's cinder slopes)."""
    img = new()
    cx, cy, radius, n = 7.5, 10.5, 6.4, 13
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r = math.hypot(dx, dy)
            if r > radius or y > 15:
                continue
            a = math.atan2(dy, dx)
            step = 2 * math.pi / n
            twist = 0.18 * r / radius  # leaves curve slightly as they reach out
            k = round((a - twist) / step)
            delta = abs((a - twist) - k * step)
            width = 0.26 * (1 - r / radius) + 0.05
            if r < 2.0:
                put(img, x, y, (96, 116, 106) if (x + y) % 2 else (122, 142, 132))
            elif delta < width:
                light = 0.5 - (dx + dy) / (radius * 3) + (1 - r / radius) * 0.2
                c = ramp([(112, 132, 124), (146, 166, 158), (178, 194, 188), (206, 218, 214)], light)
                if r > radius - 1.1:
                    c = (226, 234, 232)
                put(img, x, y, c)
            elif r < radius - 1.0:
                put(img, x, y, (58, 76, 68) if r < radius - 2.5 else (78, 98, 88))
    return img


def pompom(img, cx, cy, rnd):
    for dy in range(-2, 3):
        for dx in range(-2, 3):
            d = math.hypot(dx, dy)
            if d <= 1.9:
                c = (236, 64, 54) if d < 1 else (196, 28, 30)
                if d > 1.2 and rnd.random() < 0.5:
                    c = (255, 104, 84)
                put(img, cx + dx, cy + dy, c)
    for dx, dy in ((-2, -1), (1, -2), (2, 1), (-1, 2)):
        put(img, cx + dx, cy + dy, (250, 210, 84))


def t_ohia_sapling():
    img = new()
    rnd = random.Random(181)
    for y in range(6, 16):
        put(img, 7 + (1 if y < 10 else 0), y, (100, 72, 50))
    for x, y in ((5, 9), (6, 10), (10, 8), (11, 9), (6, 12), (9, 11), (10, 12)):
        put(img, x, y, (60, 110, 50))
        put(img, x + 1, y, (90, 140, 60))
    pompom(img, 6, 5, rnd)
    pompom(img, 11, 6, rnd)
    return img


def t_palm_sapling():
    img = new()
    for y in range(S):
        for x in range(S):
            d = ((x - 7.5) / 3.4) ** 2 + ((y - 12.8) / 2.6) ** 2
            if d <= 1:
                c = (140, 96, 54) if x < 7 and y < 13 else (110, 72, 40) if d < 0.7 else (80, 52, 30)
                put(img, x, y, c)
    put(img, 6, 11, (176, 132, 84))
    for a_deg, length in ((-125, 7.5), (-90, 8.5), (-58, 7)):
        a = math.radians(a_deg)
        for i in range(int(length * 2) + 1):
            t = i / (length * 2)
            x = 8 + math.cos(a) * length * t
            y = 10.5 + math.sin(a) * length * t + 2.5 * t * t
            put(img, x, y, (70, 140, 50))
            if i % 2 == 0 and t > 0.2:
                put(img, x + 1, y, (110, 175, 70))
                put(img, x - 1, y + 1, (60, 120, 44))
    return img


# ---- wood ----
def t_bark(colors, seed, fissure, lichen=None, rings=False):
    img = new()
    n, wobble = TileNoise(seed, 16), TileNoise(seed + 1, 4)
    rnd = random.Random(seed + 2)
    for y in range(S):
        for x in range(S):
            t = n(x * 0.5, y * 2) * 0.6 + rnd.random() * 0.4
            c = ramp(colors, t)
            if rings and y % 4 == 0:
                c = fissure
            elif rings and y % 4 == 3:
                c = colors[-1]
            if not rings and math.sin((x + wobble(x, y) * 5) * 1.9) > 0.82:
                c = fissure
            put(img, x, y, c)
    if lichen:
        for _ in range(4):
            put(img, rnd.randrange(S), rnd.randrange(S), lichen)
    return img


def t_log_top(bark, rings, center, seed):
    img = new()
    rnd = random.Random(seed)
    for y in range(S):
        for x in range(S):
            if x in (0, 15) or y in (0, 15):
                c = rnd.choice(bark)
            else:
                r = max(abs(x - 7.5), abs(y - 7.5)) + rnd.random() * 0.6
                c = center if r < 1.6 else rings[int(r) % len(rings)]
            put(img, x, y, c)
    return img


def t_planks(colors, seam, seed):
    img = new()
    n = TileNoise(seed, 16)
    rnd = random.Random(seed + 1)
    seams = {0: 3, 1: 11, 2: 6, 3: 13}
    for y in range(S):
        board = y // 4
        for x in range(S):
            if y % 4 == 3 or x == seams[board]:
                c = seam
            else:
                c = ramp(colors, n(x * 0.3 + board * 3, y * 2) * 0.7 + rnd.random() * 0.3)
            put(img, x, y, c)
    return img


def t_leaves(greens, seed, blossoms=0):
    img = new()
    n, fine = TileNoise(seed, 8), TileNoise(seed + 1, 16)
    rnd = random.Random(seed + 2)
    for y in range(S):
        for x in range(S):
            v = n(x, y) * 0.55 + fine(x, y) * 0.45
            if v > 0.36 or rnd.random() < 0.1:
                put(img, x, y, ramp(greens, v * 1.2 - 0.1 + (rnd.random() - 0.5) * 0.2))
    for _ in range(blossoms):
        pompom(img, rnd.randrange(2, 14), rnd.randrange(2, 14), rnd)
    return img


def t_palm_fronds():
    """Long feathery fronds: parallel leaflets hanging off midribs, with gaps you can see through."""
    img = new()
    greens = [(48, 104, 34), (64, 128, 42), (86, 152, 52), (116, 180, 68)]
    rnd = random.Random(198)
    gap = TileNoise(199, 8)
    for y in range(S):
        for x in range(S):
            u = (x * 0.55 + y * 0.84) % 3.2       # across the leaflets
            v = ((x * 0.84 - y * 0.55) % 8) / 8   # along a leaflet
            if u < 1.75 and gap(x, y) > 0.22 and rnd.random() > 0.06:
                put(img, x, y, ramp(greens, 1 - v * 0.9 + (rnd.random() - 0.5) * 0.25))
    for x in range(S):  # two midribs running across the tile
        for off in (2, 10):
            y = (off + x * 0.65) % S
            put(img, x, y, (122, 136, 50))
            put(img, x, (y + 1) % S, (90, 110, 42))
    return img


# ---- items ----
def t_magma_shard(frames=4):
    """A jagged splinter of dark crust split by glowing cracks."""
    poly = [(3, 14.5), (1.8, 10), (5, 6), (8, 1.2), (11, 3.4), (14.4, 2), (12.3, 8), (13.5, 11.5), (9, 13.2), (6, 15.2)]
    cracks = [((4.0, 12.5), (11.5, 3.6)), ((8.0, 8.0), (12.6, 10.6)), ((6.0, 10.0), (3.0, 9.0)), ((9.5, 5.6), (7.6, 3.2))]

    def seg_dist(px, py, a, b):
        (ax, ay), (bx, by) = a, b
        t = clamp01(((px - ax) * (bx - ax) + (py - ay) * (by - ay)) / ((bx - ax) ** 2 + (by - ay) ** 2))
        return math.hypot(px - ax - (bx - ax) * t, py - ay - (by - ay) * t)

    out = []
    for f in range(frames):
        pulse = 0.84 + 0.16 * math.sin(2 * math.pi * f / frames)
        img = new()
        for y in range(S):
            for x in range(S):
                px, py = x + 0.5, y + 0.5
                if not inside_poly(poly, px, py):
                    continue
                d = min(seg_dist(px, py, a, b) for a, b in cracks)
                if d < 0.6:
                    c = scale(GLOW[6], pulse)
                elif d < 1.15:
                    c = scale(GLOW[4], pulse)
                elif d < 1.7:
                    c = scale(GLOW[2], pulse)
                else:
                    edge = not inside_poly(poly, px - 1, py - 1)
                    c = CRUST[3] if edge else CRUST[1] if (x * 3 + y) % 5 else CRUST[0]
                put(img, x, y, c)
        out.append(img)
    img = strip(out)
    outline(img, (30, 12, 8), frames)
    return img, {"animation": {"frametime": 6, "interpolate": True}}


def t_gem(poly, palette, light_dir, glint, line):
    img = new()
    for y in range(S):
        for x in range(S):
            if inside_poly(poly, x + 0.5, y + 0.5):
                t = 0.5 + ((x - 7.5) * light_dir[0] + (y - 7.5) * light_dir[1]) / 14
                put(img, x, y, ramp(palette, t))
    for gx, gy in glint:
        put(img, gx, gy, palette[-1])
    outline(img, line)
    return img


def t_olivine():
    poly = [(5, 2.5), (11, 2.5), (14, 7), (11.5, 13.5), (4.5, 13.5), (2, 7)]
    img = t_gem(poly, OLIVINE, (-1, -1), [(5, 4), (6, 4), (5, 5)], (24, 42, 10))
    for x in range(4, 13):  # facet line
        put(img, x, 7, OLIVINE[2])
    return img


def t_peles_tear():
    img = new()
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 9.5, y + 0.5 - 10.5)
            if d < 3.9:
                c = (14, 12, 18) if d < 2.8 else (60, 45, 90)
                put(img, x, y, c)
    for i in range(9):  # thin tail of Pele's hair
        t = i / 8
        put(img, 8 - 5 * t + math.sin(t * 3) * 0.8, 7 - 5.5 * t, (44, 36, 52) if i < 6 else (150, 112, 56))
    put(img, 8, 9, (222, 216, 242))
    put(img, 8, 8, (150, 130, 200))
    outline(img, (6, 5, 8))
    return img


def t_sulfur_crystal():
    img = new()
    for px, w, h, base in ((4, 2, 8, 13), (9, 2, 7, 13), (6, 3, 11, 14)):
        top = base - h
        for y in range(top, base + 1):
            tip = y - top
            width = 1 if tip == 0 else w if tip > 1 else w - 1
            for i in range(width):
                c = SULFUR[4] if i == 0 else SULFUR[1] if i == width - 1 else SULFUR[3]
                put(img, px + i, y, c)
    outline(img, (96, 72, 14))
    return img


def t_volcanic_glass():
    """A conchoidal obsidian flake: glossy black with violet ripples and a bright edge glint."""
    poly = [(2, 9.5), (4.6, 3.6), (9, 1.2), (14.4, 3), (12.4, 7.2), (14.2, 12.6), (9, 15), (3.6, 13.6)]
    img = new()
    for y in range(S):
        for x in range(S):
            px, py = x + 0.5, y + 0.5
            if not inside_poly(poly, px, py):
                continue
            r = math.hypot(px - 4.2, py - 4.0)
            band = math.sin(r * 1.55)
            c = GLASS[4] if band > 0.93 else GLASS[3] if band > 0.7 else GLASS[2] if band > 0.2 else GLASS[1]
            if not inside_poly(poly, px - 1, py - 1):
                c = GLASS[5]
            put(img, x, y, c)
    for x, y in ((5, 4), (6, 3), (7, 3), (4, 6)):
        put(img, x, y, (226, 220, 248))
    outline(img, (4, 3, 6))
    return img


def t_hotspot_heart(frames=6):
    out = []
    for f in range(frames):
        pulse = 0.8 + 0.2 * math.sin(2 * math.pi * f / frames)
        img = new()
        for y in range(S):
            for x in range(S):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
                if d > 6.6:
                    continue
                if d > 5.0:
                    ang = math.atan2(y + 0.5 - 8, x + 0.5 - 8)
                    crack = math.sin(ang * 5 + f * 0.4) > 0.75
                    c = scale(GLOW[3], pulse) if crack else BASALT[2] if (x + y) % 3 else BASALT[3]
                else:
                    c = scale(ramp(GLOW[2:], 1 - d / 5.0), pulse)
                put(img, x, y, c)
        for (gx, gy), col in (((4, 4), OLIVINE[3]), ((11, 3), SULFUR[3]), ((12, 11), GLASS[5]), ((3, 11), (20, 16, 26))):
            put(img, gx, gy, col)
        out.append(img)
    img = strip(out)
    outline(img, (24, 10, 6), frames)
    return img, {"animation": {"frametime": 4, "interpolate": True}}


def build_textures():
    tex("block/fresh_basalt", t_fresh_basalt())
    tex("block/pillow_lava", t_pillow_lava())
    tex("block/glassy_basalt", t_glassy_basalt())
    tex("block/olivine_ore", t_olivine_ore())
    tex("block/basalt_bricks", t_basalt_bricks())
    for age in range(4):
        tex(f"block/cooling_lava_{age}", *t_cooling_lava(age))
    tex("block/molten_rock", *t_molten_rock())
    tex("block/black_sand", t_grains(SAND, 121, sparkle=[(84, 82, 104), (124, 116, 156)], clump=0.25))
    tex("block/volcanic_ash", t_grains(ASH, 125, flecks=[(60, 58, 64), (168, 166, 170)], clump=0.4))
    soil = t_volcanic_soil()
    tex("block/volcanic_soil", soil)
    tex("block/lush_volcanic_soil_top", t_lush_top())
    tex("block/lush_volcanic_soil_side", t_lush_side(soil))
    tex("block/fumarole_top", t_fumarole_top())
    tex("block/fumarole_side", t_fumarole_side())
    for stage in range(3):
        tex(f"block/sulfur_cluster_{stage}", t_sulfur_cluster(stage))
    tex("block/black_sand_glass", t_black_sand_glass())
    tex("block/hapuu_fern", t_hapuu_fern())
    tex("block/silversword", t_silversword())
    tex("block/ohia_sapling", t_ohia_sapling())
    tex("block/palm_sapling", t_palm_sapling())
    ohia_bark = [(70, 62, 58), (86, 76, 70), (102, 92, 84), (122, 110, 100)]
    palm_bark = [(112, 94, 70), (132, 112, 84), (150, 128, 96), (168, 146, 110)]
    tex("block/ohia_log", t_bark(ohia_bark, 191, (48, 42, 40), lichen=(146, 158, 124)))
    tex("block/ohia_log_top", t_log_top(ohia_bark, [(150, 92, 70), (126, 74, 56)], (104, 50, 38), 192))
    tex("block/palm_log", t_bark(palm_bark, 193, (86, 70, 52), rings=True))
    tex("block/palm_log_top", t_log_top(palm_bark, [(196, 174, 132), (176, 152, 112)], (150, 120, 84), 194))
    tex("block/ohia_leaves", t_leaves([(30, 60, 32), (42, 80, 40), (58, 100, 50), (78, 120, 62)], 195, blossoms=3))
    tex("block/palm_fronds", t_palm_fronds())
    tex("block/ohia_planks", t_planks([(104, 54, 42), (120, 64, 48), (138, 76, 56), (156, 90, 64)], (78, 40, 32), 196))
    tex("block/palm_planks", t_planks([(156, 126, 86), (176, 144, 98), (192, 160, 110), (208, 178, 124)], (120, 96, 64), 197))
    tex("item/magma_shard", *t_magma_shard())
    tex("item/olivine", t_olivine())
    tex("item/peles_tear", t_peles_tear())
    tex("item/sulfur_crystal", t_sulfur_crystal())
    tex("item/volcanic_glass", t_volcanic_glass())
    tex("item/hotspot_heart", *t_hotspot_heart())


def save_textures():
    for name, (img, anim) in TEXTURES.items():
        path = os.path.join(ASSETS, "textures", name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        WRITTEN.append(path)
        if anim:
            write_json(path + ".mcmeta", anim)


# =====================================================================================================
# Content table
# =====================================================================================================
# kind: cube | cube_rot | bottom_top | column | leaves | cross | cooling | sulfur | sapling
BLOCKS = {
    "cooling_lava":       dict(name="Cooling Lava", kind="cooling", tool="pickaxe", loot="cooling"),
    "molten_rock":        dict(name="Molten Rock", kind="cube", tool="pickaxe", loot="molten"),
    "fresh_basalt":       dict(name="Fresh Basalt", kind="cube_rot", tool="pickaxe", loot="self"),
    "pillow_lava":        dict(name="Pillow Lava", kind="cube_rot", tool="pickaxe", loot="self"),
    "glassy_basalt":      dict(name="Glassy Basalt", kind="cube", tool="pickaxe", loot=("ore", "volcanic_glass", 1, 2)),
    "olivine_ore":        dict(name="Olivine Ore", kind="cube", tool="pickaxe", loot=("ore", "olivine", 1, 2)),
    "fumarole":           dict(name="Fumarole", kind="bottom_top", tool="pickaxe", loot="self",
                               textures=("fumarole_top", "fumarole_side", "fresh_basalt")),
    "sulfur_cluster":     dict(name="Sulfur Cluster", kind="sulfur", tool="pickaxe", loot="sulfur", item=False),
    "basalt_bricks":      dict(name="Basalt Bricks", kind="cube", tool="pickaxe", loot="self"),
    "black_sand":         dict(name="Black Sand", kind="cube_rot", tool="shovel", loot="self"),
    "volcanic_ash":       dict(name="Volcanic Ash", kind="cube_rot", tool="shovel", loot="self"),
    "volcanic_soil":      dict(name="Volcanic Soil", kind="cube_rot", tool="shovel", loot="self"),
    "lush_volcanic_soil": dict(name="Lush Volcanic Soil", kind="bottom_top", tool="shovel", loot=("silk_or", "volcanic_soil"),
                               textures=("lush_volcanic_soil_top", "lush_volcanic_soil_side", "volcanic_soil"), rotate=True),
    "black_sand_glass":   dict(name="Black Sand Glass", kind="cube", tool=None, loot="silk"),
    "hapuu_fern":         dict(name="Hapu'u Fern", kind="cross", tool=None, loot="self"),
    "silversword":        dict(name="Silversword", kind="cross", tool=None, loot="self"),
    "ohia_sapling":       dict(name="Ohia Lehua Sapling", kind="sapling", tool=None, loot="self"),
    "palm_sapling":       dict(name="Coconut Palm Sapling", kind="sapling", tool=None, loot="self"),
    "ohia_log":           dict(name="Ohia Log", kind="column", tool="axe", loot="self"),
    "palm_log":           dict(name="Palm Log", kind="column", tool="axe", loot="self"),
    "ohia_leaves":        dict(name="Ohia Leaves", kind="leaves", tool="hoe", loot=("leaves", "ohia_sapling")),
    "palm_fronds":        dict(name="Palm Fronds", kind="leaves", tool="hoe", loot=("leaves", "palm_sapling")),
    "ohia_planks":        dict(name="Ohia Planks", kind="cube", tool="axe", loot="self"),
    "palm_planks":        dict(name="Palm Planks", kind="cube", tool="axe", loot="self"),
}

ITEMS = {
    "magma_shard":    dict(name="Magma Shard", desc="Still glowing. Eat it to grow (+6 mass). Offering of the Lava Flow stage."),
    "olivine":        dict(name="Olivine", desc="Green crystal from deep-sea lava and the sea floor. Offering of the Seamount stage."),
    "peles_tear":     dict(name="Pele's Tear", desc="Lava flung into the air and frozen to glass. Offering of the Shield Island stage."),
    "sulfur_crystal": dict(name="Sulfur Crystal", desc="Grown by steaming fumaroles. Offering of the Island Chain stage."),
    "volcanic_glass": dict(name="Volcanic Glass", desc="Lava quenched by the sea. Offering of the Archipelago stage."),
    "hotspot_heart":  dict(name="Hotspot Heart", desc="The fire of the deep, gathered into one stone. Offering of the Great Volcano stage."),
}

# every land block counts as island land for the vent module, plus the vanilla volcanic blocks
VOLCANIC_LAND_VANILLA = ["basalt", "smooth_basalt", "polished_basalt", "tuff", "obsidian", "magma_block", "blackstone"]


# =====================================================================================================
# Models, blockstates, item definitions
# =====================================================================================================
def block_model(name, parent, textures):
    write_json(os.path.join(ASSETS, "models", "block", name + ".json"),
               {"parent": parent, "textures": {k: nid("block/" + v) for k, v in textures.items()}})


def item_model_generated(name, texture):
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": nid(texture)}})


def item_definition(name, model):
    write_json(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": model}})


def blockstate(name, variants):
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})


def rotations(model, ys=(0, 90, 180, 270)):
    return [dict({"model": model}, **({"y": y} if y else {})) for y in ys]


def build_models():
    for bid, b in BLOCKS.items():
        kind = b["kind"]
        m = nid("block/" + bid)
        item_model = m
        if kind in ("cube", "cube_rot"):
            block_model(bid, "minecraft:block/cube_all", {"all": bid})
            blockstate(bid, {"": rotations(m) if kind == "cube_rot" else {"model": m}})
        elif kind == "bottom_top":
            top, side, bottom = b["textures"]
            block_model(bid, "minecraft:block/cube_bottom_top", {"top": top, "side": side, "bottom": bottom})
            blockstate(bid, {"": rotations(m) if b.get("rotate") else {"model": m}})
        elif kind == "column":
            block_model(bid, "minecraft:block/cube_column", {"end": bid + "_top", "side": bid})
            block_model(bid + "_horizontal", "minecraft:block/cube_column_horizontal", {"end": bid + "_top", "side": bid})
            h = m + "_horizontal"
            blockstate(bid, {"axis=x": {"model": h, "x": 90, "y": 90}, "axis=y": {"model": m}, "axis=z": {"model": h, "x": 90}})
        elif kind == "leaves":
            block_model(bid, "minecraft:block/leaves", {"all": bid})
            blockstate(bid, {"": {"model": m}})
        elif kind in ("cross", "sapling"):
            block_model(bid, "minecraft:block/cross", {"cross": bid})
            blockstate(bid, {"": {"model": m}})
            item_model_generated(bid, "block/" + bid)
            item_model = nid("item/" + bid)
        elif kind == "cooling":
            for age in range(4):
                block_model(f"{bid}_{age}", "minecraft:block/cube_all", {"all": f"{bid}_{age}"})
            blockstate(bid, {f"age={a}": {"model": nid(f"block/{bid}_{a}")} for a in range(4)})
            item_model = nid(f"block/{bid}_0")
        elif kind == "sulfur":
            for s in range(3):
                block_model(f"{bid}_{s}", "minecraft:block/cross", {"cross": f"{bid}_{s}"})
            blockstate(bid, {f"age={a}": {"model": nid(f"block/{bid}_{a}")} for a in range(3)})
        if b.get("item", True):
            item_definition(bid, item_model)
    for iid in ITEMS:
        item_model_generated(iid, "item/" + iid)
        item_definition(iid, nid("item/" + iid))


# =====================================================================================================
# Loot tables
# =====================================================================================================
SILK = "minecraft:tool/can_silk_touch"


def loot(bid, pools):
    write_json(os.path.join(DATA, "loot_table", "blocks", bid + ".json"),
               {"type": "minecraft:block", "pools": pools, "random_sequence": nid("blocks/" + bid)})


def item_entry(name, **extra):
    e = {"type": "minecraft:item", "name": name}
    e.update(extra)
    return e


def count(lo, hi):
    return {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi} if lo != hi else lo}


def fortune_ores():
    return {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"}


def state_is(bid, prop, value):
    return {"type": "minecraft:match_block", "blocks": nid(bid), "state": {prop: str(value)}}


def build_loot():
    for bid, b in BLOCKS.items():
        rule = b["loot"]
        me = nid(bid)
        if rule == "self":
            loot(bid, [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"}, "entries": [item_entry(me)]}])
        elif rule == "silk":
            loot(bid, [{"rolls": 1, "condition": SILK, "entries": [item_entry(me)]}])
        elif rule[0] == "silk_or":
            loot(bid, [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
                item_entry(me, condition=SILK),
                item_entry(nid(rule[1]), condition={"type": "minecraft:survives_explosion"})]}]}])
        elif rule[0] == "ore":
            _, drop, lo, hi = rule
            loot(bid, [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
                item_entry(me, condition=SILK),
                item_entry(nid(drop), modifier=[count(lo, hi), fortune_ores(), {"type": "minecraft:explosion_decay"}])]}]}])
        elif rule == "molten":
            loot(bid, [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
                item_entry(me, condition=SILK),
                item_entry(nid("magma_shard"), condition={"type": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                           "chances": [0.35, 0.5, 0.65, 0.8]})]}]}])
        elif rule == "cooling":
            # freshly erupted lava rock sometimes holds a Magma Shard (kept rare so spitting lava is no mass farm)
            loot(bid, [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
                item_entry(nid("magma_shard"), condition={"type": "minecraft:all_of", "terms": [
                    state_is(bid, "age", 0), {"type": "minecraft:random_chance", "chance": 0.25}]}),
                item_entry(nid("fresh_basalt"), condition={"type": "minecraft:survives_explosion"})]}]}])
        elif rule == "sulfur":
            loot(bid, [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
                item_entry(nid("sulfur_crystal"), condition=state_is(bid, "age", 2), modifier=[count(2, 3), fortune_ores()]),
                item_entry(nid("sulfur_crystal"), condition=state_is(bid, "age", 1)),
                item_entry(nid("sulfur_crystal"), condition={"type": "minecraft:random_chance", "chance": 0.5})]}]}])
        elif rule[0] == "leaves":
            shears_or_silk = {"type": "minecraft:any_of", "terms": ["minecraft:tool/can_shear", SILK]}
            loot(bid, [
                {"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
                    item_entry(me, condition=shears_or_silk),
                    item_entry(nid(rule[1]), condition={"type": "minecraft:all_of", "terms": [
                        {"type": "minecraft:survives_explosion"},
                        {"type": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                         "chances": [0.05, 0.0625, 0.083333336, 0.1]}]})]}]},
                {"rolls": 1, "condition": {"type": "minecraft:inverted", "term": shears_or_silk}, "entries": [
                    item_entry("minecraft:stick", condition={"type": "minecraft:table_bonus", "enchantment": "minecraft:fortune",
                               "chances": [0.02, 0.022222223, 0.025, 0.033333335, 0.1]},
                               modifier=[count(1, 2), {"type": "minecraft:explosion_decay"}])]}])
        else:
            raise ValueError(f"unknown loot rule {rule} for {bid}")


# =====================================================================================================
# Recipes
# =====================================================================================================
def recipe(name, obj):
    write_json(os.path.join(DATA, "recipe", name + ".json"), obj)


def build_recipes():
    recipe("basalt_bricks", {"type": "minecraft:crafting_shaped", "category": "building",
                             "key": {"#": nid("fresh_basalt")}, "pattern": ["##", "##"],
                             "result": {"id": nid("basalt_bricks"), "count": 4}})
    recipe("basalt_bricks_from_fresh_basalt_stonecutting", {"type": "minecraft:stonecutting", "ingredient": nid("fresh_basalt"),
                                                            "result": {"id": nid("basalt_bricks")}})
    recipe("black_sand_glass", {"type": "minecraft:smelting", "category": "blocks", "cookingtime": 200, "experience": 0.1,
                                "ingredient": nid("black_sand"), "result": {"id": nid("black_sand_glass")}})
    recipe("smooth_basalt_from_fresh_basalt", {"type": "minecraft:smelting", "category": "blocks", "cookingtime": 200,
                                               "experience": 0.1, "ingredient": nid("fresh_basalt"),
                                               "result": {"id": "minecraft:smooth_basalt"}})
    for wood in ("ohia", "palm"):
        recipe(f"{wood}_planks", {"type": "minecraft:crafting_shapeless", "category": "building", "group": "planks",
                                  "ingredients": [nid(f"{wood}_log")], "result": {"id": nid(f"{wood}_planks"), "count": 4}})
    recipe("magma_block_from_magma_shards", {"type": "minecraft:crafting_shaped", "category": "building",
                                             "key": {"#": nid("magma_shard")}, "pattern": ["##", "##"],
                                             "result": {"id": "minecraft:magma_block"}})
    # the end item: every earlier offering bound around a magma heart
    recipe("hotspot_heart", {"type": "minecraft:crafting_shaped", "category": "misc",
                             "key": {"T": nid("peles_tear"), "G": nid("volcanic_glass"), "S": nid("sulfur_crystal"),
                                     "O": nid("olivine"), "M": "minecraft:magma_block"},
                             "pattern": ["TGT", "SMS", "OGO"],
                             "result": {"id": nid("hotspot_heart")}})


# =====================================================================================================
# Tags
# =====================================================================================================
def tag(base, kind, name, values):
    write_json(os.path.join(base, "tags", kind, name + ".json"), {"replace": False, "values": values})


def build_tags():
    ids = {k: nid(k) for k in BLOCKS}
    land = [ids[k] for k in BLOCKS if k not in ("black_sand_glass", "ohia_planks", "palm_planks")]
    tag(DATA, "block", "volcanic_land", land + ["minecraft:" + v for v in VOLCANIC_LAND_VANILLA])
    tag(DATA, "block", "olivine_ore_replaceables", ["minecraft:" + v for v in (
        "gravel", "sand", "red_sand", "stone", "clay", "dirt", "andesite", "diorite", "granite", "tuff", "deepslate",
        "calcite", "mud")])
    vanilla = os.path.join(PACK, "data", "minecraft")
    for tool in ("pickaxe", "shovel", "axe", "hoe"):
        tag(vanilla, "block", f"mineable/{tool}", [ids[k] for k, b in BLOCKS.items() if b["tool"] == tool])
    logs = [ids["ohia_log"], ids["palm_log"]]
    planks = [ids["ohia_planks"], ids["palm_planks"]]
    leaves = [ids["ohia_leaves"], ids["palm_fronds"]]
    saplings = [ids["ohia_sapling"], ids["palm_sapling"]]
    for kind in ("block", "item"):
        tag(vanilla, kind, "logs", logs)
        tag(vanilla, kind, "logs_that_burn", logs)
        tag(vanilla, kind, "planks", planks)
        tag(vanilla, kind, "leaves", leaves)
        tag(vanilla, kind, "saplings", saplings)
        tag(vanilla, kind, "dirt", [ids["volcanic_soil"], ids["lush_volcanic_soil"]])
        tag(vanilla, kind, "sand", [ids["black_sand"]])
    tag(vanilla, "block", "replaceable_by_trees", [ids["hapuu_fern"], ids["silversword"]])


# =====================================================================================================
# Worldgen
# =====================================================================================================
def tri(lo, hi):
    return {"type": "minecraft:trapezoid", "min": lo, "max": hi, "plateau": 0}


def build_worldgen():
    wg = os.path.join(DATA, "worldgen")
    write_json(os.path.join(wg, "feature", "land", "olivine_ore.json"), {
        "type": "minecraft:ore", "discard_chance_on_air_exposure": 0.0, "size": 6,
        "targets": [{"state": nid("olivine_ore"),
                     "target": {"predicate_type": "minecraft:tag_match", "tag": nid("olivine_ore_replaceables")}}]})
    for name, n in (("olivine_ore_seafloor", 2), ("olivine_ore_deep", 4)):
        write_json(os.path.join(wg, "placed_feature", "land", name + ".json"), {
            "feature": nid("land/olivine_ore"),
            "placement": [
                {"type": "minecraft:count", "count": n},
                {"type": "minecraft:in_square"},
                {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"},
                {"type": "minecraft:offset", "x": tri(0, 0), "y": tri(-2, -1), "z": tri(0, 0)},
                {"type": "minecraft:biome"}]})
    # hydrothermal fields: clusters of fumaroles, pillow lava, magma and glowing seeps on the deep sea floor
    write_json(os.path.join(wg, "feature", "land", "hydrothermal_vent.json"), {
        "type": "minecraft:simple_block",
        "to_place": {"type": "minecraft:weighted", "entries": [
            {"data": nid("fumarole"), "weight": 3},
            {"data": nid("pillow_lava"), "weight": 5},
            {"data": nid("fresh_basalt"), "weight": 3},
            {"data": "minecraft:magma_block", "weight": 2},
            {"data": nid("molten_rock"), "weight": 1},
            {"data": nid("glassy_basalt"), "weight": 1}]}})
    write_json(os.path.join(wg, "placed_feature", "land", "hydrothermal_field.json"), {
        "feature": nid("land/hydrothermal_vent"),
        "placement": [
            {"type": "minecraft:rarity_filter", "chance": 32},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:count", "count": 18},
            {"type": "minecraft:offset", "x": tri(-6, 6), "y": tri(0, 0), "z": tri(-6, 6)},
            {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"},
            {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
                {"type": "minecraft:matching_blocks", "blocks": "minecraft:water"},
                {"type": "minecraft:matching_fluids", "fluids": "minecraft:water", "offset": [0, 4, 0]},
                {"type": "minecraft:solid", "offset": [0, -1, 0]}]}},
            {"type": "minecraft:biome"}]})
    write_json(os.path.join(wg, "feature", "land", "lava_seep.json"), {
        "type": "minecraft:simple_block", "to_place": {"id": nid("molten_rock")}})
    write_json(os.path.join(wg, "placed_feature", "land", "lava_seep.json"), {
        "feature": nid("land/lava_seep"),
        "placement": [
            {"type": "minecraft:rarity_filter", "chance": 20},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:count", "count": 5},
            {"type": "minecraft:offset", "x": tri(-2, 2), "y": tri(0, 0), "z": tri(-2, 2)},
            {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"},
            {"type": "minecraft:offset", "x": tri(0, 0), "y": tri(-1, -1), "z": tri(0, 0)},
            {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
                {"type": "minecraft:solid"},
                {"type": "minecraft:matching_fluids", "fluids": "minecraft:water", "offset": [0, 1, 0]}]}},
            {"type": "minecraft:biome"}]})


# =====================================================================================================
# Translations
# =====================================================================================================
MESSAGES = {
    "pack.lavagenesis.land_tags": "Lava Genesis: volcanic land tags",
    "message.lavagenesis.birth.stirring": "Deep beneath the sea, a hotspot stirs...",
    "message.lavagenesis.birth.rebuilding": "The hotspot gathers itself to raise a new birth volcano...",
    "message.lavagenesis.birth.born": "You are a single spark of lava, born in the magma chamber of an undersea volcano. Grow.",
    "message.lavagenesis.birth.hint": "Melt the rock, drink the lava, eat Magma Shards. Your home vent glows beneath you.",
    "message.lavagenesis.birth.rebuilt": "A new birth volcano has risen around you.",
    "message.lavagenesis.birth.failed": "The birth volcano could not be built - see the server log.",
    "commands.lavagenesis.info.header": "%s - %s",
    "commands.lavagenesis.info.mass": "Mass: %s (next stage at %s: %s)",
    "commands.lavagenesis.info.mass_final": "Mass: %s (final stage)",
    "commands.lavagenesis.info.heat": "Heat: %s   Pressure: %s/100",
    "commands.lavagenesis.info.islands": "Islands: %s (largest %s columns, %s in total)",
    "commands.lavagenesis.info.vent": "Home vent: %s in %s",
    "commands.lavagenesis.info.no_vent": "Home vent: none bound",
    "commands.lavagenesis.info.birth": "Birth volcano: %s",
    "commands.lavagenesis.info.birth_pending": "Birth volcano: being built...",
    "commands.lavagenesis.info.birth_none": "Birth volcano: not built yet",
    "commands.lavagenesis.info.birth_unknown": "Birth volcano: none recorded",
    "commands.lavagenesis.stage.unknown": "Unknown stage: %s",
    "commands.lavagenesis.stage.set": "Set the stage of %s to %s",
    "commands.lavagenesis.mass.add": "Added %s mass to %s (now %s)",
    "commands.lavagenesis.mass.set": "Set the mass of %s to %s",
    "commands.lavagenesis.heat.set": "Set the heat of %s to %s",
    "commands.lavagenesis.pressure.set": "Set the pressure of %s to %s",
    "commands.lavagenesis.rebuild.started": "Building a new birth volcano for %s...",
    "commands.lavagenesis.rebuild.busy": "A birth volcano is already being built for %s",
    "commands.lavagenesis.tp.home": "Teleported %s to the home vent at %s",
    "commands.lavagenesis.tp.no_home": "%s has no home vent or birth volcano yet",
}


def build_lang():
    lang = {}
    for bid, b in BLOCKS.items():
        lang[f"block.{NS}.{bid}"] = b["name"]
    for iid, i in ITEMS.items():
        lang[f"item.{NS}.{iid}"] = i["name"]
        lang[f"item.{NS}.{iid}.desc"] = i["desc"]
    lang.update(MESSAGES)
    os.makedirs(os.path.dirname(LANG), exist_ok=True)
    with open(LANG, "w", encoding="utf-8", newline="\n") as f:
        json.dump(dict(sorted(lang.items())), f, indent=2, ensure_ascii=False)
        f.write("\n")
    WRITTEN.append(LANG)
    return lang


# =====================================================================================================
# Audit
# =====================================================================================================
def java_registered_ids():
    """Ids registered by the land Java sources (LandBlocks / LandItems), resolving LavaIds constants."""
    consts = {}
    for cls in (("core", "LavaIds.java"), ("land", "LandIds.java")):
        with open(os.path.join(JAVA, *cls), encoding="utf-8") as f:
            for m in re.finditer(r'(\w+)\s*=\s*LavaGenesis\.id\("([a-z0-9_/]+)"\)', f.read()):
                consts[cls[1][:-5] + "." + m.group(1)] = m.group(2)
    lava_ids = {k.split(".")[1]: v for k, v in consts.items() if k.startswith("LavaIds.")}
    blocks, items = set(), set()
    with open(os.path.join(JAVA, "land", "LandBlocks.java"), encoding="utf-8") as f:
        src = f.read()
    for m in re.finditer(r'register\(((?:LavaIds|LandIds)\.\w+),[\s\S]*?,\s*(true|false)\);', src):
        bid = consts[m.group(1)]
        blocks.add(bid)
        if m.group(2) == "true":
            items.add(bid)
    with open(os.path.join(JAVA, "land", "LandItems.java"), encoding="utf-8") as f:
        for m in re.finditer(r'register\(LavaIds\.(\w+),', f.read()):
            items.add(lava_ids[m.group(1)])
    return blocks, items


def audit(lang):
    errors = []

    def exists(rel):
        return os.path.exists(os.path.join(ASSETS, rel))

    def check_model(model_id, where):
        ns, path = model_id.split(":")
        if ns != NS:
            return
        mpath = os.path.join(ASSETS, "models", path + ".json")
        if not os.path.exists(mpath):
            errors.append(f"{where}: missing model {model_id}")
            return
        with open(mpath, encoding="utf-8") as f:
            model = json.load(f)
        for key, t in model.get("textures", {}).items():
            if t.startswith("#"):
                continue
            tns, tpath = t.split(":") if ":" in t else ("minecraft", t)
            if tns == NS and not exists(os.path.join("textures", tpath + ".png")):
                errors.append(f"{mpath}: texture '{key}' -> missing {t}")

    for name in os.listdir(os.path.join(ASSETS, "blockstates")):
        if name[:-5] not in BLOCKS:
            continue
        with open(os.path.join(ASSETS, "blockstates", name), encoding="utf-8") as f:
            for variant in json.load(f)["variants"].values():
                for v in variant if isinstance(variant, list) else [variant]:
                    check_model(v["model"], "blockstates/" + name)
    for name in list(BLOCKS) + list(ITEMS):
        path = os.path.join(ASSETS, "items", name + ".json")
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as f:
            check_model(json.load(f)["model"]["model"], "items/" + name)

    java_blocks, java_items = java_registered_ids()
    for bid in java_blocks:
        if bid not in BLOCKS:
            errors.append(f"Java registers block {bid} but the content table does not know it")
        if not exists(os.path.join("blockstates", bid + ".json")):
            errors.append(f"block {bid}: no blockstate")
        if f"block.{NS}.{bid}" not in lang:
            errors.append(f"block {bid}: no translation")
        if not os.path.exists(os.path.join(DATA, "loot_table", "blocks", bid + ".json")):
            errors.append(f"block {bid}: no loot table")
    for iid in java_items:
        if not exists(os.path.join("items", iid + ".json")):
            errors.append(f"item {iid}: no item model definition")
        if f"block.{NS}.{iid}" not in lang and f"item.{NS}.{iid}" not in lang:
            errors.append(f"item {iid}: no translation")
    for bid, b in BLOCKS.items():
        if bid not in java_blocks:
            errors.append(f"content table block {bid} is not registered in LandBlocks.java")
        if b.get("item", True) != (bid in java_items):
            errors.append(f"block {bid}: block item mismatch between table and Java")
    # translation keys used from Java must exist
    for root, _, files in os.walk(os.path.join(JAVA, "land")):
        for fn in files:
            with open(os.path.join(root, fn), encoding="utf-8") as f:
                for key in re.findall(r'"((?:commands|message|pack)\.lavagenesis\.[a-z0-9_.]+)"', f.read()):
                    if key not in lang:
                        errors.append(f"{fn}: translation key {key} missing")
    # every item/block id named in data files must be one of ours or vanilla
    known = set(java_blocks) | set(java_items)
    for root, _, files in os.walk(DATA):
        for fn in files:
            path = os.path.join(root, fn)
            if path not in WRITTEN:
                continue
            with open(path, encoding="utf-8") as f:
                for ref in re.findall(r'"lavagenesis:([a-z0-9_/]+)"', f.read()):
                    if "/" in ref or ref in ("olivine_ore_replaceables",):
                        continue
                    if ref not in known:
                        errors.append(f"{os.path.relpath(path, ROOT)}: unknown id lavagenesis:{ref}")
    return errors


def main():
    build_textures()
    save_textures()
    build_models()
    build_loot()
    build_recipes()
    build_tags()
    build_worldgen()
    lang = build_lang()
    errors = audit(lang)
    print(f"gen_land: wrote {len(WRITTEN)} files ({len(TEXTURES)} textures, {len(BLOCKS)} blocks, {len(ITEMS)} items)")
    if errors:
        print("AUDIT FAILED:")
        for e in errors:
            print("  - " + e)
        sys.exit(1)
    print("audit: every model, texture, translation and loot table is present")


if __name__ == "__main__":
    main()
