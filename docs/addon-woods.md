# Branch blocks for another mod's wood

Spec section 18, Phase 4. There is no stable Java API yet (spec 22, item 17): this page describes what works today and
may change between versions. A wood mod, or a small addon beside it, can give its wood branch blocks by creating
`kz.nursultan.naturaltrees.block.BranchBlock` itself. Nothing in that class knows this mod's own eight woods.

## Code

Two blocks and two items per wood, registered under **your** namespace:

```java
// plain first or stripped first, the order does not matter: the stripped variant is looked up lazily
Block stripped = new BranchBlock(null, BranchBlock.propertiesOf(MyBlocks.STRIPPED_MAPLE_LOG));
Block plain = new BranchBlock(() -> stripped, BranchBlock.propertiesOf(MyBlocks.MAPLE_LOG));
```

- `propertiesOf(log)` copies the log's hardness, blast resistance, sound, instrument and lava ignition, takes the
  bark's map colour, and removes the full cube's occlusion.
- The first argument is what an axe turns the branch into; `null` for a branch that is already stripped.
- On NeoForge use `kz.nursultan.naturaltrees.block.NeoForgeBranchBlock` with the same arguments: it adds the
  tool-modified-state hook (stripping) and log flammability. On Fabric, stripping works through this mod's own
  use-on-block callback for any `BranchBlock`; register flammability (5, 5) and fuel (150 ticks) yourself with
  Fabric's `FlammableBlockRegistry` and `FuelRegistry`.
- Shears, placement arms, waterlogging, neighbour updates and built-in felling need nothing: they work on any
  `BranchBlock`.

## Files

`tools/assetgen` in this repository writes exactly these files for the vanilla woods and is the template:

| File | Purpose |
|---|---|
| `assets/<ns>/blockstates/<wood>_branch.json` | the multipart of arm, bark face and end-grain face models |
| `assets/<ns>/models/block/<wood>_branch_{core_side,core_end,arm,inventory}.json` | children of `naturaltrees:block/branch_*` that bind `side` and `end` to your log textures; no new textures |
| `assets/<ns>/models/item/<wood>_branch.json` | parent `<ns>:block/<wood>_branch_inventory` |
| `data/<ns>/loot_table/blocks/<wood>_branch.json` | drops itself, with `survives_explosion` |
| `data/<ns>/recipe/…` | 2 logs stacked → 4 branches; 1 branch → 2 planks |
| `data/minecraft/tags/block/logs_that_burn.json` (or your wood's log tag, if it leads there) | makes the branch a log as a block: leaves persist beside it, axes mine it |
| `data/naturaltrees/tags/item/branches.json` | fuel on NeoForge comes from this tag; **do not** add branch items to `#minecraft:logs` (spec 6.5) |

## Trees

A `minecraft:tree` configured feature of yours uses the placers like any species file (spec section 10):
`"trunk_placer": {"type": "naturaltrees:weber_penn", "branch_provider": {… "Name": "<ns>:<wood>_branch"}, …}` and
`"foliage_placer": {"type": "naturaltrees:skeleton", …}`. Tune it with `./gradlew :tools:viewer:run`.
