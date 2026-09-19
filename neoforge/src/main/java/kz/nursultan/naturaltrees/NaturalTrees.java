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

import kz.nursultan.naturaltrees.block.BranchWood;
import kz.nursultan.naturaltrees.command.NaturalTreesCommands;
import kz.nursultan.naturaltrees.platform.NeoForgePlatformHelper;
import kz.nursultan.naturaltrees.registry.ModBlocks;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(Constants.MOD_ID)
public class NaturalTrees {

    public NaturalTrees(IEventBus modBus) {
        NeoForgePlatformHelper.setModBus(modBus);
        NaturalTreesCommon.init();
        modBus.addListener(NaturalTrees::addToCreativeTabs);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> NaturalTreesCommands.register(event.getDispatcher()));
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        final CreativeModeTab.TabVisibility everywhere = CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS;
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            for (BranchWood wood : BranchWood.values()) {
                final ItemStack plain = new ItemStack(ModBlocks.branchItem(wood, false));
                event.insertAfter(new ItemStack(wood.buildingTabAnchor()), plain, everywhere);
                event.insertAfter(plain, new ItemStack(ModBlocks.branchItem(wood, true)), everywhere);
            }
        } else if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            for (BranchWood wood : BranchWood.values()) {
                event.insertAfter(new ItemStack(wood.naturalTabAnchor()), new ItemStack(ModBlocks.branchItem(wood, false)), everywhere);
            }
        }
    }
}
