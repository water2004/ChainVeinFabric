package org.edtp.chainveinfabric.client.logic;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.edtp.chainveinfabric.Chainveinfabric;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaContext;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaIntegration;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.handler.ClientChainHandler;

import java.util.List;

public class MineLogic {
    public static void perform(Minecraft client, BlockPos pos, BlockState targetState) {
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        var whitelist = config.getWhitelist(config.mode);
        LitematicaContext litematicaContext = LitematicaIntegration.createContext(
                config.mode,
                config.respectSchematicRenderLayer
        );
        Direction face = Direction.UP;
        if (client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) {
            face = hit.getDirection();
        }

        java.util.function.Predicate<BlockPos> predicate = p -> {
            BlockState s = client.level.getBlockState(p);
            String id = ChainVeinConfig.getWhitelistItemId(s.getBlock());

            if (id == null || !whitelist.contains(id)) {
                return false;
            }

            if (config.mode.isSchematicMode() && !litematicaContext.matches(client.level, p)) {
                return false;
            }

            if (config.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME) {
                return id.equals(ChainVeinConfig.getWhitelistItemId(targetState.getBlock()));
            }
            return true;
        };

        if (!predicate.test(pos)) return;

        List<BlockPos> toBreak = ChainSearcher.search(client, pos, face, predicate);

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

        boolean isDamageable = tool.isDamageableItem();
        boolean toolProtection = ChainveinfabricClient.CONFIG.toolProtection;
        int maxBlocks = ChainveinfabricClient.CONFIG.maxChainBlocks;

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
                client.gui.setOverlayMessage(Component.translatable("message.chainveinfabric.protection"), false);
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
            client.gui.setOverlayMessage(Component.translatable("message.chainveinfabric.broken", finalBreakList.size()), false);
        }
    }

}
