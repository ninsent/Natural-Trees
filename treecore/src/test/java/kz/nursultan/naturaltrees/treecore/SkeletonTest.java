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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 7.1 and 7.3 against hand-computed cases; the paper's formulas with all variation switched off. */
class SkeletonTest {

    private static final StemParams STRAIGHT = StemParams.straight();

    private static LevelParams level(int branches, double length, double down, double downV, double rotate) {
        return new LevelParams(STRAIGHT, branches, length, 0, down, downV, rotate, 0, 0);
    }

    private static TrunkParams.Builder plain(LevelParams... levels) {
        return TrunkParams.builder().shape(Shape.CYLINDRICAL).baseSize(0.25).maxRadius(12).foliageMargin(4)
                .maxTips(48).trunk(STRAIGHT).levels(levels);
    }

    private static Skeleton generate(TrunkParams p, long seed, int height) {
        Skeleton s = new Skeleton();
        s.generate(p, new Xoroshiro128PlusPlus(seed), height);
        return s;
    }

    private static List<Stem> live(Skeleton s, int level) {
        List<Stem> out = new ArrayList<>();
        for (int i = 0; i < s.count; i++) {
            if (!s.stems[i].removed && s.stems[i].level == level) {
                out.add(s.stems[i]);
            }
        }
        return out;
    }

    private static double[] direction(Stem s, int segment) {
        double dx = s.px[segment + 1] - s.px[segment];
        double dy = s.py[segment + 1] - s.py[segment];
        double dz = s.pz[segment + 1] - s.pz[segment];
        double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return new double[] {dx / n, dy / n, dz / n};
    }

    @Test
    void straightTrunkFillsTheHeight() {
        Skeleton s = generate(plain(level(0, 0.5, 60, 0, 140)).build(), 1, 9);
        assertEquals(1, s.count);
        Stem trunk = s.stems[0];
        assertEquals(2, trunk.pointCount);
        assertEquals(0.5, trunk.py[0], 1e-12);
        assertEquals(8.5, trunk.py[1], 1e-12);
        assertEquals(0.5, trunk.px[1], 1e-12);
        assertEquals(0.5, trunk.pz[1], 1e-12);
    }

    @Test
    void childrenCountOffsetsLengthAndDownAngle() {
        Skeleton s = generate(plain(level(6, 0.5, 60, 0, 140)).build(), 3, 17);
        List<Stem> limbs = live(s, 1);
        assertEquals(6, limbs.size());
        double trunkLength = 16;
        double start = (double) 0.25f * trunkLength;
        double spacing = (trunkLength - start) / 6;
        for (int k = 0; k < 6; k++) {
            Stem limb = limbs.get(k);
            assertEquals(start + (k + 0.5) * spacing, limb.parentOffset, 1e-9);
            assertEquals(0.5 + limb.parentOffset, limb.py[0], 1e-9);
            assertEquals(trunkLength * 0.5, limb.length, 1e-9, "cylindrical shape: length is the plain fraction");
            assertEquals(Math.cos(Math.toRadians(60)), direction(limb, 0)[1], 1e-9, "down angle from a vertical trunk");
        }
    }

    @Test
    void spiralRotationOf140Degrees() {
        Skeleton s = generate(plain(level(6, 0.5, 60, 0, 140)).build(), 5, 17);
        List<Stem> limbs = live(s, 1);
        for (int k = 1; k < limbs.size(); k++) {
            double[] a = direction(limbs.get(k - 1), 0);
            double[] b = direction(limbs.get(k), 0);
            double turn = Math.toDegrees(Math.atan2(b[2], b[0]) - Math.atan2(a[2], a[0]));
            turn = ((turn % 360) + 360) % 360;
            assertEquals(140, Math.min(turn, 360 - turn), 1e-6);
        }
    }

