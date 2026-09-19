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
 * The generator (spec section 7): skeleton, tip budget, thickness, wood, foliage. One instance holds the
 * buffers for one thread and is reused for every tree; nothing but buffers is carried from tree to tree.
 * The returned {@link TreeResult} is a view of those buffers and is valid until the next call.
 */
public final class TreeGenerator {

    private final Xoroshiro128PlusPlus rng = new Xoroshiro128PlusPlus(0L);
    // Package-private so that tests can check the stages against each other.
    final Skeleton skeleton = new Skeleton();
    final VoxelMap map = new VoxelMap();
    final WoodBuilder wood = new WoodBuilder();
    final FoliageBuilder foliage = new FoliageBuilder();
    private final TreeResult result = new TreeResult();

    /**
     * Generates one tree. Coordinates in the result, in {@code limits} and in the two world tests are
     * relative to the trunk origin.
     *
     * @param foliageParams the parameters of {@code naturaltrees:skeleton}, or null when the species uses
     *        another foliage placer; then no leaf voxels are computed (spec 8.1 step 3)
     * @param height the free height vanilla passed to the trunk placer, in blocks
     * @param canPlaceWorld vanilla's tree-position rule for wood; called last of the tests of spec 7.5
     * @param canLeafWorld the same for leaves
     */
    public TreeResult generate(TrunkParams trunkParams, FoliageParams foliageParams, long seed, int height,
                               PlacementLimits limits, WorldRead canPlaceWorld, WorldRead canLeafWorld) {
        if (height < 1) {
            throw new IllegalArgumentException("height: " + height + " is less than 1");
        }
        map.clear();
        rng.setSeed(seed);
        skeleton.generate(trunkParams, rng, height);
        wood.build(skeleton, trunkParams, map, limits, canPlaceWorld);
        if (foliageParams != null) {
            foliage.build(skeleton, wood, foliageParams, map, limits, canLeafWorld, seed);
        }
        result.set(skeleton, wood, foliageParams != null ? foliage : null, map);
        return result;
    }

    /**
     * The fallback cluster of spec 9.1: the foliage of one wood voxel, for a species that pairs the skeleton
     * foliage placer with another trunk placer. Coordinates are relative to that voxel, which is taken to
     * exist and is not part of the result; the result holds leaves only.
     */
    public TreeResult generateCluster(FoliageParams foliageParams, long seed, PlacementLimits limits,
                                      WorldRead canLeafWorld) {
        map.clear();
        wood.clear();
        foliage.buildCluster(foliageParams, map, limits, canLeafWorld, seed);
        result.set(skeleton, wood, foliage, map);
        return result;
    }
}
