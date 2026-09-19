# Plan — Phase 0: Spike

Spec section 18, Phase 0: `treecore` complete (skeleton, tip budget, pipe model, both rasterisers and the
2×2 section, arms, truncation, foliage), the viewer, and the species oak, fancy oak and birch.

**Status: approved 2026-09-19, in progress.** Done: T1–T13 (species are first drafts). Waiting for the human:
manual tests P0-1 to P0-4. Then T14 (tuning). See `progress.md` and `phase-0-results.md`.

## Exit criteria (spec 18) and who judges them

| Criterion | Judged by |
|---|---|
| A seed grid of oak, fancy oak and birch looks better than vanilla to someone who has not read the spec | Human, in the viewer (manual tests P0-2, P0-3) |
| Defaults chosen for `twig_radius`, `pipe_exponent`, `smother` and the other foliage parameters | Human and agent; recorded in `docs/phase-0-results.md` with proposed spec changes |
| The species that use `trunk_leader` chosen | Same |
| A test species with 28 tips shows a 2×2 trunk that narrows cleanly | Agent by test (T5, T6), human by eye (P0-4) |
| Block counts inside the section 15 budgets | Agent, by a test over 1,000 seeds per species (T12) |

## Decisions

The human delegated these on 2026-09-19 ("just do whatever is best").

| # | Decision | Taken |
|---|---|---|
| D1 | Three.js in the viewer | Loaded from a CDN by the static page. Nothing is added to the repository; tuning needs internet. |
| D2 | JSON in the viewer | A small hand-written reader inside `tools/viewer`. No dependency anywhere. |
| D3 | JMH | A separate `jmh` source set of `treecore` using only the JMH libraries, no extra Gradle plugin; nothing from it reaches the mod jars. |
| D4 | Spec gaps Q4–Q8 | Decided as recorded in `docs/questions.md`. |

## Design in brief

Package `kz.nursultan.naturaltrees.treecore`. No Minecraft, no dependencies, `StrictMath` only, no
`java.util.Random`, no hash-ordered collections on any path that affects output.

```
TreeGenerator.generate(TrunkParams, FoliageParams or null, seed, height, PlacementLimits, WorldRead canPlaceWorld, WorldRead canLeafWorld) -> TreeResult
```

- Coordinates are relative to the trunk origin. `PlacementLimits` carries the optional chunk area and the
  margin; `treecore` itself composes the three tests of spec 7.5 in order (area, `max_radius`, world read),
  so the order is provable in a pure-Java test and the Minecraft side supplies only the world read.
- `TreeResult`: wood voxels parent-first (`LOG` + axis, or `BRANCH` + arm mask), leaf voxels nearest first,
  tips with `radius_offset` and the 2×2 flag, and counters for `/naturaltrees stats` (spec 16). A debug
  channel carries per-voxel load and radius for the viewer's overlay (spec 17).
- Work is done in two steps on purpose: first a plain, readable implementation, then golden output hashes
  are recorded, then the performance rules of spec 7.8 are applied with the hashes unchanged.

## Tasks

Each task is one or a few small commits. "Test" means a JUnit test in `treecore` unless stated.

