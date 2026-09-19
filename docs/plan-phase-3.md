# Plan — Phase 3: Remaining overworld woods

Spec section 18, Phase 3: spruce and pine as conifer species; acacia; cherry; jungle; swamp oak; mangrove with its
vanilla root placer and decorators; dark oak and the mega species from 2×2 saplings with `trunk_width_min` 2.
The spec gives this phase no exit criterion of its own; the ones below are Phase 0's and Phase 2's applied again.

**Status: approved 2026-09-19, in progress.** Done: T1, T2 (first drafts of all twelve species files), T4 (the pack
now holds 29 tree features and 18 placed features). T3: no generator gap was found; every species was reached by
data. Waiting for the human: P3-1 to P3-4. Then T6 (retuning from their feedback) and T7 (measurement).
`trees_savanna` (1–2 trees per chunk) is left to vanilla: a third of its count is no fewer trees.

Phase 2 is not closed: the measurement table (`docs/measurements.md`) and the final oak and birch density wait for
the human, as do P2-1, P2-2 and the Phase 1 debts (P1-7, P1-5, the name reservation). Nothing in Phase 3 depends on
them: species are data, and the generator, the placers and the pack generator exist.

## Exit criteria

| Criterion | How it is met |
|---|---|
| Every species looks like its tree, to the human, in the viewer and in game | Manual tests P3-1 and P3-2 |
| Block counts inside the section 15 budgets | The 1,000-seed budget test, extended to every new species |
| 2×2 species keep the sapling square and narrow cleanly; conifers thin to a leader | Tests in `treecore` (already there for the rules), P3-3 in game |
| Decorators and roots behave with branch blocks in the log list | `docs/assumptions.md` P3-1 to P3-6 read in the sources; P3-4 in game |
| Forest density and cost for the new biomes inside the working ceiling of spec 15 | The Phase 2 measurement, repeated on a taiga and a jungle region |

## Decisions taken while planning (delegated; recorded in `questions.md` as Q22–Q24)

| # | Gap | Decision |
|---|---|---|
| Q22 | `trunk_vine` hangs a vine on every side of **every** position in the log list with chance 2/3 (`assumptions.md` P3-4). With 50 to 400 wood voxels per tree instead of vanilla's 10, a jungle tree would be wrapped in vines from trunk to twig. Spec section 10 says everything outside the two placers is vanilla's. | Keep vanilla's decorators in the first version, as the spec says, and let the human look (P3-4). If it is too much, the remedy stays data: the species file drops `trunk_vine` and keeps `leave_vine`, which is recorded as a deliberate difference from vanilla's file and tested as such. No code filters the log list. |
| Q23 | The placed features that mix oak or birch with other species kept vanilla's count in Phase 2 (Q19). | Phase 3 retunes all of them together with the new ones, by the same rule (a third of vanilla's base count, never below 1, shape of the distribution kept): `trees_taiga`, `trees_grove`, `trees_old_growth_pine_taiga`, `trees_old_growth_spruce_taiga`, `trees_jungle`, `trees_sparse_jungle`, `bamboo_vegetation`, `trees_savanna`, `trees_windswept_savanna`, `trees_windswept_forest`, `trees_swamp`, `trees_mangrove`, `trees_cherry`, `dark_forest_vegetation`. Those already at 0–1 trees per chunk stay vanilla's (`trees_snowy`, `trees_windswept_hills`, `trees_water`, `trees_plains`, `trees_meadow`). `bamboo_vegetation` and `dark_forest_vegetation` also place bamboo and huge mushrooms, so their new counts are judged in game before they are kept. |
| Q24 | `jungle_bush` and `azalea_tree` are `minecraft:tree` features the spec never mentions. | They stay vanilla. A bush is one log under a leaf blob, and the azalea's bending trunk is its character. |

## Species

Eleven species files, each supplying one or more vanilla keys. Size classes are those of spec 15.

