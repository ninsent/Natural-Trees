# Phase 0 results

Working notes of Phase 0 (spec section 18). Filled in as the tasks finish; the defaults and the species that
use `trunk_leader` are settled at the end of tuning (T14) and turn into proposed spec edits here.

## Performance (spec 7.8 and section 15)

Target: under 1 ms for a medium tree, wood and foliage together, measured with JMH.

Measured with `./gradlew :treecore:jmh` (JMH 1.37, average time, GC profiler) on the human's machine:
Apple M5 Pro (18 cores), 24 GB, macOS 27.0, Microsoft OpenJDK 21.0.12, one thread.

| Tree | Tips | Leaves budget | Height | Time per tree | Allocated per tree |
|---|---|---|---|---|---|
| Small | 8 | 250 | 9 | about 7 µs | 0 B |
| Medium | 14 | 600 | 13 | about 18 µs | 0 B |
| Large | 28 | 1,200 | 20 | about 58 µs | 0 B |

The medium tree is about 50 times inside the target. The generator is therefore not what world generation
will pay for; the block writes and the light engine are (section 15), which Phase 2 measures.

Notes:

- The flat voxel map with layer flags, the squared-distance ellipsoids, the one world read per position and
  the pooled skeleton, queue and lists (spec 7.8) were built in from the start, not added afterwards.
- The first JMH run showed 1 to 9 KB allocated per tree. None of it was the generator's: `StrictMath.sin` and
  `StrictMath.cos` allocate a temporary array inside the JDK when their argument exceeds π/4. `Frame` and
  `Shape` now bring every angle to within 45 degrees of a quarter turn first, which is exact in degrees, so
  the generator allocates nothing once its buffers have grown. All 60 golden hashes were unchanged by this.

## Block counts (section 15)

`ViewerTest.speciesStayInsideTheirSizeClassBudgets` generates every shipped species file at 1,000 seeds over
its whole height range and checks wood voxels, tips, leaves and `max_radius` against its size class, and that
`foliage_margin` covers the foliage. The 28-tip test species has a 2×2 lower trunk in all 1,000 seeds and its
trunk tip is never on the 2×2 section, so it always narrows to 1×1.

## Tuning log (T14)

| Round | What was seen | What changed |
|---|---|---|
| 1 | The example oak of spec 10 keeps about 4 limbs at height 9: with a spherical crown the top and bottom limbs come out under 2 blocks and are dropped (spec 7.3). | Oak uses `hemispherical`, `length` 0.55, `base_size` 0.4, `trunk_foliage` 0.3. |
| 2 | Tall birch looked like a lollipop: a round ball on a long bare pole. | Both birches: shorter and more upright limbs (`down_angle` 38–40), lower crown base, narrower and taller sleeves (`radius_tip` 1.6, `flatten` 1.2, `lift` 0.1), `max_radius` 4. |
| 2 | Small birch, 2 of 6 seeds: the whole trunk was branch blocks. With `trunk_leader` a tree with three tips or fewer has a load below 4 everywhere. | Generator rule, recorded under Q6: with `trunk_leader` the bare part of the trunk is never thinner than a log. Small birch `length` 0.45 so that more limbs survive the 2-block rule. |
| 3 | Oak at height 7: the lowest limbs went out level and their foliage hung almost to the ground, so the tree read as a bush. | Oak: `base_size` 0.45, `down_angle` 50 with `down_angle_v` −20, `foliage_start` 0.5, `lift` 0.4. Fancy oak: `down_angle` 58 with `down_angle_v` −27, `lift` 0.4. |

| 4 | **The look session (2026-09-19).** Against the human's reference builds every species was vanilla-sized (7–15 blocks), so a limb carried 1–3 tips and was branch blocks only, and sleeves of radius 2.3–2.9 starting at 0.3–0.5 of a limb merged into one solid blob: oak, swamp oak, cherry and acacia all read as "a blob on a stick", the conifers as solid cones. | All 16 species retuned, 2–3 times taller, with a second level everywhere. See "Round 4 in detail" below. Density lowered from a third to a fifth of vanilla's count. Size classes revised. |

### Round 4 in detail

What produces the look now, so that later rounds do not rediscover it:

