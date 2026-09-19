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
 * The output of one generation (spec section 7): wood voxels parent before child, leaf voxels nearest to
 * wood first, and tips. A view of the generator's buffers: read it before the generator is used again.
 */
public final class TreeResult {

    /** Log axis values of {@link #woodAxis}. */
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_Z = 2;

    private Skeleton skeleton;
    private WoodBuilder wood;
    private FoliageBuilder foliage;
    private VoxelMap map;

    TreeResult() {
    }

    void set(Skeleton skeleton, WoodBuilder wood, FoliageBuilder foliage, VoxelMap map) {
        this.skeleton = skeleton;
        this.wood = wood;
        this.foliage = foliage;
        this.map = map;
    }

    public int woodCount() {
        return wood.woodCount;
    }

    public int woodX(int i) {
        return wood.woodX[i];
    }

    public int woodY(int i) {
        return wood.woodY[i];
    }

    public int woodZ(int i) {
        return wood.woodZ[i];
    }

    private byte woodContent(int i) {
        return map.content[VoxelMap.index(wood.woodX[i], wood.woodY[i], wood.woodZ[i])];
    }

    /** True for a branch block, false for a log. */
    public boolean isBranch(int i) {
        return woodContent(i) == VoxelMap.BRANCH;
    }

    /** The axis of a log: {@link #AXIS_X}, {@link #AXIS_Y} or {@link #AXIS_Z}. Undefined for a branch. */
    public int woodAxis(int i) {
        return woodContent(i) - VoxelMap.LOG_X;
    }

    /** The arm mask of a branch, from the bits of {@link Arms}; 0 for a log. */
    public int woodArms(int i) {
        return map.arms[VoxelMap.index(wood.woodX[i], wood.woodY[i], wood.woodZ[i])];
    }

    /** The pipe-model load at this voxel (spec 7.2), for the viewer's overlay. */
    public int woodLoad(int i) {
        return wood.woodLoad[i];
    }

    public int leafCount() {
        return foliage != null ? foliage.leafCount : 0;
    }

    public int leafX(int i) {
        return foliage.leafX[i];
    }

    public int leafY(int i) {
        return foliage.leafY[i];
    }

    public int leafZ(int i) {
        return foliage.leafZ[i];
    }

    /** Face steps from this leaf to the nearest wood, 1 or more. */
    public int leafDistance(int i) {
        return foliage.leafDistance[i];
    }

    public int tipCount() {
        return wood.tipCount;
    }

    public int tipX(int i) {
        return wood.tipX[i];
    }

    public int tipY(int i) {
        return wood.tipY[i];
    }

    public int tipZ(int i) {
        return wood.tipZ[i];
    }

    public int tipRadiusOffset(int i) {
        return wood.tipRadiusOffset[i];
    }

    /** True for a tip on a 2×2 section: the attachment's {@code doubleTrunk} (spec 8.1 step 8). */
    public boolean tipOnWideTrunk(int i) {
        return wood.tipWide[i];
    }

    // ---- Counts for the stats command (spec 16) and the viewer (spec 17) ----

    public int stemsGenerated() {
        return skeleton.count;
    }

    public int stemsRemovedByTipBudget() {
        return skeleton.removedByBudget;
    }

    public boolean stemCapReached() {
        return skeleton.capped;
    }

    public int stemsTruncated() {
        return wood.truncatedStems;
    }

    public int stemsDroppedByFaceRule() {
        return wood.droppedByFace;
    }

    public int stemsDroppedWithParent() {
        return wood.droppedWithParent;
    }

    public boolean wideTrunkBlockedAtBase() {
        return wood.wideTrunkBlocked;
    }

    public int leafCandidates() {
        return foliage != null ? foliage.candidates : 0;
    }

    public int leavesDiscardedByShade() {
        return foliage != null ? foliage.discardedByShade : 0;
    }

    public int leavesDiscardedByReach() {
        return foliage != null ? foliage.discardedByReach : 0;
    }

    public int leavesDiscardedByBudget() {
        return foliage != null ? foliage.discardedByBudget : 0;
    }

    public int worldReads() {
        return wood.worldReads + (foliage != null ? foliage.worldReads : 0);
    }

    /**
     * A 64-bit hash of the ordered output: every wood voxel with its kind, axis and arms, every leaf with
     * its distance, every tip. Two results with the same hash are the same tree, block for block.
     */
    public long contentHash() {
        long h = 0x6E61747572616CL;
        h = step(h, woodCount());
        for (int i = 0; i < woodCount(); i++) {
            h = step(h, woodX(i));
            h = step(h, woodY(i));
            h = step(h, woodZ(i));
            h = step(h, isBranch(i) ? 16 + woodArms(i) : woodAxis(i));
        }
        h = step(h, leafCount());
        for (int i = 0; i < leafCount(); i++) {
            h = step(h, leafX(i));
            h = step(h, leafY(i));
            h = step(h, leafZ(i));
            h = step(h, leafDistance(i));
        }
        h = step(h, tipCount());
        for (int i = 0; i < tipCount(); i++) {
            h = step(h, tipX(i));
            h = step(h, tipY(i));
            h = step(h, tipZ(i));
            h = step(h, tipRadiusOffset(i) * 2 + (tipOnWideTrunk(i) ? 1 : 0));
        }
        return h;
    }

    private static long step(long h, int value) {
        h = (h ^ value) * 0x9E3779B97F4A7C15L;
        return h ^ (h >>> 29);
    }
}
