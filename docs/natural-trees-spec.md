# Natural Trees: Technical Specification

**Version 1.0 (draft).** Mod ID and namespace `naturaltrees`. Target: Minecraft 1.21.1.

---

## 1. Overview

Natural Trees makes Minecraft's trees look like trees: a trunk that thickens with the tree's size, limbs that fork and curve, and foliage that follows the limbs. Every tree is unique, generated from one parameter set per species.

The whole tree is one computation. A single skeleton determines where the wood goes, how thick each part is, and where the leaves sit.

The mod consists of three things:

- A **branch block** for each wood type: a thin, connecting wooden block that behaves as a log.
- A **trunk placer**, `naturaltrees:weber_penn`, for vanilla's `minecraft:tree` feature. It generates a Weber & Penn skeleton, derives thickness from it, and places vanilla logs and branch blocks.
- A **foliage placer**, `naturaltrees:skeleton`, which places the leaves that the same computation derived from the skeleton.

Leaves are vanilla leaf blocks with vanilla decay. Vanilla bakes their distances, runs decorators such as beehives, and grows saplings. A species is an ordinary configured-feature JSON file, so a datapack can add or retune one without code.

The mod ships a built-in datapack that supplies vanilla's tree configured features **by ID**. Biome feature lists are untouched, so the mod cannot introduce a feature-order cycle.

---

## 2. Scope

**Design principles**

1. **Fully procedural.** Every tree is computed from its parameters and its seed at the moment it is placed: skeleton, thickness and every leaf. Nothing is pre-grown, cached between trees, or copied from a template shape.
2. Vanilla materials and vanilla behaviour. A player who knows how logs and leaves work already knows how these trees work.
3. One skeleton drives everything: wood, thickness and foliage.
4. Leave to vanilla everything vanilla already does well.
5. Data-driven. Shape, size, thickness, foliage and density are JSON.
6. Cost is controlled by data: leaves per tree, stems per tree, trees per chunk. Optimisation may change how fast a tree is computed, never which tree comes out.

**Out of scope for version 1**

- Custom leaf blocks. Leaves are vanilla blocks with vanilla mechanics; only their arrangement is the mod's.
- More than one branch thickness.
- Any decay or collapse behaviour for branches.
- Crowns wider than 32 blocks. Trunks wider than 2×2.
- A Java API for species. The datapack format is the interface.
- An in-game editor.
- Branch blocks for modded wood types, and adoption of other mods' trees. Both are later phases (section 18).
- Nether fungi and bamboo.
- Minecraft versions other than 1.21.1.

Out of scope means not in version 1, not abandoned. Every deferred idea is recorded in section 22 so that none is lost.

---

## 3. Terminology

| Term | Meaning |
|---|---|
| **Stem** | One continuous run of wood in the skeleton: the trunk, a limb, or a twig. |
| **Level** | Depth of a stem in the skeleton. The trunk is level 0, its children are level 1, and so on. |
| **Tip** | The end of a stem. |
| **Load** | At a point on a stem, the number of tips at or beyond that point. |
| **Radius** | The thickness the pipe model assigns to a point on a stem, from its load (7.2). |
| **Arm** | The part of a branch block that reaches from its core to one of its six faces. |
| **Sleeve** | The foliage volume around the outer part of a stem (7.6). |
| **Species** | One `minecraft:tree` configured-feature file that uses the mod's placers. |
| **Size class** | A convention for species budgets: small, medium, large (section 15). |
| **Decorating chunk** | The chunk whose feature step is currently running. It may write into itself and its eight neighbours. |

---

## 4. Platform and project layout

- Minecraft **1.21.1**, Java 21, Mojang mappings.
- NeoForge and Fabric, built from jaredlll08's MultiLoader-Template.
- No runtime dependencies beyond the loaders.
- License: Apache-2.0. guita's Branches (LGPLv3) is the behavioural reference for the branch block, and Dynamic Trees (MIT) is a technique reference for the voxel map and the self-shading rule in section 7. No code, models or textures are taken from either.

| Module | Contents | Ships in the mod jar |
|---|---|---|
| `treecore` | The generator: skeleton, thickness, wood voxels, foliage voxels. Pure Java, no Minecraft classes, no dependencies. JUnit and JMH. | Yes, shaded |
| `common` | Branch block, items, tags, recipes, loot, both placers, commands, felling, built-in datapack | Yes |
| `neoforge`, `fabric` | Registration, flammability, fuel values, strip hook, datapack registration, config | Yes |
| `tools/viewer` | Developer tuning tool (section 17) | No |

One jar is published per loader. `treecore` is also published to Maven. Inside `common`, the `block` package must not depend on `worldgen` or `felling`.

---

## 5. Architecture

```
placed feature              vanilla file, count adjusted
  └─ configured feature     minecraft:tree, vanilla ID, file supplied by the mod
       ├─ minimum_size, dirt_provider, decorators      as in vanilla
       ├─ trunk_placer      naturaltrees:weber_penn
       │     treecore: skeleton → thickness → wood voxels + leaf voxels + tips
       │     places wood through vanilla's trunk setter
       │     hands the leaf voxels to the foliage placer; returns the tips
       └─ foliage_placer    naturaltrees:skeleton
             places the leaf voxels through vanilla's foliage setter

  then, inside vanilla's TreeFeature:
       decorators → updateLeaves (distance baking) → updateShapeAtEdge
```

| Concern | Handled by |
|---|---|
| Tree height, free-space check of the trunk column | Vanilla `TreeFeature` |
| Skeleton, thickness, block choice, branch arms, obstruction handling, crown limit | `treecore`, through the trunk placer |
| Foliage shape | `treecore`, through the foliage placer |
| Leaf blocks, waterlogging of leaves, replaceability of each leaf position | Vanilla `tryPlaceLeaf` |
| Leaf `distance` baking | Vanilla `TreeFeature.updateLeaves` |
| Beehives, vines and other decorators | Vanilla decorators in the species file |
| Sapling growth | Vanilla `TreeGrower` |
| Block writes, heightmaps, light | Vanilla `setBlock` path |
| Leaf decay at runtime | Vanilla `LeavesBlock`, through the `#minecraft:logs` block tag |

---

## 6. The branch block

### 6.1 Blocks

For each overworld wood type (oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry): `naturaltrees:<wood>_branch` and `naturaltrees:stripped_<wood>_branch`. Sixteen blocks, each with an item and a creative-tab entry.

