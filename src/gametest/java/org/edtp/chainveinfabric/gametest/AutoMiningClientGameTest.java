package org.edtp.chainveinfabric.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.input.MouseButtonInfo;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig.ChainMode;
import org.edtp.chainveinfabric.client.gui.malilib.GuiChainVein;
import org.edtp.chainveinfabric.client.logic.AutoMiningController.Status;
import org.edtp.chainveinfabric.client.logic.AutoMiningTrigger;

import static org.edtp.chainveinfabric.gametest.AutoMiningTestSupport.check;

/** AUTO button input, empty-search cooldowns, and cancellation independent of configuration tests. */
public final class AutoMiningClientGameTest implements FabricClientGameTest {
    private static final int COOLDOWN = 40;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (var snapshot = new AutoMiningTestSupport.ConfigSnapshot(context);
             TestSingleplayerContext world = context.worldBuilder().create()) {
            AutoMiningTestSupport.prepareWorld(context, world);
            testToggleClicks(context);
            for (ChainMode mode : new ChainMode[] {
                    ChainMode.SCHEMATIC_SELECTION, ChainMode.SCHEMATIC_EXTRA, ChainMode.SCHEMATIC_WRONG
            }) {
                testEmptySearchRepeats(context, mode);
                testModeSwitchDisarms(context);
            }
            testOrdinaryWaitsForAttack(context);
            testDisableDisarms(context);
        }
    }

    private static void testEmptySearchRepeats(ClientGameTestContext context, ChainMode mode) {
        context.runOnClient(client -> ChainveinfabricClient.CONFIG.getWhitelist(mode).clear());
        AutoMiningTestSupport.arm(context, mode, COOLDOWN);
        context.waitFor(client -> ChainveinfabricClient.getAutoMiningStatus() == Status.COOLDOWN);
        int initial = context.computeOnClient(client -> ChainveinfabricClient.getAutoMiningCooldownTicks());
        context.waitTicks(5);
        int remaining = context.computeOnClient(client -> ChainveinfabricClient.getAutoMiningCooldownTicks());
        check(remaining > 0 && remaining < initial, "Empty search did not respect the cooldown in " + mode);
        context.runOnClient(client -> {
            check(ChainveinfabricClient.handleAutoMiningAttack(), "AUTO did not consume attack input in " + mode);
            check(ChainveinfabricClient.getAutoMiningCooldownTicks() <= remaining
                            && ChainveinfabricClient.getAutoMiningCooldownTicks() > 0,
                    "Attack input bypassed the cooldown in " + mode);
        });
        context.waitFor(client -> ChainveinfabricClient.getAutoMiningCooldownTicks() > remaining);
    }

    private static void testModeSwitchDisarms(ClientGameTestContext context) {
        context.runOnClient(client -> ChainveinfabricClient.CONFIG.mode = ChainMode.CHAIN_MINE);
        context.waitFor(client -> !ChainveinfabricClient.isAutoMiningArmed());
    }

    private static void testOrdinaryWaitsForAttack(ClientGameTestContext context) {
        AutoMiningTestSupport.arm(context, ChainMode.CHAIN_MINE, COOLDOWN);
        context.waitTicks(COOLDOWN + 5);
        context.runOnClient(client -> check(ChainveinfabricClient.getAutoMiningStatus() == Status.ARMED,
                "Ordinary AUTO started searching without a left click"));
    }

    private static void testDisableDisarms(ClientGameTestContext context) {
        context.runOnClient(client -> ChainveinfabricClient.CONFIG.isChainVeinEnabled = false);
        context.waitFor(client -> !ChainveinfabricClient.isAutoMiningArmed());
    }

    private static void testToggleClicks(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ChainveinfabricClient.disarmAutoMining();
            ChainveinfabricClient.CONFIG.mode = ChainMode.CHAIN_MINE;
            ChainveinfabricClient.CONFIG.isChainVeinEnabled = false;
        });
        context.setScreen(AutoToggleTestScreen::new);
        context.waitForScreen(AutoToggleTestScreen.class);
        context.waitTicks(2);
        double[] cursor = context.computeOnClient(client -> {
            ButtonBase toggle = ((AutoToggleTestScreen) client.gui.screen()).toggle;
            check(toggle != null, "The mining toggle button was not found");
            var window = client.getWindow();
            return new double[] {
                    (toggle.getX() + toggle.getWidth() / 2.0)
                            * window.getScreenWidth() / window.getGuiScaledWidth(),
                    (toggle.getY() + toggle.getHeight() / 2.0)
                            * window.getScreenHeight() / window.getGuiScaledHeight()
            };
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        try {
            clickToggle(context, true, true, true, "Ctrl-click from OFF did not arm AUTO");
            clickToggle(context, false, false, false, "Clicking AUTO did not disable chaining");
            clickToggle(context, false, true, false, "A normal click did not enable chaining");
            clickToggle(context, true, true, true, "Ctrl-click from ON did not arm AUTO");
            clickToggle(context, true, false, false, "Ctrl-clicking AUTO did not disable chaining");
            clickToggle(context, false, true, false, "Releasing Ctrl left the toggle in AUTO mode");
            clickToggle(context, false, false, false, "A normal click did not disable chaining");
        } finally {
            context.getInput().releaseControl();
            context.runOnClient(client -> {
                ChainveinfabricClient.disarmAutoMining();
                client.gui.setScreen(null);
            });
        }
    }

    private static void clickToggle(ClientGameTestContext context, boolean control,
                                    boolean enabled, boolean automatic, String message) {
        if (control) context.getInput().holdControl();
        try {
            if (control) {
                // TestInput.pressMouse drops modifiers; send the modified event through the real handler.
                context.runOnClient(client -> {
                    var click = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, InputConstants.MOD_CONTROL);
                    client.mouseHandler.onButton(client.getWindow().handle(), click, 1);
                    client.mouseHandler.onButton(client.getWindow().handle(), click, 0);
                });
            } else {
                context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            }
        } finally {
            if (control) context.getInput().releaseControl();
        }
        context.runOnClient(client -> check(ChainveinfabricClient.CONFIG.isChainVeinEnabled == enabled
                && ChainveinfabricClient.isAutoMiningArmed() == automatic, message));
    }

    private static final class AutoToggleTestScreen extends GuiChainVein {
        private ButtonBase toggle;

        @Override
        public <T extends ButtonBase> T addButton(T button, IButtonActionListener listener) {
            if (button.getHoverStrings().contains(StringUtils.translate(
                    AutoMiningTrigger.forMode(ChainMode.CHAIN_MINE).enableTooltipKey()))) {
                this.toggle = button;
            }
            return super.addButton(button, listener);
        }
    }
}
