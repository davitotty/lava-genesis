"""Experience module asset generator for Lava Genesis.

Writes (all owned by the experience module):
  lang_parts/experience.json
  src/main/resources/assets/lavagenesis/icon.png                     (128x128 mod icon)
  src/main/resources/assets/lavagenesis/textures/item/lava_journal.png
  src/main/resources/assets/lavagenesis/textures/gui/advancements/backgrounds/experience_basalt.png
  src/main/resources/assets/lavagenesis/items/lava_journal.json
  src/main/resources/assets/lavagenesis/models/item/lava_journal.json
  src/main/resources/data/lavagenesis/advancement/experience/*.json
  src/main/resources/data/lavagenesis/recipe/lava_journal.json

Then audits that every translation key the experience Java code uses exists, every texture a model names exists,
and every advancement icon is a real item.

Run:  python tools/gen_experience.py
"""
from __future__ import annotations

import json
import math
import os
import random
import re
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src" / "main" / "resources"
ASSETS = RES / "assets" / "lavagenesis"
DATA = RES / "data" / "lavagenesis"
NS = "lavagenesis"

# ----------------------------------------------------------------------------------------------- palette
LAVA_WHITE = (255, 246, 196)
LAVA_YELLOW = (255, 210, 90)
LAVA_ORANGE = (255, 120, 20)
LAVA_RED = (196, 40, 12)
LAVA_DEEP = (120, 20, 8)
CRUST = (74, 34, 24)
CRUST_DARK = (46, 22, 18)
BASALT_0 = (18, 18, 24)
BASALT_1 = (30, 31, 40)
BASALT_2 = (46, 48, 60)
BASALT_3 = (66, 68, 82)
ASH = (150, 146, 142)
ASH_DARK = (100, 96, 94)
LUSH = (70, 140, 58)
LUSH_DARK = (40, 92, 40)
OLIVINE = (130, 170, 60)
PAPER = (226, 210, 170)
PAPER_DARK = (180, 160, 120)

# ----------------------------------------------------------------------------------------------- advancements
# name, parent, icon item, frame, title, description, (announce, toast, hidden)
STAGE_ADVANCEMENTS = [
    ("magma_droplet", "root", "minecraft:magma_cream", "task", "Hungry Spark", "Grow into a Magma Droplet"),
    ("lava_flow", "magma_droplet", "minecraft:blaze_powder", "task", "River of Fire", "Become a Lava Flow"),
    ("seamount", "lava_flow", "minecraft:basalt", "task", "Mountain in the Deep", "Pile up pillow lava until you are a Seamount"),
    ("islet", "seamount", "minecraft:smooth_basalt", "goal", "Breaking the Surface", "Rise above the waves as an Islet"),
    ("shield_island", "islet", "minecraft:shield", "task", "Shield of Basalt", "Build a broad Shield Island"),
    ("island_chain", "shield_island", "minecraft:iron_chain", "task", "Stepping Stones", "Spread out into an Island Chain"),
    ("archipelago", "island_chain", "minecraft:fern", "goal", "A Kingdom of Islands", "Rule an Archipelago"),
    ("great_volcano", "archipelago", "minecraft:respawn_anchor", "challenge", "The Great Volcano",
     "Become the largest mountain on the planet"),
]
FUN_ADVANCEMENTS = [
    ("first_melt", "root", "minecraft:flint_and_steel", "task", "Carving the Chamber", "Melt stone into lava with Flare", False),
    ("land_from_fire", "first_melt", "minecraft:magma_block", "task", "Land From Fire", "Raise new land with an eruption", False),
    ("first_sip", "root", "minecraft:lava_bucket", "task", "A Taste of Fire", "Drink lava to gain mass", False),
    ("upwelling", "first_sip", "minecraft:campfire", "task", "Fed From Below",
     "Rest in lava near your home vent and let the hotspot well up into you", False),
    ("first_meal", "root", "minecraft:amethyst_shard", "task", "Rock Candy", "Eat something volcanic", False),
    ("close_to_stone", "root", "minecraft:cobblestone", "task", "Close to Stone", "Let your heat fall below 10% - and live", False),
    ("first_offering", "lava_flow", "minecraft:amethyst_cluster", "task", "A Gift for the Hotspot",
     "Give an offering to your home vent", False),
    ("first_island", "seamount", "minecraft:oak_sapling", "goal", "Land Ho!", "Raise your first island above sea level", False),
    ("three_islands", "first_island", "minecraft:compass", "task", "Island Hopper", "Have three islands", False),
    ("six_islands", "three_islands", "minecraft:map", "challenge", "Hotspot Trail", "Have six islands", False),
    ("big_island", "first_island", "minecraft:grass_block", "goal", "The Big Island", "Grow one island to 1,000 land columns", False),
    ("land_baron", "big_island", "minecraft:moss_block", "challenge", "Continental Ambitions", "Own 5,000 land columns in all", False),
    ("heavier_than_mountains", "great_volcano", "minecraft:nether_star", "challenge", "Heavier Than Mountains",
     "Gather 10,000 mass", True),
]
ROOT_ADV = ("root", None, "minecraft:lava_bucket", "task", "Lava Genesis", "Be born as a single spark of lava beneath the sea")

