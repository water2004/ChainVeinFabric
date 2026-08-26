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
    private MineLogic() {
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
                || !config.mode.isManualMiningMode()) return false;

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
        boolean serverHandledOrigin = false;
        if (ChainVeinClientApi.canUseServerMiningProtocol()) {
            serverHandledOrigin = ChainVeinClientApi.queueMineJobs(client, List.of(pos)) == 1;
        }
        SearchRequest request = SearchRequest.targeted(
                (ClientLevel) client.level, pos, targetState, client.player.getDirection(),
                searchConfig, litematicaContext, client.player.isCreative());

        ChainveinfabricClient.getSearchService().submit(
                request, SearchService.Priority.ACTION, result -> result,
                result -> applyResult(client, result));
        return serverHandledOrigin;
    }

    private static void applyResult(Minecraft client, SearchResult result) {
        if (!isStillValid(client, result.request())) return;

        List<BlockPos> remaining = result.positions().stream()
                .filter(pos -> !pos.equals(result.request().origin()))
                .toList();
        int affectedCount = remaining.size() + 1;
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
                || !SearchConfig.from(config).equals(request.config())) return false;

        LitematicaContext currentContext = LitematicaIntegration.createContext(
                config.mode, config.respectSchematicRenderLayer);
        return currentContext.fingerprint() == request.litematicaContext().fingerprint();
    }
}
