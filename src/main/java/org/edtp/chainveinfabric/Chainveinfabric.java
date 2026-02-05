package org.edtp.chainveinfabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class Chainveinfabric implements ModInitializer {

    public static final Identifier PACKET_ID = Identifier.of("chainveinfabric", "mine");

    @Override
    public void onInitialize() {
        // Register Payload
        PayloadTypeRegistry.playC2S().register(ChainMinePayload.ID, ChainMinePayload.CODEC);
        
        // Register Receiver
        ServerPlayNetworking.registerGlobalReceiver(ChainMinePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                ServerWorld world = (ServerWorld) player.getEntityWorld();
                ItemStack tool = player.getMainHandStack();
                boolean isCreative = player.isCreative();
                boolean directToInv = payload.directToInventory(); // Use client's preference

                for (BlockPos pos : payload.positions()) {
                    // Validation: Distance check (simple)
                    if (player.squaredDistanceTo(pos.toCenterPos()) > 100) continue;

                    if (!isCreative && tool.isEmpty()) break;

                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) continue;

                    BlockEntity blockEntity = world.getBlockEntity(pos);
                    boolean canHarvest = player.canHarvest(state);

                    state.getBlock().onBreak(world, pos, state, player);
                    
                    boolean removed = world.removeBlock(pos, false);
                    if (removed) {
                        state.getBlock().onBroken(world, pos, state);
                        
                        if (!isCreative) {
                            if (canHarvest) {
                                if (directToInv) {
                                    List<ItemStack> drops = Block.getDroppedStacks(state, world, pos, blockEntity, player, tool);
                                    for (ItemStack drop : drops) {
                                        if (!player.getInventory().insertStack(drop)) {
                                            Block.dropStack(world, pos, drop);
                                        }
                                    }
                                    state.onStacksDropped(world, pos, tool, true);
                                } else {
                                    Block.dropStacks(state, world, pos, blockEntity, player, tool);
                                }
                            }
                            tool.postMine(world, state, pos, player);
                            if (tool.isEmpty()) {
                                player.sendEquipmentBreakStatus(tool.getItem(), EquipmentSlot.MAINHAND);
                            }
                        }
                    }
                }
            });
        });
    }

    public record ChainMinePayload(List<BlockPos> positions, boolean directToInventory) implements CustomPayload {
        public static final CustomPayload.Id<ChainMinePayload> ID = new CustomPayload.Id<>(PACKET_ID);
        public static final PacketCodec<RegistryByteBuf, ChainMinePayload> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC.collect(PacketCodecs.toList()), ChainMinePayload::positions,
                PacketCodecs.BOOLEAN, ChainMinePayload::directToInventory,
                ChainMinePayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
}