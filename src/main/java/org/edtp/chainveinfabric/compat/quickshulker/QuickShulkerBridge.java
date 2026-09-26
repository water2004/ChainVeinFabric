package org.edtp.chainveinfabric.compat.quickshulker;

import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflection-isolated Quick Shulker implementation using its legacy public API.
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
        int size = inventory.items.size();
        for (int slot = 0; slot < size; slot++) {
            ItemStack host = inventory.getItem(slot);
            if (!isShulkerBox(host)) continue;

            LegacyData data = LegacyData.find(host);
            if (data == null || !data.flag("supportsBundleing")) continue;
            if (!data.flag("ignoreSingleStackCheck") && host.getCount() > 1) continue;
            targets.add(new LegacyTarget(player, host, data));
        }
        return targets;
    }

    private static boolean isShulkerBox(ItemStack stack) {
        return !stack.isEmpty() && Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock;
    }

    /** Avoids linking the optional legacy API until the integration is used. */
    private record LegacyData(Object delegate) {
        static LegacyData find(ItemStack host) {
            try {
                Class<?> registry = Class.forName("net.kyrptonaught.quickshulker.api.QuickOpenableRegistry");
                Object data = registry.getMethod("getQuickie", ItemLike.class)
                        .invoke(null, host.getItem());
                return data == null ? null : new LegacyData(data);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Quick Shulker legacy registry unavailable", e);
            }
        }

        boolean flag(String name) {
            try {
                return delegate.getClass().getField(name).getBoolean(delegate);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Quick Shulker legacy flag unavailable: " + name, e);
            }
        }

        Container getInventory(ServerPlayer player, ItemStack host) {
            return (Container) invoke("getInventory", player, host);
        }

        boolean canBundleInsertItem(
                ServerPlayer player, Container inventory, ItemStack host, ItemStack source) {
            return (Boolean) invoke("canBundleInsertItem", player, inventory, host, source);
        }

        private Object invoke(String name, Object... arguments) {
            try {
                for (Method method : delegate.getClass().getMethods()) {
                    if (method.getName().equals(name) && method.getParameterCount() == arguments.length) {
                        return method.invoke(delegate, arguments);
                    }
                }
                throw new NoSuchMethodException(name);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Quick Shulker legacy call failed: " + name, e);
            }
        }
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
        private final LegacyData data;
        private boolean opened;
        private boolean changed;
        private Container container;
        private InventoryStorage storage;

        private LegacyTarget(
                ServerPlayer player,
                ItemStack host,
                LegacyData data) {
            this.player = player;
            this.host = host;
            this.data = data;
        }

        private boolean open() {
            if (opened) return container != null;
            opened = true;
            container = data.getInventory(player, host);
            if (container != null) storage = InventoryStorage.of(container, null);
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
