# Progress

Last updated: 2026-09-19

## Done

- **Milestone 0, steps 1–7.** Documents in `docs/`; wizard output audited; target set to Minecraft 1.21.1
  (NeoForge 21.1.251, NeoForm 1.21.1-20240808.144430, Parchment 2024.11.17, Fabric loader 0.19.5,
  Fabric API 0.116.17+1.21.1, Loom 1.11.8, ModDevGradle 2.0.147, Gradle 8.14.3); `forge` removed;
  names fixed (`naturaltrees`, `kz.nursultan.naturaltrees`, `NaturalTrees`); template placeholders removed;
  `LICENSE`, `README.md`, Gradle wrapper added; `treecore` project added and merged into both loader jars.
- `./gradlew clean build` passes; `treecore` has its first class (`Xoroshiro128PlusPlus`, spec 7.7) with tests.

## In progress

- **Milestone 0, step 8.** Waiting for the human to run manual tests M0-1 (Fabric) and M0-2 (NeoForge),
  then the first commit.

## Next

- Read-through notes on the paper into `docs/paper-notes.md`, then `docs/plan-phase-0.md` for approval.

## Blocked

- Nothing. Q1 in `docs/questions.md` is open but not blocking.

## Notes for future sessions

- Build from the command line with a JDK 21: the machine's default `java` is 23.
  `export JAVA_HOME=~/Library/Java/JavaVirtualMachines/ms-21.0.12/Contents/Home`
- Build-logic names containing `multiloader` (`buildSrc/.../multiloader-common.gradle`,
  `multiloader-loader.gradle`, and the plugin ids that use them) were kept on purpose: they name the
  build pattern and are not placeholders.
- Run configurations: `Fabric Client (:fabric)` and `NeoForge Client (:neoforge)`.
