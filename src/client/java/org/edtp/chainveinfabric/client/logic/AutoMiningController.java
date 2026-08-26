package org.edtp.chainveinfabric.client.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.api.ChainVeinClientApi;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.logic.search.SearchConfig;
import org.edtp.chainveinfabric.client.logic.search.SearchRequest;
import org.edtp.chainveinfabric.client.logic.search.SearchResult;
import org.edtp.chainveinfabric.client.logic.search.SearchService;

/** Drives single-flight, player-centered automatic mining. */
public final class AutoMiningController {
    private static final int WORLD_UPDATE_TIMEOUT_TICKS = 40;
    private static final int EMPTY_RETRY_TICKS = 10;

    private final SearchService searchService;
    private final Set<BlockPos> awaitingWorldUpdates = new LinkedHashSet<>();
    private SearchIdentity identity;
    private long generation;
    private boolean searchInFlight;
    private boolean adjacentWarningShown;
    private int awaitingTicks;
    private int retryCooldown;

    public AutoMiningController(SearchService searchService) {
        this.searchService = searchService;
    }

    public void tick(Minecraft client) {
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        if (!isActive(client, config)) {
            reset();
            return;
        }

        SearchConfig searchConfig = SearchConfig.from(config);
        BlockPos origin = client.player.blockPosition();
        Direction facing = client.player.getDirection();
        SearchIdentity current = new SearchIdentity(
                (ClientLevel) client.level, origin, facing, searchConfig, client.player.isCreative());
        if (!current.equals(this.identity)) {
            invalidate(current);
        }

        if (searchConfig.searchAlgorithm() == ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME) {
            if (!this.adjacentWarningShown && client.gui != null) {
                client.gui.setOverlayMessage(
                        Component.translatable("message.chainveinfabric.autoMine.adjacentSameUnavailable"), false);
                this.adjacentWarningShown = true;
            }
            return;
        }
        this.adjacentWarningShown = false;

        discardCompletedPositions(client, searchConfig.whitelist());
        if (!this.awaitingWorldUpdates.isEmpty()) {
            if (++this.awaitingTicks < WORLD_UPDATE_TIMEOUT_TICKS) return;
            this.awaitingWorldUpdates.clear();
            this.awaitingTicks = 0;
        }
        if (this.searchInFlight || ChainVeinClientApi.hasPendingMineJobs()) return;
        if (this.retryCooldown > 0) {
            this.retryCooldown--;
            return;
        }

        long requestGeneration = this.generation;
        SearchRequest request = SearchRequest.automatic(
                (ClientLevel) client.level, origin, facing, searchConfig, client.player.isCreative());
        this.searchInFlight = true;
        this.searchService.submit(
                request, SearchService.Priority.AUTO, result -> result,
                result -> acceptResult(client, result, requestGeneration));
    }

    private void acceptResult(Minecraft client, SearchResult result, long requestGeneration) {
        if (requestGeneration != this.generation || this.identity == null
                || client.level != result.request().level()) return;
        this.searchInFlight = false;

        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        if (!isActive(client, config)
                || !SearchConfig.from(config).equals(result.request().config())
                || !client.player.blockPosition().equals(result.request().origin())) return;

        List<BlockPos> reachable = filterReachable(client, result.positions());
        if (reachable.isEmpty()) {
            this.retryCooldown = EMPTY_RETRY_TICKS;
            return;
        }

        int added = ChainVeinClientApi.queueMineJobs(client, reachable);
        if (added <= 0) {
            this.retryCooldown = EMPTY_RETRY_TICKS;
            return;
        }

        for (int i = 0; i < added && i < reachable.size(); i++) {
            this.awaitingWorldUpdates.add(reachable.get(i));
        }
        this.awaitingTicks = 0;
        if (added > 1 && client.gui != null) {
            client.gui.setOverlayMessage(
                    Component.translatable("message.chainveinfabric.broken", added), false);
        }
    }

    private static List<BlockPos> filterReachable(Minecraft client, List<BlockPos> positions) {
        double range = ChainVeinClientApi.canUseServerMiningProtocol()
                ? 10.0
                : client.player.blockInteractionRange();
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

    private void discardCompletedPositions(Minecraft client, Set<String> whitelist) {
        this.awaitingWorldUpdates.removeIf(pos -> {
            String id = ChainVeinConfig.getWhitelistItemId(client.level.getBlockState(pos).getBlock());
            return id == null || !whitelist.contains(id);
        });
        if (this.awaitingWorldUpdates.isEmpty()) this.awaitingTicks = 0;
    }

    private static boolean isActive(Minecraft client, ChainVeinConfig config) {
        return config != null
                && config.isChainVeinEnabled
                && config.mode == ChainVeinConfig.ChainMode.AUTO_MINE
                && client.level != null
                && client.player != null
                && client.gameMode != null
                && !client.player.isSpectator();
    }

    private void invalidate(SearchIdentity newIdentity) {
        this.identity = newIdentity;
        this.generation++;
        this.searchInFlight = false;
        this.awaitingWorldUpdates.clear();
        this.awaitingTicks = 0;
        this.retryCooldown = 0;
        this.adjacentWarningShown = false;
    }

    private void reset() {
        if (this.identity == null && !this.searchInFlight && this.awaitingWorldUpdates.isEmpty()) return;
        invalidate(null);
    }

    private record SearchIdentity(ClientLevel level, BlockPos origin, Direction facing,
                                  SearchConfig config, boolean creative) {
    }
}
