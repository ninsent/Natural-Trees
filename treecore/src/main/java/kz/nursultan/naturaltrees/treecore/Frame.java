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
 * A stem's coordinate system: three orthonormal axes in world coordinates (Minecraft's, Y up). The local
 * z-axis runs along the stem, as in the paper (section 4.1). Rotations are right-handed and take degrees,
 * so a positive rotation about the local x-axis turns the stem toward its negative y-axis: for a limb
 * whose y-axis points at the sky, positive curvature bends it down, as the paper's sample trees show.
 */
final class Frame {

    double xx, xy, xz;
    double yx, yy, yz;
    double zx, zy, zz;

    // Sine and cosine of the last angle given to setAngle.
    private double sin;
    private double cos;

    /**
     * Sine and cosine of an angle in degrees. The angle is first brought to within 45 degrees of a multiple
     * of 90, which is exact in degrees, so the results are exact at the multiples of 90 and StrictMath never
     * sees an argument beyond pi/4, where its argument reduction would allocate a temporary array (spec 7.8).
     */
    private void setAngle(double degrees) {
        double d = degrees - 360.0 * StrictMath.floor(degrees / 360.0 + 0.5);
        final int quarter = (int) StrictMath.floor(d / 90.0 + 0.5);
        d -= 90.0 * quarter;
        final double r = StrictMath.toRadians(d);
        final double s = StrictMath.sin(r);
        final double c = StrictMath.cos(r);
        switch (quarter & 3) {
            case 0 -> {
                sin = s;
                cos = c;
            }
            case 1 -> {
                sin = c;
                cos = -s;
            }
            case 2 -> {
                sin = -s;
                cos = -c;
            }
            default -> {
                sin = -c;
                cos = s;
            }
        }
    }

    /** The trunk's frame: z up, x east, y north. */
    Frame setUpright() {
        xx = 1; xy = 0; xz = 0;
        yx = 0; yy = 0; yz = -1;
        zx = 0; zy = 1; zz = 0;
        return this;
    }

    Frame set(Frame o) {
        xx = o.xx; xy = o.xy; xz = o.xz;
        yx = o.yx; yy = o.yy; yz = o.yz;
        zx = o.zx; zy = o.zy; zz = o.zz;
        return this;
    }

    /** Rotates about the local x-axis: the stem direction turns toward the negative local y-axis. */
    void rotateX(double degrees) {
        setAngle(degrees);
        final double c = cos;
        final double s = sin;
        final double nyx = yx * c + zx * s, nyy = yy * c + zy * s, nyz = yz * c + zz * s;
        final double nzx = zx * c - yx * s, nzy = zy * c - yy * s, nzz = zz * c - yz * s;
        yx = nyx; yy = nyy; yz = nyz;
        zx = nzx; zy = nzy; zz = nzz;
    }

    /** Rotates about the local z-axis, the stem's own direction. */
    void rotateZ(double degrees) {
        setAngle(degrees);
        final double c = cos;
        final double s = sin;
        final double nxx = xx * c + yx * s, nxy = xy * c + yy * s, nxz = xz * c + yz * s;
        final double nyx = yx * c - xx * s, nyy = yy * c - xy * s, nyz = yz * c - xz * s;
        xx = nxx; xy = nxy; xz = nxz;
        yx = nyx; yy = nyy; yz = nyz;
    }

    /** Rotates the whole frame about the world's vertical axis, the axis "parallel to the tree" of section 4.2. */
    void rotateWorldUp(double degrees) {
        setAngle(degrees);
        final double c = cos;
        final double s = sin;
        double t = xx * c + xz * s; xz = xz * c - xx * s; xx = t;
        t = yx * c + yz * s; yz = yz * c - yx * s; yx = t;
        t = zx * c + zz * s; zz = zz * c - zx * s; zx = t;
    }

    /** Angle between the stem direction and the world's up, in radians: the paper's declination. */
    double declination() {
        return StrictMath.acos(zy > 1.0 ? 1.0 : (zy < -1.0 ? -1.0 : zy));
    }

    /** Removes accumulated rounding error: z is normalised, y made perpendicular to it, x rebuilt. */
    void orthonormalize() {
        double n = StrictMath.sqrt(zx * zx + zy * zy + zz * zz);
        zx /= n; zy /= n; zz /= n;
        final double d = yx * zx + yy * zy + yz * zz;
        yx -= d * zx; yy -= d * zy; yz -= d * zz;
        n = StrictMath.sqrt(yx * yx + yy * yy + yz * yz);
        yx /= n; yy /= n; yz /= n;
        xx = yy * zz - yz * zy;
        xy = yz * zx - yx * zz;
        xz = yx * zy - yy * zx;
    }
}
