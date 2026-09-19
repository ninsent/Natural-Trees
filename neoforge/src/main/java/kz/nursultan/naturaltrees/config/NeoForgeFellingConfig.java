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
package kz.nursultan.naturaltrees.config;

import kz.nursultan.naturaltrees.felling.FellingSettings;
import net.neoforged.neoforge.common.ModConfigSpec;

/** The five felling keys as NeoForge's own server configuration (spec 14.3, 16; questions.md Q26). */
public final class NeoForgeFellingConfig {

    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.BooleanValue FORCE;
    private static final ModConfigSpec.IntValue MAX_BLOCKS;
    private static final ModConfigSpec.IntValue MIN_LEAVES;
    private static final ModConfigSpec.IntValue BLOCKS_PER_TICK;
    public static final ModConfigSpec SPEC;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        final FellingSettings d = FellingSettings.DEFAULT;
        builder.comment("Built-in tree felling. Off by default.").push("felling");
        ENABLED = builder.comment("Master switch. With an axe, not sneaking, breaking a log or a branch fells the tree above it.")
                .define("enabled", d.enabled());
        FORCE = builder.comment("Stay enabled when another tree-felling mod is installed.").define("force", d.force());
        MAX_BLOCKS = builder.comment("Hard cap on the wood blocks of one felling.")
                .defineInRange("max_blocks", d.maxBlocks(), FellingSettings.MAX_BLOCKS_MIN, FellingSettings.MAX_BLOCKS_MAX);
        MIN_LEAVES = builder.comment("Non-persistent leaves the wood must touch, so that builds are never felled.")
                .defineInRange("min_leaves", d.minLeaves(), FellingSettings.MIN_LEAVES_MIN, FellingSettings.MIN_LEAVES_MAX);
        BLOCKS_PER_TICK = builder.comment("Wood blocks broken per tick.")
                .defineInRange("blocks_per_tick", d.blocksPerTick(), FellingSettings.BLOCKS_PER_TICK_MIN, FellingSettings.BLOCKS_PER_TICK_MAX);
        builder.pop();
        SPEC = builder.build();
    }

    private NeoForgeFellingConfig() {
    }

    /** The current values; the defaults while no server configuration is loaded. */
    public static FellingSettings get() {
        if (!SPEC.isLoaded()) {
            return FellingSettings.DEFAULT;
        }
        return new FellingSettings(ENABLED.get(), FORCE.get(), MAX_BLOCKS.get(), MIN_LEAVES.get(), BLOCKS_PER_TICK.get());
    }
}
