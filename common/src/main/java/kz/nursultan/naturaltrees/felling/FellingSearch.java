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
package kz.nursultan.naturaltrees.felling;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The search of spec 14.3, which follows the tree's structure. From a log it moves to adjacent logs (26
 * neighbours) and to face-adjacent branches that have an arm pointing back at it. From a branch it moves only
 * along the branch's arms. It never goes below the broken block. Two trees whose crowns touch are not joined
 * unless their branches are actually connected.
 */
public final class FellingSearch {

    /** Steps toward the six faces, in the order of {@code Direction}: down, up, north, south, west, east. */
    private static final int[][] FACES = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    private static final int[] OPPOSITE = {1, 0, 3, 2, 5, 4};

    private FellingSearch() {
    }

    /** One wood position found. */
    public record Found(int x, int y, int z) {
    }

    /**
     * @param wood the wood to fell, nearest to the cut first; the broken block itself is not in it
     * @param leaves non-persistent leaves that share a face with that wood
     * @param capped true when {@code maxBlocks} cut the search off
     */
    public record Result(List<Found> wood, int leaves, boolean capped) {
    }

    /**
     * @param brokenKind what the broken block was, {@link WoodView#LOG} or {@link WoodView#BRANCH}; it is gone
     *        from the world by now
     * @param brokenArms its arm mask if it was a branch
     */
    public static Result search(WoodView view, int bx, int by, int bz, int brokenKind, int brokenArms, int maxBlocks) {
        final List<Found> wood = new ArrayList<>();
        final Set<Long> seen = new HashSet<>();
        final Set<Long> leaves = new HashSet<>();
        seen.add(key(bx, by, bz));
        boolean capped = false;

        // The queue is the found list itself, read from `head`; the broken block is expanded first.
        int head = -1;
        while (head < wood.size()) {
            final int x, y, z, kind, arms;
            if (head < 0) {
                x = bx;
                y = by;
                z = bz;
                kind = brokenKind;
                arms = brokenArms;
            } else {
                final Found f = wood.get(head);
                x = f.x();
                y = f.y();
                z = f.z();
                kind = view.kind(x, y, z);
                arms = view.arms(x, y, z);
                for (int[] face : FACES) {
                    if (view.kind(x + face[0], y + face[1], z + face[2]) == WoodView.NATURAL_LEAF) {
                        leaves.add(key(x + face[0], y + face[1], z + face[2]));
                    }
                }
            }
            head++;

            if (kind == WoodView.BRANCH) {
                // Only along the arms.
                for (int d = 0; d < 6; d++) {
                    if ((arms & (1 << d)) != 0) {
                        capped |= visit(view, x + FACES[d][0], y + FACES[d][1], z + FACES[d][2], by, -1, seen, wood, maxBlocks);
                    }
                }
            } else {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            // A branch joins a log only through an arm, so only across a face and only if it points back.
                            final int face = faceOf(dx, dy, dz);
                            capped |= visit(view, x + dx, y + dy, z + dz, by, face, seen, wood, maxBlocks);
                        }
                    }
                }
            }
        }
        return new Result(wood, leaves.size(), capped);
    }

    /**
     * @param fromFace when coming from a log: the face index of the step, or -2 for a diagonal step; -1 when
     *        coming along a branch's arm, which joins whatever wood it points at
     * @return true when the block was wood that the cap kept out
     */
    private static boolean visit(WoodView view, int x, int y, int z, int floorY, int fromFace, Set<Long> seen,
                                 List<Found> wood, int maxBlocks) {
        if (y < floorY || seen.contains(key(x, y, z))) {
            return false;
        }
        final int kind = view.kind(x, y, z);
        if (kind != WoodView.LOG && kind != WoodView.BRANCH) {
            return false;
        }
        if (kind == WoodView.BRANCH && fromFace != -1) {
            if (fromFace < 0 || (view.arms(x, y, z) & (1 << OPPOSITE[fromFace])) == 0) {
                return false;
            }
        }
        if (wood.size() >= maxBlocks) {
            return true;
        }
        seen.add(key(x, y, z));
        wood.add(new Found(x, y, z));
        return false;
    }

    /** The face index of a unit step along one axis, or -2 for a diagonal step. */
    private static int faceOf(int dx, int dy, int dz) {
        for (int d = 0; d < 6; d++) {
            if (FACES[d][0] == dx && FACES[d][1] == dy && FACES[d][2] == dz) {
                return d;
            }
        }
        return -2;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
