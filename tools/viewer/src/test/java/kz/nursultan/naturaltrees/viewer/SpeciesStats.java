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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import kz.nursultan.naturaltrees.treecore.FoliageParams;
import kz.nursultan.naturaltrees.treecore.PlacementLimits;
import kz.nursultan.naturaltrees.treecore.TreeGenerator;
import kz.nursultan.naturaltrees.treecore.TreeResult;
import kz.nursultan.naturaltrees.treecore.TrunkParams;

/**
 * Test helper: block counts of every shipped species over 1,000 seeds and its whole height range, as a table.
 * {@code ./gradlew :tools:viewer:speciesStats}
 */
public final class SpeciesStats {

    private SpeciesStats() {
    }

    public static void main(String[] args) throws Exception {
        TreeGenerator g = new TreeGenerator();
        System.out.println("| Species | Height | Wood mean (max) | Tips mean (max) | Leaves mean (min–max) | Lowest leaf / height, min | Trees with leaves in the lowest fifth | Widest leaf |");
        System.out.println("|---|---|---|---|---|---|---|---|");
        for (String name : ViewerServer.SPECIES) {
            Map<String, Object> file = PlacerJson.object(MiniJson.parse(new String(
                    ViewerServer.resource("/species/" + name + ".json"), StandardCharsets.UTF_8)), name);
            Map<String, Object> trunkObject = PlacerJson.object(file.get("trunk_placer"), "trunk_placer");
            TrunkParams trunk = PlacerJson.trunk(trunkObject);
            FoliageParams foliage = PlacerJson.foliage(PlacerJson.object(file.get("foliage_placer"), "foliage_placer"));
            int base = PlacerJson.integer(trunkObject, "base_height", null);
            int range = PlacerJson.integer(trunkObject, "height_rand_a", null) + PlacerJson.integer(trunkObject, "height_rand_b", null);
            long wood = 0, tips = 0, leaves = 0;
            int maxWood = 0, maxTips = 0, maxLeaves = 0, minLeaves = Integer.MAX_VALUE, widest = 0;
            double lowest = 1.0;
            int lowTrees = 0;
            for (long seed = 0; seed < 1000; seed++) {
                int height = base + (int) (seed % (range + 1));
                TreeResult r = g.generate(trunk, foliage, seed, height, PlacementLimits.NONE, (x, y, z) -> y >= 0, (x, y, z) -> y >= 0);
                wood += r.woodCount();
                tips += r.tipCount();
                leaves += r.leafCount();
                maxWood = Math.max(maxWood, r.woodCount());
                maxTips = Math.max(maxTips, r.tipCount());
                maxLeaves = Math.max(maxLeaves, r.leafCount());
                minLeaves = Math.min(minLeaves, r.leafCount());
                int low = Integer.MAX_VALUE;
                for (int i = 0; i < r.leafCount(); i++) {
                    low = Math.min(low, r.leafY(i));
                    widest = Math.max(widest, Math.max(Math.abs(r.leafX(i)), Math.abs(r.leafZ(i))));
                }
                lowest = Math.min(lowest, (double) low / height);
                lowTrees += low < 0.2 * height ? 1 : 0;
            }
            System.out.printf("| %s | %d–%d | %d (%d) | %d (%d) | %d (%d–%d) | %.2f | %d | %d |%n", name, base, base + range,
                    wood / 1000, maxWood, tips / 1000, maxTips, leaves / 1000, minLeaves, maxLeaves, lowest, lowTrees, widest);
        }
    }
}
