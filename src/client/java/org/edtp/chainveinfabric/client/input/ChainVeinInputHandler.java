package org.edtp.chainveinfabric.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.gui.malilib.ConfigProxies;
import org.edtp.chainveinfabric.client.gui.malilib.GuiChainVein;

public class ChainVeinInputHandler implements IKeybindProvider {
    private static final ChainVeinInputHandler INSTANCE = new ChainVeinInputHandler();

    private ChainVeinInputHandler() {
        ConfigProxies.OPEN_CONFIG.getKeybind().setCallback(new OpenConfigCallback());
        ConfigProxies.TOGGLE_CHAIN_VEIN.getKeybind().setCallback(new ToggleChainVeinCallback());
    }

    public static ChainVeinInputHandler getInstance() {
        return INSTANCE;
    }

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkey hotkey : ConfigProxies.HOTKEY_LIST) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory("ChainVeinFabric", "key.category.chainveinfabric.general", ConfigProxies.HOTKEY_LIST);
    }

    private static class OpenConfigCallback implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            GuiBase.openGui(new GuiChainVein());
            return true;
        }
    }

    private static class ToggleChainVeinCallback implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            if (ChainveinfabricClient.CONFIG == null) {
                return false;
            }

            ChainveinfabricClient.CONFIG.isChainVeinEnabled = !ChainveinfabricClient.CONFIG.isChainVeinEnabled;
            ChainveinfabricClient.CONFIG.save();

            Minecraft client = Minecraft.getInstance();
            if (client.gui != null) {
                String messageKey = ChainveinfabricClient.CONFIG.isChainVeinEnabled
                        ? "message.chainveinfabric.enabled"
                        : "message.chainveinfabric.disabled";
                client.gui.setOverlayMessage(Component.translatable(messageKey), false);
            }

            return true;
        }
    }
}
