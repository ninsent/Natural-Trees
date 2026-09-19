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

import java.util.Arrays;

/**
 * Turns a skeleton into wood voxels: thickness from the pipe model (spec 7.2), rasterisation and arms
 * (spec 7.4), and emission against the placement tests (spec 7.5).
 */
final class WoodBuilder {

    static final byte KIND_BRANCH = 0;
    static final byte KIND_LOG = 1;
    static final byte KIND_WIDE = 2;

    private static final double EPS = 1e-9;

    private Skeleton skeleton;
    private TrunkParams params;
    private VoxelMap map;
    private PlacementLimits limits;
    private WorldRead world;
    private int maxRadiusSquared;

    /** Smallest loads that give a 1×1 log and a 2×2 trunk under the current parameters. */
    int logLoad;
    int wideLoad;

    // Wood voxels in emission order, parent before child.
    int woodCount;
    int[] woodX = new int[256];
    int[] woodY = new int[256];
    int[] woodZ = new int[256];
    int[] woodLoad = new int[256];

    // Every voxel of every stem's emitted path, new or merged, grouped by stem.
    int pathCount;
    int[] pathX = new int[256];
    int[] pathY = new int[256];
    int[] pathZ = new int[256];
    double[] pathOffset = new double[256];
    int[] stemPathStart = new int[64];
    int[] stemPathCount = new int[64];
    boolean[] stemDropped = new boolean[64];
    double[] stemEmittedEnd = new double[64];

    int tipCount;
    int[] tipX = new int[48];
    int[] tipY = new int[48];
    int[] tipZ = new int[48];
    int[] tipRadiusOffset = new int[48];
    boolean[] tipWide = new boolean[48];

    // Counters for the stats command (spec 16).
    int truncatedStems;
    int droppedByFace;
    int droppedWithParent;
    int worldReads;
    boolean wideTrunkBlocked;

    // Rasterised path of the stem being emitted.
    private int rasterCount;
    private int[] rasterX = new int[128];
    private int[] rasterY = new int[128];
    private int[] rasterZ = new int[128];
    private double[] rasterOffset = new double[128];
    private byte[] rasterKind = new byte[128];
    private byte[] rasterAxis = new byte[128];

    void build(Skeleton skeleton, TrunkParams params, VoxelMap map, PlacementLimits limits, WorldRead world) {
        this.skeleton = skeleton;
        this.params = params;
        this.map = map;
        this.limits = limits;
        this.world = world;
        maxRadiusSquared = params.maxRadius() * params.maxRadius();
        woodCount = 0;
        pathCount = 0;
        tipCount = 0;
        truncatedStems = 0;
        droppedByFace = 0;
        droppedWithParent = 0;
        worldReads = 0;
        wideTrunkBlocked = false;

        final int n = skeleton.count;
        if (stemPathStart.length < n) {
            final int size = StrictMath.max(n, stemPathStart.length * 2);
            stemPathStart = new int[size];
            stemPathCount = new int[size];
            stemDropped = new boolean[size];
            stemEmittedEnd = new double[size];
        }
        Arrays.fill(stemPathCount, 0, n, 0);
        Arrays.fill(stemDropped, 0, n, false);

        computeThickness();

        // Spec 7.4: a blocked extra column at the base makes the whole trunk 1×1.
        final Stem trunk = skeleton.stems[0];
        if (trunk.wideEnd > trunk.startOffset
                && !(canPlace(1, 0, 0) && canPlace(0, 0, 1) && canPlace(1, 0, 1))) {
            wideTrunkBlocked = true;
            for (int i = 0; i < n; i++) {
                final Stem s = skeleton.stems[i];
                s.wideEnd = s.startOffset;
            }
        }

        for (int i = 0; i < n; i++) {
            if (!skeleton.stems[i].removed) {
                emit(skeleton.stems[i]);
            }
        }
    }

