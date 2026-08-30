package org.edtp.chainveinfabric.client.gui.malilib;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Verifies that every configuration tab can be initialized and rendered. */
public final class ConfigurationScreenClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> client.gui.setScreen(new GuiChainVein()));
        context.waitFor(client -> client.gui.screen() instanceof GuiChainVein);
        context.waitTicks(2);

        for (GuiChainVein.Tab tab : GuiChainVein.Tab.values()) {
            context.runOnClient(client -> {
                if (!(client.gui.screen() instanceof GuiChainVein screen)) {
                    throw new AssertionError("The ChainVein configuration screen was closed");
                }
                screen.selectTab(tab);
            });
            context.waitTicks(2);
        }

        context.runOnClient(client -> client.gui.setScreen(null));
    }
}
