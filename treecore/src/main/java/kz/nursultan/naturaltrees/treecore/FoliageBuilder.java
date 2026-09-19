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
 * Foliage from the emitted wood (spec 7.6): sleeves of ellipsoids along the outer part of every stem,
 * thinned at the edge, self-shaded, limited to vanilla's reach of wood, and cut to the leaf budget.
 * Every ellipsoid is evaluated from its own voxel's radius; nothing is precomputed or reused.
 */
final class FoliageBuilder {

    /** Squared normalised distance above which a candidate belongs to the thinned outer shell: 0.7². */
    private static final double INNER = 0.49;

    private VoxelMap map;
    private PlacementLimits limits;
    private WorldRead world;
    private long seed;

    int leafCount;
    int[] leafX = new int[512];
    int[] leafY = new int[512];
    int[] leafZ = new int[512];
    int[] leafDistance = new int[512];

    // Counters for the stats command (spec 16).
    int candidates;
    int discardedByShade;
    int discardedByReach;
    int discardedByBudget;
    int worldReads;

    private int candidateCount;
    private int[] candidateIndex = new int[1024];
    private int[] queue = new int[1024];
    private long[] sortKeys = new long[256];
    private boolean[] shaded = new boolean[1024];

    void build(Skeleton skeleton, WoodBuilder wood, FoliageParams params, VoxelMap map, PlacementLimits limits,
               WorldRead world, long seed) {
        this.map = map;
        this.limits = limits;
        this.world = world;
        this.seed = seed;
        leafCount = 0;
        candidateCount = 0;
        discardedByShade = 0;
        discardedByReach = 0;
        discardedByBudget = 0;
        worldReads = 0;

        collectCandidates(skeleton, wood, params);
        thinAndTest(params);
        candidates = candidateCount;
        shade(params);
        reach(wood, params);
        applyBudget(params);
    }

    /** Steps 1 to 3: the bearing part of every emitted stem and the ellipsoid around each of its voxels. */
    private void collectCandidates(Skeleton skeleton, WoodBuilder wood, FoliageParams params) {
        for (int id = 0; id < skeleton.count; id++) {
            final Stem s = skeleton.stems[id];
            final int n = wood.stemPathCount[id];
            if (s.removed || n == 0) {
                continue;
            }
            final int from = wood.stemPathStart[id];
            final double start = s.level == 0 ? 1.0 - params.trunkFoliage() : params.foliageStart();
            final double stemFactor = 1.0 + params.radiusV() * s.foliageFactor;
            for (int i = 0; i < n; i++) {
                final double along = n > 1 ? (double) i / (n - 1) : 1.0;
                final double t;
                if (n < 3) {
                    t = along;
                } else if (along < start) {
                    continue;
                } else {
                    t = start < 1.0 ? (along - start) / (1.0 - start) : 1.0;
                }
                final double r = (params.radiusBase() + (params.radiusTip() - params.radiusBase()) * t) * stemFactor;
                ellipsoid(wood.pathX[from + i], wood.pathY[from + i], wood.pathZ[from + i], r,
                        r * params.flatten(), params.lift() * r);
            }
        }
    }

