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
 * The Weber and Penn skeleton (spec 7.1, paper section 4) with the voxel-scale rules and the tip budget of
 * spec 7.3. Readings of the paper are recorded in {@code docs/paper-notes.md}. One instance is reused for
 * many trees; nothing but buffers survives from one tree to the next.
 */
final class Skeleton {

    /** Safety cap on generated stems, so that no species file can stall the game (questions.md Q9). */
    static final int MAX_STEMS = 2048;
    /** A stem shorter than this many blocks is not generated (spec 7.3, questions.md Q4). */
    static final double MIN_STEM_LENGTH = 2.0;

    Stem[] stems = new Stem[64];
    int count;
    int liveCount;

    // Counters for the stats command (spec 16).
    int removedByBudget;
    boolean capped;

    private final double[] splitError = new double[4];
    private Frame[] framePool = new Frame[16];
    private int framesInUse;

    private TrunkParams params;
    private Xoroshiro128PlusPlus rng;

    /** Transient state of a stem while it grows; clones inherit a copy. */
    private static final class Growth {
        /** Offset of the next child on this stem, and the distance between successive children. */
        double nextChildOffset;
        double childSpacing;
        double rotation;
        int side;
        /** Curvature per remaining segment that undoes earlier split angles. */
        double compensation;
        // Helix mode.
        double helixAzimuth;
        double helixStep;
    }

    private Growth[] growthPool = new Growth[16];
    private int growthsInUse;

    void generate(TrunkParams params, Xoroshiro128PlusPlus rng, int height) {
        this.params = params;
        this.rng = rng;
        count = 0;
        liveCount = 0;
        removedByBudget = 0;
        capped = false;
        framesInUse = 0;
        growthsInUse = 0;
        java.util.Arrays.fill(splitError, 0.0);

        // The path runs between voxel centres, so a trunk of height h fills exactly h voxels.
        final double length = StrictMath.max(1, height - 1);
        final Stem trunk = newStem(0, -1, false, 0.0, length, 1.0, 0.0, 0);
        final Frame frame = acquireFrame().setUpright();
        frame.rotateWorldUp(rng.nextDouble() * 360.0);
        trunk.foliageFactor = signedUnit();
        final Growth growth = acquireGrowth();
        final StemParams sp = params.trunk();
        final int res = effectiveCurveRes(sp, length);
        initGrowth(growth, trunk, sp, res, -1.0, 0.0);
        trunk.addPoint(0.5, 0.5, 0.5, 0.0);
        grow(trunk, frame, growth, sp, res, 0);
        releaseGrowth();
        releaseFrame();

        applyTipBudget();
    }

    static int effectiveCurveRes(StemParams sp, double length) {
        return StrictMath.max(1, StrictMath.min(sp.curveRes(), (int) StrictMath.floor(length / 2.0)));
    }

    private StemParams stemParams(int level) {
        return level == 0 ? params.trunk() : params.levels().get(level - 1).stem();
    }

    private double signedUnit() {
        return rng.nextDouble() * 2.0 - 1.0;
    }

    /** Sets up child spacing and the rotation state for a stem that starts at its own base. */
    private void initGrowth(Growth g, Stem stem, StemParams sp, int res, double parentLength, double parentOffset) {
        g.compensation = 0.0;
        g.side = 1;
        g.rotation = rng.nextDouble() * 360.0;
        g.helixAzimuth = rng.nextDouble() * 360.0;
        g.helixStep = (rng.nextDouble() < 0.5 ? -360.0 : 360.0) / res;

        final int level = stem.level;
        double n = 0.0;
        if (level < params.levels().size()) {
            final int max = params.levels().get(level).branches();
            if (level == 0) {
                n = max;
            } else if (level == 1) {
                // Paper 4.3, first formula: a limb that is short for its position bears fewer children.
                n = stem.lengthMax > 0.0 ? max * (0.2 + 0.8 * (stem.length / parentLength) / stem.lengthMax) : 0.0;
            } else {
                n = max * (1.0 - 0.5 * parentOffset / parentLength);
            }
        }
        final int childCount = (int) StrictMath.floor(n + 0.5);
        final double childStart = level == 0 ? params.baseSize() * stem.length : 0.0;
        if (childCount > 0) {
            g.childSpacing = (stem.length - childStart) / childCount;
            g.nextChildOffset = childStart + 0.5 * g.childSpacing;
        } else {
            g.childSpacing = 0.0;
            g.nextChildOffset = Double.POSITIVE_INFINITY;
        }
    }

