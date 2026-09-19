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
package kz.nursultan.naturaltrees.block;

import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The branch block (spec section 6): a thin, connecting wooden block that behaves as a log. A centred
 * 8 px core plus one arm per set direction. Arms are explicit state: the generator, a placement or a pair
 * of shears sets them; a neighbour update only ever clears one.
 */
public class BranchBlock extends Block implements SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** The six arm properties, {@code down up north south west east}. */
    public static final Map<Direction, BooleanProperty> ARMS = PipeBlock.PROPERTY_BY_DIRECTION;

    private static final Direction[] DIRECTIONS = Direction.values();
    /** Shapes for the 64 arm masks; bit {@code i} is the arm toward {@code Direction.values()[i]}. */
    private static final VoxelShape[] SHAPES = buildShapes();

    private final Supplier<? extends Block> strippedVariant;

    /**
     * @param strippedVariant what an axe turns this branch into, itself a {@code BranchBlock}; null for a branch that
     *        is already stripped. A supplier, because the two blocks of a wood are registered one after the other.
     *        Nothing here knows this mod's own woods, so another mod can create branches for its wood the same way.
     */
    public BranchBlock(Supplier<? extends Block> strippedVariant, BlockBehaviour.Properties properties) {
        super(properties);
        this.strippedVariant = strippedVariant;
        BlockState state = stateDefinition.any().setValue(WATERLOGGED, false);
        for (BooleanProperty arm : ARMS.values()) {
            state = state.setValue(arm, false);
        }
        registerDefaultState(state);
    }

    /**
     * The properties of a branch: those of its log (hardness, blast resistance, sound, instrument,
     * lava ignition), with the bark's map colour and without the full cube's occlusion.
     */
    public static BlockBehaviour.Properties propertiesOf(Block log) {
        // A log picks its map colour by axis; a branch has no axis, so it takes the colour of the log's side.
        final BlockState sideways = log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        return BlockBehaviour.Properties.ofFullCopy(log)
                .mapColor(sideways.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
                .noOcclusion()
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .isRedstoneConductor((state, level, pos) -> false);
    }

    public boolean isStripped() {
        return strippedVariant == null;
    }

    /** The mask of a state's arms; bit {@code i} is the arm toward {@code Direction.values()[i]}. */
    public static int armMask(BlockState state) {
        int mask = 0;
        for (Direction direction : DIRECTIONS) {
            if (state.getValue(ARMS.get(direction))) {
                mask |= 1 << direction.ordinal();
            }
        }
        return mask;
    }

    /** The state with exactly the arms of {@code mask}; waterlogging is kept. */
    public static BlockState withArmMask(BlockState state, int mask) {
        for (Direction direction : DIRECTIONS) {
            state = state.setValue(ARMS.get(direction), (mask & (1 << direction.ordinal())) != 0);
        }
        return state;
    }

    /**
     * What an axe makes of this state (spec 6.4): the stripped variant with the same arms and waterlogging,
     * or null when the block is already stripped. Each loader calls this from its own hook.
     */
    public static BlockState strippedState(BlockState state) {
        if (!(state.getBlock() instanceof BranchBlock branch) || branch.strippedVariant == null
                || !(branch.strippedVariant.get() instanceof BranchBlock target)) {
            return null;
        }
        final BlockState result = target.defaultBlockState().setValue(WATERLOGGED, state.getValue(WATERLOGGED));
        return withArmMask(result, armMask(state));
    }

    private static VoxelShape[] buildShapes() {
        final VoxelShape core = Block.box(4, 4, 4, 12, 12, 12);
        final VoxelShape[] arms = new VoxelShape[6];
        arms[Direction.DOWN.ordinal()] = Block.box(4, 0, 4, 12, 4, 12);
        arms[Direction.UP.ordinal()] = Block.box(4, 12, 4, 12, 16, 12);
        arms[Direction.NORTH.ordinal()] = Block.box(4, 4, 0, 12, 12, 4);
        arms[Direction.SOUTH.ordinal()] = Block.box(4, 4, 12, 12, 12, 16);
        arms[Direction.WEST.ordinal()] = Block.box(0, 4, 4, 4, 12, 12);
        arms[Direction.EAST.ordinal()] = Block.box(12, 4, 4, 16, 12, 12);
        final VoxelShape[] shapes = new VoxelShape[64];
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = core;
            for (int i = 0; i < 6; i++) {
                if ((mask & (1 << i)) != 0) {
                    shape = Shapes.or(shape, arms[i]);
                }
            }
            shapes[mask] = shape.optimize();
        }
        return shapes;
    }

    /** The collision and selection shape for an arm mask. */
    public static VoxelShape shapeOf(int mask) {
        return SHAPES[mask];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.DOWN, BlockStateProperties.UP, BlockStateProperties.NORTH,
                BlockStateProperties.SOUTH, BlockStateProperties.WEST, BlockStateProperties.EAST, WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[armMask(state)];
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.getValue(WATERLOGGED);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    /** Spec 6.3: two arm ends that meet face to face are both skipped. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.getBlock() instanceof BranchBlock && adjacent.getValue(ARMS.get(direction.getOpposite()))
                || super.skipRendering(state, adjacent, direction);
    }

    /** Spec 6.4: a placed branch gets one arm toward the block it was placed against. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return defaultBlockState()
                .setValue(ARMS.get(context.getClickedFace().getOpposite()), true)
                .setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    /** Spec 6.4: if the block it was placed against is a branch, that branch gets the reciprocal arm. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        for (Direction direction : DIRECTIONS) {
            if (state.getValue(ARMS.get(direction))) {
                setReciprocalArm(level, pos, direction, true);
            }
        }
    }

    private static void setReciprocalArm(Level level, BlockPos pos, Direction direction, boolean value) {
        final BlockPos neighbourPos = pos.relative(direction);
        final BlockState neighbour = level.getBlockState(neighbourPos);
        if (neighbour.getBlock() instanceof BranchBlock) {
            level.setBlock(neighbourPos, neighbour.setValue(ARMS.get(direction.getOpposite()), value), Block.UPDATE_ALL);
        }
    }

    /**
     * Spec 6.4: clears an arm when the block it points at becomes air or fluid. It never sets an arm, so a
     * generated state, whose arms all point at wood, is a fixed point.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (state.getValue(ARMS.get(direction)) && (neighbour.isAir() || neighbour.getBlock() instanceof LiquidBlock)) {
            return state.setValue(ARMS.get(direction), false);
        }
        return state;
    }

    /** Spec 6.4: shears toggle the arm on the clicked face, and the reciprocal arm of a branch neighbour. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof ShearsItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            final Direction face = hit.getDirection();
            final boolean value = !state.getValue(ARMS.get(face));
            // UPDATE_KNOWN_SHAPE: the new arm may point at air, which a shape update would clear at once.
            level.setBlock(pos, state.setValue(ARMS.get(face), value), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            setReciprocalArm(level, pos, face, value);
            level.playSound(null, pos, SoundEvents.GROWING_PLANT_CROP, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState result = state;
        for (Direction direction : DIRECTIONS) {
            result = result.setValue(ARMS.get(rotation.rotate(direction)), state.getValue(ARMS.get(direction)));
        }
        return result;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState result = state;
        for (Direction direction : DIRECTIONS) {
            result = result.setValue(ARMS.get(mirror.mirror(direction)), state.getValue(ARMS.get(direction)));
        }
        return result;
    }
}
