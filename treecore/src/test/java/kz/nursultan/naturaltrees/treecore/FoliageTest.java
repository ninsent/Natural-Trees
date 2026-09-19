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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 7.6. */
class FoliageTest {

    private static final WorldRead ABOVE_GROUND = (x, y, z) -> y >= 0;
    private static final int[][] FACES = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private static FoliageParams foliage(double density, int smother, int maxDistance, int maxLeaves) {
        return new FoliageParams(0.5, 0.15, 1.0, 2.2, 0.2, 0.7, 0.3, density, smother, maxDistance, maxLeaves);
    }

    /** Face distance of every leaf from wood, by a search that shares nothing with the generator. */
    private static Map<Long, Integer> independentDistances(TreeResult r) {
        Set<Long> leaves = new HashSet<>();
        for (int i = 0; i < r.leafCount(); i++) {
            leaves.add(WoodTest.key(r.leafX(i), r.leafY(i), r.leafZ(i)));
        }
        Map<Long, Integer> distance = new HashMap<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int i = 0; i < r.woodCount(); i++) {
            queue.add(new int[] {r.woodX(i), r.woodY(i), r.woodZ(i), 0});
        }
        while (!queue.isEmpty()) {
            int[] v = queue.poll();
            for (int[] f : FACES) {
                long k = WoodTest.key(v[0] + f[0], v[1] + f[1], v[2] + f[2]);
                if (leaves.contains(k) && !distance.containsKey(k)) {
                    distance.put(k, v[3] + 1);
                    queue.add(new int[] {v[0] + f[0], v[1] + f[1], v[2] + f[2], v[3] + 1});
                }
            }
        }
        return distance;
    }

    @Test
    void everyLeafIsWithinReachOfWoodAndLimitsHold() {
        TreeGenerator g = new TreeGenerator();
        for (long seed = 0; seed < 500; seed++) {
            boolean large = seed % 2 == 1;
            int maxDistance = 1 + (int) (seed % 6);
            int smother = (int) (seed % 5);
            FoliageParams f = foliage(0.8, smother, maxDistance, large ? 400 : 250);
            TreeResult r = g.generate(large ? TestSpecies.largeBuilder().build() : TestSpecies.oak(), f, seed,
                    large ? 20 : 9, PlacementLimits.NONE, ABOVE_GROUND, ABOVE_GROUND);

            assertTrue(r.leafCount() <= f.maxLeaves(), "max_leaves");
            Set<Long> wood = new HashSet<>();
            for (int i = 0; i < r.woodCount(); i++) {
                wood.add(WoodTest.key(r.woodX(i), r.woodY(i), r.woodZ(i)));
            }
            Map<Long, Integer> distance = independentDistances(r);
            Map<Long, Integer> columnTop = new HashMap<>();
            Set<Long> leaves = new HashSet<>();
            int previous = 1;
            for (int i = 0; i < r.leafCount(); i++) {
                long k = WoodTest.key(r.leafX(i), r.leafY(i), r.leafZ(i));
                assertTrue(leaves.add(k), "a leaf is listed twice");
                assertFalse(wood.contains(k), "a leaf where wood is");
                Integer d = distance.get(k);
                assertTrue(d != null, "seed " + seed + ": a leaf that no chain of leaves connects to wood");
                assertTrue(d <= maxDistance, "a leaf beyond max_distance");
                assertEquals(d.intValue(), r.leafDistance(i), "reported leaf distance");
                assertTrue(r.leafDistance(i) >= previous, "leaves are ordered nearest first");
                previous = r.leafDistance(i);
                assertTrue(Math.abs(r.leafX(i)) <= 16 && Math.abs(r.leafZ(i)) <= 16, "a leaf outside the 33 × 33 map");
            }
            if (smother > 0) {
                for (int i = 0; i < r.leafCount(); i++) {
                    int above = 0;
                    for (int y = r.leafY(i) + 1; y < 200; y++) {
                        above += leaves.contains(WoodTest.key(r.leafX(i), y, r.leafZ(i))) ? 1 : 0;
                    }
                    assertTrue(above <= smother, "a leaf with " + above + " leaves above it, smother " + smother);
                }
            }
        }
    }

    @Test
    void smotherZeroDisablesSelfShading() {
        TreeGenerator g = new TreeGenerator();
        int shaded = 0;
        for (long seed = 0; seed < 50; seed++) {
            TreeResult off = g.generate(TestSpecies.largeBuilder().build(), foliage(0.8, 0, 5, 4096), seed, 20,
                    PlacementLimits.NONE, ABOVE_GROUND, ABOVE_GROUND);
            assertEquals(0, off.leavesDiscardedByShade());
            int unshaded = off.leafCount();
            TreeResult on = g.generate(TestSpecies.largeBuilder().build(), foliage(0.8, 1, 5, 4096), seed, 20,
                    PlacementLimits.NONE, ABOVE_GROUND, ABOVE_GROUND);
            shaded += on.leavesDiscardedByShade();
            assertTrue(on.leafCount() <= unshaded);
        }
        assertTrue(shaded > 0);
    }

    @Test
    void densityOneKeepsTheWholeShell() {
        TreeGenerator g = new TreeGenerator();
        for (long seed = 0; seed < 50; seed++) {
            TreeResult thin = g.generate(TestSpecies.oak(), foliage(0.5, 0, 6, 4096), seed, 9,
                    PlacementLimits.NONE, ABOVE_GROUND, ABOVE_GROUND);
            Set<Long> thinned = new HashSet<>();
            for (int i = 0; i < thin.leafCount(); i++) {
                thinned.add(WoodTest.key(thin.leafX(i), thin.leafY(i), thin.leafZ(i)));
            }
            TreeResult full = g.generate(TestSpecies.oak(), foliage(1.0, 0, 6, 4096), seed, 9,
                    PlacementLimits.NONE, ABOVE_GROUND, ABOVE_GROUND);
            Set<Long> all = new HashSet<>();
            for (int i = 0; i < full.leafCount(); i++) {
                all.add(WoodTest.key(full.leafX(i), full.leafY(i), full.leafZ(i)));
            }
            assertTrue(all.containsAll(thinned));
            assertTrue(all.size() > thinned.size());
            assertEquals(full.leafCandidates(), full.leafCount() + full.leavesDiscardedByReach());
        }
    }

    @Test
    void leafBudgetCutsFromTheOutsideIn() {
        TreeGenerator g = new TreeGenerator();
        for (long seed = 0; seed < 50; seed++) {
            TreeResult all = g.generate(TestSpecies.oak(), foliage(0.8, 4, 5, 4096), seed, 9,
                    PlacementLimits.NONE, ABOVE_GROUND, ABOVE_GROUND);
            int total = all.leafCount();
            Map<Long, Integer> everyLeaf = new HashMap<>();
            for (int i = 0; i < total; i++) {
                everyLeaf.put(WoodTest.key(all.leafX(i), all.leafY(i), all.leafZ(i)), all.leafDistance(i));
            }
            int budget = total / 2;
            TreeResult cut = g.generate(TestSpecies.oak(), foliage(0.8, 4, 5, budget), seed, 9,
                    PlacementLimits.NONE, ABOVE_GROUND, ABOVE_GROUND);
            assertEquals(budget, cut.leafCount());
            assertEquals(total - budget, cut.leavesDiscardedByBudget());
            int farthestKept = cut.leafDistance(budget - 1);
            Set<Long> kept = new HashSet<>();
            for (int i = 0; i < budget; i++) {
                kept.add(WoodTest.key(cut.leafX(i), cut.leafY(i), cut.leafZ(i)));
            }
            for (Map.Entry<Long, Integer> e : everyLeaf.entrySet()) {
                if (e.getValue() < farthestKept) {
                    assertTrue(kept.contains(e.getKey()), "a nearer leaf was cut before a farther one");
                }
            }
        }
    }

    @Test
    void noFoliageParametersNoLeavesSameWood() {
        TreeGenerator g = new TreeGenerator();
        TreeResult with = g.generate(TestSpecies.oak(), TestSpecies.oakFoliage(), 12, 9, PlacementLimits.NONE, ABOVE_GROUND, ABOVE_GROUND);
        int wood = with.woodCount();
        int lastX = with.woodX(wood - 1), lastY = with.woodY(wood - 1), lastZ = with.woodZ(wood - 1);
        TreeResult without = g.generate(TestSpecies.oak(), null, 12, 9, PlacementLimits.NONE, ABOVE_GROUND, ABOVE_GROUND);
        assertEquals(0, without.leafCount());
        assertEquals(wood, without.woodCount());
        assertEquals(lastX, without.woodX(wood - 1));
        assertEquals(lastY, without.woodY(wood - 1));
        assertEquals(lastZ, without.woodZ(wood - 1));
    }

    @Test
    void fallbackClusterIsFoliageAroundOneVoxel() {
        TreeGenerator g = new TreeGenerator();
        // A real tree first, so that the cluster has to cope with used buffers.
        g.generate(TestSpecies.largeBuilder().build(), TestSpecies.largeFoliage(), 1, 20, PlacementLimits.NONE, ABOVE_GROUND, ABOVE_GROUND);
        FoliageParams f = foliage(0.8, 4, 5, 40);
        TreeResult r = g.generateCluster(f, 9, PlacementLimits.NONE, (x, y, z) -> y >= -1);
        assertEquals(0, r.woodCount());
        assertEquals(0, r.tipCount());
        assertTrue(r.leafCount() > 10 && r.leafCount() <= 40, "leaves " + r.leafCount());
        long hash = r.contentHash();
        Set<Long> leaves = new HashSet<>();
        for (int i = 0; i < r.leafCount(); i++) {
            assertFalse(r.leafX(i) == 0 && r.leafY(i) == 0 && r.leafZ(i) == 0, "a leaf in the wood voxel");
            assertTrue(r.leafY(i) >= -1, "a leaf where the world forbids it");
            assertTrue(Math.abs(r.leafX(i)) <= 3 && Math.abs(r.leafZ(i)) <= 3, "inside radius_tip");
            leaves.add(WoodTest.key(r.leafX(i), r.leafY(i), r.leafZ(i)));
        }
        // Every leaf connects to the voxel through leaves, within max_distance.
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {0, 0, 0, 0});
        while (!queue.isEmpty()) {
            int[] v = queue.poll();
            for (int[] face : FACES) {
                long k = WoodTest.key(v[0] + face[0], v[1] + face[1], v[2] + face[2]);
                if (leaves.contains(k) && seen.add(k)) {
                    assertTrue(v[3] + 1 <= 5);
                    queue.add(new int[] {v[0] + face[0], v[1] + face[1], v[2] + face[2], v[3] + 1});
                }
            }
        }
        assertEquals(leaves.size(), seen.size());
        assertEquals(hash, new TreeGenerator().generateCluster(f, 9, PlacementLimits.NONE, (x, y, z) -> y >= -1).contentHash(),
                "the same on a fresh generator");
    }

    @Test
    void noLeavesHangFromALimbThatWasNotPlaced() {
        // Everything above y = 5 is blocked: limbs are cut there, and no leaf may appear above the reach of what was placed.
        TreeGenerator g = new TreeGenerator();
        WorldRead lowCeiling = (x, y, z) -> y >= 0 && y <= 5;
        for (long seed = 0; seed < 50; seed++) {
            TreeResult r = g.generate(TestSpecies.oak(), TestSpecies.oakFoliage(), seed, 9, PlacementLimits.NONE, lowCeiling, ABOVE_GROUND);
            for (int i = 0; i < r.woodCount(); i++) {
                assertTrue(r.woodY(i) <= 5);
            }
            Map<Long, Integer> distance = independentDistances(r);
            assertEquals(r.leafCount(), distance.size());
        }
    }
}
