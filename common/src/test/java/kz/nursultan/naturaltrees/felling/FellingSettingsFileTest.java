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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FellingSettingsFileTest {

    @Test
    void defaultsAreTheSpecs() {
        assertEquals(new FellingSettings(false, false, 512, 8, 32), FellingSettings.DEFAULT);
    }

    @Test
    void whatIsWrittenIsReadBack() {
        FellingSettings s = new FellingSettings(true, true, 100, 3, 64);
        List<String> warnings = new ArrayList<>();
        assertEquals(s, FellingSettingsFile.parse(FellingSettingsFile.render(s), warnings::add));
        assertEquals(FellingSettings.DEFAULT, FellingSettingsFile.parse(FellingSettingsFile.render(FellingSettings.DEFAULT), warnings::add));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void missingKeysKeepTheirDefaults() {
        List<String> warnings = new ArrayList<>();
        FellingSettings s = FellingSettingsFile.parse("[felling]\nenabled = true   # my note\n", warnings::add);
        assertEquals(new FellingSettings(true, false, 512, 8, 32), s);
        assertTrue(warnings.isEmpty());
        assertEquals(FellingSettings.DEFAULT, FellingSettingsFile.parse("", warnings::add));
    }

    @Test
    void dottedKeysAreAccepted() {
        assertEquals(new FellingSettings(true, false, 512, 8, 16),
                FellingSettingsFile.parse("felling.enabled = true\nfelling.blocks_per_tick = 16", w -> { }));
    }

    @Test
    void badValuesFallBackWithAWarning() {
        List<String> warnings = new ArrayList<>();
        FellingSettings s = FellingSettingsFile.parse(
                "enabled = yes\nmax_blocks = lots\nmin_leaves = 900\nblocks_per_tick = 0\ncolour = green\nnonsense", warnings::add);
        assertEquals(new FellingSettings(false, false, 512, 64, 1), s);
        assertEquals(5, warnings.size(), warnings.toString());
    }
}
