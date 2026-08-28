package org.edtp.chainveinfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.edtp.chainveinfabric.server.ChainVeinServerCommands;
import org.edtp.chainveinfabric.server.ChainVeinServerConfig;
import org.edtp.chainveinfabric.server.DirectDropCollector;

import java.util.List;

public class Chainveinfabric implements ModInitializer {

    public static final Identifier MINE_PACKET_ID = Identifier.fromNamespaceAndPath("chainveinfabric", "mine_v4");
    public static final Identifier INTERACT_PACKET_ID = Identifier.fromNamespaceAndPath("chainveinfabric", "interact_v4");

    @Override
    public void onInitialize() {
        ChainVeinServerCommands.register();

        // Register Payloads
        PayloadTypeRegistry.serverboundPlay().register(ChainMinePayload.ID, ChainMinePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ChainInteractPayload.ID, ChainInteractPayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> ChainVeinServerConfig.load());
        
        // Register Mine Receiver
        ServerPlayNetworking.registerGlobalReceiver(ChainMinePayload.ID, (payload, context) -> {
            context.server().execute(() -> handleMine(
                    context.player(), payload.positions(), payload.directToInventory(),
                    payload.quickShulkerOverflow()));
        });

        // Register Interact Receiver (Handles Planting, Waxing, Stripping, etc.)
        ServerPlayNetworking.registerGlobalReceiver(ChainInteractPayload.ID, (payload, context) -> {
            context.server().execute(() -> handleInteract(context.player(), payload.positions()));
        });
    }

    static void handleInteract(ServerPlayer player, List<BlockPos> positions) {
        ServerLevel world = (ServerLevel) player.level();
        if (player.getMainHandItem().isEmpty()) return;

        boolean isCreative = player.isCreative();
        ChainVeinServerConfig.Values limits = ChainVeinServerConfig.values();

        for (BlockPos pos : firstPositions(positions, limits.maxBlocks())) {
            if (!player.isWithinBlockInteractionRange(pos, 1.0)) continue;
            if (!world.isLoaded(pos)) continue;
            if (world.getServer().isUnderSpawnProtection(world, pos, player)
                    || !world.mayInteract(player, pos)) continue;
            if (!isCreative && player.getMainHandItem().isEmpty()) break;

            player.gameMode.useItemOn(
                    player,
                    world,
                    player.getMainHandItem(),
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(
                            Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false)
            );
        }
    }

    static void handleMine(ServerPlayer player, List<BlockPos> positions,
                           boolean directToInventory, boolean quickShulkerOverflow) {
        ServerLevel world = (ServerLevel) player.level();
        boolean isCreative = player.isCreative();
        boolean startedWithEmptyHand = player.getMainHandItem().isEmpty();
        ChainVeinServerConfig.Values limits = ChainVeinServerConfig.values();
        List<BlockPos> limitedPositions = firstPositions(positions, limits.maxBlocks());

        for (BlockPos pos : limitedPositions) {
            if (!world.isLoaded(pos)) continue;
            if (!isCreative && !startedWithEmptyHand && player.getMainHandItem().isEmpty()) break;

            BlockState state = world.getBlockState(pos);
            if (state.isAir()) continue;

            if (directToInventory && !isCreative) {
                DirectDropCollector.run(
                        player,
                        quickShulkerOverflow,
                        limits.pickupRadius(),
                        () -> player.gameMode.destroyBlock(pos));
            } else {
                player.gameMode.destroyBlock(pos);
            }
        }
    }

    private static List<BlockPos> firstPositions(List<BlockPos> positions, int limit) {
        return positions.size() <= limit ? positions : positions.subList(0, limit);
    }

    public record ChainMinePayload(List<BlockPos> positions, boolean directToInventory,
                                   boolean quickShulkerOverflow) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ChainMinePayload> ID = new CustomPacketPayload.Type<>(MINE_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ChainMinePayload> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(ChainVeinServerConfig.MAX_MAX_BLOCKS)), ChainMinePayload::positions,
                ByteBufCodecs.BOOL, ChainMinePayload::directToInventory,
                ByteBufCodecs.BOOL, ChainMinePayload::quickShulkerOverflow,
                ChainMinePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ChainInteractPayload(List<BlockPos> positions) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ChainInteractPayload> ID = new CustomPacketPayload.Type<>(INTERACT_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ChainInteractPayload> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(ChainVeinServerConfig.MAX_MAX_BLOCKS)), ChainInteractPayload::positions,
                ChainInteractPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
}
