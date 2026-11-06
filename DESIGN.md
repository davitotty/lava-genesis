# Lava Genesis - design and team contract

Minecraft Java **26.3**, Fabric (Loader 0.19.5, Fabric API 0.162.0+26.3, Loom 1.18, JDK 25). Mod id `lavagenesis`, base package `com.lavagenesis`.

## The game

You are not a person who lives near a volcano - **you are the volcano**. A new player is born as a single spark of
lava (`EMBER`, a tiny glowing body at scale 0.1) inside the magma chamber of a **birth volcano**: a small submerged
cone on the ocean floor, built for that player in the nearest deep ocean. At the bottom of the chamber sits the
player's **home Hotspot Vent**, the hotspot that feeds them for the whole game.

Like a real Hawaiian hotspot, you grow in place while your islands spread across the sea:

1. In the dark chamber you melt rock and absorb lava to gather **mass**, and slowly grow.
2. You push out onto the sea floor as a lava flow, piling up pillow lava until you are a **seamount**.
3. You **break the surface**: steam explosions, black sand, your first islet.
4. Flow upon flow you build a broad **shield island**, then leap across the ocean to seed new islands, forming an
   **island chain** and finally an **archipelago**, its old islands turning green with ferns and flowers.
5. At the end your home island rises into a **Great Volcano**: a huge shield volcano with a summit caldera, built
   around you in a big finale. You keep playing afterwards with the strongest powers.

### Stages (numbers live in `core/MagmaStage.java` - lead owns it; ask in your report if you need a change)

| Stage | Mass | Islands | Largest island (land columns) | Offering at home vent | Body scale |
| --- | --- | --- | --- | --- | --- |
| Ember | start | - | - | - | 0.10 |
| Magma Droplet | 60 | - | - | - | 0.18 |
| Lava Flow | 200 | - | - | 8 Magma Shard | 0.30 |
| Seamount | 500 | - | - | 4 Olivine | 0.50 |
| Islet | 900 | 1 | 120 | - | 0.75 |
| Shield Island | 1600 | 1 | 600 | 8 Pele's Tear | 1.00 |
| Island Chain | 2800 | 3 | 600 | 8 Sulfur Crystal | 1.40 |
| Archipelago | 4500 | 5 | 900 | 16 Volcanic Glass | 2.00 |
| Great Volcano | 7000 | 6 | 2500 | 1 Hotspot Heart | 3.00 |

From Seamount on a home vent must be bound (`needsVent`).

### Resources the player tracks (all in `MagmaData`, shown on the HUD)

- **Mass** - how much magma you are; the main growth number. Only added through `MagmaCore.addMass(player, n, reason)`.
  Mass economy (keep to these so the game paces right; a playthrough should take a few hours):
  - melting a stone-like block with the Flare power: +1 (`"melt"`)
  - absorbing a lava source block (sneak in lava): +3 (`"absorb_lava"`); a magma block: +4
  - hotspot upwelling - standing in lava within 12 blocks of your home vent: +1 every 2 s x (stage ordinal + 1) (`"upwelling"`, vent module)
  - eating a Magma Shard: +6 (`"eat"`, land module); other edible volcanic items as land designs them
  - offering at the home vent: +10 per offering item (`"offering"`, vent module)
  - eruption powers: +1 per 4 blocks of new land they create, +1 per block above sea level (`"eruption"`, eruption module)
- **Heat** (0..1) - your life as molten rock. Water and rain cool you (steam!), lava, magma blocks, fire and your
  vent reheat you. Water cools tiny early stages very fast and later stages barely at all (a seamount lives
  underwater). At 0 heat you start to solidify: slowness, then damage. Form module.
- **Pressure** (0..100) - the energy spent by active eruption powers. Refills over time, faster in lava and near
  your vent. Eruption module.
- **Islands / largest island / total land** - measured by the vent module from your vents (below).

### Islands (vent module)