    /**
     * Grows {@code stem} from segment {@code firstSegment} to its end: curvature, children along each
     * segment, splits at the end of each segment but the last.
     */
    private void grow(Stem stem, Frame frame, Growth g, StemParams sp, int res, int firstSegment) {
        final int level = stem.level;
        final double segLength = stem.length / res;
        final boolean helix = sp.curveV() < 0.0;
        Frame helixBase = null;
        if (helix) {
            helixBase = acquireFrame().set(frame);
        }

        for (int i = firstSegment; i < res; i++) {
            if (helix) {
                frame.set(helixBase);
                frame.rotateZ(g.helixAzimuth + g.helixStep * i);
                frame.rotateX(-sp.curveV());
            } else if (i > 0) {
                double angle;
                if (sp.curveBack() == 0.0) {
                    angle = sp.curve() / res;
                } else {
                    angle = (i < res / 2.0 ? sp.curve() : sp.curveBack()) / (res / 2.0);
                }
                angle += signedUnit() * sp.curveV() / res;
                angle += g.compensation;
                frame.rotateX(angle);
                if (level >= 2 && params.attractionUp() != 0.0) {
                    // Paper 4.8. frame.yy is cos(orientation). The sign makes a positive value bend upward.
                    final double up = params.attractionUp() * frame.declination() * frame.yy / res;
                    frame.rotateX(-StrictMath.toDegrees(up));
                }
            }

            final int last = stem.pointCount - 1;
            final double x0 = stem.px[last], y0 = stem.py[last], z0 = stem.pz[last];
            final double o0 = stem.offset[last];
            final double o1 = o0 + segLength;

            // Children whose offset falls on this segment.
            while (g.nextChildOffset < o1 && g.nextChildOffset < stem.length) {
                final double d = StrictMath.max(0.0, g.nextChildOffset - o0);
                g.nextChildOffset += g.childSpacing;
                spawnChild(stem, frame, g, x0 + frame.zx * d, y0 + frame.zy * d, z0 + frame.zz * d, o0 + d);
            }

            stem.addPoint(x0 + frame.zx * segLength, y0 + frame.zy * segLength, z0 + frame.zz * segLength, o1);

            if (i < res - 1) {
                split(stem, frame, helix ? helixBase : frame, g, sp, res, i);
            }
        }
        if (helix) {
            releaseFrame();
        }
    }

