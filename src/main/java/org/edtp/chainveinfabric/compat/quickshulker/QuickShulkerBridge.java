package org.edtp.chainveinfabric.compat.quickshulker;

import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.kyrptonaught.quickshulker.api.QuickOpenableRegistry;
import net.kyrptonaught.quickshulker.api.QuickShulkerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Linked Quick Shulker implementation. Only types from its documented API
 * package are referenced here.
 */
final class QuickShulkerBridge {
    private QuickShulkerBridge() {
    }

    static int insertIntoCarriedShulkerBoxes(
            ServerPlayer player,
            List<ItemStack> remainders) {
        if (player == null || remainders == null || remainders.isEmpty()) return 0;
        List<LegacyTarget> targets = findTargets(player);
        if (targets.isEmpty()) return 0;

        List<OverflowInsertionBatch> batches =
                OverflowInsertionBatch.consecutive(remainders);
        try (Transaction transaction = Transaction.openOuter()) {
            for (OverflowInsertionBatch batch : batches) {
                long remaining = batch.requestedAmount();
                // Legacy behavior fills each carried box before trying the next.
                for (LegacyTarget target : targets) {
                    if (remaining == 0) break;
                    if (!target.open(player)) continue;
                    if (!target.accepts(player, batch.sample())) continue;

                    long inserted = target.insert(
                            batch.variant(), remaining, transaction);
                    if (inserted <= 0) continue;
                    target.markChanged();
                    remaining -= inserted;
                }
                batch.setInsertedAmount(batch.requestedAmount() - remaining);
            }

            transaction.commit();
        }

        int total = 0;
        for (OverflowInsertionBatch batch : batches) {
            total += batch.applyInsertedAmount();
        }
        for (LegacyTarget target : targets) target.finish(player);
        return total;
    }

    private static List<LegacyTarget> findTargets(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<LegacyTarget> targets = new ArrayList<>();
        int size = inventory.getNonEquipmentItems().size();
        for (int slot = 0; slot < size; slot++) {
            ItemStack host = inventory.getItem(slot);
            if (!isShulkerBox(host)) continue;

            QuickShulkerData data = QuickOpenableRegistry.getQuickie(host.getItem());
            if (data == null || !data.supportsBundleing) continue;
            if (!data.ignoreSingleStackCheck && host.getCount() > 1) continue;
            targets.add(new LegacyTarget(host, data));
        }
        return targets;
    }

    private static boolean isShulkerBox(ItemStack stack) {
        return !stack.isEmpty() && Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock;
    }

    private static final class LegacyTarget {
        private final ItemStack host;
        private final QuickShulkerData data;
        private boolean opened;
        private boolean changed;
        private Container container;
        private ContainerStorage storage;

        private LegacyTarget(ItemStack host, QuickShulkerData data) {
            this.host = host;
            this.data = data;
        }

        private boolean open(ServerPlayer player) {
            if (opened) return container != null;
            opened = true;
            container = data.getInventory(player, host);
            if (container != null) storage = ContainerStorage.of(container, null);
            return container != null;
        }

        private boolean accepts(ServerPlayer player, ItemStack source) {
            return data.canBundleInsertItem(player, container, host, source);
        }

        private long insert(
                ItemVariant variant,
                long amount,
                Transaction transaction) {
            return storage.insert(variant, amount, transaction);
        }

        private void markChanged() {
            changed = true;
        }

        private void finish(ServerPlayer player) {
            if (changed) container.stopOpen(player);
        }
    }
}
