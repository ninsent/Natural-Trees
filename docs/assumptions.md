# Assumptions about Minecraft 1.21.1 (spec section 20)

Checked on 2026-09-19 against the decompiled vanilla sources and data that ModDevGradle produces
(`common/build/moddev/artifacts/vanilla-1.21.1-20240808.144430-sources.jar` and
`…-client-extra-aka-minecraft-resources.jar`, Mojang mappings with Parchment names) and the Fabric API sources
jar in the Gradle cache. Nothing was run in game for this file; in-game confirmation belongs to the Phase 1
manual tests.

| # | Assumption | Verdict | Read in |
|---|---|---|---|
| 1 | `TrunkPlacer.placeTrunk` receives a level reader, a block setter, a `RandomSource`, the free height, the origin and the `TreeConfiguration`, and returns a list of `FoliageAttachment`. Any block state may be placed through the setter. | **True.** `placeTrunk(LevelSimulatedReader, BiConsumer<BlockPos, BlockState>, RandomSource, int freeTreeHeight, BlockPos, TreeConfiguration)`. The setter only records the position and calls `level.setBlock(pos, state, 19)`; it does not look at the state. | `TrunkPlacer.placeTrunk`; `TreeFeature.place` (the `biconsumer1` lambda) |
| 2 | Positions placed through the trunk setter seed `updateLeaves` as distance-0 sources regardless of tag, and positions placed through the foliage setter receive baked distances. | **True, with a nuance.** `place` calls `updateLeaves(level, box, set1, set3, set)` where `set1` is the trunk setter's set; its positions are put in the distance-0 list with no tag test. The search then walks face neighbours and takes any block for which `LeavesBlock.getOptionalDistanceAt` answers: a block in `#minecraft:logs` counts as 0, a block with a `distance` property gets `min(own, k + 1)` written. Leaves are found by that property, **not** by membership of the foliage set, so every leaf reachable through leaves is baked. Positions of decorators and roots are excluded. | `TreeFeature.place`, `TreeFeature.updateLeaves`; `LeavesBlock.getOptionalDistanceAt` |
| 3 | `LeavesBlock` reads the `#minecraft:logs` **block** tag at runtime, so a leaf next to a branch resolves to `distance = 1`. | **True.** `getOptionalDistanceAt`: `state.is(BlockTags.LOGS)` gives 0; `updateDistance` takes the minimum over the six neighbours plus 1. | `LeavesBlock.getOptionalDistanceAt`, `LeavesBlock.updateDistance` |
| 4 | The exact configured-feature keys referenced by every `TreeGrower`, by structures and by every biome selector. | **Confirmed for the oak and birch families (2026-09-19); see "Item 4 in full" below.** The 15 keys of spec 11.2 are exactly vanilla's oak, fancy oak, birch and tall birch `minecraft:tree` features, and every one of them is reachable. | vanilla data: `worldgen/configured_feature`, `placed_feature`, `biome`, `template_pool`; `TreeGrower` |
| 5 | Vanilla plank recipes read per-wood **item** tags, so a branch outside them has exactly one plank recipe. | **True.** `data/minecraft/recipe/oak_planks.json`: shapeless, ingredient `{"tag": "minecraft:oak_logs"}`, result 4 planks. | vanilla data |
| 6 | The full set of block tags that vanilla logs carry. | **Found.** Directly: `minecraft:<wood>_logs`, `minecraft:overworld_natural_logs` (the un-stripped log only). Through `<wood>_logs`: `logs_that_burn`, then `logs`. Through `logs`: `mineable/axe`, `completes_find_tree_tutorial`, `lava_pool_stone_cannot_replace`, `parrots_spawnable_on`. Through `overworld_natural_logs`: `snaps_goat_horn`. | vanilla data, `data/minecraft/tags/block/` |
| 7 | Fabric's strippable-block registry requires an `axis` property, so the branch needs its own hook. | **True.** `StrippableBlockRegistry.register` calls `requireNonNullAndAxisProperty` on both blocks and throws `IllegalArgumentException("… must have the 'axis' property")`. | Fabric API `fabric-content-registries-v0` 8.0.11, `StrippableBlockRegistry` |
| 8 | The beehive decorator behaves acceptably when the log list it receives contains branches. | **True in code; look at it in game.** The decorator sorts logs by Y, picks one height (just under the lowest leaf, at least one above the lowest log), and hangs the nest beside any log **at that height** whose side and the block south of it are air. With branches in the list the nest can hang beside a branch block out on a limb. Nothing can fail: if no log is at that height no nest is placed, and decorators only run when wood or leaves were placed (and leaves need wood). | `BeehiveDecorator.place`; `TreeDecorator.Context` (sorts by Y); `TreeFeature.place` |
| 9 | The write radius at the `FEATURES` step is one chunk, and `WorldGenRegion.getCenter()` returns the decorating chunk. | **True.** `ChunkPyramid.GENERATION_PYRAMID`: the `FEATURES` step has `blockStateWriteRadius(1)`. `getCenter()` returns `center.getPos()`; `ensureCanWrite` compares the chunk distance with that radius and logs "Detected setBlock in a far chunk" beyond it. | `ChunkPyramid`; `WorldGenRegion.getCenter`, `WorldGenRegion.ensureCanWrite` |
| 10 | Generated arm states pass through `updateShapeAtEdge` and sapling-path placement unchanged; `#minecraft:replaceable_by_trees` contains water. | **True.** Blocks are set with flag 19, which includes `UPDATE_KNOWN_SHAPE`, so no shape update runs on placement, on either path. `StructureTemplate.updateShapeAtEdge` then calls `updateShape` only across faces between the tree's voxel shape and the outside; a branch never has an arm there, because arms point at the tree's own wood, and the branch's `updateShape` only clears arms (spec 6.4). The tag holds `minecraft:water`, `seagrass` and `tall_seagrass` among others. | `TreeFeature.setBlockKnownShape`, `TreeFeature.place`; `StructureTemplate.updateShapeAtEdge`; vanilla data |
| 11 | `TreeFeature` calls the trunk placer and then the foliage placer consecutively on one thread, and passes the foliage placer the same attachment objects. | **True.** `doPlace`: `list = trunkPlacer.placeTrunk(…); list.forEach(a -> foliagePlacer.createFoliage(…, a, …))`. Nothing runs between the two. | `TreeFeature.doPlace` |
| 12 | A custom `FoliagePlacer` may pass constant radius and offset to its base class and place leaves at arbitrary positions through `tryPlaceLeaf`. | **True.** The constructor takes two `IntProvider`s (`ConstantInt.of(0)` is legal: the codec range is 0–16). `tryPlaceLeaf` is `protected static`, tests `TreeFeature.validTreePos`, applies the foliage provider and waterlogging, and calls the foliage setter. | `FoliagePlacer` (constructor, `tryPlaceLeaf`) |
| 13 | `TrunkPlacer.setDirtAt` is usable from a custom placer, and vanilla's 2×2 trees occupy the origin column and the columns at +x, +z and +x+z. | **True.** `setDirtAt` is `protected static`. `GiantTrunkPlacer` sets dirt and logs at the origin, `east()`, `south()` and `south().east()`, and returns one attachment at `pos.above(freeTreeHeight)` with `doubleTrunk` true. | `TrunkPlacer.setDirtAt`; `GiantTrunkPlacer.placeTrunk` |
| 14 | Vanilla's trunk and foliage setters copy the position they are given, so a single mutable position may be passed for every block. | **True.** Every setter in `TreeFeature.place` stores `pos.immutable()`. | `TreeFeature.place` |

