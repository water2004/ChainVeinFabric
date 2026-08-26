package org.edtp.chainveinfabric.compat.quickshulker;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Reflection-isolated bridge to Quick Shulker's screen-independent storage API. */
final class QuickStorageDirectBridge {
    private static final Api API = Api.load();

    private QuickStorageDirectBridge() {
    }

    static boolean isUsable() {
        return API != null;
    }

    static int insertIntoCarriedShulkerBoxes(ServerPlayer player, ItemStack remainder) {
        if (API == null || player == null || remainder == null || remainder.isEmpty()) return 0;

        int initialCount = remainder.getCount();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && !remainder.isEmpty(); slot++) {
            ItemStack host = inventory.getItem(slot);
            if (!isShulkerBox(host)) continue;
            API.insert(player, remainder, host);
        }
        return initialCount - remainder.getCount();
    }

    private static boolean isShulkerBox(ItemStack stack) {
        return !stack.isEmpty() && Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock;
    }

    private record Api(Constructor<?> mutableStackEndpoint,
                       Constructor<?> storageItemEndpoint,
                       Constructor<?> transferSpec,
                       Method anySlotSelector,
                       Method anyStackMatcher,
                       Method allTransferLimit,
                       Method executeTransfer) {
        private static Api load() {
            try {
                ClassLoader loader = QuickStorageDirectBridge.class.getClassLoader();
                Class<?> endpoint = Class.forName(
                        "net.kyrptonaught.quickshulker.api.storage.TransferEndpoint", false, loader);
                Class<?> mutable = Class.forName(
                        "net.kyrptonaught.quickshulker.api.storage.MutableStackEndpoint", false, loader);
                Class<?> storage = Class.forName(
                        "net.kyrptonaught.quickshulker.api.storage.StorageItemEndpoint", false, loader);
                Class<?> slots = Class.forName(
                        "net.kyrptonaught.quickshulker.api.storage.SlotSelector", false, loader);
                Class<?> matcher = Class.forName(
                        "net.kyrptonaught.quickshulker.api.storage.StackMatcher", false, loader);
                Class<?> limit = Class.forName(
                        "net.kyrptonaught.quickshulker.api.storage.TransferLimit", false, loader);
                Class<?> spec = Class.forName(
                        "net.kyrptonaught.quickshulker.api.storage.TransferSpec", false, loader);
                Class<?> transfers = Class.forName(
                        "net.kyrptonaught.quickshulker.api.storage.QuickStorageTransfer", false, loader);

                return new Api(
                        mutable.getConstructor(ItemStack.class),
                        storage.getConstructor(ItemStack.class, slots),
                        spec.getConstructor(endpoint, endpoint, matcher, limit),
                        slots.getMethod("any"),
                        matcher.getMethod("any"),
                        limit.getMethod("all"),
                        transfers.getMethod("execute",
                                net.minecraft.world.entity.player.Player.class, spec));
            } catch (ReflectiveOperationException | LinkageError error) {
                return null;
            }
        }

        private void insert(ServerPlayer player, ItemStack source, ItemStack host) {
            try {
                Object sourceEndpoint = mutableStackEndpoint.newInstance(source);
                Object destinationEndpoint = storageItemEndpoint.newInstance(
                        host, anySlotSelector.invoke(null));
                Object spec = transferSpec.newInstance(
                        sourceEndpoint,
                        destinationEndpoint,
                        anyStackMatcher.invoke(null),
                        allTransferLimit.invoke(null));
                executeTransfer.invoke(null, player, spec);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Quick Shulker direct storage API failed", error);
            }
        }
    }
}
