# Lava Genesis

A Fabric mod for **Minecraft Java 26.3**: you are not someone who lives near a volcano, **you are the volcano**.

You are born as a single spark of lava in the magma chamber of a small undersea volcano. You melt rock and drink lava
to gather mass, push out onto the sea floor as a lava flow, pile up pillow lava into a seamount, break the surface in
clouds of steam, build a broad shield island, leap across the ocean to seed an island chain and an archipelago, and
finally rise into a **Great Volcano**: a huge shield volcano with a summit caldera, built around you in a big finale.

## How to play

When you join a world, the mod builds a **birth volcano** for you in the nearest deep ocean and places you inside it,
next to your **home Hotspot Vent**. A **Lava Journal** appears in your pack after a few seconds: use it to read the
whole guide in game (it has a clickable table of contents).

The panel in the top-left corner shows your **stage**, **mass** toward the next stage, **heat**, **pressure**, your
**islands** and a checklist of what the next stage still needs. Press **H** to switch it between full, compact and
hidden.

### Resources

- **Mass** - how much magma you are; every stage needs more. Melt stone with Flare (+1), sneak in lava to drink a
  source (+3), swallow a magma block (+4), eat a Magma Shard (+6), rest in lava near your home vent (the hotspot wells
  up into you, more every stage), give offerings at your vent (+10 each), and raise new land with eruptions.
- **Heat** - your life. Water and rain cool you (a tiny ember very fast, a seamount hardly at all); lava, magma blocks,
  fire and your vent warm you. At zero heat you start to set like stone.
- **Pressure** (0-100) - spent by your powers, refills over time, faster in lava and near your vent.
- **Islands** - your vents measure the land around them: every column whose top block is volcanic and at or above sea
  level. Several of your vents on one landmass count as one island.

### Stages

Stages advance by themselves (checked every 2 seconds) as soon as everything is met. Offerings are given by using the
item on your **home vent**.

| # | Stage | Mass | Islands | Largest island | Offering | Home vent | Body size |
| - | --- | ---: | ---: | ---: | --- | :---: | ---: |
| 1 | Ember | start | - | - | - | - | 10% |
| 2 | Magma Droplet | 60 | - | - | - | - | 18% |
| 3 | Lava Flow | 200 | - | - | 8 Magma Shard | - | 30% |
| 4 | Seamount | 500 | - | - | 4 Olivine | yes | 50% |
| 5 | Islet | 900 | 1 | 120 | - | yes | 75% |
| 6 | Shield Island | 1,600 | 1 | 600 | 8 Pele's Tear | yes | 100% |
| 7 | Island Chain | 2,800 | 3 | 600 | 8 Sulfur Crystal | yes | 140% |
| 8 | Archipelago | 4,500 | 5 | 900 | 16 Volcanic Glass | yes | 200% |
| 9 | Great Volcano | 7,000 | 6 | 2,500 | 1 Hotspot Heart | yes | 300% |

### Where the offerings come from

| Offering | Where |
| --- | --- |
| Magma Shard | break Molten Rock in your chamber, or lava that has only just begun to cool (also edible: +6 mass) |
| Olivine | Olivine Ore in your volcano's walls and on the sea floor; lava cooling deep underwater sometimes turns to olivine |
| Pele's Tear | sometimes flung out when your lava cools in the open air above the sea |
| Sulfur Crystal | Sulfur Clusters growing on Fumaroles (fresh land under open sky); harvest when fully grown |
| Volcanic Glass | break Glassy Basalt, made where lava is quenched by the sea or the waves |
| Hotspot Heart | craft: 2 Pele's Tear, 2 Volcanic Glass, 2 Sulfur Crystal, 2 Olivine around a magma block |

### Powers and gifts

Each stage unlocks an active **power** (pressure cost / cooldown) and a passive **gift** for your body.

