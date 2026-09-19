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
 * One entry of {@code levels} (spec 8.2): the stems of one level below the trunk. Angles are degrees.
 *
 * @param stem curvature and splits
 * @param branches children per parent stem, before the voxel-scale rules, 0 to 32
 * @param length length as a fraction of the parent's, 0 to 1
 * @param lengthV random variation of {@code length}, 0 to 1
 * @param downAngle angle from the parent's axis
 * @param downAngleV variation of the down angle; negative varies the angle along the parent, as in the paper
 * @param rotate rotation around the parent between successive children; negative alternates sides
 * @param rotateV random variation of {@code rotate}
 * @param tipRadiusOffset {@code radiusOffset} of this level's foliage attachments, -2 to 1
 */
public record LevelParams(StemParams stem, int branches, double length, double lengthV, double downAngle,
                          double downAngleV, double rotate, double rotateV, int tipRadiusOffset) {

    public LevelParams {
        Checks.notNull("level", stem);
        Checks.range("branches", branches, 0, 32);
        length = Checks.range("length", length, 0.0, 1.0);
        lengthV = Checks.range("length_v", lengthV, 0.0, 1.0);
        downAngle = Checks.finite("down_angle", downAngle);
        downAngleV = Checks.finite("down_angle_v", downAngleV);
        rotate = Checks.finite("rotate", rotate);
        rotateV = Checks.finite("rotate_v", rotateV);
        Checks.range("tip_radius_offset", tipRadiusOffset, -2, 1);
    }
}