    @Test
    void negativeRotateAlternatesSides() {
        Skeleton s = generate(plain(level(6, 0.5, 60, 0, -90)).build(), 5, 17);
        List<Stem> limbs = live(s, 1);
        for (int k = 1; k < limbs.size(); k++) {
            double[] a = direction(limbs.get(k - 1), 0);
            double[] b = direction(limbs.get(k), 0);
            assertEquals(-1, (a[0] * b[0] + a[2] * b[2]) / (Math.hypot(a[0], a[2]) * Math.hypot(b[0], b[2])), 1e-9,
                    "successive children point to opposite sides");
        }
    }

    @Test
    void negativeDownAngleVVariesAlongTheParent() {
        // Paper 4.3: large angles at the base of the crown, small ones at the top.
        Skeleton s = generate(plain(level(6, 0.5, 55, -35, 140)).build(), 5, 17);
        List<Stem> limbs = live(s, 1);
        double previous = 181;
        for (Stem limb : limbs) {
            double down = Math.toDegrees(Math.acos(direction(limb, 0)[1]));
            assertTrue(down < previous, "down angle decreases up the trunk");
            previous = down;
        }
        double ratioLowest = (16 - limbs.get(0).parentOffset) / (16 - (double) 0.25f * 16);
        double expected = 55 - 35 * (1 - 2 * (0.2 + 0.8 * ratioLowest));
        assertEquals(expected, Math.toDegrees(Math.acos(direction(limbs.get(0), 0)[1])), 1e-6);
    }

    @Test
    void conicalShapeScalesFirstLevelLengths() {
        Skeleton s = generate(plain(level(6, 0.5, 60, 0, 140)).shape(Shape.CONICAL).build(), 5, 17);
        for (Stem limb : live(s, 1)) {
            double ratio = (16 - limb.parentOffset) / (16 - (double) 0.25f * 16);
            assertEquals(16 * 0.5 * (0.2 + 0.8 * ratio), limb.length, 1e-9);
        }
    }

    @Test
    void deeperLevelsUseTheSecondLengthAndCountFormulas() {
        Skeleton s = generate(plain(level(4, 0.5, 60, 0, 140), level(4, 0.5, 45, 0, 140)).build(), 9, 33);
        List<Stem> twigs = live(s, 2);
        assertFalse(twigs.isEmpty());
        for (Stem twig : twigs) {
            Stem limb = s.stems[twig.parent];
            assertEquals(0.5 * (limb.length - 0.6 * twig.parentOffset), twig.length, 1e-9);
        }
        // Paper 4.3, first count formula: with a cylindrical shape every limb has its full length, so all 4.
        for (Stem limb : live(s, 1)) {
            int attached = 0;
            for (int a = limb.firstAttached; a >= 0; a = s.stems[a].nextSibling) {
                attached++;
            }
            assertEquals(4, attached);
        }
    }

    @Test
    void integerSplitsGiveThePapersStemCounts() {
        // Paper p.121: (seg_splits + 1) ^ (curve_res - 1) stems.
        for (long seed = 0; seed < 20; seed++) {
            TrunkParams one = plain(level(0, 0.5, 60, 0, 140)).trunk(new StemParams(0, 0, 0, 3, 1, 30, 0)).build();
            assertEquals(4, live(generate(one, seed, 13), 0).size());
            TrunkParams two = plain(level(0, 0.5, 60, 0, 140)).trunk(new StemParams(0, 0, 0, 3, 2, 30, 0)).build();
            assertEquals(9, live(generate(two, seed, 13), 0).size());
        }
    }

    @Test
    void fractionalSplitsAreDiffusedNotRandom() {
        // seg_splits 0.5 over the three segment ends of a 3-segment trunk and its clone: always 1 + 1 clones.
        TrunkParams p = plain(level(0, 0.5, 60, 0, 140)).trunk(new StemParams(0, 0, 0, 3, 0.5, 30, 0)).build();
        for (long seed = 0; seed < 50; seed++) {
            assertEquals(3, live(generate(p, seed, 13), 0).size(), "seed " + seed);
        }
    }

