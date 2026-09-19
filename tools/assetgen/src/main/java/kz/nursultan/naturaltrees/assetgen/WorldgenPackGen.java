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
            "trees_flower_forest", "trees_badlands",
            // Phase 3 (questions.md Q23). Left to vanilla because a fraction of their count is no fewer trees:
            // trees_snowy, trees_windswept_hills, trees_water, trees_plains (0-1), trees_meadow.
            // trees_savanna (vanilla 1-2) is thinned through SPARSE below (questions.md Q34).
            "trees_savanna",
            "trees_taiga", "trees_grove", "trees_old_growth_pine_taiga", "trees_old_growth_spruce_taiga",
            "trees_jungle", "trees_sparse_jungle", "bamboo_vegetation", "trees_windswept_savanna",
            "trees_windswept_forest", "trees_swamp", "trees_mangrove", "trees_cherry", "dark_forest_vegetation");

    /**
     * Trees per chunk as a fraction of vanilla's: 2 to 3 where vanilla places about 10 (spec 11.3). It is the midpoint
     * of a third (the species of Phase 3, vanilla-sized) and a fifth (tuning round 4, crowns 12 to 25 blocks wide),
     * as the species themselves are since round 5 (phase-0-results.md).
     */
    static final double DENSITY = 4.0 / 15.0;

    /**
     * questions.md Q34: the oak, birch and mixed forests are a little denser than the rest, 4 to 5 trees per chunk where
     * vanilla places 10 to 11, because their species are the smallest and a forest should close its canopy.
     */
    static final Map<String, Double> DENSITY_BY_FEATURE = Map.of(
            "trees_birch_and_oak", 0.4, "trees_birch", 0.4, "birch_tall", 0.4);

    /**
     * questions.md Q34: the savannas. Vanilla places 1 tree per chunk, sometimes 2 (windswept: 2, sometimes 3), which
     * no fraction can thin. Their count becomes this weighted list of {trees, weight}: 0.7 trees per chunk on average,
     * because an acacia's canopy is now up to 25 blocks wide.
     */
    static final List<String> SPARSE = List.of("trees_savanna", "trees_windswept_savanna");
    static final int[][] SPARSE_COUNT = {{0, 4}, {1, 5}, {2, 1}};

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private WorldgenPackGen() {
    }

    private static Map<String, List<String>> families() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("oak", List.of("oak", "oak_bees_0002", "oak_bees_002", "oak_bees_005"));
        map.put("fancy_oak", List.of("fancy_oak", "fancy_oak_bees", "fancy_oak_bees_0002", "fancy_oak_bees_002", "fancy_oak_bees_005"));
        map.put("birch", List.of("birch", "birch_bees_0002", "birch_bees_002", "birch_bees_005"));
        map.put("tall_birch", List.of("super_birch_bees", "super_birch_bees_0002"));
        // Phase 3. jungle_bush and azalea_tree are not in the spec's list and stay vanilla (questions.md Q24).
        map.put("spruce", List.of("spruce"));
        map.put("pine", List.of("pine"));
        map.put("mega_spruce", List.of("mega_spruce"));
        map.put("mega_pine", List.of("mega_pine"));
        map.put("acacia", List.of("acacia"));
        map.put("cherry", List.of("cherry", "cherry_bees_005"));
        map.put("jungle", List.of("jungle_tree", "jungle_tree_no_vine"));
        map.put("mega_jungle", List.of("mega_jungle_tree"));
        map.put("swamp_oak", List.of("swamp_oak"));
        map.put("mangrove", List.of("mangrove"));
        map.put("tall_mangrove", List.of("tall_mangrove"));
        map.put("dark_oak", List.of("dark_oak"));
        // questions.md Q35: not in spec 11.2; added on the human's word. Oak logs, azalea leaves, grown by root_system.
        map.put("azalea", List.of("azalea_tree"));
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
                if (!setCount(key, placed, density, 1.0)) {
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
                if (SPARSE.contains(key)) {
                    // The savannas' count does not scale, so a copy here would only repeat the built-in pack's.
                    continue;
                }
                JsonObject placed = read(vanilla, "data/minecraft/worldgen/placed_feature/" + key + ".json");
                setCount(key, placed, WorldgenPackGen.DENSITY, density / WorldgenPackGen.DENSITY);
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
     * The count of one placed feature: the general density, or the feature's own (DENSITY_BY_FEATURE), times the
     * multiplier of a measurement pack; the savannas get SPARSE_COUNT and are left out of the measurement packs.
     */
    static boolean setCount(String key, JsonObject placed, double density, double multiplier) {
        if (!SPARSE.contains(key)) {
            return scaleCount(placed, DENSITY_BY_FEATURE.getOrDefault(key, density) * multiplier);
        }
        boolean changed = false;
        for (JsonElement element : placed.getAsJsonArray("placement")) {
            JsonObject modifier = element.getAsJsonObject();
            if (!"minecraft:count".equals(modifier.get("type").getAsString()) || modifier.get("count").isJsonPrimitive()) {
                continue;
            }
            JsonArray distribution = new JsonArray();
            for (int[] entry : SPARSE_COUNT) {
                JsonObject o = new JsonObject();
                o.addProperty("data", entry[0]);
                o.addProperty("weight", entry[1]);
                distribution.add(o);
            }
            modifier.getAsJsonObject("count").add("distribution", distribution);
            changed = true;
        }
        return changed;
    }

    /**
     * Scales the weighted {@code minecraft:count} of a placed feature. Vanilla's counts are "n, sometimes n + 1";
     * the base is scaled and rounded, and the extra is kept, so the shape of the distribution stays.
     */
    static boolean scaleCount(JsonObject placed, double density) {
        boolean changed = false;
        for (JsonElement element : placed.getAsJsonArray("placement")) {
            JsonObject modifier = element.getAsJsonObject();
            if (!"minecraft:count".equals(modifier.get("type").getAsString())) {
                continue;
            }
            if (modifier.get("count").isJsonPrimitive()) {
                modifier.addProperty("count", scaled(modifier.get("count").getAsInt(), density));
                changed = true;
                continue;
            }
            JsonArray distribution = modifier.getAsJsonObject("count").getAsJsonArray("distribution");
            int base = Integer.MAX_VALUE;
            for (JsonElement entry : distribution) {
                base = Math.min(base, entry.getAsJsonObject().get("data").getAsInt());
            }
            int scaledBase = scaled(base, density);
            for (JsonElement entry : distribution) {
                JsonObject o = entry.getAsJsonObject();
                o.addProperty("data", scaledBase + o.get("data").getAsInt() - base);
            }
            changed = true;
        }
        return changed;
    }

    /** A count scaled by the density: never below one tree where vanilla places any, and never more than vanilla. */
    private static int scaled(int count, double density) {
        return count == 0 ? 0 : Math.min(count, Math.max(1, (int) Math.round(count * density)));
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
