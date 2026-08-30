package org.edtp.chainveinfabric.client.logic;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.api.ChainVeinClientApi;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaContext;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaIntegration;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.logic.search.SearchConfig;
import org.edtp.chainveinfabric.client.logic.search.SearchRequest;
import org.edtp.chainveinfabric.client.logic.search.SearchResult;
import org.edtp.chainveinfabric.client.logic.search.SearchService;

public final class MineLogic {
    private static PendingServerMine pendingServerMine;
    private static int pendingServerMineDelay;

    private MineLogic() {
    }

    /**
     * Sends a deferred continuation only after crossing a complete client tick.
     * This keeps the clicked block and its asynchronously discovered remainder
     * out of the same server tick while preserving direct pickup for the origin.
     */
    public static void tick(Minecraft client) {
        PendingServerMine pending = pendingServerMine;
        if (pending == null) return;

        if (client.level != pending.level() || client.player == null) {
            clearPendingServerMine();
            return;
        }
        if (pendingServerMineDelay-- > 0) return;

        clearPendingServerMine();
        int queuedCount = ChainVeinClientApi.queueMineJobs(client, pending.positions());
        if (queuedCount == pending.positions().size() && pending.affectedCount() > 1) {
            client.gui.hud.setOverlayMessage(
                    Component.translatable(
                            "message.chainveinfabric.broken", pending.affectedCount()),
                    false);
        }
    }

    public static void perform(Minecraft client, BlockPos pos, BlockState targetState) {
        performAndClaimOrigin(client, pos, targetState);
    }

    /**
     * Starts a chain search and returns whether the server protocol took
     * ownership of the clicked block. Callers must suppress vanilla's second
     * destroy path when this returns true.
     */
    public static boolean performAndClaimOrigin(
            Minecraft client, BlockPos pos, BlockState targetState) {
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        if (config == null || client.level == null || client.player == null
                || !config.mode.isManualMiningMode()
                || ChainveinfabricClient.isAutoMiningArmed()) return false;

        SearchConfig searchConfig = SearchConfig.from(config);
        LitematicaContext litematicaContext = LitematicaIntegration.createContext(
                config.mode, config.respectSchematicRenderLayer);
        String targetId = ChainVeinConfig.getWhitelistItemId(targetState.getBlock());
        if (targetId == null || !searchConfig.whitelist().contains(targetId)
                || (config.mode.isSchematicMode()
                    && !litematicaContext.matches(client.level, pos))
                || (!client.player.isCreative()
                    && targetState.getDestroySpeed(client.level, pos) < 0.0F)) {
            return false;
        }
        boolean serverHandledOrigin = ChainVeinClientApi.canUseServerMiningProtocol()
                && ChainVeinClientApi.queueMineJobs(client, List.of(pos)) == 1;
        SearchRequest request = SearchRequest.targeted(
                (ClientLevel) client.level, pos, targetState, client.player.getDirection(),
                searchConfig, litematicaContext, client.player.isCreative());

        ChainveinfabricClient.getSearchService().submit(
                request, SearchService.Priority.ACTION, result -> result,
                result -> applyResult(client, result, serverHandledOrigin));
        return serverHandledOrigin;
    }

    private static void applyResult(
            Minecraft client,
            SearchResult result,
            boolean serverHandledOrigin) {
        if (!isStillValid(client, result.request())) return;

        List<BlockPos> remaining = result.positions().stream()
                .filter(pos -> !pos.equals(result.request().origin()))
                .toList();
        int affectedCount = remaining.size() + 1;
        if (serverHandledOrigin) {
            pendingServerMine = new PendingServerMine(
                    result.request().level(), remaining, affectedCount);
            pendingServerMineDelay = 1;
            return;
        }

        int queuedCount = ChainVeinClientApi.queueMineJobs(client, remaining);
        if (queuedCount == remaining.size() && affectedCount > 1) {
            client.gui.setOverlayMessage(
                    Component.translatable("message.chainveinfabric.broken", affectedCount), false);
        }
    }

    private static boolean isStillValid(Minecraft client, SearchRequest request) {
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        if (config == null || client.level != request.level() || client.player == null
                || !config.isChainVeinEnabled || !config.mode.isManualMiningMode()
                || ChainveinfabricClient.isAutoMiningArmed()
                || !SearchConfig.from(config).equals(request.config())) return false;

        LitematicaContext currentContext = LitematicaIntegration.createContext(
                config.mode, config.respectSchematicRenderLayer);
        return currentContext.fingerprint() == request.litematicaContext().fingerprint();
    }

    private static void clearPendingServerMine() {
        pendingServerMine = null;
        pendingServerMineDelay = 0;
    }

    private record PendingServerMine(
            ClientLevel level, List<BlockPos> positions, int affectedCount) {
        private PendingServerMine {
            positions = List.copyOf(positions);
        }
    }
}
