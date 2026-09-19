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
package kz.nursultan.naturaltrees.registry;

import java.util.function.Supplier;
import kz.nursultan.naturaltrees.Constants;
import kz.nursultan.naturaltrees.platform.Services;
import kz.nursultan.naturaltrees.worldgen.SkeletonFoliagePlacer;
import kz.nursultan.naturaltrees.worldgen.WeberPennTrunkPlacer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

/** The two placer types (spec 8.1, 9.1). */
public final class ModPlacers {

    private static Supplier<TrunkPlacerType<WeberPennTrunkPlacer>> weberPenn;
    private static Supplier<FoliagePlacerType<SkeletonFoliagePlacer>> skeleton;

    private ModPlacers() {
    }

    /** Called once by each loader while registries are open. */
    public static void register() {
        weberPenn = Services.PLATFORM.register(BuiltInRegistries.TRUNK_PLACER_TYPE,
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "weber_penn"),
                () -> Services.PLATFORM.createTrunkPlacerType(WeberPennTrunkPlacer.CODEC));
        skeleton = Services.PLATFORM.register(BuiltInRegistries.FOLIAGE_PLACER_TYPE,
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "skeleton"),
                () -> Services.PLATFORM.createFoliagePlacerType(SkeletonFoliagePlacer.CODEC));
    }

    public static TrunkPlacerType<WeberPennTrunkPlacer> weberPenn() {
        return weberPenn.get();
    }

    public static FoliagePlacerType<SkeletonFoliagePlacer> skeleton() {
        return skeleton.get();
    }
}
