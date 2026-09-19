# Questions for the human

Places where the spec is silent, unclear or looks wrong. Nothing here is decided by the agent.
Each open question carries a proposal; the proposal is what the code does until the question is answered.

## Open

None.

## Answered

### Q1 — Seeding xoroshiro128++ from one 64-bit value (spec 7.7)

*Asked:* the spec does not say how 64 bits become the 128-bit state.
*Answer (2026-09-19):* "Do whatever is best for this project."
*Decision:* two steps of SplitMix64, as the algorithm's authors recommend. Implemented in `Xoroshiro128PlusPlus`.
The same freedom is taken for the position hash of spec 7.6 step 4: a SplitMix64-style finaliser over the
seed and the packed position.

### Q2 — Negative `curve_v` (the paper's helix mode)

*Answer (2026-09-19):* it should be supported.
*Decision:* implemented in Phase 0. The paper describes the mode in one sentence, so the reading is recorded
in `paper-notes.md`, entry 2. **Proposed spec change** (the agent never edits the spec): in 8.2, add to
`curve_v` "A negative `curve_v` forms the stem as a helix, as in the paper."

### Q3 — Trunk length when the trunk curves

*Answer (2026-09-19):* the tree should be generated freely by the algorithm; no reason to limit it.
*Decision:* the trunk's length along its curve equals the free height vanilla passes in, and nothing is
clamped or corrected afterwards. Stems go wherever the formulas send them; the only limits are the ones the
spec names (`canPlace`, `max_radius`, the chunk area, the budgets).

### Q4 — "A stem shorter than 2 voxels is not generated" (spec 7.3): measured how?

*Asked:* the rule runs on the skeleton, before rasterisation, so a voxel count does not exist yet.
*Answer (2026-09-19):* the human delegated Q4–Q8 ("just do whatever is best") and later accepted the decisions below.
*Decision:* the skeleton length in blocks is the measure. A stem shorter than 2.0 blocks is not generated.
The same length drives the `curve_res` cap: effective `curve_res = max(1, min(curve_res, floor(length / 2)))`.

### Q5 — "At most one child starts from any face of a parent voxel" (spec 7.3): when is it enforced?

*Asked:* the parent voxel and face a child uses are only known when the wood is emitted, after the tip
budget and the thickness were computed.
*Decision:* enforced at emission. A losing child is treated exactly like a truncated one (spec 7.5): it and
its subtree are not generated, the earlier child in generation order wins, and nothing feeds back into the
load. It is the only point where the rule can be exact, and it reuses the truncation path instead of adding
a second mechanism. Consequence, accepted: a dropped child still counted toward `max_tips` and its
parent's load, as a truncated limb does. The stats counters report dropped children separately so tuning
can see them.

### Q6 — `trunk_leader` together with `trunk_width_min` 2 (spec 7.2)

*Asked:* one says the trunk follows the pipe model to its end, the other that it is never narrower than 2×2.
*Decision:* without `trunk_leader` the clamp is literal: the whole trunk is at least `trunk_width_min` wide.
With `trunk_leader` the pipe model governs the trunk, and `trunk_width_min` holds only on the bare part of
the trunk, from the base up to the first stem attached to it. The purpose of a minimum of 2 is the 2×2
sapling footprint, which this keeps, and the trunk then narrows 2×2 → 1×1 → branch leader in order instead
of jumping from 2×2 to branch blocks. The same holds for a minimum of 1: with `trunk_leader` the bare part of the
trunk is never thinner than a 1×1 log, so the leader begins at the first limb or above. Without this a tree that
ends up with three tips or fewer (a small birch that lost limbs to the 2-block rule or to an obstacle) would be a
stick of branch blocks from the ground up, which tuning showed on 2 of 6 seeds. `trunk_width_max` always applies. To be looked at again when the
mega spruce is tuned in Phase 3. **Proposed spec change:** add this sentence to the `trunk_leader` rule of 7.2.

### Q7 — Tip budget and split clones (spec 7.3)

*Asked:* "deepest level first, shortest first" can pick a stem that still has a split twin attached.
*Decision:* only stems with nothing attached to them are candidates; among those, deepest level first,
shortest first, then generation order; repeat until the budget holds. Each removal takes exactly one tip,
and no stem is ever orphaned.

### Q8 — Leaves outside the 33 × 33 column map (spec 7.5, 7.8, 13)

