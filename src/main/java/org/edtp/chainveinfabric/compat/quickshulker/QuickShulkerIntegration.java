package org.edtp.chainveinfabric.compat.quickshulker;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Safe optional-dependency boundary for Quick Shulker.
 *
 * <p>This class deliberately contains no Quick Shulker types, so it remains
 * loadable when the mod is absent. All linked API calls live in the bridge
 * class and are reached only after the API package has been detected.</p>
 */
public final class QuickShulkerIntegration {
    public static final String MOD_ID = "quickshulker";

    private static boolean legacyAvailable = detectLegacyAvailability();
    private static boolean directAvailable = FabricLoader.getInstance().isModLoaded(MOD_ID)
            && QuickStorageDirectBridge.isUsable();

    private QuickShulkerIntegration() {
    }

    public static boolean isAvailable() {
        return directAvailable || legacyAvailable;
    }

    public static int insertOverflow(ServerPlayer player, ItemStack remainder) {
        if (!isAvailable() || remainder.isEmpty()) return 0;

        if (directAvailable) {
            try {
                return QuickStorageDirectBridge.insertIntoCarriedShulkerBoxes(player, remainder);
            } catch (RuntimeException | LinkageError error) {
                directAvailable = false;
            }
        }
        if (legacyAvailable) {
            try {
                return QuickShulkerBridge.insertIntoCarriedShulkerBoxes(player, remainder);
            } catch (RuntimeException | LinkageError error) {
                legacyAvailable = false;
            }
        }
        return 0;
    }

    private static boolean detectLegacyAvailability() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return false;

        try {
            ClassLoader loader = QuickShulkerIntegration.class.getClassLoader();
            Class.forName("net.kyrptonaught.quickshulker.api.QuickOpenableRegistry", false, loader);
            Class.forName("net.kyrptonaught.quickshulker.api.QuickShulkerData", false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError error) {
            return false;
        }
    }
}
