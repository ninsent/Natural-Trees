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

/** Bit masks of a branch block's six arms (spec 6.2), in the order of Minecraft's {@code Direction}. */
public final class Arms {

    public static final int DOWN = 1;
    public static final int UP = 2;
    public static final int NORTH = 4;
    public static final int SOUTH = 8;
    public static final int WEST = 16;
    public static final int EAST = 32;

    private Arms() {
    }

    /** The arm bit for a unit step, or 0 when the step is not along exactly one axis. */
    public static int of(int dx, int dy, int dz) {
        if (dx == 0 && dz == 0) {
            return dy == -1 ? DOWN : (dy == 1 ? UP : 0);
        }
        if (dx == 0 && dy == 0) {
            return dz == -1 ? NORTH : (dz == 1 ? SOUTH : 0);
        }
        if (dy == 0 && dz == 0) {
            return dx == -1 ? WEST : (dx == 1 ? EAST : 0);
        }
        return 0;
    }

    public static int opposite(int arm) {
        switch (arm) {
            case DOWN: return UP;
            case UP: return DOWN;
            case NORTH: return SOUTH;
            case SOUTH: return NORTH;
            case WEST: return EAST;
            case EAST: return WEST;
            default: throw new IllegalArgumentException("not a single arm: " + arm);
        }
    }
}