### 6.2 Block state

| Property | Values | Meaning |
|---|---|---|
| `up`, `down`, `north`, `south`, `east`, `west` | boolean | An arm reaches the block face in that direction |
| `waterlogged` | boolean | Standard |

128 states per block.

### 6.3 Shape, models and rendering

- A centred core cube plus one arm per set direction. **Thickness is 8 px.**
- Two hand-authored models: `branch_core` and `branch_arm`. A multipart blockstate rotates the arm into the six directions.
- Models reference textures only through `#side` and `#end`. Each wood type's blockstate binds them to its `log` and `log_top` textures, or the stripped pair. The mod adds no textures, and resource packs that retexture logs retexture branches.
- The arm's end face carries `cullface` for its direction, so it is culled against a full log. The block overrides `skipRendering` so that two arm ends meeting face to face are both skipped.
- Collision and selection shapes are the union of core and arms, cached for the 64 arm combinations.
- The block does not occlude, lets skylight through when not waterlogged, and is not pathfindable.

### 6.4 Behaviour

- **Arms are explicit state.** An arm exists only because the generator, a placement or a pair of shears set it. Two unrelated branches that pass side by side do not join.
- **Placement.** A placed branch gets one arm toward the block it was placed against. If that block is a branch, it gets the reciprocal arm.
- **Shears** toggle the arm on the clicked face, and the reciprocal arm if the neighbour is a branch. One durability per use.
- **Axe** strips: the block becomes its stripped variant and keeps its arms and waterlogging. NeoForge uses the tool-modified-state hook; Fabric uses a use-on-block hook.
- **Neighbour updates.** `updateShape` clears an arm when the block it points at becomes air or fluid. It never sets an arm. Generated states contain only arms that point at wood, so they are fixed points.
- **Waterlogging** as any waterloggable block.
- **Persistence.** A branch has no decay mechanic. It stays until it is broken, burned or exploded, as a log does.
- Hardness, blast resistance, sound, map colour, lava ignition and flammability are those of the wood type's log.

### 6.5 Treated as a log

**As a block, a branch is a log.**

- It is in `#minecraft:logs_that_burn`, and through it `#minecraft:logs`; in `#minecraft:mineable/axe`; and in every other block tag that vanilla logs carry in 1.21.1.
- Vanilla leaf decay therefore applies to it exactly as to a log: a leaf next to a branch has `distance = 1`, leaves within reach of a branch persist, and leaves decay once the branch is removed.
- Tag-driven mods, including tree-felling mods, see a branch as a log.
- It drops itself, with the same explosion condition as a log.

**As an item, a branch is worth half a log.**

| Conversion | Result | Value in planks |
|---|---|---|
| 1 log → planks (vanilla recipe) | 4 planks | 4 |
| 2 logs, one above the other (shaped) | 4 branches | 8 |
| 1 branch → planks (shapeless) | **2 planks** | 2 |
| Furnace fuel | 150 ticks; a log is 300 | — |

Every conversion is value-neutral, so no loop gains material. Stripped branches follow the same recipes with stripped logs and yield the same planks.

Branch items are in `#naturaltrees:branches` and `#naturaltrees:<wood>_branches`. They are **not** in `#minecraft:logs`, `#minecraft:logs_that_burn` or `#minecraft:<wood>_logs`. The vanilla plank recipe reads the per-wood log item tag and yields four planks, which would break the half-log value; and any recipe in any mod that accepts "any log" would accept a branch at half price. For the same reason a branch does not smelt into charcoal.

---

## 7. The generator (`treecore`)

**Input:** trunk placer parameters (8.2), foliage parameters (9.2), a 64-bit seed, the tree height in blocks, the origin, and two predicates: `canPlace(x, y, z)` for wood and `canLeaf(x, y, z)` for leaves.

**Output:**

- wood voxels, parent before child, each either `LOG` with an axis or `BRANCH` with an arm mask;
- leaf voxels, nearest to wood first;
- tips, each a position and a `radius_offset`.

The pipeline is: skeleton (7.1) → tip budget (7.3) → thickness (7.2) → wood (7.4, 7.5) → foliage (7.6).

### 7.1 Skeleton

