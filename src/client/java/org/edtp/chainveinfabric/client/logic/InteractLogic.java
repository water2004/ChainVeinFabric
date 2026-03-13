package org.edtp.chainveinfabric.client.logic;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
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
        // 排除掉方块类物品，防止意外触发“连锁放置”
        if (stack.getItem() instanceof BlockItem) return;

        // 既然已经走到了这里，说明 Mixin 已经验证了第一次交互是成功的 (result.isAccepted())
        // 我们只需要检查该方块是否在“适用方块”名单中即可
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        if (!ChainveinfabricClient.CONFIG.whitelistedUtilityBlocks.contains(blockId)) return;

        List<BlockPos> targets = ChainSearcher.findConnected(client, pos, (start, current) -> 
            client.world.getBlockState(current).isOf(state.getBlock())
        );

        if (targets.size() <= 1) return;

        executeInteract(client, pos, targets, stack, "message.chainveinfabric.processed");
    }

    private static void executeInteract(MinecraftClient client, BlockPos startPos, List<BlockPos> targets, ItemStack stack, String translationKey) {
        if (targets.isEmpty()) return;

        boolean isDamageable = stack.isDamageable();
        int available = client.player.isCreative() ? ChainveinfabricClient.CONFIG.maxChainBlocks : (isDamageable ? ChainveinfabricClient.CONFIG.maxChainBlocks : stack.getCount());
        
        // 如果是可损耗物品且开启了工具保护
        if (!client.player.isCreative() && isDamageable && ChainveinfabricClient.CONFIG.toolProtection) {
            int remainingDurability = stack.getMaxDamage() - stack.getDamage();
            available = Math.min(available, remainingDurability - 10);
        }
        
        int count = Math.max(1, Math.min(targets.size(), available));
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
