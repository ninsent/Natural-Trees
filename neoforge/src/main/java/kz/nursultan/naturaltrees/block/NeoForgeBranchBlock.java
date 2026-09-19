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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/** The branch block with NeoForge's extension methods: the tool-modified-state hook and flammability (spec 6.4). */
public class NeoForgeBranchBlock extends BranchBlock {

    /** Vanilla's values for logs in {@code FireBlock.bootStrap}. */
    private static final int LOG_IGNITE_ODDS = 5;
    private static final int LOG_BURN_ODDS = 5;

    public NeoForgeBranchBlock(BranchWood wood, boolean stripped, BlockBehaviour.Properties properties) {
        super(wood, stripped, properties);
    }

    @Override
    public BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility ability, boolean simulate) {
        if (ability == ItemAbilities.AXE_STRIP) {
            final BlockState stripped = strippedState(state);
            if (stripped != null) {
                return stripped;
            }
        }
        return super.getToolModifiedState(state, context, ability, simulate);
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return LOG_BURN_ODDS;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return LOG_IGNITE_ODDS;
    }
}
