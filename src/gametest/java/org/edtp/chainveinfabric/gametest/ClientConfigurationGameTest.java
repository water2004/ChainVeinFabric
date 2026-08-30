package org.edtp.chainveinfabric.gametest;

import fi.dy.masa.malilib.event.InputEventHandler;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.gui.malilib.ConfigProxies;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

/** Exercises the real malilib controls and raw-key callbacks used by players. */
public final class ClientConfigurationGameTest implements FabricClientGameTest {
    private static final BlockPos TARGET = new BlockPos(0, 65, 0);

    @Override
    public void runTest(ClientGameTestContext context) {
        ClientConfigSnapshot snapshot = context.computeOnClient(
                client -> ClientConfigSnapshot.capture(ChainveinfabricClient.CONFIG));
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            prepareWorld(singleplayer);
            singleplayer.getServer().runCommand("tp @p 0.5 65 -3.5");

            context.waitTicks(5);
            singleplayer.getClientLevel().waitForChunksDownload();
            context.waitFor(client -> client.player != null
                    && client.level != null
                    && client.level.getBlockState(TARGET).is(Blocks.STONE)
                    && client.player.getMainHandItem().is(Items.WHEAT_SEEDS));

            configureAndTestControls(context);
            testModeHotkeys(context);
            testHeldPlantWhitelistHotkey(context, singleplayer);
            testTargetedBlockWhitelistHotkey(context, singleplayer);
        } finally {
            restoreClientConfiguration(context, snapshot);
        }
    }

    private static void configureAndTestControls(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ChainVeinConfig config = ChainveinfabricClient.CONFIG;
            ChainveinfabricClient.disarmAutoMining();
            config.mode = ChainVeinConfig.ChainMode.CHAIN_MINE;
            config.isChainVeinEnabled = false;
            config.enableChainVeinOnModeHotkey = false;
            config.maxChainBlocks = 73;
            config.directToInventory = false;
            config.toggleChainVeinHotkey = "F6";
            config.cycleModeHotkey = "F7";
            config.switchToMineModeHotkey = "F4";
            config.switchToPlantModeHotkey = "F5";
            config.switchToUtilityModeHotkey = "F8";
            config.toggleTargetWhitelistHotkey = "F9";
            config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_MINE).clear();
            config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_PLANT).clear();

            ConfigProxies.load();
            InputEventHandler.getKeybindManager().updateUsedKeys();
            assertState(ConfigProxies.MAX_BLOCKS.getIntegerValue() == 73,
                    "The settings proxy did not load max blocks");
            assertState(!ConfigProxies.DIRECT_INV.getBooleanValue(),
                    "The settings proxy did not load direct-to-inventory");

            // These are the same option objects changed by the settings screen.
            ConfigProxies.MAX_BLOCKS.setIntegerValue(137);
            ConfigProxies.DIRECT_INV.setBooleanValue(true);
            assertState(config.maxChainBlocks == 137,
                    "Changing the max-blocks control did not update the config");
            assertState(config.directToInventory,
                    "Changing the direct-to-inventory control did not update the config");
            assertState("F7".equals(config.cycleModeHotkey),
                    "Saving another control lost a configured hotkey");
        });
    }

    private static void testModeHotkeys(ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_F6);
        assertClientState(context, config -> config.isChainVeinEnabled,
                "The toggle hotkey did not enable chaining");
        context.getInput().pressKey(GLFW.GLFW_KEY_F6);
        assertClientState(context, config -> !config.isChainVeinEnabled,
                "The toggle hotkey did not disable chaining");

        context.runOnClient(client -> {
            ChainVeinConfig config = ChainveinfabricClient.CONFIG;
            config.mode = ChainVeinConfig.ChainMode.CHAIN_MINE;
            config.isChainVeinEnabled = true;
            config.enableChainVeinOnModeHotkey = false;
            if (!ChainveinfabricClient.toggleAutoMining()) {
                throw new AssertionError("Could not arm automatic mining before a mode switch");
            }
        });
        context.getInput().pressKey(GLFW.GLFW_KEY_F7);
        assertClientState(context, config ->
                        config.mode == ChainVeinConfig.ChainMode.CHAIN_PLANT
                                && config.isChainVeinEnabled
                                && !ChainveinfabricClient.isAutoMiningArmed(),
                "Cycling modes did not select Planting and disarm automatic mining");

        context.runOnClient(client -> {
            ChainveinfabricClient.CONFIG.isChainVeinEnabled = false;
            ChainveinfabricClient.CONFIG.enableChainVeinOnModeHotkey = true;
        });
        context.getInput().pressKey(GLFW.GLFW_KEY_F8);
        assertClientState(context, config ->
                        config.mode == ChainVeinConfig.ChainMode.CHAIN_UTILITY
                                && config.isChainVeinEnabled,
                "The direct mode hotkey did not select Utility and enable chaining");
    }

    private static void testHeldPlantWhitelistHotkey(
            ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        context.runOnClient(client -> {
            ChainveinfabricClient.CONFIG.enableChainVeinOnModeHotkey = false;
            ChainveinfabricClient.CONFIG.getWhitelist(
                    ChainVeinConfig.ChainMode.CHAIN_PLANT).clear();
        });
        context.getInput().pressKey(GLFW.GLFW_KEY_F5);
        context.getInput().pressKey(GLFW.GLFW_KEY_F9);
        assertClientState(context, config -> config.getWhitelist(
                        ChainVeinConfig.ChainMode.CHAIN_PLANT).contains("minecraft:wheat_seeds"),
                "The whitelist hotkey did not add the held planting item");

        context.getInput().pressKey(GLFW.GLFW_KEY_F9);
        assertClientState(context, config -> config.getWhitelist(
                        ChainVeinConfig.ChainMode.CHAIN_PLANT).isEmpty(),
                "The whitelist hotkey did not remove the held planting item");

        setMainHand(singleplayer, new ItemStack(Items.STONE));
        context.waitFor(client -> client.player != null
                && client.player.getMainHandItem().is(Items.STONE));
        context.getInput().pressKey(GLFW.GLFW_KEY_F9);
        assertClientState(context, config -> config.getWhitelist(
                        ChainVeinConfig.ChainMode.CHAIN_PLANT).isEmpty(),
                "Planting mode accepted a non-plantable held item");
    }

    private static void testTargetedBlockWhitelistHotkey(
            ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        setMainHand(singleplayer, new ItemStack(Items.DIAMOND_PICKAXE));
        context.waitFor(client -> client.player != null
                && client.player.getMainHandItem().is(Items.DIAMOND_PICKAXE));
        context.getInput().pressKey(GLFW.GLFW_KEY_F4);
        context.getInput().lookAt(TARGET);
        context.waitTick();
        context.waitFor(client -> client.hitResult instanceof BlockHitResult hit
                && hit.getBlockPos().equals(TARGET));

        context.getInput().pressKey(GLFW.GLFW_KEY_F9);
        assertClientState(context, config -> config.getWhitelist(
                        ChainVeinConfig.ChainMode.CHAIN_MINE).contains("minecraft:stone"),
                "The whitelist hotkey did not add the targeted block item");
        context.getInput().pressKey(GLFW.GLFW_KEY_F9);
        assertClientState(context, config -> config.getWhitelist(
                        ChainVeinConfig.ChainMode.CHAIN_MINE).isEmpty(),
                "The whitelist hotkey did not remove the targeted block item");
    }

    private static void prepareWorld(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.setInvulnerable(true);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(Items.WHEAT_SEEDS));
            player.getInventory().setSelectedSlot(0);
            for (int x = -2; x <= 2; x++) {
                for (int z = -5; z <= 2; z++) {
                    server.overworld().setBlockAndUpdate(
                            new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState());
                }
            }
            server.overworld().setBlockAndUpdate(TARGET, Blocks.STONE.defaultBlockState());
            server.overworld().setBlockAndUpdate(TARGET.above(), Blocks.AIR.defaultBlockState());
            player.inventoryMenu.sendAllDataToRemote();
        });
    }

    private static void setMainHand(TestSingleplayerContext singleplayer, ItemStack stack) {
        singleplayer.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().setItem(0, stack);
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.sendAllDataToRemote();
        });
    }

    private static void restoreClientConfiguration(
            ClientGameTestContext context,
            ClientConfigSnapshot snapshot) {
        context.runOnClient(client -> {
            ChainVeinConfig config = ChainveinfabricClient.CONFIG;
            if (config == null) return;
            ChainveinfabricClient.disarmAutoMining();
            snapshot.restore(config);
            ConfigProxies.load();
            InputEventHandler.getKeybindManager().updateUsedKeys();
            config.save();
        });
    }

    private record ClientConfigSnapshot(
            boolean enabled,
            ChainVeinConfig.ChainMode mode,
            boolean enableOnModeHotkey,
            int maxBlocks,
            boolean directToInventory,
            String toggleHotkey,
            String cycleHotkey,
            String mineHotkey,
            String plantHotkey,
            String utilityHotkey,
            String whitelistHotkey,
            Set<String> miningWhitelist,
            Set<String> plantingWhitelist) {
        private static ClientConfigSnapshot capture(ChainVeinConfig config) {
            return new ClientConfigSnapshot(
                    config.isChainVeinEnabled,
                    config.mode,
                    config.enableChainVeinOnModeHotkey,
                    config.maxChainBlocks,
                    config.directToInventory,
                    config.toggleChainVeinHotkey,
                    config.cycleModeHotkey,
                    config.switchToMineModeHotkey,
                    config.switchToPlantModeHotkey,
                    config.switchToUtilityModeHotkey,
                    config.toggleTargetWhitelistHotkey,
                    Set.copyOf(config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_MINE)),
                    Set.copyOf(config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_PLANT)));
        }

        private void restore(ChainVeinConfig config) {
            config.isChainVeinEnabled = enabled;
            config.mode = mode;
            config.enableChainVeinOnModeHotkey = enableOnModeHotkey;
            config.maxChainBlocks = maxBlocks;
            config.directToInventory = directToInventory;
            config.toggleChainVeinHotkey = toggleHotkey;
            config.cycleModeHotkey = cycleHotkey;
            config.switchToMineModeHotkey = mineHotkey;
            config.switchToPlantModeHotkey = plantHotkey;
            config.switchToUtilityModeHotkey = utilityHotkey;
            config.toggleTargetWhitelistHotkey = whitelistHotkey;
            config.replaceWhitelist(ChainVeinConfig.ChainMode.CHAIN_MINE, miningWhitelist);
            config.replaceWhitelist(ChainVeinConfig.ChainMode.CHAIN_PLANT, plantingWhitelist);
        }
    }

    private static void assertClientState(
            ClientGameTestContext context,
            java.util.function.Predicate<ChainVeinConfig> predicate,
            String message) {
        if (!context.computeOnClient(client -> predicate.test(ChainveinfabricClient.CONFIG))) {
            throw new AssertionError(message);
        }
    }

    private static void assertState(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
