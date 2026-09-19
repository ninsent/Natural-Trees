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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import kz.nursultan.naturaltrees.Constants;
import kz.nursultan.naturaltrees.block.BranchBlock;
import kz.nursultan.naturaltrees.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Built-in tree felling (spec 14.3), off by default. A break only queues a felling; the next server tick checks
 * that the block is really gone and the player still holds an axe, searches along the tree's structure, applies
 * the guards, and then breaks the wood a few blocks per tick (questions.md Q28). Both loaders call the same four
 * methods from their own events.
 */
public final class FellingManager {

    /** Mods that fell trees themselves (spec 14.2, questions.md Q27). */
    static final List<String> KNOWN_FELLING_MODS = List.of("fallingtree", "treechop", "treeharvester");

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final List<Job> JOBS = new ArrayList<>();
    private static String otherFellingMod;

    private FellingManager() {
    }

    private record Pending(ServerLevel level, BlockPos pos, int kind, int arms, UUID player) {
    }

    private static final class Job {
        final ServerLevel level;
        final UUID player;
        final List<FellingSearch.Found> wood;
        int next;

        Job(ServerLevel level, UUID player, List<FellingSearch.Found> wood) {
            this.level = level;
            this.player = player;
            this.wood = wood;
        }
    }

    public static void onServerStarted() {
        PENDING.clear();
        JOBS.clear();
        otherFellingMod = null;
        for (String id : KNOWN_FELLING_MODS) {
            if (Services.PLATFORM.isModLoaded(id)) {
                otherFellingMod = id;
                Constants.LOG.info("The tree-felling mod '{}' is installed: built-in felling stays off unless felling.force is set", id);
                break;
            }
        }
    }

    public static void onServerStopped() {
        PENDING.clear();
        JOBS.clear();
    }

    private static boolean active(FellingSettings settings) {
        return settings.enabled() && (otherFellingMod == null || settings.force());
    }

    static int kindOf(BlockState state) {
        if (state.getBlock() instanceof BranchBlock) {
            return WoodView.BRANCH;
        }
        if (state.is(BlockTags.LOGS)) {
            return WoodView.LOG;
        }
        if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
            return WoodView.NATURAL_LEAF;
        }
        return WoodView.OTHER;
    }

    /** Called by each loader when a player breaks, or is about to break, a block. */
    public static void onBlockBroken(Level level, BlockPos pos, BlockState state, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || player.isShiftKeyDown() || player.isSpectator()
                || !(player.getMainHandItem().getItem() instanceof AxeItem) || !active(Services.PLATFORM.fellingSettings())) {
            return;
        }
        final int kind = kindOf(state);
        if (kind == WoodView.LOG || kind == WoodView.BRANCH) {
            PENDING.add(new Pending(serverLevel, pos.immutable(), kind,
                    kind == WoodView.BRANCH ? BranchBlock.armMask(state) : 0, player.getUUID()));
        }
    }

    /** Called by each loader at the end of every server tick. */
    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty() && JOBS.isEmpty()) {
            return;
        }
        final FellingSettings settings = Services.PLATFORM.fellingSettings();
        for (Pending pending : PENDING) {
            start(server, pending, settings);
        }
        PENDING.clear();
        for (Iterator<Job> jobs = JOBS.iterator(); jobs.hasNext(); ) {
            if (!work(server, jobs.next(), settings)) {
                jobs.remove();
            }
        }
    }

    private static void start(MinecraftServer server, Pending pending, FellingSettings settings) {
        final int now = kindOf(pending.level().getBlockState(pending.pos()));
        if (now == WoodView.LOG || now == WoodView.BRANCH) {
            return; // the break was cancelled: the block is still there
        }
        final ServerPlayer player = server.getPlayerList().getPlayer(pending.player());
        if (player == null || !(player.getMainHandItem().getItem() instanceof AxeItem) || !active(settings)) {
            return;
        }
        final ServerLevel level = pending.level();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final WoodView view = new WoodView() {
            @Override
            public int kind(int x, int y, int z) {
                return level.isLoaded(cursor.set(x, y, z)) ? kindOf(level.getBlockState(cursor)) : OTHER;
            }

            @Override
            public int arms(int x, int y, int z) {
                final BlockState state = level.getBlockState(cursor.set(x, y, z));
                return state.getBlock() instanceof BranchBlock ? BranchBlock.armMask(state) : 0;
            }
        };
        final BlockPos pos = pending.pos();
        final FellingSearch.Result found = FellingSearch.search(view, pos.getX(), pos.getY(), pos.getZ(),
                pending.kind(), pending.arms(), settings.maxBlocks());
        // Spec 14.3: player-placed leaves are persistent, so a build touches none and is never felled.
        if (!found.wood().isEmpty() && found.leaves() >= settings.minLeaves()) {
            JOBS.add(new Job(level, pending.player(), found.wood()));
        }
    }

    /** Breaks up to {@code blocks_per_tick} blocks; false when the job is over. */
    private static boolean work(MinecraftServer server, Job job, FellingSettings settings) {
        final ServerPlayer player = server.getPlayerList().getPlayer(job.player);
        if (player == null || player.level() != job.level) {
            return false;
        }
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int broken = 0; broken < settings.blocksPerTick() && job.next < job.wood.size(); job.next++) {
            final ItemStack tool = player.getMainHandItem();
            if (!(tool.getItem() instanceof AxeItem)) {
                return false; // the axe broke or was put away
            }
            final FellingSearch.Found f = job.wood.get(job.next);
            cursor.set(f.x(), f.y(), f.z());
            final int kind = kindOf(job.level.getBlockState(cursor));
            if (kind != WoodView.LOG && kind != WoodView.BRANCH) {
                continue; // gone in the meantime
            }
            // Drops appear where the block was; leaves are left to decay the vanilla way. One durability per block.
            job.level.destroyBlock(cursor, true, player);
            tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            broken++;
        }
        return job.next < job.wood.size();
    }
}