# ----------------------------------------------------------------------------------------------- translations
LANG: dict[str, str] = {
    "item.lavagenesis.lava_journal": "Lava Journal",
    "key.lavagenesis.toggle_hud": "Cycle Lava Panel (full / compact / hidden)",

    # HUD
    "hud.lavagenesis.stage_of": "Stage %s of %s",
    "hud.lavagenesis.final": "Final stage",
    "hud.lavagenesis.mass": "Mass",
    "hud.lavagenesis.heat": "Heat",
    "hud.lavagenesis.solidifying": "Setting!",
    "hud.lavagenesis.pressure": "Pressure",
    "hud.lavagenesis.islands": "Islands",
    "hud.lavagenesis.largest": "Largest",
    "hud.lavagenesis.land": "Land",
    "hud.lavagenesis.next": "Next: %s",
    "hud.lavagenesis.done": "The archipelago is yours.",
    "hud.lavagenesis.check.mass": "Mass",
    "hud.lavagenesis.check.islands": "Islands",
    "hud.lavagenesis.check.largest": "Largest island",
    "hud.lavagenesis.check.vent": "Home vent bound",
    "hud.lavagenesis.power_keys": "%s use  %s switch  %s Flare",
    "hud.lavagenesis.mode": "Lava panel: %s",
    "hud.lavagenesis.mode.full": "full",
    "hud.lavagenesis.mode.compact": "compact",
    "hud.lavagenesis.mode.hidden": "hidden",

    # stage-up celebration and feedback
    "experience.lavagenesis.stage_up.header": "You are now %s",
    "experience.lavagenesis.stage_up.power": "New power: %s",
    "experience.lavagenesis.stage_up.passive": "New gift: %s",
    "experience.lavagenesis.stage_up.size": "Your body swells to %s of a human's size",
    "experience.lavagenesis.stage_up.next": "Next: %s - %s",
    "experience.lavagenesis.stage_up.final": "There is nothing left to become. Keep erupting: the sea is yours.",
    "experience.lavagenesis.req.mass": "%s mass",
    "experience.lavagenesis.req.islands": "%s island(s)",
    "experience.lavagenesis.req.largest": "largest island %s",
    "experience.lavagenesis.req.offering": "%s %s",
    "experience.lavagenesis.req.vent": "a home vent",
    "experience.lavagenesis.finale.rumble": "The sea floor groans. Something enormous is waking...",
    "experience.lavagenesis.finale.title2": "The hotspot is satisfied",
    "experience.lavagenesis.finale.subtitle2": "...for now. Keep erupting, Great Volcano.",
    "experience.lavagenesis.finale.journey": "From spark to Great Volcano in %s: %s mass, %s islands, %s columns of land.",
    "experience.lavagenesis.mass.quarter": "Mass %s%% of the way to %s (%s / %s)",
    "experience.lavagenesis.mass.ready": "You have the mass to become %s!",
    "experience.lavagenesis.mass.ready_chat": "Enough mass for %s. In all it asks for: %s",
    "experience.lavagenesis.mass.beyond": "%s mass - and still the mountain grows",
    "experience.lavagenesis.hint.cooling": "You are cooling! Water and rain steal your heat. Get back into lava, onto magma, or near your home vent.",
    "experience.lavagenesis.welcome.title": "Lava Genesis",
    "experience.lavagenesis.welcome.journal": "A Lava Journal smoulders in your pack. Hold it and use it to read what you have become.",
}

