package org.edtp.chainveinfabric.compat.quickshulker;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

/** Reflection-isolated bridge to Quick Shulker's standard Fabric storage resolver. */
final class QuickStorageDirectBridge {
    private static final Method FIND_CARRIED = findApi();

    private QuickStorageDirectBridge() {
    }

    static boolean isUsable() {
        return FIND_CARRIED != null;
    }

    static SlottedStorage<ItemVariant> find(ServerPlayer player, int slot) {
        if (FIND_CARRIED == null || player == null) return null;
        try {
            Object result = FIND_CARRIED.invoke(null, player, slot);
            Object storage = result instanceof Optional<?> optional
                    ? optional.orElse(null) : null;
            if (!(storage instanceof SlottedStorage<?> slotted)) return null;
            @SuppressWarnings("unchecked")
            SlottedStorage<ItemVariant> items =
                    (SlottedStorage<ItemVariant>) slotted;
            return items;
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(
                    "Quick Shulker storage resolver is inaccessible", error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error fatal) throw fatal;
            throw new IllegalStateException(
                    "Quick Shulker storage resolution failed", cause);
        }
    }

    private static Method findApi() {
        try {
            Class<?> facade = Class.forName(
                    "net.kyrptonaught.quickshulker.api.shulker.server.ShulkerStorages",
                    false,
                    QuickStorageDirectBridge.class.getClassLoader());
            return facade.getMethod("findCarried", ServerPlayer.class, int.class);
        } catch (ReflectiveOperationException | LinkageError error) {
            return null;
        }
    }
}
