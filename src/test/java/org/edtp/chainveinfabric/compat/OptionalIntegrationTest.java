package org.edtp.chainveinfabric.compat;

import org.edtp.chainveinfabric.client.compat.litematica.LitematicaContext;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaImportSnapshot;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaIntegration;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.compat.quickshulker.QuickShulkerIntegration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class OptionalIntegrationTest {
    @Test
    void optionalModsCanBeAbsentWithoutLinkingTheirApis() {
        assertFalse(QuickShulkerIntegration.isAvailable());
        assertFalse(LitematicaIntegration.isAvailable());
        assertSame(LitematicaContext.NONE, LitematicaIntegration.createContext(
                ChainVeinConfig.ChainMode.SCHEMATIC_WRONG, true));
        assertSame(LitematicaImportSnapshot.EMPTY, LitematicaIntegration.createImportSnapshot(
                ChainVeinConfig.ChainMode.SCHEMATIC_EXTRA, true));
    }
}
