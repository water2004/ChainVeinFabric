package org.edtp.chainveinfabric.client.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

/**
 * Guards the opt-in automatic-mining state and runs at most one search-and-mine
 * batch for each accepted attack press.
 */
public final class AutoMiningController {
    private static final int WORLD_UPDATE_TIMEOUT_TICKS = 40;

    public enum Status {
        INACTIVE,
        ARMED,
        RUNNING,
        COOLDOWN
    }

    private final SearchService searchService;
    private final Map<BlockPos, BlockState> awaitingWorldUpdates = new LinkedHashMap<>();
    private ClientLevel armedLevel;
    private ChainVeinConfig.ChainMode armedMode;
    private SearchRequest activeRequest;
    private long generation;
    private boolean armed;
    private boolean batchActive;
    private boolean searchInFlight;
    private int awaitingTicks;
    private int cooldownTicks;

    public AutoMiningController(SearchService searchService) {
        this.searchService = searchService;
    }

    public boolean toggle(Minecraft client) {
        if (this.armed) {
            this.disarm(client, true);
            return false;
        }

        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        if (!canArm(client, config)) {
            showOverlay(client, "message.chainveinfabric.autoMine.unavailable");
            return false;
        }

        config.isChainVeinEnabled = true;
        this.generation++;
        this.armed = true;
        this.armedLevel = (ClientLevel) client.level;
        this.armedMode = config.mode;
        this.clearBatch();
        this.cooldownTicks = 0;
        showOverlay(client, "message.chainveinfabric.autoMine.armed");
        return true;
    }

    public void disarm(Minecraft client) {
        this.disarm(client, false);
    }

    public boolean handleAttack(Minecraft client) {
        if (!this.armed) return false;
        if (!this.canRemainArmed(client)) {
            this.disarm(client, false);
            return false;
        }

        this.trigger(client);
        return true;
    }

    public void tick(Minecraft client) {
        if (!this.armed) return;
        if (!this.canRemainArmed(client)) {
            this.disarm(client, false);
            return;
        }

        if (!this.batchActive) {
            if (this.cooldownTicks > 0) this.cooldownTicks--;
            return;
        }
        if (this.searchInFlight) return;

        this.discardCompletedPositions(client);
        if (ChainVeinClientApi.hasPendingMineJobs()) return;

        if (this.awaitingWorldUpdates.isEmpty()) {
            this.finishBatch();
            return;
        }

        if (++this.awaitingTicks >= WORLD_UPDATE_TIMEOUT_TICKS) {
            this.finishBatch();
        }
    }

    public boolean isArmed() {
        return this.armed;
    }

    public Status status() {
        if (!this.armed) return Status.INACTIVE;
        if (this.batchActive) return Status.RUNNING;
        if (this.cooldownTicks > 0) return Status.COOLDOWN;
        return Status.ARMED;
    }

    public int cooldownTicksRemaining() {
        return this.cooldownTicks;
    }

    private void trigger(Minecraft client) {
        if (this.batchActive || this.cooldownTicks > 0
                || ChainVeinClientApi.hasPendingMineJobs()) {
            return;
        }

        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        SearchConfig searchConfig = SearchConfig.from(config);
        boolean adjacentSame = searchConfig.searchAlgorithm()
                == ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME;
        BlockPos origin = adjacentSame
                ? client.player.blockPosition().below()
                : client.player.blockPosition();
        BlockState targetState = adjacentSame ? client.level.getBlockState(origin) : null;
        LitematicaContext litematicaContext = LitematicaIntegration.createContext(
                config.mode, config.respectSchematicRenderLayer);
        SearchRequest request = SearchRequest.automatic(
                (ClientLevel) client.level,
                origin,
                targetState,
                client.player.getDirection(),
                searchConfig,
                litematicaContext,
                client.player.isCreative());

        long requestGeneration = this.generation;
        this.activeRequest = request;
        this.batchActive = true;
        this.searchInFlight = true;
        this.awaitingWorldUpdates.clear();
        this.awaitingTicks = 0;
        this.searchService.submit(
                request,
                SearchService.Priority.ACTION,
                result -> result,
                result -> this.acceptResult(client, result, requestGeneration));
    }

