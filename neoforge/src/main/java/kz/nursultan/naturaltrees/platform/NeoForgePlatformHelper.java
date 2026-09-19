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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import kz.nursultan.naturaltrees.Constants;
import kz.nursultan.naturaltrees.block.BranchBlock;
import kz.nursultan.naturaltrees.block.NeoForgeBranchBlock;
import kz.nursultan.naturaltrees.config.NeoForgeFellingConfig;
import kz.nursultan.naturaltrees.felling.FellingSettings;
import kz.nursultan.naturaltrees.platform.services.IPlatformHelper;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.registries.DeferredRegister;

public class NeoForgePlatformHelper implements IPlatformHelper {

    /** The mod's event bus; set by the mod class before anything is registered. */
    private static IEventBus modBus;
    private static final Map<ResourceKey<?>, DeferredRegister<?>> REGISTERS = new HashMap<>();

    public static void setModBus(IEventBus bus) {
        modBus = bus;
    }

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R, T extends R> Supplier<T> register(Registry<R> registry, ResourceLocation id, Supplier<T> factory) {
        final DeferredRegister<R> register = (DeferredRegister<R>) REGISTERS.computeIfAbsent(registry.key(), key -> {
            final DeferredRegister<R> created = DeferredRegister.create(registry.key(), Constants.MOD_ID);
            created.register(modBus);
            return created;
        });
        return register.register(id.getPath(), factory);
    }

    @Override
    public BranchBlock createBranchBlock(Supplier<? extends Block> strippedVariant, BlockBehaviour.Properties properties) {
        return new NeoForgeBranchBlock(strippedVariant, properties);
    }

    // NeoForge makes both constructors public.
    @Override
    public <P extends TrunkPlacer> TrunkPlacerType<P> createTrunkPlacerType(MapCodec<P> codec) {
        return new TrunkPlacerType<>(codec);
    }

    @Override
    public <P extends FoliagePlacer> FoliagePlacerType<P> createFoliagePlacerType(MapCodec<P> codec) {
        return new FoliagePlacerType<>(codec);
    }

    @Override
    public FellingSettings fellingSettings() {
        return NeoForgeFellingConfig.get();
    }
}