    /** Empties the output, for a result that holds leaves only (the fallback cluster of spec 9.1). */
    void clear() {
        woodCount = 0;
        pathCount = 0;
        tipCount = 0;
        truncatedStems = 0;
        droppedByFace = 0;
        droppedWithParent = 0;
        worldReads = 0;
        wideTrunkBlocked = false;
    }

    // ---- Thickness: the pipe model, spec 7.2 --------------------------------------------------------

    /** Radius in blocks of a stem that carries {@code load} tips. */
    static double radius(TrunkParams params, int load) {
        return params.twigRadius() * StrictMath.pow(load, 1.0 / params.pipeExponent());
    }

    private int smallestLoadWithRadius(double radius) {
        for (int load = 1; load <= 4096; load++) {
            if (radius(params, load) >= radius) {
                return load;
            }
        }
        return Integer.MAX_VALUE;
    }

    private void computeThickness() {
        logLoad = smallestLoadWithRadius(0.5);
        wideLoad = smallestLoadWithRadius(1.0);

        // Children always have a higher id than the stem they hang from.
        for (int i = skeleton.count - 1; i >= 0; i--) {
            final Stem s = skeleton.stems[i];
            if (s.removed) {
                continue;
            }
            if (s.root) {
                s.baseLoad = 0;
                continue;
            }
            int load = 1;
            for (int a = s.firstAttached; a >= 0; a = skeleton.stems[a].nextSibling) {
                if (!skeleton.stems[a].removed) {
                    load += skeleton.stems[a].baseLoad;
                }
            }
            s.baseLoad = load;
        }

        for (int i = 0; i < skeleton.count; i++) {
            final Stem s = skeleton.stems[i];
            if (s.removed) {
                continue;
            }
            if (s.root) {
                // A root carries no tips, so the pipe model has nothing to say: its thickness is data.
                s.wideEnd = s.startOffset;
                s.logEnd = s.startOffset + params.roots().logShare() * s.pathLength();
                continue;
            }
            // Load only drops where a stem is attached, so each thickness ends at an attachment or at the tip.
            double wideEnd = s.startOffset;
            double logEnd = s.startOffset;
            double firstAttachment = s.endOffset();
            boolean anyAttachment = false;
            int load = s.baseLoad;
            for (int a = s.firstAttached; a >= 0; a = skeleton.stems[a].nextSibling) {
                final Stem attached = skeleton.stems[a];
                if (attached.removed) {
                    continue;
                }
                if (!anyAttachment) {
                    anyAttachment = true;
                    firstAttachment = attached.parentOffset;
                }
                if (load >= wideLoad) {
                    wideEnd = attached.parentOffset;
                }
                if (load >= logLoad) {
                    logEnd = attached.parentOffset;
                }
                load -= attached.baseLoad;
            }
            if (load >= wideLoad) {
                wideEnd = s.endOffset();
            }
            if (load >= logLoad) {
                logEnd = s.endOffset();
            }

            if (s.level > 0) {
                wideEnd = s.startOffset;
            } else {
                if (!params.trunkLeader()) {
                    logEnd = s.endOffset();
                } else if (i == 0) {
                    // questions.md Q6: the bare part of the trunk is never thinner than a log, so a tree with
                    // few tips is not a stick of branch blocks. The leader begins at the first limb or above.
                    logEnd = StrictMath.max(logEnd, firstAttachment);
                }
                if (params.trunkWidthMax() < 2) {
                    wideEnd = s.startOffset;
                } else if (i == 0 && params.trunkWidthMin() >= 2) {
                    // questions.md Q6: with trunk_leader the minimum holds on the bare part of the trunk only.
                    wideEnd = params.trunkLeader() ? StrictMath.max(wideEnd, firstAttachment) : s.endOffset();
                }
            }
            s.wideEnd = wideEnd;
            s.logEnd = StrictMath.max(logEnd, wideEnd);
        }
    }

