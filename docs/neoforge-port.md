# The NeoForge 1.21.1 port

Electrical Age was written for Forge 1.7.10. This document explains how the 1.21.1 version is put together, and what
changed for players and modpack authors.

## For players

- **Worlds from 1.7.10 cannot be opened.** Minecraft itself cannot carry blocks across, and neither can the mod.
- Needs NeoForge 21.1 and [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge) 5.
- The mod id is now `eln`, in lower case. Every component is an item of its own (`eln:240v_macerator`), so `/give`,
  JEI/EMI, recipes and data packs can refer to each one. The old numeric ids live on inside the mod and in saved nodes;
  see [id-mappings.md](id-mappings.md).
- Settings are still in `config/Eln.cfg`.

### Integrations

| 1.7.10 | 1.21.1 |
| --- | --- |
| Waila | Jade |
| ComputerCraft | CC: Tweaked |
| RF (CoFH) | NeoForge energy (FE), through the Eln exporter |
| Ore dictionary | Item tags: `ingotCopper` is `c:ingots/copper`, `plateIron` is `c:plates/iron` and so on |
| IC2, OpenComputers, HBM | Removed, there is nothing to integrate with |

Steam machines need another mod that provides steam, as before.

### Recipes

Recipes depend on the configuration and on which materials other mods bring, so they are built in code when data packs
are (re)loaded. They behave like ordinary recipes: a data pack can replace one by providing a recipe with the same id,
for example `data/eln/recipe/crafting/wrench.json`.

## For developers

### Commands

| Command | What it does |
| --- | --- |
| `./gradlew build` | Compiles, runs the unit tests, and writes the mod to `build/libs` |
| `./gradlew runClient`, `./gradlew runServer` | Starts the game |
| `./gradlew runData` | Regenerates models, block states, loot and tags in `src/generated/resources` |
| `./gradlew runGameTestServer` | Places, saves and loads every component, checks recipes and that power flows |
| `./gradlew runClientSmokeTest` | Builds a world with everything, opens every GUI, takes screenshots into `run/screenshots`, reloads the world and quits |
| `./gradlew generateLangFiles` | Updates the language files |

Two of them take a system property through `JAVA_TOOL_OPTIONS`:

- `-Deln.stress=200` makes `runGameTestServer` also build 200 circuits and every component, and report what a tick costs.
- `-Deln.clientSmokeTest.fps=true` makes `runClientSmokeTest` only measure the frame rate, with everything in view.
  `-Deln.clientSmokeTest.noGuis=true` leaves out the GUIs, `-Deln.clientSmokeTest.ores=true` only counts ores.

Run `runData` after adding a component, so that it gets an item model.

### Releases

Set `mod_version` in `gradle.properties`, describe the version in [changelog.md](changelog.md) and push a tag `v<mod_version>`.
The Build workflow then publishes the release, as a pre-release if the version has a `-` in it. The update check of the
mod looks at these releases.

### Layout

- `mods.eln.compat` keeps the vocabulary most of the code is written in, on top of the current API: `Level.getBlock(x, y, z)`,
  `ItemStack.tagCompound` (a copy; use `updateTag { }` to change it), `IInventory` with `null` for empty slots, `OreDict`
  over tags.
- `mods.eln.compat.client.GL11` is an immediate mode drawing API with the calls of the old OpenGL, drawing through
  Minecraft's buffers. It only works within `GL11.inWorld` and `GL11.inGui`; anything else is ignored. Display lists
  record meshes. Big meshes that stay the same for a few frames are kept on the graphics card (`GlBake`).
- `mods.eln.registration.ElnRegistry` collects what the mod registers. `Eln.createContent()` runs once, when the first
  registry opens. Do not call `Block.asItem()` or make stacks from blocks before that is over.
- A descriptor (`SixNodeDescriptor`, `TransparentNodeDescriptor`, `GenericItemUsingDamageDescriptor`) is one item. Six
  nodes share one block, as do transparent nodes; what is in the block is the node's business.
- Node state travels in one payload, `packets.ElnPayload`, and in the update tag of the block entity (`node.NodeSync`).
- Nodes are saved in `data/electricalAgeWorld<dimension>.dat` next to the dimension's other data.
- All container GUIs share one menu type (`GuiHandler`), and the screen of a node is found through the node.
- File names under `assets/eln` are lower case, with `_` for spaces. Code may still use the old spelling, `rl()` converts it.