Every Hotspot Vent belongs to one player (the home vent plus any number of island vents). Every few seconds each
loaded vent measures its island: a flood fill over columns starting at the vent, where a column is land when its
top solid (non-fluid) block is at or above sea level (`level.getSeaLevel()`) and is in the block tag
`#lavagenesis:volcanic_land` (`LavaIds.VOLCANIC_LAND`, defined by the land module). Several vents of one player
on the same connected landmass count as one island. The player's `islands`, `largestIsland`, `totalLand` are
summed from their vents (remember results of unloaded vents in a saved per-world record so far islands still count).

## Shared core (written by the lead, compiles - read it, don't edit it)

- `LavaGenesis` - `MOD_ID`, `LOGGER`, `id(path)`; calls each module's `init()` in order core, land, vent, form, eruption, experience.
- `client/LavaGenesisClient` - calls each module's `XxxClient.init()`.
- `core/MagmaStage` - the stage enum above (`minMass`, `minIslands`, `minLargestIsland`, `offering`, `offeringCount`,
  `needsVent`, `scale`, `color`, `next()`, `atLeast()`, `isFinal()`, `isAboveWater()`, `displayName()`, `description()`).
- `core/MagmaData` - per-player record `stage, mass, heat, pressure, homeVent, islands, largestIsland, totalLand, started`
  with `withX(...)` copy methods. Each field has one owning module (see its javadoc).
- `core/MagmaCore` - the `MAGMA` attachment (persistent, copied on death, auto-synced to the owning client);
  `get(Player)` (both sides), `set`, `update(ServerPlayer, UnaryOperator<MagmaData>)`, `addMass(player, n, reason)`,
  events `STAGE_CHANGED (player, from, to)` and `MASS_GAINED (player, amount, total, reason)` (server side).
  If your module needs more per-player state, make your **own** attachment in your package - don't change `MagmaData`.
- `core/VentAccess` - `placeVent(owner, level, pos, home)`: the way the land and eruption modules create vents.
  The vent module installs the implementation with `VentAccess.install(...)` in its init.
- `core/LavaIds` - ids shared between modules + `LavaIds.item(id)` / `LavaIds.block(id)` lookups, and the
  `VOLCANIC_LAND` block tag. Use another module's things **only through these ids, looked up at runtime** (never
  in a static initialiser), so every module works alone. If you need a new shared id, define it in your own
  module and list it in your report.
- `core/ModTab` - one creative tab that lists every `lavagenesis` item automatically. Don't make another tab.

## Modules (one agent per module - edit only your own files)

| Module | Java (main) | Java (client) | Owns |
| --- | --- | --- | --- |
| **land** | `com.lavagenesis.land.*` | `com.lavagenesis.land.client.*` | the volcanic blocks and items (cooling lava, molten rock, fresh basalt, black sand, volcanic soil, lush volcanic soil, olivine ore, fumaroles, island plants...), the offering items and how each is obtained, their recipes/loot/tags (incl. `volcanic_land`), lava cooling and plant succession, worldgen, the **birth volcano** built for each new player, and the `/lavagenesis` admin/debug command |
| **vent** | `com.lavagenesis.vent.*` | `com.lavagenesis.vent.client.*` | the Hotspot Vent block, block entity, item and renderer; vent ownership and `VentAccess` implementation; island measuring; offerings, stage advancement and requirement feedback; hotspot upwelling; respawning at the home vent; the **Great Volcano finale** (the home island rising into a giant shield volcano) |
| **form** | `com.lavagenesis.form.*` | `com.lavagenesis.form.client.*` | the player's body: size per stage (scale attribute), health/speed/reach/step per stage, fire and lava immunity, swimming in lava, heat and solidifying, absorbing lava, no hunger, per-stage passive powers (e.g. lava-walking over water that leaves cooling rock), body visuals (glow, drips, steam), lava-vision |
| **eruption** | `com.lavagenesis.eruption.*` | `com.lavagenesis.eruption.client.*` | pressure, all **active powers** on keybinds and their network payloads, power unlocks per stage, the land-building effects (cones, flows, lava bombs, ash), seeding new islands with island vents |
| **experience** | `com.lavagenesis.experience.*` | `com.lavagenesis.experience.client.*` | HUD, stage-up celebration, advancements, the Lava Journal guide book, mod icon, README, sounds/music cues, and the client game test + play-test world in `src/gametest` |

