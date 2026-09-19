# Plan — Phase 4: Felling and compatibility

Spec section 18, Phase 4: built-in felling; tests against common felling mods; branch blocks for modded woods,
through an Every Compat module or addon mods that instantiate `BranchBlock`. Spec section 14 defines felling;
spec 16 says the config is server-side and holds only the `felling.*` keys. The spec gives this phase no exit
criterion; the ones below follow from section 14.

**Status: approved 2026-09-19, in progress.** Done: T1–T5, T7, and the skeleton of T6. The human answered Q26 (the
Fabric config must work with Mod Menu: a settings screen and a `modmenu` entrypoint now exist), accepted Q27 and
delegated Q28. T8 (Every Compat) is **not** started: Q29 is unanswered. T9 is **dropped**: the human does not want
compatibility code for guita's Branches (Q30).
Waiting for the human: P4-1 to P4-3.

Earlier phases are not closed; everything open there waits for the human (the in-game tests P1-5, P1-7, P2-1, P2-2,
P3-1 to P3-4, the measurement table, the name reservation). Nothing in Phase 4 depends on them.

## Exit criteria

| Criterion | How it is met |
|---|---|
| With felling disabled (the default) nothing changes: cutting the trunk leaves limbs and foliage in place (14.1) | Test P4-1 |
| Built-in felling fells one tree along its structure, never a neighbour whose crown merely touches, never a build (14.3) | Unit tests of the search on hand-built block sets; test P4-2 |
| The guards and the pacing hold: `max_blocks`, `min_leaves`, `blocks_per_tick`, one durability per block | Unit tests; test P4-2 |
| With a known felling mod installed, the built-in feature logs a notice and stays off unless `felling.force` (14.2) | Test P4-3 |
| The three or four most used felling mods fell the mod's trees, or their limits are documented (14.2) | Test P4-3, results in `docs/felling-mods.md` |
| A wood mod can get branch blocks without touching this mod's code | T7; test P4-4 if the human wants the Every Compat module |

## Decisions taken while planning (delegated; recorded in `questions.md` as Q26–Q29)