J = "book.lavagenesis.journal."
BOOK = {
    "title": "Lava Journal",
    "subtitle": "kept by the fire beneath the sea",
    "motto": "Read on, little spark.",
    "contents": "Contents",
    "contents.goto": "Go to page %s",
    "contents.intro": "What You Are",
    "contents.heat": "Heat",
    "contents.mass": "Mass",
    "contents.pressure": "Pressure",
    "contents.powers": "Powers",
    "contents.islands": "Islands",
    "contents.stages": "The Nine Stages",
    "contents.offerings": "Offerings",
    "contents.tips": "Tips",
    "powers": "Powers",
    "controls": "Controls",
    "controls.use": "Unleash the selected power: %s",
    "controls.switch": "Switch power: %s (sneak to go back)",
    "controls.flare": "Flare, whatever is selected: %s",
    "controls.panel": "Shrink or hide the lava panel: %s",
    "controls.more": "Change them under Options, Controls, Key Binds: Lava Genesis.",
    "contents.controls": "Controls",
    "stage.gift": "Gift: %s",
    "stage.number": "Stage %s of %s",
    "stage.start": "Where every volcano begins. Melt, drink and grow.",
    "stage.needs": "%s asks for:",
    "stage.mass": "%s mass",
    "stage.islands": "%s island(s) above the sea",
    "stage.largest": "a largest island of %s land columns",
    "stage.offering": "an offering of %s %s at your home vent",
    "stage.vent": "a home vent bound to you",
    "stage.power": "Power: %s",
    "stage.size": "Body: %s%% of a human",
    "offering.for": "Offer %s to become %s",

    # section headers (bold, on the first page of each section)
    "intro.header": "What You Are",
    "heat.header": "Heat",
    "mass.header": "Mass",
    "pressure.header": "Pressure",
    "islands.header": "Islands",
    "stages.header": "The Nine Stages",
    "offerings.header": "Offerings",
    "tips.header": "Tips",

    # prose pages: book.lavagenesis.journal.<section>.<n>  (counts must match LavaJournal.SECTIONS)
    "intro.1": "You were not born. You were released.\n\nFar below the waves a hotspot breathes, and its breath is you: one spark of lava in the throat of a drowned volcano.",
    "intro.2": "You are not someone who lives beside a volcano.\n\nYou are the volcano.\n\nAll you melt, drink and pour out becomes you, until the sea itself must make room.",
    "intro.3": "The panel at the top left of your sight is your body: stage, mass, heat, pressure, land, and what the next stage still asks of you.\n\nPress %s to shrink or hide it.",
    "heat.1": "Heat is your life. While you glow, you live.\n\nWater and rain steal it in hissing steam. A tiny ember cools in moments; a seamount hardly notices the sea.",
    "heat.2": "Lava, magma, fire and your own vent warm you again.\n\nWhen the heat bar fades to grey you begin to set like stone: first slow, then cracking. Find lava. Quickly.",
    "mass.1": "Mass is how much magma you are: the measure of your growth. Every stage asks for more, and the top bar of your panel shows how close you are.",
    "mass.2": "Gather it like this:\n\n- melt stone with Flare: +1\n- sneak in lava to drink a source: +3\n- swallow a magma block: +4\n- eat a Magma Shard: +6",
    "mass.3": "- rest in lava near your home vent and the hotspot wells up into you, more every stage\n- offer gifts at your vent: +10 each\n- new land raised by eruptions feeds you too",
    "pressure.1": "Pressure is the force behind your powers. Every eruption spends it, and it builds again by itself - faster in lava and close to your vent.",
    "pressure.2": "Each stage wakes a new power. Choose one and unleash it. Small at first, your powers grow until they can raise whole islands from the sea.",
    "islands.1": "Land only counts while it stands above the waves. Your vents measure the island around them: every column topped with volcanic rock at or above sea level.",
    "islands.2": "Black sand, basalt and pillow lava, ash and volcanic soil, and the ferns, ohia and palms that come later all count. Ordinary stone, sand and dirt do not.",
    "islands.3": "Your vents on one landmass make one island. For a new island, cross open water and raise a new vent there. Far islands are remembered when you leave.",
    "stages.1": "From spark to Great Volcano you will wear nine bodies. The pages that follow name each one and what it asks of you.\n\nWhen all is met, the hotspot surges and you grow.",
    "offerings.1": "Some stages demand a gift. Carry it to your home vent and use it on the vent. Each gift counts toward the next stage and feeds you +10 mass.",
    "tips.1": "- While small, never stray far from lava.\n- Rain is cold: shelter, or stay in the fire.\n- Your home vent warms you, feeds you and brings you back if you fall.",
    "tips.2": "- Islands rise best from the shallows: build up from the sea floor where the water is not too deep.\n- The checklist on your panel shows what the next stage still needs.",
    "tips.3": "- Lost this journal? Craft another from a book and a lump of coal or charcoal.\n- Every stage also brings a gift to your body. Read the chat when you grow.",
    "end.1": "One day the sea will part around a mountain taller than any other on this world, measured from the abyss to its smoking crown.\n\nThat mountain is you.\n\nNow go. Burn.",

    # where to find each offering (book.lavagenesis.journal.offering.<item path>)
    "offering.magma_shard": "Break Molten Rock in your chamber, or lava that has only just begun to cool. Shards are good to eat, too: +6 mass.",
    "offering.olivine": "Mine Olivine Ore in the walls of your volcano and on the sea floor. Lava that cools deep underwater sometimes turns to olivine.",
    "offering.peles_tear": "When your lava cools in the open air above the sea, escaping gas sometimes flings out a glassy drop. Listen for the chime.",
    "offering.sulfur_crystal": "Fumaroles steam on fresh land under the open sky. Sulfur clusters grow on top: harvest them fully grown.",
    "offering.volcanic_glass": "Break Glassy Basalt: black glass made where lava is quenched by the sea or by the waves.",
    "offering.hotspot_heart": "Craft it: two each of Pele's Tear, Volcanic Glass, Sulfur Crystal and Olivine around a magma block.",
}
for k, v in BOOK.items():
    LANG[J + k] = v

