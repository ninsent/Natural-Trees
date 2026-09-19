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
package kz.nursultan.naturaltrees.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import kz.nursultan.naturaltrees.Constants;
import kz.nursultan.naturaltrees.treecore.TreeResult;
import kz.nursultan.naturaltrees.worldgen.WeberPennTrunkPlacer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

/**
 * {@code /naturaltrees place <configured_feature> [seed]}, {@code grid <configured_feature> <n>} and
 * {@code stats <configured_feature> [seeds]} (spec section 16). Permission level 2. A seed makes the tree repeatable: the feature's whole random source
 * starts from it, so the same seed at the same place gives the same tree.
 */
public final class NaturalTreesCommands {

    private static final SimpleCommandExceptionType NOT_LOADED =
            new SimpleCommandExceptionType(Component.literal("That position is not loaded"));
    private static final SimpleCommandExceptionType FAILED =
            new SimpleCommandExceptionType(Component.literal("The feature could not be placed there"));

    /** Crowns are at most 33 blocks wide (spec 13); trees of another kind get this spacing. */
    private static final int DEFAULT_SPACING = 34;
    private static final int MAX_GRID = 32;
    private static final int DEFAULT_STATS_SEEDS = 1000;
    private static final int MAX_STATS_SEEDS = 100_000;
    private static final SimpleCommandExceptionType NOT_OURS = new SimpleCommandExceptionType(
            Component.literal("That feature is not a minecraft:tree with the naturaltrees:weber_penn trunk placer"));

