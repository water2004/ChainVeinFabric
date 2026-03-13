package org.edtp.chainveinfabric.client.logic;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoneycombItem;
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

        Block targetSoil = state.getBlock(); // 以当前交互成功的方块作为基准土壤

        // 搜索连接的同类土壤，且上方必须是空气
        List<BlockPos> targets = ChainSearcher.findConnected(client, pos, (start, current) -> 
            client.world.getBlockState(current).isOf(targetSoil) && 
            client.world.getBlockState(current.up()).isAir()
        );

        if (targets.size() <= 1) return;

        executeInteract(client, pos, targets, stack, "message.chainveinfabric.planted");
    }

    private static void handleUtility(MinecraftClient client, BlockPos pos, BlockState state, ItemStack stack) {
        boolean isWaxing = stack.getItem() instanceof HoneycombItem;
        boolean isAxe = stack.getItem() instanceof AxeItem;
        if (!isWaxing && !isAxe) return;

        List<BlockPos> targets = ChainSearcher.findConnected(client, pos, (start, current) -> 
            client.world.getBlockState(current).isOf(state.getBlock())
        );

        if (targets.size() <= 1) return;

        executeInteract(client, pos, targets, stack, "message.chainveinfabric.processed");
    }

    private static void executeInteract(MinecraftClient client, BlockPos startPos, List<BlockPos> targets, ItemStack stack, String translationKey) {
        if (targets.isEmpty()) return;

        int available = client.player.isCreative() ? ChainveinfabricClient.CONFIG.maxChainBlocks : stack.getCount();
        int count = Math.min(targets.size(), available);
        List<BlockPos> finalSubList = targets.subList(0, count);

        if (ClientPlayNetworking.canSend(Chainveinfabric.ChainInteractPayload.ID)) {
            ClientPlayNetworking.send(new Chainveinfabric.ChainInteractPayload(finalSubList));
        } else {
            for (BlockPos p : finalSubList) {
                if (p.equals(startPos)) continue;
                client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, 
                    new BlockHitResult(p.toCenterPos(), Direction.UP, p, false));
            }
        }

        if (finalSubList.size() > 1) {
            client.player.sendMessage(Text.translatable(translationKey, finalSubList.size()), true);
        }
    }
}