    /** Spec 7.2: the number of tips at or beyond {@code offset} on {@code s}. */
    int loadAt(Stem s, double offset) {
        int load = s.baseLoad;
        for (int a = s.firstAttached; a >= 0; a = skeleton.stems[a].nextSibling) {
            final Stem attached = skeleton.stems[a];
            if (!attached.removed && attached.parentOffset < offset - EPS) {
                load -= attached.baseLoad;
            }
        }
        return load;
    }

    // ---- Placement tests, spec 7.5 -------------------------------------------------------------------

    /** The three tests in order; only the last reads the world, once per position. */
    boolean canPlace(int x, int y, int z) {
        if (!VoxelMap.inBounds(x, y, z)) {
            return false;
        }
        if (!limits.contains(x, z, params.foliageMargin())) {
            return false;
        }
        if (x * x + z * z > maxRadiusSquared) {
            return false;
        }
        final int index = VoxelMap.index(x, y, z);
        byte flags = map.flags[index];
        if ((flags & VoxelMap.PLACE_KNOWN) == 0) {
            worldReads++;
            flags |= VoxelMap.PLACE_KNOWN;
            if (world.test(x, y, z)) {
                flags |= VoxelMap.PLACE_OK;
            }
            map.flags[index] = flags;
            map.touch(y);
        }
        return (flags & VoxelMap.PLACE_OK) != 0;
    }

    // ---- Emission ------------------------------------------------------------------------------------