| # | Gap | Decision |
|---|---|---|
| Q26 | Where the five `felling.*` keys live. Spec 22 item 4 speaks of NeoForge's native config screen and a hand-written Fabric one, so the spec expects NeoForge's own config system. | NeoForge: a `ModConfigSpec` of type SERVER. Fabric has no config API and the mod may add no dependency: a small key = value file, `config/naturaltrees-server.toml`, written with the defaults and comments on first start, read by a few lines of code in the `fabric` module. `common` sees only a `FellingSettings` record through the platform service. |
| Q27 | Which mods count as "known felling mods" (14.2). | Detected by mod id: `fallingtree` (FallingTree), `treechop` (HT's TreeChop), `treeharvester` (Tree Harvester). The ids are checked against the real jars in test P4-3 before the list is final; a fourth is added if the human names one. |
| Q28 | When the search runs. NeoForge's break event fires before the block is gone and can be cancelled by others; Fabric offers an after-break event. | The break only **queues** a felling at that position; the first server tick after it checks that the block is really gone and that the player still holds an axe, then searches. One code path for both loaders, and a cancelled break fells nothing. |
| Q29 | Modded woods. `BranchBlock` takes a value of the closed `BranchWood` enum, so no other mod can instantiate it. Every Compat is a third-party API this project has never compiled against. | First make `BranchBlock` independent of the enum: it takes the log it copies and, for a plain branch, a supplier of its stripped variant; the enum stays as this mod's own table. That alone lets an addon mod create branches, which is what spec section 18 asks for at minimum. The Every Compat module is a separate, optional step (T8) that adds a compile-only dependency on another mod: **it is done only if the human says so**, after the agent has read that mod's API and licence and reported what the module would need. A stable Java API stays out of scope (spec 22, item 17). |

## Tasks

| # | Task | Spec | Acceptance check |
|---|---|---|---|
| T1 | **Settings.** `FellingSettings(enabled, force, maxBlocks, minLeaves, blocksPerTick)` with the defaults of 14.3 (`false`, `false`, 512, 8, 32) and sane ranges; NeoForge `ModConfigSpec`, Fabric file (Q26). | 14.3, 16 | Unit test of the Fabric reader (defaults written, values read, bad values fall back with a warning); both clients start. |
| T2 | **The search**, in `common`, package `felling`, which `block` does not depend on (spec section 4). From a log: adjacent logs among the 26 neighbours, none below the broken block, and adjacent branches that have an arm pointing back at it. From a branch: only along its arms. It stops at `max_blocks`. It returns the wood in breadth-first order and the count of non-persistent leaves touching it. It reads blocks through a small interface, so it is tested without the game. | 14.3 | Unit tests on hand-built sets: one tree is found whole; a second tree whose crown touches is not entered; two trees joined by a real arm are; nothing below the cut; a branch beside a log without an arm toward it is skipped; the cap cuts the result off; a build of logs with persistent leaves has 0 leaves counted. |
| T3 | **The trigger and the guards.** A player breaks a log or a branch with an axe while not sneaking; felling is enabled and not suppressed (T5); the found wood touches at least `min_leaves` non-persistent leaves. | 14.3 | Unit tests of the guard logic; P4-2. |
| T4 | **Pacing.** A per-level queue; each server tick breaks up to `blocks_per_tick` blocks of each job in search order with `destroyBlock`, so drops appear where the blocks were; one durability per block, and the job ends when the tool breaks, the player leaves, or a block is no longer wood. Leaves are left to decay the vanilla way. | 14.3 | Unit test of the job's bookkeeping; P4-2 (a large tree comes down over several ticks, the axe loses one durability per block). |
| T5 | **Third-party felling mods.** On server start, if a known mod (Q27) is loaded: one INFO notice naming it, and the built-in feature stays off unless `felling.force`. | 14.2 | P4-3. |
| T6 | **Compatibility notes**, `docs/felling-mods.md`: for each tested mod, whether it fells the mod's trees whole, its block cap against the large trees of spec 15 (up to 400 wood voxels), and whether it assumes full cubes. | 14.2 | Filled in from P4-3. |
| T7 | **`BranchBlock` without the enum** (Q29), with a short `docs/addon-woods.md`: what an addon registers (two blocks, two items), which tags, recipes, models and loot it supplies, how its trees name the branch in `branch_provider`. `tools/assetgen` is pointed out as the template for the files. | 18 | The build and every existing test pass unchanged; `BranchShapeTest` still covers the shapes. |
| T8 | **Every Compat module** — only on the human's word (Q29). First a written report: that mod's API for registering a block per wood type, its licence, and what assets it generates at runtime. | 18 | Decided after the report. |
| T9 | ~~The recipe clash with guita's Branches (spec section 19).~~ **Dropped on the human's word (Q30):** no detection, no recipe condition. | 19 | — |

Order: T1 → T2 → T3 → T4 → T5 → (P4-1, P4-2) → T7 → (P4-3) → T6 → T8 if wanted.

## Manual tests the human will be asked to run

```
P4-1 — the default                 felling disabled (default): cut a fancy oak's trunk. The limbs and leaves stay in the
                                   air, as a vanilla fancy oak's do. Nothing else happens.
P4-2 — built-in felling            set felling.enabled = true (NeoForge: the server config file; Fabric:
                                   config/naturaltrees-server.toml), restart. With an axe, not sneaking, cut a large tree
                                   at the base: all its wood comes down over a second or two, drops lie where the wood was,
                                   the axe loses one durability per block, leaves decay afterwards. A neighbouring tree whose
                                   crown touches stays. Sneaking cuts one block. A log cabin with placed leaves is never felled.
                                   A tree above max_blocks stops at the cap.
P4-3 — other felling mods          one at a time: FallingTree, HT's TreeChop, Tree Harvester. The log shows the mod's notice and
                                   the built-in feature is off; note whether the mod fells our trees whole and what it leaves.
                                   With felling.force = true both run; note what happens.
P4-4 — a modded wood (if wanted)   a wood mod plus the compat module: its branches exist, craft, strip and burn.
```

## Out of scope for this phase

Fast leaf decay, per-tool rules and a client hint of what will fall (spec 22, item 13); config screens (22, item 4);
a stable addon API (22, item 17); runtime adoption of other mods' trees (22, item 15).
