package org.edtp.chainveinfabric.mixin.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.edtp.chainveinfabric.Chainveinfabric;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ChainVeinClientMixin {

    @Shadow @Final private MinecraftClient client;

    private boolean isChainMining = false;

    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void onBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (isChainMining || !ChainveinfabricClient.CONFIG.isChainVeinEnabled || client.world == null || client.player == null) {
            return;
        }

        BlockState state = client.world.getBlockState(pos);
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();

        if (!ChainveinfabricClient.CONFIG.whitelistedBlocks.contains(blockId)) {
            return;
        }

        isChainMining = true;
        try {
            performClientChainMining(pos, state);
        } finally {
            isChainMining = false;
        }
    }

    private void performClientChainMining(BlockPos startPos, BlockState targetState) {
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> toBreak = new ArrayList<>();

        queue.add(startPos);
        visited.add(startPos);

        int maxBlocks = ChainveinfabricClient.CONFIG.maxChainBlocks;
        Block targetBlock = targetState.getBlock();

        // BFS to find connected blocks
        while (!queue.isEmpty() && toBreak.size() < maxBlocks) {
            BlockPos current = queue.poll();
            toBreak.add(current); // Add everything, including startPos

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        int diffs = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);

                        if (diffs == 2 && !ChainveinfabricClient.CONFIG.diagonalEdge) continue;
                        if (diffs == 3 && !ChainveinfabricClient.CONFIG.diagonalCorner) continue;

                        BlockPos neighbor = current.add(dx, dy, dz);
                        
                        // Distance check: only add if within maxRadius from the original startPos
                        if (neighbor.getSquaredDistance(startPos) > ChainveinfabricClient.CONFIG.maxRadius * ChainveinfabricClient.CONFIG.maxRadius) {
                            continue;
                        }

                        if (!visited.contains(neighbor)) {
                            visited.add(neighbor);
                            if (client.world.getBlockState(neighbor).isOf(targetBlock)) {
                                queue.add(neighbor);
                            }
                        }
                    }
                }
            }
        }

        if (toBreak.isEmpty()) return;

        // Tool Protection Logic
        ItemStack tool = client.player.getMainHandStack();
        boolean toolProtection = ChainveinfabricClient.CONFIG.toolProtection;
        boolean isCreative = client.player.isCreative();
        int toolDamage = tool.getDamage();
        int toolMaxDamage = tool.getMaxDamage();

        List<BlockPos> finalBreakList = new ArrayList<>();
        boolean stoppedByProtection = false;

        for (BlockPos p : toBreak) {
            if (!isCreative && toolProtection && tool.isDamageable()) {
                if (toolMaxDamage - toolDamage <= 10) {
                    stoppedByProtection = true;
                    break;
                }
                // Simulate damage increase (1 per block roughly)
                toolDamage++;
            }
            finalBreakList.add(p);
        }

        if (stoppedByProtection) {
             client.player.sendMessage(net.minecraft.text.Text.translatable("message.chainveinfabric.protection"), true);
        }

        if (finalBreakList.isEmpty()) return;

        // Check if server supports mod
        if (ClientPlayNetworking.canSend(Chainveinfabric.ChainMinePayload.ID)) {
            // Send payload to server, including startPos so server handles its drop logic (Direct to Inventory)
            ClientPlayNetworking.send(new Chainveinfabric.ChainMinePayload(finalBreakList, ChainveinfabricClient.CONFIG.directToInventory));
        } else {
            // Vanilla Server Fallback
            for (BlockPos pos : finalBreakList) {
                if (pos.equals(startPos)) continue; // Skip startPos, standard logic handles it
                client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
                client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
            }
        }
        
        // Notify broken count
        int totalBroken = finalBreakList.size(); // finalBreakList now includes startPos logic logic
        // If vanilla server, totalBroken is correct.
        // If modded server, totalBroken is correct.
        if (totalBroken > 1) {
             client.player.sendMessage(net.minecraft.text.Text.translatable("message.chainveinfabric.broken", totalBroken), true);
        }
    }
}