    private void emit(Stem s) {
        final int id = s.id;
        stemPathStart[id] = pathCount;
        stemEmittedEnd[id] = s.startOffset - 1.0;

        int prevX = 0, prevY = 0, prevZ = 0;
        boolean hasPrev = false;
        final boolean attached = s.parent >= 0;
        if (attached) {
            if (stemDropped[s.parent] || s.parentOffset > stemEmittedEnd[s.parent] + EPS) {
                stemDropped[id] = true;
                droppedWithParent++;
                return;
            }
            final int p = parentVoxelAt(s.parent, s.parentOffset);
            if (p < 0) {
                stemDropped[id] = true;
                droppedWithParent++;
                return;
            }
            prevX = pathX[p];
            prevY = pathY[p];
            prevZ = pathZ[p];
            hasPrev = true;
        }

        rasterise(s);

        int i = 0;
        if (attached) {
            // The child's path begins inside its parent; its own wood starts where it leaves the parent's.
            while (i < rasterCount && inPath(s.parent, rasterX[i], rasterY[i], rasterZ[i])) {
                prevX = rasterX[i];
                prevY = rasterY[i];
                prevZ = rasterZ[i];
                i++;
            }
        }

        int steps = 0;
        boolean truncated = false;
        int lastX = 0, lastY = 0, lastZ = 0;
        boolean lastWide = false;

        for (; i < rasterCount; i++) {
            final int x = rasterX[i], y = rasterY[i], z = rasterZ[i];
            final byte kind = rasterKind[i];
            final byte axis = rasterAxis[i];
            final int load = loadAt(s, rasterOffset[i]);

            // Branch voxels, and the first voxel of any attached stem, must share a face with what precedes them.
            if (hasPrev && (kind == KIND_BRANCH || (steps == 0 && attached))
                    && !faceAdjacent(prevX, prevY, prevZ, x, y, z)) {
                if (steps == 0 && attached) {
                    final int p = parentVoxelBeside(s.parent, x, y, z, s.parentOffset);
                    if (p >= 0) {
                        prevX = pathX[p];
                        prevY = pathY[p];
                        prevZ = pathZ[p];
                    }
                }
                while (!faceAdjacent(prevX, prevY, prevZ, x, y, z)) {
                    // One axis step toward the target, longest remaining distance first.
                    final int dx = x - prevX, dy = y - prevY, dz = z - prevZ;
                    final int ax = StrictMath.abs(dx), ay = StrictMath.abs(dy), az = StrictMath.abs(dz);
                    int bx = prevX, by = prevY, bz = prevZ;
                    if (ax >= az && ax >= ay) {
                        bx += Integer.signum(dx);
                    } else if (az >= ay) {
                        bz += Integer.signum(dz);
                    } else {
                        by += Integer.signum(dy);
                    }
                    if (steps == 0 && attached && inPath(s.parent, bx, by, bz)) {
                        prevX = bx;
                        prevY = by;
                        prevZ = bz;
                        continue;
                    }
                    if (steps == 0 && attached && !claimFace(prevX, prevY, prevZ, bx, by, bz)) {
                        stemDropped[id] = true;
                        droppedByFace++;
                        return;
                    }
                    if (!place(bx, by, bz, kind == KIND_BRANCH ? KIND_BRANCH : KIND_LOG, axis, rasterOffset[i], load,
                            true, prevX, prevY, prevZ)) {
                        truncated = true;
                        break;
                    }
                    steps++;
                    prevX = bx;
                    prevY = by;
                    prevZ = bz;
                    lastX = bx;
                    lastY = by;
                    lastZ = bz;
                    lastWide = false;
                    stemEmittedEnd[id] = rasterOffset[i];
                }
                if (truncated) {
                    break;
                }
            }

            if (steps == 0 && attached && !claimFace(prevX, prevY, prevZ, x, y, z)) {
                stemDropped[id] = true;
                droppedByFace++;
                return;
            }
            if (!place(x, y, z, kind, axis, rasterOffset[i], load, hasPrev, prevX, prevY, prevZ)) {
                truncated = true;
                break;
            }
            if (kind == KIND_WIDE) {
                // Spec 7.4: the section occupies the centreline's column and the columns at +x, +z and +x+z.
                place(x + 1, y, z, KIND_LOG, axis, rasterOffset[i], load, false, 0, 0, 0);
                place(x, y, z + 1, KIND_LOG, axis, rasterOffset[i], load, false, 0, 0, 0);
                place(x + 1, y, z + 1, KIND_LOG, axis, rasterOffset[i], load, false, 0, 0, 0);
            }
            steps++;
            hasPrev = true;
            prevX = x;
            prevY = y;
            prevZ = z;
            lastX = x;
            lastY = y;
            lastZ = z;
            lastWide = kind == KIND_WIDE;
            stemEmittedEnd[id] = rasterOffset[i];
        }

        stemPathCount[id] = pathCount - stemPathStart[id];
        if (truncated) {
            // The ground is what ends a root; that is not a truncation worth reporting.
            truncatedStems += s.root ? 0 : 1;
        } else if (steps > 0) {
            stemEmittedEnd[id] = s.endOffset();
        }
        // Spec 7.3 and 7.5: the end of a stem is a tip; a truncated stem needs two voxels to keep one.
        if (!s.root && steps >= (truncated ? 2 : 1)) {
            addTip(lastX, lastY, lastZ, s.tipRadiusOffset, lastWide);
        }
    }

    /** Spec 7.3: at most one child starts from any face of a parent voxel. */
    private boolean claimFace(int px, int py, int pz, int x, int y, int z) {
        final int face = Arms.of(x - px, y - py, z - pz);
        final int index = VoxelMap.index(px, py, pz);
        if ((map.childFaces[index] & face) != 0) {
            return false;
        }
        map.childFaces[index] |= (byte) face;
        return true;
    }

