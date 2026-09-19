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
of jumping from 2×2 to branch blocks. `trunk_width_max` always applies. To be looked at again when the
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

