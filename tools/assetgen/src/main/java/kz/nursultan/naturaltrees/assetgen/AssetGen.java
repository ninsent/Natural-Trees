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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Generates the per-wood resource files of the sixteen branch blocks: blockstates, child models that bind the
 * log textures, item models, loot tables, recipes, tags and English names. The four shared models
 * ({@code branch_core_side}, {@code branch_core_end}, {@code branch_arm}, {@code branch_inventory}) are written
 * by hand and not touched.
 */
public final class AssetGen {

    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry"};

    private final Path root;

    private AssetGen(Path root) {
        this.root = root;
    }

    public static void main(String[] args) throws IOException {
        new AssetGen(Path.of(args[0])).run();
    }

    private void write(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static String title(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("_")) {
            out.append(out.length() == 0 ? "" : " ").append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }

    private static String list(List<String> values) {
        StringJoiner joiner = new StringJoiner(",\n    ", "[\n    ", "\n  ]");
        values.forEach(v -> joiner.add("\"" + v + "\""));
        return joiner.toString();
    }

    private static String tag(List<String> values) {
        return "{\n  \"replace\": false,\n  \"values\": " + list(values) + "\n}\n";
    }

    private static final String[][] TURNS = {
        {"north", "south", ""}, {"east", "west", ", \"y\": 90"}, {"south", "north", ", \"y\": 180"},
        {"west", "east", ", \"y\": 270"}, {"up", "down", ", \"x\": 270"}, {"down", "up", ", \"x\": 90"}};

    /**
     * The arm and the two core-face models point north; the blockstate turns them into the other directions.
     * A core face is drawn only where no arm covers it. It shows the log's end grain when the branch is a stub
     * whose single arm is on the opposite side, so that a cut or a twig's tip shows rings as a log's end does;
     * everywhere else it shows bark.
     */
    private static String blockstate(String id) {
        StringJoiner parts = new StringJoiner(",\n", "{\n  \"multipart\": [\n", "\n  ]\n}\n");
        for (String[] turn : TURNS) {
            String direction = turn[0];
            String opposite = turn[1];
            String rotation = turn[2];
            parts.add("    { \"when\": { \"" + direction + "\": \"true\" }, \"apply\": { \"model\": \"naturaltrees:block/"
                    + id + "_arm\"" + rotation + " } }");

            StringJoiner stub = new StringJoiner(", ", "{ ", " }");
            StringJoiner notStub = new StringJoiner(", ", "[ ", " ]");
            notStub.add("{ \"" + opposite + "\": \"false\" }");
            for (String[] other : TURNS) {
                String name = other[0];
                if (name.equals(opposite)) {
                    stub.add("\"" + name + "\": \"true\"");
                } else {
                    stub.add("\"" + name + "\": \"false\"");
                    if (!name.equals(direction)) {
                        notStub.add("{ \"" + name + "\": \"true\" }");
                    }
                }
            }
            parts.add("    { \"when\": " + stub + ", \"apply\": { \"model\": \"naturaltrees:block/" + id + "_core_end\""
                    + rotation + " } }");
            parts.add("    { \"when\": { \"AND\": [ { \"" + direction + "\": \"false\" }, { \"OR\": " + notStub
                    + " } ] }, \"apply\": { \"model\": \"naturaltrees:block/" + id + "_core_side\"" + rotation + " } }");
        }
        return parts.toString();
    }

    private void run() throws IOException {
        List<String> allBranches = new ArrayList<>();
        List<String> plainBranches = new ArrayList<>();
        StringJoiner lang = new StringJoiner(",\n", "{\n", "\n}\n");

        for (String wood : WOODS) {
            List<String> ofWood = new ArrayList<>();
            for (boolean stripped : new boolean[] {false, true}) {
                String prefix = stripped ? "stripped_" : "";
                String id = prefix + wood + "_branch";
                String log = prefix + wood + "_log";
                String full = "naturaltrees:" + id;
                ofWood.add(full);
                allBranches.add(full);
                if (!stripped) {
                    plainBranches.add(full);
                }
                lang.add("  \"block.naturaltrees." + id + "\": \"" + title(id) + "\"");

                for (String part : new String[] {"core_side", "core_end", "arm", "inventory"}) {
                    write("assets/naturaltrees/models/block/" + id + "_" + part + ".json", """
                            {
                              "parent": "naturaltrees:block/branch_%s",
                              "textures": {
                                "side": "minecraft:block/%s",
                                "end": "minecraft:block/%s_top"
                              }
                            }
                            """.formatted(part, log, log));
                }
                write("assets/naturaltrees/models/item/" + id + ".json", """
                        {
                          "parent": "naturaltrees:block/%s_inventory"
                        }
                        """.formatted(id));
                write("assets/naturaltrees/blockstates/" + id + ".json", blockstate(id));
                // Drops itself, with the explosion condition of a log (spec 6.5).
                write("data/naturaltrees/loot_table/blocks/" + id + ".json", """
                        {
                          "type": "minecraft:block",
                          "pools": [
                            {
                              "bonus_rolls": 0.0,
                              "conditions": [ { "condition": "minecraft:survives_explosion" } ],
                              "entries": [ { "type": "minecraft:item", "name": "%1$s" } ],
                              "rolls": 1.0
                            }
                          ],
                          "random_sequence": "naturaltrees:blocks/%2$s"
                        }
                        """.formatted(full, id));
                // Two logs, one above the other, make four branches: value-neutral at 2 planks a branch (spec 6.5).
                write("data/naturaltrees/recipe/" + id + ".json", """
                        {
                          "type": "minecraft:crafting_shaped",
                          "category": "building",
                          "group": "branches",
                          "key": { "#": { "item": "minecraft:%s" } },
                          "pattern": [ "#", "#" ],
                          "result": { "count": 4, "id": "%s" }
                        }
                        """.formatted(log, full));
            }
            // One branch of either kind makes two planks; the item tag is the mod's own, never a log tag.
            write("data/naturaltrees/recipe/" + wood + "_planks_from_branch.json", """
                    {
                      "type": "minecraft:crafting_shapeless",
                      "category": "building",
                      "group": "planks",
                      "ingredients": [ { "tag": "naturaltrees:%1$s_branches" } ],
                      "result": { "count": 2, "id": "minecraft:%1$s_planks" }
                    }
                    """.formatted(wood));
            write("data/naturaltrees/tags/item/" + wood + "_branches.json", tag(ofWood));
            // As a block a branch is a log (spec 6.5): the per-wood tag leads to logs_that_burn, logs and mineable/axe.
            write("data/minecraft/tags/block/" + wood + "_logs.json", tag(ofWood));
        }
        write("data/naturaltrees/tags/item/branches.json", tag(allBranches));
        write("data/minecraft/tags/block/overworld_natural_logs.json", tag(plainBranches));
        write("assets/naturaltrees/lang/en_us.json", lang.toString());
    }
}
