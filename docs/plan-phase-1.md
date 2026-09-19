# Plan — Phase 1: Block and placers in game

Spec section 18, Phase 1: the branch block with states, models, shapes, tags, loot, recipes, fuel,
flammability, shears, stripping and waterlogging, on both loaders; both placer types and the foliage handoff,
on both loaders; `/naturaltrees place` and `grid`; the name and slug reserved on Modrinth and CurseForge.

**Status: waiting for approval.** No Phase 1 code exists. Phase 0's exit criteria that need the human's eyes
(manual tests P0-2 and P0-3) are still open; tuning can go on beside Phase 1, because species are data.

## Exit criteria (spec 18)

| Criterion | How it is met |
|---|---|
| Section 20 items 1–3 and 5–14 confirmed | Read in the sources: `docs/assumptions.md`, all true. Confirmed in game by manual tests P1-5 to P1-8. |
| A sapling-grown tree and a generated tree both keep every leaf after several in-game days | Manual test P1-7 |
| No far-chunk messages over a 1,000-chunk pre-generation | Manual test P1-8 |
| Name and slug reserved on Modrinth and CurseForge | The human; the agent cannot create accounts or projects |

## Decisions taken while planning (delegated; recorded in `questions.md` as Q12–Q15)

| # | Gap | Decision |
|---|---|---|
| Q12 | Spec 6.1 asks for a creative-tab entry but names no tab. | No new tab. Each branch goes into vanilla's Building Blocks tab right after that wood's stripped wood, and into Natural Blocks after that wood's log, which is where players look for logs. |
| Q13 | The built-in datapack is Phase 2, but Phase 1's exit tests need a sapling and world generation to grow the mod's trees. | A **test datapack** under `docs/test-datapacks/phase-1/`, not shipped in any jar: `minecraft:oak`, `minecraft:fancy_oak` and `minecraft:birch` with the Phase 0 species. The human copies it into the test world's `datapacks` folder. |
| Q14 | Spec 6.4 says NeoForge uses the tool-modified-state hook and Fabric a use-on-block hook, and `common` cannot see either API. | `BranchBlock` lives in `common` and holds all behaviour. NeoForge registers a thin subclass that overrides the loader's extension methods (tool-modified state, flammability); Fabric registers `BranchBlock` itself plus a `UseBlockCallback` for axes and the flammable-block registry. The strip itself (state copy, sound, durability) is one method in `common`. |
| Q15 | Fuel value 150 ticks (spec 6.5). | NeoForge: the `neoforge:furnace_fuels` data map. Fabric: `FuelRegistry`. No code path in `common`. |

## Tasks

