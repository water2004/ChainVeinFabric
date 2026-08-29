package org.edtp.chainveinfabric.client.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.edtp.chainveinfabric.Chainveinfabric;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.mixin.client.MultiPlayerGameModeAccessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * ChainVein 的客户端作业 API。
 *
 * <p>搜索逻辑和外部 Mod 都只向这里提交已经确定的坐标。API 统一负责作业排队、
 * 服务端协议批量发送，以及服务端未安装 ChainVein 时的原版客户端发包回退。</p>
 */
@Environment(EnvType.CLIENT)
public final class ChainVeinClientApi {
    public static final int MAX_QUEUED_JOBS = 10_000;

    private static final ArrayDeque<Job> INTERACTION_JOBS = new ArrayDeque<>();
    private static final ClientMineRequestQueue<Job> CLIENT_MINES = new ClientMineRequestQueue<>();

    private static ClientLevel queuedLevel;
    private static int tickCounter;
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
                && ChainveinfabricClient.CONFIG.quickShulkerOverflow;
        int protectionCapacity = getProtectedMineCapacity(client);
        int added;
        if (canUseServerProtocol(JobType.MINE)) {
            added = dispatchMineServerBatchImmediately(
                    positions, directToInventory, quickShulkerOverflow, protectionCapacity);
        } else {
            added = enqueuePrepared(
                    client, JobType.MINE, positions, directToInventory,
                    quickShulkerOverflow, protectionCapacity);
        }