*Asked:* `canLeaf` has no radius test, so a sapling-grown tree could put leaves 18–20 blocks from the trunk.
*Decision:* a position more than 16 blocks from the trunk origin on either horizontal axis is never a leaf
candidate. It is an arithmetic test and runs before any world read. This is what makes the 32-block crown
limit of section 13 and the map size of 7.8 true for every tree, not only for generated ones.
**Proposed spec change:** state this test in 7.5 next to the description of `canLeaf`.

### Q9 — A species file can ask for an unbounded number of stems

*Asked:* `branches` up to 32 on three levels, or `seg_splits` 2 with `curve_res` 8, describe tens of thousands
of stems before the tip budget cuts them to `max_tips`. The spec sets no limit, so one datapack file could
stall world generation.
*Decision (delegated; accepted by the human 2026-09-19):* the skeleton stops creating stems at 2,048 (`Skeleton.MAX_STEMS`). The cap
is deterministic, is far above anything a sane species reaches (the 28-tip test species generates about 45),
and is reported by `TreeResult.stemCapReached()` so that `/naturaltrees stats` can show it.
**Proposed spec change:** mention the cap in 7.3.

### Q10 — A blocked extra column of a 2×2 section above the base

*Asked:* spec 7.4 says what happens when an extra column is blocked at the base (the whole trunk becomes
1×1) but not higher up, for example where a 2×2 trunk grows past an overhang.
*Decision (delegated; accepted by the human 2026-09-19):* the centreline column decides. If it is blocked the trunk is truncated there, as any
stem (7.5). A blocked extra voxel is simply not placed and the trunk continues; the trunk never floats,
because the centreline column is continuous.

### Q11 — The trunk's path and the free height

*Asked:* follows from Q3. A trunk of free height `h` must fill exactly `h` voxels, as a vanilla trunk does,
because that is the space vanilla checked.
*Decision (delegated; accepted by the human 2026-09-19):* the skeleton runs between voxel centres. The trunk starts at the centre of the origin
block and its length along the curve is `h − 1`, so a straight trunk fills `y = 0 … h − 1`. `length_trunk`
in the paper's formulas is this `h − 1`.

### Q12–Q15 — gaps found while planning Phase 1

*Decisions (delegated, 2026-09-19), details in `plan-phase-1.md`:*

- **Q12, creative tab (spec 6.1).** No new tab: each branch goes into vanilla's Building Blocks tab after that
  wood's stripped wood and into Natural Blocks after that wood's log.
- **Q13, growing the mod's trees before the built-in datapack exists (Phase 2).** A test datapack under
  `docs/test-datapacks/phase-1/`, shipped in no jar, supplies `minecraft:oak`, `minecraft:fancy_oak` and
  `minecraft:birch` for the Phase 1 manual tests. *(Deleted in the cleanup of 2026-09-20; the built-in pack replaces it.)*
- **Q14, loader hooks for stripping and flammability (spec 6.4).** `BranchBlock` in `common` holds all behaviour.
  NeoForge registers a thin subclass overriding the loader's extension methods; Fabric registers the block
  itself with a use-on-block callback and the flammable-block registry.
- **Q15, fuel (spec 6.5).** NeoForge's `neoforge:furnace_fuels` data map and Fabric's `FuelRegistry`.

### Q16 — Where the log textures are bound (spec 6.3)

*Asked:* spec 6.3 says each wood's **blockstate** binds `#side` and `#end`. Vanilla blockstate files cannot bind
textures; only models can.
*Decision (delegated, 2026-09-19):* the two hand-authored models stay as the spec says (`branch_core`, `branch_arm`,
textures only through `#side` and `#end`). Each wood gets two tiny child models that do nothing but bind those
two variables to its `log` and `log_top` textures, and the blockstate points at the children. The intent of 6.3
holds: no textures are added, and a resource pack that retextures logs retextures branches. A further
hand-authored model, `branch_inventory` (a core with an up and a down arm), is the item icon.

