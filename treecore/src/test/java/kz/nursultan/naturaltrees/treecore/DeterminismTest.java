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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Spec 7.7 and spec section 2, principle 1. */
class DeterminismTest {

    private static final WorldRead ABOVE_GROUND = (x, y, z) -> y >= 0;

    private static long oak(TreeGenerator g, long seed) {
        return g.generate(TestSpecies.oak(), TestSpecies.oakFoliage(), seed, 9, PlacementLimits.NONE,
                ABOVE_GROUND, ABOVE_GROUND).contentHash();
    }

    private static long large(TreeGenerator g, long seed) {
        return g.generate(TestSpecies.largeBuilder().build(), TestSpecies.largeFoliage(), seed, 20,
                PlacementLimits.NONE, ABOVE_GROUND, ABOVE_GROUND).contentHash();
    }

    @Test
    void sameParametersAndSeedGiveTheSameTree() {
        for (long seed = 0; seed < 100; seed++) {
            assertEquals(oak(new TreeGenerator(), seed), oak(new TreeGenerator(), seed));
            assertEquals(large(new TreeGenerator(), seed), large(new TreeGenerator(), seed));
        }
    }

    @Test
    void differentSeedsGiveDifferentTrees() {
        TreeGenerator g = new TreeGenerator();
        Set<Long> oaks = new HashSet<>();
        Set<Long> larges = new HashSet<>();
        for (long seed = 0; seed < 100; seed++) {
            oaks.add(oak(g, seed));
            larges.add(large(g, seed));
        }
        assertEquals(100, oaks.size());
        assertEquals(100, larges.size());
    }

    @Test
    void differentParametersGiveDifferentTrees() {
        TreeGenerator g = new TreeGenerator();
        long a = oak(g, 5);
        long b = g.generate(TestSpecies.oakBuilder().baseSize(0.2).build(), TestSpecies.oakFoliage(), 5, 9,
                PlacementLimits.NONE, ABOVE_GROUND, ABOVE_GROUND).contentHash();
        assertNotEquals(a, b);
    }

    /** Tree B generated after tree A is the tree B generated alone: buffers are reused, results are not. */
    @Test
    void nothingIsCarriedBetweenTrees() {
        TreeGenerator reused = new TreeGenerator();
        for (long seed = 0; seed < 100; seed++) {
            // A is large, obstructed and leafy, so it leaves as much as possible behind in every buffer.
            WorldRead holes = (x, y, z) -> y >= 0 && (x + y + z) % 5 != 0;
            reused.generate(TestSpecies.largeBuilder().trunkWidth(2, 2).build(), TestSpecies.largeFoliage(), seed + 1000, 24,
                    PlacementLimits.chunkArea(3, 5, 0, 0), holes, holes);
            long after = oak(reused, seed);
            assertEquals(oak(new TreeGenerator(), seed), after, "seed " + seed);
            assertEquals(large(new TreeGenerator(), seed), large(reused, seed), "seed " + seed);
        }
    }

    @Test
    void generatorsOnSeveralThreadsAgree() throws Exception {
        long[] expected = new long[64];
        TreeGenerator g = new TreeGenerator();
        for (int seed = 0; seed < expected.length; seed++) {
            expected[seed] = large(g, seed);
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<long[]>> runs = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                runs.add(pool.submit(() -> {
                    TreeGenerator own = new TreeGenerator();
                    long[] hashes = new long[expected.length];
                    for (int seed = 0; seed < hashes.length; seed++) {
                        hashes[seed] = large(own, seed);
                    }
                    return hashes;
                }));
            }
            for (Future<long[]> run : runs) {
                org.junit.jupiter.api.Assertions.assertArrayEquals(expected, run.get());
            }
        } finally {
            pool.shutdown();
        }
    }
}