    /**
     * Places one voxel of a stem, or merges it into wood that is already there (spec 7.4: masks are
     * merged and LOG wins). Returns false when the position may not hold wood, which truncates the stem.
     */
    private boolean place(int x, int y, int z, byte kind, byte axis, double offset, int load,
                          boolean hasPrev, int prevX, int prevY, int prevZ) {
        if (!VoxelMap.inBounds(x, y, z)) {
            return false;
        }
        final int index = VoxelMap.index(x, y, z);
        final byte existing = map.content[index];
        if (VoxelMap.isWood(existing)) {
            if (kind != KIND_BRANCH && existing == VoxelMap.BRANCH) {
                map.content[index] = (byte) (VoxelMap.LOG_X + axis);
                map.arms[index] = 0;
            }
        } else {
            if (!canPlace(x, y, z)) {
                return false;
            }
            map.content[index] = kind == KIND_BRANCH ? VoxelMap.BRANCH : (byte) (VoxelMap.LOG_X + axis);
            map.touch(y);
            addWood(x, y, z, load);
        }
        // Spec 7.4: arms follow the skeleton. A branch voxel reaches back to the voxel before it, and that
        // voxel reaches forward if it is a branch too.
        if (hasPrev && map.content[index] == VoxelMap.BRANCH) {
            final int arm = Arms.of(prevX - x, prevY - y, prevZ - z);
            if (arm != 0) {
                map.arms[index] |= (byte) arm;
                final int prevIndex = VoxelMap.index(prevX, prevY, prevZ);
                if (map.content[prevIndex] == VoxelMap.BRANCH) {
                    map.arms[prevIndex] |= (byte) Arms.opposite(arm);
                }
            }
        }
        addPath(x, y, z, offset);
        return true;
    }

    private static boolean faceAdjacent(int ax, int ay, int az, int bx, int by, int bz) {
        return StrictMath.abs(ax - bx) + StrictMath.abs(ay - by) + StrictMath.abs(az - bz) == 1;
    }

    private boolean inPath(int stem, int x, int y, int z) {
        final int from = stemPathStart[stem];
        final int to = from + stemPathCount[stem];
        for (int i = from; i < to; i++) {
            if (pathX[i] == x && pathY[i] == y && pathZ[i] == z) {
                return true;
            }
        }
        return false;
    }

    /** The parent's emitted voxel at the greatest offset not beyond {@code offset}; the first of equals. */
    private int parentVoxelAt(int stem, double offset) {
        final int from = stemPathStart[stem];
        final int to = from + stemPathCount[stem];
        int best = -1;
        for (int i = from; i < to; i++) {
            if (pathOffset[i] <= offset + EPS && (best < 0 || pathOffset[i] > pathOffset[best])) {
                best = i;
            }
        }
        return best < 0 && to > from ? from : best;
    }

    /** A voxel of the parent that shares a face with the given position, nearest in offset to the attachment. */
    private int parentVoxelBeside(int stem, int x, int y, int z, double offset) {
        final int from = stemPathStart[stem];
        final int to = from + stemPathCount[stem];
        int best = -1;
        for (int i = from; i < to; i++) {
            if (faceAdjacent(pathX[i], pathY[i], pathZ[i], x, y, z)
                    && (best < 0 || StrictMath.abs(pathOffset[i] - offset) < StrictMath.abs(pathOffset[best] - offset))) {
                best = i;
            }
        }
        return best;
    }

    // ---- Rasterisation, spec 7.4 ----------------------------------------------------------------------

    private void rasterise(Stem s) {
        rasterCount = 0;
        final double start = s.startOffset;
        final double end = s.endOffset();
        if (s.wideEnd > start) {
            rasterLogs(s, start, s.wideEnd, KIND_WIDE);
        }
        if (s.logEnd > s.wideEnd) {
            rasterLogs(s, s.wideEnd, s.logEnd, KIND_LOG);
        }
        if (end > s.logEnd) {
            rasterBranches(s, s.logEnd, end);
        }
    }

