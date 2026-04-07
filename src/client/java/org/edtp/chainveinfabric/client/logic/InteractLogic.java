package org.edtp.chainveinfabric.client.logic;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.edtp.chainveinfabric.Chainveinfabric;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.handler.ClientChainHandler;

import java.util.List;

public class InteractLogic {
    public static void perform(Minecraft client, BlockPos pos, BlockState state, ItemStack stack) {
        if (ChainveinfabricClient.CONFIG.mode == ChainVeinConfig.ChainMode.CHAIN_PLANT) {
            handlePlanting(client, pos, state, stack);
        } else {
            handleUtility(client, pos, state, stack);
        }
    }

    private static void handlePlanting(Minecraft client, BlockPos pos, BlockState state, ItemStack stack) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!ChainveinfabricClient.CONFIG.whitelistedCrops.contains(itemId)) return;

        Block targetSoil = state.getBlock();
        Direction face = Direction.UP;
        if (client.hitResult instanceof BlockHitResult hit) {
            face = hit.getDirection();
        }

        List<BlockPos> targets = ChainSearcher.search(client, pos, face, p -> 
            client.level.getBlockState(p).is(targetSoil) && 
            client.level.getBlockState(p.above()).isAir()
        );

        if (targets.size() <= 1) return;

        executeInteract(client, pos, targets, stack, "message.chainveinfabric.planted");
    }

    private static void handleUtility(Minecraft client, BlockPos pos, BlockState state, ItemStack stack) {
        if (stack.getItem() instanceof BlockItem) return;

        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (!ChainveinfabricClient.CONFIG.whitelistedUtilityBlocks.contains(blockId)) return;

        Direction face = Direction.UP;
        if (client.hitResult instanceof BlockHitResult hit) {
            face = hit.getDirection();
        }

        List<BlockPos> targets = ChainSearcher.search(client, pos, face, p -> {
            BlockState s = client.level.getBlockState(p);
            String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
            
            if (!ChainveinfabricClient.CONFIG.whitelistedUtilityBlocks.contains(id)) {
                return false;
            }
            
            if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME) {
                return s.is(state.getBlock());
            }
            return true;
        });

        if (targets.size() <= 1) return;

        executeInteract(client, pos, targets, stack, "message.chainveinfabric.processed");
    }

    private static void executeInteract(Minecraft client, BlockPos startPos, List<BlockPos> targets, ItemStack stack, String translationKey) {
        if (targets.isEmpty()) return;

        boolean isEmptyHand = stack.isEmpty();
        boolean isDamageable = stack.isDamageableItem();
        int configLimit = ChainveinfabricClient.CONFIG.maxChainBlocks;
        int available = client.player.isCreative() ? configLimit : (isEmptyHand || isDamageable ? configLimit : stack.getCount());
        boolean limitedByDurability = false;
        
        if (!client.player.isCreative() && isDamageable && ChainveinfabricClient.CONFIG.toolProtection) {
            int remainingDurability = stack.getMaxDamage() - stack.getDamageValue();
            int safeLimit = Math.max(0, remainingDurability - 10);
            if (safeLimit < available) {
                available = safeLimit;
                limitedByDurability = true;
            }
        }
        
        int count = Math.min(targets.size(), available);
        List<BlockPos> finalSubList = targets.subList(0, count);

        if (limitedByDurability && targets.size() > available) {
            client.player.displayClientMessage(Component.translatable("message.chainveinfabric.protection"), true);
        }

        if (ClientPlayNetworking.canSend(Chainveinfabric.ChainInteractPayload.ID)) {
            ClientPlayNetworking.send(new Chainveinfabric.ChainInteractPayload(finalSubList));
        } else {
            for (BlockPos p : finalSubList) {
                if (p.equals(startPos)) continue;
                ClientChainHandler.addTask(() -> {
                    client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, 
                        new BlockHitResult(p.getCenter(), Direction.UP, p, false));
                });
            }
        }

        if (finalSubList.size() > 1) {
            client.player.displayClientMessage(Component.translatable(translationKey, finalSubList.size()), true);
        }
    }
}