*Changed on the human's feedback from manual test P1-2 (2026-09-19):* a broken-off branch showed bark on its cut
end, because `updateShape` clears the arm (6.4) and the core had bark on every face. The core is now two
hand-authored face models, `branch_core_side` (bark) and `branch_core_end` (the log's end texture). A core face
is drawn only where no arm covers it, and it shows the end grain when the branch is a stub: exactly one arm, on
the opposite side. Elbows and junctions keep bark on their outer faces. The blockstate logic is checked for all
64 arm masks (exactly one model per face). So the hand-authored models are four, not the two of spec 6.3.
**Proposed spec change:** in 6.3, "blockstate binds them" becomes "child models bind them", and "two hand-authored
models" becomes the four named above, with the rule for the end grain.

### Q17 — Which placer fields are optional (spec 8.2)

*Asked:* the top-level table of 8.2 has a Default column, but the tables for `trunk` and for each entry of `levels`
have none, so read literally every one of their fields is required, including the many that are 0 in every species.
*Decision (delegated, 2026-09-19):* in `trunk` and in a level, `curve_res` is required, and so are a level's
`branches`, `length`, `down_angle` and `rotate`; `curve`, `curve_back`, `curve_v`, `seg_splits`, `split_angle`,
`split_angle_v`, `base_splits`, `length_v`, `down_angle_v` and `rotate_v` default to 0 and `tip_radius_offset` to 0.
The game's codecs (`ParamCodecs`) and the viewer (`PlacerJson`) apply the same rule, so a file means the same in both.
**Proposed spec change:** add a Default column to those two tables.

### Q18 — `/naturaltrees grid`: which seeds, how far apart (spec 16)

*Decision (delegated):* seeds 1 to `n` (at most 32), in a row toward +x, each tree on the ground of its own column.
Trees are `2 × (max_radius + foliage_margin) + 2` blocks apart when the feature uses the mod's trunk placer, so crowns
never touch, and 34 otherwise. Positions in unloaded chunks are skipped and counted in the message. `place` without a
seed draws one and prints it, so any tree can be placed again.

### Q19–Q21 — gaps found while planning Phase 2

*Decisions (delegated, 2026-09-19), details in `plan-phase-2.md`:*

- **Q19, which placed features get a new `count` (spec 11.3).** Only the seven made purely of oak and birch:
  `trees_birch_and_oak`, `trees_birch`, `birch_tall`, `trees_flower_forest`, `trees_plains`, `trees_meadow`,
  `trees_badlands`. Eleven more mix oak with species that stay vanilla until Phase 3 (`assumptions.md`, item 4);
  they keep vanilla's count until then.
- **Q20, where the built-in pack lives (spec 11.1).** `resourcepacks/naturaltrees_worldgen` in `common`'s resources,
  registered as optional and enabled by default through Fabric's `registerBuiltinResourcePack(…, DEFAULT_ENABLED)`
  and NeoForge's `AddPackFindersEvent.addPackFinders(…, PackSource.DEFAULT, false, …)`.
- **Q21, how the 15 files are made.** Generated by `tools/assetgen` from vanilla's own JSON and the viewer's species
  files, output checked in, with a test that each file equals vanilla's except for the two placer objects.
  `azalea_tree` uses oak logs but is not in spec 11.2 or the Phase 3 list, so it stays vanilla.

### Q22–Q24 — gaps found while planning Phase 3

*Decisions (delegated, 2026-09-19), details in `plan-phase-3.md`:*

- **Q22, `trunk_vine` on trees with hundreds of wood voxels.** Vanilla's decorator visits every position in the log
  list, which now includes every branch block (`assumptions.md`, P3-4). First version: vanilla's decorators
  unchanged, as spec section 10 says, and the human judges the amount in game. If it is too much, the jungle species
  files drop `trunk_vine` and keep `leave_vine`, as a recorded and tested difference from vanilla's file. No code
  filters the log list.
- **Q23, the mixed placed features left at vanilla's count in Phase 2 (Q19).** Retuned in Phase 3 by the same rule,
  together with the new ones; those already at 0–1 trees per chunk stay vanilla's. `bamboo_vegetation` and
  `dark_forest_vegetation` also place bamboo and huge mushrooms, so their counts are judged in game before they stay.
- **Q24, `jungle_bush` and `azalea_tree`.** Not in the spec; they stay vanilla.

### Q25 — A sleeve thinner than one block can produce no leaves at all (spec 7.6, 9.2)

*Found while drafting the conifers (2026-09-19).* The ranges of 9.2 allow `radius_tip` 0.5 and `flatten` 0.3. An
ellipsoid whose vertical radius is under half a block, shifted by `lift`, can sit between two block layers and
contain no block centre; a horizontal radius under 1.0 reaches no neighbouring block either. A spruce drafted with
`flatten` 0.45, `radius_tip` 0.8 and `lift` 0.5 had a bare top, and one tree in a thousand came out as a pole with
26 leaves.
*Decision (delegated):* the generator stays as the spec writes it: every ellipsoid is evaluated analytically from its
own radius, and nothing is rounded up behind the author's back. The remedy is data and is now tested: keep
`radius × flatten` at about 0.6 or more and radii at 1.0 or more where leaves are wanted. `ViewerTest` fails any
shipped species in which even one tree of 1,000 has 30 leaves or fewer. **Proposed spec change:** a sentence in 9.2
saying so, since a datapack author will meet it.

### Q26–Q29 — gaps found while planning Phase 4

*Decisions (delegated, 2026-09-19), details in `plan-phase-4.md`:*

- **Q26, where the `felling.*` keys live.** NeoForge: a `ModConfigSpec` of type SERVER. Fabric: a small key = value file,
  `config/naturaltrees-server.toml`, written with defaults on first start and read by the `fabric` module; no dependency.
- **Q27, the "known felling mods" of 14.2.** `fallingtree`, `treechop`, `treeharvester`, by mod id; checked against the
  real jars in test P4-3 before the list is final.
- **Q28, when the felling search runs.** The break only queues it; the next server tick checks that the block is gone
  and the player still holds an axe. One code path for both loaders; a cancelled break fells nothing.
- **Q29, modded woods.** `BranchBlock` is made independent of the closed `BranchWood` enum so that an addon mod can
  instantiate it. The Every Compat module adds a compile-only dependency on another mod and is **not** started
  without the human's word; the agent first reports that mod's API and licence.

### Q26–Q28 — answered by the human (2026-09-19)

- **Q26.** *"There is a Mod Menu mod for Fabric. Our configs should be compatible with this mod."* Done: the Fabric
  module has a `modmenu` entrypoint and a hand-written settings screen for the five `felling.*` keys, which edits
  `config/naturaltrees-server.toml` and applies at once to a world open on the same computer. Mod Menu 11.0.4 (MIT)
  is a compile-only reference and is put into the development client for testing; the published mod neither
  requires nor bundles it, so "no runtime dependencies beyond the loaders" (spec section 4) still holds.
  This brings the Fabric half of spec 22 item 4 (config screens) into version 1 at the human's request.
  **Proposed spec change:** say so in section 16. NeoForge's native config screen (the other half of 22, item 4)
  followed on the same day, when the human found the Config button of the NeoForge Mods screen greyed out: a
  client-only `@Mod` class (`NaturalTreesClient`) registers NeoForge's own `ConfigurationScreen`, with labels
  under `naturaltrees.configuration.*`. Server values are editable there while a world is open on the same computer.
- **Q27.** Accepted: `fallingtree`, `treechop`, `treeharvester`, to be confirmed against the jars in test P4-3.
- **Q28.** Delegated: the break queues the felling and the next server tick starts it, as planned.

### Q30 — The mod id of guita's Branches (spec section 19)

*Asked:* the agent needed that mod's id to disable the stacked-logs recipe when it is installed, as the risk table of
spec section 19 says.
*Answer (2026-09-19):* "You don't need to write compat with guita's Branches mod."
*Decision:* no recipe condition, no detection of that mod. The stacked-logs recipe is unconditional. If both mods are
installed and their recipes collide, the recipe manager picks one; that is accepted.
**Proposed spec change:** in section 19, the row "The stacked-logs recipe collides with guita's Branches" changes
its response from "Detect that mod and disable the recipe" to "Accepted; no special handling."

### Q31 — Size classes and density after the look session (spec 15, 11.3)

*Context (2026-09-19):* the human asked for beautiful, realistic trees and allowed them to be larger than vanilla's,
inside the hard limits (`max_radius` ≤ 14, `max_radius + foliage_margin` ≤ 16, 48 tips, 4,096 leaves, heights,
1×1 trunks for single-sapling species, 2×2 for the others).
*Decisions (delegated):*

- The size classes of spec 15 are replaced in `ViewerTest` by medium ≤ 220 wood / 48 tips / 900 leaves / radius 9,
  large ≤ 400 / 48 / 2,200 / 12 and giant ≤ 600 / 48 / 3,500 / 12; the small class is empty. A conscious revision on
  the human's instruction, not a weakened test: the near-leafless, 2×2-base, trunk-top and margin checks are unchanged.
- `WorldgenPackGen.DENSITY` goes from a third to a fifth of vanilla's count, because crowns are now 12–25 blocks wide.
  To be judged in game; jungle (`trees_jungle` 50 → 10) and the dark forest (16 → 3) are the ones to look at first.
- Test helpers only: `SpeciesDump` spreads its seeds over the whole height range and prints the lowest and the widest
  leaf; `SpeciesStats` (`./gradlew :tools:viewer:speciesStats`) prints the per-species table of `phase-0-results.md`.

**Proposed spec change:** `phase-0-results.md`, proposed edit 6.

### Q32 — "The exact midpoint between the current and previous versions": what has no midpoint

*Instruction (2026-09-19):* apply the exact midpoint values between the current (round 4) and previous (commit
`683e2e1`) versions.
*Decisions (delegated):* every number is the arithmetic mean. A whole-number field whose mean is a half is rounded
toward round 4. A `shape` that changed, a level that did not exist before, and acacia's level-2 `rotate` (whose sign is
a mode, not a magnitude) keep their round 4 value. `WorldgenPackGen.DENSITY` is treated the same way, 4/15, because it
was changed together with the sizes. The revised size classes (Q31) are upper bounds and stay. The copy of round 4's
species files and the script that made the blend are not in the repository (removed in the cleanup of 2026-09-20).

