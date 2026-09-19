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

import org.junit.jupiter.api.Test;

class FrameTest {

    private static final double EPS = 1e-12;

    @Test
    void knownRotations() {
        Frame f = new Frame().setUpright();
        assertEquals(0.0, f.declination(), EPS);

        // A down angle of 90 degrees lays the stem flat, toward -y, and its y-axis then points at the sky.
        f.rotateX(90);
        assertEquals(0, f.zx, EPS);
        assertEquals(0, f.zy, EPS);
        assertEquals(1, f.zz, EPS);
        assertEquals(1, f.yy, EPS);
        assertEquals(StrictMath.PI / 2, f.declination(), EPS);

        // Positive curvature now bends the flat stem downward.
        f.rotateX(30);
        assertEquals(-0.5, f.zy, EPS);

        Frame g = new Frame().setUpright();
        g.rotateWorldUp(90);
        assertEquals(0, g.xx, EPS);
        assertEquals(-1, g.xz, EPS);
        assertEquals(1, g.zy, EPS);

        Frame h = new Frame().setUpright();
        h.rotateZ(90);
        assertEquals(0, h.xx, EPS);
        assertEquals(-1, h.xz, EPS);
        assertEquals(-1, h.yx, EPS);
    }

    @Test
    void staysOrthonormalAfterManyRotations() {
        Xoroshiro128PlusPlus rng = new Xoroshiro128PlusPlus(1);
        Frame f = new Frame().setUpright();
        for (int i = 0; i < 1000; i++) {
            f.rotateX(rng.nextDouble() * 720 - 360);
            f.rotateZ(rng.nextDouble() * 720 - 360);
            f.rotateWorldUp(rng.nextDouble() * 720 - 360);
        }
        assertOrthonormal(f, 1e-9);
        f.orthonormalize();
        assertOrthonormal(f, 1e-14);
    }

    static void assertOrthonormal(Frame f, double eps) {
        assertEquals(1, f.xx * f.xx + f.xy * f.xy + f.xz * f.xz, eps);
        assertEquals(1, f.yx * f.yx + f.yy * f.yy + f.yz * f.yz, eps);
        assertEquals(1, f.zx * f.zx + f.zy * f.zy + f.zz * f.zz, eps);
        assertEquals(0, f.xx * f.yx + f.xy * f.yy + f.xz * f.yz, eps);
        assertEquals(0, f.xx * f.zx + f.xy * f.zy + f.xz * f.zz, eps);
        assertEquals(0, f.yx * f.zx + f.yy * f.zy + f.yz * f.zz, eps);
        // Right-handed: x cross y equals z.
        assertEquals(f.zx, f.xy * f.yz - f.xz * f.yy, eps);
    }
}
