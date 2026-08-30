package org.edtp.chainveinfabric;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.edtp.chainveinfabric.server.ChainVeinServerConfig;
import org.edtp.chainveinfabric.server.DirectDropCollector;

import java.util.List;

/** Owns serverbound packet registration, validation, and execution. */
final class ChainVeinServerPacketHandler {
    private ChainVeinServerPacketHandler() {
    }

    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(
                Chainveinfabric.ChainMinePayload.ID,
                Chainveinfabric.ChainMinePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                Chainveinfabric.ChainInteractPayload.ID,
                Chainveinfabric.ChainInteractPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(
                Chainveinfabric.ChainMinePayload.ID,
                (payload, context) -> ChainVeinServerScheduler.submitMine(
                        context.player(), payload.positions(), payload.directToInventory(),
                        payload.quickShulkerOverflow()));
        ServerPlayNetworking.registerGlobalReceiver(
                Chainveinfabric.ChainInteractPayload.ID,
                (payload, context) -> ChainVeinServerScheduler.submitInteract(
                        context.player(), payload.positions()));
    }

    static void handleInteract(ServerPlayer player, List<BlockPos> positions) {
        executeInteractSlice(player, firstRequestPositions(positions));
    }

    static boolean executeInteractSlice(ServerPlayer player, List<BlockPos> positions) {
        ServerLevel world = (ServerLevel) player.level();
        if (player.getMainHandItem().isEmpty()) return false;

        boolean isCreative = player.isCreative();

        for (BlockPos pos : positions) {
            if (!player.isWithinBlockInteractionRange(pos, 1.0)) continue;
            if (!world.isLoaded(pos)) continue;
            if (world.getServer().isUnderSpawnProtection(world, pos, player)
                    || !world.mayInteract(player, pos)) continue;
            if (!isCreative && player.getMainHandItem().isEmpty()) return false;

            player.gameMode.useItemOn(
                    player,
                    world,
                    player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos),
                            net.minecraft.core.Direction.UP, pos, false));
        }
        return true;
    }

    static void handleMine(ServerPlayer player, List<BlockPos> positions,
                           boolean directToInventory, boolean quickShulkerOverflow) {
        boolean startedWithEmptyHand = player.getMainHandItem().isEmpty();
        executeMineSlice(
                player, firstRequestPositions(positions), directToInventory,
                quickShulkerOverflow, startedWithEmptyHand);
    }

    static boolean executeMineSlice(
            ServerPlayer player,
            List<BlockPos> positions,
            boolean directToInventory,
            boolean quickShulkerOverflow,
            boolean startedWithEmptyHand) {
        ServerLevel world = (ServerLevel) player.level();
        boolean isCreative = player.isCreative();
        ChainVeinServerConfig.Values limits = ChainVeinServerConfig.values();

        if (directToInventory && !isCreative) {
            return DirectDropCollector.run(
                    player,
                    quickShulkerOverflow,
                    limits.pickupRadius(),
                    () -> minePositions(
                            player, world, positions, false, startedWithEmptyHand));
        }

        return minePositions(player, world, positions, isCreative, startedWithEmptyHand);
    }

    private static boolean minePositions(ServerPlayer player,
                                         ServerLevel world,
                                         List<BlockPos> positions,
                                         boolean isCreative,
                                         boolean startedWithEmptyHand) {
        for (BlockPos pos : positions) {
            if (!world.isLoaded(pos)) continue;
            if (!isCreative && !startedWithEmptyHand && player.getMainHandItem().isEmpty()) {
                return false;
            }

            BlockState state = world.getBlockState(pos);
            if (state.isAir()) continue;

            DirectDropCollector.withDropOrigin(
                    pos, () -> player.gameMode.destroyBlock(pos));
        }
        return true;
    }

    private static List<BlockPos> firstRequestPositions(List<BlockPos> positions) {
        int limit = ChainVeinServerConfig.MAX_REQUEST_POSITIONS;
        return positions.size() <= limit ? positions : positions.subList(0, limit);
    }
}