    /** Paper 4.2: clones at the end of segment {@code i}. {@code turning} is the frame the split rotates. */
    private void split(Stem stem, Frame frame, Frame turning, Growth g, StemParams sp, int res, int i) {
        final int level = stem.level;
        int clones;
        if (level == 0 && !stem.clone && i == 0 && params.baseSplits() > 0) {
            // Paper 4.2: base_splits stands in for seg_splits at the end of the trunk's first segment.
            clones = params.baseSplits();
        } else {
            final double wanted = sp.segSplits();
            final double effective = StrictMath.floor(wanted + splitError[level] + 0.5);
            splitError[level] -= effective - wanted;
            clones = (int) StrictMath.max(0.0, effective);
        }
        final int remaining = res - 1 - i;
        final double segLength = stem.length / res;
        if (clones == 0 || remaining * segLength < MIN_STEM_LENGTH) {
            return;
        }

        // Paper 4.3: a stem that has been cloned, and its clones, bear children less densely, so that the
        // stems of a split share the children the unsplit stem would have had (paper-notes.md, entry 7).
        final double oldSpacing = g.childSpacing;
        g.childSpacing = oldSpacing * (clones + 1);

        final int last = stem.pointCount - 1;
        final double declination = StrictMath.toDegrees(frame.declination());
        final double spreadRange = 0.75 * (30.0 + StrictMath.abs(declination - 90.0));

        for (int k = 1; k <= clones; k++) {
            if (count >= MAX_STEMS) {
                capped = true;
                break;
            }
            final Stem c = newStem(level, stem.id, true, stem.offset[last], stem.length, stem.lengthMax,
                    stem.offset[last], stem.tipRadiusOffset);
            c.addPoint(stem.px[last], stem.py[last], stem.pz[last], stem.offset[last]);

            final Frame cf = acquireFrame().set(turning);
            final Growth cg = acquireGrowth();
            copyGrowth(cg, g);
            cg.nextChildOffset = g.nextChildOffset + k * oldSpacing;
            final double angle = StrictMath.max(0.0, sp.splitAngle() + signedUnit() * sp.splitAngleV() - declination);
            cf.rotateX(angle);
            cg.compensation -= angle / remaining;
            final double r = rng.nextDouble();
            final double sign = rng.nextDouble() < 0.5 ? -1.0 : 1.0;
            if (clones > 1) {
                cf.rotateWorldUp(k * 360.0 / (clones + 1) + sign * spreadRange * r * r / (clones + 1));
            }
            cg.helixAzimuth = g.helixAzimuth;
            grow(c, cf, cg, sp, res, i + 1);
            releaseGrowth();
            releaseFrame();
        }

        // The original stem turns away as well, and spreads about the vertical axis.
        final double angle = StrictMath.max(0.0, sp.splitAngle() + signedUnit() * sp.splitAngleV() - declination);
        turning.rotateX(angle);
        g.compensation -= angle / remaining;
        final double r = rng.nextDouble();
        final double sign = rng.nextDouble() < 0.5 ? -1.0 : 1.0;
        final double spread = clones > 1 ? spreadRange * r * r / (clones + 1) : 20.0 + spreadRange * r * r;
        turning.rotateWorldUp(sign * spread);
    }

    /** Paper 4.3: one child of {@code parent} at the given point of its path. */
    private void spawnChild(Stem parent, Frame frame, Growth g, double x, double y, double z, double off) {
        final int level = parent.level + 1;
        final LevelParams lp = params.levels().get(level - 1);

        // The rotation advances for every child, generated or not, so that the spiral keeps its rhythm.
        double turn;
        if (lp.rotate() >= 0.0) {
            g.rotation += lp.rotate() + signedUnit() * lp.rotateV();
            turn = g.rotation;
        } else {
            turn = g.side * (-lp.rotate() + signedUnit() * lp.rotateV());
            g.side = -g.side;
        }

        final double lengthMax = StrictMath.max(0.0, lp.length() + signedUnit() * lp.lengthV());
        final double baseLength = parent.level == 0 ? params.baseSize() * parent.length : 0.0;
        final double ratio = (parent.length - off) / (parent.length - baseLength);
        final double length;
        if (level == 1) {
            length = parent.length * lengthMax * params.shape().ratio(ratio);
        } else {
            length = lengthMax * (parent.length - 0.6 * off);
        }

        final double down;
        if (lp.downAngleV() >= 0.0) {
            down = lp.downAngle() + signedUnit() * lp.downAngleV();
        } else {
            down = lp.downAngle() + lp.downAngleV() * (1.0 - 2.0 * Shape.CONICAL.ratio(ratio));
        }
        final double foliageFactor = signedUnit();

        if (length < MIN_STEM_LENGTH) {
            return;
        }
        if (count >= MAX_STEMS) {
            capped = true;
            return;
        }

        final Stem child = newStem(level, parent.id, false, off, length, lengthMax, 0.0, lp.tipRadiusOffset());
        child.foliageFactor = foliageFactor;
        child.addPoint(x, y, z, 0.0);

        final Frame cf = acquireFrame().set(frame);
        cf.rotateZ(turn);
        cf.rotateX(down);
        cf.orthonormalize();
        final Growth cg = acquireGrowth();
        final StemParams sp = lp.stem();
        final int res = effectiveCurveRes(sp, length);
        initGrowth(cg, child, sp, res, parent.length, off);
        grow(child, cf, cg, sp, res, 0);
        releaseGrowth();
        releaseFrame();
    }

