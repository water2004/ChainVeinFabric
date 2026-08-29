package org.edtp.chainveinfabric.client.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * ChainVein's stable client job API.
 *
 * <p>Search logic and external mods submit resolved positions here. Protocol
 * selection and the vanilla client fallback remain private implementation details.</p>
 */
@Environment(EnvType.CLIENT)
public final class ChainVeinClientApi {
    public static final int MAX_QUEUED_JOBS = 10_000;

    private ChainVeinClientApi() {
    }

    public static int queueMineJobs(Minecraft client, Collection<BlockPos> positions) {
        return ClientJobDispatcher.queueMineJobs(client, positions);
    }

    public static int queuePlantJobs(Minecraft client, Collection<BlockPos> positions) {
        return ClientJobDispatcher.queuePlantJobs(client, positions);
    }

    public static int queueUseJobs(Minecraft client, Collection<BlockPos> positions) {
        return ClientJobDispatcher.queueUseJobs(client, positions);
    }

    public static boolean isDispatching() {
        return ClientJobDispatcher.isDispatching();
    }

    public static int queuedJobCount() {
        return ClientJobDispatcher.queuedJobCount();
    }

    public static boolean hasPendingMineJobs() {
        return ClientJobDispatcher.hasPendingMineJobs();
    }

    public static boolean canUseServerMiningProtocol() {
        return ClientJobDispatcher.canUseServerMiningProtocol();
    }

    public static void tick(Minecraft client) {
        ClientJobDispatcher.tick(client);
    }

    public static void clear() {
        ClientJobDispatcher.clear();
    }

    /** Cancels only vanilla client-side mining, leaving interaction jobs intact. */
    @ApiStatus.Internal
    public static void cancelClientMining(Minecraft client) {
        ClientJobDispatcher.cancelClientMining(client);
    }

    /** Read-only HUD state for the vanilla client-side mining fallback. */
    @ApiStatus.Internal
    public static @Nullable ClientMiningProgress getClientMiningProgress(Minecraft client) {
        return ClientJobDispatcher.getClientMiningProgress(client);
    }

    @ApiStatus.Internal
    public record ClientMiningProgress(int currentBlock, int totalBlocks,
                                       float currentBlockProgress) {
    }
}
