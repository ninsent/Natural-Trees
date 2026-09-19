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
package kz.nursultan.naturaltrees.treecore;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins the generator's exact output. Spec 7.8 allows only optimisations that leave the output unchanged;
 * this test is how that is proved. The hashes change only when the algorithm changes on purpose: then
 * regenerate them with {@code ./gradlew :treecore:test -Dnaturaltrees.printGolden=true -i} and say why in
 * the commit message.
 */
class GoldenHashTest {

    private static final WorldRead ABOVE_GROUND = (x, y, z) -> y >= 0;
    private static final WorldRead HOLES = (x, y, z) -> y >= 0 && (x * 3 + y * 5 + z * 7 + 1) % 11 != 0;

    private static final long[] OAK = {
        -6259116965643371545L, 2728904090870272092L, -2281080244652861089L,
        -232411491698412163L, 3207956603524685437L, -7105325522818853036L,
        4520853042188079675L, 2513959023376316895L, -5548391653670100853L,
        -3235455048387157875L, 161920148939365206L, 1623808693782482517L,
        6348547869245959806L, -4617583064166125786L, -4119514826612014954L,
        -2993863851188669837L, 5125888961305014068L, -6954025913686293128L,
        -3335872517064577617L, -6271609131747482021L,
    };
    private static final long[] LARGE = {
        -6162016024701107292L, -6567876772591823276L, 5420085558456099539L,
        17259470407463102L, 3417493980758132152L, -1932750062904708089L,
        -5391476395278540644L, 1163235019058693184L, -8225015977793542635L,
        2032979598934574349L, 4310550534628283946L, 4386594794612965307L,
        1188450101024142967L, -4974810580596072368L, 6762408529682053967L,
        9187616786297899897L, -2175726795177015985L, 8689500009048211088L,
        -4083235432969142641L, 5318435238776857394L,
    };
    private static final long[] OBSTRUCTED_LEADER = {
        -5298628946536524876L, 8131226077926307996L, -6672260286953846149L,
        -8804387617363419693L, 8275865390902783081L, 3100687414152203415L,
        -9044297677420311125L, 8275865390902783081L, 4643000369627936590L,
        4620458506302121656L, 9130691297191805629L, -2098200365840278718L,
        3661784059965453766L, -2627439004712573832L, -6877612684163301720L,
        8275865390902783081L, -1075559238523495950L, 8890261016164929207L,
        -7364024340866750806L, 3140824577768479461L,
    };

    private static long[] hashes(int kind) {
        TreeGenerator g = new TreeGenerator();
        long[] out = new long[20];
        for (int seed = 0; seed < out.length; seed++) {
            TreeResult r = switch (kind) {
                case 0 -> g.generate(TestSpecies.oak(), TestSpecies.oakFoliage(), seed, 9, PlacementLimits.NONE,
                        ABOVE_GROUND, ABOVE_GROUND);
                case 1 -> g.generate(TestSpecies.largeBuilder().build(), TestSpecies.largeFoliage(), seed, 20,
                        PlacementLimits.NONE, ABOVE_GROUND, ABOVE_GROUND);
                default -> g.generate(TestSpecies.largeBuilder().trunkLeader(true).baseSplits(1)
                                .trunk(new StemParams(10, -10, -15, 4, 0.3, 25, 5)).build(),
                        TestSpecies.largeFoliage(), seed, 24, PlacementLimits.chunkArea(5, -9, 0, -1), HOLES, HOLES);
            };
            out[seed] = r.contentHash();
        }
        return out;
    }

    @Test
    void outputIsUnchanged() {
        if (Boolean.getBoolean("naturaltrees.printGolden")) {
            for (int kind = 0; kind < 3; kind++) {
                StringBuilder line = new StringBuilder("GOLDEN " + kind + ":");
                for (long h : hashes(kind)) {
                    line.append(' ').append(h).append("L,");
                }
                System.out.println(line);
            }
            return;
        }
        assertArrayEquals(OAK, hashes(0), "oak");
        assertArrayEquals(LARGE, hashes(1), "large");
        assertArrayEquals(OBSTRUCTED_LEADER, hashes(2), "obstructed, helix trunk with a leader, inside a chunk area");
    }
}
