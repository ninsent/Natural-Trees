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

import com.mojang.serialization.MapCodec;
import kz.nursultan.naturaltrees.registry.ModPlacers;
import kz.nursultan.naturaltrees.treecore.FoliageParams;
import kz.nursultan.naturaltrees.treecore.TreeResult;
import kz.nursultan.naturaltrees.treecore.WorldRead;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;

/**
 * The foliage placer {@code naturaltrees:skeleton} (spec section 9). Its shape comes entirely from the
 * generator: it places the leaf voxels the trunk placer left in the foliage handoff, through vanilla's
 * {@code tryPlaceLeaf}, so that the leaf provider, waterlogging, distance baking and decorators work as for
 * any tree.
 */
public class SkeletonFoliagePlacer extends FoliagePlacer {

    public static final MapCodec<SkeletonFoliagePlacer> CODEC =
            ParamCodecs.FOLIAGE.xmap(SkeletonFoliagePlacer::new, SkeletonFoliagePlacer::params);

    private final FoliageParams params;

    public SkeletonFoliagePlacer(FoliageParams params) {
        // Spec 9.1: constant zero radius and offset for the vanilla base class (assumption 12).
        super(ConstantInt.of(0), ConstantInt.of(0));
        this.params = params;
    }

    public FoliageParams params() {
        return params;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return ModPlacers.skeleton();
    }

    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliageSetter setter, RandomSource random,
                                 TreeConfiguration config, int maxFreeTreeHeight, FoliageAttachment attachment,
                                 int foliageHeight, int foliageRadius, int offset) {
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final FoliageHandoff handoff = FoliageHandoff.get();
        if (handoff.owns(attachment)) {
            // The first attachment of the tree places every leaf; the others find the slot empty.
            final int count = handoff.leafCount();
            for (int i = 0; i < count; i++) {
                tryPlaceLeaf(level, setter, random, config, cursor.set(handoff.leaf(i)));
            }
            handoff.dropLeaves();
            return;
        }

        // Spec 9.1: paired with another trunk placer. One cluster around the wood voxel below the attachment.
        final BlockPos wood = attachment.pos().below();
        final int ox = wood.getX(), oy = wood.getY(), oz = wood.getZ();
        final WorldRead validTreePos = (x, y, z) -> TreeFeature.validTreePos(level, cursor.set(ox + x, oy + y, oz + z));
        final TreeResult cluster = TreeGenerators.get().generateCluster(params, random.nextLong(),
                TreeGenerators.limitsFor(level, wood), validTreePos);
        for (int i = 0; i < cluster.leafCount(); i++) {
            tryPlaceLeaf(level, setter, random, config, cursor.set(ox + cluster.leafX(i), oy + cluster.leafY(i), oz + cluster.leafZ(i)));
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return 0;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        return false;
    }
}
