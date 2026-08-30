package org.edtp.chainveinfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.edtp.chainveinfabric.compat.quickshulker.QuickShulkerIntegration;
import org.edtp.chainveinfabric.server.ChainVeinServerCommands;
import org.edtp.chainveinfabric.server.ChainVeinServerConfig;

import java.util.List;

public class Chainveinfabric implements ModInitializer {

    public static final Identifier MINE_PACKET_ID = Identifier.fromNamespaceAndPath("chainveinfabric", "mine_v4");
    public static final Identifier INTERACT_PACKET_ID = Identifier.fromNamespaceAndPath("chainveinfabric", "interact_v4");

    @Override
    public void onInitialize() {
        ChainVeinServerCommands.register();
        ChainVeinServerScheduler.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ChainVeinServerConfig.load();
            QuickShulkerIntegration.initialize();
        });
        ChainVeinServerPacketHandler.register();
    }

    static void handleInteract(ServerPlayer player, List<BlockPos> positions) {
        ChainVeinServerPacketHandler.handleInteract(player, positions);
    }

    static void handleMine(ServerPlayer player, List<BlockPos> positions,
                           boolean directToInventory, boolean quickShulkerOverflow) {
        ChainVeinServerPacketHandler.handleMine(
                player, positions, directToInventory, quickShulkerOverflow);
    }

    public record ChainMinePayload(List<BlockPos> positions, boolean directToInventory,
                                   boolean quickShulkerOverflow) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ChainMinePayload> ID = new CustomPacketPayload.Type<>(MINE_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ChainMinePayload> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(ChainVeinServerConfig.MAX_REQUEST_POSITIONS)), ChainMinePayload::positions,
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
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(ChainVeinServerConfig.MAX_REQUEST_POSITIONS)), ChainInteractPayload::positions,
                ChainInteractPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
}