for name, parent, icon, frame, title, desc in [ROOT_ADV] + STAGE_ADVANCEMENTS:
    LANG[f"advancements.lavagenesis.experience.{name}.title"] = title
    LANG[f"advancements.lavagenesis.experience.{name}.description"] = desc
for name, parent, icon, frame, title, desc, hidden in FUN_ADVANCEMENTS:
    LANG[f"advancements.lavagenesis.experience.{name}.title"] = title
    LANG[f"advancements.lavagenesis.experience.{name}.description"] = desc


# ----------------------------------------------------------------------------------------------- helpers
def write_json(path: Path, obj) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def jitter(c, rng, amt):
    return tuple(max(0, min(255, v + rng.randint(-amt, amt))) for v in c)


# ----------------------------------------------------------------------------------------------- textures
def journal_texture() -> Image.Image:
    """16x16 Lava Journal: a basalt-bound book with a glowing volcano emblem and lava-lit page edges."""
    rows = [
        "................",
        "....kkkkkkkkk...",
        "...kSbbbbbbbbk..",
        "...kSbbybbybbkp.",
        "...kSbbbyobbbkp.",
        "...kSbbdoodbbkp.",
        "...kSbddroddbkp.",
        "...kSdddoddddkp.",
        "...kSddodddddkp.",
        "...kSdoddddddkp.",
        "...kSwwwwwwwwkp.",
        "...kSbbbbbbbbkp.",
        "...kSbbbbbbbbkp.",
        "...kkkkkkkkkkkp.",
        "....ppppppppppl.",
        "................",
    ]
    pal = {
        "k": BASALT_0,           # outline
        "S": BASALT_3,           # spine highlight
        "b": BASALT_1,           # cover
        "d": (84, 78, 86),       # emblem cone (warm grey, lit by the lava)
        "y": LAVA_YELLOW,        # spark
        "o": LAVA_ORANGE,        # lava
        "r": LAVA_RED,           # lava edge
        "c": CRUST,              # glowing crust band
        "w": (34, 74, 124),      # the sea
        "p": PAPER,              # page edges
        "l": PAPER_DARK,
    }
    rng = random.Random(7)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            c = pal[ch]
            if ch == "b":
                c = jitter(c, rng, 4)
            img.putpixel((x, y), c + (255,))
    # steam where the flow meets the sea, a glint on the water
    img.putpixel((5, 10), (200, 200, 210, 255))
    img.putpixel((10, 10), (70, 120, 170, 255))
    # warm glow on the page edge nearest the emblem
    for y in (4, 5, 6):
        img.putpixel((14, y), lerp(PAPER, LAVA_YELLOW, 0.35) + (255,))
    return img


