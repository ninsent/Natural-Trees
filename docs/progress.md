# Progress

Last updated: 2026-09-19

## Done

- **Milestone 0 — project skeleton.** Commit `74c6276`. Both clients confirmed by the human (M0-1, M0-2).
- **Phase 0, tasks T1–T10** (uncommitted; the human makes all commits):
  - T1 parameters with the ranges and defaults of spec 8.2 and 9.2; float fields are rounded to `float` so
    that the game (float codecs) and the viewer (JSON doubles) generate the same tree.
  - T2 `Frame`; T3 `Skeleton` (paper §4.1–4.3, 4.8, helix mode); T4 voxel-scale rules and tip budget.
  - T5–T7 `WoodBuilder`: pipe model, DDA and face-connected rasterisers, 2×2 sections, arms, truncation,
    one child per face, predicate order with one world read per position.
  - T8 `FoliageBuilder`: spec 7.6 steps 1–7. T9 determinism, independence, golden hashes.
  - T10 `TreeDump` test helper and `./gradlew :treecore:dumpTrees -Pspecies=oak|large -Pseeds=N -Pheight=H`.
  - 65 tests pass (60 in `treecore`, 5 in `tools/viewer`); `./gradlew build` passes with `treecore` compiled into both loader jars.
- **Phase 0, T11–T12** (uncommitted):
  - T11 JMH benchmark (`./gradlew :treecore:jmh`): small 7 µs, medium 18 µs, large 58 µs per tree, 0 bytes
    allocated per tree. Details in `phase-0-results.md`.
  - T12 species drafts as JSON in `tools/viewer/src/main/resources/species/`: oak, fancy_oak, birch,
    tall_birch, test_28_tips. They are the exact `trunk_placer` and `foliage_placer` objects for Phase 2.
    All stay inside their section 15 budgets over 1,000 seeds (`ViewerTest`).
    `./gradlew :tools:viewer:dumpSpecies -Pspecies=birch -Pseeds=8` draws one to PNG.
- Decisions Q1–Q11 in `questions.md`; paper readings 1–17 in `paper-notes.md`, all chosen.

## In progress

- **Phase 1, T1–T9 done, uncommitted.** Block side (T1–T5): P1-1 to P1-4 passed in game (the human, 2026-09-19);
  on their feedback a stub's cut end now shows the log's end grain (Q16). Placers (T6–T9): `ParamCodecs`
  (ranges checked by `treecore`, so game and viewer agree), `WeberPennTrunkPlacer`, `SkeletonFoliagePlacer`,
  `FoliageHandoff`, `TreeGenerators` (one generator per thread, chunk area from `WorldGenRegion.getCenter()`),
  `ModPlacers`, Fabric invoker mixins, `/naturaltrees place` and `grid`, `TreeGenerator.generateCluster` for the
  fallback of spec 9.1, and the test datapack `docs/test-datapacks/phase-1` (15 keys of spec 11.2).
  Tests: `ParamCodecsTest` reads the viewer's species files through the game's codecs.
  **First in-game result (the human's screenshot, 2026-09-19):** world generation with the test datapack grows
  the mod's oaks and birches: log trunks, branch-block limbs, foliage on the limbs, nothing floating, no crown
  cut in a straight line. The canopy is nearly closed because forests still use vanilla's tree count; spec 11.3
  lowers it in Phase 2. **Still waiting for the human:** the far-chunk log search (P1-8), leaf persistence at
  raised randomTickSpeed (P1-7), repeatability of `/naturaltrees place … 1` (P1-5), and their judgement of the look.
- **Phase 0, T14 tuning.** Still waiting for the human: P0-2 (seed grids), P0-3 (the outsider check), P0-4.
  With P1-5 the species can now be judged in the game itself, which is the better place.

## Blocked

- Nothing.

## Notes for future sessions

- TODO(naturaltrees): spec section 19 wants the stacked-logs recipe disabled when guita's Branches is installed.
  Not done; it needs loader-specific recipe conditions and belongs with the compatibility work of Phase 4.

- **Never commit.** The human makes every commit; suggest commit points with files and a message.
- The human delegates spec-gap and small technical decisions: decide, record in `docs/questions.md`, continue.
- The agent brief is `AGENTS.md` at the repository root (renamed from `CLAUDE.md` by the human).
- Build from the command line with a JDK 21: the machine's default `java` is 23.
  `export JAVA_HOME=~/Library/Java/JavaVirtualMachines/ms-21.0.12/Contents/Home`
- Rough timing (not JMH): small tree 0.008 ms, 28-tip tree 0.07 ms, wood and foliage, on the human's Mac.
- Tuning notes from the first dumps: the oak placeholder of spec 10 keeps only about 4 limbs at height 9,
  because limbs shorter than 2 blocks are dropped; the trunk top can show bare; low limbs carry heavy clusters.
- If the algorithm changes on purpose, regenerate `GoldenHashTest` with
  `./gradlew :treecore:test --tests '*GoldenHashTest*' -Dnaturaltrees.printGolden=true -i`.
- Build-logic names containing `multiloader` were kept on purpose. Run configurations:
  `Fabric Client (:fabric)` and `NeoForge Client (:neoforge)`.
- The paper PDF is git-ignored. To read it without system PDF tools, a throwaway Python venv with PyMuPDF works.
