# Notes on Weber & Penn 1995

J. Weber, J. Penn, "Creation and Rendering of Realistic Trees", SIGGRAPH '95, pp. 119–128,
doi:10.1145/218380.218427. The PDF is kept locally as `docs/weber-penn-1995.pdf` and is not in version control.

Precedence is spec, then paper, then judgement. This file records where the paper is ambiguous, the page,
and the reading chosen. Entries marked **proposed** come from the first full read-through (2026-09-19)
and become **chosen** when the code that depends on them is written in Phase 0.

## What the spec takes from the paper

Spec 7.1 uses: §4.1 curved stem, §4.2 splits, §4.3 children (count, length, down angle, rotation,
`ShapeRatio` shapes 0–7), §4.8 vertical attraction. It does not use: §4.4 radius, taper, flare and lobes
(replaced by the pipe model, spec 7.2), §4.5 leaves and §4.9 leaf orientation (replaced by spec 7.6),
§4.6 pruning and shape 8 (replaced by `max_radius`), §4.7 wind, §5 range degradation, `Scale`/`ScaleV`.

## Ambiguities

| # | Page, section | Ambiguity | Reading | Status |
|---|---|---|---|---|
| 1 | p.120, §4.1 | With `nCurveBack` ≠ 0 the stem curves by `nCurve` over its "first half" and `nCurveBack` over its second; for an odd `nCurveRes` the halves are not defined. | Segment `i` (0-based) is in the first half when `i < nCurveRes / 2.0`. | chosen |
| 2 | p.120, §4.1 | A negative `nCurveV` forms the stem as a helix; the paper gives only that its declination is the magnitude of `nCurveV`. Turns, handedness and start angle are not stated. Supported by decision Q2. | The helix axis is the stem's initial direction. Every segment is inclined to that axis by `|nCurveV|` degrees. The azimuth about the axis starts at a random angle, advances by `360 / nCurveRes` per segment (one full turn per stem), and the handedness is random per stem. `nCurve` and `nCurveBack` are ignored in this mode. | chosen |
| 3 | p.121, §4.2 | The printed stem-count formula reads `(nSegSplits+1)nCurveRes-1`; the worked examples (81 = 3⁴, 4 = 2²) show it is `(nSegSplits+1)^(nCurveRes−1)`. | Typesetting loss only; no effect on code. | chosen |
| 4 | p.121, §4.2 | The split error-diffusion value is "global" per recursive level. | One error value per level **per tree**, reset to 0 at the start of every tree, so nothing carries between trees (spec section 2, principle 1). | chosen |
| 5 | p.121, §4.2 | The rotation of a stem about the tree-parallel axis after a split, `20 + 0.75 × (30 + |declination − 90|) × RANDOM²` with random sign, is given only for "the normal case of a single clone". | One clone: as printed. Several clones at one segment: stem and clones are spread evenly around the axis, each with the printed formula's random part as jitter. | chosen |
| 6 | p.121, §4.2 | `angle_split` is "later distributed over the remaining segments in the reverse direction". | Each remaining segment curves back by `angle_split / remaining_segments`. | chosen |
| 7 | p.121, §4.3 | "Any stem that has been cloned or is, itself, a clone reduces its propensity to form clones by half" sits in the paragraph about the number of children. Read literally (half the split rate) it contradicts the paper's own example on the same page: `SegSplits` 1 with `CurveRes` 3 gives 4 stems, which needs the full split rate at the second segment. | It is the density of **children** that drops: after a split into `n + 1` stems, each of them spaces its children `n + 1` times wider, staggered, so together they bear what the unsplit stem would have. The split rate is never reduced. | chosen |
| 8 | p.121, §4.3 | How children are spaced along the parent is not stated. | Evenly along the part of the parent that may bear children: above `BaseSize` on the trunk, the whole length elsewhere. | chosen |
| 9 | p.121, §4.3 | Two formulas give the number of children, "for the first level of branches" and "for further levels"; the text calls them "really grandchildren". | The trunk bears exactly `1Branches` children. A level-1 stem bears children by the first formula, deeper stems by the second. | chosen |
| 10 | p.121–122, §4.3 | `length_base` in the length and down-angle formulas is defined through `Scale`, which the spec does not use. | `scale_tree` is the free height vanilla passes in; `length_trunk` equals it (the spec has no `0Length`); `length_base = base_size × height`. For a non-trunk parent `length_base` is 0. See `questions.md` Q3. | chosen |
| 11 | p.123–124, §4.8 | The vertical-attraction formula mixes radians and degrees in the text around it. | `declination` and `orientation` in radians, `curve_up` in radians, as printed; applied to level 2 and deeper only (paper and spec 8.2 agree). | chosen |
| 12 | p.121, §4.2 | `nBaseSplits` is "the equivalent of `nSegSplits` at the end of the first segment of the trunk". Does 0 mean "no split there" or "not set"? | `base_splits` replaces `seg_splits` at that one place only when it is greater than 0. The paper's Figure 1 example (`0SegSplits` 1, `0CurveRes` 3 → 4 stems, `BaseSplits` unset) needs `seg_splits` to act at the first segment. | chosen |
| 13 | p.122, §4.3 | With a negative `nDownAngleV` the formula is written with "±" like the random case. | It is not random: `down = nDownAngle + nDownAngleV × (1 − 2 × ShapeRatio(conical, ratio))`. With the paper's own values this gives large angles at the base of the crown and small ones at the top, which is the effect the text describes. | chosen |
| 14 | p.124, §4.8 | `curve_up` is "added to the segment's curvature", but with the paper's rotation convention (a positive rotation about x turns the stem toward its −y axis, which is what makes a positive `nCurve` bend a limb down, as the sample trees need) adding it would bend a stem **down** for a positive `AttractionUp`. | The stem is rotated by `−curve_up`, so that a positive `attraction_up` bends upward and a negative one droops, as the text says. | chosen |
| 15 | p.122, §4.3 | Where the spiral of children starts is not stated. | Every stem draws a random start angle for its children; the trunk's frame is also turned by a random angle about the vertical, so that curvature has no preferred compass direction. | chosen |
| 16 | p.120, §4.1 | "The z-axis of each segment is rotated away from the z-axis of the previous segment": does the first segment curve? | No: it keeps the direction the stem starts with, so `down_angle` means what it says. Curvature, its variation, split compensation and vertical attraction apply from the second segment on. A stem of one segment is straight. | chosen |
| 17 | p.121, §4.3 | The number of children is a real number. | Rounded to the nearest integer. Children sit at `start + (k + 0.5) × spacing`, so none sits exactly at the base or the tip of the bearing part. | chosen |

