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

## Defaults and `trunk_leader`

Open until tuning (T14). First drafts: birch and tall birch set `trunk_leader`; oak and fancy oak do not.