    private void ellipsoid(int vx, int vy, int vz, double r, double ry, double lift) {
        final int reach = (int) StrictMath.ceil(r);
        final int yMin = (int) StrictMath.floor(lift - ry);
        final int yMax = (int) StrictMath.ceil(lift + ry);
        final double invR2 = 1.0 / (r * r);
        final double invRy2 = 1.0 / (ry * ry);
        for (int dy = yMin; dy <= yMax; dy++) {
            final double ey = (dy - lift) * (dy - lift) * invRy2;
            if (ey > 1.0) {
                continue;
            }
            final int y = vy + dy;
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    final double d2 = (dx * dx + dz * dz) * invR2 + ey;
                    if (d2 > 1.0) {
                        continue;
                    }
                    final int x = vx + dx, z = vz + dz;
                    // questions.md Q8: the map's 33 × 33 columns are the widest legal crown.
                    if (!VoxelMap.inBounds(x, y, z)) {
                        continue;
                    }
                    final int index = VoxelMap.index(x, y, z);
                    final byte content = map.content[index];
                    if (content == VoxelMap.EMPTY) {
                        map.content[index] = d2 <= INNER ? VoxelMap.CANDIDATE_INNER : VoxelMap.CANDIDATE_OUTER;
                        map.touch(y);
                        addCandidate(index);
                    } else if (content == VoxelMap.CANDIDATE_OUTER && d2 <= INNER) {
                        // Overlapping ellipsoids keep the smallest distance; only its side of 0.7 matters.
                        map.content[index] = VoxelMap.CANDIDATE_INNER;
                    }
                }
            }
        }
    }

    /** Step 4 and the canLeaf test of step 3, cheapest first: position hash, area, then the world read. */
    private void thinAndTest(FoliageParams params) {
        int kept = 0;
        for (int i = 0; i < candidateCount; i++) {
            final int index = candidateIndex[i];
            final int x = VoxelMap.xOf(index), y = VoxelMap.yOf(index), z = VoxelMap.zOf(index);
            boolean keep = map.content[index] == VoxelMap.CANDIDATE_INNER
                    || unitHash(seed, x, y, z) < params.density();
            keep = keep && limits.contains(x, z, 0);
            if (keep) {
                byte flags = map.flags[index];
                if ((flags & VoxelMap.LEAF_KNOWN) == 0) {
                    worldReads++;
                    flags |= VoxelMap.LEAF_KNOWN;
                    if (world.test(x, y, z)) {
                        flags |= VoxelMap.LEAF_OK;
                    }
                    map.flags[index] = flags;
                }
                keep = (flags & VoxelMap.LEAF_OK) != 0;
            }
            if (keep) {
                map.content[index] = VoxelMap.CANDIDATE_KEPT;
                candidateIndex[kept++] = index;
            } else {
                map.content[index] = VoxelMap.EMPTY;
            }
        }
        candidateCount = kept;
    }

    /** Step 5: a candidate with more than {@code smother} kept candidates above it in its column is discarded. */
    private void shade(FoliageParams params) {
        final int smother = params.smother();
        if (smother == 0) {
            return;
        }
        if (shaded.length < candidateCount) {
            shaded = new boolean[StrictMath.max(candidateCount, shaded.length * 2)];
        }
        final int top = VoxelMap.index(0, map.maxTouchedY(), 0) / VoxelMap.LAYER;
        for (int i = 0; i < candidateCount; i++) {
            int above = 0;
            int index = candidateIndex[i] + VoxelMap.LAYER;
            for (int layer = index / VoxelMap.LAYER; layer <= top && above <= smother; layer++, index += VoxelMap.LAYER) {
                if (map.content[index] == VoxelMap.CANDIDATE_KEPT) {
                    above++;
                }
            }
            shaded[i] = above > smother;
        }
        int kept = 0;
        for (int i = 0; i < candidateCount; i++) {
            if (shaded[i]) {
                map.content[candidateIndex[i]] = VoxelMap.EMPTY;
                discardedByShade++;
            } else {
                candidateIndex[kept++] = candidateIndex[i];
            }
        }
        candidateCount = kept;
    }

    /** Step 6: breadth-first from all wood through kept candidates across faces, vanilla's leaf metric. */
    private void reach(WoodBuilder wood, FoliageParams params) {
        final int needed = wood.woodCount + candidateCount;
        if (queue.length < needed) {
            queue = new int[StrictMath.max(needed, queue.length * 2)];
        }
        int head = 0, tail = 0;
        for (int i = 0; i < wood.woodCount; i++) {
            queue[tail++] = VoxelMap.index(wood.woodX[i], wood.woodY[i], wood.woodZ[i]);
        }
        final int maxDistance = params.maxDistance();
        while (head < tail) {
            final int index = queue[head++];
            final byte content = map.content[index];
            final int distance = VoxelMap.isWood(content) ? 0 : content - VoxelMap.LEAF;
            if (distance >= maxDistance) {
                continue;
            }
            final int x = VoxelMap.xOf(index), y = VoxelMap.yOf(index), z = VoxelMap.zOf(index);
            tail = visit(x, y - 1, z, distance + 1, tail);
            tail = visit(x, y + 1, z, distance + 1, tail);
            tail = visit(x, y, z - 1, distance + 1, tail);
            tail = visit(x, y, z + 1, distance + 1, tail);
            tail = visit(x - 1, y, z, distance + 1, tail);
            tail = visit(x + 1, y, z, distance + 1, tail);
        }
        for (int i = 0; i < candidateCount; i++) {
            final int index = candidateIndex[i];
            if (map.content[index] == VoxelMap.CANDIDATE_KEPT) {
                map.content[index] = VoxelMap.EMPTY;
                discardedByReach++;
            }
        }
    }

    private int visit(int x, int y, int z, int distance, int tail) {
        if (!VoxelMap.inBounds(x, y, z)) {
            return tail;
        }
        final int index = VoxelMap.index(x, y, z);
        if (map.content[index] != VoxelMap.CANDIDATE_KEPT) {
            return tail;
        }
        map.content[index] = (byte) (VoxelMap.LEAF + distance);
        queue[tail] = index;
        addLeaf(x, y, z, distance);
        return tail + 1;
    }

    /** Step 7: over budget, leaves go from the greatest distance inward, ties broken by the position hash. */
    private void applyBudget(FoliageParams params) {
        final int budget = params.maxLeaves();
        if (leafCount <= budget) {
            return;
        }
        // Leaves are in breadth-first order, so sorted by distance. Only the distance class that straddles
        // the budget is cut partly: its leaves with the smallest hashes stay.
        final int cutDistance = leafDistance[budget];
        int from = budget;
        while (from > 0 && leafDistance[from - 1] == cutDistance) {
            from--;
        }
        int to = budget;
        while (to < leafCount && leafDistance[to] == cutDistance) {
            to++;
        }
        final int size = to - from;
        if (sortKeys.length < size) {
            sortKeys = new long[StrictMath.max(size, sortKeys.length * 2)];
        }
        for (int i = 0; i < size; i++) {
            final long hash = mix(seed, leafX[from + i], leafY[from + i], leafZ[from + i]);
            sortKeys[i] = (hash & ~0xFFFFL) | i;
        }
        Arrays.sort(sortKeys, 0, size);
        final int keepInClass = budget - from;
        for (int i = keepInClass; i < size; i++) {
            leafDistance[from + (int) (sortKeys[i] & 0xFFFFL)] = -1;
        }
        int kept = from;
        for (int i = from; i < leafCount; i++) {
            if (i < to && leafDistance[i] >= 0) {
                leafX[kept] = leafX[i];
                leafY[kept] = leafY[i];
                leafZ[kept] = leafZ[i];
                leafDistance[kept] = leafDistance[i];
                kept++;
            } else {
                map.content[VoxelMap.index(leafX[i], leafY[i], leafZ[i])] = VoxelMap.EMPTY;
                discardedByBudget++;
            }
        }
        leafCount = kept;
    }

    /** A hash of the seed and a position, spread over all 64 bits (spec 7.6 step 4, spec 7.7). */
    static long mix(long seed, int x, int y, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    static double unitHash(long seed, int x, int y, int z) {
        return (mix(seed, x, y, z) >>> 11) * 0x1.0p-53;
    }

    private void addCandidate(int index) {
        if (candidateCount == candidateIndex.length) {
            candidateIndex = Arrays.copyOf(candidateIndex, candidateCount * 2);
        }
        candidateIndex[candidateCount++] = index;
    }

    private void addLeaf(int x, int y, int z, int distance) {
        if (leafCount == leafX.length) {
            final int size = leafCount * 2;
            leafX = Arrays.copyOf(leafX, size);
            leafY = Arrays.copyOf(leafY, size);
            leafZ = Arrays.copyOf(leafZ, size);
            leafDistance = Arrays.copyOf(leafDistance, size);
        }
        leafX[leafCount] = x;
        leafY[leafCount] = y;
        leafZ[leafCount] = z;
        leafDistance[leafCount] = distance;
        leafCount++;
    }
}
