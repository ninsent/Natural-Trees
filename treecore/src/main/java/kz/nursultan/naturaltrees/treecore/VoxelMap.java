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
 * The reusable voxel map of spec 7.8: flat byte arrays sized for the largest legal tree, 33 × 33 columns
 * around the trunk origin, with one flag per Y layer recording whether the layer was written. Clearing
 * touches only written layers.
 */
final class VoxelMap {

    static final int RADIUS = TrunkParams.MAX_REACH;
    static final int SIZE = 2 * RADIUS + 1;
    static final int LAYER = SIZE * SIZE;
    static final int MIN_Y = -32;
    static final int HEIGHT = 224;

    static final byte EMPTY = 0;
    static final byte LOG_X = 1;
    static final byte LOG_Y = 2;
    static final byte LOG_Z = 3;
    static final byte BRANCH = 4;
    static final byte CANDIDATE_OUTER = 5;
    static final byte CANDIDATE_INNER = 6;
    static final byte CANDIDATE_KEPT = 7;
    /** A placed leaf at face distance d from wood is stored as {@code LEAF + d}. */
    static final byte LEAF = 8;

    static final byte PLACE_KNOWN = 1;
    static final byte PLACE_OK = 2;
    static final byte LEAF_KNOWN = 4;
    static final byte LEAF_OK = 8;

    final byte[] content = new byte[LAYER * HEIGHT];
    final byte[] arms = new byte[LAYER * HEIGHT];
    final byte[] flags = new byte[LAYER * HEIGHT];
    /** Faces of a wood voxel from which a child stem already starts (spec 7.3). */
    final byte[] childFaces = new byte[LAYER * HEIGHT];
    private final boolean[] touched = new boolean[HEIGHT];
    private int minTouched = HEIGHT;
    private int maxTouched = -1;

    static boolean inBounds(int x, int y, int z) {
        return x >= -RADIUS && x <= RADIUS && z >= -RADIUS && z <= RADIUS && y >= MIN_Y && y < MIN_Y + HEIGHT;
    }

    static int index(int x, int y, int z) {
        return (y - MIN_Y) * LAYER + (z + RADIUS) * SIZE + (x + RADIUS);
    }

    static int xOf(int index) {
        return index % SIZE - RADIUS;
    }

    static int zOf(int index) {
        return index / SIZE % SIZE - RADIUS;
    }

    static int yOf(int index) {
        return index / LAYER + MIN_Y;
    }

    static boolean isWood(byte content) {
        return content >= LOG_X && content <= BRANCH;
    }

    void touch(int y) {
        final int layer = y - MIN_Y;
        if (!touched[layer]) {
            touched[layer] = true;
            if (layer < minTouched) {
                minTouched = layer;
            }
            if (layer > maxTouched) {
                maxTouched = layer;
            }
        }
    }

    /** Highest written Y, or {@code MIN_Y - 1} when nothing was written. */
    int maxTouchedY() {
        return maxTouched + MIN_Y;
    }

    void clear() {
        for (int layer = minTouched; layer <= maxTouched; layer++) {
            if (touched[layer]) {
                final int from = layer * LAYER;
                Arrays.fill(content, from, from + LAYER, EMPTY);
                Arrays.fill(arms, from, from + LAYER, (byte) 0);
                Arrays.fill(flags, from, from + LAYER, (byte) 0);
                Arrays.fill(childFaces, from, from + LAYER, (byte) 0);
                touched[layer] = false;
            }
        }
        minTouched = HEIGHT;
        maxTouched = -1;
    }
}
