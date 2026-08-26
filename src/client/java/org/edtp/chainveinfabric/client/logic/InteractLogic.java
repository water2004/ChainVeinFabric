package org.edtp.chainveinfabric.client.logic;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.api.ChainVeinClientApi;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaContext;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.logic.search.SearchConfig;
import org.edtp.chainveinfabric.client.logic.search.SearchRequest;
import org.edtp.chainveinfabric.client.logic.search.SearchResult;
import org.edtp.chainveinfabric.client.logic.search.SearchService;
import org.edtp.chainveinfabric.logic.InteractionBatchPlanner;

public final class InteractLogic {
    private InteractLogic() {
    }

    public static void perform(Minecraft client, BlockPos pos, BlockState state, ItemStack stack) {
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        if (config == null || client.level == null || client.player == null) return;

        boolean planting = config.mode == ChainVeinConfig.ChainMode.CHAIN_PLANT;
        if (planting) {
            if (stack.isEmpty() || !PlantingItems.isPlantable(stack.getItem())) return;
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!config.getWhitelist(config.mode).contains(itemId)) return;
        } else if (config.mode == ChainVeinConfig.ChainMode.CHAIN_UTILITY) {
            if (stack.getItem() instanceof BlockItem) return;
            String targetId = ChainVeinConfig.getWhitelistItemId(state.getBlock());
            if (targetId == null || !config.getWhitelist(config.mode).contains(targetId)) return;
        } else {
            return;
        }

        Item expectedItem = stack.getItem();
        SearchConfig searchConfig = SearchConfig.from(config);
        SearchRequest request = SearchRequest.targeted(
                (ClientLevel) client.level, pos, state, client.player.getDirection(), searchConfig,
                LitematicaContext.NONE, client.player.isCreative());
        ChainveinfabricClient.getSearchService().submit(
                request, SearchService.Priority.ACTION, result -> result,
                result -> applyResult(client, result, expectedItem, planting));
    }

    private static void applyResult(Minecraft client, SearchResult result,
                                    Item expectedItem, boolean planting) {
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        if (config == null || client.level != result.request().level() || client.player == null
                || !config.isChainVeinEnabled
                || !SearchConfig.from(config).equals(result.request().config())) return;

        ItemStack stack = client.player.getMainHandItem();
        if (stack.getItem() != expectedItem || result.positions().isEmpty()) return;
        executeInteract(client, result.request().origin(), result.positions(), stack,
                planting ? "message.chainveinfabric.planted" : "message.chainveinfabric.processed",
                planting);
    }

    private static void executeInteract(Minecraft client, BlockPos startPos, List<BlockPos> targets,
                                        ItemStack stack, String translationKey, boolean planting) {
        boolean isEmptyHand = stack.isEmpty();
        boolean isDamageable = stack.isDamageableItem();
        int remainingDurability = isDamageable
                ? stack.getMaxDamage() - stack.getDamageValue()
                : Integer.MAX_VALUE;
        InteractionBatchPlanner.Plan plan = InteractionBatchPlanner.create(
                targets, startPos, ChainveinfabricClient.CONFIG.maxChainBlocks, stack.getCount(),
                client.player.isCreative(), isEmptyHand, isDamageable,
                ChainveinfabricClient.CONFIG.toolProtection, remainingDurability);

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
