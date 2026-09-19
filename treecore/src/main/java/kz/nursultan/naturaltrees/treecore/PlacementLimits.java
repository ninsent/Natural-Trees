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
 * The area a tree may write to, as inclusive block bounds relative to the trunk origin (spec 7.5 test 1,
 * spec 13). During world generation it is the 3×3 chunk area around the decorating chunk; for a sapling
 * there is none.
 */
public record PlacementLimits(int minX, int maxX, int minZ, int maxZ) {

    private static final int FAR = 1 << 24;

    /** No area limit: sapling growth and commands. */
    public static final PlacementLimits NONE = new PlacementLimits(-FAR, FAR, -FAR, FAR);

    /** The 3×3 chunk area around the decorating chunk, for a trunk origin at the given block position. */
    public static PlacementLimits chunkArea(int originX, int originZ, int decoratingChunkX, int decoratingChunkZ) {
        return new PlacementLimits(
                (decoratingChunkX - 1) * 16 - originX, (decoratingChunkX + 2) * 16 - 1 - originX,
                (decoratingChunkZ - 1) * 16 - originZ, (decoratingChunkZ + 2) * 16 - 1 - originZ);
    }

    boolean contains(int x, int z, int margin) {
        return x >= minX + margin && x <= maxX - margin && z >= minZ + margin && z <= maxZ - margin;
    }
}
