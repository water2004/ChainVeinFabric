package org.edtp.chainveinfabric.server;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import org.edtp.chainveinfabric.compat.quickshulker.QuickShulkerIntegration;

/** Redirects vanilla block drops produced during one server mining operation. */
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

    /**
     * Returns true when the entity's entire stack was captured and its spawn
     * should be cancelled. Partially inserted stacks remain on the entity so
     * vanilla can spawn the exact remainder.
     */
    public static boolean capture(ServerLevel level, Entity entity) {
        Context context = ACTIVE.get();
        if (context == null || context.level() != level || !(entity instanceof ItemEntity itemEntity)) {
            return false;
        }
        if (!context.isWithinPickupRadius(itemEntity)) return false;

        ItemStack remainder = itemEntity.getItem();
        context.player().getInventory().add(remainder);
        if (!remainder.isEmpty() && context.quickShulkerOverflow()) {
            context.defer(itemEntity);
            return true;
        }
        return remainder.isEmpty();
    }

    private static final class Context {
        private final ServerLevel level;
        private final ServerPlayer player;
        private final boolean quickShulkerOverflow;
        private final double pickupRadiusSquared;
        private final List<ItemEntity> deferred = new ArrayList<>();

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

        private void defer(ItemEntity itemEntity) {
            deferred.add(itemEntity);
        }

        private void finish() {
            if (quickShulkerOverflow) {
                List<ItemStack> remainders = deferred.stream()
                        .map(ItemEntity::getItem)
                        .filter(stack -> !stack.isEmpty())
                        .toList();
                QuickShulkerIntegration.insertOverflow(player, remainders);
            }
            for (ItemEntity itemEntity : deferred) {
                if (!itemEntity.getItem().isEmpty()) {
                    level.addFreshEntity(itemEntity);
                }
            }
        }
    }
}