| Stage | Power | Cost | Cooldown | Gift |
| --- | --- | ---: | ---: | --- |
| Ember | **Flare** - melt the rock you look at into lava | 1 | 0.25 s | Spark: immune to fire and lava |
| Magma Droplet | **Lava Spit** - lob a glob of lava that splashes into cooling rock | 6 | 0.8 s | Lava Dart: shoot through lava, dart through water |
| Lava Flow | **Flow Surge** - dash forward as a river of fire | 18 | 3.5 s | Glowing Trail: water cools you far more slowly |
| Seamount | **Pillow Mound** - heap pillow lava on the sea floor | 28 | 5.5 s | Deep Fire: breathe and swim underwater |
| Islet | **Eruption** - a lava fountain that builds a cone and flings bombs | 40 | 9 s | Lava-Walking: water under your steps turns to cooling lava |
| Shield Island | **Shield Flow** - a wide flat flow that fills shallow sea<br>**Hotspot Leap** - arc 40-80 blocks and raise a seed island with a new vent (70 pressure, 30 s) | 50 | 12 s | Fire Aura: attackers burst into flames |
| Island Chain | - | | | Steam Jump: burst out of the sea on steam |
| Archipelago | **Bomb Rain** - volcanic bombs over a wide area | 60 | 16 s | Lava Sight: see in the dark and through lava |
| Great Volcano | **Great Eruption** - ash column, lightning, a huge new cone | 100 | 60 s | Ash Plume: fly (double-tap jump) |

### Controls (Options > Controls > Key Binds > Lava Genesis)

| Key | Action |
| --- | --- |
| **R** | use the selected power (Flare and Lava Spit repeat while held) |
| **V** | switch power (sneak + V: back) |
| **G** | Flare, whatever power is selected |
| **H** | lava panel: full / compact / hidden |

### Feedback and advancements

Growing into a stage plays a title, a stage-specific sound mix and a particle burst sized to your new body, and posts
a chat summary of the new power, the new gift and what the next stage asks for. The Great Volcano gets a long finale:
an ash column with lightning, lava fountains, fireworks of lava and a journey summary. Small chimes and action-bar
lines mark every quarter of the way to the next stage's mass.

The **Lava Genesis** advancement tab has one advancement per stage plus extras (first melt, first sip of lava, first
offering, land from fire, first / three / six islands, a 1,000-column island, 5,000 columns of land, nearly freezing
solid, and a hidden one for 10,000 mass).

Lost the journal? Craft a new one from a **book** and a piece of **coal or charcoal**.

## Install

1. Install Fabric Loader 0.19.5+ for Minecraft 26.3 and **Fabric API** 0.162.0+26.3.
2. Put `lavagenesis-1.0.0.jar` (from `build/libs/`) into your `mods` folder.
3. Start a new world. Java 25 is required.

## Build and test

Requires JDK 25 (`C:\Program Files\Java\jdk-25.0.5.0.2`). From the project root, in bash:

```bash
./gradlew build -x test --console=plain          # the mod jar -> build/libs/lavagenesis-1.0.0.jar
./gradlew gametestClasses --console=plain        # compile the client game test

# the real-game client test: screenshots of every stage, HUD, journal, finale; optionally a play-test world
./gradlew runClientGameTest -Plavagenesis.shots=run/screenshots -Plavagenesis.makeworld=run/saves/LavaGenesis

# play the saved play-test world directly
./gradlew runClient -Plavagenesis.world=LavaGenesis
```

Relative `lavagenesis.shots` / `lavagenesis.makeworld` paths are resolved against the project root. The test never
overwrites an existing world folder; it saves to `LavaGenesis-2`, `-3`, ... instead and logs the path.

Debug command (operators): `/lavagenesis info | stage set <stage> | mass add|set <n> | heat set <0..1> |
pressure set <0..100> | rebuild | tp home`, each with an optional player argument.

Assets are generated by one Python script per module (`tools/gen_<module>.py`, Pillow): run e.g.
`python tools/gen_experience.py` to redraw the mod icon, the journal texture and the advancement files and to audit
the experience module's translations. Translations live in `lang_parts/<module>.json` and are merged into `en_us.json`
by the build.

## Project layout

The mod is split into modules (see `DESIGN.md`): `core` (shared player state `MagmaData`, stages, ids), `land`
(volcanic blocks, items, birth volcano, worldgen, command), `vent` (Hotspot Vents, islands, offerings, stage
advancement, Great Volcano finale), `form` (the player's body, heat, passives), `eruption` (pressure and powers) and
`experience` (HUD, celebrations, advancements, Lava Journal, icon, client game test).

License: MIT.
