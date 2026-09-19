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

Waiting for the human's view in the viewer (manual tests P0-2 and P0-3) before further rounds.

## Defaults and `trunk_leader`

Current state, to be confirmed at the end of tuning:

- `twig_radius` 0.25, `pipe_exponent` 2.0, `smother` 4 and the foliage defaults of spec 9.2 are unchanged so far:
  nothing seen in tuning argues against them. Species override `radius_base`, `radius_tip`, `flatten`, `lift`,
  `foliage_start` and `trunk_foliage` where their character needs it.
- `trunk_leader`: birch and tall birch set it. Oak and fancy oak do not.

## Proposed spec edits (the human edits the spec)

1. 8.2, `curve_v`: add "A negative `curve_v` forms the stem as a helix, as in the paper." (Q2)
2. 7.2, the `trunk_leader` rule: add "With `trunk_leader`, `trunk_width_min` holds on the bare part of the trunk,
   from the base to the first stem attached to it, and that part is never thinner than a 1×1 log." (Q6)
3. 7.3: mention the cap of 2,048 generated stems. (Q9)
4. 7.5, next to `canLeaf`: "A position more than 16 blocks from the trunk origin on either horizontal axis is
   never a leaf candidate." (Q8)
5. Section 10: the example oak's values are superseded by `tools/viewer/src/main/resources/species/oak.json`
   once tuning ends.