    @Test
    void baseSplitsReplaceSegSplitsAtTheFirstSegment() {
        TrunkParams p = plain(level(0, 0.5, 60, 0, 140)).trunk(new StemParams(0, 0, 0, 2, 0, 30, 0)).baseSplits(2).build();
        Skeleton s = generate(p, 4, 13);
        List<Stem> trunks = live(s, 0);
        assertEquals(3, trunks.size());
        for (Stem t : trunks.subList(1, 3)) {
            assertTrue(t.clone);
            assertEquals(6.0, t.startOffset, 1e-9);
            assertEquals(6.0, t.pathLength(), 1e-9);
        }
    }

    @Test
    void aSplitSharesTheChildrenBetweenItsStems() {
        TrunkParams unsplit = plain(level(8, 0.3, 60, 0, 140)).baseSize(0).build();
        TrunkParams split = plain(level(8, 0.3, 60, 0, 140)).baseSize(0)
                .trunk(new StemParams(0, 0, 0, 2, 0, 30, 0)).baseSplits(1).build();
        assertEquals(8, live(generate(unsplit, 2, 21), 1).size());
        assertEquals(8, live(generate(split, 2, 21), 1).size());
    }

    @Test
    void helixSegmentsKeepTheirDeclination() {
        TrunkParams p = plain(level(0, 0.5, 60, 0, 140)).trunk(new StemParams(0, 0, -30, 4, 0, 0, 0)).build();
        Stem trunk = generate(p, 8, 17).stems[0];
        assertEquals(5, trunk.pointCount);
        for (int i = 0; i < 4; i++) {
            assertEquals(Math.cos(Math.toRadians(30)), direction(trunk, i)[1], 1e-9);
        }
        double[] a = direction(trunk, 0);
        double[] b = direction(trunk, 1);
        double turn = Math.toDegrees(Math.atan2(b[2], b[0]) - Math.atan2(a[2], a[0]));
        assertEquals(90, Math.abs(((turn + 540) % 360) - 180), 1e-6, "one full turn over four segments");
    }

    @Test
    void curveAndCurveBackMakeAnSCurve() {
        TrunkParams p = plain(level(0, 0.5, 60, 0, 140)).trunk(new StemParams(40, -40, 0, 4, 0, 0, 0)).build();
        Stem trunk = generate(p, 8, 17).stems[0];
        double[] decl = new double[4];
        for (int i = 0; i < 4; i++) {
            decl[i] = Math.toDegrees(Math.acos(direction(trunk, i)[1]));
        }
        assertEquals(0, decl[0], 1e-9);
        assertEquals(20, decl[1], 1e-9);
        assertEquals(0, decl[2], 1e-9);
        assertEquals(20, decl[3], 1e-9);
    }

    @Test
    void attractionUpBendsTwigsUpward() {
        LevelParams limbs = level(6, 0.6, 80, 0, 140);
        LevelParams twigs = new LevelParams(new StemParams(0, 0, 0, 4, 0, 0, 0), 4, 0.8, 0, 30, 0, 140, 0, 0);
        double without = meanFinalRise(plain(limbs, twigs).attractionUp(0).build());
        double with = meanFinalRise(plain(limbs, twigs).attractionUp(1).build());
        double against = meanFinalRise(plain(limbs, twigs).attractionUp(-1).build());
        assertTrue(with > without + 0.2, with + " vs " + without);
        assertTrue(against < without - 0.2, against + " vs " + without);
    }

    private static double meanFinalRise(TrunkParams p) {
        double sum = 0;
        int n = 0;
        for (long seed = 0; seed < 10; seed++) {
            for (Stem twig : live(generate(p, seed, 41), 2)) {
                if (twig.pointCount > 2) {
                    sum += direction(twig, twig.pointCount - 2)[1];
                    n++;
                }
            }
        }
        assertTrue(n > 20, "too few curved twigs: " + n);
        return sum / n;
    }

