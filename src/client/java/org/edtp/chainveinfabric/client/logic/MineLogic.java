package org.edtp.chainveinfabric.client.logic;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.edtp.chainveinfabric.Chainveinfabric;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.handler.ClientChainHandler;

import java.util.List;

public class MineLogic {
    public static void perform(Minecraft client, BlockPos pos, BlockState targetState) {
        Direction face = Direction.UP;
        if (client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) {
            face = hit.getDirection();
        }

        List<BlockPos> toBreak = ChainSearcher.search(client, pos, face, p -> {
            BlockState s = client.level.getBlockState(p);
            String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
            
            if (!ChainveinfabricClient.CONFIG.whitelistedBlocks.contains(id)) {
                return false;
            }
            
            if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME) {
                return s.is(targetState.getBlock());
            }
            return true;
        });

        if (toBreak.isEmpty()) return;

        ItemStack tool = client.player.getMainHandItem();
        boolean isCreative = client.player.isCreative();
        
        if (!isCreative) {
            toBreak.removeIf(p -> {
                BlockState s = client.level.getBlockState(p);
                return s.getDestroySpeed(client.level, p) < 0.0F;
            });
        }

        if (toBreak.isEmpty()) return;

        boolean emptyHand = tool.isEmpty();
        boolean isDamageable = tool.isDamageableItem();
        boolean toolProtection = ChainveinfabricClient.CONFIG.toolProtection;
        int maxBlocks = ChainveinfabricClient.CONFIG.maxChainBlocks;
        maxBlocks = isCreative ? maxBlocks : (emptyHand || isDamageable ? maxBlocks : tool.getCount());

        boolean limitedByDurability = false;
        
        if (!isCreative && toolProtection && isDamageable) {
            int remainingDurability = tool.getMaxDamage() - tool.getDamageValue();
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
                client.player.displayClientMessage(Component.translatable("message.chainveinfabric.protection"), true);
            }
        }

        if (finalBreakList.isEmpty()) return;

        if (ClientPlayNetworking.canSend(Chainveinfabric.ChainMinePayload.ID)) {
            ClientPlayNetworking.send(new Chainveinfabric.ChainMinePayload(finalBreakList, ChainveinfabricClient.CONFIG.directToInventory));
        } else {
            for (BlockPos p : finalBreakList) {
                if (p.equals(pos)) continue;
                ClientChainHandler.addTask(() -> {
                    client.getConnection().send(new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, p, Direction.UP));
                    client.getConnection().send(new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, p, Direction.UP));
                });
            }
        }

        if (finalBreakList.size() > 1) {
            client.player.displayClientMessage(Component.translatable("message.chainveinfabric.broken", finalBreakList.size()), true);
        }
    }
}
