"""Eruption module asset audit.

The eruption module draws everything with vanilla items and particles, so it generates no textures. This script
checks that every translation key the module's Java code uses exists in lang_parts/eruption.json (26.3 shows raw
keys without logging anything), and that the data files it ships parse.

Run from the project root:  python tools/gen_eruption.py
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = [ROOT / "src/main/java/com/lavagenesis/eruption", ROOT / "src/client/java/com/lavagenesis/eruption"]
LANG = ROOT / "lang_parts/eruption.json"
DATA = [ROOT / "src/main/resources/data/lavagenesis/tags/block/flare_meltable.json"]


def main() -> int:
    lang = json.loads(LANG.read_text(encoding="utf-8"))
    sources = "\n".join(p.read_text(encoding="utf-8") for d in JAVA for p in d.rglob("*.java"))
    need = set()

    # powers: the enum lines look like  FLARE("flare", MagmaStage.EMBER, ...
    power_src = (ROOT / "src/main/java/com/lavagenesis/eruption/Power.java").read_text(encoding="utf-8")
    for pid in re.findall(r'^\s*[A-Z_]+\("([a-z_]+)",', power_src, re.M):
        need.add(f"power.lavagenesis.{pid}")
        need.add(f"power.lavagenesis.{pid}.desc")
    # msg("x.y") helper and literal keys
    for key in re.findall(r'msg\("([a-z_.]+)"', sources):
        need.add(f"msg.lavagenesis.{key}")
    for key in re.findall(r'"((?:msg|key)\.lavagenesis\.[a-z_.]+)"', sources):
        need.add(key)
    # leap failure keys chosen at runtime
    for key in re.findall(r'fail = "([a-z_.]+)"', sources):
        need.add(f"msg.lavagenesis.{key}")
    need.add("key.category.lavagenesis.main")
    need.add("entity.lavagenesis.lava_bomb")

    missing = sorted(k for k in need if k not in lang)
    unused = sorted(k for k in lang if k not in need)
    for f in DATA:
        json.loads(f.read_text(encoding="utf-8"))
    if missing:
        print("MISSING translations:", *missing, sep="\n  ")
    if unused:
        print("unused translations (ok if used elsewhere):", *unused, sep="\n  ")
    print(f"checked {len(need)} keys, {len(DATA)} data files")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
