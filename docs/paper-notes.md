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
| 1 | p.120, §4.1 | With `nCurveBack` ≠ 0 the stem curves by `nCurve` over its "first half" and `nCurveBack` over its second; for an odd `nCurveRes` the halves are not defined. | Segment `i` (0-based) is in the first half when `i < nCurveRes / 2.0`. | proposed |
| 2 | p.120, §4.1 | A negative `nCurveV` switches to a helix mode that the paper barely describes. | Not decided: see `questions.md` Q2. | open |
| 3 | p.121, §4.2 | The printed stem-count formula reads `(nSegSplits+1)nCurveRes-1`; the worked examples (81 = 3⁴, 4 = 2²) show it is `(nSegSplits+1)^(nCurveRes−1)`. | Typesetting loss only; no effect on code. | chosen |
| 4 | p.121, §4.2 | The split error-diffusion value is "global" per recursive level. | One error value per level **per tree**, reset to 0 at the start of every tree, so nothing carries between trees (spec section 2, principle 1). | proposed |
| 5 | p.121, §4.2 | The rotation of a stem about the tree-parallel axis after a split, `20 + 0.75 × (30 + |declination − 90|) × RANDOM²` with random sign, is given only for "the normal case of a single clone". | One clone: as printed. Several clones at one segment: stem and clones are spread evenly around the axis, each with the printed formula's random part as jitter. | proposed |
| 6 | p.121, §4.2 | `angle_split` is "later distributed over the remaining segments in the reverse direction". | Each remaining segment curves back by `angle_split / remaining_segments`. | proposed |
| 7 | p.121, §4.3 | "Any stem that has been cloned or is, itself, a clone reduces its propensity to form clones by half" sits in the paragraph about the number of children. | Literal: such a stem uses half of `nSegSplits` from then on. | proposed |
| 8 | p.121, §4.3 | How children are spaced along the parent is not stated. | Evenly along the part of the parent that may bear children: above `BaseSize` on the trunk, the whole length elsewhere. | proposed |
| 9 | p.121, §4.3 | Two formulas give the number of children, "for the first level of branches" and "for further levels"; the text calls them "really grandchildren". | The trunk bears exactly `1Branches` children. A level-1 stem bears children by the first formula, deeper stems by the second. | proposed |
| 10 | p.121–122, §4.3 | `length_base` in the length and down-angle formulas is defined through `Scale`, which the spec does not use. | `scale_tree` is the free height vanilla passes in; `length_trunk` equals it (the spec has no `0Length`); `length_base = base_size × height`. For a non-trunk parent `length_base` is 0. See `questions.md` Q3. | proposed |
| 11 | p.123–124, §4.8 | The vertical-attraction formula mixes radians and degrees in the text around it. | `declination` and `orientation` in radians, `curve_up` in radians, as printed; applied to level 2 and deeper only (paper and spec 8.2 agree). | proposed |
