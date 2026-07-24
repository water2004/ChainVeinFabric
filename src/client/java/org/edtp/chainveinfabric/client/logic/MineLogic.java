package org.edtp.chainveinfabric.client.logic;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.api.ChainVeinClientApi;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaContext;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaIntegration;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;

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

        boolean isCreative = client.player.isCreative();
        
        if (!isCreative) {
            toBreak.removeIf(p -> {
                BlockState s = client.level.getBlockState(p);
                return s.getDestroySpeed(client.level, p) < 0.0F;
            });
        }

        if (toBreak.isEmpty()) return;

        int maxBlocks = ChainveinfabricClient.CONFIG.maxChainBlocks;

        List<BlockPos> finalBreakList = toBreak;
        if (toBreak.size() > maxBlocks) {
            finalBreakList = toBreak.subList(0, maxBlocks);
        }

        if (finalBreakList.isEmpty()) return;

        int affectedCount = finalBreakList.size();
        int queuedCount = ChainVeinClientApi.queueMineJobs(client, finalBreakList);

        if (queuedCount == affectedCount && affectedCount > 1) {
            client.gui.hud.setOverlayMessage(
                    Component.translatable("message.chainveinfabric.broken", affectedCount), false);
        }
    }

}
