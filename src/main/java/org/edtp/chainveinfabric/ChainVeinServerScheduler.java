package org.edtp.chainveinfabric;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.edtp.chainveinfabric.server.ChainVeinServerConfig;

/**
 * Holds at most one active and one same-tick replacement request per player.
 * Active requests share one global position budget at the end of every tick.
 */
final class ChainVeinServerScheduler {
    private static final Map<UUID, ServerJob> ACTIVE_JOBS = new LinkedHashMap<>();
    private static final Map<UUID, ServerJob> RECEIVED_THIS_TICK = new LinkedHashMap<>();
    private static int fairnessStart;

    private ChainVeinServerScheduler() {
    }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ChainVeinServerScheduler::endServerTick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
    }

    static boolean submitMine(
            ServerPlayer player,
            List<BlockPos> positions,
            boolean directToInventory,
            boolean quickShulkerOverflow) {
        return receive(new MineJob(
                player,
                copyRequestPositions(positions),
                directToInventory,
                quickShulkerOverflow,
                player.getMainHandItem().isEmpty()));
    }

    static boolean submitInteract(ServerPlayer player, List<BlockPos> positions) {
        return receive(new InteractJob(player, copyRequestPositions(positions)));
    }

    private static boolean receive(ServerJob job) {
        return RECEIVED_THIS_TICK.putIfAbsent(job.playerId(), job) == null;
    }

    private static List<BlockPos> copyRequestPositions(List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) return List.of();
        int size = Math.min(
                positions.size(), ChainVeinServerConfig.MAX_REQUEST_POSITIONS);
        return List.copyOf(positions.subList(0, size));
    }

    private static void endServerTick(MinecraftServer server) {
        processActiveJobs(server);
        promoteReceivedJobs(server);
    }

    private static void processActiveJobs(MinecraftServer server) {
        ACTIVE_JOBS.values().removeIf(job -> !job.isRunnable(server));
        if (ACTIVE_JOBS.isEmpty()) {
            fairnessStart = 0;
            return;
        }

        List<ServerJob> jobs = new ArrayList<>(ACTIVE_JOBS.values());
        int[] demands = jobs.stream().mapToInt(ServerJob::remainingPositions).toArray();
        int[] shares = allocateFairShares(
                demands,
                ChainVeinServerConfig.values().maxBlocks(),
                fairnessStart);

        for (int index = 0; index < jobs.size(); index++) {
            if (shares[index] > 0) jobs.get(index).process(shares[index]);
        }

        ACTIVE_JOBS.values().removeIf(job -> !job.isRunnable(server));
        fairnessStart = ACTIVE_JOBS.isEmpty()
                ? 0
                : (fairnessStart + 1) % ACTIVE_JOBS.size();
    }

    private static void promoteReceivedJobs(MinecraftServer server) {
        for (Map.Entry<UUID, ServerJob> entry : RECEIVED_THIS_TICK.entrySet()) {
            UUID playerId = entry.getKey();
            ServerJob replacement = entry.getValue();
            ACTIVE_JOBS.remove(playerId);
            if (replacement.isRunnable(server)) {
                ACTIVE_JOBS.put(playerId, replacement);
            }
        }
        RECEIVED_THIS_TICK.clear();
    }

    /** Max-min fair allocation with rotating priority for indivisible remainders. */
    static int[] allocateFairShares(int[] demands, int budget, int startIndex) {
        int[] shares = new int[demands.length];
        if (demands.length == 0 || budget <= 0) return shares;

        List<Integer> candidates = new ArrayList<>(demands.length);
        int normalizedStart = Math.floorMod(startIndex, demands.length);
        for (int offset = 0; offset < demands.length; offset++) {
            int index = (normalizedStart + offset) % demands.length;
            if (demands[index] > 0) candidates.add(index);
        }

        int remainingBudget = budget;
        while (remainingBudget > 0 && !candidates.isEmpty()) {
            int evenShare = remainingBudget / candidates.size();
            if (evenShare == 0) {
                for (int index = 0; index < remainingBudget; index++) {
                    shares[candidates.get(index)]++;
                }
                break;
            }

            List<Integer> stillDemanding = new ArrayList<>(candidates.size());
            for (int index : candidates) {
                int unmetDemand = demands[index] - shares[index];
                int allocation = Math.min(unmetDemand, evenShare);
                shares[index] += allocation;
                remainingBudget -= allocation;
                if (allocation < unmetDemand) stillDemanding.add(index);
            }
            candidates = stillDemanding;
        }
        return shares;
    }

    static void runEndTickForTests(MinecraftServer server) {
        endServerTick(server);
    }

    static void clearForTests() {
        clear();
    }

    private static void clear() {
        ACTIVE_JOBS.clear();
        RECEIVED_THIS_TICK.clear();
        fairnessStart = 0;
    }

    private abstract static class ServerJob {
        private final ServerPlayer player;
        private final ServerLevel level;
        private final List<BlockPos> positions;
        private int nextPosition;
        private boolean stopped;

        private ServerJob(ServerPlayer player, List<BlockPos> positions) {
            this.player = player;
            this.level = (ServerLevel) player.level();
            this.positions = positions;
        }

        private UUID playerId() {
            return player.getUUID();
        }

        private int remainingPositions() {
            return stopped ? 0 : positions.size() - nextPosition;
        }

        private boolean isRunnable(MinecraftServer server) {
            return !stopped
                    && nextPosition < positions.size()
                    && !player.isRemoved()
                    && level.getServer() == server
                    && player.level() == level;
        }

        private void process(int positionLimit) {
            int end = Math.min(positions.size(), nextPosition + positionLimit);
            List<BlockPos> slice = positions.subList(nextPosition, end);
            nextPosition = end;
            if (!execute(slice)) stopped = true;
        }

        protected final ServerPlayer player() {
            return player;
        }

        protected abstract boolean execute(List<BlockPos> positions);
    }

    private static final class MineJob extends ServerJob {
        private final boolean directToInventory;
        private final boolean quickShulkerOverflow;
        private final boolean startedWithEmptyHand;

        private MineJob(
                ServerPlayer player,
                List<BlockPos> positions,
                boolean directToInventory,
                boolean quickShulkerOverflow,
                boolean startedWithEmptyHand) {
            super(player, positions);
            this.directToInventory = directToInventory;
            this.quickShulkerOverflow = quickShulkerOverflow;
            this.startedWithEmptyHand = startedWithEmptyHand;
        }

        @Override
        protected boolean execute(List<BlockPos> positions) {
            return ChainVeinServerPacketHandler.executeMineSlice(
                    player(), positions, directToInventory,
                    quickShulkerOverflow, startedWithEmptyHand);
        }
    }

    private static final class InteractJob extends ServerJob {
        private InteractJob(ServerPlayer player, List<BlockPos> positions) {
            super(player, positions);
        }

        @Override
        protected boolean execute(List<BlockPos> positions) {
            return ChainVeinServerPacketHandler.executeInteractSlice(
                    player(), positions);
        }
    }
}
