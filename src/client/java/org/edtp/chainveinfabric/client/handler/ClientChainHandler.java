package org.edtp.chainveinfabric.client.handler;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.edtp.chainveinfabric.Chainveinfabric;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;

import java.util.*;

public class ClientChainHandler {
    private static boolean isChainOperating = false;

    public static boolean isChainOperating() {
        return isChainOperating;
    }

    public static void performChainMine(MinecraftClient client, BlockPos pos, BlockState targetState) {
        String blockId = Registries.BLOCK.getId(targetState.getBlock()).toString();
        if (!ChainveinfabricClient.CONFIG.whitelistedBlocks.contains(blockId)) return;

        isChainOperating = true;
        try {
            List<BlockPos> toBreak = findConnectedBlocks(client, pos, targetState.getBlock());
            if (toBreak.isEmpty()) return;

            ItemStack tool = client.player.getMainHandStack();
            boolean toolProtection = ChainveinfabricClient.CONFIG.toolProtection;
            boolean isCreative = client.player.isCreative();
            int toolDamage = tool.getDamage();
            int toolMaxDamage = tool.getMaxDamage();

            List<BlockPos> finalBreakList = new ArrayList<>();
            for (BlockPos p : toBreak) {
                if (!isCreative && toolProtection && tool.isDamageable()) {
                    if (toolMaxDamage - toolDamage <= 10) {
                        client.player.sendMessage(Text.translatable("message.chainveinfabric.protection"), true);
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
                for (BlockPos p : finalBreakList) {
                    if (p.equals(pos)) continue;
                    client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, p, Direction.UP));
                    client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, p, Direction.UP));
                }
            }

            if (finalBreakList.size() > 1) {
                client.player.sendMessage(Text.translatable("message.chainveinfabric.broken", finalBreakList.size()), true);
            }
        } finally {
            isChainOperating = false;
        }
    }

    public static void performChainPlant(MinecraftClient client, BlockPos pos, ItemStack stack) {
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        if (!ChainveinfabricClient.CONFIG.whitelistedCrops.contains(itemId)) return;

        isChainOperating = true;
        try {
            List<BlockPos> toPlant = findPlantableFarmland(client, pos);
            if (toPlant.isEmpty()) return;

            int available = client.player.isCreative() ? ChainveinfabricClient.CONFIG.maxChainBlocks : stack.getCount();
            int plantCount = Math.min(toPlant.size(), available);
            List<BlockPos> finalPlantList = toPlant.subList(0, plantCount);

            if (finalPlantList.isEmpty()) return;

            if (ClientPlayNetworking.canSend(Chainveinfabric.ChainPlantPayload.ID)) {
                ClientPlayNetworking.send(new Chainveinfabric.ChainPlantPayload(finalPlantList));
            } else {
                for (BlockPos p : finalPlantList) {
                    if (p.equals(pos)) continue;
                    client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, new BlockHitResult(p.toCenterPos(), Direction.UP, p, false));
                }
            }

            if (finalPlantList.size() > 1) {
                client.player.sendMessage(Text.translatable("message.chainveinfabric.planted", finalPlantList.size()), true);
            }
        } finally {
            isChainOperating = false;
        }
    }

    private static List<BlockPos> findConnectedBlocks(MinecraftClient client, BlockPos startPos, Block targetBlock) {
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

    private static List<BlockPos> findPlantableFarmland(MinecraftClient client, BlockPos startPos) {
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();

        queue.add(startPos);
        visited.add(startPos);

        int maxBlocks = ChainveinfabricClient.CONFIG.maxChainBlocks;
        int maxRadiusSq = ChainveinfabricClient.CONFIG.maxRadius * ChainveinfabricClient.CONFIG.maxRadius;

        while (!queue.isEmpty() && result.size() < maxBlocks) {
            BlockPos current = queue.poll();
            
            if (client.world.getBlockState(current).getBlock() instanceof FarmlandBlock && 
                client.world.getBlockState(current.up()).isAir()) {
                result.add(current);
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0 || dy != 0) continue;

                        int diffs = (dx != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
                        if (diffs == 2 && !ChainveinfabricClient.CONFIG.diagonalEdge) continue;

                        BlockPos neighbor = current.add(dx, dy, dz);
                        if (neighbor.getSquaredDistance(startPos) > maxRadiusSq) continue;

                        if (!visited.contains(neighbor)) {
                            visited.add(neighbor);
                            if (client.world.getBlockState(neighbor).getBlock() instanceof FarmlandBlock) {
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