| Species file | Vanilla keys | Class | Character to reach (spec 7.6, 12) |
|---|---|---|---|
| `spruce` | `spruce` | small | `conical`, `trunk_leader`, many short limbs, small constant sleeve, low `flatten`, slightly negative `lift`: layered boughs |
| `pine` | `pine` | medium | as spruce, a long bare trunk (`base_size` high), crown near the top |
| `mega_spruce` | `mega_spruce` | large | 2×2 from `trunk_width_min` 2 with `trunk_leader` (Q6: 2×2 on the bare trunk, then 1×1, then a leader), boughs far down |
| `mega_pine` | `mega_pine` | large | as mega spruce with a bare lower trunk |
| `acacia` | `acacia` | small | `base_splits`, strong curve, `inverse_conical`, flat wide sleeves (`flatten` low): an umbrella |
| `cherry` | `cherry`, `cherry_bees_005` | medium | low splits, spreading limbs that droop a little at the ends, generous sleeves |
| `jungle` | `jungle_tree`, `jungle_tree_no_vine` | small | slender and tall for its class, a small crown near the top |
| `mega_jungle` | `mega_jungle_tree` | large | 2×2, very tall bare trunk, a broad crown on top, a few limbs lower down |
| `swamp_oak` | `swamp_oak` | small | the oak, wider and lower, for `leave_vine` to hang from |
| `mangrove` | `mangrove`, `tall_mangrove` | small, medium | arching limbs with negative `attraction_up`; roots stay vanilla's. Two files if one cannot serve both heights. |
| `dark_oak` | `dark_oak` | medium | 2×2 short thick trunk (`trunk_width_min` 2, no leader, so 2×2 to the top), a wide dense crown |

## Tasks

| # | Task | Acceptance check |
|---|---|---|
| T1 | **Reading** of what the new woods rely on. | Done: `assumptions.md`, P3-1 to P3-6. |
| T2 | **Species drafts** as JSON in `tools/viewer`, tuned by the agent with `dumpSpecies` until each reads as its tree, in the order conifers, 2×2 species, acacia, cherry, jungle, swamp oak, mangrove. Heights start from vanilla's and move toward the size classes of spec 15. | The agent's PNG grids; the budget test over 1,000 seeds per species; for 2×2 species, the 2×2 base in every seed and a trunk that never ends wide unless the species says so. |
| T3 | **Generator gaps**, only if a species cannot be reached by data. Anything found is reported first as a question, with the species that needs it; spec 22 items stay out. | — |
| T4 | **Pack generator** extended: the new families and keys, the placed features of Q23, and the viewer's species list. `WorldgenPackTest` extended to every new file. | The test passes; both jars hold the new files. |
| T5 | **Branch blocks** need nothing: all eight woods exist since Phase 1. `swamp_oak` uses oak, `mangrove` uses mangrove branches. | — |
| T6 | **In-game round** with the human (tests below), then retuning from their feedback. | P3-1 to P3-4. |
| T7 | **Measurement** on a taiga and a jungle region with the Phase 2 kit; density packs regenerated for the new placed features. | Inside the ceiling of spec 15, or the counts lowered. |

## Manual tests the human will be asked to run

```
P3-1 — species in the viewer     ./gradlew :tools:viewer:run, seed grid of each new species: does it read as that tree?
P3-2 — species in the world      a new world; visit taiga, old growth taiga, jungle, savanna, cherry grove, swamp,
                                 mangrove swamp and dark forest. Screenshots; name anything that looks wrong.
P3-3 — 2×2 saplings              four spruce, jungle and dark oak saplings in a square, bone meal: the trunk stands on the
                                 sapling square; a single dark oak sapling still does nothing; podzol under mega spruce.
P3-4 — decorators                jungle trees: cocoa on the trunk, vines (judge the amount: Q22); swamp oaks: hanging vines;
                                 mangroves: roots under the trunk, propagules under leaves, the odd bee nest.
```

## Out of scope for this phase

Nether fungi and bamboo (spec section 2), felling (Phase 4), modded woods, crowns wider than 32 blocks, trunks wider
than 2×2, and everything else in spec section 22. In particular: roots of our own (22, item 5), hanging foliage for
weeping forms (22, item 11).
