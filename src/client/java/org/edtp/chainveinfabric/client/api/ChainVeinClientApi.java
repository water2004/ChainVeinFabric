package org.edtp.chainveinfabric.client.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.edtp.chainveinfabric.Chainveinfabric;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.compat.quickshulker.QuickShulkerIntegration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * ChainVein 的客户端作业 API。
 *
 * <p>搜索逻辑和外部 Mod 都只向这里提交已经确定的坐标。API 统一负责坐标去重、
 * 作业排队、服务端协议批量发送，以及服务端未安装 ChainVein 时的原版客户端发包回退。</p>
 */
@Environment(EnvType.CLIENT)
public final class ChainVeinClientApi {
    public static final int MAX_QUEUED_JOBS = 10_000;

    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();
    private static final Set<JobKey> QUEUED_JOBS = new HashSet<>();
    private static final Map<BlockPos, PendingMine> PENDING_MINES = new HashMap<>();

    private static ClientLevel queuedLevel;
    private static int tickCounter;
    private static long clientTick;
    private static int activeMineJobs;
    private static boolean dispatching;

    private ChainVeinClientApi() {
    }

    public static int queueMineJobs(Minecraft client, Collection<BlockPos> positions) {
        if (!prepareQueue(client, positions)) {
            return 0;
        }

        boolean directToInventory = ChainveinfabricClient.CONFIG != null
                && ChainveinfabricClient.CONFIG.directToInventory;
        boolean quickShulkerOverflow = directToInventory
                && ChainveinfabricClient.CONFIG.quickShulkerOverflow
                && QuickShulkerIntegration.isAvailable();
        int protectionCapacity = getProtectedMineCapacity(client);
        int requestedNewJobs = countNewJobs(JobType.MINE, positions);
        int added = enqueuePrepared(
                client, JobType.MINE, positions, directToInventory,
                quickShulkerOverflow, protectionCapacity);

        if (protectionCapacity < requestedNewJobs) {
            client.gui.setOverlayMessage(
                    Component.translatable("message.chainveinfabric.protection"), false);
        }
        return added;
    }

    public static int queuePlantJobs(Minecraft client, Collection<BlockPos> positions) {
        return enqueue(client, JobType.PLANT, positions, false);
    }

    public static int queueUseJobs(Minecraft client, Collection<BlockPos> positions) {
        return enqueue(client, JobType.USE, positions, false);
    }

    public static boolean isDispatching() {
        return dispatching;
    }

    public static int queuedJobCount() {
        return JOBS.size();
    }

    public static boolean hasPendingMineJobs() {
        return activeMineJobs > 0;
    }

    public static void tick(Minecraft client) {
        if (!isClientReady(client)) {
            clear();
            return;
        }

        if (queuedLevel != client.level) {
            clear();
            queuedLevel = client.level;
        }

        clientTick++;
        updatePendingMines(client);

        if (JOBS.isEmpty()) {
            tickCounter = 0;
            return;
        }

        int interval = ChainveinfabricClient.CONFIG != null
                ? ChainveinfabricClient.CONFIG.packetInterval
                : 0;
        if (interval <= 0) {
            while (!JOBS.isEmpty()) {
                dispatchNext(client);
            }
            return;
        }

        if (interval >= 50) {
            int ticksPerDispatch = Math.max(1, interval / 50);
            if (++tickCounter >= ticksPerDispatch) {
                dispatchNext(client);
                tickCounter = 0;
            }
            return;
        }

        int dispatchesPerTick = Math.max(1, 50 / interval);
        for (int i = 0; i < dispatchesPerTick && !JOBS.isEmpty(); i++) {
            dispatchNext(client);
        }
    }

    public static void clear() {
        JOBS.clear();
        QUEUED_JOBS.clear();
        PENDING_MINES.clear();
        queuedLevel = null;
        tickCounter = 0;
        clientTick = 0L;
        activeMineJobs = 0;
        dispatching = false;
    }

    private static int enqueue(Minecraft client, JobType type,
                               Collection<BlockPos> positions, boolean directToInventory) {
        if (!prepareQueue(client, positions)) {
            return 0;
        }

        return enqueuePrepared(
                client, type, positions, directToInventory, false, Integer.MAX_VALUE);
    }

    private static int enqueuePrepared(Minecraft client, JobType type,
                                       Collection<BlockPos> positions,
                                       boolean directToInventory, boolean quickShulkerOverflow,
                                       int maxAdds) {
        if (maxAdds <= 0) return 0;

        int added = 0;
        for (BlockPos pos : positions) {
            if (JOBS.size() >= MAX_QUEUED_JOBS || added >= maxAdds) break;
            if (pos == null) continue;

            BlockPos immutablePos = pos.immutable();
            JobKey key = new JobKey(type, immutablePos);
            if (!QUEUED_JOBS.add(key)) continue;

            JOBS.addLast(new Job(type, immutablePos, directToInventory, quickShulkerOverflow,
                    client.level.getBlockState(immutablePos)));
            if (type == JobType.MINE) activeMineJobs++;
            added++;
        }
        return added;
    }

    private static boolean prepareQueue(Minecraft client, Collection<BlockPos> positions) {
        if (!isClientReady(client) || positions == null || positions.isEmpty()) {
            return false;
        }

        if (queuedLevel != client.level) {
            clear();
            queuedLevel = client.level;
        }
        return true;
    }

