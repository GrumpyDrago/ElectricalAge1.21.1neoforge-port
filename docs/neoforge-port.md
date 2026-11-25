# The NeoForge 1.21.1 port

Electrical Age was written for Forge 1.7.10. This document explains how the 1.21.1 version is put together, and what
changed for players and modpack authors.

## For players

- **Worlds from 1.7.10 cannot be opened.** Minecraft itself cannot carry blocks across, and neither can the mod.
- Needs NeoForge 21.1 and [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge) 5.
- The mod id is now `eln`, in lower case. Every component is an item of its own (`eln:240v_macerator`), so `/give`,
  JEI/EMI, recipes and data packs can refer to each one. The old numeric ids live on inside the mod and in saved nodes;
  see [id-mappings.md](id-mappings.md).
- Settings are in `config/eln/eln-1.24.json`, fuels in `config/eln/fluids.json`. A `config/Eln.cfg` of 1.7.10 is taken over.

### Integrations

| 1.7.10 | 1.21.1 |
| --- | --- |
| Waila | Jade |
| (in-game wiki only) | JEI: what the machines make of what, as JEI shows crafting recipes itself |
| ComputerCraft | CC: Tweaked |
| RF (CoFH) | NeoForge energy (FE), through the Eln exporter |
| Ore dictionary | Item tags: `ingotCopper` is `c:ingots/copper`, `plateIron` is `c:plates/iron` and so on |
| IC2, OpenComputers, HBM | Removed, there is nothing to integrate with |

Steam machines need another mod that provides steam, as before.

### Recipes

Recipes depend on the configuration and on which materials other mods bring, so they are built in code when data packs
are (re)loaded. They behave like ordinary recipes: a data pack can replace one by providing a recipe with the same id,
for example `data/eln/recipe/crafting/wrench.json`.

Machines make the same of an item as of others in a tag like `c:ores/iron`, `c:ingots/copper`, `c:dusts/lead`,
`c:sands/colorless` or `minecraft:logs`; see `OreDict.isMaterial` for which tags count.

### Fuels

Fuels are in `config/eln/fluids.json`, by name. A fluid is a fuel if an entry has its id (`somemod:juice`), else its name,
else the name of one of its `c:` tags; spelling and some common other names do not matter (`FuelNames`). Entries of
`diesel` with `engine` set to true also work in engines and turbines. Mekanism's chemicals are not fluids, so its gases
have to go through a rotary condensentrator first.

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
  With `-Deln.clientSmokeTest.jei=true` it looks up the recipes of the machines in JEI, which has to be in `run/mods`.
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
- The icon of an item is an ordinary model with the textures its descriptor draws; `runData` finds out which those are (`FlatIcon`).
  What the descriptor draws on top, such as text, becomes a decoration. Anything else is drawn by `ElnItemRenderer`.
- `mods.eln.registration.ElnRegistry` collects what the mod registers. `Eln.createContent()` runs once, when the first
  registry opens. Do not call `Block.asItem()` or make stacks from blocks before that is over.
- What machines make of what is in the `RecipesList`s of `Eln`. `MachineRecipes` has it all in one form, for the JEI plugin.
- A descriptor (`SixNodeDescriptor`, `TransparentNodeDescriptor`, `GenericItemUsingDamageDescriptor`) is one item. Six
  nodes share one block, as do transparent nodes; what is in the block is the node's business.
- Node state travels in one payload, `packets.ElnPayload`, and in the update tag of the block entity (`node.NodeSync`).
- Nodes are saved in `data/electricalAgeWorld<dimension>.dat` next to the dimension's other data.
- All container GUIs share one menu type (`GuiHandler`), and the screen of a node is found through the node.
- File names under `assets/eln` are lower case, with `_` for spaces. Code may still use the old spelling, `rl()` converts it.