| # | Task | Spec | Acceptance check |
|---|---|---|---|
| T1 | **Parameters.** Immutable `TrunkParams`, `StemParams`, `LevelParams`, `FoliageParams`, `Shape`; defaults and ranges of 8.2 and 9.2; the rule `max_radius + foliage_margin ≤ 16` with both numbers in the message. | 8.2, 9.2 | Tests: every range boundary accepted, every value just outside rejected with the field name; defaults equal the spec's tables. |
| T2 | **Maths.** 3-vector and rotation-frame helpers on `double`, degrees in, `StrictMath` inside. | 7.7 | Tests: frames stay orthonormal after 1,000 rotations; known rotations give known vectors. |
| T3 | **Skeleton.** Curved stems with `curve`, `curve_back`, `curve_v` and the helix mode; splits with error diffusion, `base_splits`, split angle and its compensation; children: count, length with `ShapeRatio` 0–7, down angle (both modes), rotation (both modes); `attraction_up` for level 2+. Readings from `paper-notes.md`. | 7.1, paper §4.1–4.3, 4.8 | Tests: `ShapeRatio` table values; with all variation at 0 the skeleton equals hand-computed positions (straight trunk, child count, 140° spiral, alternating mode); `seg_splits` 1.2 over many segments averages 1.2 with no run of extra splits; `(seg_splits+1)^(curve_res−1)` stems for integer splits; helix segments all at the stated declination; same seed same skeleton, different seed different. |
| T4 | **Voxel-scale rules and tip budget.** Short-stem rule and `curve_res` cap (Q4); tips; budget removal order (Q7). | 7.3 | Tests: no stem under 2 blocks; effective `curve_res` ≤ length/2; stems ≤ `max_tips` always; removal order matches a hand-built example; a removed stem's subtree is gone. |
| T5 | **Pipe model.** Load per point, radius, kind per point; level-0 rules, `trunk_leader`, width clamp, limbs capped at 1×1. | 7.2 | Tests: the table (load 1–3 branch, 4–15 log, 16+ 2×2) with defaults; load and kind never increase toward a tip on any stem over 1,000 seeds; trunk never below a log without `trunk_leader`; with it, the top is branch where load < 4; `trunk_width_max` 1 never gives 2×2; `trunk_width_min` 2 always does; the three worked examples of 7.2 (8, 14, 28 tips). |
| T6 | **Rasterisers and arms.** 26-connected DDA for logs with the axis rule; 2×2 sections per Y layer with the +x/+z convention and the outer-face rule for children; face-connected branches; step-down joints; arm masks; merge rule (`LOG` wins, masks OR-ed). | 7.4 | Tests: consecutive branch voxels share a face; consecutive log voxels are 26-adjacent; log axis on straight and exactly diagonal segments; arms reciprocal; two unrelated stems passing side by side have no arm between them; each child's first voxel is face-adjacent to a parent voxel; 2×2 footprint and its narrowing column. |
| T7 | **Emission, obstruction, limits.** Parent-first emission; truncation at the first `false`; children beyond a cut dropped; the 2-voxel tip rule; 2×2 base fallback; one child per face (Q5); predicate composition and the once-only world read. | 7.5, 7.8 | Tests: with random blocked regions over 1,000 seeds every wood voxel is connected to the trunk base (independent flood fill in the test); a recording test double shows zero world reads outside the area or beyond `max_radius`, and at most one read per position; wood never beyond `max_radius`; blocked extra base column gives a 1×1 trunk even with `trunk_width_min` 2. |
| T8 | **Foliage.** Bearing wood, sleeve radius, ellipsoid candidates, edge thinning by position hash, self-shading, reach search, leaf budget, ordering. Leaves clipped to the map (Q8). | 7.6 | Tests: every leaf within `max_distance` face-steps of wood by an independent BFS in the test; leaves ≤ `max_leaves`; no leaf has more than `smother` leaves of the tree above it in its column at the moment step 5 ran, and `smother` 0 disables the rule; no leaf where wood is; no leaf hangs from a truncated limb; `density` 1 keeps every candidate; output sorted by distance. |
| T9 | **Determinism and independence.** Canonical hash over the ordered result. | 2, 7.7 | Tests: same parameters and seed give an identical hash; 100 different seeds give 100 different hashes; tree B generated after tree A equals tree B generated alone, on the same generator instance and on a fresh one; results identical across threads. Golden hashes for 3 species × 20 seeds are committed at this point. |
| T10 | **Dump helper** (test code only): PNG side, front and top projections, an isometric view, and text slices; a grid mode for many seeds. | brief §5 | I can open the PNGs of any species and seed; used for every tuning step below. |
| T11 | **Performance pass.** One reusable voxel map per thread with layer flags; bounding-box iteration on squared distances; pooled nodes, queue and lists; no allocation on the hot path. JMH benchmark (D3). | 7.8, 15 | Golden hashes from T9 unchanged; JMH: medium tree under 1 ms on this machine, number recorded with the machine's specification; an allocation check in the benchmark shows a steady state without garbage. |
| T12 | **Species.** Oak (small), fancy oak (medium), birch (small), tall birch (medium), and the 28-tip test species. Stored as JSON, the exact `trunk_placer` and `foliage_placer` objects that Phase 2 will paste into the datapack. | 10, 11.2, 15 | Test (in `tools/viewer`, which can read JSON): over 1,000 seeds each species stays inside its size class budget of section 15 (wood voxels, tips, leaves, `max_radius`); the 28-tip species has a 2×2 lower trunk narrowing to 1×1 in every seed where it reaches 16 tips. |
| T13 | **Viewer** (`tools/viewer`, not in any mod jar). JDK `HttpServer`; `POST /generate` returning a binary voxel buffer; static Three.js page with instanced cubes; sliders with the ranges of 8.2 and 9.2; seed scrubber; seed grid; load and radius overlay; the stats counts; export of both placer objects. | 17 | `./gradlew :tools:viewer:run` starts it; a test posts a species and decodes the buffer to the same voxels `treecore` returns directly; manual test P0-1. |
| T14 | **Tuning.** Iterate on the species and on the defaults with T10 and T13. | 18, 21 | Manual tests P0-2 to P0-4; results and proposed spec edits in `docs/phase-0-results.md`. |

Order: T1 → T2 → T3 → T4 → T5 → T6 → T7 → T8 → T9 → T10 (the dump helper is started early, alongside T3,
and grows with each stage) → T12 first draft → T13 → T11 → T14.

## Manual tests you will be asked to run

```
Manual test P0-1 — the viewer runs
1. In a terminal at the project root: ./gradlew :tools:viewer:run
2. Open the address it prints (http://localhost:<port>) in a browser.
3. Expect: an oak appears; dragging the seed scrubber changes the tree at once; moving any slider regenerates it.
4. Send back: a screenshot, and the terminal output if anything failed.

Manual test P0-2 — seed grids
1. In the viewer choose "oak", then "seed grid". Repeat for "fancy oak" and "birch".
2. Look for: trunks and limbs that read as one tree; no floating wood; no bare sticks poking far out of the crown;
   crowns that differ from seed to seed without any looking broken.
3. Send back: one screenshot per species, and the seed of any tree that looks wrong.

Manual test P0-3 — the outsider check (spec 18 exit criterion)
1. Show the three grids next to vanilla trees to someone who has not read the spec. Ask which look better.
2. Send back: the answer, and any remark they made.

Manual test P0-4 — the 28-tip species
1. In the viewer choose "test-28-tips" and switch on the load overlay.
2. Expect: a 2×2 lower trunk that narrows to 1×1 where the overlay shows a load below 16; limbs that start as logs
   and turn into branch blocks after they fork.
3. Send back: a screenshot from the side.
```

## Out of scope for this phase

Anything that touches Minecraft: the branch block, the placers, codecs, commands, the datapack. No item of
spec section 22. No config. The viewer gets the features spec 17 lists and nothing from section 22 items 1–2.
