package org.edtp.chainveinfabric.server;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import org.edtp.chainveinfabric.compat.quickshulker.QuickShulkerIntegration;

/** Redirects block drops produced during one server mining operation. */
public final class DirectDropCollector {
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();

    private DirectDropCollector() {
    }

    public static boolean run(ServerPlayer player, boolean quickShulkerOverflow,
                              BooleanSupplier action) {
        return run(player, quickShulkerOverflow,
                ChainVeinServerConfig.values().pickupRadius(), action);
    }

    public static boolean run(ServerPlayer player, boolean quickShulkerOverflow,
                              int pickupRadius, BooleanSupplier action) {
        Context previous = ACTIVE.get();
        Context context = new Context(
                (ServerLevel) player.level(), player,
                quickShulkerOverflow && QuickShulkerIntegration.isAvailable(),
                pickupRadius);
        ACTIVE.set(context);
        try {
            return action.getAsBoolean();
        } finally {
            // Flush with capture disabled so an uninserted remainder can take
            // the same vanilla world-item path exactly once.
            ACTIVE.remove();
            try {
                context.finish();
            } finally {
                if (previous != null) ACTIVE.set(previous);
            }
        }
    }

    /** Associates all synchronous drops with the block currently being mined. */
    public static boolean withDropOrigin(BlockPos origin, BooleanSupplier action) {
        Context context = ACTIVE.get();
        if (context == null) return action.getAsBoolean();
        return context.withDropOrigin(origin, action);
    }

    /**
     * Captures a vanilla block drop before its lazy entity factory is invoked.
     * The original factory is retained so a true remainder keeps vanilla's
     * precomputed position, movement, and pickup delay.
     */
    public static boolean captureLazyBlockDrop(
            ServerLevel level,
            ItemStack stack,
            Supplier<ItemEntity> entityFactory) {
        Context context = activeContext(level);
        if (context == null || !context.isCurrentOriginWithinPickupRadius()) {
            return false;
        }

        context.player().getInventory().add(stack);
        if (stack.isEmpty()) return true;
        if (!context.quickShulkerOverflow()) return false;

        context.defer(stack, () -> {
            ItemEntity entity = entityFactory.get();
            entity.setDefaultPickUpDelay();
            level.addFreshEntity(entity);
        });
        return true;
    }

    /**
     * Captures a container stack before vanilla splits it into item entities.
     * The source is emptied when deferred to preserve dropItemStack's contract;
     * only a post-storage remainder is replayed through the vanilla method.
     */
    public static boolean captureContainerDrop(
            ServerLevel level,
            double x,
            double y,
            double z,
            ItemStack source,
            Consumer<ItemStack> remainderSpawner) {
        Context context = activeContext(level);
        if (context == null
                || !context.isDropOriginWithinPickupRadius(x, y, z)) {
            return false;
        }

        context.player().getInventory().add(source);
        if (source.isEmpty()) return true;
        if (!context.quickShulkerOverflow()) return false;

        ItemStack deferred = source.copy();
        source.setCount(0);
        context.defer(deferred, () -> remainderSpawner.accept(deferred));
        return true;
    }

    /**
     * Returns true when the entity's entire stack was captured and its spawn
     * should be cancelled. Partially inserted stacks remain on the entity so
     * vanilla can spawn the exact remainder.
     */
    public static boolean capture(ServerLevel level, Entity entity) {
        Context context = activeContext(level);
        if (context == null || !(entity instanceof ItemEntity itemEntity)) {
            return false;
        }
        if (!context.isWithinPickupRadius(itemEntity)) return false;

        ItemStack remainder = itemEntity.getItem();
        context.player().getInventory().add(remainder);
        if (!remainder.isEmpty() && context.quickShulkerOverflow()) {
            context.defer(remainder, () -> level.addFreshEntity(itemEntity));
            return true;
        }
        return remainder.isEmpty();
    }

    private static Context activeContext(ServerLevel level) {
        Context context = ACTIVE.get();
        return context != null && context.level() == level ? context : null;
    }

    private static final class Context {
        private final ServerLevel level;
        private final ServerPlayer player;
        private final boolean quickShulkerOverflow;
        private final double pickupRadiusSquared;
        private final List<DeferredDrop> deferred = new ArrayList<>();
        private BlockPos currentOrigin;

        private Context(ServerLevel level,
                        ServerPlayer player,
                        boolean quickShulkerOverflow,
                        int pickupRadius) {
            this.level = level;
            this.player = player;
            this.quickShulkerOverflow = quickShulkerOverflow;
            this.pickupRadiusSquared = (double) pickupRadius * pickupRadius;
        }

        private ServerLevel level() {
            return level;
        }

        private ServerPlayer player() {
            return player;
        }

        private boolean quickShulkerOverflow() {
            return quickShulkerOverflow;
        }

        private boolean isWithinPickupRadius(ItemEntity itemEntity) {
            return pickupRadiusSquared > 0.0
                    && player.distanceToSqr(itemEntity) <= pickupRadiusSquared;
        }

        private boolean isWithinPickupRadius(double x, double y, double z) {
            return pickupRadiusSquared > 0.0
                    && player.distanceToSqr(x, y, z) <= pickupRadiusSquared;
        }

        private boolean isCurrentOriginWithinPickupRadius() {
            return currentOrigin != null
                    && isWithinPickupRadius(
                            currentOrigin.getX() + 0.5,
                            currentOrigin.getY() + 0.5,
                            currentOrigin.getZ() + 0.5);
        }

        private boolean isDropOriginWithinPickupRadius(
                double fallbackX,
                double fallbackY,
                double fallbackZ) {
            return currentOrigin != null
                    ? isCurrentOriginWithinPickupRadius()
                    : isWithinPickupRadius(fallbackX, fallbackY, fallbackZ);
        }

        private boolean withDropOrigin(BlockPos origin, BooleanSupplier action) {
            BlockPos previous = currentOrigin;
            currentOrigin = origin;
            try {
                return action.getAsBoolean();
            } finally {
                currentOrigin = previous;
            }
        }

        private void defer(ItemStack stack, Runnable remainderSpawner) {
            deferred.add(new DeferredDrop(stack, remainderSpawner));
        }

        private void finish() {
            if (quickShulkerOverflow) {
                List<ItemStack> remainders = deferred.stream()
                        .map(DeferredDrop::stack)
                        .filter(stack -> !stack.isEmpty())
                        .toList();
                QuickShulkerIntegration.insertOverflow(player, remainders);
            }
            for (DeferredDrop drop : deferred) {
                if (!drop.stack().isEmpty()) {
                    drop.spawnRemainder().run();
                }
            }
        }
    }

    private record DeferredDrop(ItemStack stack, Runnable spawnRemainder) {
    }
}
