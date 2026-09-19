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

import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** The overworld wood types that have a branch block (spec 6.1), each bound to its vanilla log. */
public enum BranchWood {
    OAK(() -> Blocks.OAK_LOG, () -> Blocks.STRIPPED_OAK_LOG, () -> Items.OAK_LOG, () -> Items.STRIPPED_OAK_WOOD),
    SPRUCE(() -> Blocks.SPRUCE_LOG, () -> Blocks.STRIPPED_SPRUCE_LOG, () -> Items.SPRUCE_LOG, () -> Items.STRIPPED_SPRUCE_WOOD),
    BIRCH(() -> Blocks.BIRCH_LOG, () -> Blocks.STRIPPED_BIRCH_LOG, () -> Items.BIRCH_LOG, () -> Items.STRIPPED_BIRCH_WOOD),
    JUNGLE(() -> Blocks.JUNGLE_LOG, () -> Blocks.STRIPPED_JUNGLE_LOG, () -> Items.JUNGLE_LOG, () -> Items.STRIPPED_JUNGLE_WOOD),
    ACACIA(() -> Blocks.ACACIA_LOG, () -> Blocks.STRIPPED_ACACIA_LOG, () -> Items.ACACIA_LOG, () -> Items.STRIPPED_ACACIA_WOOD),
    DARK_OAK(() -> Blocks.DARK_OAK_LOG, () -> Blocks.STRIPPED_DARK_OAK_LOG, () -> Items.DARK_OAK_LOG, () -> Items.STRIPPED_DARK_OAK_WOOD),
    MANGROVE(() -> Blocks.MANGROVE_LOG, () -> Blocks.STRIPPED_MANGROVE_LOG, () -> Items.MANGROVE_LOG, () -> Items.STRIPPED_MANGROVE_WOOD),
    CHERRY(() -> Blocks.CHERRY_LOG, () -> Blocks.STRIPPED_CHERRY_LOG, () -> Items.CHERRY_LOG, () -> Items.STRIPPED_CHERRY_WOOD);

    private final Supplier<Block> log;
    private final Supplier<Block> strippedLog;
    private final Supplier<Item> naturalTabAnchor;
    private final Supplier<Item> buildingTabAnchor;

    BranchWood(Supplier<Block> log, Supplier<Block> strippedLog, Supplier<Item> naturalTabAnchor,
               Supplier<Item> buildingTabAnchor) {
        this.log = log;
        this.strippedLog = strippedLog;
        this.naturalTabAnchor = naturalTabAnchor;
        this.buildingTabAnchor = buildingTabAnchor;
    }

    /** The wood's name as it appears in ids: {@code oak}, {@code dark_oak}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The registry path of this wood's branch: {@code oak_branch} or {@code stripped_oak_branch}. */
    public String branchId(boolean stripped) {
        return (stripped ? "stripped_" : "") + id() + "_branch";
    }

    /** The vanilla log whose properties and textures the branch takes (spec 6.3, 6.4). */
    public Block log(boolean stripped) {
        return (stripped ? strippedLog : log).get();
    }

    /** The item after which the plain branch appears in the Natural Blocks tab: the wood's log. */
    public Item naturalTabAnchor() {
        return naturalTabAnchor.get();
    }

    /** The item after which both branches appear in the Building Blocks tab: the wood's stripped wood. */
    public Item buildingTabAnchor() {
        return buildingTabAnchor.get();
    }
}
