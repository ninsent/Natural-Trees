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
package kz.nursultan.naturaltrees.assetgen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Generates the built-in world generation datapack (spec section 11). Every file is vanilla's file of the same
 * id with as little changed as the spec allows: a configured feature gets the species' {@code trunk_placer} and
 * {@code foliage_placer} and nothing else (spec 10, 11.2); a placed feature gets a new {@code count} and nothing
 * else (spec 11.3). Nothing is written by hand, so the pack cannot drift from vanilla or from the viewer's species.
 */
final class WorldgenPackGen {

    static final String PACK = "resourcepacks/naturaltrees_worldgen/";

    /** Spec 11.2: the vanilla keys of each family, and the species file that replaces their placers. */
    static final Map<String, List<String>> FAMILIES = families();

    /**
     * Spec 11.3 and questions.md Q19: the placed features made purely of oak and birch whose count changes.
     * {@code trees_plains} and {@code trees_meadow} are as sparse as they should be and are left to vanilla.
     */
    static final List<String> PLACED = List.of("trees_birch_and_oak", "trees_birch", "birch_tall",
            "trees_flower_forest", "trees_badlands");

    /** Trees per chunk as a fraction of vanilla's: about 3 to 4 where vanilla places about 10 (spec 11.3). */
    static final double DENSITY = 1.0 / 3.0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private WorldgenPackGen() {
    }

    private static Map<String, List<String>> families() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("oak", List.of("oak", "oak_bees_0002", "oak_bees_002", "oak_bees_005"));
        map.put("fancy_oak", List.of("fancy_oak", "fancy_oak_bees", "fancy_oak_bees_0002", "fancy_oak_bees_002", "fancy_oak_bees_005"));
        map.put("birch", List.of("birch", "birch_bees_0002", "birch_bees_002", "birch_bees_005"));
        map.put("tall_birch", List.of("super_birch_bees", "super_birch_bees_0002"));
        return map;
    }

    static void run(Path resources, Path vanillaJar, Path speciesDir, double density) throws IOException {
        try (ZipFile vanilla = new ZipFile(vanillaJar.toFile())) {
            for (Map.Entry<String, List<String>> family : FAMILIES.entrySet()) {
                JsonObject species = JsonParser.parseString(
                        Files.readString(speciesDir.resolve(family.getKey() + ".json"))).getAsJsonObject();
                for (String key : family.getValue()) {
                    JsonObject feature = read(vanilla, "data/minecraft/worldgen/configured_feature/" + key + ".json");
                    if (!"minecraft:tree".equals(feature.get("type").getAsString())) {
                        throw new IllegalStateException(key + " is not a minecraft:tree in this version");
                    }
                    JsonObject config = feature.getAsJsonObject("config");
                    config.add("trunk_placer", species.get("trunk_placer"));
                    config.add("foliage_placer", species.get("foliage_placer"));
                    write(resources, PACK + "data/minecraft/worldgen/configured_feature/" + key + ".json", feature);
                }
            }
            for (String key : PLACED) {
                JsonObject placed = read(vanilla, "data/minecraft/worldgen/placed_feature/" + key + ".json");
                if (!scaleCount(placed, density)) {
                    throw new IllegalStateException(key + " has no weighted count in this version");
                }
                write(resources, PACK + "data/minecraft/worldgen/placed_feature/" + key + ".json", placed);
            }
        }
        JsonObject meta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Natural Trees: oak and birch world generation");
        pack.addProperty("pack_format", 48);
        meta.add("pack", pack);
        write(resources, PACK + "pack.mcmeta", meta);
    }

    /**
     * A small datapack for the measurements of spec section 15: only the placed features, at another density.
     * Put above the built-in pack it overrides that pack's counts and nothing else.
     */
    static void runDensityPack(Path folder, Path vanillaJar, String label, double density) throws IOException {
        try (ZipFile vanilla = new ZipFile(vanillaJar.toFile())) {
            for (String key : PLACED) {
                JsonObject placed = read(vanilla, "data/minecraft/worldgen/placed_feature/" + key + ".json");
                scaleCount(placed, density);
                write(folder, "data/minecraft/worldgen/placed_feature/" + key + ".json", placed);
            }
        }
        JsonObject meta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Natural Trees measurement pack: tree density " + label);
        pack.addProperty("pack_format", 48);
        meta.add("pack", pack);
        write(folder, "pack.mcmeta", meta);
    }

    /**
     * Scales the weighted {@code minecraft:count} of a placed feature. Vanilla's counts are "n, sometimes n + 1";
     * the base is scaled and rounded, and the extra is kept, so the shape of the distribution stays.
     */
    static boolean scaleCount(JsonObject placed, double density) {
        boolean changed = false;
        for (JsonElement element : placed.getAsJsonArray("placement")) {
            JsonObject modifier = element.getAsJsonObject();
            if (!"minecraft:count".equals(modifier.get("type").getAsString()) || !modifier.get("count").isJsonObject()) {
                continue;
            }
            JsonArray distribution = modifier.getAsJsonObject("count").getAsJsonArray("distribution");
            int base = Integer.MAX_VALUE;
            for (JsonElement entry : distribution) {
                base = Math.min(base, entry.getAsJsonObject().get("data").getAsInt());
            }
            int scaled = (int) Math.round(base * density);
            for (JsonElement entry : distribution) {
                JsonObject o = entry.getAsJsonObject();
                o.addProperty("data", scaled + o.get("data").getAsInt() - base);
            }
            changed = true;
        }
        return changed;
    }

    private static JsonObject read(ZipFile jar, String name) throws IOException {
        ZipEntry entry = jar.getEntry(name);
        if (entry == null) {
            throw new IllegalStateException("vanilla has no " + name);
        }
        try (InputStream in = jar.getInputStream(entry)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void write(Path resources, String path, JsonObject json) throws IOException {
        Path file = resources.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
    }
}