- **Log limbs come from tips, not from a thickness knob.** A limb is logs where its load makes the radius 0.5 or more.
  `twig_radius` 0.3 puts that at 3 tips (0.25 needs 4), so a limb with two or more surviving twigs starts as logs and
  thins to branch blocks after its forks. Every broadleaf, the pine and the mega pine use 0.3. `max_tips` is 28–48
  so that the twigs survive the tip budget.
- **Separate foliage masses** need three things together: `foliage_start` 0.55–0.72 (only the outer part of a limb
  bears), a smaller and flatter sleeve, and limbs far enough apart, which only a taller tree gives.
- **Spruce and mega spruce.** Tiers: about 18 level boughs (`down_angle` 92–95, slight upward `curve`) with three
  alternating twigs (`rotate` −90), flat sleeves (`flatten` 0.5) that narrow toward the bough tip (`radius_base` 2.0 →
  `radius_tip` 1.2), `conical`. The top limbs fall under 2 blocks and are dropped, so the pointed top is the
  **trunk's** sleeve (`trunk_foliage` 0.2–0.25) on a branch-block leader; with `radius_tip` 1.2 the last layers are a
  plus shape, which reads as a spire. **`smother` must be off or high for a tiered conifer** (spruce 0, mega spruce 6):
  at 3 it removed the lower tiers, because every tier has tiers above it in the same columns.
  `tapered_cylindrical` was tried and rejected: a column with no point.
- **Pine and mega pine.** A bare pole (`base_size` 0.5–0.55), `inverse_conical` so the longest limbs are at the top,
  6–10 limbs at 75° curving upward, four alternating twigs, flat pads (`flatten` 0.4, `lift` 0.4, `foliage_start` 0.55).
  Mega pine without `trunk_leader` ended in a blunt 2×2 stub above the head; with it the trunk narrows in order.
