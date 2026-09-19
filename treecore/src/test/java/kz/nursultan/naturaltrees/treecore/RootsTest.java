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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The {@code roots} object of the trunk placer: wood only, below the crown, and nothing else about the tree changes. */
class RootsTest {

    private static final RootParams ROOTS = new RootParams(5, 4.0, 4.0, 3, 0.5);

    private static long key(int x, int y, int z) {
        return ((long) (x + 64) << 40) | ((long) (y + 64) << 20) | (z + 64);
    }

    @Test
    void rootsAddWoodAndChangeNothingElseAboutTheTree() {
        TreeGenerator plain = new TreeGenerator();
        TreeGenerator rooted = new TreeGenerator();
        TrunkParams without = TestSpecies.largeBuilder().build();
        TrunkParams with = without.toBuilder().roots(ROOTS).build();
        WorldRead ground = (x, y, z) -> y >= 0;
        for (long seed = 0; seed < 300; seed++) {
            TreeResult a = plain.generate(without, TestSpecies.largeFoliage(), seed, 20, PlacementLimits.NONE, ground, ground);
            TreeResult b = rooted.generate(with, TestSpecies.largeFoliage(), seed, 20, PlacementLimits.NONE, ground, ground);
            assertTrue(b.woodCount() > a.woodCount(), "seed " + seed + ": no root wood");
            // Roots are emitted last, so the rest of the tree is the same wood in the same order.
            for (int i = 0; i < a.woodCount(); i++) {
                assertEquals(key(a.woodX(i), a.woodY(i), a.woodZ(i)), key(b.woodX(i), b.woodY(i), b.woodZ(i)), "seed " + seed);
                assertEquals(a.isBranch(i), b.isBranch(i));
            }
            // A root is not a tip: no attachment, no foliage of its own, no load on the trunk.
            assertEquals(a.tipCount(), b.tipCount());
            for (int i = 0; i < a.tipCount(); i++) {
                assertEquals(key(a.tipX(i), a.tipY(i), a.tipZ(i)), key(b.tipX(i), b.tipY(i), b.tipZ(i)));
            }
            for (int i = a.woodCount(); i < b.woodCount(); i++) {
                assertTrue(b.woodY(i) >= 0 && b.woodY(i) <= ROOTS.height() + 1, "root wood at y " + b.woodY(i));
                int reach = (int) Math.ceil(ROOTS.spread()) + 2;
                assertTrue(Math.abs(b.woodX(i)) <= reach && Math.abs(b.woodZ(i)) <= reach, "root wood beyond its spread");
            }
        }
    }

    @Test
    void rootsGoDownWhereTheGroundFallsAwayAndNeverFloat() {
        TreeGenerator g = new TreeGenerator();
        TrunkParams p = TestSpecies.largeBuilder().roots(ROOTS).build();
        // A bank: solid ground under the trunk and to the west, water or air three blocks deep to the east.
        WorldRead bank = (x, y, z) -> y >= 0 || (x >= 2 && y >= -3);
        int below = 0;
        for (long seed = 0; seed < 300; seed++) {
            TreeResult r = g.generate(p, TestSpecies.largeFoliage(), seed, 20, PlacementLimits.NONE, bank, bank);
            Set<Long> wood = new HashSet<>();
            for (int i = 0; i < r.woodCount(); i++) {
                assertTrue(bank.test(r.woodX(i), r.woodY(i), r.woodZ(i)), "wood in the ground");
                assertTrue(r.woodY(i) >= -ROOTS.depth(), "a root deeper than roots.depth");
                below += r.woodY(i) < 0 ? 1 : 0;
                wood.add(key(r.woodX(i), r.woodY(i), r.woodZ(i)));
            }
            Set<Long> seen = new HashSet<>();
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            queue.add(new int[] {0, 0, 0});
            seen.add(key(0, 0, 0));
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
        }
        assertTrue(below > 300, "roots should reach below the trunk's base where they can, was " + below);
    }

    @Test
    void rootsAreDeterministicAndIndependentOfTheTreeBefore() {
        TrunkParams p = TestSpecies.largeBuilder().roots(ROOTS).build();
        WorldRead ground = (x, y, z) -> y >= 0;
        TreeGenerator fresh = new TreeGenerator();
        TreeGenerator used = new TreeGenerator();
        used.generate(p, TestSpecies.largeFoliage(), 99L, 22, PlacementLimits.NONE, ground, ground);
        TreeResult a = fresh.generate(p, TestSpecies.largeFoliage(), 7L, 20, PlacementLimits.NONE, ground, ground);
        TreeResult b = used.generate(p, TestSpecies.largeFoliage(), 7L, 20, PlacementLimits.NONE, ground, ground);
        assertEquals(a.woodCount(), b.woodCount());
        assertEquals(a.leafCount(), b.leafCount());
        for (int i = 0; i < a.woodCount(); i++) {
            assertEquals(key(a.woodX(i), a.woodY(i), a.woodZ(i)), key(b.woodX(i), b.woodY(i), b.woodZ(i)));
            assertEquals(a.woodArms(i), b.woodArms(i));
        }
    }

    @Test
    void rangesAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> new RootParams(9, 3, 3, 2, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new RootParams(3, 0.5, 3, 2, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new RootParams(3, 3, 9, 2, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new RootParams(3, 3, 3, 7, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new RootParams(3, 3, 3, 2, 1.5));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> TestSpecies.largeBuilder().maxRadius(3).roots(new RootParams(3, 3, 4, 2, 0.5)).build());
        assertTrue(e.getMessage().startsWith("roots.spread:"), e.getMessage());
        assertEquals(0, RootParams.NONE.count());
    }
}