### Powers (eruption module - suggested set, refine freely; keybinds in a "Lava Genesis" category)

| Unlocked at | Power | Idea |
| --- | --- | --- |
| Ember | **Flare** | melt the stone-like block you look at into lava (+1 mass); how an ember carves its chamber |
| Magma Droplet | **Lava Spit** | lob a glob of lava that splashes into cooling lava where it lands; hurts and ignites mobs |
| Lava Flow | **Flow Surge** | dash forward as a river of fire, leaving a trail of cooling lava |
| Seamount | **Pillow Mound** | heap a mound of pillow lava on the sea floor in front of you - raises the seamount |
| Islet | **Eruption** | a fountain at the target: builds a cone of cooling lava, flings lava bombs, ash and steam |
| Shield Island | **Shield Flow** | a wide, flat lava flow that spreads over shallow sea and fills it to just above sea level |
| Shield Island | **Hotspot Leap** | arc 40-80 blocks over the ocean and erupt a seed island there with a new island vent |
| Archipelago | **Bomb Rain** | volcanic bombs over a wide area: many small patches of land, damage to mobs |
| Great Volcano | **Great Eruption** | ash column, lightning in the ash cloud, a huge cone of new rock |

### Files outside Java follow the same split

- **Translations**: write yours to `lang_parts/<module>.json` (flat JSON object). The build merges every part into
  `en_us.json`; a key in two parts fails the build. Never create `assets/lavagenesis/lang/en_us.json` yourself.
  `lang_parts/core.json` (stage names, creative tab) belongs to the lead.
- **Assets/data**: name every file after the thing you own (`textures/block/cooling_lava_0.png`,
  `blockstates/hotspot_vent.json`, `data/lavagenesis/recipe/hotspot_heart.json`, ...). Put advancements, worldgen and
  sounds in subfolders named for your module where the format allows (`data/lavagenesis/advancement/experience/...`).
- **Textures**: make them with your own Python script `tools/gen_<module>.py` (Pillow is installed; run with `python`).
  Hand-drawn-looking 16x16 pixel art in one palette: glowing lava (white-yellow core, orange, deep red), cooling crust
  (dark red-brown with orange cracks), basalt (near-black blue-greys), black sand, olivine green, sulfur yellow,
  volcanic glass (glossy black with a violet sheen), lush green, ash grey. Have each script check that every model,
  texture and translation it names exists - 26.3 shows a placeholder for a missing texture without logging anything.
- `src/gametest/**` belongs to experience. `build.gradle`, `gradle.properties`, `fabric.mod.json`, `core/*`,
  `lang_parts/core.json`, `DESIGN.md` and the two entrypoint classes belong to the lead. Your `XxxModule` /
  `XxxClient` stub is yours: fill it in.

## Rules for every agent

1. **Only references: the `fabric-26-3-modding` skill** (load it with the Skill tool first) and the real game jars. Its
   `references/*.md` are the API and data-format guides; `scripts/api.sh <fully.qualified.Class> [regex]` prints real
   26.3 signatures (set `JAVAP` to a JDK 25 `javap`, and for Fabric API classes
   `FABRIC_JAR=<the jar under ~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/...>`), and
   `scripts/vanilla.sh` extracts vanilla data/asset files to copy their shape. **Do not read other mods**: not the
   other mod projects in `~/Documents` (ignore the skill's "worked examples" section), not mod sources online.
   Write everything yourself.
2. Build with bash from the project root: `./gradlew build -x test --console=plain`. Others are editing at the same
   time, so a compile error in a file you don't own is not yours: carry on with your own work and build again
   later; never edit another module's files. If Gradle reports a lock timeout, run it again.
3. Do not launch the game (`runClient`, `runClientGameTest`) - the lead does that at the end.
4. Never delete or overwrite files you did not create. Stay inside the project folder. Don't run git commands that
   change history or the working tree (no commit, reset, checkout, stash, clean); the lead commits.
5. Server-side logic only on the server; keep per-tick work cheap (spread big block edits over many ticks, never
   scan huge areas in one tick).
6. Finish with a short report: what you built, files you own, ids/APIs others can use, what compiles, anything unfinished.
