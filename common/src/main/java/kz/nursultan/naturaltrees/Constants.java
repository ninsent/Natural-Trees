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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Constants {

    public static final String MOD_ID = "naturaltrees";
    public static final String MOD_NAME = "Natural Trees";
    /** Folder of the built-in world generation datapack under {@code resourcepacks/} (spec 11.1). */
    public static final String WORLDGEN_PACK = "naturaltrees_worldgen";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    private Constants() {
    }
}
