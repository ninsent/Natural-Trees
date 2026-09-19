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
 * The generator's own seeded PRNG: xoroshiro128++ (Blackman and Vigna), as spec 7.7 requires.
 *
 * <p>The 128-bit state is expanded from one 64-bit seed with two steps of SplitMix64, the seeding the
 * algorithm's authors recommend. The sequence depends on the seed alone, so the same seed gives the same
 * tree on every platform.
 */
public final class Xoroshiro128PlusPlus {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private long s0;
    private long s1;

    public Xoroshiro128PlusPlus(long seed) {
        setSeed(seed);
    }

    /** Restarts the sequence from {@code seed}, so that one instance can serve many trees. */
    public void setSeed(long seed) {
        long x = seed + GOLDEN_GAMMA;
        s0 = mix(x);
        x += GOLDEN_GAMMA;
        s1 = mix(x);
        if ((s0 | s1) == 0L) {
            // The all-zero state is the one fixed point of the generator.
            s0 = GOLDEN_GAMMA;
        }
    }

    /** Sets the raw state. For tests against the reference sequence. */
    void setState(long s0, long s1) {
        if ((s0 | s1) == 0L) {
            throw new IllegalArgumentException("state must not be all zero");
        }
        this.s0 = s0;
        this.s1 = s1;
    }

    public long nextLong() {
        final long a = s0;
        long b = s1;
        final long result = Long.rotateLeft(a + b, 17) + a;
        b ^= a;
        s0 = Long.rotateLeft(a, 49) ^ b ^ (b << 21);
        s1 = Long.rotateLeft(b, 28);
        return result;
    }

    /** A uniform double in [0, 1), from the upper 53 bits of {@link #nextLong()}. */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
