# Questions for the human

Places where the spec is silent, unclear or looks wrong. Nothing here is decided by the agent.

## Open

### Q1 — Seeding xoroshiro128++ from one 64-bit value (spec 7.7) — not blocking

Spec 7.7 says the trunk placer draws one `nextLong()` and "seeds an in-core xoroshiro128++ generator from it",
but xoroshiro128++ has 128 bits of state and the spec does not say how 64 bits become 128.
`Xoroshiro128PlusPlus` currently expands the seed with two steps of SplitMix64, which is what the
algorithm's authors recommend. Changing this later changes every tree, so please confirm before Phase 0 tuning.

### Q2 — Negative `curve_v` (the paper's helix mode) — not blocking

Spec 8.2 lists `curve_v` as a plain float and says nothing about negative values. In the paper (p.120, §4.1)
a negative `nCurveV` turns the stem into a helix. The spec calls out the other two negative-flag modes
explicitly (`down_angle_v`, `rotate`) but not this one. Should the codec reject a negative `curve_v`,
or should the helix mode exist? Until answered, Phase 0 will treat negative `curve_v` as invalid input.

### Q3 — Trunk length when the trunk curves — not blocking

The spec drops `Scale` and `0Length`, so the trunk's length is the free height from vanilla. Measured along
a curved trunk (`trunk.curve`, `curve_v`), the top then sits slightly below that height. I read this as
intended (the tree never exceeds the space vanilla checked). Please confirm.

## Answered

None yet.
