package org.edtp.chainveinfabric.server;

import java.util.function.BooleanSupplier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import org.edtp.chainveinfabric.compat.quickshulker.QuickShulkerIntegration;

/** Redirects item entities produced by one vanilla block break into the miner's inventory. */
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
        ACTIVE.set(new Context(
                (ServerLevel) player.level(), player, quickShulkerOverflow,
                (double) pickupRadius * pickupRadius));
        try {
            return action.getAsBoolean();
        } finally {
            if (previous != null) {
                ACTIVE.set(previous);
            } else {
                ACTIVE.remove();
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
            QuickShulkerIntegration.insertOverflow(context.player(), remainder);
        }
        return remainder.isEmpty();
    }

    private record Context(ServerLevel level, ServerPlayer player,
                           boolean quickShulkerOverflow, double pickupRadiusSquared) {
        private boolean isWithinPickupRadius(ItemEntity itemEntity) {
            return pickupRadiusSquared > 0.0
                    && player.distanceToSqr(itemEntity) <= pickupRadiusSquared;
        }
    }
}
