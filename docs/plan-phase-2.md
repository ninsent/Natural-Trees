# Plan — Phase 2: Oak and birch world generation

Spec section 18, Phase 2: the built-in datapack with the keys of 11.2 and the placed-feature counts; the
measurement table. *Exit:* section 20 item 4 confirmed; table filled; density set from it; results inside the
working ceiling, or the ceiling consciously revised.

**Status: approved 2026-09-19, in progress.** Done: T1–T6 and T8 (first version). Waiting for the human: P2-1 and
P2-2, then the measurements P2-3 to P2-5 (`docs/measurements.md`). Then T7 (final counts from the table).
Changes from the plan as written: `trees_plains` and `trees_meadow` are as sparse in vanilla as they should be
(0–1 trees per chunk; one chunk in 100), so the pack ships five placed features, not seven; NeoForge registers the
pack with `PackSource.BUILT_IN`, the source that adds a pack to new worlds automatically.

Phase 1 is not formally closed: world generation works in game (the human's screenshot, 2026-09-19), but the
results of P1-7 (leaves persist), P1-8 (no far-chunk writes) and P1-5 (repeatable seeds), and the name
reservation on Modrinth and CurseForge, are still owed. Phase 2's measurements run the same pre-generation as
P1-8, so P1-8 is folded into test P2-3 below; P1-7 stays a Phase 1 debt and is listed again here.

## Exit criteria

| Criterion | How it is met |
|---|---|
| Section 20 item 4 confirmed | Done: `docs/assumptions.md`, "Item 4 in full". The 15 keys of 11.2 are exactly vanilla's, and all are reachable. |
| Measurement table filled | The human runs P2-3 to P2-5 on one reference machine; the agent writes `docs/measurements.md` from the numbers. |
| Density set from it | T5, then a second pass after the table. |
| Inside the working ceiling (15 % chunk time on the forest region, 10 % mean frame time), or the ceiling consciously revised | Judged from the table. Above the ceiling: lower `count`, then `smother`, then `max_leaves`, then `max_tips` (spec 15). |

## Decisions taken while planning (delegated; recorded in `questions.md` as Q19–Q21)

| # | Gap | Decision |
|---|---|---|
| Q19 | Spec 11.3 says the pack supplies "the placed features that biomes reference", changing only `count`. Seven are purely oak and birch; eleven more mix oak with species that stay vanilla until Phase 3 (item 4). | Phase 2 retunes only the seven pure ones: `trees_birch_and_oak`, `trees_birch`, `birch_tall`, `trees_flower_forest`, `trees_plains`, `trees_meadow`, `trees_badlands`. The mixed ones keep vanilla's count until their other species arrive in Phase 3; thinning them now would thin vanilla spruce, jungle and acacia too. |
| Q20 | Where the pack lives and how it is registered (spec 11.1: optional, enabled by default). | One folder in `common`'s resources, `resourcepacks/naturaltrees_worldgen`, which is the path Fabric requires. Fabric: `ResourceManagerHelper.registerBuiltinResourcePack(…, DEFAULT_ENABLED)`. NeoForge: `AddPackFindersEvent.addPackFinders(…, PackType.SERVER_DATA, …, PackSource.DEFAULT, false, Pack.Position.TOP)`. Disabling it returns generation to vanilla and leaves blocks, recipes and commands. |
| Q21 | The 15 files are vanilla's files with two objects swapped; they must not be edited by hand, or they drift from vanilla and from the viewer's species. | They are generated: `tools/assetgen` reads vanilla's JSON from the ModDevGradle artifact and the species from `tools/viewer`, and its output is checked in. The Phase 1 test datapack under `docs/` is deleted when the built-in pack works. |

## Tasks

| # | Task | Spec | Acceptance check |
|---|---|---|---|
| T1 | **Item 4.** | 20 | Done (see above). |
| T2 | **Pack generator** (Q21): 15 configured features, identical within a family except for decorators, as vanilla's are; the seven placed features of Q19 with only `count` changed. A unit test compares each generated file with vanilla's: equal except `trunk_placer`, `foliage_placer`, or `count`. | 10, 11.2, 11.3 | The test passes; every file loads through the game's codecs in a `common` test. |
| T3 | **Pack registration** on both loaders (Q20), with a translated name and description. | 11.1 | P2-1. |
| T4 | **`/naturaltrees stats <configured_feature> [seeds]`**: height, crown radius, trunk width, wood voxels by kind, tips, truncated stems, leaves, and leaves discarded by self-shading, reach and budget, averaged over the seeds (default 1,000). It runs the generator at the player's position without writing blocks. | 16 | P2-2; the averages agree with the viewer's stats panel for the same species. |
| T5 | **Starting density**: counts of about a third of vanilla's for the seven placed features (3–4 trees per chunk where vanilla places about 10), keeping each weighted list's shape. | 11.3 | P2-1: forests read as forests, with light reaching the floor in places. |
| T6 | **Measurement kit**: three density packs under `docs/measurement-packs/` (1×, 1.5×, 2× the candidate counts) that override the built-in one, a fixed seed and region, a scripted flight path, and `docs/measurements.md` with the table of spec 15 to fill in. | 15 | The human can run P2-3 to P2-5 from the document alone. |
| T7 | **Second pass**: set the final counts from the table; if over the ceiling, apply spec 15's order of remedies and measure again. Publishable figures go into the README. | 15 | Exit criteria above. |
| T8 | **User documentation** in the README: what the pack replaces, how to disable it, and that another pack supplying the same vanilla keys wins or loses by pack order (spec 19). | 11.1, 19 | Reviewed by the human. |

Order: T2 → T3 → T5 → (P2-1) → T4 → (P2-2) → T6 → (P2-3 to P2-5) → T7 → T8.

## Manual tests the human will be asked to run

```
P1-7 (owed from Phase 1) — leaves persist: saplings and generated trees at randomTickSpeed 300 for two minutes;
      no leaf decays; a cut limb's leaves do.
P2-1 — the built-in pack            new world without any extra datapack: forests, plains, birch forests and a plains
                                    village grow the mod's trees; /datapack list shows the pack enabled; after
                                    /datapack disable, newly generated chunks have vanilla trees again.
P2-2 — stats                        /naturaltrees stats minecraft:fancy_oak 1000 in the open and again beside a cliff:
                                    open-ground numbers match the viewer; the cliff shows truncated stems.
P2-3 — chunk time (also P1-8)       Fabric, Chunky, fixed seed, forest-only region and mixed region, with spark and
                                    misode's worldgen-profiling; vanilla first, then the mod at 1×, 1.5×, 2×.
                                    Also search the log for "Detected setBlock in a far chunk".
P2-4 — block counts                 /naturaltrees stats for each of the four species, 1,000 seeds.
P2-5 — frame time                   the scripted forest flight at fixed render distance, vanilla renderer and Sodium,
                                    vanilla versus the mod at 1×, 1.5×, 2×: mean and 1 % low.
```

P2-3 and P2-5 need third-party tools in the run folder (Chunky, spark, worldgen-profiling, Sodium). They are test
tools only; none becomes a dependency.

## Out of scope for this phase

The other overworld woods (Phase 3), felling (Phase 4), anything in spec section 22; in particular crown-aware
spacing (22, item 14), however tempting the dense canopy makes it.
