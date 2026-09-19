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

import java.util.function.Consumer;

/**
 * The text form of {@link FellingSettings} for loaders without a config system of their own (Fabric):
 * {@code key = value} lines under a {@code [felling]} header, which is also valid TOML. Pure text in, pure text
 * out, so it is tested without the game.
 */
public final class FellingSettingsFile {

    private FellingSettingsFile() {
    }

    /** The file as the mod writes it: every key with its comment and current value. */
    public static String render(FellingSettings s) {
        return """
                # Natural Trees, server settings. Built-in tree felling is off by default.
                [felling]
                # Master switch. With an axe, not sneaking, breaking a log or a branch fells the tree above it.
                enabled = %s
                # Stay enabled when another tree-felling mod is installed.
                force = %s
                # Hard cap on the wood blocks of one felling (%d to %d).
                max_blocks = %d
                # Non-persistent leaves the wood must touch, so that builds are never felled (%d to %d).
                min_leaves = %d
                # Wood blocks broken per tick (%d to %d).
                blocks_per_tick = %d
                """.formatted(s.enabled(), s.force(),
                FellingSettings.MAX_BLOCKS_MIN, FellingSettings.MAX_BLOCKS_MAX, s.maxBlocks(),
                FellingSettings.MIN_LEAVES_MIN, FellingSettings.MIN_LEAVES_MAX, s.minLeaves(),
                FellingSettings.BLOCKS_PER_TICK_MIN, FellingSettings.BLOCKS_PER_TICK_MAX, s.blocksPerTick());
    }

    /**
     * Reads the file. A missing key keeps its default; a value that cannot be read or lies outside its range is
     * reported through {@code warn} and replaced by the default or the nearest legal value. Unknown keys are reported.
     */
    public static FellingSettings parse(String text, Consumer<String> warn) {
        boolean enabled = FellingSettings.DEFAULT.enabled();
        boolean force = FellingSettings.DEFAULT.force();
        int maxBlocks = FellingSettings.DEFAULT.maxBlocks();
        int minLeaves = FellingSettings.DEFAULT.minLeaves();
        int blocksPerTick = FellingSettings.DEFAULT.blocksPerTick();

        for (String raw : text.split("\\R")) {
            String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')) : raw;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("[")) {
                continue;
            }
            final int equals = line.indexOf('=');
            if (equals < 0) {
                warn.accept("not a key = value line: " + raw.trim());
                continue;
            }
            String key = line.substring(0, equals).trim();
            if (key.startsWith("felling.")) {
                key = key.substring("felling.".length());
            }
            final String value = line.substring(equals + 1).trim();
            try {
                switch (key) {
                    case "enabled" -> enabled = bool(value);
                    case "force" -> force = bool(value);
                    case "max_blocks" -> maxBlocks = Integer.parseInt(value);
                    case "min_leaves" -> minLeaves = Integer.parseInt(value);
                    case "blocks_per_tick" -> blocksPerTick = Integer.parseInt(value);
                    default -> warn.accept("unknown key: " + key);
                }
            } catch (IllegalArgumentException e) {
                warn.accept(key + ": cannot read '" + value + "', the default is used");
            }
        }
        final FellingSettings read = new FellingSettings(enabled, force, maxBlocks, minLeaves, blocksPerTick);
        final FellingSettings legal = read.clamped();
        if (!legal.equals(read)) {
            warn.accept("a value was outside its range and was brought inside: " + legal);
        }
        return legal;
    }

    private static boolean bool(String value) {
        if (value.equals("true")) {
            return true;
        }
        if (value.equals("false")) {
            return false;
        }
        throw new IllegalArgumentException(value);
    }
}
