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
 * One stem of the skeleton: the trunk, a limb, a twig, or a clone that split off one of them. A stem is a
 * polyline. Offsets are distances along the stem from its base; a clone continues its twin's offsets, so
 * an offset always means "this far from the base of the original stem".
 */
final class Stem {

    static final int MAX_POINTS = 9;

    int id;
    int level;
    /** The stem this one is attached to: the parent of a child, the twin of a clone; -1 for the trunk. */
    int parent;
    boolean clone;
    /** A root (spec 8.2, {@code roots}): wood only, with no tip, no foliage and no load. */
    boolean root;
    /** Where on the parent's path this stem is attached. */
    double parentOffset;
    /** Nominal length of the whole stem; for a clone, of the original stem. */
    double length;
    /** The paper's {@code length_child,max}: the length fraction drawn for this stem. */
    double lengthMax;
    /** Offset at which this stem's own path begins: 0, or the split point for a clone. */
    double startOffset;
    int tipRadiusOffset;
    /** Per-stem random factor of the sleeve radius, in [-1, 1] (spec 7.6 step 2). */
    double foliageFactor;

    int pointCount;
    final double[] px = new double[MAX_POINTS];
    final double[] py = new double[MAX_POINTS];
    final double[] pz = new double[MAX_POINTS];
    /** Offset of each point. */
    final double[] offset = new double[MAX_POINTS];

    boolean removed;
    /** Live stems attached to this one. */
    int attachedCount;
    /** Attachments in generation order, which is also the order of their offsets. */
    int firstAttached;
    int lastAttached;
    int nextSibling;

    // Thickness (spec 7.2), filled after the tip budget.
    int baseLoad;
    /** The path is 2×2 up to this offset, a 1×1 log up to {@link #logEnd}, branch blocks beyond. */
    double wideEnd;
    double logEnd;

    double endOffset() {
        return offset[pointCount - 1];
    }

    double pathLength() {
        return endOffset() - startOffset;
    }

    void addPoint(double x, double y, double z, double off) {
        px[pointCount] = x;
        py[pointCount] = y;
        pz[pointCount] = z;
        offset[pointCount] = off;
        pointCount++;
    }
}
