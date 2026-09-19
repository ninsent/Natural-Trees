# Measurements (spec section 15)

One table, one reference machine. **Working ceiling:** 15 % more total chunk time on the forest region and 10 % more
mean frame time than vanilla. Above it: lower `count` first, then `smother`, then `max_leaves`, then `max_tips`, and
measure again. The figures are published in the mod description.

Status: **not measured yet.** The candidate density is a third of vanilla's count (3–4 trees per chunk where vanilla
places 10–11), set in `tools/assetgen` (`WorldgenPackGen.DENSITY`).

## Reference machine

Fill in once: CPU, RAM given to the game (`-Xmx`), GPU, OS, Java version, Minecraft 1.21.1, loader and its version,
render distance, simulation distance, graphics setting, window size.

## What is compared

| Label | Setup |
|---|---|
| vanilla | the mod installed, the built-in pack **disabled** (`/datapack disable` before generating, or untick it on the Create World screen) |
| 1× | the mod with its built-in pack (the candidate density) |
| 1.5× | the same plus `docs/measurement-packs/density-1.5x` above it in the pack list |
| 2× | the same plus `docs/measurement-packs/density-2x` |

The density packs change only the `count` of the five oak and birch placed features, like the built-in pack.
Always generate a **fresh world** per row: a datapack only affects chunks generated after it is enabled.

## Regions and seed

- Seed: pick one and write it here; use it for every row.
- **Forest region:** a 250-block radius centred in a large Forest or Birch Forest biome (find one with `/locate biome`).
- **Mixed region:** a 250-block radius around world spawn.

## Procedure

Tools, in `fabric/runs/client/mods` for the test only: Chunky (pre-generation), spark (profiling), and misode's
worldgen-profiling datapack or mod; Sodium for the second renderer row. None is a dependency of the mod.

1. **Chunk time per generation stage.** New world with the row's packs. `/chunky center <x> <z>`, `/chunky radius 250`,
   `/spark profiler start`, `/chunky start`; when Chunky finishes, `/spark profiler stop`. Write down Chunky's total
   time and chunks per second, and from the profiler the share of `FEATURES` and of `LIGHT`. Do it for the forest
   region and the mixed region.
   Then search `logs/latest.log` for `Detected setBlock in a far chunk` and for `naturaltrees`: both must find
   nothing at WARN or ERROR (this is also Phase 1's test P1-8).
2. **Block counts per species.** Stand on open flat ground and run
   `/naturaltrees stats minecraft:oak 1000`, then `fancy_oak`, `birch`, `super_birch_bees`. Copy the chat output.
3. **Frame time.** In the pre-generated forest region: creative, fly at a fixed height along a fixed line
   (for example from `<x> 100 <z>` due east for 400 blocks at flying speed, F3 off), recording with spark's
   `/sparkc profiler` or the F3 frame graph export. Note the mean and the 1 % low frame time. Once with the vanilla
   renderer, once with Sodium.

## Table

Chunk time, forest region / mixed region:

| Row | Total time | Chunks per second | FEATURES share | LIGHT share | Difference to vanilla |
|---|---|---|---|---|---|
| vanilla | | | | | — |
| 1× | | | | | |
| 1.5× | | | | | |
| 2× | | | | | |

Block counts per tree (from `/naturaltrees stats`, 1,000 seeds):

| Species | Height | Crown radius | Trunk width | Logs | Branch blocks | Tips | Truncated | Leaves | Lost to shade / reach / budget |
|---|---|---|---|---|---|---|---|---|---|
| oak | | | | | | | | | |
| fancy_oak | | | | | | | | | |
| birch | | | | | | | | | |
| super_birch_bees | | | | | | | | | |

Frame time on the forest flight, mean / 1 % low, in milliseconds:

| Row | Vanilla renderer | Sodium | Difference to vanilla (mean) |
|---|---|---|---|
| vanilla | | | — |
| 1× | | | |
| 1.5× | | | |
| 2× | | | |

## Conclusion

To be written from the table: the chosen density, whether it is inside the ceiling, and what was changed if not.
