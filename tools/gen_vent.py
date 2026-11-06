"""Generates every asset and data file of the vent module (Hotspot Vent) and audits them.

Run from anywhere:  python tools/gen_vent.py
Writes textures (Pillow, 16x16 pixel art, animated strips + .mcmeta), the blockstate, block/item models, the item
model definition, the loot table and lang_parts/vent.json, then checks that every texture/model/translation the
files and the vent Java code name exists.
"""
import json
import math
import random
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src" / "main" / "resources"
ASSETS = RES / "assets" / "lavagenesis"
DATA = RES / "data" / "lavagenesis"
JAVA = [ROOT / "src" / "main" / "java" / "com" / "lavagenesis" / "vent", ROOT / "src" / "client" / "java" / "com" / "lavagenesis" / "vent"]

# ------------------------------------------------------------------ palette (DESIGN.md)
LAVA = [(255, 252, 214), (255, 232, 120), (255, 186, 60), (255, 132, 24), (226, 72, 14), (168, 34, 10)]
CRUST = [(58, 26, 20), (78, 36, 24), (98, 46, 28)]
CRACK = [(255, 150, 40), (236, 96, 22), (180, 52, 14)]
BASALT = [(22, 24, 31), (31, 34, 43), (41, 45, 56), (53, 57, 70), (66, 71, 86)]


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def ramp(colors, t):
    """t in 0..1 along a list of colours."""
    t = max(0.0, min(1.0, t)) * (len(colors) - 1)
    i = min(int(t), len(colors) - 2)
    return lerp(colors[i], colors[i + 1], t - i)


class Noise:
    def __init__(self, seed):
        rnd = random.Random(seed)
        self.v = [[rnd.random() for _ in range(17)] for _ in range(17)]

    def at(self, x, y, scale):
        """Tileable value noise on a 16x16 tile."""
        gx, gy = x / scale, y / scale
        n = int(16 / scale)
        x0, y0 = int(math.floor(gx)) % n, int(math.floor(gy)) % n
        x1, y1 = (x0 + 1) % n, (y0 + 1) % n
        fx, fy = gx - math.floor(gx), gy - math.floor(gy)
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a, b, c, d = self.v[x0][y0], self.v[x1][y0], self.v[x0][y1], self.v[x1][y1]
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def basalt_pixel(n, x, y, rnd):
    v = 0.55 * n.at(x, y, 4) + 0.3 * n.at(x, y, 2) + 0.15 * rnd.random()
    return ramp(BASALT, v)


# Crack network shared by side and bottom so the faces line up at the edges.
def crack_mask(seed, count):
    rnd = random.Random(seed)
    mask = {}
    for k in range(count):
        x, y = rnd.randrange(16), rnd.randrange(16)
        length = rnd.randrange(5, 11)
        dx = rnd.choice([-1, 1])
        for i in range(length):
            mask[(x % 16, y % 16)] = max(mask.get((x % 16, y % 16), 0), 1.0 - i / (length + 2))
            if rnd.random() < 0.55:
                y += 1
            else:
                x += dx
            if rnd.random() < 0.15:
                dx = -dx
    return mask


def write_png(img, rel):
    path = ASSETS / "textures" / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    return path


def write_mcmeta(rel, frametime, interpolate=True):
    path = ASSETS / "textures" / (rel + ".mcmeta")
    path.write_text(json.dumps({"animation": {"frametime": frametime, "interpolate": interpolate}}, indent=2) + "\n", encoding="utf-8")


# ------------------------------------------------------------------ textures

