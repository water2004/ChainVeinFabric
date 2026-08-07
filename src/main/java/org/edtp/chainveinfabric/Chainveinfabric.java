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
import net.minecraft.world.phys.Vec3;
import org.edtp.chainveinfabric.compat.quickshulker.QuickShulkerIntegration;

import java.util.List;

public class Chainveinfabric implements ModInitializer {

    public static final Identifier MINE_PACKET_ID = Identifier.fromNamespaceAndPath("chainveinfabric", "mine_v4");
    public static final Identifier INTERACT_PACKET_ID = Identifier.fromNamespaceAndPath("chainveinfabric", "interact_v4");

    @Override
    public void onInitialize() {
        // Register Payloads
        PayloadTypeRegistry.serverboundPlay().register(ChainMinePayload.ID, ChainMinePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ChainInteractPayload.ID, ChainInteractPayload.CODEC);
        
        // Register Mine Receiver
        ServerPlayNetworking.registerGlobalReceiver(ChainMinePayload.ID, (payload, context) -> {
            context.server().execute(() -> handleMine(
                    context.player(), payload.positions(), payload.directToInventory(),
                    payload.quickShulkerOverflow()));
        });

        // Register Interact Receiver (Handles Planting, Waxing, Stripping, etc.)
        ServerPlayNetworking.registerGlobalReceiver(ChainInteractPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                ServerLevel world = (ServerLevel) player.level();
                ItemStack stack = player.getMainHandItem();
                
                if (stack.isEmpty()) return;

                boolean isCreative = player.isCreative();

                for (BlockPos pos : payload.positions()) {
                    // Safety: Distance Check
                    if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 100) continue;
                    if (!isCreative && stack.isEmpty()) break;

                    // Replicate vanilla creative protection (ServerPlayerGameMode.useItemOn)
                    int oldCount = stack.getCount();
                    stack.useOn(new net.minecraft.world.item.context.UseOnContext(
                        player,
                        net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false)
                    ));
                    if (isCreative) {
                        stack.setCount(oldCount);
                    }
                }
            });
        });
    }

    private static void handleMine(ServerPlayer player, List<BlockPos> positions,
                                   boolean directToInventory, boolean quickShulkerOverflow) {
        ServerLevel world = (ServerLevel) player.level();
        ItemStack tool = player.getMainHandItem();
        boolean isCreative = player.isCreative();
        boolean startedWithEmptyHand = tool.isEmpty();

        for (BlockPos pos : positions) {
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 100) continue;
            if (!isCreative && !startedWithEmptyHand && tool.isEmpty()) break;

            BlockState state = world.getBlockState(pos);
            if (state.isAir()) continue;

            BlockEntity blockEntity = world.getBlockEntity(pos);
            boolean canHarvest = player.hasCorrectToolForDrops(state);

            state.getBlock().playerWillDestroy(world, pos, state, player);

            boolean removed = world.removeBlock(pos, false);
            if (!removed) continue;

            state.getBlock().destroy(world, pos, state);

            if (!isCreative) {
                if (canHarvest) {
                    if (directToInventory) {
                        List<ItemStack> drops = Block.getDrops(state, world, pos, blockEntity, player, tool);
                        for (ItemStack drop : drops) {
                            deliverDrop(player, world, pos, drop, quickShulkerOverflow);
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

    private static void deliverDrop(ServerPlayer player, ServerLevel world, BlockPos pos,
                                    ItemStack drop, boolean quickShulkerOverflow) {
        player.getInventory().add(drop);
        if (!drop.isEmpty() && quickShulkerOverflow) {
            QuickShulkerIntegration.insertOverflow(player, drop);
        }
        if (!drop.isEmpty()) {
            Block.popResource(world, pos, drop);
        }
    }

    public record ChainMinePayload(List<BlockPos> positions, boolean directToInventory,
                                   boolean quickShulkerOverflow) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ChainMinePayload> ID = new CustomPacketPayload.Type<>(MINE_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ChainMinePayload> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), ChainMinePayload::positions,
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
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), ChainInteractPayload::positions,
                ChainInteractPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
}
