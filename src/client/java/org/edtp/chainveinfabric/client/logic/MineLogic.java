package org.edtp.chainveinfabric.client.logic;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.edtp.chainveinfabric.Chainveinfabric;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.handler.ClientChainHandler;

import java.util.List;

public class MineLogic {
    public static void perform(MinecraftClient client, BlockPos pos, BlockState targetState) {
        Direction face = Direction.UP;
        if (client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit) {
            face = hit.getSide();
        }

        List<BlockPos> toBreak = ChainSearcher.search(client, pos, face, p -> {
            BlockState s = client.world.getBlockState(p);
            String id = Registries.BLOCK.getId(s.getBlock()).toString();
            if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME) {
                return s.isOf(targetState.getBlock());
            }
            return ChainveinfabricClient.CONFIG.whitelistedBlocks.contains(id);
        });

        if (toBreak.isEmpty()) return;

        ItemStack tool = client.player.getMainHandStack();
        boolean isCreative = client.player.isCreative();
        boolean emptyHand = tool.isEmpty();
        boolean isDamageable = tool.isDamageable();
        boolean toolProtection = ChainveinfabricClient.CONFIG.toolProtection;
        int maxBlocks = ChainveinfabricClient.CONFIG.maxChainBlocks;
        maxBlocks = isCreative ? maxBlocks : (emptyHand || isDamageable ? maxBlocks : tool.getCount());

        boolean limitedByDurability = false;
        
        if (!isCreative && toolProtection && isDamageable) {
            int remainingDurability = tool.getMaxDamage() - tool.getDamage();
            int safeLimit = Math.max(0, remainingDurability - 10);
            if (safeLimit < maxBlocks) {
                maxBlocks = safeLimit;
                limitedByDurability = true;
            }
        }

        List<BlockPos> finalBreakList = toBreak;
        if (toBreak.size() > maxBlocks) {
            finalBreakList = toBreak.subList(0, maxBlocks);
            if (limitedByDurability) {
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
                    client.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket(net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, p, Direction.UP));
                    client.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket(net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, p, Direction.UP));
                });
            }
        }

        if (finalBreakList.size() > 1) {
            client.player.sendMessage(Text.translatable("message.chainveinfabric.broken", finalBreakList.size()), true);
        }
    }
}
