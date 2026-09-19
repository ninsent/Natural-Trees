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
  - 60 tests pass; `./gradlew build` passes with `treecore` compiled into both loader jars.
- Decisions Q1–Q11 in `questions.md`; paper readings 1–17 in `paper-notes.md`, all chosen.

## In progress

- **Phase 0.** Next: T12 species as JSON (oak, fancy oak, birch, tall birch, 28-tip test species),
  T13 viewer (`tools/viewer`), T11 JMH benchmark, T14 tuning with the human.

## Blocked

- Nothing.

## Notes for future sessions

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