| # | Task | Spec | Acceptance check |
|---|---|---|---|
| T1 | **Registration scaffolding.** A small registry service in `common` (`Services`), implemented with `DeferredRegister` on NeoForge and `Registry.register` on Fabric. The `WoodType` table: 8 woods × {plain, stripped}, each bound to its log, stripped log, planks and textures. | 4, 6.1 | `./gradlew build`; both clients start (P1-1). |
| T2 | **`BranchBlock`.** Six arm booleans and `waterlogged` (128 states); collision and selection shapes as the union of an 8 px core and arms, cached for the 64 arm masks; no occlusion, skylight passes when not waterlogged, not pathfindable; `updateShape` clears an arm whose target became air or fluid and never sets one; placement gives one arm toward the clicked block and the reciprocal arm on a branch; `skipRendering` for two arm ends face to face; log properties (hardness, blast resistance, sound, map colour, lava ignition) copied from the wood's log. | 6.2–6.4 | A unit test over all 64 masks for the shapes (pure geometry, no game needed); manual tests P1-2, P1-3. |
| T3 | **Shears and stripping.** Shears toggle the arm on the clicked face and the reciprocal arm on a branch neighbour, one durability. Axe strips to the stripped variant and keeps arms and waterlogging (Q14). | 6.4 | Manual tests P1-3, P1-4. |
| T4 | **Assets.** Two hand-authored models, `branch_core` and `branch_arm` with `cullface` on the arm's end; 16 multipart blockstates binding `#side` and `#end` to the log textures; 16 item models; `en_us` names. The 16 × 2 repetitive files are written by a small generator under `tools/`, and its output is checked in. No textures. | 6.3 | P1-2: every wood renders with its log's bark and end grain; a resource pack that retextures logs retextures branches. |
| T5 | **Data.** Block tags of assumption 6 (`<wood>_logs`, `overworld_natural_logs` for the plain branch; the rest follows); item tags `#naturaltrees:branches` and `#naturaltrees:<wood>_branches` only; loot (drops itself, explosion condition as a log); recipes: 2 stacked logs → 4 branches, 1 branch → 2 planks, the same with stripped; fuel 150 (Q15); flammability 5/5 as logs (Q14). | 6.5 | P1-4: recipes and values as the table in 6.5; a branch is refused by the vanilla plank recipe and by a furnace charcoal recipe; a leaf beside a branch does not decay (assumption 3). |
| T6 | **Trunk placer** `naturaltrees:weber_penn`. Codec with every field, default and range of 8.2, delegating validation to `treecore` so game and viewer agree; the rule `max_radius + foliage_margin ≤ 16`; `placeTrunk` steps 1–8 of spec 8.1: seed from one `nextLong()`, `canPlace`/`canLeaf` from `PlacementLimits` and `TreeFeature.validTreePos`, dirt under every trunk column, logs with axis and branches with arms (waterlogged where the position held water) through the setter with one mutable position, attachments above each tip. Registered through an invoker mixin on Fabric. One `TreeGenerator` per thread. The margin warning of 9.2, once per species. | 7.5, 7.7, 8 | P1-5, P1-6. |
| T7 | **Foliage placer** `naturaltrees:skeleton` and the **foliage handoff**: a per-thread slot holding the leaf voxels and the identity of the returned attachments; first matching attachment places all leaves through `tryPlaceLeaf` and empties the slot; others do nothing; a foreign attachment gets the fallback cluster of 9.1. | 9 | P1-5; P1-6 includes a species that pairs the foliage placer with a vanilla trunk placer (fallback) and one that pairs the trunk placer with `minecraft:blob_foliage_placer` (9.3). |
| T8 | **Commands** `/naturaltrees place <configured_feature> [seed]` and `/naturaltrees grid <configured_feature> <n>`, permission level 2. `stats` is Phase 2 with the measurements. | 16 | P1-5. |
| T9 | **Test datapack** (Q13) and the manual test scripts below, kept current. | 18 | The human can run P1-1 to P1-8 from `docs/` alone. |

Order: T1 → T2 → T4 → T3 → T5 → (P1-1 to P1-4 with the human) → T6 → T7 → T8 → T9 → (P1-5 to P1-8).
Inside `common`, the `block` package does not depend on `worldgen` (spec section 4).

## Manual tests the human will be asked to run

Each is run on **both** clients unless it says otherwise. Every one ends with: send back a screenshot and any
line of `logs/latest.log` containing `naturaltrees` at WARN or ERROR.

```
P1-1 — the mod still loads            both clients reach the title screen; 16 branch items in the creative tabs (Q12).
P1-2 — the block looks right          place every wood's branch against its log; bark on the sides, end grain on
                                      free arm ends, no face drawn between two joined arms or against a log.
P1-3 — arms                           the script of the brief (section 3): placement arms, reciprocal arms, shears,
                                      breaking a neighbour clears the arm that pointed at it; water flows in and out.
P1-4 — a branch is half a log         recipes and fuel of 6.5; axe strips and keeps arms; fire burns it like a log;
                                      a leaf placed beside a lone branch does not decay; pistons and explosions drop it.
P1-5 — a placed tree                  /naturaltrees place minecraft:oak 1 on a superflat world, then grid … 8:
                                      the same seed gives the same tree; trees look like the viewer's.
P1-6 — obstruction                    place trees beside a wall, under a roof, in water, on a cliff edge: limbs stop
                                      at obstacles, nothing floats, no leaves without wood, branches in water are waterlogged.
P1-7 — leaves persist                 grow oak and birch saplings (bone meal) and generate a forest chunk area; wait
                                      several in-game days (/time add, randomTickSpeed up): no leaf decays; then cut a
                                      limb and watch its leaves decay.
P1-8 — no far-chunk writes            Fabric, with a pre-generation mod in the run folder: pre-generate 1,000 chunks
                                      of a forest-heavy seed with the test datapack; search the log for
                                      "Detected setBlock in a far chunk": expect none.
```

## Out of scope for this phase

The built-in datapack and placed-feature counts (Phase 2), `/naturaltrees stats`, felling and its config
(Phase 4), modded woods, anything in spec section 22.
