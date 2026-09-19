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
package kz.nursultan.naturaltrees.viewer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import kz.nursultan.naturaltrees.treecore.TreeResult;

/**
 * The binary answer of {@code POST /generate} (spec 17). Little-endian.
 *
 * <pre>
 * int32  magic "NTV1"
 * int32  wood count, leaf count, tip count
 * int32  x 12 counters, in the order of {@link #COUNTERS}
 * wood   int16 x, y, z, kind (0 1 2 = log on x y z, 3 = branch), arms, load
 * leaf   int16 x, y, z, distance
 * tip    int16 x, y, z, flags (bit 0 = on a 2×2 section; radius offset + 2 in bits 1 to 3)
 * </pre>
 */
final class VoxelBuffer {

    static final int MAGIC = 0x3156544E;
    static final String[] COUNTERS = {"stems_generated", "stems_removed_by_tip_budget", "stems_truncated",
        "stems_dropped_by_face_rule", "stems_dropped_with_parent", "leaf_candidates", "leaves_discarded_by_shade",
        "leaves_discarded_by_reach", "leaves_discarded_by_budget", "world_reads", "stem_cap_reached",
        "wide_trunk_blocked_at_base"};

    private VoxelBuffer() {
    }

    static byte[] encode(TreeResult r) {
        int size = 4 * (4 + COUNTERS.length) + r.woodCount() * 12 + r.leafCount() * 8 + r.tipCount() * 8;
        ByteBuffer b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(MAGIC).putInt(r.woodCount()).putInt(r.leafCount()).putInt(r.tipCount());
        b.putInt(r.stemsGenerated()).putInt(r.stemsRemovedByTipBudget()).putInt(r.stemsTruncated())
                .putInt(r.stemsDroppedByFaceRule()).putInt(r.stemsDroppedWithParent()).putInt(r.leafCandidates())
                .putInt(r.leavesDiscardedByShade()).putInt(r.leavesDiscardedByReach())
                .putInt(r.leavesDiscardedByBudget()).putInt(r.worldReads()).putInt(r.stemCapReached() ? 1 : 0)
                .putInt(r.wideTrunkBlockedAtBase() ? 1 : 0);
        for (int i = 0; i < r.woodCount(); i++) {
            b.putShort((short) r.woodX(i)).putShort((short) r.woodY(i)).putShort((short) r.woodZ(i))
                    .putShort((short) (r.isBranch(i) ? 3 : r.woodAxis(i))).putShort((short) r.woodArms(i))
                    .putShort((short) Math.min(r.woodLoad(i), Short.MAX_VALUE));
        }
        for (int i = 0; i < r.leafCount(); i++) {
            b.putShort((short) r.leafX(i)).putShort((short) r.leafY(i)).putShort((short) r.leafZ(i))
                    .putShort((short) r.leafDistance(i));
        }
        for (int i = 0; i < r.tipCount(); i++) {
            b.putShort((short) r.tipX(i)).putShort((short) r.tipY(i)).putShort((short) r.tipZ(i))
                    .putShort((short) ((r.tipOnWideTrunk(i) ? 1 : 0) | ((r.tipRadiusOffset(i) + 2) << 1)));
        }
        return b.array();
    }
}
