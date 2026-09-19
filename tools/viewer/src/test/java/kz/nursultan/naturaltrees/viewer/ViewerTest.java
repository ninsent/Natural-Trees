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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import kz.nursultan.naturaltrees.treecore.FoliageParams;
import kz.nursultan.naturaltrees.treecore.PlacementLimits;
import kz.nursultan.naturaltrees.treecore.TreeGenerator;
import kz.nursultan.naturaltrees.treecore.TreeResult;
import kz.nursultan.naturaltrees.treecore.TrunkParams;
import org.junit.jupiter.api.Test;

class ViewerTest {

    private static Map<String, Object> species(String name) throws Exception {
        return PlacerJson.object(MiniJson.parse(new String(ViewerServer.resource("/species/" + name + ".json"),
                StandardCharsets.UTF_8)), name);
    }

    private static String request(String name, int height, long seed) throws Exception {
        String file = new String(ViewerServer.resource("/species/" + name + ".json"), StandardCharsets.UTF_8).trim();
        return file.substring(0, file.length() - 1) + ", \"height\": " + height + ", \"seed\": \"" + seed + "\"}";
    }

    @Test
    void miniJsonReadsWhatSpeciesFilesUse() {
        Object v = MiniJson.parse(" {\"a\": [1, -2.5e1, true, false, null], \"b\": {\"c\": \"x\\n\\u0041\"}} ");
        Map<String, Object> o = PlacerJson.object(v, "test");
        assertEquals(List.of(1.0, -25.0, true, false), ((List<?>) o.get("a")).subList(0, 4));
        assertEquals(null, ((List<?>) o.get("a")).get(4));
        assertEquals("x\nA", PlacerJson.object(o.get("b"), "b").get("c"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{\"a\": }"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("[1, 2] x"));
    }

    @Test
    void defaultsOfTheSpecApplyAndRequiredFieldsAreNamed() {
        String minimal = "{\"shape\": \"conical\", \"base_size\": 0.2, \"max_radius\": 5, \"max_tips\": 8,"
                + " \"trunk\": {\"curve_res\": 3},"
                + " \"levels\": [{\"branches\": 4, \"length\": 0.5, \"down_angle\": 60, \"rotate\": 140, \"curve_res\": 2}]}";
        TrunkParams p = PlacerJson.trunk(PlacerJson.object(MiniJson.parse(minimal), "trunk_placer"));
        assertEquals(0.25, p.twigRadius());
        assertEquals(2.0, p.pipeExponent());
        assertEquals(1, p.trunkWidthMin());
        assertEquals(2, p.trunkWidthMax());
        assertEquals(3, p.foliageMargin());
        assertEquals(0, p.levels().get(0).tipRadiusOffset());
        assertEquals(FoliageParams.defaults(300),
                PlacerJson.foliage(PlacerJson.object(MiniJson.parse("{\"max_leaves\": 300}"), "foliage_placer")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> PlacerJson.trunk(
                PlacerJson.object(MiniJson.parse(minimal.replace("\"max_tips\": 8,", "")), "trunk_placer")));
        assertTrue(e.getMessage().startsWith("max_tips:"), e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> PlacerJson.trunk(
                PlacerJson.object(MiniJson.parse(minimal.replace("\"max_tips\": 8", "\"max_tips\": 8.5")), "trunk_placer")));
        assertTrue(e.getMessage().startsWith("max_tips:"), e.getMessage());
    }

    @Test
    void theBufferHoldsExactlyWhatTheGeneratorReturns() throws Exception {
        ViewerServer viewer = new ViewerServer(0);
        ByteBuffer b = ByteBuffer.wrap(viewer.generate(request("fancy_oak", 12, -42L))).order(ByteOrder.LITTLE_ENDIAN);

        Map<String, Object> file = species("fancy_oak");
        TreeResult r = new TreeGenerator().generate(PlacerJson.trunk(PlacerJson.object(file.get("trunk_placer"), "t")),
                PlacerJson.foliage(PlacerJson.object(file.get("foliage_placer"), "f")), -42L, 12, PlacementLimits.NONE,
                (x, y, z) -> y >= 0, (x, y, z) -> y >= 0);

        assertEquals(VoxelBuffer.MAGIC, b.getInt());
        assertEquals(r.woodCount(), b.getInt());
        assertEquals(r.leafCount(), b.getInt());
        assertEquals(r.tipCount(), b.getInt());
        assertEquals(r.stemsGenerated(), b.getInt());
        b.position(4 * (4 + VoxelBuffer.COUNTERS.length));
        assertTrue(r.woodCount() > 20 && r.leafCount() > 100);
        for (int i = 0; i < r.woodCount(); i++) {
            assertEquals(r.woodX(i), b.getShort());
            assertEquals(r.woodY(i), b.getShort());
            assertEquals(r.woodZ(i), b.getShort());
            assertEquals(r.isBranch(i) ? 3 : r.woodAxis(i), b.getShort());
            assertEquals(r.woodArms(i), b.getShort());
            assertEquals(r.woodLoad(i), b.getShort());
        }
        for (int i = 0; i < r.leafCount(); i++) {
            assertEquals(r.leafX(i), b.getShort());
            assertEquals(r.leafY(i), b.getShort());
            assertEquals(r.leafZ(i), b.getShort());
            assertEquals(r.leafDistance(i), b.getShort());
        }
        for (int i = 0; i < r.tipCount(); i++) {
            assertEquals(r.tipX(i), b.getShort());
            assertEquals(r.tipY(i), b.getShort());
            assertEquals(r.tipZ(i), b.getShort());
            assertEquals((r.tipOnWideTrunk(i) ? 1 : 0) | ((r.tipRadiusOffset(i) + 2) << 1), b.getShort());
        }
        assertEquals(0, b.remaining());
    }

    @Test
    void httpEndpoints() throws Exception {
        ViewerServer viewer = new ViewerServer(0);
        viewer.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + viewer.port();

            HttpResponse<String> page = client.send(HttpRequest.newBuilder(URI.create(base + "/")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("<html"));

            HttpResponse<String> index = client.send(HttpRequest.newBuilder(URI.create(base + "/species/index.json")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(ViewerServer.SPECIES.length, ((List<?>) MiniJson.parse(index.body())).size());

            HttpResponse<byte[]> tree = client.send(HttpRequest.newBuilder(URI.create(base + "/generate"))
                    .POST(HttpRequest.BodyPublishers.ofString(request("oak", 9, 7))).build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, tree.statusCode());
            assertEquals(VoxelBuffer.MAGIC, ByteBuffer.wrap(tree.body()).order(ByteOrder.LITTLE_ENDIAN).getInt());

            HttpResponse<String> bad = client.send(HttpRequest.newBuilder(URI.create(base + "/generate"))
                    .POST(HttpRequest.BodyPublishers.ofString(request("oak", 9, 7).replace("\"base_size\": 0.4", "\"base_size\": 1.2")))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, bad.statusCode());
            assertTrue(bad.body().startsWith("base_size:"), bad.body());

            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create(base + "/species/../secret")).build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode());
        } finally {
            viewer.stop();
        }
    }

    /** Spec section 15: block counts of every shipped species stay inside the budget of its size class. */
    @Test
    void speciesStayInsideTheirSizeClassBudgets() throws Exception {
        // name, wood voxels, tips, leaves, max_radius
        Object[][] budgets = {{"oak", 50, 8, 250, 5}, {"birch", 50, 8, 250, 5}, {"fancy_oak", 140, 14, 600, 8},
            {"tall_birch", 140, 14, 600, 8}, {"test_28_tips", 400, 28, 1200, 12}};
        TreeGenerator g = new TreeGenerator();
        for (Object[] budget : budgets) {
            String name = (String) budget[0];
            Map<String, Object> file = species(name);
            Map<String, Object> trunkObject = PlacerJson.object(file.get("trunk_placer"), "trunk_placer");
            TrunkParams trunk = PlacerJson.trunk(trunkObject);
            FoliageParams foliage = PlacerJson.foliage(PlacerJson.object(file.get("foliage_placer"), "foliage_placer"));
            int base = PlacerJson.integer(trunkObject, "base_height", null);
            int range = PlacerJson.integer(trunkObject, "height_rand_a", null) + PlacerJson.integer(trunkObject, "height_rand_b", null);
            assertTrue(trunk.maxRadius() <= (int) budget[4], name + " max_radius");
            assertTrue(trunk.maxTips() <= (int) budget[2], name + " max_tips");
            assertTrue(foliage.maxLeaves() <= (int) budget[3], name + " max_leaves");
            assertTrue(trunk.foliageMargin() >= foliage.requiredMargin(), name + " foliage_margin is lower than its foliage needs");
            int wide = 0;
            for (long seed = 0; seed < 1000; seed++) {
                int height = base + (int) (seed % (range + 1));
                TreeResult r = g.generate(trunk, foliage, seed, height, PlacementLimits.NONE, (x, y, z) -> y >= 0, (x, y, z) -> y >= 0);
                assertTrue(r.woodCount() <= (int) budget[1], name + " seed " + seed + ": " + r.woodCount() + " wood voxels");
                assertTrue(r.tipCount() <= (int) budget[2], name + " tips");
                assertTrue(r.leafCount() <= (int) budget[3], name + " leaves");
                assertTrue(r.leafCount() > 30, name + " seed " + seed + ": a tree with " + r.leafCount() + " leaves");
                for (int i = 0; i < r.tipCount(); i++) {
                    wide += r.tipOnWideTrunk(i) ? 1 : 0;
                }
                if (name.equals("test_28_tips")) {
                    boolean baseWide = false;
                    for (int i = 0; i < r.woodCount(); i++) {
                        baseWide |= r.woodX(i) == 1 && r.woodY(i) == 0 && r.woodZ(i) == 1;
                    }
                    assertTrue(baseWide, "seed " + seed + ": the 28-tip species has a 2×2 lower trunk");
                }
            }
            assertEquals(0, wide, name + ": the trunk narrows to 1×1 before its top");
        }
    }
}
