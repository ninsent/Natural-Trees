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

/** The foliage placer's parameters (spec 9.2). */
public record FoliageParams(double foliageStart, double trunkFoliage, double radiusBase, double radiusTip,
                            double radiusV, double flatten, double lift, double density, int smother,
                            int maxDistance, int maxLeaves) {

    public static final double DEFAULT_FOLIAGE_START = 0.5;
    public static final double DEFAULT_TRUNK_FOLIAGE = 0.15;
    public static final double DEFAULT_RADIUS_BASE = 1.0;
    public static final double DEFAULT_RADIUS_TIP = 2.2;
    public static final double DEFAULT_RADIUS_V = 0.2;
    public static final double DEFAULT_FLATTEN = 0.7;
    public static final double DEFAULT_LIFT = 0.3;
    public static final double DEFAULT_DENSITY = 0.8;
    public static final int DEFAULT_SMOTHER = 4;
    public static final int DEFAULT_MAX_DISTANCE = 5;

    public FoliageParams {
        foliageStart = Checks.range("foliage_start", foliageStart, 0.0, 1.0);
        trunkFoliage = Checks.range("trunk_foliage", trunkFoliage, 0.0, 1.0);
        radiusBase = Checks.range("radius_base", radiusBase, 0.5, 4.0);
        radiusTip = Checks.range("radius_tip", radiusTip, 0.5, 4.0);
        radiusV = Checks.range("radius_v", radiusV, 0.0, 0.5);
        flatten = Checks.range("flatten", flatten, 0.3, 1.5);
        lift = Checks.range("lift", lift, -1.0, 1.0);
        density = Checks.range("density", density, 0.0, 1.0);
        Checks.range("smother", smother, 0, 16);
        Checks.range("max_distance", maxDistance, 1, 6);
        Checks.range("max_leaves", maxLeaves, 1, 4096);
    }

    /** The defaults of spec 9.2 with the given leaf budget, the one field without a default. */
    public static FoliageParams defaults(int maxLeaves) {
        return new FoliageParams(DEFAULT_FOLIAGE_START, DEFAULT_TRUNK_FOLIAGE, DEFAULT_RADIUS_BASE,
                DEFAULT_RADIUS_TIP, DEFAULT_RADIUS_V, DEFAULT_FLATTEN, DEFAULT_LIFT, DEFAULT_DENSITY,
                DEFAULT_SMOTHER, DEFAULT_MAX_DISTANCE, maxLeaves);
    }

    /**
     * The smallest {@code foliage_margin} that this foliage needs: {@code radius_tip × (1 + radius_v)},
     * rounded up (spec 9.2). The trunk placer warns when its margin is lower.
     */
    public int requiredMargin() {
        return (int) StrictMath.ceil(radiusTip * (1.0 + radiusV));
    }
}
