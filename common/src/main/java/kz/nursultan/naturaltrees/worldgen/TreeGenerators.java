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
package kz.nursultan.naturaltrees.worldgen;

import kz.nursultan.naturaltrees.treecore.PlacementLimits;
import kz.nursultan.naturaltrees.treecore.TreeGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelSimulatedReader;

/** What both placers share: one generator per thread (spec 7.8) and the chunk area of spec 7.5. */
final class TreeGenerators {

    private static final ThreadLocal<TreeGenerator> GENERATOR = ThreadLocal.withInitial(TreeGenerator::new);

    private TreeGenerators() {
    }

    static TreeGenerator get() {
        return GENERATOR.get();
    }

    /**
     * During world generation, the 3×3 chunk area around the decorating chunk (assumption 9); for a sapling
     * or a command, no limit.
     */
    static PlacementLimits limitsFor(LevelSimulatedReader level, BlockPos origin) {
        if (level instanceof WorldGenRegion region) {
            final ChunkPos center = region.getCenter();
            return PlacementLimits.chunkArea(origin.getX(), origin.getZ(), center.x, center.z);
        }
        return PlacementLimits.NONE;
    }
}
