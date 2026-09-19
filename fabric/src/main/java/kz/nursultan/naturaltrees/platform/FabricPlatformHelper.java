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
package kz.nursultan.naturaltrees.platform;

import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import kz.nursultan.naturaltrees.block.BranchBlock;
import kz.nursultan.naturaltrees.config.FabricFellingConfig;
import kz.nursultan.naturaltrees.felling.FellingSettings;
import kz.nursultan.naturaltrees.mixin.FoliagePlacerTypeInvoker;
import kz.nursultan.naturaltrees.mixin.TrunkPlacerTypeInvoker;
import kz.nursultan.naturaltrees.platform.services.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public <R, T extends R> Supplier<T> register(Registry<R> registry, ResourceLocation id, Supplier<T> factory) {
        final T value = Registry.register(registry, id, factory.get());
        return () -> value;
    }

    @Override
    public BranchBlock createBranchBlock(Supplier<? extends Block> strippedVariant, BlockBehaviour.Properties properties) {
        return new BranchBlock(strippedVariant, properties);
    }

    @Override
    public <P extends TrunkPlacer> TrunkPlacerType<P> createTrunkPlacerType(MapCodec<P> codec) {
        return TrunkPlacerTypeInvoker.naturaltrees$create(codec);
    }

    @Override
    public <P extends FoliagePlacer> FoliagePlacerType<P> createFoliagePlacerType(MapCodec<P> codec) {
        return FoliagePlacerTypeInvoker.naturaltrees$create(codec);
    }

    @Override
    public FellingSettings fellingSettings() {
        return FabricFellingConfig.get();
    }
}
