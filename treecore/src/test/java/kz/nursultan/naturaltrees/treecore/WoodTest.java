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

/** Spec 7.2 (pipe model), 7.4 (rasterisation and arms) and 7.5 (obstruction and limits). */
class WoodTest {

    private static final WorldRead ABOVE_GROUND = (x, y, z) -> y >= 0;
    private static final int[][] FACES = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    private static final int[] FACE_ARMS = {Arms.DOWN, Arms.UP, Arms.NORTH, Arms.SOUTH, Arms.WEST, Arms.EAST};

    static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }

    private static TreeResult grow(TreeGenerator g, TrunkParams p, long seed, int height) {
        return g.generate(p, null, seed, height, PlacementLimits.NONE, ABOVE_GROUND, ABOVE_GROUND);
    }

    @Test
    void pipeModelTable() {
        TrunkParams p = TestSpecies.oak();
        for (int load = 1; load <= 64; load++) {
            double r = WoodBuilder.radius(p, load);
            if (load <= 3) {
                assertTrue(r < 0.5, "load " + load + " is a branch");
            } else if (load <= 15) {
                assertTrue(r >= 0.5 && r < 1.0, "load " + load + " is a 1×1 log");
            } else {
                assertTrue(r >= 1.0, "load " + load + " is a 2×2 trunk");
            }
        }
        TreeGenerator g = new TreeGenerator();
        grow(g, p, 1, 9);
        assertEquals(4, g.wood.logLoad);
        assertEquals(16, g.wood.wideLoad);
    }

    @Test
    void thicknessOnlyStepsDownTowardATip() {
        TreeGenerator g = new TreeGenerator();
        for (long seed = 0; seed < 1000; seed++) {
            grow(g, TestSpecies.largeBuilder().build(), seed, 20);
            for (int i = 0; i < g.skeleton.count; i++) {
                Stem s = g.skeleton.stems[i];
                if (s.removed) {
                    continue;
                }
                assertTrue(s.startOffset <= s.wideEnd && s.wideEnd <= s.logEnd && s.logEnd <= s.endOffset() + 1e-9);
                int previous = Integer.MAX_VALUE;
                for (int k = 0; k <= 20; k++) {
                    int load = g.wood.loadAt(s, s.startOffset + s.pathLength() * k / 20.0);
                    assertTrue(load <= previous && load >= 1, "load rises toward the tip");
                    previous = load;
                }
                assertEquals(1, g.wood.loadAt(s, s.endOffset()), "a tip carries itself");
                if (s.level > 0) {
                    assertEquals(s.startOffset, s.wideEnd, 0.0, "only level-0 stems may be 2×2");
                    // Limbs follow the table exactly: logs while the load is 4 or more.
                    assertTrue(s.logEnd == s.startOffset || g.wood.loadAt(s, s.logEnd) >= 4);
                    assertTrue(s.logEnd >= s.endOffset() || g.wood.loadAt(s, s.logEnd + 1e-6) < 4);
                } else {
                    assertEquals(s.endOffset(), s.logEnd, 1e-9, "level 0 is never thinner than a log");
                    assertTrue(s.wideEnd == s.startOffset || g.wood.loadAt(s, s.wideEnd) >= 16);
                    assertTrue(s.wideEnd >= s.endOffset() || g.wood.loadAt(s, s.wideEnd + 1e-6) < 16);
                }
            }
        }
    }

    @Test
    void branchBlocksCarryAtMostThreeTips() {
        TreeGenerator g = new TreeGenerator();
        for (long seed = 0; seed < 300; seed++) {
            TreeResult r = grow(g, TestSpecies.largeBuilder().build(), seed, 20);
            for (int i = 0; i < r.woodCount(); i++) {
                if (r.isBranch(i)) {
                    assertTrue(r.woodLoad(i) <= 3, "branch with load " + r.woodLoad(i));
                    assertTrue(r.woodArms(i) != 0, "a generated branch always has an arm");
                } else {
                    assertEquals(0, r.woodArms(i));
                }
            }
        }
    }

    @Test
    void workedExamplesOfSpec72() {
        TreeGenerator g = new TreeGenerator();
        int wideTrunks = 0;
        int logLimbs = 0;
        for (long seed = 0; seed < 200; seed++) {
            // 8 tips: a 1×1 trunk and branch-block limbs.
            grow(g, TestSpecies.oak(), seed, 9);
            for (int i = 0; i < g.skeleton.count; i++) {
                Stem s = g.skeleton.stems[i];
                if (!s.removed) {
                    assertEquals(s.startOffset, s.wideEnd, 0.0);
                }
            }
            // 14 tips: a limb carrying four or more tips starts as logs and turns into branch blocks after it forks.
            // The tip budget removes twigs before limbs, so the example needs few limbs with several twigs each.
            TrunkParams medium = TestSpecies.largeBuilder().shape(Shape.CYLINDRICAL).maxTips(14).levels(
                    new LevelParams(new StemParams(-20, 0, 30, 3, 0, 0, 0), 3, 0.7, 0.05, 60, 10, 140, 20, 0),
                    new LevelParams(new StemParams(0, 0, 30, 2, 0, 0, 0), 4, 0.6, 0.05, 45, 10, 140, 30, 0)).build();
            grow(g, medium, seed, 16);
            assertEquals(g.skeleton.stems[0].startOffset, g.skeleton.stems[0].wideEnd, 0.0, "14 tips: 1×1 trunk");
            for (int i = 1; i < g.skeleton.count; i++) {
                Stem s = g.skeleton.stems[i];
                if (!s.removed && s.level == 1 && s.baseLoad >= 4) {
                    logLimbs++;
                    assertTrue(s.logEnd > s.startOffset && s.logEnd < s.endOffset());
                }
            }
            // 28 tips: a 2×2 lower trunk that narrows to 1×1 where the load falls below 16.
            grow(g, TestSpecies.largeBuilder().build(), seed, 20);
            Stem trunk = g.skeleton.stems[0];
            if (trunk.baseLoad >= 16) {
                wideTrunks++;
                assertTrue(trunk.wideEnd > 0 && trunk.wideEnd < trunk.endOffset());
            }
        }
        assertTrue(wideTrunks > 150, "the 28-tip species should usually reach 16 tips, was " + wideTrunks);
        assertTrue(logLimbs > 50, "log-based limbs should occur, was " + logLimbs);
    }

    @Test
    void wideSectionsFollowTheOriginConvention() {
        TreeGenerator g = new TreeGenerator();
        for (long seed = 0; seed < 100; seed++) {
            TreeResult r = grow(g, TestSpecies.largeBuilder().trunkWidth(2, 2).trunk(StemParams.straight()).build(), seed, 20);
            Set<Long> wood = new HashSet<>();
            for (int i = 0; i < r.woodCount(); i++) {
                wood.add(key(r.woodX(i), r.woodY(i), r.woodZ(i)));
            }
            // trunk_width_min 2 without trunk_leader: 2×2 to the top, in the columns at +x, +z and +x+z.
            for (int y = 0; y < 20; y++) {
                assertTrue(wood.contains(key(0, y, 0)) && wood.contains(key(1, y, 0))
                        && wood.contains(key(0, y, 1)) && wood.contains(key(1, y, 1)), "layer " + y);
            }
            boolean wideTip = false;
            for (int i = 0; i < r.tipCount(); i++) {
                wideTip |= r.tipOnWideTrunk(i);
            }
            assertTrue(wideTip, "the trunk's tip is on the 2×2 section");
        }
    }

    @Test
    void trunkWidthMaxOneNeverGivesAWideTrunk() {
        TreeGenerator g = new TreeGenerator();
        for (long seed = 0; seed < 100; seed++) {
            TreeResult r = grow(g, TestSpecies.largeBuilder().trunkWidth(1, 1).build(), seed, 20);
            assertEquals(0.0, g.skeleton.stems[0].wideEnd, 0.0);
            for (int i = 0; i < r.tipCount(); i++) {
                assertFalse(r.tipOnWideTrunk(i));
            }
        }
    }

    @Test
    void trunkLeaderThinsTheTopToBranchBlocks() {
        TreeGenerator g = new TreeGenerator();
        for (long seed = 0; seed < 100; seed++) {
            TreeResult r = grow(g, TestSpecies.largeBuilder().trunkLeader(true).build(), seed, 20);
            Stem trunk = g.skeleton.stems[0];
            assertTrue(trunk.logEnd < trunk.endOffset(), "the leader is branch blocks where the load is below 4");
            assertTrue(g.wood.loadAt(trunk, trunk.logEnd + 1e-6) < 4);
            // The trunk is emitted first, so the first tip is the trunk's.
            assertTrue(isBranchAt(r, r.tipX(0), r.tipY(0), r.tipZ(0)));

            // questions.md Q6: with trunk_width_min 2 the bare trunk stays 2×2, then the pipe model takes over.
            grow(g, TestSpecies.largeBuilder().trunkLeader(true).trunkWidth(2, 2).build(), seed, 20);
            Stem wide = g.skeleton.stems[0];
            assertTrue(wide.wideEnd > 0 && wide.wideEnd <= wide.logEnd && wide.logEnd < wide.endOffset());
        }
    }

    private static boolean isBranchAt(TreeResult r, int x, int y, int z) {
        for (int i = 0; i < r.woodCount(); i++) {
            if (r.woodX(i) == x && r.woodY(i) == y && r.woodZ(i) == z) {
                return r.isBranch(i);
            }
        }
        throw new AssertionError("no wood at the tip");
    }

    @Test
    void logAxisIsTheDominantComponentAndHorizontalWinsADiagonal() {
        assertEquals(TreeResult.AXIS_Y, WoodBuilder.dominantAxis(0.2, 1, -0.3));
        assertEquals(TreeResult.AXIS_X, WoodBuilder.dominantAxis(-1, 0.5, 0.3));
        assertEquals(TreeResult.AXIS_Z, WoodBuilder.dominantAxis(0.1, 0.5, -2));
        assertEquals(TreeResult.AXIS_X, WoodBuilder.dominantAxis(1, 1, 0));
        assertEquals(TreeResult.AXIS_Z, WoodBuilder.dominantAxis(0, -1, 1));
        assertEquals(TreeResult.AXIS_X, WoodBuilder.dominantAxis(1, 1, 1));

        TreeGenerator g = new TreeGenerator();
        TreeResult r = grow(g, TestSpecies.oakBuilder().trunk(StemParams.straight()).build(), 1, 9);
        for (int i = 0; i < 9; i++) {
            assertEquals(TreeResult.AXIS_Y, r.woodAxis(i), "a straight trunk is upright logs");
        }
    }

    /** Spec 7.4 over many seeds: connectivity of each stem's voxels, and arms that follow the skeleton only. */
    @Test
    void connectivityAndArms() {
        TreeGenerator g = new TreeGenerator();
        int unjoinedNeighbours = 0;
        for (long seed = 0; seed < 1000; seed++) {
            TrunkParams p = seed % 2 == 0 ? TestSpecies.oak() : TestSpecies.largeBuilder().build();
            TreeResult r = grow(g, p, seed, seed % 2 == 0 ? 9 : 20);
            WoodBuilder w = g.wood;

            Map<Long, Integer> woodIndex = new HashMap<>();
            for (int i = 0; i < r.woodCount(); i++) {
                assertTrue(woodIndex.put(key(r.woodX(i), r.woodY(i), r.woodZ(i)), i) == null, "a wood voxel is listed twice");
            }

            // Pairs of voxels that the skeleton joins: neighbours in a stem, and a stem's first voxel with its parent.
            Set<Long> joined = new HashSet<>();
            for (int id = 0; id < g.skeleton.count; id++) {
                Stem s = g.skeleton.stems[id];
                int from = w.stemPathStart[id];
                int n = w.stemPathCount[id];
                if (s.removed || n == 0) {
                    continue;
                }
                boolean wide = s.wideEnd > s.startOffset;
                for (int k = 0; k < n; k++) {
                    int x = w.pathX[from + k], y = w.pathY[from + k], z = w.pathZ[from + k];
                    boolean branch = r.isBranch(woodIndex.get(key(x, y, z)));
                    if (k == 0) {
                        if (s.parent >= 0) {
                            assertTrue(touchesStem(w, s.parent, x, y, z, joined), "a child's first voxel is face-adjacent to its parent");
                        }
                        continue;
                    }
                    int px = w.pathX[from + k - 1], py = w.pathY[from + k - 1], pz = w.pathZ[from + k - 1];
                    int step = Math.abs(x - px) + Math.abs(y - py) + Math.abs(z - pz);
                    if (branch) {
                        assertEquals(1, step, "consecutive branch voxels share a face");
                    } else if (!wide) {
                        assertTrue(Math.max(Math.abs(x - px), Math.max(Math.abs(y - py), Math.abs(z - pz))) <= 1,
                                "consecutive log voxels are 26-adjacent");
                    }
                    if (step == 1) {
                        joined.add(pair(key(x, y, z), key(px, py, pz)));
                    }
                }
            }

            for (int i = 0; i < r.woodCount(); i++) {
                if (!r.isBranch(i)) {
                    continue;
                }
                long here = key(r.woodX(i), r.woodY(i), r.woodZ(i));
                for (int f = 0; f < 6; f++) {
                    long there = key(r.woodX(i) + FACES[f][0], r.woodY(i) + FACES[f][1], r.woodZ(i) + FACES[f][2]);
                    Integer neighbour = woodIndex.get(there);
                    boolean arm = (r.woodArms(i) & FACE_ARMS[f]) != 0;
                    if (arm) {
                        assertTrue(neighbour != null, "an arm points at air");
                        assertTrue(joined.contains(pair(here, there)), "an arm between stems the skeleton does not join");
                        if (r.isBranch(neighbour)) {
                            assertTrue((r.woodArms(neighbour) & Arms.opposite(FACE_ARMS[f])) != 0, "arms are reciprocal");
                        }
                    } else if (neighbour != null && r.isBranch(neighbour) && !joined.contains(pair(here, there))) {
                        unjoinedNeighbours++;
                    }
                }
            }
        }
        assertTrue(unjoinedNeighbours > 0, "the test never saw two merely adjacent branches");
    }

    private static long pair(long a, long b) {
        return Math.min(a, b) * 31 + Math.max(a, b);
    }

    private static boolean touchesStem(WoodBuilder w, int stem, int x, int y, int z, Set<Long> joined) {
        // The generator joins the child to one of the parent's voxels that share a face with it; any of them
        // is a connection the skeleton makes.
        int from = w.stemPathStart[stem];
        boolean touches = false;
        for (int k = from; k < from + w.stemPathCount[stem]; k++) {
            if (Math.abs(w.pathX[k] - x) + Math.abs(w.pathY[k] - y) + Math.abs(w.pathZ[k] - z) == 1) {
                joined.add(pair(key(x, y, z), key(w.pathX[k], w.pathY[k], w.pathZ[k])));
                touches = true;
            }
        }
        return touches;
    }

    /** Spec 7.5: with arbitrary blocked regions no wood floats, and the limits hold. */
    @Test
    void obstructionNeverLeavesFloatingWood() {
        TreeGenerator g = new TreeGenerator();
        int truncated = 0;
        for (long seed = 0; seed < 1000; seed++) {
            Xoroshiro128PlusPlus boxes = new Xoroshiro128PlusPlus(seed * 31 + 7);
            int[][] blocked = new int[6][];
            for (int b = 0; b < blocked.length; b++) {
                int cx = (int) (boxes.nextDouble() * 20) - 10, cy = (int) (boxes.nextDouble() * 22), cz = (int) (boxes.nextDouble() * 20) - 10;
                int size = 1 + (int) (boxes.nextDouble() * 4);
                blocked[b] = new int[] {cx - size, cy - size, cz - size, cx + size, cy + size, cz + size};
            }
            WorldRead world = (x, y, z) -> {
                if (y < 0) {
                    return false;
                }
                for (int[] b : blocked) {
                    if (x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5]) {
                        return false;
                    }
                }
                return true;
            };
            TrunkParams p = TestSpecies.largeBuilder().build();
            TreeResult r = g.generate(p, TestSpecies.largeFoliage(), seed, 20, PlacementLimits.NONE, world, world);
            truncated += r.stemsTruncated();

            Set<Long> wood = new HashSet<>();
            for (int i = 0; i < r.woodCount(); i++) {
                assertTrue(world.test(r.woodX(i), r.woodY(i), r.woodZ(i)), "wood in a blocked position");
                assertTrue(r.woodX(i) * r.woodX(i) + r.woodZ(i) * r.woodZ(i) <= p.maxRadius() * p.maxRadius(), "wood beyond max_radius");
                wood.add(key(r.woodX(i), r.woodY(i), r.woodZ(i)));
            }
            if (wood.isEmpty()) {
                assertFalse(world.test(0, 0, 0));
                assertEquals(0, r.tipCount());
                assertEquals(0, r.leafCount());
                continue;
            }
            // Independent flood fill from the trunk base over 26-neighbours.
            Set<Long> seen = new HashSet<>();
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            queue.add(new int[] {0, 0, 0});
            seen.add(key(0, 0, 0));
            assertTrue(wood.contains(key(0, 0, 0)));
            while (!queue.isEmpty()) {
                int[] v = queue.poll();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            long k = key(v[0] + dx, v[1] + dy, v[2] + dz);
                            if (wood.contains(k) && seen.add(k)) {
                                queue.add(new int[] {v[0] + dx, v[1] + dy, v[2] + dz});
                            }
                        }
                    }
                }
            }
            assertEquals(wood.size(), seen.size(), "seed " + seed + ": wood that is not connected to the trunk base");
            for (int i = 0; i < r.tipCount(); i++) {
                assertTrue(wood.contains(key(r.tipX(i), r.tipY(i), r.tipZ(i))), "a tip without wood");
            }
        }
        assertTrue(truncated > 500, "the blocked regions should truncate many stems, was " + truncated);
    }

    /** Spec 7.5: arithmetic tests run first; the world is never read outside the area or beyond the radius. */
    @Test
    void worldIsReadLastAndOncePerPosition() {
        TreeGenerator g = new TreeGenerator();
        for (long seed = 0; seed < 200; seed++) {
            // A trunk two blocks from the corner of the 3×3 chunk area: most of the crown is cut by the area.
            PlacementLimits area = PlacementLimits.chunkArea(-14, -14, 0, 0);
            TrunkParams p = TestSpecies.largeBuilder().build();
            Set<Long> woodReads = new HashSet<>();
            Set<Long> leafReads = new HashSet<>();
            WorldRead canPlace = (x, y, z) -> {
                assertTrue(area.contains(x, z, p.foliageMargin()), "wood read outside the area less the margin");
                assertTrue(x * x + z * z <= p.maxRadius() * p.maxRadius(), "wood read beyond max_radius");
                assertTrue(woodReads.add(key(x, y, z)), "wood position read twice");
                return y >= 0;
            };
            WorldRead canLeaf = (x, y, z) -> {
                assertTrue(area.contains(x, z, 0), "leaf read outside the area");
                assertTrue(Math.abs(x) <= 16 && Math.abs(z) <= 16, "leaf read outside the 33 × 33 map");
                assertTrue(leafReads.add(key(x, y, z)), "leaf position read twice");
                return y >= 0;
            };
            TreeResult r = g.generate(p, TestSpecies.largeFoliage(), seed, 20, area, canPlace, canLeaf);
            assertEquals(woodReads.size() + leafReads.size(), r.worldReads());
            for (int i = 0; i < r.woodCount(); i++) {
                assertTrue(area.contains(r.woodX(i), r.woodZ(i), p.foliageMargin()));
            }
            for (int i = 0; i < r.leafCount(); i++) {
                assertTrue(area.contains(r.leafX(i), r.leafZ(i), 0));
            }
        }
    }

    @Test
    void blockedExtraColumnAtTheBaseMakesTheTrunkOneByOne() {
        TreeGenerator g = new TreeGenerator();
        WorldRead world = (x, y, z) -> y >= 0 && !(x == 1 && y == 0 && z == 1);
        TreeResult r = g.generate(TestSpecies.largeBuilder().trunkWidth(2, 2).build(), null, 3, 20,
                PlacementLimits.NONE, world, world);
        assertTrue(r.wideTrunkBlockedAtBase());
        int atBase = 0;
        for (int i = 0; i < r.woodCount(); i++) {
            atBase += r.woodY(i) == 1 ? 1 : 0;
        }
        assertEquals(1, atBase, "one log per layer on the bare trunk");
        for (int i = 0; i < r.tipCount(); i++) {
            assertFalse(r.tipOnWideTrunk(i));
        }
    }

    @Test
    void tipsAreStemEndsAndStayInsideTheBudget() {
        TreeGenerator g = new TreeGenerator();
        for (long seed = 0; seed < 300; seed++) {
            TreeResult r = grow(g, TestSpecies.oak(), seed, 9);
            assertTrue(r.tipCount() >= 1 && r.tipCount() <= 8, "tips " + r.tipCount());
            assertEquals(8, r.tipY(0), "the top of the trunk is a tip");
        }
    }
}
