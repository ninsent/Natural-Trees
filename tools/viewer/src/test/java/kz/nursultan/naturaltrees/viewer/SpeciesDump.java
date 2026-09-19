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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import kz.nursultan.naturaltrees.treecore.FoliageParams;
import kz.nursultan.naturaltrees.treecore.PlacementLimits;
import kz.nursultan.naturaltrees.treecore.TreeDump;
import kz.nursultan.naturaltrees.treecore.TreeGenerator;
import kz.nursultan.naturaltrees.treecore.TreeResult;
import kz.nursultan.naturaltrees.treecore.TrunkParams;

/**
 * Test helper: draws a species file to PNG, at the heights vanilla would give it.
 * {@code ./gradlew :tools:viewer:dumpSpecies -Pspecies=oak -Pseeds=8}
 */
public final class SpeciesDump {

    private SpeciesDump() {
    }

    public static void main(String[] args) throws IOException {
        String species = args[0];
        int seeds = Integer.parseInt(args[1]);
        File dir = new File(args[2]);
        dir.mkdirs();

        Map<String, Object> file = PlacerJson.object(MiniJson.parse(new String(
                ViewerServer.resource("/species/" + species + ".json"), StandardCharsets.UTF_8)), species);
        Map<String, Object> trunkObject = PlacerJson.object(file.get("trunk_placer"), "trunk_placer");
        TrunkParams trunk = PlacerJson.trunk(trunkObject);
        FoliageParams foliage = PlacerJson.foliage(PlacerJson.object(file.get("foliage_placer"), "foliage_placer"));
        int base = PlacerJson.integer(trunkObject, "base_height", null);
        int randA = PlacerJson.integer(trunkObject, "height_rand_a", null);
        int randB = PlacerJson.integer(trunkObject, "height_rand_b", null);

        TreeGenerator generator = new TreeGenerator();
        List<BufferedImage> full = new ArrayList<>();
        List<BufferedImage> bare = new ArrayList<>();
        for (int seed = 1; seed <= seeds; seed++) {
            int height = base + (seed * 7) % (randA + 1) + (seed * 3) % (randB + 1);
            TreeResult r = generator.generate(trunk, foliage, seed, height, PlacementLimits.NONE,
                    (x, y, z) -> y >= 0, (x, y, z) -> y >= 0);
            full.add(TreeDump.isometric(r, true, 10));
            bare.add(TreeDump.isometric(r, false, 10));
            int logs = 0;
            for (int i = 0; i < r.woodCount(); i++) {
                logs += r.isBranch(i) ? 0 : 1;
            }
            System.out.printf("%s seed %d height %d: %d logs, %d branches, %d tips, %d leaves (shade -%d, reach -%d, budget -%d)%n",
                    species, seed, height, logs, r.woodCount() - logs, r.tipCount(), r.leafCount(),
                    r.leavesDiscardedByShade(), r.leavesDiscardedByReach(), r.leavesDiscardedByBudget());
        }
        File out = new File(dir, species + "-grid.png");
        ImageIO.write(TreeDump.grid(full, bare), "png", out);
        System.out.println("wrote " + out.getAbsolutePath());
    }
}
