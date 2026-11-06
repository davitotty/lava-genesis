"""Textures of the form module (the player's lava body). Run from the project root: python tools/gen_form.py

Writes:
  textures/entity/form/magma_blob.png      64x32  equirectangular lava for the blob core (Ember..Lava Flow)
  textures/entity/form/magma_halo.png      16x16  soft glow shell around the blob
  textures/entity/form/lava_crust_skin.png 64x64  player skin: basalt crust with orange cracks (Seamount on)
  textures/entity/form/lava_crust_glow.png 64x64  only the cracks and eyes, drawn full-bright on top
  textures/misc/form_crust_overlay.png     256x256 screen vignette when the body is crusting

Then checks that every texture the form module's Java code names exists, and that every stage has a passive text.
"""
import json
import math
import random
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/lavagenesis"
TEX = ASSETS / "textures"

# shared palette (DESIGN.md): glowing lava, cooling crust, basalt
LAVA_CORE = (255, 246, 196)
LAVA_YELLOW = (255, 210, 90)
LAVA_ORANGE = (255, 138, 30)
LAVA_RED = (214, 64, 12)
LAVA_DEEP = (140, 28, 8)
CRUST = [(30, 26, 29), (42, 34, 38), (58, 43, 40), (74, 49, 40), (36, 30, 36)]


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def value_noise(w, h, cell, rnd, wrap_x=True):
    """Smooth tileable value noise in 0..1."""
    gw, gh = max(1, w // cell), max(1, h // cell) + 1
    grid = [[rnd.random() for _ in range(gw)] for _ in range(gh)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        gy = y / cell
        y0 = int(gy)
        ty = gy - y0
        ty = ty * ty * (3 - 2 * ty)
        for x in range(w):
            gx = x / cell
            x0 = int(gx)
            tx = gx - x0
            tx = tx * tx * (3 - 2 * tx)
            x1 = (x0 + 1) % gw if wrap_x else min(x0 + 1, gw - 1)
            a = grid[min(y0, gh - 1)][x0 % gw]
            b = grid[min(y0, gh - 1)][x1]
            c = grid[min(y0 + 1, gh - 1)][x0 % gw]
            d = grid[min(y0 + 1, gh - 1)][x1]
            out[y][x] = (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty
    return out


def lava_color(v):
    """0..1 -> deep red .. white-yellow."""
    stops = [(0.0, LAVA_DEEP), (0.35, LAVA_RED), (0.6, LAVA_ORANGE), (0.82, LAVA_YELLOW), (1.0, LAVA_CORE)]
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if v <= t1:
            return lerp(c0, c1, (v - t0) / (t1 - t0))
    return LAVA_CORE


def magma_blob():
    rnd = random.Random(1001)
    w, h = 64, 32
    n1 = value_noise(w, h, 8, rnd)
    n2 = value_noise(w, h, 4, rnd)
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            v = 0.62 * n1[y][x] + 0.38 * n2[y][x]
            # bright veins where the noise crosses the middle
            vein = 1.0 - min(1.0, abs(n1[y][x] - 0.5) * 9.0)
            v = min(1.0, v * 0.85 + vein * 0.45 + 0.12)
            c = lava_color(v)
            if rnd.random() < 0.03 and vein < 0.2:
                c = lerp(c, CRUST[2], 0.55)       # a few cooling flecks
            img.putpixel((x, y), c + (255,))
    return img


def magma_halo():
    rnd = random.Random(2002)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            a = 150 + rnd.randint(-40, 40)
            img.putpixel((x, y), lerp(LAVA_ORANGE, LAVA_YELLOW, rnd.random() * 0.6) + (a,))
    return img


# player skin layout: base-layer boxes that get crust, everything else stays transparent (hat/jacket/sleeves)
BASE_REGIONS = [
    (0, 0, 32, 16),    # head
    (0, 16, 16, 32),   # right leg
    (16, 16, 40, 32),  # body
    (40, 16, 56, 32),  # right arm
    (16, 48, 32, 64),  # left leg
    (32, 48, 48, 64),  # left arm
]
# the unused corners of each box row (e.g. 0..8 x 0..8 of the head) are never sampled; filling them is harmless


def in_base(x, y):
    return any(x0 <= x < x1 and y0 <= y < y1 for x0, y0, x1, y1 in BASE_REGIONS)


def crack_mask(rnd):
    """Random-walk cracks over the skin; value 2 = hot core of a crack, 1 = its glowing edge."""
    mask = [[0] * 64 for _ in range(64)]
    for x0, y0, x1, y1 in BASE_REGIONS:
        area = (x1 - x0) * (y1 - y0)
        for _ in range(max(2, area // 70)):
            x = rnd.randrange(x0, x1)
            y = rnd.randrange(y0, y1)
            dx, dy = rnd.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
            for _ in range(rnd.randint(5, 12)):
                if x0 <= x < x1 and y0 <= y < y1:
                    mask[y][x] = 2
                    for ex, ey in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                        if x0 <= ex < x1 and y0 <= ey < y1 and mask[ey][ex] == 0 and rnd.random() < 0.35:
                            mask[ey][ex] = 1
                if rnd.random() < 0.35:
                    dx, dy = rnd.choice([(1, 0), (-1, 0), (0, 1), (0, -1), (dx, dy)])
                x += dx
                y += dy
    # the face: two glowing eyes and a molten mouth on the head front (8..16 x 8..16)
    for ex in (9, 10, 13, 14):
        mask[12][ex] = 3
    for mx in (10, 11, 12, 13):
        mask[14][mx] = 2
    return mask


def crust_skins():
    rnd = random.Random(3003)
    noise = value_noise(64, 64, 4, rnd, wrap_x=False)
    mask = crack_mask(rnd)
    skin = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    glow = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for y in range(64):
        for x in range(64):
            if not in_base(x, y):
                continue
            n = noise[y][x]
            base = CRUST[min(len(CRUST) - 1, int(n * len(CRUST)))]
            if rnd.random() < 0.08:
                base = lerp(base, (96, 60, 44), 0.5)    # rusty, still-warm specks
            m = mask[y][x]
            if m == 3:
                skin.putpixel((x, y), LAVA_CORE + (255,))
                glow.putpixel((x, y), LAVA_CORE + (255,))
            elif m == 2:
                skin.putpixel((x, y), LAVA_ORANGE + (255,))
                glow.putpixel((x, y), LAVA_YELLOW + (255,))
            elif m == 1:
                skin.putpixel((x, y), lerp(base, LAVA_RED, 0.6) + (255,))
                glow.putpixel((x, y), LAVA_RED + (200,))
            else:
                skin.putpixel((x, y), base + (255,))
    return skin, glow


def crust_overlay():
    rnd = random.Random(4004)
    size = 256
    noise = value_noise(size, size, 16, rnd, wrap_x=False)
    fine = value_noise(size, size, 5, rnd, wrap_x=False)
    img = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            dx = (x - size / 2 + 0.5) / (size / 2)
            dy = (y - size / 2 + 0.5) / (size / 2)
            d = math.sqrt(dx * dx + dy * dy) + (noise[y][x] - 0.5) * 0.35
            a = max(0.0, min(1.0, (d - 0.55) / 0.55))
            a = a ** 1.4
            n = fine[y][x]
            c = CRUST[min(len(CRUST) - 1, int(n * len(CRUST)))]
            crack = abs(noise[y][x] - 0.5) < 0.018 or abs(fine[y][x] - 0.5) < 0.012
            if crack and a > 0.15:
                c = lerp(LAVA_RED, LAVA_ORANGE, rnd.random())
                a = min(1.0, a + 0.2)
            img.putpixel((x, y), c + (int(a * 235),))
    return img


def save(img, rel):
    path = TEX / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print("wrote", path.relative_to(ROOT))


def audit():
    ok = True
    java = list((ROOT / "src/main/java/com/lavagenesis/form").rglob("*.java")) + \
        list((ROOT / "src/client/java/com/lavagenesis/form").rglob("*.java"))
    for f in java:
        for m in re.finditer(r'LavaGenesis\.id\("(textures/[^"]+)"\)', f.read_text(encoding="utf-8")):
            p = ASSETS / m.group(1)
            if not p.exists():
                print("MISSING texture", m.group(1), "named in", f.name)
                ok = False
    lang = json.loads((ROOT / "lang_parts/form.json").read_text(encoding="utf-8"))
    stages = ["ember", "magma_droplet", "lava_flow", "seamount", "islet", "shield_island", "island_chain",
              "archipelago", "great_volcano"]
    for s in stages:
        for key in (f"passive.lavagenesis.{s}", f"passive.lavagenesis.{s}.title"):
            if key not in lang:
                print("MISSING translation", key)
                ok = False
    for f in java:
        for m in re.finditer(r'"((?:form|passive|death\.attack)\.lavagenesis[a-z_.]*)"', f.read_text(encoding="utf-8")):
            if m.group(1) not in lang:
                print("MISSING translation", m.group(1), "named in", f.name)
                ok = False
    dmg = ROOT / "src/main/resources/data/lavagenesis/damage_type/solidified.json"
    msg = json.loads(dmg.read_text(encoding="utf-8"))["message_id"]
    if f"death.attack.{msg}" not in lang:
        print("MISSING death message", f"death.attack.{msg}")
        ok = False
    print("audit", "ok" if ok else "FAILED")
    return ok


def main():
    save(magma_blob(), "entity/form/magma_blob.png")
    save(magma_halo(), "entity/form/magma_halo.png")
    skin, glow = crust_skins()
    save(skin, "entity/form/lava_crust_skin.png")
    save(glow, "entity/form/lava_crust_glow.png")
    save(crust_overlay(), "misc/form_crust_overlay.png")
    sys.exit(0 if audit() else 1)


if __name__ == "__main__":
    main()