    /** 26-connected 3D DDA over the part of the path between two offsets, as vanilla's fancy-oak limbs. */
    private void rasterLogs(Stem s, double from, double to, byte kind) {
        for (int k = 0; k + 1 < s.pointCount; k++) {
            final double o0 = StrictMath.max(from, s.offset[k]);
            final double o1 = StrictMath.min(to, s.offset[k + 1]);
            if (o1 <= o0) {
                continue;
            }
            final double span = s.offset[k + 1] - s.offset[k];
            final double t0 = (o0 - s.offset[k]) / span;
            final double t1 = (o1 - s.offset[k]) / span;
            final double sx = s.px[k + 1] - s.px[k], sy = s.py[k + 1] - s.py[k], sz = s.pz[k + 1] - s.pz[k];
            final double ax = s.px[k] + sx * t0, ay = s.py[k] + sy * t0, az = s.pz[k] + sz * t0;
            final double dx = sx * (t1 - t0), dy = sy * (t1 - t0), dz = sz * (t1 - t0);

            final byte axis = dominantAxis(sx, sy, sz);

            final int n = StrictMath.max(1, (int) StrictMath.ceil(
                    StrictMath.max(StrictMath.abs(dx), StrictMath.max(StrictMath.abs(dy), StrictMath.abs(dz)))));
            for (int i = 0; i <= n; i++) {
                final double t = (double) i / n;
                addRaster((int) StrictMath.floor(ax + dx * t), (int) StrictMath.floor(ay + dy * t),
                        (int) StrictMath.floor(az + dz * t), o0 + (o1 - o0) * t, kind, axis);
            }
        }
    }

    /** Spec 7.4: the log axis is the dominant component of the segment; at an exact diagonal, horizontal wins. */
    static byte dominantAxis(double sx, double sy, double sz) {
        final double mx = StrictMath.abs(sx), my = StrictMath.abs(sy), mz = StrictMath.abs(sz);
        return my > mx && my > mz ? (byte) TreeResult.AXIS_Y : (mx >= mz ? (byte) TreeResult.AXIS_X : (byte) TreeResult.AXIS_Z);
    }

    /** Face-connected traversal: every voxel the path passes through, one axis step at a time. */
    private void rasterBranches(Stem s, double from, double to) {
        for (int k = 0; k + 1 < s.pointCount; k++) {
            final double o0 = StrictMath.max(from, s.offset[k]);
            final double o1 = StrictMath.min(to, s.offset[k + 1]);
            if (o1 <= o0) {
                continue;
            }
            final double span = s.offset[k + 1] - s.offset[k];
            final double t0 = (o0 - s.offset[k]) / span;
            final double t1 = (o1 - s.offset[k]) / span;
            final double sx = s.px[k + 1] - s.px[k], sy = s.py[k + 1] - s.py[k], sz = s.pz[k + 1] - s.pz[k];
            final double ax = s.px[k] + sx * t0, ay = s.py[k] + sy * t0, az = s.pz[k] + sz * t0;
            final double dx = sx * (t1 - t0), dy = sy * (t1 - t0), dz = sz * (t1 - t0);

            int x = (int) StrictMath.floor(ax), y = (int) StrictMath.floor(ay), z = (int) StrictMath.floor(az);
            final int ex = (int) StrictMath.floor(ax + dx), ey = (int) StrictMath.floor(ay + dy),
                    ez = (int) StrictMath.floor(az + dz);
            final int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
            int leftX = StrictMath.abs(ex - x), leftY = StrictMath.abs(ey - y), leftZ = StrictMath.abs(ez - z);
            final double deltaX = dx != 0 ? StrictMath.abs(1.0 / dx) : Double.POSITIVE_INFINITY;
            final double deltaY = dy != 0 ? StrictMath.abs(1.0 / dy) : Double.POSITIVE_INFINITY;
            final double deltaZ = dz != 0 ? StrictMath.abs(1.0 / dz) : Double.POSITIVE_INFINITY;
            double nextX = dx != 0 ? ((dx > 0 ? x + 1 - ax : ax - x) * deltaX) : Double.POSITIVE_INFINITY;
            double nextY = dy != 0 ? ((dy > 0 ? y + 1 - ay : ay - y) * deltaY) : Double.POSITIVE_INFINITY;
            double nextZ = dz != 0 ? ((dz > 0 ? z + 1 - az : az - z) * deltaZ) : Double.POSITIVE_INFINITY;

            addRaster(x, y, z, o0, KIND_BRANCH, (byte) 0);
            while (leftX + leftY + leftZ > 0) {
                // The boundary crossed first; an axis with no steps left never moves. Ties: x, then z, then y.
                final double cx = leftX > 0 ? nextX : Double.POSITIVE_INFINITY;
                final double cy = leftY > 0 ? nextY : Double.POSITIVE_INFINITY;
                final double cz = leftZ > 0 ? nextZ : Double.POSITIVE_INFINITY;
                final double t;
                if (cx <= cz && cx <= cy) {
                    t = nextX;
                    x += stepX;
                    leftX--;
                    nextX += deltaX;
                } else if (cz <= cy) {
                    t = nextZ;
                    z += stepZ;
                    leftZ--;
                    nextZ += deltaZ;
                } else {
                    t = nextY;
                    y += stepY;
                    leftY--;
                    nextY += deltaY;
                }
                addRaster(x, y, z, o0 + (o1 - o0) * StrictMath.min(1.0, StrictMath.max(0.0, t)), KIND_BRANCH, (byte) 0);
            }
        }
    }

