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

/**
 * Linked Quick Shulker implementation. Only types from its documented API
 * package are referenced here.
 */
final class QuickShulkerBridge {
    private QuickShulkerBridge() {
    }

    static int insertIntoCarriedShulkerBoxes(ServerPlayer player, ItemStack remainder) {
        int initialCount = remainder.getCount();
        Inventory playerInventory = player.getInventory();

        for (int slot = 0; slot < playerInventory.getNonEquipmentItems().size()
                && !remainder.isEmpty(); slot++) {
            ItemStack hostStack = playerInventory.getItem(slot);
            if (!isShulkerBox(hostStack)) continue;

            QuickShulkerData data = QuickOpenableRegistry.getQuickie(hostStack.getItem());
            if (data == null || !data.supportsBundleing) continue;
            if (!data.ignoreSingleStackCheck && hostStack.getCount() > 1) continue;

            Container container = data.getInventory(player, hostStack);
            if (container == null
                    || !data.canBundleInsertItem(player, container, hostStack, remainder)) {
                continue;
            }

            int inserted = insert(container, remainder);
            if (inserted <= 0) continue;

            remainder.shrink(inserted);
            container.stopOpen(player);
        }

        return initialCount - remainder.getCount();
    }

    private static int insert(Container container, ItemStack stack) {
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = ContainerStorage.of(container, null).insert(
                    ItemVariant.of(stack), stack.getCount(), transaction);
            if (inserted <= 0) return 0;

            transaction.commit();
            return (int) inserted;
        }
    }

    private static boolean isShulkerBox(ItemStack stack) {
        return !stack.isEmpty() && Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock;
    }
}