### Q33 — Roots (spec section 22, item 5), brought into version 1 by the human

*Instruction (2026-09-19):* "A distinctive feature of the swamp tree is its very large roots that protrude from the
ground or water. If the algorithm doesn't allow you to achieve this, you'll have to change the algorithm."
*Decisions (delegated), details in `phase-0-results.md`:*

- A `roots` object in the trunk placer, not a vanilla `root_placer`: vanilla's slot moves the trunk origin and places
  its own blocks, while these roots must be this tree's logs and branch blocks, inside `canPlace`, fellable with the tree.
- Roots are outside the pipe model, the tip budget and the foliage, so existing species and golden hashes are untouched.
- Thickness is data (`log_share`), because a root carries no tips for the pipe model to count.
- **To check in game:** vanilla's `mangrove_root_placer` stays in the mangrove files (everything outside the two placers
  is vanilla's), so a mangrove gets vanilla's prop roots under the raised trunk **and** these stilt roots from the trunk.
  They stop at the first `mangrove_roots` block (`canPlace`). If the two together are too much, the mangrove files lower
  `roots.count` or `roots.depth`; nothing in code changes.

**Proposed spec change:** `phase-0-results.md`, proposed edit 7.

### Q34 — Density per biome: denser oak and birch forests, sparser savannas (spec 11.3)

