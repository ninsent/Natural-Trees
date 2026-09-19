/*
 * Copyright 2026 Nursultan Akim
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kz.nursultan.naturaltrees.treecore;

/**
 * The curvature and split fields that the trunk and every level share (spec 8.2). Angles are degrees.
 *
 * @param curve total curvature over the stem
 * @param curveBack curvature of the second half of the stem, for an S-curve; 0 turns it off
 * @param curveV random curvature over the stem; a negative value forms the stem as a helix
 * @param curveRes segments per stem, 1 to 8
 * @param segSplits average splits per segment, 0 to 2
 * @param splitAngle angle of a split
 * @param splitAngleV random variation of the split angle
 */
public record StemParams(double curve, double curveBack, double curveV, int curveRes, double segSplits,
                         double splitAngle, double splitAngleV) {

    public StemParams {
        curve = Checks.finite("curve", curve);
        curveBack = Checks.finite("curve_back", curveBack);
        curveV = Checks.finite("curve_v", curveV);
        Checks.range("curve_res", curveRes, 1, 8);
        segSplits = Checks.range("seg_splits", segSplits, 0.0, 2.0);
        splitAngle = Checks.finite("split_angle", splitAngle);
        splitAngleV = Checks.finite("split_angle_v", splitAngleV);
    }

    /** A straight stem of one segment without splits. */
    public static StemParams straight() {
        return new StemParams(0, 0, 0, 1, 0, 0, 0);
    }
}