## Other facts read on the way that the design relies on

- Both placer type classes have a **private** constructor (`TrunkPlacerType(MapCodec)`, `FoliagePlacerType(MapCodec)`),
  so Fabric needs an invoker mixin for each, as spec 8.1 and 9.1 say.
- `TrunkPlacer.trunkPlacerParts` gives the three height fields with the ranges of spec 8.2 (0–32, 0–24, 0–24).
- The attachment position of a vanilla straight trunk is `pos.above(freeTreeHeight)`: the block above the top
  log, which is the convention spec 8.1 step 8 names.
- `TreeFeature.doPlace` passes the **free** height, which is lower than the drawn height when `minimum_size`
  allows clipping; the generator takes whatever it is given.

## Item 4 in full

1.21.1 has 31 `minecraft:tree` configured features. Those on oak or birch logs:

| Family (spec 11.2) | Keys | Vanilla placers |
|---|---|---|
| Oak | `oak`, `oak_bees_0002`, `oak_bees_002`, `oak_bees_005` | straight trunk, blob foliage |
| Fancy oak | `fancy_oak`, `fancy_oak_bees`, `fancy_oak_bees_0002`, `fancy_oak_bees_002`, `fancy_oak_bees_005` | fancy trunk, fancy foliage |
| Birch | `birch`, `birch_bees_0002`, `birch_bees_002`, `birch_bees_005` | straight trunk, blob foliage |
| Tall birch | `super_birch_bees`, `super_birch_bees_0002` | straight trunk, blob foliage |
| Not in 11.2 | `swamp_oak` (Phase 3), `azalea_tree` (oak logs, azalea leaves; the spec does not mention it, so it stays vanilla) | |