The skeleton follows Weber & Penn, "Creation and Rendering of Realistic Trees" (SIGGRAPH '95), section 4. The paper's formulas apply to everything listed as used below.

| Paper parameters | Use |
|---|---|
| `Shape` (ShapeRatio shapes 0–7), `BaseSize`, `AttractionUp` | Used |
| Per level: `nBranches`, `nLength`/`V`, `nDownAngle`/`V`, `nRotate`/`V`, `nCurve`, `nCurveBack`, `nCurveV`, `nCurveRes`, `nSegSplits`, `nSplitAngle`/`V`; `0BaseSplits` | Used |
| `Scale`, `ScaleV` | Not used. Tree height comes from vanilla's `base_height`, `height_rand_a` and `height_rand_b`; vanilla checks free space and passes the usable height to the placer. |
| `Ratio`, `RatioPower`, `nTaper`, `Flare`, `Lobes`, `LobeDepth`, `0Scale` | Not used. The paper's radii are metres and fall below one block. Thickness comes from the pipe model (7.2). |
| `Leaves`, `LeafShape`, `LeafScale`, `LeafScaleX` | Not used. The paper places individual leaves; a leaf block is a cubic metre of foliage. Foliage comes from 7.6. |
| `Prune*` | Not used. The crown is limited by `max_radius` (7.5). |

### 7.2 Thickness: the pipe model

Thickness is computed, not assigned. It follows the botanical pipe model: the cross-section of a stem is proportional to the foliage it carries.

- **Load** at a point on a stem is the number of tips at or beyond that point: the stem's own tip plus the tips of every stem attached at or beyond it. Load is counted on the skeleton after the tip budget (7.3).
- **Radius** in blocks: `radius = twig_radius × load ^ (1 / pipe_exponent)`.

With the defaults, `twig_radius` 0.25 and `pipe_exponent` 2.0:

| Radius | Placed as | Load that reaches it |
|---|---|---|
| below 0.5 | Branch block | 1–3 |
| 0.5 to below 1.0 | 1×1 log | 4–15 |
| 1.0 and above | 2×2 logs | 16 and more |

Rules:

- Load never increases toward a tip, so a stem only ever steps down in thickness: 2×2, then 1×1, then branch.
- Level-0 stems are never thinner than a 1×1 log, unless the species sets `trunk_leader`. With `trunk_leader`, the trunk follows the pipe model to its end, so its top thins to a leader of branch blocks where the load falls below 4. This suits conifers and slender upright species.
- Only level-0 stems may be 2×2. Limbs stop at 1×1.
- The trunk's width is clamped to `trunk_width_min` and `trunk_width_max`.

Consequences with the defaults: a tree with 8 tips has a 1×1 trunk and branch-block limbs. A tree with 14 tips has a 1×1 trunk, and any limb carrying four or more tips starts as logs and turns into branch blocks after it forks. A tree with 28 tips has a 2×2 lower trunk that narrows to 1×1 where the load falls below 16. Very tall trees get a 2×2 trunk because they carry many tips, with no species-specific rule.

A higher `pipe_exponent` gives slimmer trees; a higher `twig_radius` gives stouter ones.

### 7.3 Voxel-scale rules

- A stem shorter than 2 voxels is not generated.
- The effective `curve_res` of a stem is capped at half its length in voxels.
- At most one child starts from any face of a parent voxel.
- **Tips:** the end of every stem is a tip, including the top of the trunk.
- **Tip budget:** if the skeleton has more stems than `max_tips`, stems are removed until it fits, deepest level first and shortest first within a level, with ties broken by generation order. A removed stem takes its wood, its tip and its foliage with it.

### 7.4 Rasterisation and arms

- **1×1 logs** are rasterised with a 3D DDA, 26-connected, as vanilla fancy-oak limbs are. The log axis is the dominant component of the segment direction; at an exact diagonal, horizontal wins.
- **2×2 trunk sections** are rasterised per Y layer. If the centreline passes through column (x, z) at that layer, the section occupies (x, z), (x+1, z), (x, z+1) and (x+1, z+1), which is the convention vanilla's 2×2 trees follow from their origin. Where the trunk narrows to 1×1 it continues in the centreline's column. A child of a 2×2 section starts from the section's outer face in the child's direction.
- If any of the three extra columns is blocked at the base, the whole trunk is 1×1, regardless of `trunk_width_min`.
- **Branches** are rasterised **face-connected**: one axis step at a time, so consecutive branch voxels always share a face.
- The first voxel of a child stem is face-adjacent to a voxel of its parent. Where a stem steps down from log to branch, the first branch voxel is face-adjacent to the last log voxel.
- **Arms come from the skeleton, never from adjacency.** Each consecutive pair of branch voxels in a stem gets reciprocal arms. A branch voxel that follows a log, or starts a child stem, gets an arm toward that parent voxel, and the parent gets the reciprocal arm if it is a branch. Where two stems occupy the same voxel, their arm masks are merged and `LOG` wins over `BRANCH`.

### 7.5 Obstruction and limits for wood

Stems are emitted parent-first. Before each voxel the generator calls `canPlace`. At the first `false`:

- the stem is truncated there;
- children that would attach beyond the cut are not generated;
- if at least 2 voxels of the stem were emitted, its new end is its tip; otherwise it has no tip.

Children attach only to emitted voxels, so no wood can float.

The trunk placer builds `canPlace` from three tests, evaluated in this order, and stops at the first failure:

1. During world generation, the position is inside the 3×3 chunk area around the decorating chunk, with `foliage_margin` blocks to spare.
2. The position is within `max_radius` blocks, horizontally, of the trunk origin.
3. Vanilla's tree-position rule: the block is air or in `#minecraft:replaceable_by_trees`.

The first two are arithmetic. Only the third reads the world, and because it runs last the generator never asks for a block outside the area it may write to. `canLeaf` is built the same way, without the radius test and without the margin.

Terrain, neighbouring trees, the crown limit and the chunk boundary are thus one code path.

### 7.6 Foliage

Foliage is computed from the **emitted** wood, after truncation, so nothing hangs from a limb that was not placed. It follows the stems: each stem carries a sleeve of leaves around its outer part, wider toward the tip, and the crown is the union of the sleeves.

1. **Bearing wood.** On each emitted stem, the part from fraction `foliage_start` of its emitted length to its end bears foliage. On level-0 stems the bearing part is the last `trunk_foliage` of the length. A stem shorter than 3 voxels bears along its whole length.
2. **Sleeve radius.** At bearing position `t`, from 0 where the bearing part starts to 1 at the tip: `r(t) = radius_base + (radius_tip − radius_base) × t`, multiplied by one random factor per stem in the range `1 ± radius_v`.
3. **Candidates.** Each bearing wood voxel defines an ellipsoid with horizontal radius `r` and vertical radius `r × flatten`, centred `lift × r` blocks above the voxel. A position is a candidate if it lies inside any ellipsoid, holds no wood of this tree, and passes `canLeaf` (7.5). Every ellipsoid is evaluated analytically from that voxel's own radius; no shape is precomputed or reused.
4. **Edge thinning.** For each candidate, take the smallest normalised distance to the centre of any ellipsoid containing it. Above 0.7, the candidate is kept with probability `density`, decided by a hash of the seed and the position. At or below 0.7 it is always kept, so the inner mass stays connected.
5. **Self-shading.** A kept candidate is discarded if more than `smother` of this tree's kept candidates lie above it in the same column. Foliage buried under the tree's own canopy is removed, as shaded foliage dies on a real tree: crowns become hollow, and twigs deep under the canopy may end bare. Only the tree's own candidates are counted, so the rule reads nothing from the world. `smother` 0 turns it off.
6. **Reach.** A breadth-first search starts from all emitted wood voxels and moves through kept candidates across shared faces, which is the metric vanilla leaves use. A candidate more than `max_distance` steps from wood, or not reached, is discarded. It runs after self-shading, so it validates the foliage that will actually be placed.
7. **Leaf budget.** If more than `max_leaves` remain, leaves are discarded from the greatest distance inward, ties broken by the position hash, until the budget holds. Removing the farthest leaves first never disconnects a nearer one. Self-shading removes leaves nobody sees; the budget removes visible ones, so it is the last resort.

The result is ordered by distance, nearest first.

Because `max_distance` is at most 6 and the search uses vanilla's metric over the positions that will actually receive leaves, every generated leaf is within vanilla's reach of wood and none decays after generation.

Species character comes from a few numbers. A broadleaf crown uses a wide tip radius and positive lift, so foliage sits on top of the limbs. A conifer uses the `conical` shape, many short limbs, a small constant radius, a low `flatten` and a slightly negative `lift`, which gives layered boughs. A weeping form uses negative `attraction_up` and negative `lift`.

### 7.7 Determinism

The trunk placer draws one `nextLong()` from the `RandomSource` vanilla supplies and seeds an in-core xoroshiro128++ generator from it. Vanilla seeds that source per chunk and per feature, so the same world seed and the same data produce the same trees. Position hashes in 7.6 mix in the same seed. All transcendental functions use `StrictMath`.

### 7.8 Performance rules

These rules make the generator fast without changing its output. The same parameters and seed give the same tree with or without them.

- **One reusable voxel map per thread.** Wood and foliage candidates live in a flat byte array sized for the largest legal tree (33 × 33 columns), with one flag per Y layer that records whether the layer was ever written. Reads, the self-shading scan, the reach search and the final output walk skip untouched layers. The map is cleared by layer flags, not by refilling the array.
- **Bounding-box iteration, squared distances.** Each ellipsoid in 7.6 is tested over its integer bounding box using squared normalised distance; the 0.7 threshold becomes 0.49. Foliage uses no square roots and no trigonometry. Overlapping ellipsoids merge by keeping the smallest value per voxel.
- **Cheapest test first.** Arithmetic tests run before any world read (7.5), and each position's world read is done once and remembered in the map.
- **No allocation on the hot path.** Skeleton nodes, the search queue and the output lists are pooled per thread. The Minecraft side passes one mutable block position to vanilla's setters, which copy it.
- **Nothing carried between trees.** Buffers are reused; results are not.

---

## 8. The trunk placer

### 8.1 Registration and operation

`naturaltrees:weber_penn` is registered in `Registries.TRUNK_PLACER_TYPE`: through a `DeferredRegister` on NeoForge, and through an accessor for the type's private constructor on Fabric.

`placeTrunk`:

1. clears the foliage handoff (9.1) and draws the seed;
2. builds `canPlace` and `canLeaf`;
3. reads the foliage parameters from the species' foliage placer if it is `naturaltrees:skeleton`; otherwise no leaf voxels are computed;
4. runs `treecore` with the free height vanilla passed in;
5. sets dirt under every trunk column, as vanilla trunk placers do;
6. places each wood voxel through the supplied block setter, in output order: logs from `trunk_provider` with the computed axis; branches from `branch_provider` with the computed arms, waterlogged if the position held water;
7. stores the leaf voxels in the foliage handoff, together with the attachment objects it is about to return;
8. returns one `FoliageAttachment` per tip. The attachment position is **the block above the tip's wood voxel**, the convention vanilla trunk placers follow. `radiusOffset` is the tip's `radius_offset`. `doubleTrunk` is true for a tip on a 2×2 section.

### 8.2 Parameters

Angles are degrees. "±v" means a uniform random variation of that size, as in the paper.

**Top level**

| Field | Type, range | Default | Meaning |
|---|---|---|---|
| `base_height`, `height_rand_a`, `height_rand_b` | int; 0–32, 0–24, 0–24 | required | Vanilla's trunk placer height fields |
| `branch_provider` | block state provider | required | The branch block. Its state must have the six arm properties. |
| `shape` | `conical`, `spherical`, `hemispherical`, `cylindrical`, `tapered_cylindrical`, `flame`, `inverse_conical`, `tend_flame` | required | Crown envelope (the paper's shapes 0–7) |
| `base_size` | float, 0–0.9 | required | Fraction of the trunk, from the ground, that carries no limbs |
| `attraction_up` | float, −3 to 3 | 0 | Upward or downward bending of level 2+ stems |
| `twig_radius` | float, 0.1–0.5 | 0.25 | Radius of a stem that carries one tip |
| `pipe_exponent` | float, 1.5–3.5 | 2.0 | Exponent of the pipe model |
| `trunk_width_min`, `trunk_width_max` | int, 1–2 | 1, 2 | Clamp on the computed trunk width. Species grown from 2×2 saplings set the minimum to 2. |
| `trunk_leader` | boolean | false | Let the top of the trunk thin to branch blocks (7.2) |
| `max_radius` | int, 2–14 | required | Horizontal limit for wood, in blocks from the trunk origin |
| `foliage_margin` | int, 1–6 | 3 | Horizontal reach of the species' foliage beyond its wood |
| `max_tips` | int, 1–48 | required | Tip budget (7.3) |
| `trunk` | object | required | Trunk stem fields below |
| `levels` | list of 1–3 objects | required | One entry per level, starting at level 1 |

The codec rejects a file unless **`max_radius + foliage_margin ≤ 16`**, and reports both numbers.

**`trunk`**

| Field | Type, range | Meaning |
|---|---|---|
| `curve`, `curve_back`, `curve_v` | float | Total curvature, S-curve return, and random curvature over the stem |
| `curve_res` | int, 1–8 | Segments per stem |
| `base_splits` | int, 0–3 | Extra trunks that split off at the first segment |
| `seg_splits` | float, 0–2 | Average splits per segment |
| `split_angle`, `split_angle_v` | float | Angle of a split ±v |

**Each entry of `levels`** has `curve`, `curve_back`, `curve_v`, `curve_res`, `seg_splits`, `split_angle`, `split_angle_v` as above, plus:

| Field | Type, range | Meaning |
|---|---|---|
| `branches` | int, 0–32 | Children per parent stem, before the voxel-scale rules |
| `length`, `length_v` | float, 0–1 | Length as a fraction of the parent's, ±v, scaled by `shape` at level 1 |
| `down_angle`, `down_angle_v` | float | Angle from the parent's axis ±v. A negative `down_angle_v` varies the angle along the parent, as in the paper. |
| `rotate`, `rotate_v` | float | Rotation around the parent between successive children ±v. A negative `rotate` alternates sides, as in the paper. |
| `tip_radius_offset` | int, −2 to 1; default 0 | `radiusOffset` of this level's attachments. Read only by stock foliage placers (9.3). |

---

## 9. The foliage placer

### 9.1 Registration and operation

`naturaltrees:skeleton` is registered in `Registries.FOLIAGE_PLACER_TYPE`. It passes constant zero radius and offset to the vanilla base class; its shape comes entirely from 7.6.

Vanilla calls the trunk placer and then the foliage placer, once per returned attachment, consecutively on one thread. The two placers share a **foliage handoff**: a per-thread slot that holds the leaf voxels and the identity of the attachment objects the trunk placer returned.

- On the first attachment that belongs to the handoff, the foliage placer places every leaf voxel through vanilla's `tryPlaceLeaf`, then empties the handoff. Vanilla's helper applies the leaf provider, waterlogging and the replaceability test, and records each position as foliage, so distance baking and leaf-based decorators work as for any tree.
- On the other attachments of the same tree it does nothing.
- On an attachment that does not belong to the handoff, which happens when a species pairs this placer with a different trunk placer, it falls back to one cluster: steps 3–7 of 7.6 around the single wood voxel below the attachment, with radius `radius_tip`.

### 9.2 Parameters

| Field | Type, range | Default | Meaning |
|---|---|---|---|
| `foliage_start` | float, 0–1 | 0.5 | Where along a limb or twig the sleeve begins |
| `trunk_foliage` | float, 0–1 | 0.15 | Share of the trunk, from its top, that bears foliage |
| `radius_base`, `radius_tip` | float, 0.5–4 | 1.0, 2.2 | Sleeve radius where it begins and at the tip |
| `radius_v` | float, 0–0.5 | 0.2 | Per-stem variation of the radius |
| `flatten` | float, 0.3–1.5 | 0.7 | Vertical radius as a multiple of the horizontal |
| `lift` | float, −1 to 1 | 0.3 | Vertical shift of the sleeve, as a multiple of its radius |
| `density` | float, 0–1 | 0.8 | Chance that an outer-shell leaf is kept |
| `smother` | int, 0–16 | 4 | Most leaves of the same tree allowed above a leaf before it is discarded; 0 disables self-shading |
| `max_distance` | int, 1–6 | 5 | Greatest allowed leaf distance from wood |
| `max_leaves` | int, 1–4096 | required | Leaf budget per tree |

`foliage_margin` on the trunk placer should be at least `radius_tip × (1 + radius_v)`, rounded up. The trunk placer logs a warning once per species when it is not. Leaves are tested against the chunk area individually in any case, so a low margin flattens a crown at a chunk border; it never writes outside the area.

### 9.3 Stock foliage placers

A species may use a stock vanilla foliage placer in place of `naturaltrees:skeleton`. The tips are returned as ordinary attachments for that purpose, and `tip_radius_offset` sizes them per level. A stock blob of radius 2 on a single tip voxel puts its farthest corner exactly at vanilla's reach limit, so positive offsets are not advisable with blob placers.

---

## 10. Example species file

Everything outside `trunk_placer` and `foliage_placer` is taken from the vanilla file with the same ID. Values are placeholders until Phase 0 tuning.

```json
{
  "type": "minecraft:tree",
  "config": {
    "ignore_vines": true,
    "force_dirt": false,
    "minimum_size": { "type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1 },
    "dirt_provider": { "type": "minecraft:simple_state_provider", "state": { "Name": "minecraft:dirt" } },
    "trunk_provider": { "type": "minecraft:simple_state_provider",
      "state": { "Name": "minecraft:oak_log", "Properties": { "axis": "y" } } },
    "foliage_provider": { "type": "minecraft:simple_state_provider",
      "state": { "Name": "minecraft:oak_leaves",
                 "Properties": { "distance": "7", "persistent": "false", "waterlogged": "false" } } },

    "trunk_placer": {
      "type": "naturaltrees:weber_penn",
      "base_height": 7, "height_rand_a": 3, "height_rand_b": 0,
      "branch_provider": { "type": "minecraft:simple_state_provider",
        "state": { "Name": "naturaltrees:oak_branch" } },

      "shape": "spherical",
      "base_size": 0.35,
      "attraction_up": 0.3,
      "twig_radius": 0.25,
      "pipe_exponent": 2.0,
      "trunk_width_min": 1, "trunk_width_max": 1,
      "trunk_leader": false,
      "max_radius": 5,
      "foliage_margin": 3,
      "max_tips": 8,

      "trunk": { "curve": 0, "curve_back": 0, "curve_v": 15, "curve_res": 3,
                 "base_splits": 0, "seg_splits": 0.0, "split_angle": 0, "split_angle_v": 0 },
      "levels": [
        { "branches": 6, "length": 0.45, "length_v": 0.1,
          "down_angle": 55, "down_angle_v": -35, "rotate": 140, "rotate_v": 20,
          "curve": -20, "curve_back": 0, "curve_v": 40, "curve_res": 3,
          "seg_splits": 0.2, "split_angle": 30, "split_angle_v": 10 },
        { "branches": 2, "length": 0.4, "length_v": 0.1,
          "down_angle": 40, "down_angle_v": 10, "rotate": 140, "rotate_v": 30,
          "curve": 0, "curve_back": 0, "curve_v": 30, "curve_res": 2,
          "seg_splits": 0.0, "split_angle": 0, "split_angle_v": 0 }
      ]
    },

    "foliage_placer": {
      "type": "naturaltrees:skeleton",
      "foliage_start": 0.5,
      "trunk_foliage": 0.15,
      "radius_base": 1.0, "radius_tip": 2.2, "radius_v": 0.2,
      "flatten": 0.7, "lift": 0.3,
      "density": 0.8,
      "smother": 4,
      "max_distance": 5,
      "max_leaves": 250
    },

    "decorators": [ { "type": "minecraft:beehive", "probability": 0.002 } ]
  }
}
```

---

## 11. World generation data

### 11.1 The built-in datapack

All world generation JSON lives in a built-in datapack that both loaders register as **optional and enabled by default**. Disabling it returns tree generation to vanilla and leaves the blocks, recipes and felling available, which serves players who want only the blocks. Conflicts with other worldgen packs are resolved by pack order.

### 11.2 Every vanilla key keeps its vanilla role

The datapack supplies one file for each vanilla tree configured-feature ID, and each file is that tree's Natural Trees counterpart. The mod does not touch `TreeGrower`, the biome selector features or their weights.

The fancy oak therefore appears by exactly vanilla's mechanics: from one oak sapling in ten, from the flower variants of that roll, and in each biome at the weight its selector gives it. The same holds for every other variant vanilla has.

Families for oak and birch. Files within a family are identical except for their decorators, as vanilla's are. The key list is confirmed against the 1.21.1 data before Phase 2 (section 20, item 4).

| Family | Vanilla keys | Species |
|---|---|---|
| Oak | `oak`, `oak_bees_0002`, `oak_bees_002`, `oak_bees_005` | Oak: small, rounded crown |
| Fancy oak | `fancy_oak`, `fancy_oak_bees`, `fancy_oak_bees_0002`, `fancy_oak_bees_002`, `fancy_oak_bees_005` | Fancy oak: medium, broad and spreading, heavier limbs |
| Birch | `birch`, `birch_bees_0002`, `birch_bees_002`, `birch_bees_005` | Birch: small, slender, upright |
| Tall birch | `super_birch_bees`, `super_birch_bees_0002` | Tall birch: medium height, narrow |

Structures that place a vanilla key, such as village oaks, get that key's species.

### 11.3 Density

The datapack also supplies the **placed** features that biomes reference, such as `trees_birch_and_oak`, changing only their `count`. Starting point for a dense forest: 3–4 trees per chunk where vanilla places about 10. Final counts come from the measurement in section 15.

---

## 12. Saplings

Sapling growth is vanilla's and needs no mod code. `TreeGrower` looks up the same configured-feature keys, removes the sapling, and calls the feature; vanilla checks the trunk column and restores the sapling if the trunk does not fit; the trunk placer truncates any limb that is blocked, and the foliage follows what was placed. A sapling next to a wall or another tree grows a lopsided tree. Bone meal follows the same path.

A sapling grows whatever its key's species file describes. The largest single-sapling species in this version is the fancy oak, a medium tree of well under a thousand blocks, placed within one tick as vanilla's is. Species grown from 2×2 saplings set `trunk_width_min` to 2 and use vanilla's north-west origin convention (Phase 3).

---

## 13. Chunk boundary

During world generation a feature may write into the decorating chunk and its eight neighbours. A trunk anywhere in the decorating chunk is at least 16 blocks from the edge of that area in every direction.

- The codec rule `max_radius + foliage_margin ≤ 16` guarantees that a tree fits.
- Test 2 of `canPlace` keeps wood inside `max_radius`.
- Test 1 of `canPlace`, and the same test in `canLeaf`, cover placement modifiers that move a tree's origin away from the decorating chunk. No block is ever read or written outside the area.

The widest possible crown is 32 blocks.

---

## 14. Tree felling

### 14.1 Default behaviour

A branch supports its leaves for as long as it exists. When a player cuts only the trunk, the limbs and their foliage remain in place, as the limbs of a vanilla fancy oak do. This is the specified behaviour with felling disabled.

### 14.2 Third-party felling mods

Branches carry the logs block tag, so tag-driven felling mods are expected to recognise them. Phase 4 tests the three or four most used felling mods for two likely problems: default size caps below a large tree's log count, and the assumption that every log is a full cube. When a known felling mod is present, the built-in feature logs a notice and stays off unless `felling.force` is set.

### 14.3 Built-in felling

Server config. **Disabled by default.**

| Key | Default | Meaning |
|---|---|---|
| `felling.enabled` | `false` | Master switch |
| `felling.force` | `false` | Stay enabled when a known felling mod is present |
| `felling.max_blocks` | 512 | Hard cap on one felling |
| `felling.min_leaves` | 8 | Non-persistent leaves the found wood must touch |
| `felling.blocks_per_tick` | 32 | Pacing |

- **Trigger:** a player breaks a log or a branch with an axe while not sneaking.
- **Search follows the tree's structure.** From a log, the search moves to adjacent logs (26 neighbours, none below the broken block) and to adjacent branches that have an arm pointing back at it. From a branch, it moves only along the branch's arms. Two trees whose crowns touch are not joined unless their branches are actually connected.
- **Guards:** `max_blocks`, and `min_leaves`. Player-placed leaves are persistent, so builds are never felled.
- **Cost and pacing:** one durability per block. Blocks break over several ticks and drop where they were. Leaves are left to decay the vanilla way.

---

## 15. Performance

Trees are placed on vanilla's write path. Cost follows block counts, and block counts are data: `max_leaves` and `max_tips` per species, `count` per biome.

**Per-tree budgets (provisional)**

| Size class | Height | `max_radius` | Wood voxels | `max_tips` | `max_leaves` | Trunk from the pipe model |
|---|---|---|---|---|---|---|
| Small | 7–10 | 5 | ≤ 50 | 8 | 250 | 1×1 |
| Medium | 10–15 | 8 | ≤ 140 | 14 | 600 | 1×1, log-based main limbs |
| Large | 15–24 | 12 | ≤ 400 | 28 | 1,200 | 2×2 narrowing to 1×1 |

For scale: a vanilla oak is about 5 logs and 55 leaves, a fancy oak a few dozen logs and 300–500 leaves, and a dense vanilla forest chunk holds 8–10 trees, on the order of 1,000 leaves. At 3–4 trees per chunk, mostly small with the occasional medium, the mod is expected near one to two times vanilla's leaf volume.

`treecore` target: under 1 ms for a medium tree, wood and foliage together, measured with JMH.

**Measurement (Phase 2).** One table, one reference machine, recorded with its specification:

| Measured | Method |
|---|---|
| Vanilla and modded chunk time per generation stage, on a forest-only and a mixed region | Chunky pre-generation at a fixed seed; spark and misode's `worldgen-profiling` |
| Wood voxels, tips and leaves per tree, per species | `/naturaltrees stats` over 1,000 seeds |
| Client mean and 1% frame time on a scripted forest flight, with the vanilla renderer and with Sodium | Fixed render distance and settings |
| The three above at 1×, 1.5× and 2× the candidate density | Placed-feature `count` |

**Acceptance.** Working ceiling: 15% total chunk time on the forest region and 10% mean frame time. Above it, lower `count` first, then `smother`, then `max_leaves`, then `max_tips`. The measured figures are published in the mod description.

**Known costs.** Leaves dominate both server light time and client geometry; branch faces are minor by comparison. Self-shading (7.6) works on exactly this: stacked leaf layers are what the light engine and the renderer pay most for. Canopy chunk sections hold a few dozen distinct branch states and exceed the 16-state palette, so they store 5–6 bits per block where a vanilla canopy stores 4.

---

## 16. Commands and config

- `/naturaltrees place <configured_feature> [seed]` places one tree at the player's position.
- `/naturaltrees grid <configured_feature> <n>` places a row of `n` trees at consecutive seeds, for screenshots.
- `/naturaltrees stats <configured_feature> [seeds]` reports height, crown radius, trunk width, wood voxels by kind, tips, truncated stems, leaves, and leaves discarded by self-shading, by reach and by budget, averaged over the given number of seeds.

Commands require permission level 2.

Config is server-side and contains only the `felling.*` keys. Everything that affects generation is data; changing the datapack mid-world affects new chunks only, as with any worldgen pack.

---

## 17. Tuning tool

`tools/viewer` is a developer tool and is not part of the mod jar. It embeds `treecore` behind the JDK's `HttpServer` and serves a static Three.js page.

- `POST /generate` takes the `trunk_placer` and `foliage_placer` objects, a height and a seed, and returns a binary voxel buffer. Generation takes about a millisecond, so every slider change is a fresh request.
- Wood and foliage are both exact, because both come from the real generator. What the tool cannot show is the world: terrain, neighbouring trees and decorators. `/naturaltrees grid` shows those.
- Features: parameter sliders with the ranges of 8.2 and 9.2, a seed scrubber, a seed grid, a load and radius overlay for the pipe model, the counts from `/naturaltrees stats`, and export of both placer objects.

This is the minimum needed to tune species. The full editor is planned as future work (section 22, items 1 and 2).

---

## 18. Roadmap

**Phase 0 — Spike.** `treecore` complete: skeleton, tip budget, pipe model, both rasterisers and the 2×2 section, arms, truncation, foliage. The viewer. Oak, fancy oak and birch.
*Exit:* a seed grid of each that looks better than vanilla to someone who has not read this document; defaults chosen for `twig_radius`, `pipe_exponent`, `smother` and the other foliage parameters; the species that use `trunk_leader` chosen; a test species with 28 tips shows a 2×2 trunk that narrows cleanly; block counts inside the section 15 budgets.

**Phase 1 — Block and placers in game.** The branch block with states, models, shapes, tags, loot, recipes, fuel, flammability, shears, stripping and waterlogging, on both loaders. Both placer types and the foliage handoff, on both loaders. `/naturaltrees place` and `grid`. The name and slug reserved on Modrinth and CurseForge.
*Exit:* section 20 items 1–3 and 5–14 confirmed; a sapling-grown tree and a generated tree both keep every leaf after several in-game days; no far-chunk messages over a 1,000-chunk pre-generation.

**Phase 2 — Oak and birch world generation.** The built-in datapack with the keys of 11.2 and the placed-feature counts. The measurement table.
*Exit:* section 20 item 4 confirmed; table filled; density set from it; results inside the working ceiling, or the ceiling consciously revised.

**Phase 3 — Remaining overworld woods.** Spruce and pine as conifer species; acacia; cherry; jungle; swamp oak; mangrove with its vanilla root placer and decorators; dark oak and the mega species from 2×2 saplings with `trunk_width_min` 2.

**Phase 4 — Felling and compatibility.** Built-in felling. Tests against common felling mods. Branch blocks for modded woods, through an Every Compat module or addon mods that instantiate `BranchBlock`.

**Phase 5 — Adoption and ports.** Species packs for Terralith and Biomes O' Plenty, whose trees are ordinary `minecraft:tree` files under their own IDs and need only the two placers swapped. Newer Minecraft versions.

---

## 19. Risks

| Risk | Severity | Response |
|---|---|---|
| The foliage algorithm needs more than parameter tuning to look right | High | Phase 0 exists to find out, in the viewer, before any game code depends on it. The algorithm lives entirely in `treecore`. |
| A mod changes how `TreeFeature` calls the placers, breaking the foliage handoff | Medium | Attachment identity check; fallback cluster per attachment, so a tree always gets leaves. |
| Limbs left in the air after a trunk is cut annoy survival players | Medium | Specified behaviour (14.1). Optional felling; third-party felling mods. |
| Chunk time or frame time regression | Medium | Budgets are data. Measure in Phase 2; tune `count`, then `smother`, then `max_leaves`, then `max_tips`. |
| Another datapack supplies the same vanilla keys | Medium | The built-in pack is optional; pack order decides. Documented for users. |
| A 2×2 trunk from a single sapling overhangs neighbouring blocks | Low | `trunk_width_max` is 1 for every single-sapling species; blocked columns fall back to 1×1. |
| Writes outside the 3×3 chunk area | Low | Codec rule, `max_radius` test, area tests in `canPlace` and `canLeaf`. |
| Felling runs into a neighbouring tree | Low | The search follows arms, not adjacency; block cap. |
| Players expect branches to make charcoal or fit "any log" recipes | Low | Deliberate (6.5); stated in the mod description. |
| The stacked-logs recipe collides with guita's Branches when both are installed | Low | Detect that mod and disable the recipe. |
| A vanilla behaviour assumed here is wrong | Medium | Section 20, checked in Phase 1 before dependent work. |

---

## 20. Assumptions to verify

These statements about Minecraft 1.21.1 are made from memory. Each is confirmed against the sources before work that depends on it.

1. `TrunkPlacer.placeTrunk` receives a level reader, a block setter, a `RandomSource`, the free height, the origin and the `TreeConfiguration`, and returns a list of `FoliageAttachment`. Any block state may be placed through the setter.
2. Positions placed through the trunk setter seed `TreeFeature.updateLeaves` as distance-0 sources regardless of tag, and positions placed through the foliage setter receive baked distances.
3. `LeavesBlock` reads the `#minecraft:logs` **block** tag at runtime, so a leaf next to a branch resolves to `distance = 1`.
4. The exact configured-feature keys referenced by every `TreeGrower`, by village and other structure placements, and by every biome selector.
5. Vanilla plank recipes read per-wood **item** tags, so a branch outside them has exactly one plank recipe.
6. The full set of block tags that vanilla logs carry.
7. Fabric's strippable-block registry requires an `axis` property, so the branch needs its own hook.
8. The beehive decorator behaves acceptably when the log list it receives contains branches.
9. The write radius at the `FEATURES` step is one chunk, and `WorldGenRegion.getCenter()` returns the decorating chunk.
10. Generated arm states pass through `updateShapeAtEdge` and sapling-path placement unchanged; `#minecraft:replaceable_by_trees` contains water.
11. `TreeFeature` calls the trunk placer and then the foliage placer consecutively on one thread, and passes the foliage placer the same attachment objects the trunk placer returned.
12. A custom `FoliagePlacer` may pass constant radius and offset to its base class and place leaves at arbitrary positions through `tryPlaceLeaf`.
13. `TrunkPlacer.setDirtAt` is usable from a custom placer, and vanilla's 2×2 trees occupy the origin column and the columns at +x, +z and +x+z.
14. Vanilla's trunk and foliage setters copy the position they are given, so a single mutable position may be passed for every block.

---

## 21. Open questions

No design question is open. The mod ID `naturaltrees` and the command root `/naturaltrees` are fixed.

Phase 0 settles, by tuning in the viewer and not by design: the defaults for `twig_radius`, `pipe_exponent`, `smother` and the other foliage parameters, and which species set `trunk_leader`.

---

## 22. Future work

Planned or wanted after version 1. None of it is required by the version 1 design, and none of it is ruled out by it. Each item notes what it needs, so it can be picked up without rediscovery.

**Tools**

| # | Item | What it adds | What it needs |
|---|---|---|---|
| 1 | **Full web editor** | Grows the tuning tool into a species editor: grouped parameter panels with per-field reset and change highlighting; fixed-seed mode with a next-seed key; texture pickers per block role (pick a vanilla texture or upload one); branch blocks rendered from their real block-model JSON; JSON import as well as export; whole species files, not only the two placer objects. | Front-end work only. Wood and foliage already come from `treecore`, so the preview is exact. |
| 2 | **In-game editor mode** | `/naturaltrees editor` from a development instance serves the same page with every loaded mod's blocks and textures in the pickers, and places the edited tree in the world to show terrain and decorators. | The editor server as an optional, dev-only module. It must never be active in a normal install. |
| 3 | **Published JSON Schema** | Validation and completion for both placer objects in IDEs and in the editor. | Generated from the codecs so the two cannot drift. |
| 4 | **Config screens** | Native NeoForge config screen and a hand-written Fabric screen for `felling.*`. | Nothing special. |

**Tree features**

| # | Item | What it adds | What it needs |
|---|---|---|---|
| 5 | **Roots** | Flared or surface roots on medium and large species: a short outward-and-down stem level at the trunk base, placed as horizontal logs and sized by the pipe model. | A `roots` object in the trunk placer, or a root placer beside vanilla's `root_placer` slot. Must respect `canPlace`. |
| 6 | **Bark variation** | Coherent patches of stripped log on trunks, and `wood` blocks where an end face would show. | Partly available now as data: `trunk_provider` accepts vanilla's weighted and noise state providers. End-face detection needs placer support. |
| 7 | **Mixed foliage** | A second leaf block scattered through the crown for dappled colour. | Available now as data through a weighted `foliage_provider`. Needs only testing and a documented example. |
| 8 | **Several variants per key** | More than one species behind a vanilla key, picked by weight. | Data only: the key becomes a `minecraft:random_selector` over several tree features. Check what saplings and mods that read `TreeConfiguration` do with it. |
| 9 | **Crowns wider than 32 blocks** | Giant trees up to 40 wide. | An inset placement modifier that keeps the trunk in the central 8×8 of its chunk, which raises the safe radius to 20, and a codec rule tied to its presence. |
| 10 | **Thicker wood** | 3×3 trunks for giants, and 2×2 main limbs. | A third radius threshold in 7.2; a rasteriser for thick non-vertical stems. |
| 11 | **Foliage refinements** | Gaps between sleeves for a layered look; denser foliage on the sunlit side; hanging foliage for weeping forms. | New terms in 7.6. The reach search already guarantees validity, so these are shape work only. |
| 12 | **More wood types** | Crimson and warped branches for builders; pale oak and later woods with the ports; bamboo if a use appears. | Blocks only. Nether fungi are not `minecraft:tree` features, so generation is a separate question. |
| 13 | **Felling options** | Fast leaf decay after a felling; per-tool rules; a client hint showing what will fall. | Built-in felling (Phase 4) first. |
| 14 | **Crown-aware spacing** | Trees placed so that crowns touch instead of interpenetrating, by packing circles of each species' crown radius, as Dynamic Trees does. Fewer truncated limbs, fewer wasted leaves, a more natural forest floor. | A placement modifier that must give the same layout whichever chunk generates first: either a position hash evaluated over neighbouring chunks, or stored per-chunk layouts. Replaces `count` for the biomes that use it. |

**Compatibility and reach**

| # | Item | What it adds | What it needs |
|---|---|---|---|
| 15 | **Runtime adoption of other mods' trees** | An opt-in rule that swaps the placers of any `minecraft:tree` config at load, keeping its blocks and decorators. Covers mods with many tree files without a hand-made pack for each. | A mapping from log block to branch block; a deny-list; off by default. |
| 16 | **Modded biomes** | The mod's trees in biomes that other mods add. | Loader-native biome modifiers that replace a tree placed feature at the same index, never appending one, so no feature-order cycle arises. |
| 17 | **Addon API** | A small stable Java surface for wood mods: `BranchBlock`, a wood-type binding, and a registration hook on each loader. Semantic versioning. | Only once a second consumer exists. Until then the datapack format is the interface. |
| 18 | **Player-facing density and size settings** | A density multiplier and a sapling size limit in config, for players who will not edit a datapack. | Generation-affecting config must be snapshotted per world, so that existing worlds do not change when the config does. |
| 19 | **Ports** | Newer Minecraft versions from one codebase. | Stonecutter or an equivalent multi-version setup; the newer toolchain and Java version those releases require. |
| 20 | **Determinism hardening** | A CI check that the generator's voxel output hashes identically on x86-64 and arm64. | `treecore` is already pure Java on `StrictMath`; this adds only the second runner. |

---

## References

- Weber & Penn 1995: https://dl.acm.org/doi/10.1145/218380.218427 ; https://courses.cs.duke.edu/cps124/fall01/resources/p119-weber.pdf
- guita's Branches: https://www.curseforge.com/minecraft/mc-mods/branches
- Dynamic Trees, world generation and leaves properties: https://github.com/DynamicTreesTeam/DynamicTrees/wiki/World-generation ; https://github.com/DynamicTreesTeam/DynamicTrees/wiki/Tree-Packs:-Leaves-Properties
- Configured and placed features: https://minecraft.wiki/w/Configured_feature ; https://minecraft.wiki/w/Placed_feature
- Fabric trees and placer registration: https://wiki.fabricmc.net/tutorial:trees
- Far-chunk writes: https://bugs.mojang.com/browse/MC-248758
- Feature order cycle: https://modrinth.com/mod/feature-recycler
- Profiling: https://github.com/misode/worldgen-profiling
- MultiLoader-Template: https://github.com/jaredlll08/MultiLoader-Template
- JDK HTTP server: https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html
- Three.js InstancedMesh: https://threejs.org/docs/#api/en/objects/InstancedMesh