def tex_top():
    """Crater seen from above: basalt rim ring (under the model's raised lip), crust edge, churning lava pool."""
    frames = 8
    img = Image.new("RGBA", (16, 16 * frames))
    n = Noise(11)
    cracks = crack_mask(5, 5)
    for f in range(frames):
        ph = f / frames * 2 * math.pi
        rnd = random.Random(100)
        for y in range(16):
            for x in range(16):
                cx, cy = x - 7.5, y - 7.5
                edge = max(abs(cx), abs(cy))          # square crater fits the square lip
                r = math.hypot(cx, cy)
                if edge > 5.0:
                    c = basalt_pixel(n, x, y, rnd)
                    if (x, y) in cracks:
                        glow = cracks[(x, y)] * (0.75 + 0.25 * math.sin(ph + x))
                        c = lerp(c, CRACK[1], glow * 0.8)
                    if edge < 6.0:                   # inner side of the rim warmed by the lava
                        c = lerp(c, CRACK[2], 0.35)
                elif edge > 4.0 and r > 4.6:
                    c = ramp(CRUST, n.at(x, y, 2))
                    c = lerp(c, CRACK[1], 0.25 + 0.2 * math.sin(ph * 2 + r))
                else:
                    a = math.atan2(cy, cx)
                    swirl = 0.5 + 0.5 * math.sin(a * 2 + r * 1.4 - ph)
                    churn = n.at((x + f * 2) % 16, (y + f) % 16, 4)
                    heat = 1.0 - r / 6.5
                    t = 0.15 + 0.45 * heat + 0.25 * swirl + 0.25 * churn
                    c = ramp(list(reversed(LAVA)), t)
                    if rnd.random() < 0.05:           # floating crust flakes
                        c = lerp(c, CRUST[2], 0.6)
                img.putpixel((x, y + 16 * f), c + (255,))
    write_png(img, "block/hotspot_vent_top.png")
    write_mcmeta("block/hotspot_vent_top.png", 3)


def tex_side():
    """Basalt with glowing cracks that pulse, a hot lip along the top."""
    frames = 6
    img = Image.new("RGBA", (16, 16 * frames))
    n = Noise(23)
    cracks = crack_mask(7, 9)
    for f in range(frames):
        pulse = 0.65 + 0.35 * math.sin(f / frames * 2 * math.pi)
        rnd = random.Random(200)
        for y in range(16):
            for x in range(16):
                c = basalt_pixel(n, x, y, rnd)
                if (x, y) in cracks:
                    g = cracks[(x, y)] * pulse
                    c = ramp([c, CRACK[2], CRACK[1], CRACK[0], LAVA[1]], g)
                if y == 0:
                    c = lerp(c, CRACK[0], 0.55 * pulse + 0.2)
                elif y == 1:
                    c = lerp(c, CRACK[2], 0.35 * pulse)
                if y >= 14:                           # cooler, darker base
                    c = lerp(c, BASALT[0], 0.35)
                img.putpixel((x, y + 16 * f), c + (255,))
    write_png(img, "block/hotspot_vent_side.png")
    write_mcmeta("block/hotspot_vent_side.png", 5)


def tex_bottom():
    img = Image.new("RGBA", (16, 16))
    n = Noise(31)
    cracks = crack_mask(9, 4)
    rnd = random.Random(300)
    for y in range(16):
        for x in range(16):
            c = basalt_pixel(n, x, y, rnd)
            if (x, y) in cracks:
                c = lerp(c, CRACK[2], cracks[(x, y)] * 0.6)
            img.putpixel((x, y), c + (255,))
    write_png(img, "block/hotspot_vent_bottom.png")


def tex_core():
    """Bright, mottled magma for the block entity renderer (vertex colours tint it)."""
    img = Image.new("RGBA", (16, 16))
    n = Noise(41)
    for y in range(16):
        for x in range(16):
            v = 0.6 * n.at(x, y, 4) + 0.4 * n.at(x, y, 2)
            c = ramp([(255, 196, 120), (255, 226, 170), (255, 246, 220), (255, 255, 250)], v)
            img.putpixel((x, y), c + (255,))
    write_png(img, "entity/hotspot_vent_core.png")


# ------------------------------------------------------------------ models / data

def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


def face(tex, uv=None, cull=None):
    f = {"texture": tex}
    if uv:
        f["uv"] = uv
    if cull:
        f["cullface"] = cull
    return f


