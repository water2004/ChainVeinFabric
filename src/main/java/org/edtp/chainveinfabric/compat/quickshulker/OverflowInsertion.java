package org.edtp.chainveinfabric.compat.quickshulker;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;

/** Shared request-level insertion engine for carried shulker boxes. */
final class OverflowInsertion {
    private OverflowInsertion() {
    }

    static int insertInBoxOrder(
            List<ItemStack> remainders,
            OrderedTargets targets) {
        List<Batch> batches = consecutiveBatches(remainders);
        if (batches.isEmpty() || targets.size() == 0) return 0;

        try (Transaction transaction = Transaction.openOuter()) {
            for (Batch batch : batches) {
                long remaining = batch.requestedAmount;
                for (int index = 0; index < targets.size(); index++) {
                    if (remaining == 0) break;
                    Target target = targets.resolve(index);
                    if (target == null || !target.accepts(batch.sample())) continue;
                    remaining -= target.insert(
                            batch.variant, remaining, transaction);
                }
                batch.insertedAmount = batch.requestedAmount - remaining;
            }
            transaction.commit();
        }

        int total = 0;
        for (Batch batch : batches) total += batch.applyInsertedAmount();
        targets.finish();
        return total;
    }

    private static List<Batch> consecutiveBatches(List<ItemStack> remainders) {
        List<Batch> batches = new ArrayList<>();
        Batch current = null;
        for (ItemStack source : remainders) {
            if (source == null || source.isEmpty()) continue;
            ItemVariant variant = ItemVariant.of(source);
            if (current == null || !current.variant.equals(variant)) {
                current = new Batch(variant);
                batches.add(current);
            }
            current.sources.add(source);
            current.requestedAmount += source.getCount();
        }
        return batches;
    }

    interface OrderedTargets {
        int size();

        Target resolve(int index);

        default void finish() {
        }
    }

    interface Target {
        boolean accepts(ItemStack sample);

        long insert(ItemVariant variant, long amount, Transaction transaction);
    }

    private static final class Batch {
        private final ItemVariant variant;
        private final List<ItemStack> sources = new ArrayList<>();
        private long requestedAmount;
        private long insertedAmount;

        private Batch(ItemVariant variant) {
            this.variant = variant;
        }

        private ItemStack sample() {
            return sources.getFirst();
        }

        private int applyInsertedAmount() {
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
}