Who reaches the 15 keys:

- **Saplings** (`TreeGrower`): `oak`, `oak_bees_005`, `fancy_oak`, `fancy_oak_bees_005`, `birch`, `birch_bees_005`.
- **Structures**: the placed feature `minecraft:oak` in the template pools `village/plains/trees`,
  `village/plains/decor` and `village/plains/zombie/decor`. No other structure places one of the 15.
- **Biomes, directly**: `trees_badlands` → `oak` (wooded badlands); `trees_birch` → `birch_bees_0002` (birch forest).
- **Biomes, through a selector**:
  - `trees_birch_and_oak` (forest): `oak_bees_0002`, `birch_bees_0002`, `fancy_oak_bees_0002`;
  - `birch_tall` (old growth birch forest): `birch_bees_0002`, `super_birch_bees_0002`;
  - `trees_flower_forest`: `oak_bees_002`, `birch_bees_002`, `fancy_oak_bees_002`;
  - `trees_plains` (plains, sunflower plains; also listed by dripstone caves and deep dark): `oak_bees_005`,
    `fancy_oak_bees_005`, **inline**, not by placed-feature id;
  - `trees_meadow`: `fancy_oak_bees`, `super_birch_bees`;
  - `dark_forest_vegetation`: `oak_checked`, `fancy_oak_checked`, `birch_checked`, beside dark oak and mushrooms;
  - `trees_water` (11 river and ocean biomes), `trees_windswept_hills`, `trees_windswept_forest`, `trees_savanna`,
    `trees_windswept_savanna`, `trees_jungle`, `trees_sparse_jungle`, `bamboo_vegetation`: oak or fancy oak as a
    minor part beside other species.

Consequences for Phase 2:

- Supplying the 15 configured features **by id** covers every path above, the inline ones included. No biome,
  selector or structure file has to be touched for the trees themselves (spec 11.2).
- Density (spec 11.3) is set on the biome-level **placed** features. Those made only of oak and birch are
  `trees_birch_and_oak`, `trees_birch`, `birch_tall`, `trees_flower_forest`, `trees_plains`, `trees_meadow` and
  `trees_badlands`. The mixed ones (`dark_forest_vegetation`, `trees_water`, the windswept, savanna and jungle
  ones, `bamboo_vegetation`) also place species that stay vanilla until Phase 3; lowering their count now would
  thin those too.

