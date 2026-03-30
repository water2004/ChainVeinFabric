package org.edtp.chainveinfabric.client.logic;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.edtp.chainveinfabric.Chainveinfabric;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.handler.ClientChainHandler;

import java.util.ArrayList;
import java.util.List;

public class MineLogic {
    public static void perform(MinecraftClient client, BlockPos pos, BlockState targetState) {
        String blockId = Registries.BLOCK.getId(targetState.getBlock()).toString();
        if (!ChainveinfabricClient.CONFIG.whitelistedBlocks.contains(blockId)) return;

        List<BlockPos> toBreak = ChainSearcher.findConnected(client, pos, (start, current) -> 
            client.world.getBlockState(current).isOf(targetState.getBlock())
        );

        if (toBreak.isEmpty()) return;

        ItemStack tool = client.player.getMainHandStack();
        boolean isCreative = client.player.isCreative();
        boolean emptyHand = tool.isEmpty();
        boolean toolProtection = ChainveinfabricClient.CONFIG.toolProtection;
        int maxBlocks = ChainveinfabricClient.CONFIG.maxChainBlocks;

        if (!isCreative && toolProtection && !emptyHand && tool.isDamageable()) {
            int remainingDurability = tool.getMaxDamage() - tool.getDamage();
            maxBlocks = Math.min(maxBlocks, Math.max(0, remainingDurability - 10));
        }

        List<BlockPos> finalBreakList = toBreak;
        if (toBreak.size() > maxBlocks) {
            finalBreakList = toBreak.subList(0, maxBlocks);
            if (!isCreative && toolProtection && !emptyHand) {
                client.player.sendMessage(Text.translatable("message.chainveinfabric.protection"), true);
            }
        }

        if (finalBreakList.isEmpty()) return;

        if (ClientPlayNetworking.canSend(Chainveinfabric.ChainMinePayload.ID)) {
            ClientPlayNetworking.send(new Chainveinfabric.ChainMinePayload(finalBreakList, ChainveinfabricClient.CONFIG.directToInventory));
        } else {
            for (BlockPos p : finalBreakList) {
                if (p.equals(pos)) continue;
                ClientChainHandler.addTask(() -> {
                    client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, p, Direction.UP));
                    client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, p, Direction.UP));
                });
            }
        }

        if (finalBreakList.size() > 1) {
            client.player.sendMessage(Text.translatable("message.chainveinfabric.broken", finalBreakList.size()), true);
        }
    }
}
