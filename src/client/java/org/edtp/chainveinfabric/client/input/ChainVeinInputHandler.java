package org.edtp.chainveinfabric.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaIntegration;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.gui.malilib.ConfigProxies;
import org.edtp.chainveinfabric.client.gui.malilib.GuiChainVein;
import org.edtp.chainveinfabric.client.logic.PlantingItems;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ChainVeinInputHandler implements IKeybindProvider {
    private static final ChainVeinInputHandler INSTANCE = new ChainVeinInputHandler();

    private ChainVeinInputHandler() {
        ConfigProxies.OPEN_CONFIG.getKeybind().setCallback(new OpenConfigCallback());
        ConfigProxies.TOGGLE_CHAIN_VEIN.getKeybind().setCallback(new ToggleChainVeinCallback());
        ConfigProxies.CYCLE_MODE.getKeybind().setCallback(new CycleModeCallback());
        for (ChainVeinConfig.ChainMode mode : ChainVeinConfig.ChainMode.values()) {
            ConfigProxies.getModeHotkey(mode).getKeybind().setCallback(new SwitchModeCallback(mode));
        }
        ConfigProxies.TOGGLE_TARGET_WHITELIST.getKeybind().setCallback(new ToggleTargetWhitelistCallback());
    }

    public static ChainVeinInputHandler getInstance() {
        return INSTANCE;
    }

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkey hotkey : ConfigProxies.getAvailableHotkeys()) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory(
                "ChainVeinFabric",
                "key.category.chainveinfabric.general",
                ConfigProxies.getAvailableHotkeys()
        );
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
            if (!ChainveinfabricClient.CONFIG.isChainVeinEnabled) {
                ChainveinfabricClient.disarmAutoMining();
            }
            ChainveinfabricClient.CONFIG.save();

            Minecraft client = Minecraft.getInstance();
            if (client.gui != null) {
                String messageKey = ChainveinfabricClient.CONFIG.isChainVeinEnabled
                        ? "message.chainveinfabric.enabled"
                        : "message.chainveinfabric.disabled";
                client.gui.hud.setOverlayMessage(Component.translatable(messageKey), false);
            }

            return true;
        }
    }

    private static class CycleModeCallback implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            if (ChainveinfabricClient.CONFIG == null) {
                return false;
            }

            List<ChainVeinConfig.ChainMode> modes = Arrays.stream(ChainVeinConfig.ChainMode.values())
                    .filter(ChainVeinInputHandler::isModeAvailable)
                    .toList();
            int currentIndex = modes.indexOf(ChainveinfabricClient.CONFIG.mode);
            int nextIndex = currentIndex >= 0 ? (currentIndex + 1) % modes.size() : 0;
            return switchMode(modes.get(nextIndex));
        }
    }

    private record SwitchModeCallback(ChainVeinConfig.ChainMode mode) implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            return switchMode(this.mode);
        }
    }

    private static boolean switchMode(ChainVeinConfig.ChainMode mode) {
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        if (config == null || !isModeAvailable(mode)) {
            return false;
        }

        ChainveinfabricClient.disarmAutoMining();
        config.mode = mode;
        if (config.enableChainVeinOnModeHotkey) {
            config.isChainVeinEnabled = true;
        }
        config.save();

        String suffix = mode.name().toLowerCase(Locale.ROOT).replace("chain_", "");
        Component modeName = Component.translatable("options.chainveinfabric.mode." + suffix);
        String messageKey = config.enableChainVeinOnModeHotkey
                ? "message.chainveinfabric.modeSwitchedAndEnabled"
                : "message.chainveinfabric.modeSwitched";
        Minecraft client = Minecraft.getInstance();
        if (client.gui != null) {
            client.gui.hud.setOverlayMessage(Component.translatable(messageKey, modeName), false);
        }
        return true;
    }

    private static boolean isModeAvailable(ChainVeinConfig.ChainMode mode) {
        return !mode.isSchematicMode() || LitematicaIntegration.isAvailable();
    }

    private static class ToggleTargetWhitelistCallback implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            if (ChainveinfabricClient.CONFIG == null) {
                return false;
            }

            Minecraft client = Minecraft.getInstance();
            WhitelistTarget target = ChainveinfabricClient.CONFIG.mode == ChainVeinConfig.ChainMode.CHAIN_PLANT
                    ? getHeldPlantingTarget(client)
                    : getTargetedBlock(client);
            if (target == null) {
                return false;
            }

            Set<String> whitelist = getWhitelist(ChainveinfabricClient.CONFIG);
            boolean removed;
            if (whitelist.contains(target.id())) {
                whitelist.remove(target.id());
                removed = true;
            } else {
                whitelist.add(target.id());
                removed = false;
            }

            ChainveinfabricClient.CONFIG.save();
            showOverlay(client, Component.translatable(
                    removed ? "message.chainveinfabric.whitelist.removed" : "message.chainveinfabric.whitelist.added",
                    target.displayName()
            ));
            return true;
        }

        private static Set<String> getWhitelist(ChainVeinConfig config) {
            return config.getWhitelist(config.mode);
        }

        private static WhitelistTarget getHeldPlantingTarget(Minecraft client) {
            if (client.player == null) {
                showOverlay(client, Component.translatable("message.chainveinfabric.whitelist.noPlantableHeld"));
                return null;
            }

            ItemStack stack = client.player.getMainHandItem();
            if (stack.isEmpty() || !PlantingItems.isPlantable(stack.getItem())) {
                showOverlay(client, Component.translatable("message.chainveinfabric.whitelist.noPlantableHeld"));
                return null;
            }

            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return new WhitelistTarget(id.toString(), stack.getHoverName());
        }

        private static WhitelistTarget getTargetedBlock(Minecraft client) {
            if (client.level == null || client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) {
                showOverlay(client, Component.translatable("message.chainveinfabric.whitelist.noTarget"));
                return null;
            }

            BlockPos pos = ((BlockHitResult) client.hitResult).getBlockPos();
            BlockState state = client.level.getBlockState(pos);
            Block block = state.getBlock();
            Item item = block.asItem();
            if (item == Items.AIR) {
                showOverlay(client, Component.translatable("message.chainveinfabric.whitelist.noTarget"));
                return null;
            }

            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            return new WhitelistTarget(id.toString(), new ItemStack(item).getHoverName());
        }

        private static void showOverlay(Minecraft client, Component message) {
            if (client.gui != null) {
                client.gui.hud.setOverlayMessage(message, false);
            }
        }
    }

    private record WhitelistTarget(String id, Component displayName) {
    }
}