## Phase 3: facts read before planning (2026-09-19)

Not items of spec section 20; they are what the remaining woods newly rely on. Read in the same sources.

| # | Fact | Verdict | Read in |
|---|---|---|---|
| P3-1 | A tree from 2×2 saplings is placed with its origin at the **north-west** sapling, the four saplings having been cleared, so the origin column plus +x, +z and +x+z is exactly the sapling square. | **True.** `placeMega` loops the offsets, and calls `configuredfeature.place(level, generator, random, pos.offset(i, 0, j))` for the square whose corner is `(i, j)`. Matches spec 7.4 and assumption 13. | `TreeGrower` (lines 136–150) |
| P3-2 | The mangrove root placer hands the trunk placer a raised origin, and the trunk placer needs to know nothing about roots. | **True.** `RootPlacer.getTrunkOrigin` returns `pos.above(trunk_offset_y)`; `TreeFeature.doPlace` places the roots from `pos` to that origin and then calls `placeTrunk` with it. Roots are in their own position set, excluded from distance baking (assumption 2). | `RootPlacer.getTrunkOrigin`; `TreeFeature.doPlace` |
| P3-3 | `cocoa` with branches in the log list. | **Fine.** It takes logs at most 2 above the lowest log, which are trunk logs, and hangs cocoa beside them where there is air. Cocoa survives on `#minecraft:jungle_logs`, which a jungle branch carries. | `CocoaDecorator.place`; `CocoaBlock.canSurvive` |
| P3-4 | `trunk_vine` with branches in the log list. | **Works, but look at it.** It visits **every** position in the log list and, on each of four sides that is air, places a vine with chance 2/3. A vanilla jungle tree has about 10 logs; one of ours has 50 to 400 wood voxels, so every limb and twig would be hung with vines. Nothing fails; whether it looks right is for the human (plan decision Q22). | `TrunkVineDecorator.place` |
| P3-5 | `leave_vine` and `attached_to_leaves` (mangrove propagules). | **Fine.** Both read only the leaf list. More leaves means more vines and propagules in proportion. | `LeaveVineDecorator`, `AttachedToLeavesDecorator` |
| P3-6 | `alter_ground` (podzol under mega spruce and pine). | **Fine.** It circles the logs at the lowest Y of the log list, which is the trunk base. A limb that droops below the base on a slope would move the circles there; rare, harmless. | `AlterGroundDecorator.place` |

Remaining vanilla tree features, all `two_layers` size except dark oak (`three_layers`):

| Key | Vanilla height | Vanilla placers | Decorators, roots |
|---|---|---|---|
| `spruce` | 5+2+1 | straight, spruce foliage | |
| `pine` | 6+4+0 | straight, pine foliage | |
| `mega_spruce`, `mega_pine` | 13+2+14 | giant (2×2), mega pine foliage | `alter_ground` |
| `acacia` | 5+2+2 | forking, acacia foliage | |
| `cherry`, `cherry_bees_005` | 7+1+0 | cherry, cherry foliage | beehive on the second |
| `jungle_tree`, `jungle_tree_no_vine` | 4+8+0 | straight, blob | `cocoa`, `trunk_vine`, `leave_vine` on the first |
| `mega_jungle_tree` | 10+2+19 | mega jungle (2×2), jungle foliage | `trunk_vine`, `leave_vine` |
| `swamp_oak` | 5+3+0 | straight, blob | `leave_vine` |
| `mangrove`, `tall_mangrove` | 2+1+4, 4+1+9 | upwards branching, random spread | `leave_vine`, `attached_to_leaves`, beehive; **mangrove root placer** |
| `dark_oak` | 6+2+1 | dark oak (2×2), dark oak foliage | |
| not in the spec's list | `jungle_bush` (one log and a bush), `azalea_tree` | | they stay vanilla |

