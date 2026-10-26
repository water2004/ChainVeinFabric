package org.edtp.chainveinfabric.gametest;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.api.ChainVeinClientApi;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig.ChainMode;
import org.edtp.chainveinfabric.client.config.preset.ConfigPreset;
import org.edtp.chainveinfabric.client.gui.malilib.ConfigProxies;

/** World setup and configuration isolation shared only by AUTO game tests. */
final class AutoMiningTestSupport {
    private AutoMiningTestSupport() {
    }

    static void prepareWorld(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.getAbilities().invulnerable = true;
            player.onUpdateAbilities();
            player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).setBaseValue(64);
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SHOVEL));
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.sendAllDataToRemote();
            for (int x = -2; x <= 7; x++) {
                for (int z = -2; z <= 2; z++) {
                    server.overworld().setBlockAndUpdate(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState());
                }
            }
        });
        world.getServer().runCommand("gamemode survival @p");
        world.getServer().runCommand("tp @p 0.5 65 0.5");
        context.waitFor(client -> client.player != null && client.level != null
                && client.player.blockPosition().equals(new BlockPos(0, 65, 0))
                && client.player.getMainHandItem().is(Items.DIAMOND_SHOVEL)
                && !client.player.isCreative());
    }

    static void arm(ClientGameTestContext context, ChainMode mode, int cooldown) {
        context.runOnClient(client -> {
            ChainveinfabricClient.disarmAutoMining();
            ChainVeinClientApi.clear();
            ChainVeinConfig config = ChainveinfabricClient.CONFIG;
            config.mode = mode;
            config.autoMineCooldownTicks = cooldown;
            check(ChainveinfabricClient.toggleAutoMining(), "Could not enable AUTO in " + mode);
        });
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static final class ConfigSnapshot implements AutoCloseable {
        private final ClientGameTestContext context;
        private final ConfigPreset settings;
        private final Map<ChainMode, Set<String>> whitelists;

        ConfigSnapshot(ClientGameTestContext context) {
            this.context = context;
            this.settings = context.computeOnClient(client ->
                    ConfigPreset.create("auto-test", "Auto Test", ChainveinfabricClient.CONFIG));
            this.whitelists = context.computeOnClient(client -> {
                Map<ChainMode, Set<String>> entries = new EnumMap<>(ChainMode.class);
                for (ChainMode mode : ChainMode.values()) {
                    entries.put(mode, Set.copyOf(ChainveinfabricClient.CONFIG.getWhitelist(mode)));
                }
                return entries;
            });
        }

        @Override
        public void close() {
            this.context.runOnClient(client -> {
                ChainveinfabricClient.disarmAutoMining();
                client.gui.setScreen(null);
                this.settings.applyTo(ChainveinfabricClient.CONFIG);
                this.whitelists.forEach(ChainveinfabricClient.CONFIG::replaceWhitelist);
                ConfigProxies.load();
                ChainveinfabricClient.CONFIG.save();
            });
        }
    }
}
