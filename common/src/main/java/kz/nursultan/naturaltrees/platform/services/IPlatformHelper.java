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
package kz.nursultan.naturaltrees.platform.services;

import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import kz.nursultan.naturaltrees.block.BranchBlock;
import kz.nursultan.naturaltrees.block.BranchWood;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

public interface IPlatformHelper {

    /**
     * Registers an object the loader's way: a {@code DeferredRegister} on NeoForge, {@code Registry.register}
     * on Fabric. The factory runs once, when the loader is ready for it.
     *
     * @return a supplier of the registered object, valid once registration has happened
     */
    <R, T extends R> Supplier<T> register(Registry<R> registry, ResourceLocation id, Supplier<T> factory);

    /**
     * Creates a branch block. NeoForge returns a subclass that overrides the loader's extension methods for
     * stripping and flammability; Fabric returns the common class and uses its registries (spec 6.4).
     */
    BranchBlock createBranchBlock(BranchWood wood, boolean stripped, BlockBehaviour.Properties properties);

    /**
     * Both placer type classes have a private constructor in vanilla (docs/assumptions.md). NeoForge makes
     * them public; Fabric reaches them through invoker mixins (spec 8.1, 9.1).
     */
    <P extends TrunkPlacer> TrunkPlacerType<P> createTrunkPlacerType(MapCodec<P> codec);

    <P extends FoliagePlacer> FoliagePlacerType<P> createFoliagePlacerType(MapCodec<P> codec);

    /**
     * Gets the name of the current platform
     *
     * @return The name of the current platform.
     */
    String getPlatformName();

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return True if the mod is loaded, false otherwise.
     */
    boolean isModLoaded(String modId);

    /**
     * Check if the game is currently in a development environment.
     *
     * @return True if in a development environment, false otherwise.
     */
    boolean isDevelopmentEnvironment();

    /**
     * Gets the name of the environment type as a string.
     *
     * @return The name of the environment type.
     */
    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }
}
