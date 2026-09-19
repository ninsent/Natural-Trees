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

/**
 * The server configuration (spec 14.3, 16): the five {@code felling.*} keys and nothing else. Each loader reads
 * them its own way; this record is all that the rest of the mod sees.
 *
 * @param enabled master switch; false by default
 * @param force stay enabled when a known felling mod is present
 * @param maxBlocks hard cap on one felling
 * @param minLeaves non-persistent leaves the found wood must touch
 * @param blocksPerTick pacing
 */
public record FellingSettings(boolean enabled, boolean force, int maxBlocks, int minLeaves, int blocksPerTick) {

    public static final FellingSettings DEFAULT = new FellingSettings(false, false, 512, 8, 32);

    public static final int MAX_BLOCKS_MIN = 1;
    public static final int MAX_BLOCKS_MAX = 4096;
    public static final int MIN_LEAVES_MIN = 0;
    public static final int MIN_LEAVES_MAX = 64;
    public static final int BLOCKS_PER_TICK_MIN = 1;
    public static final int BLOCKS_PER_TICK_MAX = 256;

    /** The same settings with every number brought inside its range. */
    public FellingSettings clamped() {
        return new FellingSettings(enabled, force,
                Math.max(MAX_BLOCKS_MIN, Math.min(MAX_BLOCKS_MAX, maxBlocks)),
                Math.max(MIN_LEAVES_MIN, Math.min(MIN_LEAVES_MAX, minLeaves)),
                Math.max(BLOCKS_PER_TICK_MIN, Math.min(BLOCKS_PER_TICK_MAX, blocksPerTick)));
    }
}
