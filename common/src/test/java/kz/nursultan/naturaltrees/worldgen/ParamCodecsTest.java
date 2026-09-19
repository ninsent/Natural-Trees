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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import kz.nursultan.naturaltrees.treecore.FoliageParams;
import kz.nursultan.naturaltrees.treecore.TrunkParams;
import org.junit.jupiter.api.Test;

/** The game's codecs read the very species files the viewer tunes (spec 8.2, 9.2, 17). */
class ParamCodecsTest {

    private static final Path SPECIES = Path.of("../tools/viewer/src/main/resources/species");

    private static List<Path> speciesFiles() throws IOException {
        try (Stream<Path> files = Files.list(SPECIES)) {
            List<Path> list = files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
            assertTrue(list.size() >= 5, "species files not found from " + Path.of("").toAbsolutePath());
            return list;
        }
    }

    @Test
    void everySpeciesFileDecodesAndSurvivesARoundTrip() throws IOException {
        for (Path file : speciesFiles()) {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();

            TrunkParams trunk = ParamCodecs.TRUNK.decoder().parse(JsonOps.INSTANCE, root.get("trunk_placer"))
                    .getOrThrow(message -> new AssertionError(file + ": " + message));
            var trunkJson = ParamCodecs.TRUNK.encoder().encodeStart(JsonOps.INSTANCE, trunk).getOrThrow();
            assertEquals(trunk, ParamCodecs.TRUNK.decoder().parse(JsonOps.INSTANCE, trunkJson).getOrThrow(), file.toString());

            FoliageParams foliage = ParamCodecs.FOLIAGE.decoder().parse(JsonOps.INSTANCE, root.get("foliage_placer"))
                    .getOrThrow(message -> new AssertionError(file + ": " + message));
            var foliageJson = ParamCodecs.FOLIAGE.encoder().encodeStart(JsonOps.INSTANCE, foliage).getOrThrow();
            assertEquals(foliage, ParamCodecs.FOLIAGE.decoder().parse(JsonOps.INSTANCE, foliageJson).getOrThrow(), file.toString());
        }
    }

    @Test
    void defaultsOfTheSpecApply() {
        JsonObject minimal = JsonParser.parseString("""
                {"shape": "conical", "base_size": 0.2, "max_radius": 5, "max_tips": 8, "trunk": {"curve_res": 3},
                 "levels": [{"branches": 4, "length": 0.5, "down_angle": 60, "rotate": 140, "curve_res": 2}]}
                """).getAsJsonObject();
        TrunkParams p = ParamCodecs.TRUNK.decoder().parse(JsonOps.INSTANCE, minimal).getOrThrow();
        assertEquals(0.25, p.twigRadius());
        assertEquals(2.0, p.pipeExponent());
        assertEquals(1, p.trunkWidthMin());
        assertEquals(2, p.trunkWidthMax());
        assertEquals(3, p.foliageMargin());
        assertEquals(0, p.baseSplits());
        assertEquals(FoliageParams.defaults(300), ParamCodecs.FOLIAGE.decoder()
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"max_leaves\": 300}")).getOrThrow());
    }

    @Test
    void rangeErrorsNameTheField() throws IOException {
        JsonObject oak = JsonParser.parseString(Files.readString(SPECIES.resolve("oak.json"))).getAsJsonObject()
                .getAsJsonObject("trunk_placer");
        oak.addProperty("base_size", 1.2);
        DataResult<TrunkParams> result = ParamCodecs.TRUNK.decoder().parse(JsonOps.INSTANCE, oak);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().get().message().contains("base_size"), result.error().get().message());

        oak.addProperty("base_size", 0.4);
        oak.addProperty("max_radius", 12);
        oak.addProperty("foliage_margin", 5);
        result = ParamCodecs.TRUNK.decoder().parse(JsonOps.INSTANCE, oak);
        String message = result.error().orElseThrow().message();
        assertTrue(message.contains("12") && message.contains("5"), message);
    }
}
