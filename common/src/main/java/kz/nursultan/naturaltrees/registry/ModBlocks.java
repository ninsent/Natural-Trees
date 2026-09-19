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
package kz.nursultan.naturaltrees.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import kz.nursultan.naturaltrees.Constants;
import kz.nursultan.naturaltrees.block.BranchBlock;
import kz.nursultan.naturaltrees.block.BranchWood;
import kz.nursultan.naturaltrees.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

/** The sixteen branch blocks and their items (spec 6.1). */
public final class ModBlocks {

    private static final Map<BranchWood, Supplier<BranchBlock>> PLAIN = new EnumMap<>(BranchWood.class);
    private static final Map<BranchWood, Supplier<BranchBlock>> STRIPPED = new EnumMap<>(BranchWood.class);
    private static final Map<BranchWood, Supplier<Item>> PLAIN_ITEMS = new EnumMap<>(BranchWood.class);
    private static final Map<BranchWood, Supplier<Item>> STRIPPED_ITEMS = new EnumMap<>(BranchWood.class);

    private ModBlocks() {
    }

    /** Called once by each loader while registries are open. */
    public static void register() {
        for (BranchWood wood : BranchWood.values()) {
            for (boolean stripped : new boolean[] {false, true}) {
                final ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, wood.branchId(stripped));
                final Supplier<BranchBlock> block = Services.PLATFORM.register(BuiltInRegistries.BLOCK, id,
                        () -> Services.PLATFORM.createBranchBlock(stripped ? null : () -> branch(wood, true),
                                BranchBlock.propertiesOf(wood.log(stripped))));
                final Supplier<Item> item = Services.PLATFORM.register(BuiltInRegistries.ITEM, id,
                        () -> new BlockItem(block.get(), new Item.Properties()));
                (stripped ? STRIPPED : PLAIN).put(wood, block);
                (stripped ? STRIPPED_ITEMS : PLAIN_ITEMS).put(wood, item);
            }
        }
    }

    public static BranchBlock branch(BranchWood wood, boolean stripped) {
        return (stripped ? STRIPPED : PLAIN).get(wood).get();
    }

    public static Item branchItem(BranchWood wood, boolean stripped) {
        return (stripped ? STRIPPED_ITEMS : PLAIN_ITEMS).get(wood).get();
    }

    /** Every branch block, plain before stripped within each wood. */
    public static List<BranchBlock> allBranches() {
        final List<BranchBlock> all = new ArrayList<>();
        for (BranchWood wood : BranchWood.values()) {
            all.add(branch(wood, false));
            all.add(branch(wood, true));
        }
        return all;
    }
}
