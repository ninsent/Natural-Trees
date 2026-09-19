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
package kz.nursultan.naturaltrees;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only entry point on NeoForge: enables the Config button of the Mods screen with NeoForge's own
 * configuration screen, which edits the {@code felling.*} keys of the server configuration. The counterpart of the
 * Mod Menu screen on Fabric (questions.md Q26). Server values can be edited while a world is open on this computer.
 */
@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public class NaturalTreesClient {

    public NaturalTreesClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (minecraft, parent) -> new ConfigurationScreen(container, parent));
    }
}