    @Test
    void voxelScaleRules() {
        for (long seed = 0; seed < 200; seed++) {
            Skeleton s = generate(TestSpecies.largeBuilder().maxTips(48).build(), seed, 20);
            for (int i = 0; i < s.count; i++) {
                Stem stem = s.stems[i];
                assertTrue(stem.pathLength() >= Skeleton.MIN_STEM_LENGTH - 1e-9, "stem shorter than 2 blocks");
                int segments = (int) Math.round(stem.length / (stem.offset[1] - stem.offset[0]));
                assertTrue(segments == 1 || segments <= Math.floor(stem.length / 2), "curve_res cap");
                assertTrue(stem.parent < stem.id, "parent before child");
            }
        }
    }

    @Test
    void tipBudgetHoldsAndRemovesDeepestShortestFirst() {
        for (long seed = 0; seed < 200; seed++) {
            Skeleton s = generate(TestSpecies.oak(), seed, 9);
            assertTrue(s.liveCount <= 8);
            assertFalse(s.stems[0].removed, "the trunk is never removed");
            int live = 0;
            for (int i = 0; i < s.count; i++) {
                Stem stem = s.stems[i];
                if (stem.removed) {
                    continue;
                }
                live++;
                assertTrue(stem.parent < 0 || !s.stems[stem.parent].removed, "a live stem hangs from a removed one");
            }
            assertEquals(s.liveCount, live);
            assertEquals(s.count - live, s.removedByBudget);
        }

        // Conical crown, 6 limbs of decreasing length, budget 4: the three shortest limbs go, top first.
        TrunkParams p = plain(level(6, 0.5, 60, 0, 140)).shape(Shape.CONICAL).maxTips(4).build();
        Skeleton s = generate(p, 1, 17);
        List<Stem> kept = live(s, 1);
        assertEquals(3, kept.size());
        for (int i = 0; i < s.count; i++) {
            if (s.stems[i].removed) {
                assertTrue(s.stems[i].length < kept.get(kept.size() - 1).length);
            }
        }

        // With twigs present, every twig goes before any limb.
        TrunkParams q = plain(level(4, 0.5, 60, 0, 140), level(4, 0.5, 45, 0, 140)).maxTips(5).build();
        Skeleton t = generate(q, 1, 33);
        assertEquals(4, live(t, 1).size());
        assertEquals(0, live(t, 2).size());
    }

    @Test
    void sameSeedSameSkeletonDifferentSeedDifferent() {
        Skeleton a = generate(TestSpecies.oak(), 77, 9);
        Skeleton b = generate(TestSpecies.oak(), 77, 9);
        Skeleton c = generate(TestSpecies.oak(), 78, 9);
        assertEquals(a.count, b.count);
        boolean differs = a.count != c.count;
        for (int i = 0; i < a.count; i++) {
            for (int k = 0; k < a.stems[i].pointCount; k++) {
                assertEquals(a.stems[i].px[k], b.stems[i].px[k]);
                assertEquals(a.stems[i].py[k], b.stems[i].py[k]);
                assertEquals(a.stems[i].pz[k], b.stems[i].pz[k]);
            }
            if (!differs && i < c.count && a.stems[i].pointCount > 1 && c.stems[i].pointCount > 1) {
                differs = a.stems[i].px[1] != c.stems[i].px[1];
            }
        }
        assertTrue(differs);
        assertNotEquals(0, a.count);
    }

    @Test
    void aReusedSkeletonForgetsThePreviousTree() {
        Skeleton reused = new Skeleton();
        reused.generate(TestSpecies.largeBuilder().build(), new Xoroshiro128PlusPlus(5), 20);
        reused.generate(TestSpecies.oak(), new Xoroshiro128PlusPlus(6), 9);
        Skeleton fresh = generate(TestSpecies.oak(), 6, 9);
        assertEquals(fresh.count, reused.count);
        assertEquals(fresh.liveCount, reused.liveCount);
        for (int i = 0; i < fresh.count; i++) {
            assertEquals(fresh.stems[i].removed, reused.stems[i].removed);
            assertEquals(fresh.stems[i].attachedCount, reused.stems[i].attachedCount);
            for (int k = 0; k < fresh.stems[i].pointCount; k++) {
                assertEquals(fresh.stems[i].px[k], reused.stems[i].px[k]);
            }
        }
    }
}