    private void acceptResult(Minecraft client, SearchResult result, long requestGeneration) {
        if (requestGeneration != this.generation || !this.armed) return;
        this.searchInFlight = false;

        if (!this.isStillValid(client, result.request())) {
            this.finishBatch();
            return;
        }

        List<BlockPos> reachable = filterReachable(client, result.positions());
        if (reachable.isEmpty()) {
            this.finishBatch();
            return;
        }

        List<BlockState> originalStates = new ArrayList<>(reachable.size());
        for (BlockPos pos : reachable) {
            originalStates.add(client.level.getBlockState(pos));
        }
        int added = ChainVeinClientApi.queueMineJobs(client, reachable);
        if (added <= 0) {
            this.finishBatch();
            return;
        }

        for (int i = 0; i < added && i < reachable.size(); i++) {
            this.awaitingWorldUpdates.put(reachable.get(i), originalStates.get(i));
        }
        this.awaitingTicks = 0;
        if (added > 1 && client.gui != null) {
            client.gui.hud.setOverlayMessage(
                    Component.translatable("message.chainveinfabric.broken", added), false);
        }
    }

    private boolean isStillValid(Minecraft client, SearchRequest request) {
        if (!this.canRemainArmed(client) || client.level != request.level()
                || this.activeRequest != request
                || !SearchConfig.from(ChainveinfabricClient.CONFIG).equals(request.config())) {
            return false;
        }

        LitematicaContext currentContext = LitematicaIntegration.createContext(
                ChainveinfabricClient.CONFIG.mode,
                ChainveinfabricClient.CONFIG.respectSchematicRenderLayer);
        return currentContext.fingerprint() == request.litematicaContext().fingerprint();
    }

    private static List<BlockPos> filterReachable(Minecraft client, List<BlockPos> positions) {
        if (ChainVeinClientApi.canUseServerMiningProtocol()) return positions;

        double range = client.player.blockInteractionRange();
        double maxDistanceSqr = range * range;
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (client.player.distanceToSqr(
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= maxDistanceSqr) {
                result.add(pos);
            }
        }
        return result;
    }

    private void discardCompletedPositions(Minecraft client) {
        this.awaitingWorldUpdates.entrySet().removeIf(entry ->
                !client.level.getBlockState(entry.getKey()).equals(entry.getValue()));
        if (this.awaitingWorldUpdates.isEmpty()) this.awaitingTicks = 0;
    }

    private boolean canRemainArmed(Minecraft client) {
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        return config != null
                && config.isChainVeinEnabled
                && canArm(client, config)
                && client.level == this.armedLevel
                && config.mode == this.armedMode;
    }

    private static boolean canArm(Minecraft client, ChainVeinConfig config) {
        return config != null
                && config.mode.isMiningMode()
                && client.level != null
                && client.player != null
                && client.gameMode != null
                && !client.player.isSpectator();
    }

    private void finishBatch() {
        this.clearBatch();
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        this.cooldownTicks = config != null
                ? Math.max(10, Math.min(200, config.autoMineCooldownTicks))
                : 40;
    }

    private void clearBatch() {
        this.activeRequest = null;
        this.batchActive = false;
        this.searchInFlight = false;
        this.awaitingWorldUpdates.clear();
        this.awaitingTicks = 0;
    }

    private void disarm(Minecraft client, boolean notify) {
        if (!this.armed) return;
        this.generation++;
        this.armed = false;
        this.armedLevel = null;
        this.armedMode = null;
        this.clearBatch();
        this.cooldownTicks = 0;
        if (notify) showOverlay(client, "message.chainveinfabric.autoMine.disarmed");
    }

    private static void showOverlay(Minecraft client, String translationKey) {
        if (client != null && client.gui != null) {
            client.gui.hud.setOverlayMessage(Component.translatable(translationKey), false);
        }
    }
}