        if (protectionCapacity < positions.size()) {
            client.gui.hud.setOverlayMessage(
                    Component.translatable("message.chainveinfabric.protection"), false);
        }
        return added;
    }

    private static int dispatchMineServerBatchImmediately(Collection<BlockPos> positions,
                                                           boolean directToInventory,
                                                           boolean quickShulkerOverflow,
                                                           int maxAdds) {
        if (maxAdds <= 0) return 0;

        List<BlockPos> batch = new ArrayList<>();
        int limit = Math.min(MAX_QUEUED_JOBS, maxAdds);
        for (BlockPos pos : positions) {
            if (batch.size() >= limit) break;
            if (pos != null) batch.add(pos.immutable());
        }

        if (!batch.isEmpty()) {
            ClientPlayNetworking.send(new Chainveinfabric.ChainMinePayload(
                    batch, directToInventory, quickShulkerOverflow));
        }
        return batch.size();
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
        return INTERACTION_JOBS.size() + CLIENT_MINES.pendingCount();
    }

    public static boolean hasPendingMineJobs() {
        return CLIENT_MINES.hasPendingWork();
    }

    public static boolean canUseServerMiningProtocol() {
        return canUseServerProtocol(JobType.MINE);
    }

    public static void tick(Minecraft client) {
        if (!isClientReady(client)) {
            clear(client);
            return;
        }

        if (queuedLevel != client.level) {
            clear(client);
            queuedLevel = client.level;
        }

        if (CLIENT_MINES.hasActiveItem()) {
            continueClientMineJob(client);
            return;
        }

        if (CLIENT_MINES.hasPendingWork()) {
            if (consumeVanillaDestroyDelay(client)) return;
            if (isDispatchDue()) {
                dispatchNextClientMine(client);
            }
            return;
        }

        if (INTERACTION_JOBS.isEmpty()) {
            tickCounter = 0;
            return;
        }

        int interval = ChainveinfabricClient.CONFIG != null
                ? ChainveinfabricClient.CONFIG.packetInterval
                : 0;
        if (interval <= 0) {
            while (!INTERACTION_JOBS.isEmpty()) {
                dispatchNextInteraction(client);
            }
            return;
        }

        if (interval >= 50) {
            int ticksPerDispatch = Math.max(1, interval / 50);
            if (++tickCounter >= ticksPerDispatch) {
                dispatchNextInteraction(client);
                tickCounter = 0;
            }
            return;
        }

        int dispatchesPerTick = Math.max(1, 50 / interval);
        for (int i = 0; i < dispatchesPerTick && !INTERACTION_JOBS.isEmpty(); i++) {
            dispatchNextInteraction(client);
        }
    }

    public static void clear() {
        clear(Minecraft.getInstance());
    }

    /** Cancels only vanilla client-side mining, leaving interaction jobs intact. */
    @ApiStatus.Internal
    public static void cancelClientMining(Minecraft client) {
        if (CLIENT_MINES.hasActiveItem() && client != null && client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
        }
        CLIENT_MINES.clear();
        tickCounter = 0;
    }

    private static void clear(Minecraft client) {
        cancelClientMining(client);
        INTERACTION_JOBS.clear();
        queuedLevel = null;
        tickCounter = 0;
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

        List<Job> prepared = type == JobType.MINE ? new ArrayList<>() : null;
        int added = 0;
        for (BlockPos pos : positions) {
            if (added >= maxAdds || added >= MAX_QUEUED_JOBS) break;
            if (type != JobType.MINE && INTERACTION_JOBS.size() >= MAX_QUEUED_JOBS) break;
            if (pos == null) continue;

            BlockPos immutablePos = pos.immutable();
            Job job = new Job(type, immutablePos, directToInventory, quickShulkerOverflow);
            if (type == JobType.MINE) {
                prepared.add(job);
            } else {
                INTERACTION_JOBS.addLast(job);
            }
            added++;
        }
        return type == JobType.MINE ? CLIENT_MINES.submit(prepared) : added;
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

    /**
     * 返回在当前工具保护设置下还能安全接收的挖掘作业数。
     *
     * <p>新的纯客户端请求会替代尚未开始的旧请求，因此这里只保留当前已经
     * 开始破坏的方块。它确实可能消耗一次耐久，不能被替代请求绕过。</p>
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
        int activeToolUse = CLIENT_MINES.hasActiveItem() ? 1 : 0;
        return Math.max(0, safeJobs - activeToolUse);
    }

    private static void dispatchNextInteraction(Minecraft client) {
        Job first = INTERACTION_JOBS.pollFirst();
        if (first == null) return;

        if (canUseServerProtocol(first.type())) {
            List<Job> batch = new ArrayList<>();
            batch.add(first);

            while (!INTERACTION_JOBS.isEmpty()) {
                Job next = INTERACTION_JOBS.peekFirst();
                if (next == null
                        || next.type() != first.type()
                        || next.directToInventory() != first.directToInventory()
                        || next.quickShulkerOverflow() != first.quickShulkerOverflow()) {
                    break;
                }
                batch.add(INTERACTION_JOBS.pollFirst());
            }

            dispatchServerBatch(first, batch.stream().map(Job::pos).toList());
            return;
        }

        dispatchClientInteraction(client, first);
    }

    private static boolean isDispatchDue() {
        int interval = ChainveinfabricClient.CONFIG != null
                ? ChainveinfabricClient.CONFIG.packetInterval
                : 0;
        if (interval <= 0 || interval < 50) return true;

        int ticksPerDispatch = Math.max(1, interval / 50);
        if (++tickCounter < ticksPerDispatch) return false;
        tickCounter = 0;
        return true;
    }

    /**
     * Vanilla advances its post-break delay through continueDestroyBlock.
     * Starting the next target directly would bypass that state entirely.
     */
    private static boolean consumeVanillaDestroyDelay(Minecraft client) {
        MultiPlayerGameModeAccessor accessor =
                (MultiPlayerGameModeAccessor) client.gameMode;
        if (accessor.chainveinfabric$getDestroyDelay() <= 0) return false;

        Job next = CLIENT_MINES.nextItem();
        if (next == null) return false;

        dispatching = true;
        try {
            client.gameMode.continueDestroyBlock(next.pos(), Direction.UP);
        } finally {
            dispatching = false;
        }
        return true;
    }

    private static boolean canUseServerProtocol(JobType type) {
        return switch (type) {
            case MINE -> ClientPlayNetworking.canSend(Chainveinfabric.ChainMinePayload.ID);
            case PLANT, USE -> ClientPlayNetworking.canSend(Chainveinfabric.ChainInteractPayload.ID);
        };
    }

    private static void dispatchServerBatch(Job first, List<BlockPos> positions) {
        if (first.type() == JobType.MINE) {
            ClientPlayNetworking.send(new Chainveinfabric.ChainMinePayload(
                    positions, first.directToInventory(), first.quickShulkerOverflow()));
        } else {
            ClientPlayNetworking.send(new Chainveinfabric.ChainInteractPayload(positions));
        }
    }

    private static void dispatchNextClientMine(Minecraft client) {
        Job job = CLIENT_MINES.startNext();
        if (job == null) return;

        dispatching = true;
        try {
            if (!client.level.getBlockState(job.pos()).isAir()
                    && client.gameMode.startDestroyBlock(job.pos(), Direction.UP)
                    && !client.level.getBlockState(job.pos()).isAir()) {
                return;
            }

            CLIENT_MINES.completeActive();
        } finally {
            dispatching = false;
        }
    }

    private static void dispatchClientInteraction(Minecraft client, Job job) {
        dispatching = true;
        try {
            client.gameMode.useItemOn(
                    client.player,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(job.pos()), Direction.UP, job.pos(), false));
        } finally {
            dispatching = false;
        }
    }

    private static void continueClientMineJob(Minecraft client) {
        Job job = CLIENT_MINES.activeItem();
        if (job == null) return;

        if (client.level.getBlockState(job.pos()).isAir()) {
            finishClientMineJob();
            return;
        }

        dispatching = true;
        try {
            boolean continuing = client.gameMode.continueDestroyBlock(job.pos(), Direction.UP);
            if (!continuing) {
                client.gameMode.stopDestroyBlock();
                finishClientMineJob();
            } else if (client.level.getBlockState(job.pos()).isAir()) {
                finishClientMineJob();
            }
        } finally {
            dispatching = false;
        }
    }

    private static void finishClientMineJob() {
        CLIENT_MINES.completeActive();
    }

    /** Read-only HUD state for the vanilla client-side mining fallback. */
    @ApiStatus.Internal
    public static @Nullable ClientMiningProgress getClientMiningProgress(Minecraft client) {
        if (client == null || canUseServerMiningProtocol()) return null;

        ClientMineRequestQueue.Snapshot snapshot = CLIENT_MINES.snapshot();
        if (snapshot == null) return null;

        float blockProgress = 0.0F;
        if (snapshot.activelyMining() && client.gameMode != null) {
            int destroyStage = client.gameMode.getDestroyStage();
            blockProgress = Math.max(0.0F, Math.min(1.0F, (destroyStage + 1) / 10.0F));
        }
        return new ClientMiningProgress(snapshot.current(), snapshot.total(), blockProgress);
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
                       boolean quickShulkerOverflow) {
    }

    @ApiStatus.Internal
    public record ClientMiningProgress(int currentBlock, int totalBlocks,
                                       float currentBlockProgress) {
    }

}