def models():
    lip = "#side"
    elements = [
        {   # the vent body
            "from": [0, 0, 0], "to": [16, 16, 16],
            "faces": {
                "down": face("#bottom", cull="down"),
                "up": face("#top", cull="up"),
                "north": face("#side", cull="north"), "south": face("#side", cull="south"),
                "west": face("#side", cull="west"), "east": face("#side", cull="east"),
            },
        },
    ]
    # a raised 2 px basalt lip around the crater
    for frm, to in (([0, 16, 0], [16, 18, 3]), ([0, 16, 13], [16, 18, 16]), ([0, 16, 3], [3, 18, 13]), ([13, 16, 3], [16, 18, 13])):
        elements.append({
            "from": frm, "to": to,
            "faces": {
                "up": face("#top", uv=[frm[0], frm[2], to[0], to[2]]),
                "north": face(lip, uv=[16 - to[0], 0, 16 - frm[0], 2]), "south": face(lip, uv=[frm[0], 0, to[0], 2]),
                "west": face(lip, uv=[frm[2], 0, to[2], 2]), "east": face(lip, uv=[16 - to[2], 0, 16 - frm[2], 2]),
            },
        })
    write_json(ASSETS / "models" / "block" / "hotspot_vent.json", {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "lavagenesis:block/hotspot_vent_side",
            "top": "lavagenesis:block/hotspot_vent_top",
            "side": "lavagenesis:block/hotspot_vent_side",
            "bottom": "lavagenesis:block/hotspot_vent_bottom",
        },
        "elements": elements,
    })
    write_json(ASSETS / "blockstates" / "hotspot_vent.json", {"variants": {"": {"model": "lavagenesis:block/hotspot_vent"}}})
    write_json(ASSETS / "items" / "hotspot_vent.json", {"model": {"type": "minecraft:model", "model": "lavagenesis:block/hotspot_vent"}})
    write_json(DATA / "loot_table" / "blocks" / "hotspot_vent.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "condition": {"type": "minecraft:survives_explosion"},
            "entries": [{"type": "minecraft:item", "name": "lavagenesis:hotspot_vent"}],
        }],
        "random_sequence": "lavagenesis:blocks/hotspot_vent",
    })


LANG = {
    "block.lavagenesis.hotspot_vent": "Hotspot Vent",

    "requirement.lavagenesis.mass": "Mass",
    "requirement.lavagenesis.islands": "Islands",
    "requirement.lavagenesis.largest_island": "Largest island",
    "requirement.lavagenesis.offering": "Offering",
    "requirement.lavagenesis.home_vent": "Home vent",
    "requirement.lavagenesis.mass.line": "Mass %s / %s",
    "requirement.lavagenesis.islands.line": "Islands above the sea %s / %s",
    "requirement.lavagenesis.largest_island.line": "Largest island %s / %s land columns",
    "requirement.lavagenesis.offering.line": "Offering at the home vent: %s / %s %s",
    "requirement.lavagenesis.home_vent.yes": "Home vent bound",
    "requirement.lavagenesis.home_vent.no": "No home vent bound - you need a Hotspot Vent of your own",

    "message.lavagenesis.vent.header_home": "Your home Hotspot Vent (island here: %s land columns)",
    "message.lavagenesis.vent.header_island": "Your island vent (island here: %s land columns)",
    "message.lavagenesis.vent.next": "Next stage: %s",
    "message.lavagenesis.vent.final": "You are the %s. There is nothing greater left to become.",
    "message.lavagenesis.vent.ready": "Everything is in place - the hotspot is about to surge!",
    "message.lavagenesis.vent.offer_at_home": "Offerings are given at your home vent.",
    "message.lavagenesis.vent.offered": "The vent drinks it in: %s / %s %s",
    "message.lavagenesis.vent.offering_full": "The vent has all the offerings it needs for now.",
    "message.lavagenesis.vent.not_now": "The vent does not want %s yet - it asks for %s.",
    "message.lavagenesis.vent.no_offering": "no offering at all",
    "message.lavagenesis.vent.not_yours": "This vent answers only to %s.",
    "message.lavagenesis.vent.belongs_to": "This Hotspot Vent belongs to %s.",
    "message.lavagenesis.vent.unclaimed": "An unclaimed Hotspot Vent.",
    "message.lavagenesis.vent.unbreakable_other": "The hotspot of %s will not yield to you.",
    "message.lavagenesis.vent.unbreakable_home": "Your home vent is the hotspot itself - it cannot be moved.",
    "message.lavagenesis.vent.placed_home": "The hotspot wakes beneath you: this is now your home vent.",
    "message.lavagenesis.vent.placed_island": "A new island vent, fed by your hotspot.",
    "message.lavagenesis.vent.home_lost": "Your home vent is gone! Bind a new Hotspot Vent to keep growing.",

    "message.lavagenesis.finale.start": "The sea floor heaves. Your island begins to rise into a Great Volcano...",
    "message.lavagenesis.finale.done": "The Great Volcano stands complete. Its summit lake is your new home vent.",
}