    /**
     * Spec 7.3, tip budget: while there are more stems than {@code max_tips}, remove one that has nothing
     * attached: deepest level first, shortest first, latest generated first (questions.md Q7).
     */
    private void applyTipBudget() {
        final int budget = params.maxTips();
        while (liveCount > budget) {
            Stem pick = null;
            for (int i = count - 1; i > 0; i--) {
                final Stem s = stems[i];
                if (s.removed || s.attachedCount > 0) {
                    continue;
                }
                if (pick == null || s.level > pick.level
                        || (s.level == pick.level && s.pathLength() < pick.pathLength())) {
                    pick = s;
                }
            }
            if (pick == null) {
                break;
            }
            pick.removed = true;
            stems[pick.parent].attachedCount--;
            liveCount--;
            removedByBudget++;
        }
    }

    private Stem newStem(int level, int parent, boolean clone, double parentOffset, double length,
                         double lengthMax, double startOffset, int tipRadiusOffset) {
        if (count == stems.length) {
            stems = java.util.Arrays.copyOf(stems, count * 2);
        }
        Stem s = stems[count];
        if (s == null) {
            s = new Stem();
            stems[count] = s;
        }
        s.id = count++;
        s.level = level;
        s.parent = parent;
        s.clone = clone;
        s.parentOffset = parentOffset;
        s.length = length;
        s.lengthMax = lengthMax;
        s.startOffset = startOffset;
        s.tipRadiusOffset = tipRadiusOffset;
        s.foliageFactor = 0.0;
        s.pointCount = 0;
        s.removed = false;
        s.attachedCount = 0;
        s.firstAttached = -1;
        s.lastAttached = -1;
        s.nextSibling = -1;
        s.baseLoad = 0;
        if (parent >= 0) {
            final Stem p = stems[parent];
            if (p.lastAttached < 0) {
                p.firstAttached = s.id;
            } else {
                stems[p.lastAttached].nextSibling = s.id;
            }
            p.lastAttached = s.id;
            p.attachedCount++;
            if (clone) {
                s.foliageFactor = p.foliageFactor;
            }
        }
        liveCount++;
        return s;
    }

    private static void copyGrowth(Growth to, Growth from) {
        to.nextChildOffset = from.nextChildOffset;
        to.childSpacing = from.childSpacing;
        to.rotation = from.rotation;
        to.side = from.side;
        to.compensation = from.compensation;
        to.helixAzimuth = from.helixAzimuth;
        to.helixStep = from.helixStep;
    }

    private Frame acquireFrame() {
        if (framesInUse == framePool.length) {
            framePool = java.util.Arrays.copyOf(framePool, framesInUse * 2);
        }
        Frame f = framePool[framesInUse];
        if (f == null) {
            f = new Frame();
            framePool[framesInUse] = f;
        }
        framesInUse++;
        return f;
    }

    private void releaseFrame() {
        framesInUse--;
    }

    private Growth acquireGrowth() {
        if (growthsInUse == growthPool.length) {
            growthPool = java.util.Arrays.copyOf(growthPool, growthsInUse * 2);
        }
        Growth g = growthPool[growthsInUse];
        if (g == null) {
            g = new Growth();
            growthPool[growthsInUse] = g;
        }
        growthsInUse++;
        return g;
    }

    private void releaseGrowth() {
        growthsInUse--;
    }
}