*Instruction (2026-09-19):* "Slightly increase the forest density in a typical oak forest and in a birch forest (and the
mixed forest), and slightly decrease the density in the savanna."
*Decisions (delegated):*

- `WorldgenPackGen.DENSITY_BY_FEATURE`: `trees_birch_and_oak`, `trees_birch` and `birch_tall` use 0.4 of vanilla's count
  instead of the general 4/15: 4 trees per chunk, sometimes 5 (was 3, sometimes 4; vanilla 10, sometimes 11).
  `trees_flower_forest` stays at 2–3: it is meant to be open.
- The savannas cannot be thinned by a fraction, because vanilla places 1 tree per chunk (windswept: 2). `trees_savanna`
  joins the pack (19 placed features) and both savannas get their own weighted count, 0 × 4, 1 × 5, 2 × 1: 0.7 trees
  per chunk on average (vanilla 1.1 and 2.1; before this, 1.1 for both).
- `WorldgenPackTest` keeps its rule "fewer trees than vanilla, never none, weights as vanilla's" for every other feature;
  for the two savannas, whose weights must differ, it checks the same rule on the average. A conscious revision on the
  human's instruction. The measurement packs scale the per-feature densities and do not contain the savannas,
  whose count does not scale: the built-in pack's file applies.

### Q35 — The azalea tree joins the pack (reverses part of Q21 and Q24)

*Instruction (2026-09-19):* a screenshot of vanilla's azalea tree, "I think we missed this small tree with flowers".
*Decision:* `azalea_tree` gets the mod's two placers from a new species file, `azalea.json`, as every other key: 30
configured features. It uses oak logs, so its branch block is `naturaltrees:oak_branch`; no new block. Everything else
in the file stays vanilla's, including the weighted leaf provider (azalea and flowering azalea leaves), `force_dirt`
and rooted dirt, and the `root_system` feature that grows it above lush caves is not touched. `jungle_bush` stays
vanilla: it is a bush, not a tree with a trunk. **To check in game:** the tree on its rooted-dirt column above a lush
cave, and an azalea bush grown with bone meal. **Proposed spec change:** add `azalea_tree` to the table of 11.2.