    private NaturalTreesCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Constants.MOD_ID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("place")
                        .then(Commands.argument("feature", ResourceKeyArgument.key(Registries.CONFIGURED_FEATURE))
                                .executes(context -> place(context.getSource(),
                                        ResourceKeyArgument.getConfiguredFeature(context, "feature"),
                                        context.getSource().getLevel().getRandom().nextLong()))
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .executes(context -> place(context.getSource(),
                                                ResourceKeyArgument.getConfiguredFeature(context, "feature"),
                                                LongArgumentType.getLong(context, "seed"))))))
                .then(Commands.literal("stats")
                        .then(Commands.argument("feature", ResourceKeyArgument.key(Registries.CONFIGURED_FEATURE))
                                .executes(context -> stats(context.getSource(),
                                        ResourceKeyArgument.getConfiguredFeature(context, "feature"), DEFAULT_STATS_SEEDS))
                                .then(Commands.argument("seeds", IntegerArgumentType.integer(1, MAX_STATS_SEEDS))
                                        .executes(context -> stats(context.getSource(),
                                                ResourceKeyArgument.getConfiguredFeature(context, "feature"),
                                                IntegerArgumentType.getInteger(context, "seeds"))))))
                .then(Commands.literal("grid")
                        .then(Commands.argument("feature", ResourceKeyArgument.key(Registries.CONFIGURED_FEATURE))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, MAX_GRID))
                                        .executes(context -> grid(context.getSource(),
                                                ResourceKeyArgument.getConfiguredFeature(context, "feature"),
                                                IntegerArgumentType.getInteger(context, "n")))))));
    }

    private static boolean placeAt(ServerLevel level, ConfiguredFeature<?, ?> feature, BlockPos pos, long seed) {
        return feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed), pos);
    }

    private static int place(CommandSourceStack source, Holder.Reference<ConfiguredFeature<?, ?>> feature, long seed)
            throws CommandSyntaxException {
        final ServerLevel level = source.getLevel();
        final BlockPos pos = BlockPos.containing(source.getPosition());
        if (!level.isLoaded(pos)) {
            throw NOT_LOADED.create();
        }
        if (!placeAt(level, feature.value(), pos, seed)) {
            throw FAILED.create();
        }
        final String id = feature.key().location().toString();
        source.sendSuccess(() -> Component.literal("Placed " + id + " with seed " + seed + " at "
                + pos.getX() + " " + pos.getY() + " " + pos.getZ()), true);
        return 1;
    }

    /** A row of {@code n} trees toward +x at seeds 1 to n, each on the ground at its column, for screenshots. */
    private static int grid(CommandSourceStack source, Holder.Reference<ConfiguredFeature<?, ?>> feature, int n)
            throws CommandSyntaxException {
        final ServerLevel level = source.getLevel();
        final BlockPos start = BlockPos.containing(source.getPosition());
        final int spacing = spacing(feature.value());
        int placed = 0;
        int skipped = 0;
        for (int i = 0; i < n; i++) {
            final BlockPos column = start.offset(i * spacing, 0, 0);
            if (!level.isLoaded(column)) {
                skipped++;
                continue;
            }
            final BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (placeAt(level, feature.value(), ground, i + 1L)) {
                placed++;
            }
        }
        final int placedCount = placed;
        final int skippedCount = skipped;
        final String id = feature.key().location().toString();
        source.sendSuccess(() -> Component.literal("Placed " + placedCount + " of " + n + " " + id + " at seeds 1 to " + n
                + ", " + spacing + " blocks apart toward +x"
                + (skippedCount > 0 ? "; " + skippedCount + " positions were not loaded" : "")), true);
        return placedCount;
    }

    /** A running minimum, maximum and mean. */
    private static final class Tally {
        private double sum;
        private double min = Double.MAX_VALUE;
        private double max = -Double.MAX_VALUE;
        private int n;

        void add(double value) {
            sum += value;
            min = Math.min(min, value);
            max = Math.max(max, value);
            n++;
        }

        String line(String name) {
            return String.format(java.util.Locale.ROOT, "%s: %.1f (%.0f to %.0f)", name, sum / n, min, max);
        }
    }

    /**
     * Spec 16: averages over seeds 1 to {@code seeds} of the trees this feature would place where the player
     * stands. Nothing is written. Each seed draws its height and its tree seed as {@code place <seed>} does,
     * so the numbers describe real trees at this spot, obstacles included.
     */
    private static int stats(CommandSourceStack source, Holder.Reference<ConfiguredFeature<?, ?>> feature, int seeds)
            throws CommandSyntaxException {
        if (!(feature.value().config() instanceof TreeConfiguration config)
                || !(config.trunkPlacer instanceof WeberPennTrunkPlacer placer)) {
            throw NOT_OURS.create();
        }
        final ServerLevel level = source.getLevel();
        final BlockPos origin = BlockPos.containing(source.getPosition());
        final Tally height = new Tally(), radius = new Tally(), width = new Tally(), logs = new Tally(),
                branches = new Tally(), tips = new Tally(), truncated = new Tally(), dropped = new Tally(),
                leaves = new Tally(), shade = new Tally(), reach = new Tally(), budget = new Tally();
        int capped = 0;
        for (int seed = 1; seed <= seeds; seed++) {
            final RandomSource random = RandomSource.create(seed);
            final TreeResult tree = placer.simulate(level, random, placer.getTreeHeight(random), origin, config);
            int top = 0;
            int logCount = 0;
            double far = 0;
            boolean wide = false;
            for (int i = 0; i < tree.woodCount(); i++) {
                top = Math.max(top, tree.woodY(i) + 1);
                logCount += tree.isBranch(i) ? 0 : 1;
                far = Math.max(far, Math.hypot(tree.woodX(i), tree.woodZ(i)));
                wide |= tree.woodX(i) == 1 && tree.woodY(i) == 0 && tree.woodZ(i) == 1;
            }
            for (int i = 0; i < tree.leafCount(); i++) {
                top = Math.max(top, tree.leafY(i) + 1);
                far = Math.max(far, Math.hypot(tree.leafX(i), tree.leafZ(i)));
            }
            height.add(top);
            radius.add(far);
            width.add(wide ? 2 : 1);
            logs.add(logCount);
            branches.add(tree.woodCount() - logCount);
            tips.add(tree.tipCount());
            truncated.add(tree.stemsTruncated());
            dropped.add(tree.stemsDroppedByFaceRule() + tree.stemsDroppedWithParent());
            leaves.add(tree.leafCount());
            shade.add(tree.leavesDiscardedByShade());
            reach.add(tree.leavesDiscardedByReach());
            budget.add(tree.leavesDiscardedByBudget());
            capped += tree.stemCapReached() ? 1 : 0;
        }
        final int cappedCount = capped;
        final String id = feature.key().location().toString();
        source.sendSuccess(() -> Component.literal(String.join("\n",
                id + " at " + origin.getX() + " " + origin.getY() + " " + origin.getZ() + ", seeds 1 to " + seeds
                        + ": mean (min to max)",
                height.line("height"), radius.line("crown radius"), width.line("trunk width"),
                logs.line("logs"), branches.line("branch blocks"), tips.line("tips"),
                truncated.line("truncated stems"), dropped.line("dropped stems"),
                leaves.line("leaves"), shade.line("leaves lost to self-shading"), reach.line("leaves lost to reach"),
                budget.line("leaves lost to the budget"),
                "trees that hit the stem cap: " + cappedCount)), false);
        return seeds;
    }

    private static int spacing(ConfiguredFeature<?, ?> feature) {
        if (feature.config() instanceof TreeConfiguration tree && tree.trunkPlacer instanceof WeberPennTrunkPlacer placer) {
            return 2 * (placer.params().maxRadius() + placer.params().foliageMargin()) + 2;
        }
        return DEFAULT_SPACING;
    }
}
