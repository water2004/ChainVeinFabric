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

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.ActionResult;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ChainVeinClientMixin {

    @Shadow @Final private MinecraftClient client;

    private boolean isChainOperating = false;

    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void onBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (isChainOperating || !ChainveinfabricClient.CONFIG.isChainVeinEnabled || 
            ChainveinfabricClient.CONFIG.mode != ChainVeinConfig.ChainMode.CHAIN_MINE ||
            client.world == null || client.player == null) {
            return;
        }

        BlockState state = client.world.getBlockState(pos);
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();

        if (!ChainveinfabricClient.CONFIG.whitelistedBlocks.contains(blockId)) {
            return;
        }

        isChainOperating = true;
        try {
            performClientChainMining(pos, state);
        } finally {
            isChainOperating = false;
        }
    }

    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void onInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        if (isChainOperating || !ChainveinfabricClient.CONFIG.isChainVeinEnabled || 
            ChainveinfabricClient.CONFIG.mode != ChainVeinConfig.ChainMode.CHAIN_PLANT ||
            client.world == null || client.player == null || hand != Hand.MAIN_HAND) {
            return;
        }

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = client.world.getBlockState(pos);
        ItemStack stack = player.getStackInHand(hand);
        
        if (stack.isEmpty()) return;
        
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        if (!ChainveinfabricClient.CONFIG.whitelistedCrops.contains(itemId)) {
            return;
        }

        // Check if it's farmland
        if (!(state.getBlock() instanceof net.minecraft.block.FarmlandBlock)) {
            return;
        }

        isChainOperating = true;
        try {
            performClientChainPlanting(pos, stack);
        } finally {
            isChainOperating = false;
        }
    }

    private void performClientChainMining(BlockPos startPos, BlockState targetState) {
        List<BlockPos> toBreak = findConnectedBlocks(startPos, targetState.getBlock());
        if (toBreak.isEmpty()) return;

        // Tool Protection Logic
        ItemStack tool = client.player.getMainHandStack();
        boolean toolProtection = ChainveinfabricClient.CONFIG.toolProtection;
        boolean isCreative = client.player.isCreative();
        int toolDamage = tool.getDamage();
        int toolMaxDamage = tool.getMaxDamage();

        List<BlockPos> finalBreakList = new ArrayList<>();
        for (BlockPos p : toBreak) {
            if (!isCreative && toolProtection && tool.isDamageable()) {
                if (toolMaxDamage - toolDamage <= 10) {
                    client.player.sendMessage(net.minecraft.text.Text.translatable("message.chainveinfabric.protection"), true);
                    break;
                }
                toolDamage++;
            }
            finalBreakList.add(p);
        }

        if (finalBreakList.isEmpty()) return;

        if (ClientPlayNetworking.canSend(Chainveinfabric.ChainMinePayload.ID)) {
            ClientPlayNetworking.send(new Chainveinfabric.ChainMinePayload(finalBreakList, ChainveinfabricClient.CONFIG.directToInventory));
        } else {
            for (BlockPos pos : finalBreakList) {
                if (pos.equals(startPos)) continue;
                client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
                client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
            }
        }
        
        if (finalBreakList.size() > 1) {
             client.player.sendMessage(net.minecraft.text.Text.translatable("message.chainveinfabric.broken", finalBreakList.size()), true);
        }
    }

    private void performClientChainPlanting(BlockPos startPos, ItemStack stack) {
        // Find connected farmland that doesn't have a crop on top
        List<BlockPos> toPlant = findPlantableFarmland(startPos);
        if (toPlant.isEmpty()) return;

        int available = client.player.isCreative() ? ChainveinfabricClient.CONFIG.maxChainBlocks : stack.getCount();
        int plantCount = Math.min(toPlant.size(), available);
        List<BlockPos> finalPlantList = toPlant.subList(0, plantCount);

        if (finalPlantList.isEmpty()) return;

        if (ClientPlayNetworking.canSend(Chainveinfabric.ChainPlantPayload.ID)) {
            ClientPlayNetworking.send(new Chainveinfabric.ChainPlantPayload(finalPlantList));
        } else {
            // Vanilla Fallback: use item on each block
            for (BlockPos pos : finalPlantList) {
                if (pos.equals(startPos)) continue; // Standard logic handles startPos
                client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, new BlockHitResult(pos.toCenterPos(), Direction.UP, pos, false));
            }
        }

        if (finalPlantList.size() > 1) {
            client.player.sendMessage(net.minecraft.text.Text.translatable("message.chainveinfabric.planted", finalPlantList.size()), true);
        }
    }

    private List<BlockPos> findConnectedBlocks(BlockPos startPos, Block targetBlock) {
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();

        queue.add(startPos);
        visited.add(startPos);

        int maxBlocks = ChainveinfabricClient.CONFIG.maxChainBlocks;
        int maxRadiusSq = ChainveinfabricClient.CONFIG.maxRadius * ChainveinfabricClient.CONFIG.maxRadius;

        while (!queue.isEmpty() && result.size() < maxBlocks) {
            BlockPos current = queue.poll();
            result.add(current);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        int diffs = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
                        if (diffs == 2 && !ChainveinfabricClient.CONFIG.diagonalEdge) continue;
                        if (diffs == 3 && !ChainveinfabricClient.CONFIG.diagonalCorner) continue;

                        BlockPos neighbor = current.add(dx, dy, dz);
                        if (neighbor.getSquaredDistance(startPos) > maxRadiusSq) continue;

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
        return result;
    }

    private List<BlockPos> findPlantableFarmland(BlockPos startPos) {
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();

        queue.add(startPos);
        visited.add(startPos);

        int maxBlocks = ChainveinfabricClient.CONFIG.maxChainBlocks;
        int maxRadiusSq = ChainveinfabricClient.CONFIG.maxRadius * ChainveinfabricClient.CONFIG.maxRadius;

        while (!queue.isEmpty() && result.size() < maxBlocks) {
            BlockPos current = queue.poll();
            
            // Check if current is plantable (Farmland and Air above)
            if (client.world.getBlockState(current).getBlock() instanceof net.minecraft.block.FarmlandBlock && 
                client.world.getBlockState(current.up()).isAir()) {
                result.add(current);
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        if (dy != 0) continue; // Only horizontal adjacency for farmland? 
                        // Actually, vanilla usually doesn't have vertical farmland but let's stick to horizontal for simplicity or follow same rules.
                        // The user said "与该耕地相邻的耕地", usually means same level.

                        int diffs = (dx != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
                        if (diffs == 2 && !ChainveinfabricClient.CONFIG.diagonalEdge) continue;
                        // diagonalCorner (diffs=3) is impossible if dy=0.

                        BlockPos neighbor = current.add(dx, dy, dz);
                        if (neighbor.getSquaredDistance(startPos) > maxRadiusSq) continue;

                        if (!visited.contains(neighbor)) {
                            visited.add(neighbor);
                            if (client.world.getBlockState(neighbor).getBlock() instanceof net.minecraft.block.FarmlandBlock) {
                                queue.add(neighbor);
                            }
                        }
                    }
                }
            }
        }
        return result;
    }
}