    private static int countNewJobs(JobType type, Collection<BlockPos> positions) {
        Set<JobKey> newJobs = new HashSet<>();
        for (BlockPos pos : positions) {
            if (pos == null) continue;
            JobKey key = new JobKey(type, pos);
            if (!QUEUED_JOBS.contains(key)) {
                newJobs.add(key);
            }
        }
        return newJobs.size();
    }

    /**
     * 返回在当前工具保护设置下还能安全接收的挖掘作业数。
     *
     * <p>activeMineJobs 同时包含尚未发出的作业和等待世界状态确认的作业，
     * 因此连续调用公共 API 也不会绕过耐久余量。</p>
     */
    private static int getProtectedMineCapacity(Minecraft client) {
        if (ChainveinfabricClient.CONFIG == null
                || !ChainveinfabricClient.CONFIG.toolProtection
                || client.player.isCreative()) {
            return Integer.MAX_VALUE;
        }

        ItemStack tool = client.player.getMainHandItem();
        if (!tool.isDamageableItem()) {
            return Integer.MAX_VALUE;
        }

        int remainingDurability = tool.getMaxDamage() - tool.getDamageValue();
        int safeJobs = Math.max(0, remainingDurability - 10);
        return Math.max(0, safeJobs - activeMineJobs);
    }

    private static void dispatchNext(Minecraft client) {
        Job first = poll();
        if (first == null) return;

        if (canUseServerProtocol(first.type())) {
            List<Job> batch = new ArrayList<>();
            batch.add(first);

            while (!JOBS.isEmpty()) {
                Job next = JOBS.peekFirst();
                if (next == null
                        || next.type() != first.type()
                        || next.directToInventory() != first.directToInventory()
                        || next.quickShulkerOverflow() != first.quickShulkerOverflow()) {
                    break;
                }
                batch.add(poll());
            }

            dispatchServerBatch(first, batch.stream().map(Job::pos).toList());
            for (Job job : batch) markPendingMine(job);
            return;
        }

        dispatchClientJob(client, first);
        markPendingMine(first);
    }

    private static Job poll() {
        Job job = JOBS.pollFirst();
        if (job != null && job.type() != JobType.MINE) {
            QUEUED_JOBS.remove(new JobKey(job.type(), job.pos()));
        }
        return job;
    }

    private static boolean canUseServerProtocol(JobType type) {
        return switch (type) {
            case MINE -> ClientPlayNetworking.canSend(Chainveinfabric.ChainMinePayload.ID);
            case PLANT, USE -> ClientPlayNetworking.canSend(Chainveinfabric.ChainInteractPayload.ID);
        };
    }

    private static void dispatchServerBatch(Job first, List<BlockPos> positions) {
        if (first.type() == JobType.MINE) {
            if (first.quickShulkerOverflow()
                    && ClientPlayNetworking.canSend(Chainveinfabric.ChainMineWithShulkerPayload.ID)) {
                ClientPlayNetworking.send(new Chainveinfabric.ChainMineWithShulkerPayload(
                        positions, first.directToInventory()));
            } else {
                ClientPlayNetworking.send(
                        new Chainveinfabric.ChainMinePayload(positions, first.directToInventory()));
            }
        } else {
            ClientPlayNetworking.send(new Chainveinfabric.ChainInteractPayload(positions));
        }
    }

    private static void dispatchClientJob(Minecraft client, Job job) {
        dispatching = true;
        try {
            if (job.type() == JobType.MINE) {
                client.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                        job.pos(), Direction.UP));
                client.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                        job.pos(), Direction.UP));
                return;
            }

            client.gameMode.useItemOn(
                    client.player,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(job.pos().getCenter(), Direction.UP, job.pos(), false));
        } finally {
            dispatching = false;
        }
    }

    private static void markPendingMine(Job job) {
        if (job.type() != JobType.MINE) return;
        PENDING_MINES.put(job.pos(),
                new PendingMine(job.initialState(), clientTick + 100L));
    }

    private static void updatePendingMines(Minecraft client) {
        Iterator<Map.Entry<BlockPos, PendingMine>> iterator = PENDING_MINES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, PendingMine> entry = iterator.next();
            PendingMine pending = entry.getValue();
            if (clientTick >= pending.expiresAtTick()
                    || !client.level.getBlockState(entry.getKey()).equals(pending.initialState())) {
                iterator.remove();
                QUEUED_JOBS.remove(new JobKey(JobType.MINE, entry.getKey()));
                activeMineJobs = Math.max(0, activeMineJobs - 1);
            }
        }
    }

    private static boolean isClientReady(Minecraft client) {
        return client != null
                && client.level != null
                && client.player != null
                && client.gameMode != null
                && client.getConnection() != null;
    }

    private enum JobType {
        MINE,
        PLANT,
        USE
    }

    private record Job(JobType type, BlockPos pos, boolean directToInventory,
                       boolean quickShulkerOverflow,
                       BlockState initialState) {
    }

    private record JobKey(JobType type, BlockPos pos) {
    }

    private record PendingMine(BlockState initialState, long expiresAtTick) {
    }
}
