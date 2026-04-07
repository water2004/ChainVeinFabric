package org.edtp.chainveinfabric;

import net.fabricmc.api.ModInitializer;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

public class Chainveinfabric implements ModInitializer {

    public static final Identifier MINE_PACKET_ID = Identifier.fromNamespaceAndPath("chainveinfabric", "mine");
    public static final Identifier INTERACT_PACKET_ID = Identifier.fromNamespaceAndPath("chainveinfabric", "interact");

    @Override
    public void onInitialize() {
        // Register Payloads
        PayloadTypeRegistry.playC2S().register(ChainMinePayload.ID, ChainMinePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChainInteractPayload.ID, ChainInteractPayload.CODEC);
        
        // Register Mine Receiver
        ServerPlayNetworking.registerGlobalReceiver(ChainMinePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                ServerLevel world = (ServerLevel) player.level();
                ItemStack tool = player.getMainHandItem();
                boolean isCreative = player.isCreative();
                boolean directToInv = payload.directToInventory();
                boolean startedWithEmptyHand = tool.isEmpty();

                for (BlockPos pos : payload.positions()) {
                    if (player.distanceToSqr(pos.getCenter()) > 100) continue;
                    if (!isCreative && !startedWithEmptyHand && tool.isEmpty()) break;

                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) continue;

                    BlockEntity blockEntity = world.getBlockEntity(pos);
                    boolean canHarvest = player.hasCorrectToolForDrops(state);

                    state.getBlock().playerWillDestroy(world, pos, state, player);
                    
                    boolean removed = world.removeBlock(pos, false);
                    if (removed) {
                        state.getBlock().destroy(world, pos, state);
                        
                        if (!isCreative) {
                            if (canHarvest) {
                                if (directToInv) {
                                    List<ItemStack> drops = Block.getDrops(state, world, pos, blockEntity, player, tool);
                                    for (ItemStack drop : drops) {
                                        if (!player.getInventory().add(drop)) {
                                            Block.popResource(world, pos, drop);
                                        }
                                    }
                                    state.spawnAfterBreak(world, pos, tool, true);
                                } else {
                                    Block.dropResources(state, world, pos, blockEntity, player, tool);
                                }
                            }
                            tool.mineBlock(world, state, pos, player);
                            if (tool.isEmpty()) {
                                player.onEquippedItemBroken(tool.getItem(), EquipmentSlot.MAINHAND);
                            }
                        }
                    }
                }
            });
        });

        // Register Interact Receiver (Handles Planting, Waxing, Stripping, etc.)
        ServerPlayNetworking.registerGlobalReceiver(ChainInteractPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                ServerLevel world = (ServerLevel) player.level();
                ItemStack stack = player.getMainHandItem();
                
                if (stack.isEmpty()) return;

                for (BlockPos pos : payload.positions()) {
                    // Safety: Distance Check
                    if (player.distanceToSqr(pos.getCenter()) > 100) continue;
                    if (!player.isCreative() && stack.isEmpty()) break;

                    // Simulate right-click interaction
                    stack.useOn(new net.minecraft.world.item.context.UseOnContext(
                        player, 
                        net.minecraft.world.InteractionHand.MAIN_HAND, 
                        new net.minecraft.world.phys.BlockHitResult(pos.getCenter(), net.minecraft.core.Direction.UP, pos, false)
                    ));
                }
            });
        });
    }

    public record ChainMinePayload(List<BlockPos> positions, boolean directToInventory) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ChainMinePayload> ID = new CustomPacketPayload.Type<>(MINE_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ChainMinePayload> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), ChainMinePayload::positions,
                ByteBufCodecs.BOOL, ChainMinePayload::directToInventory,
                ChainMinePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ChainInteractPayload(List<BlockPos> positions) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ChainInteractPayload> ID = new CustomPacketPayload.Type<>(INTERACT_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ChainInteractPayload> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), ChainInteractPayload::positions,
                ChainInteractPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
}