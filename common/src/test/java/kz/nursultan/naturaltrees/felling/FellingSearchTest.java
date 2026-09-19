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
package kz.nursultan.naturaltrees.felling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 14.3 on hand-built block sets. Arm bits: down 1, up 2, north 4, south 8, west 16, east 32. */
class FellingSearchTest {

    private static final int DOWN = 1, UP = 2, WEST = 16, EAST = 32;

    /** A tiny world: positions to {kind, arms}. */
    private static final class World implements WoodView {
        private final Map<String, int[]> blocks = new HashMap<>();

        World log(int x, int y, int z) {
            blocks.put(x + "," + y + "," + z, new int[] {LOG, 0});
            return this;
        }

        World branch(int x, int y, int z, int arms) {
            blocks.put(x + "," + y + "," + z, new int[] {BRANCH, arms});
            return this;
        }

        World leaf(int x, int y, int z) {
            blocks.put(x + "," + y + "," + z, new int[] {NATURAL_LEAF, 0});
            return this;
        }

        World remove(int x, int y, int z) {
            blocks.remove(x + "," + y + "," + z);
            return this;
        }

        @Override
        public int kind(int x, int y, int z) {
            int[] b = blocks.get(x + "," + y + "," + z);
            return b == null ? OTHER : b[0];
        }

        @Override
        public int arms(int x, int y, int z) {
            int[] b = blocks.get(x + "," + y + "," + z);
            return b == null ? 0 : b[1];
        }
    }

    /** A trunk at x = 0 from y = 0 to 5, a limb of three branches going east from y = 4, leaves on the limb's tip. */
    private static World tree() {
        World w = new World();
        for (int y = 0; y <= 5; y++) {
            w.log(0, y, 0);
        }
        w.branch(1, 4, 0, WEST | EAST).branch(2, 4, 0, WEST | EAST).branch(3, 4, 0, WEST);
        w.leaf(3, 5, 0).leaf(4, 4, 0).leaf(3, 4, 1).leaf(0, 6, 0);
        return w;
    }

    private static boolean contains(FellingSearch.Result r, int x, int y, int z) {
        return r.wood().contains(new FellingSearch.Found(x, y, z));
    }

    @Test
    void cuttingTheBaseFellsTheWholeTree() {
        World w = tree().remove(0, 1, 0);
        FellingSearch.Result r = FellingSearch.search(w, 0, 1, 0, WoodView.LOG, 0, 512);
        assertEquals(4 + 3, r.wood().size(), "four logs above the cut and the three branches");
        assertFalse(contains(r, 0, 0, 0), "nothing below the broken block");
        assertEquals(4, r.leaves());
        assertFalse(r.capped());
        assertEquals(new FellingSearch.Found(0, 2, 0), r.wood().get(0), "nearest to the cut first");
    }

    @Test
    void aBranchBesideALogWithoutAnArmTowardItIsNotTaken() {
        World w = tree().remove(0, 1, 0);
        // Another tree's twig passes the trunk: it touches the log but has no arm toward it.
        w.branch(-1, 3, 0, UP | DOWN).branch(-1, 4, 0, DOWN);
        FellingSearch.Result r = FellingSearch.search(w, 0, 1, 0, WoodView.LOG, 0, 512);
        assertFalse(contains(r, -1, 3, 0));
        assertFalse(contains(r, -1, 4, 0));
        assertEquals(7, r.wood().size());
    }

    @Test
    void crownsThatTouchAreNotJoinedButRealArmsAre() {
        World w = tree().remove(0, 1, 0);
        // A neighbour's limb ends right beside ours, tip to tip, without arms between them.
        w.branch(4, 4, 0, EAST).branch(5, 4, 0, WEST | EAST).log(6, 4, 0).remove(4, 4, 0).branch(4, 4, 0, EAST);
        FellingSearch.Result apart = FellingSearch.search(w, 0, 1, 0, WoodView.LOG, 0, 512);
        assertFalse(contains(apart, 4, 4, 0), "merely adjacent branches are two trees");
        assertFalse(contains(apart, 6, 4, 0));

        // Now the two tips really are joined by arms: the search follows them, as the spec says.
        w.branch(3, 4, 0, WEST | EAST).branch(4, 4, 0, WEST | EAST);
        FellingSearch.Result joined = FellingSearch.search(w, 0, 1, 0, WoodView.LOG, 0, 512);
        assertTrue(contains(joined, 4, 4, 0));
        assertTrue(contains(joined, 6, 4, 0), "an arm that points at a log joins it");
    }

    @Test
    void cuttingALimbFollowsItsArmsOnly() {
        World w = tree().remove(2, 4, 0);
        FellingSearch.Result r = FellingSearch.search(w, 2, 4, 0, WoodView.BRANCH, WEST | EAST, 512);
        assertTrue(contains(r, 3, 4, 0), "the tip beyond the cut");
        assertTrue(contains(r, 1, 4, 0), "back along the arm toward the trunk");
        assertTrue(contains(r, 0, 5, 0), "and into the trunk at and above the cut's height");
        assertFalse(contains(r, 0, 3, 0), "never below the broken block");
    }

    @Test
    void logsJoinDiagonallyAsFancyOakLimbsDo() {
        World w = new World().log(0, 0, 0).log(0, 1, 0).log(1, 2, 1).log(2, 3, 2);
        w.remove(0, 0, 0);
        FellingSearch.Result r = FellingSearch.search(w, 0, 0, 0, WoodView.LOG, 0, 512);
        assertEquals(3, r.wood().size());
    }

    @Test
    void theCapCutsTheSearchOff() {
        World w = new World();
        for (int y = 0; y < 40; y++) {
            w.log(0, y, 0);
        }
        w.remove(0, 0, 0);
        FellingSearch.Result r = FellingSearch.search(w, 0, 0, 0, WoodView.LOG, 0, 10);
        assertEquals(10, r.wood().size());
        assertTrue(r.capped());
        assertEquals(new FellingSearch.Found(0, 10, 0), r.wood().get(9), "the nearest ten");
    }

    @Test
    void aBuildHasNoNaturalLeaves() {
        // A log cabin wall: plenty of logs, and leaves a player placed are persistent, which the view reports as OTHER.
        World w = new World();
        for (int x = 0; x < 6; x++) {
            for (int y = 0; y < 4; y++) {
                w.log(x, y, 0);
            }
        }
        w.remove(0, 0, 0);
        FellingSearch.Result r = FellingSearch.search(w, 0, 0, 0, WoodView.LOG, 0, 512);
        assertEquals(23, r.wood().size());
        assertEquals(0, r.leaves(), "min_leaves then refuses the felling");
    }
}
