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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

/**
 * Spec 10, 11.2, 11.3: every file of the built-in pack is vanilla's file of the same id, changed only where the
 * spec allows. A failure here means the pack was edited by hand or vanilla's data moved; run
 * {@code ./gradlew :tools:assetgen:run} and look at the difference.
 */
class WorldgenPackTest {

    private static final Path PACK = Path.of("src/main/resources/resourcepacks/naturaltrees_worldgen");
    private static final Path SPECIES = Path.of("../tools/viewer/src/main/resources/species");

    private static ZipFile vanilla() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("build/moddev/artifacts"))) {
            Path jar = files.filter(p -> p.getFileName().toString().contains("client-extra")).findFirst().orElseThrow();
            return new ZipFile(jar.toFile());
        }
    }

    private static JsonObject vanilla(ZipFile jar, String name) throws IOException {
        try (var in = jar.getInputStream(jar.getEntry(name))) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static List<Path> files(String folder) throws IOException {
        try (Stream<Path> files = Files.list(PACK.resolve("data/minecraft/worldgen").resolve(folder))) {
            return files.sorted().toList();
        }
    }

    /** The species file behind a vanilla key: the families of spec 11.2 and the Phase 3 woods. */
    private static String speciesOf(String key) {
        if (key.startsWith("super_birch")) {
            return "tall_birch";
        }
        if (key.startsWith("fancy_oak")) {
            return "fancy_oak";
        }
        if (key.startsWith("birch")) {
            return "birch";
        }
        if (key.startsWith("oak")) {
            return "oak";
        }
        if (key.startsWith("cherry")) {
            return "cherry";
        }
        if (key.startsWith("jungle_tree")) {
            return "jungle";
        }
        if (key.equals("azalea_tree")) {
            return "azalea";
        }
        return key.equals("mega_jungle_tree") ? "mega_jungle" : key;
    }

    @Test
    void configuredFeaturesAreVanillaExceptTheTwoPlacers() throws IOException {
        List<Path> files = files("configured_feature");
        assertEquals(30, files.size(), "the 15 keys of spec 11.2, the 14 of Phase 3 and azalea_tree (questions.md Q35)");
        try (ZipFile jar = vanilla()) {
            for (Path file : files) {
                String key = file.getFileName().toString().replace(".json", "");
                JsonObject ours = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                JsonObject theirs = vanilla(jar, "data/minecraft/worldgen/configured_feature/" + key + ".json");
                JsonObject species = JsonParser.parseString(Files.readString(SPECIES.resolve(speciesOf(key) + ".json"))).getAsJsonObject();

                JsonObject config = ours.getAsJsonObject("config");
                assertEquals(species.get("trunk_placer"), config.get("trunk_placer"), key + ": trunk placer is the species file's");
                assertEquals(species.get("foliage_placer"), config.get("foliage_placer"), key + ": foliage placer is the species file's");
                ParamCodecs.TRUNK.decoder().parse(JsonOps.INSTANCE, config.get("trunk_placer")).getOrThrow();
                ParamCodecs.FOLIAGE.decoder().parse(JsonOps.INSTANCE, config.get("foliage_placer")).getOrThrow();

                config.add("trunk_placer", theirs.getAsJsonObject("config").get("trunk_placer"));
                config.add("foliage_placer", theirs.getAsJsonObject("config").get("foliage_placer"));
                assertEquals(theirs, ours, key + ": everything else is vanilla's");
            }
        }
    }

    @Test
    void placedFeaturesAreVanillaExceptTheCount() throws IOException {
        List<Path> files = files("placed_feature");
        assertEquals(19, files.size());
        try (ZipFile jar = vanilla()) {
            for (Path file : files) {
                String key = file.getFileName().toString().replace(".json", "");
                JsonObject ours = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                JsonObject theirs = vanilla(jar, "data/minecraft/worldgen/placed_feature/" + key + ".json");
                JsonArray ourPlacement = ours.getAsJsonArray("placement");
                JsonArray theirPlacement = theirs.getAsJsonArray("placement");
                assertEquals(theirPlacement.size(), ourPlacement.size(), key);
                boolean fewer = false;
                for (int i = 0; i < ourPlacement.size(); i++) {
                    JsonObject mine = ourPlacement.get(i).getAsJsonObject();
                    JsonObject vanillas = theirPlacement.get(i).getAsJsonObject();
                    if (!"minecraft:count".equals(mine.get("type").getAsString())) {
                        continue;
                    }
                    if (mine.get("count").isJsonPrimitive()) {
                        int mineCount = mine.get("count").getAsInt();
                        assertTrue(mineCount >= 1 && mineCount < vanillas.get("count").getAsInt(), key + ": fewer trees than vanilla, never none");
                        fewer = true;
                    } else if (key.equals("trees_savanna") || key.equals("trees_windswept_savanna")) {
                        // questions.md Q34: vanilla's 1 tree per chunk cannot be thinned by a fraction, so the savannas
                        // have their own weighted list. The rule is the same: fewer trees than vanilla, never none.
                        assertTrue(mean(mine) > 0.0 && mean(mine) < mean(vanillas), key + ": fewer trees than vanilla, never none");
                        fewer = true;
                    } else {
                        JsonArray a = mine.getAsJsonObject("count").getAsJsonArray("distribution");
                        JsonArray b = vanillas.getAsJsonObject("count").getAsJsonArray("distribution");
                        for (int k = 0; k < a.size(); k++) {
                            int mineData = a.get(k).getAsJsonObject().get("data").getAsInt();
                            int vanillaData = b.get(k).getAsJsonObject().get("data").getAsInt();
                            assertTrue(mineData >= 1 && mineData < vanillaData, key + ": fewer trees than vanilla, never none");
                            assertEquals(b.get(k).getAsJsonObject().get("weight"), a.get(k).getAsJsonObject().get("weight"), key);
                            fewer = true;
                        }
                    }
                    mine.add("count", vanillas.get("count"));
                }
                assertTrue(fewer, key + ": the count was changed");
                assertEquals((JsonElement) theirs, ours, key + ": everything else is vanilla's");
            }
        }
    }

    /** Trees per chunk of a weighted {@code minecraft:count}, on average. */
    private static double mean(JsonObject modifier) {
        double sum = 0.0, weights = 0.0;
        for (JsonElement entry : modifier.getAsJsonObject("count").getAsJsonArray("distribution")) {
            JsonObject o = entry.getAsJsonObject();
            sum += o.get("data").getAsDouble() * o.get("weight").getAsDouble();
            weights += o.get("weight").getAsDouble();
        }
        return sum / weights;
    }
}
