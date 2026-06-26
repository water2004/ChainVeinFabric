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
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.gui.malilib.ConfigProxies;
import org.edtp.chainveinfabric.client.gui.malilib.GuiChainVein;

import java.util.Set;

public class ChainVeinInputHandler implements IKeybindProvider {
    private static final ChainVeinInputHandler INSTANCE = new ChainVeinInputHandler();

    private ChainVeinInputHandler() {
        ConfigProxies.OPEN_CONFIG.getKeybind().setCallback(new OpenConfigCallback());
        ConfigProxies.TOGGLE_CHAIN_VEIN.getKeybind().setCallback(new ToggleChainVeinCallback());
        ConfigProxies.TOGGLE_TARGET_WHITELIST.getKeybind().setCallback(new ToggleTargetWhitelistCallback());
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
                client.gui.hud.setOverlayMessage(Component.translatable(messageKey), false);
            }

            return true;
        }
    }

    private static class ToggleTargetWhitelistCallback implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            if (ChainveinfabricClient.CONFIG == null) {
                return false;
            }

            Minecraft client = Minecraft.getInstance();
            if (client.level == null || client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) {
                showOverlay(client, Component.translatable("message.chainveinfabric.whitelist.noTarget"));
                return false;
            }

            BlockPos pos = ((BlockHitResult) client.hitResult).getBlockPos();
            BlockState state = client.level.getBlockState(pos);
            WhitelistTarget target = getWhitelistTarget(ChainveinfabricClient.CONFIG.mode, state);
            if (target == null) {
                showOverlay(client, Component.translatable("message.chainveinfabric.whitelist.noTarget"));
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
            return switch (config.mode) {
                case CHAIN_MINE -> config.whitelistedBlocks;
                case CHAIN_PLANT -> config.whitelistedCrops;
                case CHAIN_UTILITY -> config.whitelistedUtilityBlocks;
            };
        }

        private static WhitelistTarget getWhitelistTarget(ChainVeinConfig.ChainMode mode, BlockState state) {
            Block block = state.getBlock();
            if (mode == ChainVeinConfig.ChainMode.CHAIN_PLANT) {
                Item item = block.asItem();
                if (item == Items.AIR) {
                    return null;
                }
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                return new WhitelistTarget(id.toString(), new ItemStack(item).getHoverName());
            }

            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            return new WhitelistTarget(id.toString(), block.getName());
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
