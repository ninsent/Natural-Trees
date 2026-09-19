# Phase 1 test datapack

Not part of the mod. The built-in datapack comes in Phase 2 (spec 11); until then this pack lets a sapling and
world generation grow the mod's trees for the Phase 1 manual tests (`docs/plan-phase-1.md`, decision Q13).

It supplies the 15 vanilla configured-feature keys of spec 11.2 (the oak, fancy oak, birch and tall birch
families). Each file is vanilla 1.21.1's file with `trunk_placer` and `foliage_placer` replaced by the species
in `tools/viewer/src/main/resources/species/`; decorators, providers and `minimum_size` are vanilla's.

## Use

1. Create the world first, then close it (or use "Data Packs" on the Create World screen and drop the folder in).
2. Copy this folder, `phase-1`, into the world's `datapacks` folder:
   `fabric/runs/client/saves/<world>/datapacks/` or `neoforge/runs/client/saves/<world>/datapacks/`.
3. Open the world. `/datapack list` should show `file/phase-1` as enabled.
   Only chunks generated from now on get the new trees; saplings and `/naturaltrees place` use them at once.

If a file fails to load, the game log names it; send that line back.
