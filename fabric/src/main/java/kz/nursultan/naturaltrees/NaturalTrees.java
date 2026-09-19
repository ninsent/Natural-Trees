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
package kz.nursultan.naturaltrees;

import kz.nursultan.naturaltrees.block.BranchBlock;
import kz.nursultan.naturaltrees.block.BranchWood;
import kz.nursultan.naturaltrees.command.NaturalTreesCommands;
import kz.nursultan.naturaltrees.registry.ModBlocks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class NaturalTrees implements ModInitializer {

    /** Spec 6.5: a branch burns for half as long as a log. */
    private static final int BRANCH_BURN_TICKS = 150;
    /** Vanilla's values for logs in {@code FireBlock.bootStrap}. */
    private static final int LOG_IGNITE_ODDS = 5;
    private static final int LOG_BURN_ODDS = 5;

    @Override
    public void onInitialize() {
        NaturalTreesCommon.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> NaturalTreesCommands.register(dispatcher));

        for (BranchBlock branch : ModBlocks.allBranches()) {
            FlammableBlockRegistry.getDefaultInstance().add(branch, LOG_IGNITE_ODDS, LOG_BURN_ODDS);
            FuelRegistry.INSTANCE.add(branch, BRANCH_BURN_TICKS);
        }

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.BUILDING_BLOCKS).register(entries -> {
            for (BranchWood wood : BranchWood.values()) {
                entries.addAfter(wood.buildingTabAnchor(), ModBlocks.branchItem(wood, false), ModBlocks.branchItem(wood, true));
            }
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.NATURAL_BLOCKS).register(entries -> {
            for (BranchWood wood : BranchWood.values()) {
                entries.addAfter(wood.naturalTabAnchor(), ModBlocks.branchItem(wood, false));
            }
        });

        // Spec 6.4: Fabric's strippable registry needs an axis property (assumption 7), so the axe gets its own hook.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            final ItemStack stack = player.getItemInHand(hand);
            if (player.isSpectator() || !(stack.getItem() instanceof AxeItem)) {
                return InteractionResult.PASS;
            }
            final BlockPos pos = hit.getBlockPos();
            final BlockState stripped = BranchBlock.strippedState(level.getBlockState(pos));
            if (stripped == null) {
                return InteractionResult.PASS;
            }
            level.playSound(player, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(serverPlayer, pos, stack);
                level.setBlock(pos, stripped, Block.UPDATE_ALL_IMMEDIATE);
                level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, stripped));
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        });
    }
}