- **Oak, fancy oak, dark oak.** `down_angle` 52–58 with `down_angle_v` −15 and `curve` −20 to −35: the low limbs leave
  at about 70° from vertical and turn upward. **A negative `down_angle_v` makes the LOW limbs more level, not less**
  (paper-notes 13): with 65 and −30 the fancy oak's lowest limbs left at 95° and hung foliage to the ground.
  Oak `spherical`, fancy oak and dark oak `hemispherical` (long low limbs, the veteran's spread).
- **Birches.** `tapered_cylindrical`, short limbs (`length` 0.2–0.24) at 45–48°, two twigs, small tall tufts
  (`radius_tip` 1.5–1.6, `flatten` 0.85, `density` 0.7), a gently curved trunk that sometimes forks
  (`trunk.seg_splits` 0.25 at 18–20°). With `trunk_leader` and `twig_radius` 0.25 the log trunk ended at half height
  and the rest was branch blocks; 0.3 keeps the white trunk visible well into the crown.
- **Acacia.** `base_splits` 1 at 35° on a 3-segment trunk (the fork sits at a third of the height; with 4 segments and
  `curve_v` 50 some trees lay down), `inverse_conical`, long limbs (0.8) at 50° that curve toward level, plates of
  `flatten` 0.3 with `radius_tip` 3.2 on the last 28% of a limb.
- **Cherry.** Acacia's fork with `hemispherical`, `flatten` 0.55 and a slightly negative `attraction_up`. `curve` 25–35
  on the limbs dropped foliage to the ground; 10 does not.
- **Jungle, mega jungle.** A pole with 62% bare trunk; the mega jungle is an emergent umbrella, 32–44 tall.
- **Swamp oak, mangroves.** Low and wide with a slight droop (`attraction_up` −0.2 to −0.3, `lift` 0.1–0.15);
  `base_size` 0.45–0.5 keeps the foliage off the ground in all but 1% of trees.

Helpers added: the dump now walks the whole height range across its seeds (the old formula gave every tree the base
height whenever `height_rand_a` + 1 divided 7) and prints the lowest leaf and the widest leaf;
`./gradlew :tools:viewer:speciesStats` prints the table below.

### Block counts after round 4 (1,000 seeds, whole height range; superseded by round 5 below)

| Species | Height | Wood mean (max) | Tips mean (max) | Leaves mean (min–max) | Lowest leaf / height, min | Trees with leaves in the lowest fifth | Widest leaf |
|---|---|---|---|---|---|---|---|
| oak | 13–19 | 127 (200) | 23 (33) | 424 (212–643) | 0.40 | 0 | 10 |
| fancy_oak | 18–26 | 272 (388) | 42 (48) | 883 (514–1311) | 0.21 | 0 | 15 |
| birch | 14–20 | 66 (106) | 12 (20) | 106 (51–164) | 0.20 | 0 | 6 |
| tall_birch | 20–28 | 127 (191) | 23 (34) | 182 (68–279) | 0.25 | 0 | 7 |
| spruce | 20–28 | 115 (175) | 22 (32) | 219 (138–338) | 0.12 | 883 | 8 |
| pine | 18–26 | 96 (153) | 15 (23) | 306 (155–481) | 0.54 | 0 | 10 |
| mega_spruce | 30–40 | 242 (333) | 35 (48) | 452 (305–624) | 0.14 | 919 | 11 |
| mega_pine | 30–40 | 290 (376) | 30 (39) | 760 (500–1102) | 0.58 | 0 | 15 |
| acacia | 12–17 | 124 (191) | 20 (27) | 481 (181–743) | 0.50 | 0 | 15 |
| cherry | 13–18 | 166 (252) | 27 (35) | 685 (362–1043) | 0.20 | 0 | 14 |
| jungle | 15–23 | 77 (124) | 12 (20) | 267 (131–447) | 0.50 | 0 | 9 |
| mega_jungle | 32–44 | 441 (547) | 46 (48) | 1212 (828–1598) | 0.55 | 0 | 15 |
| swamp_oak | 10–14 | 107 (155) | 18 (24) | 553 (317–815) | 0.10 | 4 | 12 |
| mangrove | 9–13 | 70 (119) | 13 (20) | 352 (153–581) | 0.10 | 9 | 11 |
| tall_mangrove | 12–18 | 140 (231) | 23 (32) | 647 (302–1101) | 0.17 | 8 | 13 |
| dark_oak | 13–18 | 242 (350) | 32 (46) | 1290 (735–1917) | 0.23 | 0 | 14 |
| test_28_tips | 18–22 | 241 (293) | 27 (28) | 701 (504–948) | 0.10 | 103 | 14 |

"Lowest leaf / height" is the worst tree of the thousand; the spruces carry boughs low on purpose.
For scale: vanilla's oak has about 55 leaves and its dense forest chunk about 1,000. At a fifth of vanilla's count a
birch-and-oak forest chunk holds 2–3 trees: about 850–1,300 leaves if oak, 200–550 if birch.

### Size classes, revised (proposed change to spec 15)

The provisional classes (small 50 wood / 8 tips / 250 leaves / radius 5; medium 140/14/600/8; large 400/28/1200/12)
cannot hold a tree with log-based limbs and separate foliage masses. `ViewerTest` now enforces:

| Class | Height | `max_radius` | Wood voxels | `max_tips` | `max_leaves` | Species |
|---|---|---|---|---|---|---|
| Medium | 9–28 | ≤ 9 | ≤ 220 | 48 | 900 | oak, birch, tall birch, pine, jungle, swamp oak, mangrove |
| Large | 12–26 | ≤ 12 | ≤ 400 | 48 | 2,200 | spruce (since round 6: 226 wood voxels at most, with its roots), fancy oak, acacia, cherry, tall mangrove, dark oak |
| Giant | 30–44 | ≤ 12 | ≤ 600 | 48 | 3,500 | mega spruce, mega pine, mega jungle |

The small class is empty and can go. The other checks of the test are unchanged.

### Round 5: the exact midpoint (2026-09-19, on the human's instruction)

The human asked for "the exact midpoint values between the current and previous versions". Every number in every
species file is now `(value at commit 683e2e1 + value of round 4) / 2`. (A copy of round 4's files was kept under
`docs/tuning-snapshots/` until the cleanup of 2026-09-20; round 6 moved far from both ends.) What cannot be a midpoint,
and what was done instead (Q32):

- **Whole-number fields whose midpoint is a half** (heights, `max_radius`, `branches`, `curve_res`, `smother`, …):
  rounded toward the round 4 value. 60 fields; the script lists each one.
- **`shape`** where it changed (oak, fancy oak, birch, tall birch, pine, mega pine, mega jungle): round 4's shape.
- **The second level of spruce, pine, mega spruce and mega pine**, which did not exist before: kept as in round 4.
- **Acacia's level-2 `rotate`**, 140 → −85: the sign selects spiral or alternating placement, so 27.5 would be a third
  behaviour, not a blend. Kept −85.
- No `foliage_margin` fell below what its foliage needs, and no codec range or hard limit is violated.

Consequences worth knowing before judging it: `twig_radius` is 0.275 almost everywhere, so a limb needs **4** tips to
start as logs (0.3 needs 3): there are fewer log limbs than in round 4 and more than before. The spruce keeps its point
and its boughs but the tiers are less distinct (14 boughs on 15–21 blocks, `smother` 2). Nothing looks broken in the
dumps. `WorldgenPackGen.DENSITY` is the midpoint as well: (1/3 + 1/5) / 2 = 4/15, 2–3 trees per chunk where vanilla
places 10. The size classes of round 4 still hold with room to spare and were left as they are.

### Block counts after round 5 (superseded by round 6 below)

| Species | Height | Wood mean (max) | Tips mean (max) | Leaves mean (min–max) | Lowest leaf / height, min | Trees with leaves in the lowest fifth | Widest leaf |
|---|---|---|---|---|---|---|---|
| oak | 10–15 | 73 (116) | 13 (20) | 286 (129–468) | 0.45 | 0 | 9 |
| fancy_oak | 14–21 | 164 (251) | 26 (31) | 656 (326–1075) | 0.20 | 0 | 13 |
| birch | 11–15 | 55 (80) | 10 (14) | 122 (71–181) | 0.27 | 0 | 5 |
| tall_birch | 16–22 | 81 (123) | 15 (22) | 151 (90–220) | 0.32 | 0 | 6 |
| spruce | 15–21 | 80 (120) | 15 (23) | 140 (86–199) | 0.10 | 913 | 7 |
| pine | 15–21 | 63 (97) | 11 (18) | 185 (105–281) | 0.58 | 0 | 8 |
| mega_spruce | 23–32 | 245 (311) | 37 (38) | 370 (258–487) | 0.15 | 373 | 10 |
| mega_pine | 23–32 | 297 (385) | 37 (38) | 591 (393–862) | 0.55 | 0 | 12 |
| acacia | 10–14 | 74 (122) | 13 (20) | 299 (131–479) | 0.58 | 0 | 13 |
| cherry | 11–15 | 110 (167) | 19 (27) | 520 (243–886) | 0.20 | 0 | 13 |
| jungle | 12–18 | 50 (80) | 7 (14) | 191 (99–325) | 0.50 | 0 | 8 |
| mega_jungle | 26–36 | 371 (467) | 37 (38) | 1071 (680–1449) | 0.48 | 0 | 15 |
| swamp_oak | 8–12 | 69 (102) | 12 (18) | 377 (172–575) | 0.11 | 16 | 10 |
| mangrove | 8–12 | 55 (99) | 10 (18) | 268 (109–488) | 0.13 | 2 | 9 |
| tall_mangrove | 10–16 | 105 (174) | 18 (27) | 445 (135–750) | 0.09 | 13 | 11 |
| dark_oak | 11–15 | 207 (267) | 29 (31) | 1114 (674–1400) | 0.23 | 0 | 13 |
| test_28_tips | 18–22 | 241 (293) | 27 (28) | 701 (504–948) | 0.10 | 103 | 14 |

### Round 6: shapes at the agreed size (2026-09-19)

The human's verdict on round 5: the **sizes are right**, the **shapes are not** — "the spruce has too few leaves, the
birch looks like a very thick tree, none of the trees meet my expectations yet". Later in the round: the oak a bit
smaller; the pine must not be flat-topped (two photos of Scots pines); the acacia divides into several limbs low down
(two photos of umbrella thorns) and its limbs should be thinner.

**The main finding is about the tool, not the trees.** `TreeDump.isometric` looks down from 27° and draws a block
twice as wide as it is tall. Every tree looked squat and every trunk thick in it, so rounds 1–5 were judged on wrong
proportions: a crown that looked generous was, from the ground, a small tuft on a long pole. `SpeciesDump` now draws an
eye-level view (orthographic, turned 30°, raised 12°, true proportions). **Judge silhouettes only in that view, in the
viewer or in game.**

| Species | What was wrong in true proportion | What changed |
|---|---|---|
| Spruce, mega spruce | Thin one-layer pads on a visible pole. | 18 (27) drooping boughs (`down_angle` 98–100, `curve` −20) bearing from 0.15 of their length, sleeves 2.1 → 1.25 (2.2 → 1.3), `flatten` 0.52–0.55, `trunk_foliage` 0.25–0.35, `smother` 0 (8). Leaves 140 → 288 (370 → 499). |
| Birch, tall birch | A ball of leaves on a pole; log limb stubs made the trunk look two blocks thick in the crown. | `twig_radius` 0.25 so limbs are branch blocks; 10 (14) short limbs at 42°, tufts of radius 1.45 from 0.5 of the limb, `density` 0.7, slight droop. Birch 13–18 tall (was 11–15): a 1-block trunk needs the height to read as slender. |
| Oak | Lollipop: 60% bare pole, small flat crown. | 9–13 tall (the human: "a bit smaller"), `base_size` 0.38, `hemispherical`, limbs 0.6 of the trunk at 62° turning upward, sleeves from 0.4 of the limb, `trunk_foliage` 0.3: a broad rounded crown wider than the bare trunk is tall. |
| Pine, mega pine | Round 4's flat umbrella, which the human rejected. | `flame` (widest in the lower middle, domed top), `base_size` 0.28 (0.38), 13 (16) limbs at 60° with `down_angle_v` −25 so the top ones are steep, rounder clumps (`flatten` 0.65) on the outer 45% of the limbs, `trunk_foliage` 0.2–0.25. |
| Acacia | One leaning pole with a small plate. | A short trunk that divides at a fifth of its height: `base_splits` 2 on a 5-segment trunk, `split_angle` 52, and **`trunk.curve` +60 to cancel the paper's split compensation**, which otherwise turns the stems back to vertical (a candelabra). All foliage in one layer (`foliage_start` 0.7, `flatten` 0.3, radius 3.4). `trunk_leader` with `twig_radius` 0.25 and `pipe_exponent` 2.3, so only the fork is logs and the stems thin to branch blocks (the human: "less logs, more branch blocks"). |
| Jungle | Small tuft on a pole. | Limbs 0.45, 8 of them, `base_size` 0.55: an umbrella on a pole. |
| Cherry | Fine; a little narrow. | Limbs 0.85, `flatten` 0.5, `twig_radius` 0.3. |
| Fancy oak, dark oak, mega jungle, swamp oak, mangroves | Looked right in true proportion. | Unchanged from round 5. Swamp oak needs **roots**, which data cannot give (next section). |

**Later in round 6, on the human's feedback and photos:**

| Species | The human said | What changed |
|---|---|---|
| Birch, tall birch | "Make it thinner: branch blocks from about halfway up the main trunk; branches mostly point upward"; then, with a photo of a silver birch, "more foliage, and not just on top". | `trunk_leader` with `twig_radius` 0.22 and `pipe_exponent` 3.0: the log trunk ends near the first limbs and the rest is a branch-block leader. 12 (15) limbs at 30° from the trunk, `base_size` 0.22 so the crown is a tall ovoid over most of the height, drooping twigs (`attraction_up` −0.6, level-2 `down_angle` 50), airy foliage (`density` 0.55). |
| Spruce, mega spruce | "A triangular shape, wide bottom, narrow pointed top" (photo of a Norway spruce); then "a little more foliage than we need"; then "pointier". | `base_size` 0.08–0.1 (boughs to the ground), 22 (30) boughs of `length` 0.4 (0.36) in a `conical` envelope, sleeves 1.9 → 1.2 (2.1 → 1.2), `density` 0.85, `trunk_foliage` 0.38 (0.3) with `lift` 0.3 for a long tapering point, straighter leader. `max_radius` 9 (12). |
| Cherry | "The thick part of the trunk is not very tall; sturdy branches extend from it, forming spherical foliage" (two photos). | `base_splits` 2 at 48° on a 4-segment trunk with `trunk.curve` 40 (as the acacia), `spherical`, limbs 0.6, rounder sleeves (`flatten` 0.8). |
| Dark oak | Two photos of veteran oaks; then "a bit smaller". | 9–13 tall, `max_radius` 10, limbs as long as the trunk leaving at 62–76° with an S-curve (`curve` −35, `curve_back` 10), `seg_splits` 0.4, five short all-log buttress roots. |
| Jungle, mega jungle | A screenshot of three hand-built jungle trees: "like in vanilla, branches with their own foliage occasionally branch off to the side from the main trunk; the foliage should resemble an umbrella"; then "root is too big"; then "slightly fewer independent branches". (An earlier try from two rainforest photos, a bare column with a crown only at the top, was dropped.) | A winding trunk (`trunk.curve_v` 70 (50)), 4 (6) side limbs from a fifth of the height in an `inverse_conical` envelope so the upper ones are longer and the lowest fall under 2 blocks and vanish, each limb turning upward (`curve` −40) into its own flat umbrella: `foliage_start` 0.7, radius 2.0 → 3.1 (2.4 → 3.6), `flatten` 0.3, `max_distance` 6. **`smother` 0**, as for the spruce: self-shading deletes every umbrella that has another above it. The trunk's own tip carries the top umbrella. 3 (5) small roots, mostly branch blocks. Mega jungle `max_radius` 11 with `foliage_margin` 5, which its 3.6 sleeves need. |
| Fancy oak | "You have a general idea of what I want; update all the JSON files." | Made the oak's big sibling on the dark oak's recipe: `base_size` 0.36, limbs 0.75 of the trunk at 62–76° with an S-curve, `seg_splits` 0.35, clumps on the outer half. |
| All species without roots | "Add roots to all trees that don't have them, but don't overdo it and keep the tree's shape in mind: a couple of blocks of branches are enough for a birch." | Birches 2 roots of one or two branch blocks; oak, spruce, pine, acacia 3 short branch-block roots (`height` 1–1.2, `spread` 1.3–1.5); cherry 4 (`log_share` 0.3); fancy oak, mega spruce, mega pine 4 roots of `height` 2, `spread` 2.2–2.4, half logs, as a flare under a big trunk. Every shipped species now has `roots`; `test_28_tips` does not. |
| Birch, tall birch (again) | "Less branches and more foliage." | 7 (9) limbs with 2 twigs each, `max_tips` 24 (30): tips fall from 14 to 8 (20 to 12) and wood from 69 to 50 (100 to 76). Sleeves 1.5 → 2.2 from 0.3 of the limb, `trunk_foliage` 0.35, `density` 0.75, `smother` 5, `lift` 0: leaves rise from 143 to 217 (182 to 289). The crown stays a tall ovoid on a branch-block leader. |
| Mangrove, tall mangrove (again) | "Vanilla already stands the mangrove on its root structure, so lower the tree a lot: it is now one of the tallest in the game"; then, with two photos, "smaller overall, in reality they aren't that big"; then "mostly branches instead of logs". | Vanilla's `mangrove_root_placer` lifts the trunk 1–3 (tall: 3–7) blocks, which the 11–15 (14–20) trunks ignored. Now 5–7 (6–9) tall, `max_radius` 6 (7), a compact rounded crown about as wide as the tree is tall: `base_splits` 2 at 35°, 7 limbs of 0.8 at 52° turning upward, sleeves 1.6 → 2.5 with `flatten` 0.8 from 0.35 of the limb, `trunk_foliage` 0.45, `lift` 0.4, `base_size` 0.42, `foliage_margin` 4. `trunk_leader` with `twig_radius` 0.2 and `pipe_exponent` 3.0: only the bare 2 blocks of trunk are logs (4–7 logs per tree), all else branch blocks. Own roots reduced to 5 of `height` 2, `spread` 3, since vanilla's prop roots stand under them. A first try that kept the old leaf volume (475 and 680 leaves on a 6–10 trunk) was rejected as too big. |
| Azalea (new species, Q35) | A screenshot of vanilla's azalea tree in a mangrove swamp: "we missed this small tree with flowers"; then "smaller and mostly from branches". | New `azalea.json` for vanilla's `azalea_tree` (oak logs and `oak_branch`; vanilla's weighted azalea and flowering-azalea leaves, rooted dirt and `force_dirt` are untouched). A shrub 4–5 tall: `trunk_leader` with `twig_radius` 0.2 and `pipe_exponent` 3.0 so only the 2 blocks at the foot are logs, `base_splits` 2 on a bent 2-segment trunk, 5 limbs of 0.8 turning upward, a low dome (`radius_tip` 2.0, `flatten` 0.75, `trunk_foliage` 0.6, `density` 0.9), two one-block roots. 17 wood voxels and 77 leaves on average; the fewest leaves in 1,000 seeds is 31, just above the test's floor of 30, so do not shrink it further without checking. |
| Birch, tall birch (third time) | "The leaves are high up; stretch a little to bring them down lower." | `tapered_cylindrical` instead of `tend_flame`: the lowest limbs are now the longest (`tend_flame` halves them, and those under 2 blocks vanish, which is what left the lower trunk bare). `base_size` 0.17, 8 (10) limbs of 0.27 (0.25), low limbs a little more open (`down_angle` 34, `down_angle_v` −12), sleeves from 0.25 of the limb with `lift` −0.1. The crown now starts at 3–4 blocks (was 3–7) on a 13–18 trunk; leaves and wood are unchanged. `base_size` 0.14 with `lift` −0.2 was tried and put leaves at head height in half the trees. |
| Swamp oak, mangroves | "The swamp oak's distinctive feature is very large roots; change the algorithm if you must" (two photos); "use branch blocks to make roots thinner"; then "it ended up looking like a mangrove: copy its values to the mangrove, make the swamp oak from scratch with much smaller roots". | **Generator change: `roots`** (next section). Mangrove and tall mangrove (3 taller) now hold those values: 7 stilt roots from up to 5 blocks up the trunk, 90% branch blocks. Swamp oak is new: the oak's recipe, low and wide with a slight droop, and 4 small roots (`height` 1.6, `spread` 2). |

The eye-level dump now also draws a branch block's arms, so thin wood reads as it does in game.

### Generator change: `roots` (spec 22 item 5, brought in on the human's instruction; Q33)

An optional `roots` object in the trunk placer: `count` 0–8, `height` 1–8 (how far up the trunk the highest root leaves
it), `spread` 1–8 (≤ `max_radius`), `depth` 0–6 (blocks below the trunk's base, used only where the ground falls
away or is water), `log_share` 0–1 (share of a root, from the trunk, that is logs; the rest is branch blocks).
Each root leaves the lower trunk, arches out and down to the level of the trunk's base, then goes straight down until
`canPlace` stops it, which is the ordinary truncation path of spec 7.5, so roots respect terrain, `max_radius` and the
chunk area with no new rule. A root is wood only: no tip, no attachment, no foliage, no load in the pipe model, not
counted by `max_tips`. Roots are created after the tip budget and draw their random numbers last, and their wood is
emitted last, so **a species without `roots` generates byte-identically to before** (all 60 golden hashes unchanged,
nothing regenerated), and adding roots to a species changes nothing else about its trees (`RootsTest`). Codec
(`ParamCodecs`), viewer (`PlacerJson`, a Roots slider group) and tests (`RootsTest`: identity of the rest of the tree,
no floating wood on a bank with water, depth limit, determinism after another tree, ranges) are done.

### Block counts after round 6 (1,000 seeds, whole height range)

| Species | Height | Wood mean (max) | Tips mean (max) | Leaves mean (min–max) | Lowest leaf / height, min | Trees with leaves in the lowest fifth | Widest leaf |
|---|---|---|---|---|---|---|---|
| oak | 9–13 | 76 (113) | 12 (21) | 314 (161–512) | 0.18 | 1 | 9 |
| fancy_oak | 14–21 | 261 (358) | 35 (43) | 1202 (745–1677) | 0.14 | 6 | 14 |
| birch | 13–18 | 48 (76) | 8 (13) | 218 (132–329) | 0.07 | 150 | 6 |
| tall_birch | 16–22 | 71 (104) | 11 (17) | 281 (181–422) | 0.10 | 212 | 7 |
| spruce | 15–21 | 163 (226) | 28 (40) | 398 (224–585) | 0.05 | 1000 | 9 |
| pine | 15–21 | 87 (132) | 13 (22) | 253 (116–405) | 0.31 | 0 | 9 |
| mega_spruce | 23–32 | 367 (448) | 47 (48) | 891 (608–1215) | 0.03 | 1000 | 13 |
| mega_pine | 23–32 | 230 (326) | 30 (41) | 576 (375–840) | 0.41 | 0 | 12 |
| acacia | 10–14 | 117 (172) | 20 (30) | 570 (223–906) | 0.29 | 0 | 15 |
| cherry | 11–15 | 119 (166) | 19 (26) | 760 (390–1182) | 0.08 | 30 | 13 |
| jungle | 12–18 | 47 (70) | 6 (9) | 208 (66–385) | 0.33 | 0 | 11 |
| mega_jungle | 26–36 | 227 (296) | 16 (21) | 867 (451–1293) | 0.31 | 0 | 14 |
| swamp_oak | 9–13 | 115 (169) | 19 (28) | 536 (256–866) | 0.10 | 17 | 12 |
| mangrove | 5–7 | 49 (86) | 7 (14) | 206 (75–430) | 0.14 | 17 | 8 |
| tall_mangrove | 6–9 | 72 (122) | 11 (20) | 311 (113–615) | 0.11 | 27 | 9 |
| dark_oak | 9–13 | 190 (289) | 28 (43) | 839 (471–1268) | 0.17 | 7 | 13 |
| azalea | 4–5 | 17 (32) | 4 (7) | 77 (31–143) | 0.25 | 0 | 5 |
| test_28_tips | 18–22 | 241 (293) | 27 (28) | 701 (504–948) | 0.10 | 103 | 14 |

**Density after round 6 (Q34):** oak, birch and mixed forests 4–5 trees per chunk (0.4 of vanilla's), savannas 0.7 on
average from their own weighted count, everything else 4/15 of vanilla's.

Waiting for the human's view of round 6 in the viewer and in game.

## Defaults and `trunk_leader`

Current state, to be confirmed at the end of tuning:

- `pipe_exponent` 2.0 and the foliage defaults of spec 9.2 are unchanged. After round 4 most species set
  `twig_radius` 0.3 (log limbs from 3 tips) and `smother` 3; whether 0.3 should become the default is for the human. Species override `radius_base`, `radius_tip`, `flatten`, `lift`,
  `foliage_start` and `trunk_foliage` where their character needs it.
- `trunk_leader`: birch, tall birch, spruce, pine, mega spruce and mega pine set it. The others do not.

## Proposed spec edits (the human edits the spec)

1. 8.2, `curve_v`: add "A negative `curve_v` forms the stem as a helix, as in the paper." (Q2)
2. 7.2, the `trunk_leader` rule: add "With `trunk_leader`, `trunk_width_min` holds on the bare part of the trunk,
   from the base to the first stem attached to it, and that part is never thinner than a 1×1 log." (Q6)
3. 7.3: mention the cap of 2,048 generated stems. (Q9)
4. 7.5, next to `canLeaf`: "A position more than 16 blocks from the trunk origin on either horizontal axis is
   never a leaf candidate." (Q8)
5. Section 10: the example oak's values are superseded by `tools/viewer/src/main/resources/species/oak.json`
   once tuning ends.
6. Section 15: replace the provisional size classes with the table under "Size classes, revised" above, and the
   sentence about "3–4 trees per chunk" with "about 2". Section 11.3: a fifth of vanilla's count, not a third. (Q31)
7. New in 8.2: the `roots` object, as described under "Generator change: `roots`" above; 7.2 gains "roots carry no
   load"; 7.3 "a root is not a tip"; 7.6 "roots bear no foliage"; section 22 item 5 moves into version 1. (Q33)