def basalt_background() -> Image.Image:
    """16x16 tiling advancement background: dark basalt with faint glowing seams."""
    rng = random.Random(1234)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            base = BASALT_1 if (x * 7 + y * 13) % 5 else BASALT_0
            img.putpixel((x, y), jitter(base, rng, 5) + (255,))
    # wrapping seams (columnar basalt joints) - drawn with modulo so the tile repeats seamlessly
    for i in range(16):
        img.putpixel((i % 16, (5 + i // 4) % 16), BASALT_2 + (255,))
        img.putpixel(((11 + i // 5) % 16, i % 16), BASALT_2 + (255,))
    for x, y in [(3, 6), (4, 6), (12, 9), (12, 10), (7, 14)]:
        img.putpixel((x, y), lerp(CRUST, LAVA_RED, 0.4) + (255,))
    return img


def mod_icon() -> Image.Image:
    """128x128: a shield volcano island rising from a blue sea, crater glowing, lava running to the sea."""
    S = 4                                   # draw at 4x and downsample for smooth edges
    W = 128 * S
    rng = random.Random(42)
    img = Image.new("RGBA", (W, W))
    px = img.load()
    horizon = int(80 * S)
    peak_x, peak_y = 64 * S, 32 * S
    half = 58 * S

    def surface(x):                         # top of the island at column x (None = sea)
        d = abs(x - peak_x) / half
        if d >= 1.0:
            return None
        y = peak_y + (horizon - peak_y) * (d ** 1.75)
        if d < 0.13:                        # summit caldera
            y += (0.13 - d) / 0.13 * 6 * S
        return y

    # sky and sea
    for y in range(W):
        if y < horizon:
            t = y / horizon
            c = lerp((14, 18, 52), (70, 46, 92), t * 1.2) if t < 0.65 else lerp((70, 46, 92), (240, 132, 70), (t - 0.65) / 0.35)
        else:
            t = (y - horizon) / (W - horizon)
            c = lerp((40, 110, 170), (8, 30, 76), t)
        for x in range(W):
            px[x, y] = c + (255,)

    # stars
    for _ in range(26):
        x, y = rng.randrange(W), rng.randrange(int(horizon * 0.45))
        b = rng.randint(150, 255)
        for dx in range(S // 2 + 1):
            for dy in range(S // 2 + 1):
                px[min(W - 1, x + dx), min(W - 1, y + dy)] = (b, b, min(255, b + 20), 255)

    draw = ImageDraw.Draw(img)
    # distant islet on the left horizon
    draw.polygon([(6 * S, horizon), (14 * S, horizon - 5 * S), (20 * S, horizon - 6 * S), (27 * S, horizon)], fill=(34, 40, 56, 255))

    # glow behind the summit
    glow = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for r in range(40 * S, 0, -2 * S):
        a = int(150 * (1 - r / (40 * S)) ** 1.3)
        gd.ellipse([peak_x - r, peak_y - r, peak_x + r, peak_y + r], fill=(255, 120, 30, a))
    img.alpha_composite(glow)
    px = img.load()

    # the island body
    for x in range(W):
        top = surface(x)
        if top is None:
            continue
        d = abs(x - peak_x) / half
        for y in range(int(top), horizon + 2 * S):
            depth = (y - top) / max(1, horizon - top)
            lit = x < peak_x                  # light from the left / glow
            base = BASALT_2 if lit else BASALT_1
            c = base
            if d > 0.55 and y > top + 2 * S:  # green lower slopes
                g = (d - 0.55) / 0.45
                c = lerp(base, LUSH if lit else LUSH_DARK, min(1.0, g * 1.6))
            if y > horizon - 3 * S:           # black sand beach
                c = (28, 26, 30)
            if y - top < 1.5 * S:             # rim highlight
                c = lerp(c, BASALT_3 if lit else BASALT_2, 0.8)
            n = rng.randint(-6, 6)
            c = tuple(max(0, min(255, v + n)) for v in c)
            px[x, y] = c + (255,)

    # crater lava pool
    for x in range(peak_x - 8 * S, peak_x + 8 * S):
        top = surface(x)
        if top is None:
            continue
        for y in range(int(top) - 1 * S, int(top) + 2 * S):
            t = abs(x - peak_x) / (8 * S)
            px[x, y] = lerp(LAVA_WHITE, LAVA_ORANGE, t) + (255,)

    # lava river down the right flank into the sea
    pts = []
    for i in range(80):
        t = i / 79
        x = peak_x + 4 * S + t * 44 * S + math.sin(t * 7.0) * 3 * S
        top = surface(x) or horizon
        pts.append((x, top + 1.5 * S + t * 2 * S))
    for w, col in [(int(4.5 * S), LAVA_RED), (int(3 * S), LAVA_ORANGE), (int(1.4 * S), LAVA_YELLOW)]:
        draw = ImageDraw.Draw(img)
        draw.line(pts, fill=col + (255,), width=w, joint="curve")

    # smoke plume drifting right
    smoke = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    sd = ImageDraw.Draw(smoke)
    for i in range(18):
        t = i / 17
        cx = peak_x + t * 46 * S + rng.uniform(-2, 2) * S
        cy = peak_y - 10 * S - t * 26 * S + rng.uniform(-2, 2) * S
        r = (5 + t * 10) * S
        shade = int(80 + t * 60)
        sd.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(shade, shade - 6, shade - 10, int(170 - t * 90)))
    smoke = smoke.filter(ImageFilter.GaussianBlur(2 * S))
    img.alpha_composite(smoke)

    # fountain sparks above the crater (over the smoke) and a hot halo on the crater
    draw = ImageDraw.Draw(img)
    halo = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    hd = ImageDraw.Draw(halo)
    hd.ellipse([peak_x - 12 * S, peak_y - 6 * S, peak_x + 12 * S, peak_y + 8 * S], fill=(255, 170, 60, 140))
    img.alpha_composite(halo.filter(ImageFilter.GaussianBlur(4 * S)))
    draw = ImageDraw.Draw(img)
    for _ in range(40):
        a = rng.uniform(-1.2, 1.2)
        r = rng.uniform(2, 15) * S
        x = peak_x + math.sin(a) * r * 0.7
        y = peak_y - abs(math.cos(a)) * r - 2 * S
        s = rng.choice([S // 2 + 1, S])
        col = rng.choice([LAVA_YELLOW, LAVA_ORANGE, LAVA_WHITE])
        draw.rectangle([x, y, x + s, y + s], fill=col + (255,))

    # steam where the lava meets the sea
    steam = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    st = ImageDraw.Draw(steam)
    ex, ey = pts[-1]
    for i in range(7):
        r = rng.uniform(3, 6) * S
        cx = ex + rng.uniform(-4, 6) * S
        cy = ey - rng.uniform(0, 12) * S
        st.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(235, 235, 240, 150))
    steam = steam.filter(ImageFilter.GaussianBlur(1.5 * S))
    img.alpha_composite(steam)

    # waves and the glow's reflection on the water
    draw = ImageDraw.Draw(img)
    for _ in range(34):
        y = rng.randint(horizon + 3 * S, W - 4 * S)
        x = rng.randint(0, W - 10 * S)
        ln = rng.randint(4, 12) * S
        near = 1 - (y - horizon) / (W - horizon)
        draw.line([(x, y), (x + ln, y)], fill=(120, 180, 220, int(80 + 80 * near)), width=S // 2 + 1)
    for i in range(10):
        y = horizon + (3 + i * 4) * S
        w = (14 - i) * S
        draw.line([(peak_x - w // 2 + rng.randint(-2, 2) * S, y), (peak_x + w // 2, y)],
                  fill=(255, 150, 60, max(30, 170 - i * 15)), width=S)

    img = img.resize((128, 128), Image.LANCZOS)
    # rounded corners + thin dark frame
    mask = Image.new("L", (128, 128), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, 127, 127], radius=14, fill=255)
    framed = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    framed.paste(img, (0, 0), mask)
    ImageDraw.Draw(framed).rounded_rectangle([0, 0, 127, 127], radius=14, outline=(20, 12, 10, 255), width=2)
    return framed


# ----------------------------------------------------------------------------------------------- data files
def advancement_json(name, parent, icon, frame, title_key, desc_key, hidden=False, root=False):
    display = {
        "icon": {"id": icon},
        "title": {"translate": title_key},
        "description": {"translate": desc_key},
        "frame": frame,
        "show_toast": not root,
        "announce_to_chat": not root,
        "hidden": hidden,
    }
    if root:
        display["background"] = f"{NS}:gui/advancements/backgrounds/experience_basalt"
    obj = {}
    if parent:
        obj["parent"] = f"{NS}:experience/{parent}"
    obj["criteria"] = {"granted": {"trigger": "minecraft:impossible"}}
    obj["display"] = display
    obj["requirements"] = [["granted"]]
    return obj


def write_all() -> list[str]:
    written = []

    def save_png(img, rel):
        p = RES / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        img.save(p)
        written.append(str(p.relative_to(ROOT)))

    save_png(mod_icon(), "assets/lavagenesis/icon.png")
    save_png(journal_texture(), "assets/lavagenesis/textures/item/lava_journal.png")
    save_png(basalt_background(), "assets/lavagenesis/textures/gui/advancements/backgrounds/experience_basalt.png")

    write_json(ASSETS / "items" / "lava_journal.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/lava_journal"}})
    write_json(ASSETS / "models" / "item" / "lava_journal.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/lava_journal"}})
    write_json(DATA / "recipe" / "lava_journal.json", {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ["minecraft:book", "#minecraft:coals"],
        "result": {"id": f"{NS}:lava_journal"},
    })

    adv_dir = DATA / "advancement" / "experience"
    adv_dir.mkdir(parents=True, exist_ok=True)
    names = []
    name, parent, icon, frame, title, desc = ROOT_ADV
    write_json(adv_dir / "root.json", advancement_json(name, None, icon, frame,
               f"advancements.{NS}.experience.root.title", f"advancements.{NS}.experience.root.description", root=True))
    names.append("root")
    for name, parent, icon, frame, title, desc in STAGE_ADVANCEMENTS:
        write_json(adv_dir / f"{name}.json", advancement_json(name, parent, icon, frame,
                   f"advancements.{NS}.experience.{name}.title", f"advancements.{NS}.experience.{name}.description"))
        names.append(name)
    for name, parent, icon, frame, title, desc, hidden in FUN_ADVANCEMENTS:
        write_json(adv_dir / f"{name}.json", advancement_json(name, parent, icon, frame,
                   f"advancements.{NS}.experience.{name}.title", f"advancements.{NS}.experience.{name}.description", hidden))
        names.append(name)
    # drop advancement files of ours that are no longer in the table
    for f in adv_dir.glob("*.json"):
        if f.stem not in names:
            print(f"removing stale advancement {f.name}")
            f.unlink()

    write_json(ROOT / "lang_parts" / "experience.json", dict(sorted(LANG.items())))
    return written


# ----------------------------------------------------------------------------------------------- audits
def vanilla_items() -> set[str] | None:
    jar = Path.home() / ".gradle" / "caches" / "fabric-loom" / "26.3" / "minecraft-client.jar"
    if not jar.exists():
        return None
    with zipfile.ZipFile(jar) as z:
        return {"minecraft:" + n[len("assets/minecraft/items/"):-5] for n in z.namelist()
                if n.startswith("assets/minecraft/items/") and n.endswith(".json")}


NARROW = {"i": 2, "!": 2, ".": 2, ",": 2, ":": 2, ";": 2, "'": 2, "|": 2, "l": 3, "`": 3, "t": 4, "I": 4, "[": 4, "]": 4,
          " ": 4, "f": 5, "k": 5, "(": 5, ")": 5, "{": 5, "}": 5, "<": 5, ">": 5, "*": 5, "\"": 5, "@": 7, "~": 7}


def book_lines(text: str, width: int = 114) -> int:
    """Rough number of lines Minecraft's book screen wraps a page into."""
    total = 0
    for para in text.split("\n"):
        line_w, n = 0, 1
        for word in para.split(" "):
            w = sum(NARROW.get(ch, 6) for ch in word)
            add = w if line_w == 0 else w + 4
            if line_w + add > width and line_w > 0:
                n += 1
                line_w = w
            else:
                line_w += add
        total += n
    return total



def audit() -> list[str]:
    problems = []
    java_main = ROOT / "src" / "main" / "java" / "com" / "lavagenesis" / "experience"
    java_client = ROOT / "src" / "client" / "java" / "com" / "lavagenesis" / "experience"
    sources = list(java_main.glob("*.java")) + list(java_client.glob("*.java"))

    # 1. every literal key used as  K + "x"  (with K = "<prefix>." + LavaGenesis.MOD_ID + "<suffix>") exists
    for src in sources:
        text = src.read_text(encoding="utf-8")
        m = re.search(r'String K = "(\w+)\." \+ LavaGenesis\.MOD_ID \+ "([\w.]*)"', text)
        if m:
            prefix = f"{m.group(1)}.{NS}{m.group(2)}"
            for key in re.findall(r'\bK \+ "([\w.]+)"', text):
                full = prefix + key
                if full.endswith("."):
                    continue          # dynamic suffix, checked below
                if full not in LANG:
                    problems.append(f"{src.name}: missing translation {full}")
        for key in re.findall(r'"((?:hud|experience|book|key)\.lavagenesis\.[\w.]+)"', text):
            if not key.endswith(".") and key not in LANG:
                problems.append(f"{src.name}: missing translation {key}")

    # 2. journal prose sections match LavaJournal.SECTIONS
    jtext = (java_main / "LavaJournal.java").read_text(encoding="utf-8")
    for sec, count in re.findall(r'new Section\("(\w+)", (\d+)\)', jtext):
        for n in range(1, int(count) + 1):
            if f"{J}{sec}.{n}" not in LANG:
                problems.append(f"journal: missing page {J}{sec}.{n}")
        if f"{J}{sec}.{int(count) + 1}" in LANG:
            problems.append(f"journal: {J}{sec}.{int(count) + 1} exists but SECTIONS says {count} pages")
    toc = re.search(r'tocIds = List\.of\(([^)]*)\)', jtext)
    if toc:
        for tid in re.findall(r'"(\w+)"', toc.group(1)):
            if f"{J}contents.{tid}" not in LANG:
                problems.append(f"journal: missing {J}contents.{tid}")
    for path in ["magma_shard", "olivine", "peles_tear", "sulfur_crystal", "volcanic_glass", "hotspot_heart"]:
        if f"{J}offering.{path}" not in LANG:
            problems.append(f"journal: missing offering hint for {path}")

    # 3. advancements named in ExperienceAdvancements exist as files
    atext = (java_main / "ExperienceAdvancements.java").read_text(encoding="utf-8")
    for adv in re.findall(r'public static final String \w+ = "(\w+)";', atext):
        if not (DATA / "advancement" / "experience" / f"{adv}.json").exists():
            problems.append(f"advancement file missing: experience/{adv}")
    for stage in ["magma_droplet", "lava_flow", "seamount", "islet", "shield_island", "island_chain", "archipelago", "great_volcano"]:
        if not (DATA / "advancement" / "experience" / f"{stage}.json").exists():
            problems.append(f"advancement file missing: experience/{stage}")

    # 4. models -> textures; advancement icons are real items
    for model in (ASSETS / "models" / "item").glob("lava_journal*.json"):
        for tex in json.loads(model.read_text(encoding="utf-8")).get("textures", {}).values():
            ns, path = tex.split(":", 1)
            if ns == NS and not (ASSETS / "textures" / f"{path}.png").exists():
                problems.append(f"{model.name}: texture {tex} missing")
    items = vanilla_items()
    icons = {ROOT_ADV[2]} | {a[2] for a in STAGE_ADVANCEMENTS} | {a[2] for a in FUN_ADVANCEMENTS}
    if items is None:
        print("note: vanilla jar not found, advancement icons not checked")
    else:
        for icon in sorted(icons):
            if icon.startswith("minecraft:") and icon not in items:
                problems.append(f"advancement icon {icon} is not a vanilla item")
        if "minecraft:book" not in items:
            problems.append("recipe ingredient minecraft:book missing")

    # 4b. prose pages fit on a book page (114 px wide, 14 lines; approximate Minecraft font widths)
    for key, text in LANG.items():
        if not key.startswith(J) or not re.search(r"\.\d+$", key):
            continue
        sec = key[len(J):].rsplit(".", 1)[0]
        extra = 2 if key.endswith(".1") and f"{J}{sec}.header" in LANG else 0
        lines = book_lines(text) + extra
        if lines > 14:
            problems.append(f"journal page {key} is about {lines} lines (max 14)")

    # 5. other modules' translation parts must not define our keys
    for part in (ROOT / "lang_parts").glob("*.json"):
        if part.name == "experience.json":
            continue
        try:
            other = json.loads(part.read_text(encoding="utf-8"))
        except Exception as e:  # noqa: BLE001
            print(f"note: could not read {part.name}: {e}")
            continue
        for k in other:
            if k in LANG:
                problems.append(f"translation key {k} is also defined in {part.name}")
    return problems


def main() -> int:
    written = write_all()
    for w in written:
        print("wrote", w)
    print(f"wrote lang_parts/experience.json ({len(LANG)} keys) and the advancement/model/recipe JSON")
    problems = audit()
    for p in problems:
        print("PROBLEM:", p)
    if not problems:
        print("audit OK")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
