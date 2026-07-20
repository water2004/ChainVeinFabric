package org.edtp.chainveinfabric.client.compat.litematica;

import net.fabricmc.loader.api.FabricLoader;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig.ChainMode;

public final class LitematicaIntegration {
    private static final boolean AVAILABLE = FabricLoader.getInstance().isModLoaded("litematica");

    private LitematicaIntegration() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static LitematicaContext createContext(ChainMode mode, boolean respectRenderLayer) {
        if (!AVAILABLE || !mode.isSchematicMode()) {
            return LitematicaContext.NONE;
        }

        return LitematicaBridge.createContext(mode, respectRenderLayer);
    }

    public static LitematicaImportSnapshot createImportSnapshot(ChainMode mode, boolean respectRenderLayer) {
        if (!AVAILABLE || !mode.isSchematicMode()) {
            return LitematicaImportSnapshot.EMPTY;
        }

        return LitematicaBridge.createImportSnapshot(mode, respectRenderLayer);
    }
}