    private void addRaster(int x, int y, int z, double offset, byte kind, byte axis) {
        if (rasterCount > 0 && rasterX[rasterCount - 1] == x && rasterY[rasterCount - 1] == y
                && rasterZ[rasterCount - 1] == z) {
            return;
        }
        if (rasterCount == rasterX.length) {
            final int size = rasterCount * 2;
            rasterX = Arrays.copyOf(rasterX, size);
            rasterY = Arrays.copyOf(rasterY, size);
            rasterZ = Arrays.copyOf(rasterZ, size);
            rasterOffset = Arrays.copyOf(rasterOffset, size);
            rasterKind = Arrays.copyOf(rasterKind, size);
            rasterAxis = Arrays.copyOf(rasterAxis, size);
        }
        rasterX[rasterCount] = x;
        rasterY[rasterCount] = y;
        rasterZ[rasterCount] = z;
        rasterOffset[rasterCount] = offset;
        rasterKind[rasterCount] = kind;
        rasterAxis[rasterCount] = axis;
        rasterCount++;
    }

    private void addWood(int x, int y, int z, int load) {
        if (woodCount == woodX.length) {
            final int size = woodCount * 2;
            woodX = Arrays.copyOf(woodX, size);
            woodY = Arrays.copyOf(woodY, size);
            woodZ = Arrays.copyOf(woodZ, size);
            woodLoad = Arrays.copyOf(woodLoad, size);
        }
        woodX[woodCount] = x;
        woodY[woodCount] = y;
        woodZ[woodCount] = z;
        woodLoad[woodCount] = load;
        woodCount++;
    }

    private void addPath(int x, int y, int z, double offset) {
        if (pathCount == pathX.length) {
            final int size = pathCount * 2;
            pathX = Arrays.copyOf(pathX, size);
            pathY = Arrays.copyOf(pathY, size);
            pathZ = Arrays.copyOf(pathZ, size);
            pathOffset = Arrays.copyOf(pathOffset, size);
        }
        pathX[pathCount] = x;
        pathY[pathCount] = y;
        pathZ[pathCount] = z;
        pathOffset[pathCount] = offset;
        pathCount++;
    }

    private void addTip(int x, int y, int z, int radiusOffset, boolean wide) {
        if (tipCount == tipX.length) {
            final int size = tipCount * 2;
            tipX = Arrays.copyOf(tipX, size);
            tipY = Arrays.copyOf(tipY, size);
            tipZ = Arrays.copyOf(tipZ, size);
            tipRadiusOffset = Arrays.copyOf(tipRadiusOffset, size);
            tipWide = Arrays.copyOf(tipWide, size);
        }
        tipX[tipCount] = x;
        tipY[tipCount] = y;
        tipZ[tipCount] = z;
        tipRadiusOffset[tipCount] = radiusOffset;
        tipWide[tipCount] = wide;
        tipCount++;
    }
}
