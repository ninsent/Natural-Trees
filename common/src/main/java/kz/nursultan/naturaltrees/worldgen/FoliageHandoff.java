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

import java.util.List;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

/**
 * The foliage handoff of spec 9.1: a per-thread slot through which the trunk placer passes the leaf voxels
 * to the foliage placer. Vanilla calls the two consecutively on one thread and hands the foliage placer the
 * very attachment objects the trunk placer returned (assumption 11), so identity tells whose leaves these are.
 */
final class FoliageHandoff {

    private static final ThreadLocal<FoliageHandoff> SLOT = ThreadLocal.withInitial(FoliageHandoff::new);

    private long[] leaves = new long[1024];
    private int leafCount;
    private List<FoliagePlacer.FoliageAttachment> attachments = List.of();

    private FoliageHandoff() {
    }

    static FoliageHandoff get() {
        return SLOT.get();
    }

    void clear() {
        leafCount = 0;
        attachments = List.of();
    }

    void addLeaf(long packedPosition) {
        if (leafCount == leaves.length) {
            leaves = java.util.Arrays.copyOf(leaves, leafCount * 2);
        }
        leaves[leafCount++] = packedPosition;
    }

    void setAttachments(List<FoliagePlacer.FoliageAttachment> attachments) {
        this.attachments = attachments;
    }

    /** True when the trunk placer returned this very object for the tree whose leaves are held here. */
    boolean owns(FoliagePlacer.FoliageAttachment attachment) {
        for (FoliagePlacer.FoliageAttachment own : attachments) {
            if (own == attachment) {
                return true;
            }
        }
        return false;
    }

    int leafCount() {
        return leafCount;
    }

    long leaf(int index) {
        return leaves[index];
    }

    /** The leaves are placed once; the tree's other attachments then find the slot empty and do nothing. */
    void dropLeaves() {
        leafCount = 0;
    }
}
