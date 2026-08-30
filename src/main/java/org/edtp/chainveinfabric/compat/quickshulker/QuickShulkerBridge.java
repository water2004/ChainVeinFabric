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
        return OverflowInsertion.insertInBoxOrder(
                remainders, new LegacyTargets(targets));
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
            targets.add(new LegacyTarget(player, host, data));
        }
        return targets;
    }

    private static boolean isShulkerBox(ItemStack stack) {
        return !stack.isEmpty() && Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock;
    }

    private static final class LegacyTargets
            implements OverflowInsertion.OrderedTargets {
        private final List<LegacyTarget> targets;

        private LegacyTargets(List<LegacyTarget> targets) {
            this.targets = targets;
        }

        @Override
        public int size() {
            return targets.size();
        }

        @Override
        public OverflowInsertion.Target resolve(int index) {
            LegacyTarget target = targets.get(index);
            return target.open() ? target : null;
        }

        @Override
        public void finish() {
            for (LegacyTarget target : targets) target.finish();
        }
    }

    private static final class LegacyTarget
            implements OverflowInsertion.Target {
        private final ServerPlayer player;
        private final ItemStack host;
        private final QuickShulkerData data;
        private boolean opened;
        private boolean changed;
        private Container container;
        private ContainerStorage storage;

        private LegacyTarget(
                ServerPlayer player,
                ItemStack host,
                QuickShulkerData data) {
            this.player = player;
            this.host = host;
            this.data = data;
        }

        private boolean open() {
            if (opened) return container != null;
            opened = true;
            container = data.getInventory(player, host);
            if (container != null) storage = ContainerStorage.of(container, null);
            return container != null;
        }

        @Override
        public boolean accepts(ItemStack source) {
            return data.canBundleInsertItem(player, container, host, source);
        }

        @Override
        public long insert(
                ItemVariant variant,
                long amount,
                Transaction transaction) {
            long inserted = storage.insert(variant, amount, transaction);
            if (inserted > 0) changed = true;
            return inserted;
        }

        private void finish() {
            if (changed) container.stopOpen(player);
        }
    }
}
