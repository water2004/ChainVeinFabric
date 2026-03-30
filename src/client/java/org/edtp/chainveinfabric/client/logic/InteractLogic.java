package org.edtp.chainveinfabric.client.logic;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.edtp.chainveinfabric.Chainveinfabric;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.handler.ClientChainHandler;

import java.util.List;

public class InteractLogic {
    public static void perform(MinecraftClient client, BlockPos pos, BlockState state, ItemStack stack) {
        if (ChainveinfabricClient.CONFIG.mode == ChainVeinConfig.ChainMode.CHAIN_PLANT) {
            handlePlanting(client, pos, state, stack);
        } else {
            handleUtility(client, pos, state, stack);
        }
    }

    private static void handlePlanting(MinecraftClient client, BlockPos pos, BlockState state, ItemStack stack) {
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        if (!ChainveinfabricClient.CONFIG.whitelistedCrops.contains(itemId)) return;

        Block targetSoil = state.getBlock();
        Direction face = Direction.UP;
        if (client.crosshairTarget instanceof BlockHitResult hit) {
            face = hit.getSide();
        }

        List<BlockPos> targets = ChainSearcher.search(client, pos, face, p -> 
            client.world.getBlockState(p).isOf(targetSoil) && 
            client.world.getBlockState(p.up()).isAir()
        );

        if (targets.size() <= 1) return;

        executeInteract(client, pos, targets, stack, "message.chainveinfabric.planted");
    }

    private static void handleUtility(MinecraftClient client, BlockPos pos, BlockState state, ItemStack stack) {
        if (stack.getItem() instanceof BlockItem) return;

        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        if (!ChainveinfabricClient.CONFIG.whitelistedUtilityBlocks.contains(blockId)) return;

        Direction face = Direction.UP;
        if (client.crosshairTarget instanceof BlockHitResult hit) {
            face = hit.getSide();
        }

        List<BlockPos> targets = ChainSearcher.search(client, pos, face, p -> {
            BlockState s = client.world.getBlockState(p);
            String id = Registries.BLOCK.getId(s.getBlock()).toString();
            if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME) {
                return s.isOf(state.getBlock());
            }
            return ChainveinfabricClient.CONFIG.whitelistedUtilityBlocks.contains(id);
        });

        if (targets.size() <= 1) return;

        executeInteract(client, pos, targets, stack, "message.chainveinfabric.processed");
    }

    private static void executeInteract(MinecraftClient client, BlockPos startPos, List<BlockPos> targets, ItemStack stack, String translationKey) {
        if (targets.isEmpty()) return;

        boolean isDamageable = stack.isDamageable();
        int configLimit = ChainveinfabricClient.CONFIG.maxChainBlocks;
        int available = client.player.isCreative() ? configLimit : (isDamageable ? configLimit : stack.getCount());
        
        if (!client.player.isCreative() && isDamageable && ChainveinfabricClient.CONFIG.toolProtection) {
            int remainingDurability = stack.getMaxDamage() - stack.getDamage();
            available = Math.min(available, Math.max(0, remainingDurability - 10));
        }
        
        int count = Math.min(targets.size(), available);
        List<BlockPos> finalSubList = targets.subList(0, count);

        if (ClientPlayNetworking.canSend(Chainveinfabric.ChainInteractPayload.ID)) {
            ClientPlayNetworking.send(new Chainveinfabric.ChainInteractPayload(finalSubList));
        } else {
            for (BlockPos p : finalSubList) {
                if (p.equals(startPos)) continue;
                ClientChainHandler.addTask(() -> {
                    client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, 
                        new BlockHitResult(p.toCenterPos(), Direction.UP, p, false));
                });
            }
        }

        if (finalSubList.size() > 1) {
            client.player.sendMessage(Text.translatable(translationKey, finalSubList.size()), true);
        }
    }
}