def lang():
    path = ROOT / "lang_parts" / "vent.json"
    path.write_text(json.dumps(LANG, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


# ------------------------------------------------------------------ audit

def audit():
    problems = []

    def tex_exists(ref):
        ns, p = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
        if ns != "lavagenesis":
            return True
        return (RES / "assets" / ns / "textures" / (p + ".png")).exists()

    def model_exists(ref):
        ns, p = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
        if ns != "lavagenesis":
            return True
        return (RES / "assets" / ns / "models" / (p + ".json")).exists()

    for m in (ASSETS / "models").rglob("hotspot_vent*.json"):
        data = json.loads(m.read_text(encoding="utf-8"))
        for ref in data.get("textures", {}).values():
            if not ref.startswith("#") and not tex_exists(ref):
                problems.append(f"{m.name}: missing texture {ref}")
        for el in data.get("elements", []):
            for fc in el["faces"].values():
                key = fc["texture"]
                if key.startswith("#") and key[1:] not in data.get("textures", {}):
                    problems.append(f"{m.name}: face uses undefined {key}")
    for f in [ASSETS / "blockstates" / "hotspot_vent.json", ASSETS / "items" / "hotspot_vent.json"]:
        if not f.exists():
            problems.append(f"missing {f}")
            continue
        for ref in re.findall(r'"model":\s*"([^"]+)"', f.read_text(encoding="utf-8")):
            if not model_exists(ref):
                problems.append(f"{f.name}: missing model {ref}")
    if not (DATA / "loot_table" / "blocks" / "hotspot_vent.json").exists():
        problems.append("missing loot table")

    # textures and translations named in the Java code
    for d in JAVA:
        for src in d.rglob("*.java"):
            text = src.read_text(encoding="utf-8")
            for ref in re.findall(r'id\("textures/([^"]+)\.png"\)', text):
                if not (ASSETS / "textures" / (ref + ".png")).exists():
                    problems.append(f"{src.name}: missing texture textures/{ref}.png")
            for key in re.findall(r'"((?:message|requirement|block|item)\.lavagenesis\.[a-z0-9_.]+)"', text):
                if key.endswith(".") or key in LANG:
                    continue
                problems.append(f"{src.name}: missing translation {key}")
    # keys assembled at runtime from requirement names
    for k in ("mass", "islands", "largest_island", "offering", "home_vent"):
        if f"requirement.lavagenesis.{k}" not in LANG:
            problems.append(f"missing translation requirement.lavagenesis.{k}")
    for k in ("mass", "islands", "largest_island"):
        if f"requirement.lavagenesis.{k}.line" not in LANG:
            problems.append(f"missing translation requirement.lavagenesis.{k}.line")
    # no key may be claimed by another lang part
    for part in (ROOT / "lang_parts").glob("*.json"):
        if part.name == "vent.json":
            continue
        try:
            other = json.loads(part.read_text(encoding="utf-8"))
        except Exception as e:  # another agent may be mid-write
            print(f"note: could not read {part.name}: {e}")
            continue
        for k in LANG:
            if k in other:
                problems.append(f"translation {k} also defined in {part.name}")
    return problems


def main():
    tex_top()
    tex_side()
    tex_bottom()
    tex_core()
    models()
    lang()
    problems = audit()
    if problems:
        print("AUDIT FAILED:")
        for p in problems:
            print("  " + p)
        sys.exit(1)
    print("vent assets generated and audited OK")


if __name__ == "__main__":
    main()
