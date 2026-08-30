package org.edtp.chainveinfabric.compat.quickshulker;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.world.item.ItemStack;

/**
 * Tracks consecutive equal overflow stacks as one storage insertion while
 * retaining their original order for exact partial-remainder mapping.
 */
final class OverflowInsertionBatch {
    private final ItemVariant variant;
    private final List<ItemStack> sources = new ArrayList<>();
    private long requestedAmount;
    private long insertedAmount;

    private OverflowInsertionBatch(ItemVariant variant) {
        this.variant = variant;
    }

    static List<OverflowInsertionBatch> consecutive(List<ItemStack> remainders) {
        List<OverflowInsertionBatch> batches = new ArrayList<>();
        OverflowInsertionBatch current = null;
        for (ItemStack source : remainders) {
            if (source == null || source.isEmpty()) continue;
            ItemVariant variant = ItemVariant.of(source);
            if (current == null || !current.variant.equals(variant)) {
                current = new OverflowInsertionBatch(variant);
                batches.add(current);
            }
            current.sources.add(source);
            current.requestedAmount += source.getCount();
        }
        return batches;
    }

    ItemVariant variant() {
        return variant;
    }

    ItemStack sample() {
        return sources.getFirst();
    }

    long requestedAmount() {
        return requestedAmount;
    }

    void setInsertedAmount(long insertedAmount) {
        this.insertedAmount = insertedAmount;
    }

    int applyInsertedAmount() {
        long remaining = insertedAmount;
        int applied = 0;
        for (ItemStack source : sources) {
            if (remaining == 0) break;
            int shrink = (int) Math.min(source.getCount(), remaining);
            source.shrink(shrink);
            remaining -= shrink;
            applied += shrink;
        }
        return applied;
    }
}
