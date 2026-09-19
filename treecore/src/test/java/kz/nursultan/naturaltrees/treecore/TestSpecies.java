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

/** Parameter sets for tests. {@link #oak()} is the example species of spec section 10. */
final class TestSpecies {

    private TestSpecies() {
    }

    static TrunkParams.Builder oakBuilder() {
        return TrunkParams.builder()
                .shape(Shape.SPHERICAL)
                .baseSize(0.35)
                .attractionUp(0.3)
                .trunkWidth(1, 1)
                .maxRadius(5)
                .foliageMargin(3)
                .maxTips(8)
                .trunk(new StemParams(0, 0, 15, 3, 0.0, 0, 0))
                .levels(
                        new LevelParams(new StemParams(-20, 0, 40, 3, 0.2, 30, 10), 6, 0.45, 0.1, 55, -35, 140, 20, 0),
                        new LevelParams(new StemParams(0, 0, 30, 2, 0.0, 0, 0), 2, 0.4, 0.1, 40, 10, 140, 30, 0));
    }

    static TrunkParams oak() {
        return oakBuilder().build();
    }

    static FoliageParams oakFoliage() {
        return FoliageParams.defaults(250);
    }

    /** A large species whose tip budget of 28 gives a 2×2 lower trunk (spec 7.2, spec 18 exit criterion). */
    static TrunkParams.Builder largeBuilder() {
        return TrunkParams.builder()
                .shape(Shape.HEMISPHERICAL)
                .baseSize(0.3)
                .attractionUp(0.4)
                .trunkWidth(1, 2)
                .maxRadius(12)
                .foliageMargin(4)
                .maxTips(28)
                .trunk(new StemParams(0, 0, 10, 4, 0.0, 0, 0))
                .levels(
                        new LevelParams(new StemParams(-25, 0, 40, 4, 0.3, 35, 10), 12, 0.5, 0.1, 60, -30, 140, 20, 0),
                        new LevelParams(new StemParams(0, 0, 30, 2, 0.0, 0, 0), 4, 0.45, 0.1, 45, 10, 140, 30, 0));
    }

    static FoliageParams largeFoliage() {
        return FoliageParams.defaults(1200);
    }
}
