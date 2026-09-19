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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

/** Spec 8.2 and 9.2: every range boundary is accepted, every value just outside is rejected by field name. */
class ParamsTest {

    private static final StemParams STEM = StemParams.straight();
    private static final LevelParams LEVEL = new LevelParams(STEM, 4, 0.5, 0.1, 45, 10, 140, 20, 0);

    private static void doubles(String field, double min, double max, DoubleFunction<Object> make) {
        assertDoesNotThrow(() -> make.apply(min), field + " min");
        assertDoesNotThrow(() -> make.apply(max), field + " max");
        for (double bad : new double[] {StrictMath.nextDown((float) min), StrictMath.nextUp((float) max), Double.NaN}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> make.apply(bad),
                    field + " accepts " + bad);
            assertTrue(e.getMessage().startsWith(field + ":"), e.getMessage());
        }
    }

    private static void ints(String field, int min, int max, IntFunction<Object> make) {
        assertDoesNotThrow(() -> make.apply(min), field + " min");
        assertDoesNotThrow(() -> make.apply(max), field + " max");
        for (int bad : new int[] {min - 1, max + 1}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> make.apply(bad),
                    field + " accepts " + bad);
            assertTrue(e.getMessage().startsWith(field + ":"), e.getMessage());
        }
    }

    @Test
    void stemRanges() {
        ints("curve_res", 1, 8, v -> new StemParams(0, 0, 0, v, 0, 0, 0));
        doubles("seg_splits", 0, 2, v -> new StemParams(0, 0, 0, 1, v, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new StemParams(Double.POSITIVE_INFINITY, 0, 0, 1, 0, 0, 0));
        assertDoesNotThrow(() -> new StemParams(-400, 720, -30, 1, 0, -10, 500));
    }

    @Test
    void levelRanges() {
        ints("branches", 0, 32, v -> new LevelParams(STEM, v, 0.5, 0, 0, 0, 0, 0, 0));
        doubles("length", 0, 1, v -> new LevelParams(STEM, 1, v, 0, 0, 0, 0, 0, 0));
        doubles("length_v", 0, 1, v -> new LevelParams(STEM, 1, 0.5, v, 0, 0, 0, 0, 0));
        ints("tip_radius_offset", -2, 1, v -> new LevelParams(STEM, 1, 0.5, 0, 0, 0, 0, 0, v));
        assertDoesNotThrow(() -> new LevelParams(STEM, 1, 0.5, 0, 55, -35, -120, 30, 0));
    }

    @Test
    void trunkRanges() {
        doubles("base_size", 0, 0.9, v -> TestSpecies.oakBuilder().baseSize(v).build());
        doubles("attraction_up", -3, 3, v -> TestSpecies.oakBuilder().attractionUp(v).build());
        doubles("twig_radius", 0.1, 0.5, v -> TestSpecies.oakBuilder().twigRadius(v).build());
        doubles("pipe_exponent", 1.5, 3.5, v -> TestSpecies.oakBuilder().pipeExponent(v).build());
        ints("max_radius", 2, 14, v -> TestSpecies.oakBuilder().maxRadius(v).foliageMargin(1).build());
        ints("foliage_margin", 1, 6, v -> TestSpecies.oakBuilder().foliageMargin(v).build());
        ints("max_tips", 1, 48, v -> TestSpecies.oakBuilder().maxTips(v).build());
        ints("base_splits", 0, 3, v -> TestSpecies.oakBuilder().baseSplits(v).build());
        ints("trunk_width_max", 1, 2, v -> TestSpecies.oakBuilder().trunkWidth(1, v).build());
        ints("trunk_width_min", 1, 2, v -> TestSpecies.oakBuilder().trunkWidth(v, 2).build());
        assertThrows(IllegalArgumentException.class, () -> TestSpecies.oakBuilder().trunkWidth(2, 1).build());
    }

    @Test
    void reachRuleReportsBothNumbers() {
        assertDoesNotThrow(() -> TestSpecies.oakBuilder().maxRadius(12).foliageMargin(4).build());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> TestSpecies.oakBuilder().maxRadius(12).foliageMargin(5).build());
        assertTrue(e.getMessage().contains("12") && e.getMessage().contains("5"), e.getMessage());
    }

    @Test
    void levelsListIsOneToThree() {
        assertThrows(IllegalArgumentException.class, () -> TestSpecies.oakBuilder().levels(List.of()).build());
        assertThrows(IllegalArgumentException.class,
                () -> TestSpecies.oakBuilder().levels(LEVEL, LEVEL, LEVEL, LEVEL).build());
        assertDoesNotThrow(() -> TestSpecies.oakBuilder().levels(LEVEL, LEVEL, LEVEL).build());
    }

    @Test
    void requiredFieldsHaveNoDefault() {
        assertThrows(IllegalArgumentException.class, () -> TrunkParams.builder().build());
        assertThrows(IllegalArgumentException.class,
                () -> TrunkParams.builder().shape(Shape.CONICAL).baseSize(0.2).maxRadius(5).trunk(STEM).levels(LEVEL).build());
    }

    @Test
    void trunkDefaultsAreTheSpecs() {
        TrunkParams p = TrunkParams.builder().shape(Shape.CONICAL).baseSize(0.2).maxRadius(5).maxTips(8)
                .trunk(STEM).levels(LEVEL).build();
        assertEquals(0.0, p.attractionUp());
        assertEquals(0.25, p.twigRadius());
        assertEquals(2.0, p.pipeExponent());
        assertEquals(1, p.trunkWidthMin());
        assertEquals(2, p.trunkWidthMax());
        assertEquals(false, p.trunkLeader());
        assertEquals(3, p.foliageMargin());
        assertEquals(0, p.baseSplits());
    }

    @Test
    void foliageRangesAndDefaults() {
        doubles("foliage_start", 0, 1, v -> new FoliageParams(v, 0.15, 1, 2.2, 0.2, 0.7, 0.3, 0.8, 4, 5, 250));
        doubles("trunk_foliage", 0, 1, v -> new FoliageParams(0.5, v, 1, 2.2, 0.2, 0.7, 0.3, 0.8, 4, 5, 250));
        doubles("radius_base", 0.5, 4, v -> new FoliageParams(0.5, 0.15, v, 2.2, 0.2, 0.7, 0.3, 0.8, 4, 5, 250));
        doubles("radius_tip", 0.5, 4, v -> new FoliageParams(0.5, 0.15, 1, v, 0.2, 0.7, 0.3, 0.8, 4, 5, 250));
        doubles("radius_v", 0, 0.5, v -> new FoliageParams(0.5, 0.15, 1, 2.2, v, 0.7, 0.3, 0.8, 4, 5, 250));
        doubles("flatten", 0.3, 1.5, v -> new FoliageParams(0.5, 0.15, 1, 2.2, 0.2, v, 0.3, 0.8, 4, 5, 250));
        doubles("lift", -1, 1, v -> new FoliageParams(0.5, 0.15, 1, 2.2, 0.2, 0.7, v, 0.8, 4, 5, 250));
        doubles("density", 0, 1, v -> new FoliageParams(0.5, 0.15, 1, 2.2, 0.2, 0.7, 0.3, v, 4, 5, 250));
        ints("smother", 0, 16, v -> new FoliageParams(0.5, 0.15, 1, 2.2, 0.2, 0.7, 0.3, 0.8, v, 5, 250));
        ints("max_distance", 1, 6, v -> new FoliageParams(0.5, 0.15, 1, 2.2, 0.2, 0.7, 0.3, 0.8, 4, v, 250));
        ints("max_leaves", 1, 4096, v -> new FoliageParams(0.5, 0.15, 1, 2.2, 0.2, 0.7, 0.3, 0.8, 4, 5, v));

        FoliageParams d = FoliageParams.defaults(250);
        assertEquals(new FoliageParams(0.5, 0.15, 1.0, 2.2, 0.2, 0.7, 0.3, 0.8, 4, 5, 250), d);
        assertEquals(3, d.requiredMargin());
    }

    @Test
    void floatFieldsAreRoundedToFloatSoGameAndViewerAgree() {
        assertEquals((double) 0.35f, TestSpecies.oak().baseSize());
        assertEquals((double) 0.8f, FoliageParams.defaults(1).density());
    }

    @Test
    void shapeRatioTable() {
        assertEquals(0.2, Shape.CONICAL.ratio(0), 1e-12);
        assertEquals(1.0, Shape.CONICAL.ratio(1), 1e-12);
        assertEquals(1.0, Shape.SPHERICAL.ratio(0.5), 1e-12);
        assertEquals(0.2, Shape.SPHERICAL.ratio(0), 1e-12);
        assertEquals(1.0, Shape.HEMISPHERICAL.ratio(1), 1e-12);
        assertEquals(1.0, Shape.CYLINDRICAL.ratio(0.3), 1e-12);
        assertEquals(0.75, Shape.TAPERED_CYLINDRICAL.ratio(0.5), 1e-12);
        assertEquals(1.0, Shape.FLAME.ratio(0.7), 1e-12);
        assertEquals(0.5, Shape.FLAME.ratio(0.35), 1e-12);
        assertEquals(0.5, Shape.FLAME.ratio(0.85), 1e-12);
        assertEquals(0.2, Shape.INVERSE_CONICAL.ratio(1), 1e-12);
        assertEquals(1.0, Shape.TEND_FLAME.ratio(0.7), 1e-12);
        assertEquals(0.5, Shape.TEND_FLAME.ratio(0), 1e-12);
        assertEquals(0.75, Shape.TEND_FLAME.ratio(0.85), 1e-12);
        assertEquals(Shape.TAPERED_CYLINDRICAL, Shape.bySerializedName("tapered_cylindrical"));
        assertThrows(IllegalArgumentException.class, () -> Shape.bySerializedName("envelope"));
    }
}
