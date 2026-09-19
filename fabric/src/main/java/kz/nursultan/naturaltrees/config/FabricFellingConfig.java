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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import kz.nursultan.naturaltrees.Constants;
import kz.nursultan.naturaltrees.felling.FellingSettings;
import kz.nursultan.naturaltrees.felling.FellingSettingsFile;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric has no config system, and the mod adds no dependency: the five felling keys live in
 * {@code config/naturaltrees-server.toml} (questions.md Q26). The file is written with its defaults on first
 * start, read again whenever a server starts, and rewritten by the Mod Menu config screen.
 */
public final class FabricFellingConfig {

    private static volatile FellingSettings current = FellingSettings.DEFAULT;

    private FabricFellingConfig() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(Constants.MOD_ID + "-server.toml");
    }

    public static FellingSettings get() {
        return current;
    }

    public static void load() {
        final Path file = file();
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, FellingSettingsFile.render(FellingSettings.DEFAULT), StandardCharsets.UTF_8);
            }
            current = FellingSettingsFile.parse(Files.readString(file, StandardCharsets.UTF_8),
                    warning -> Constants.LOG.warn("{}: {}", file.getFileName(), warning));
        } catch (IOException e) {
            Constants.LOG.warn("Could not read {}; the defaults are used", file, e);
            current = FellingSettings.DEFAULT;
        }
    }

    /** Writes the settings and makes them current at once. */
    public static void save(FellingSettings settings) {
        current = settings.clamped();
        final Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, FellingSettingsFile.render(current), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Constants.LOG.warn("Could not write {}", file, e);
        }
    }
}
