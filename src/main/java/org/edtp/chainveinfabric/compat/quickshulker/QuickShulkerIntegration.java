package org.edtp.chainveinfabric.compat.quickshulker;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Optional server-side Quick Shulker integration with one startup-selected path. */
public final class QuickShulkerIntegration {
    public static final String MOD_ID = "quickshulker";

    private static final Logger LOGGER = LoggerFactory.getLogger("ChainVeinFabric/QuickShulker");
    private static final Path SELECTED_PATH = selectPath();
    private static boolean broken;

    private QuickShulkerIntegration() {
    }

    /** Forces path selection during server startup. */
    public static void initialize() {
        LOGGER.info("Quick Shulker server integration path: {}", SELECTED_PATH);
    }

    public static boolean isAvailable() {
        return SELECTED_PATH != Path.NONE && !broken;
    }

    public static int insertOverflow(ServerPlayer player, ItemStack remainder) {
        if (remainder == null || remainder.isEmpty()) return 0;
        return insertOverflow(player, List.of(remainder));
    }

    /** Inserts the real post-mining remainders; callers retain every uninserted item. */
    public static int insertOverflow(ServerPlayer player, List<ItemStack> remainders) {
        if (!isAvailable() || player == null || remainders == null) return 0;
        try {
            return switch (SELECTED_PATH) {
                case DIRECT -> insertDirect(player, remainders);
                case LEGACY -> insertLegacy(player, remainders);
                case NONE -> 0;
            };
        } catch (RuntimeException | LinkageError error) {
            disable(error);
            return 0;
        }
    }

    private static int insertDirect(ServerPlayer player, List<ItemStack> remainders) {
        List<SingleSlotStorage<ItemVariant>> slots = new ArrayList<>();
        for (SlottedStorage<ItemVariant> storage :
                QuickStorageDirectBridge.findAll(player)) {
            slots.addAll(storage.getSlots());
        }
        if (slots.isEmpty()) return 0;

        List<InsertionBatch> batches = batchConsecutiveVariants(remainders);
        try (Transaction transaction = Transaction.openOuter()) {
            for (InsertionBatch batch : batches) {
                long remaining = batch.requestedAmount();

                // Merge globally before consuming an empty slot in any box.
                for (SingleSlotStorage<ItemVariant> slot : slots) {
                    if (remaining == 0) break;
                    if (slot.isResourceBlank()
                            || !batch.variant().equals(slot.getResource())) continue;
                    remaining -= slot.insert(
                            batch.variant(), remaining, transaction);
                }
                for (SingleSlotStorage<ItemVariant> slot : slots) {
                    if (remaining == 0) break;
                    if (!slot.isResourceBlank()) continue;
                    remaining -= slot.insert(
                            batch.variant(), remaining, transaction);
                }
                batch.setInsertedAmount(batch.requestedAmount() - remaining);
            }
            transaction.commit();
        }

        int total = 0;
        for (InsertionBatch batch : batches) {
            total += batch.applyInsertedAmount();
        }
        return total;
    }

    /**
     * Batches only adjacent equal variants so storage priority remains identical
     * for interleaved item types while repeated block drops need one slot scan.
     */
    private static List<InsertionBatch> batchConsecutiveVariants(
            List<ItemStack> remainders) {
        List<InsertionBatch> batches = new ArrayList<>();
        InsertionBatch current = null;
        for (ItemStack source : remainders) {
            if (source == null || source.isEmpty()) continue;
            ItemVariant variant = ItemVariant.of(source);
            if (current == null || !current.variant().equals(variant)) {
                current = new InsertionBatch(variant);
                batches.add(current);
            }
            current.add(source);
        }
        return batches;
    }

    private static final class InsertionBatch {
        private final ItemVariant variant;
        private final List<ItemStack> sources = new ArrayList<>();
        private long requestedAmount;
        private long insertedAmount;

        private InsertionBatch(ItemVariant variant) {
            this.variant = variant;
        }

        private ItemVariant variant() {
            return variant;
        }

        private long requestedAmount() {
            return requestedAmount;
        }

        private void add(ItemStack source) {
            sources.add(source);
            requestedAmount += source.getCount();
        }

        private void setInsertedAmount(long insertedAmount) {
            this.insertedAmount = insertedAmount;
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

    private static int insertLegacy(ServerPlayer player, List<ItemStack> remainders) {
        int total = 0;
        for (ItemStack remainder : remainders) {
            if (remainder == null || remainder.isEmpty()) continue;
            total += QuickShulkerBridge.insertIntoCarriedShulkerBoxes(
                    player, remainder);
        }
        return total;
    }

    private static Path selectPath() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return Path.NONE;
        if (QuickStorageDirectBridge.isUsable()) return Path.DIRECT;
        return detectLegacyAvailability() ? Path.LEGACY : Path.NONE;
    }

    private static boolean detectLegacyAvailability() {
        try {
            ClassLoader loader = QuickShulkerIntegration.class.getClassLoader();
            Class.forName("net.kyrptonaught.quickshulker.api.QuickOpenableRegistry", false, loader);
            Class.forName("net.kyrptonaught.quickshulker.api.QuickShulkerData", false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError error) {
            return false;
        }
    }

    private static void disable(Throwable error) {
        broken = true;
        LOGGER.error("Disabling the selected Quick Shulker integration path {}",
                SELECTED_PATH, error);
    }

    private enum Path {
        DIRECT,
        LEGACY,
        NONE
    }
}
