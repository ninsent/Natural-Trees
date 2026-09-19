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
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import kz.nursultan.naturaltrees.Constants;
import kz.nursultan.naturaltrees.registry.ModPlacers;
import kz.nursultan.naturaltrees.treecore.FoliageParams;
import kz.nursultan.naturaltrees.treecore.PlacementLimits;
import kz.nursultan.naturaltrees.treecore.TreeResult;
import kz.nursultan.naturaltrees.treecore.TrunkParams;
import kz.nursultan.naturaltrees.treecore.WorldRead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import net.minecraft.world.level.material.Fluids;

/**
 * The trunk placer {@code naturaltrees:weber_penn} (spec section 8). It runs the generator once per tree,
 * places the wood through vanilla's trunk setter, hands the leaf voxels to the foliage placer and returns one
 * attachment per tip.
 */
public class WeberPennTrunkPlacer extends TrunkPlacer {

    public static final MapCodec<WeberPennTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(instance ->
            trunkPlacerParts(instance)
                    .and(BlockStateProvider.CODEC.fieldOf("branch_provider").forGetter(p -> p.branchProvider))
                    .and(ParamCodecs.TRUNK.forGetter(p -> p.params))
                    .apply(instance, WeberPennTrunkPlacer::new));

    private static final Direction.Axis[] AXES = {Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z};
    private static final Direction[] DIRECTIONS = Direction.values();

    private final BlockStateProvider branchProvider;
    private final TrunkParams params;
    private volatile boolean warnedAboutMargin;
    private volatile boolean warnedAboutArms;

    public WeberPennTrunkPlacer(int baseHeight, int heightRandA, int heightRandB, BlockStateProvider branchProvider,
                                TrunkParams params) {
        super(baseHeight, heightRandA, heightRandB);
        this.branchProvider = branchProvider;
        this.params = params;
    }

    public TrunkParams params() {
        return params;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return ModPlacers.weberPenn();
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader level,
                                                            BiConsumer<BlockPos, BlockState> setter,
                                                            RandomSource random, int freeTreeHeight, BlockPos origin,
                                                            TreeConfiguration config) {
        // Spec 8.1 step 1: nothing of an earlier tree may reach this tree's foliage placer.
        final FoliageHandoff handoff = FoliageHandoff.get();
        handoff.clear();
        if (freeTreeHeight < 1) {
            return List.of();
        }
        final long seed = random.nextLong();

        // Step 2. The arithmetic tests live in treecore; only vanilla's tree-position rule reads the world.
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        final WorldRead validTreePos = (x, y, z) -> TreeFeature.validTreePos(level, cursor.set(ox + x, oy + y, oz + z));
        final PlacementLimits limits = TreeGenerators.limitsFor(level, origin);

        // Step 3.
        final FoliageParams foliage = config.foliagePlacer instanceof SkeletonFoliagePlacer skeleton ? skeleton.params() : null;
        if (foliage != null && !warnedAboutMargin && params.foliageMargin() < foliage.requiredMargin()) {
            warnedAboutMargin = true;
            Constants.LOG.warn("A species has foliage_margin {} but its foliage needs {} (radius_tip × (1 + radius_v), "
                    + "rounded up); its crowns will be cut flat at chunk-area borders", params.foliageMargin(), foliage.requiredMargin());
        }

        // Step 4.
        final TreeResult tree = TreeGenerators.get().generate(params, foliage, seed, freeTreeHeight, limits, validTreePos, validTreePos);

        // Step 5: dirt under every trunk column, as vanilla trunk placers do.
        for (int i = 0; i < tree.woodCount() && tree.woodY(i) <= 0; i++) {
            if (tree.woodY(i) == 0 && tree.woodX(i) >= 0 && tree.woodX(i) <= 1 && tree.woodZ(i) >= 0 && tree.woodZ(i) <= 1) {
                setDirtAt(level, setter, random, cursor.set(ox + tree.woodX(i), oy - 1, oz + tree.woodZ(i)).immutable(), config);
            }
        }

        // Step 6. The setter copies the position (assumption 14), so one mutable position serves every block.
        for (int i = 0; i < tree.woodCount(); i++) {
            cursor.set(ox + tree.woodX(i), oy + tree.woodY(i), oz + tree.woodZ(i));
            setter.accept(cursor, tree.isBranch(i) ? branchState(level, random, cursor, tree.woodArms(i))
                    : logState(config, random, cursor, tree.woodAxis(i)));
        }

        // Steps 7 and 8. The attachment sits above the tip's wood voxel, as with vanilla trunk placers.
        for (int i = 0; i < tree.leafCount(); i++) {
            handoff.addLeaf(BlockPos.asLong(ox + tree.leafX(i), oy + tree.leafY(i), oz + tree.leafZ(i)));
        }
        final List<FoliagePlacer.FoliageAttachment> attachments = new ArrayList<>(tree.tipCount());
        for (int i = 0; i < tree.tipCount(); i++) {
            attachments.add(new FoliagePlacer.FoliageAttachment(
                    new BlockPos(ox + tree.tipX(i), oy + tree.tipY(i) + 1, oz + tree.tipZ(i)),
                    tree.tipRadiusOffset(i), tree.tipOnWideTrunk(i)));
        }
        handoff.setAttachments(attachments);
        return attachments;
    }

    private static BlockState logState(TreeConfiguration config, RandomSource random, BlockPos pos, int axis) {
        final BlockState state = config.trunkProvider.getState(random, pos);
        return state.hasProperty(RotatedPillarBlock.AXIS) ? state.setValue(RotatedPillarBlock.AXIS, AXES[axis]) : state;
    }

    private BlockState branchState(LevelSimulatedReader level, RandomSource random, BlockPos pos, int arms) {
        BlockState state = branchProvider.getState(random, pos);
        for (Direction direction : DIRECTIONS) {
            final BooleanProperty arm = PipeBlock.PROPERTY_BY_DIRECTION.get(direction);
            if (!state.hasProperty(arm)) {
                if (!warnedAboutArms) {
                    warnedAboutArms = true;
                    Constants.LOG.warn("branch_provider gave {}, which lacks the six arm properties (spec 8.2); "
                            + "it is placed without arms", state.getBlock());
                }
                return state;
            }
            // treecore's arm bits follow the order of Direction, down up north south west east.
            state = state.setValue(arm, (arms & (1 << direction.ordinal())) != 0);
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            state = state.setValue(BlockStateProperties.WATERLOGGED,
                    level.isFluidAtPosition(pos, fluid -> fluid.isSourceOfType(Fluids.WATER)));
        }
        return state;
    }
}
