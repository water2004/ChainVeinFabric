package org.edtp.chainveinfabric.client.logic;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.api.ChainVeinClientApi;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.logic.InteractionBatchPlanner;

import java.util.List;

public class InteractLogic {
    public static void perform(Minecraft client, BlockPos pos, BlockState state, ItemStack stack) {
        if (ChainveinfabricClient.CONFIG.mode == ChainVeinConfig.ChainMode.CHAIN_PLANT) {
            handlePlanting(client, pos, state, stack);
        } else if (ChainveinfabricClient.CONFIG.mode == ChainVeinConfig.ChainMode.CHAIN_UTILITY) {
            handleUtility(client, pos, state, stack);
        }
    }

    private static void handlePlanting(Minecraft client, BlockPos pos, BlockState state, ItemStack stack) {
        if (stack.isEmpty() || !PlantingItems.isPlantable(stack.getItem())) return;

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!ChainveinfabricClient.CONFIG.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_PLANT).contains(itemId)) return;

        Block targetSoil = state.getBlock();
        Direction face = Direction.UP;
        if (client.hitResult instanceof BlockHitResult hit) {
            face = hit.getDirection();
        }

        List<BlockPos> targets = ChainSearcher.search(client, pos, face, p -> 
            client.level.getBlockState(p).is(targetSoil) && 
            client.level.getBlockState(p.above()).isAir()
        );

        executeInteract(client, pos, targets, stack, "message.chainveinfabric.planted", true);
    }

    private static void handleUtility(Minecraft client, BlockPos pos, BlockState state, ItemStack stack) {
        if (stack.getItem() instanceof BlockItem) return;

        String itemId = ChainVeinConfig.getWhitelistItemId(state.getBlock());
        var whitelist = ChainveinfabricClient.CONFIG.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_UTILITY);
        if (itemId == null || !whitelist.contains(itemId)) return;

        Direction face = Direction.UP;
        if (client.hitResult instanceof BlockHitResult hit) {
            face = hit.getDirection();
        }

        List<BlockPos> targets = ChainSearcher.search(client, pos, face, p -> {
            BlockState s = client.level.getBlockState(p);
            String id = ChainVeinConfig.getWhitelistItemId(s.getBlock());
            
            if (id == null || !whitelist.contains(id)) {
                return false;
            }
            
            if (ChainveinfabricClient.CONFIG.searchAlgorithm == ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME) {
                return id.equals(itemId);
            }
            return true;
        });

        executeInteract(client, pos, targets, stack, "message.chainveinfabric.processed", false);
    }

    private static void executeInteract(Minecraft client, BlockPos startPos, List<BlockPos> targets,
                                        ItemStack stack, String translationKey, boolean planting) {
        if (targets.isEmpty()) return;

        boolean isEmptyHand = stack.isEmpty();
        boolean isDamageable = stack.isDamageableItem();
        int remainingDurability = isDamageable
                ? stack.getMaxDamage() - stack.getDamageValue()
                : Integer.MAX_VALUE;
        InteractionBatchPlanner.Plan plan = InteractionBatchPlanner.create(
                targets,
                startPos,
                ChainveinfabricClient.CONFIG.maxChainBlocks,
                stack.getCount(),
                client.player.isCreative(),
                isEmptyHand,
                isDamageable,
                ChainveinfabricClient.CONFIG.toolProtection,
                remainingDurability
        );

        if (plan.limitedByDurability()) {
            client.gui.setOverlayMessage(Component.translatable("message.chainveinfabric.protection"), false);
        }

        List<BlockPos> queuedPositions = plan.queuedPositions();
        if (queuedPositions.isEmpty()) return;

        if (planting) {
            ChainVeinClientApi.queuePlantJobs(client, queuedPositions);
        } else {
            ChainVeinClientApi.queueUseJobs(client, queuedPositions);
        }

        client.gui.setOverlayMessage(Component.translatable(translationKey, plan.affectedCount()), false);
    }
}
