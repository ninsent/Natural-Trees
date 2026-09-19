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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Xoroshiro128PlusPlusTest {

    // Reference values computed with an independent implementation of the published algorithm.

    @Test
    void rawStateMatchesReferenceSequence() {
        Xoroshiro128PlusPlus rng = new Xoroshiro128PlusPlus(0L);
        rng.setState(1L, 2L);
        assertArrayEquals(
                new long[] {393217L, 669327710093319L, 1732421326133921491L, -7051953992050424633L},
                take(rng, 4));
    }

    @Test
    void seedingMatchesSplitMix64Reference() {
        assertArrayEquals(
                new long[] {8027914721839836897L, -4641210657545349971L, 5256508173613850168L},
                take(new Xoroshiro128PlusPlus(0L), 3));
        assertArrayEquals(
                new long[] {-1690267358668702685L, 6098722386207918385L, -905081495677017275L},
                take(new Xoroshiro128PlusPlus(42L), 3));
        assertArrayEquals(
                new long[] {-5145538296528975598L, -7877794946629591436L, 8721079291393070325L},
                take(new Xoroshiro128PlusPlus(-1L), 3));
    }

    @Test
    void sameSeedGivesSameSequenceAndDifferentSeedsDiffer() {
        assertArrayEquals(take(new Xoroshiro128PlusPlus(7L), 64), take(new Xoroshiro128PlusPlus(7L), 64));
        assertNotEquals(new Xoroshiro128PlusPlus(7L).nextLong(), new Xoroshiro128PlusPlus(8L).nextLong());
    }

    @Test
    void setSeedRestartsTheSequence() {
        Xoroshiro128PlusPlus rng = new Xoroshiro128PlusPlus(5L);
        long[] first = take(rng, 16);
        rng.setSeed(99L);
        rng.nextLong();
        rng.setSeed(5L);
        assertArrayEquals(first, take(rng, 16));
    }

    @Test
    void nextDoubleIsInUnitInterval() {
        Xoroshiro128PlusPlus rng = new Xoroshiro128PlusPlus(123L);
        double sum = 0;
        for (int i = 0; i < 10_000; i++) {
            double d = rng.nextDouble();
            assertTrue(d >= 0.0 && d < 1.0, "out of range: " + d);
            sum += d;
        }
        assertEquals(0.5, sum / 10_000, 0.02);
    }

    private static long[] take(Xoroshiro128PlusPlus rng, int n) {
        long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = rng.nextLong();
        }
        return out;
    }
}
