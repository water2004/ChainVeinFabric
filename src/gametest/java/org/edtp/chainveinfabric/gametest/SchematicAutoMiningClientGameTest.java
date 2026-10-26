package org.edtp.chainveinfabric.gametest;

import java.util.List;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.block.Blocks;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig.ChainMode;
import org.edtp.chainveinfabric.client.logic.AutoMiningController.Status;
import org.edtp.chainveinfabric.client.compat.litematica.LitematicaIntegration;
import org.edtp.chainveinfabric.client.logic.search.SearchConfig;
import org.edtp.chainveinfabric.client.logic.search.SearchEngine;
import org.edtp.chainveinfabric.client.logic.search.SearchRequest;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import static org.edtp.chainveinfabric.gametest.AutoMiningTestSupport.check;
import static org.edtp.chainveinfabric.gametest.LitematicaTestFixture.MATCHING;
import static org.edtp.chainveinfabric.gametest.LitematicaTestFixture.NON_WHITELISTED;
import static org.edtp.chainveinfabric.gametest.LitematicaTestFixture.OUTSIDE;
import static org.edtp.chainveinfabric.gametest.LitematicaTestFixture.TARGET;

/** Mines real schematic targets across consecutive batches, on both protocol paths. */
public final class SchematicAutoMiningClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchematicAutoMiningClientGameTest.class);
    private static final int COOLDOWN = 60;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (var snapshot = new AutoMiningTestSupport.ConfigSnapshot(context);
             TestSingleplayerContext world = context.worldBuilder().create()) {
            AutoMiningTestSupport.prepareWorld(context, world);
            for (ChainMode mode : new ChainMode[] {
                    ChainMode.SCHEMATIC_SELECTION, ChainMode.SCHEMATIC_EXTRA, ChainMode.SCHEMATIC_WRONG
            }) {
                testRepeatedMining(context, world, mode);
            }
        }
    }

    private static void testRepeatedMining(ClientGameTestContext context,
                                          TestSingleplayerContext world, ChainMode mode) {
        prepareTargets(context, world);
        try (var fixture = LitematicaTestFixture.create(context, mode)) {
            configureSearch(context, mode);
            AutoMiningTestSupport.arm(context, mode, COOLDOWN);
            // No attack input is sent at any point in this test.
            for (int batch = 0; batch < 3; batch++) {
                waitForMinedTarget(context, mode, batch + 1);
                waitForServerMining(context, world, mode);
                context.waitFor(client -> ChainveinfabricClient.getAutoMiningStatus() == Status.COOLDOWN);
                assertServerBlocks(world, mode, true);
                if (batch < 2) {
                    world.getServer().runOnServer(server ->
                            server.overworld().setBlockAndUpdate(TARGET, Blocks.DIRT.defaultBlockState()));
                    context.waitFor(client -> client.level.getBlockState(TARGET).is(Blocks.DIRT));
                    context.waitTicks(5);
                    context.runOnClient(client -> check(ChainveinfabricClient.getAutoMiningCooldownTicks() > 0
                                    && client.level.getBlockState(TARGET).is(Blocks.DIRT),
                            "A new target was mined before the cooldown ended in " + mode));
                }
            }
            context.runOnClient(client -> ChainveinfabricClient.disarmAutoMining());
            world.getServer().runOnServer(server ->
                    server.overworld().setBlockAndUpdate(TARGET, Blocks.DIRT.defaultBlockState()));
            context.waitTicks(COOLDOWN + 5);
            assertServerBlocks(world, mode, false);
        } finally {
            context.runOnClient(client -> ChainveinfabricClient.disarmAutoMining());
        }
    }

    private static void configureSearch(ClientGameTestContext context, ChainMode mode) {
        context.runOnClient(client -> {
            ChainVeinConfig config = ChainveinfabricClient.CONFIG;
            config.searchAlgorithm = ChainVeinConfig.SearchAlgorithm.SPHERE;
            config.sphereRadius = 7;
            config.maxChainBlocks = 1;
            config.toolProtection = false;
            config.directToInventory = false;
            config.packetInterval = 0;
            config.showBlockOutlines = false;
            config.respectSchematicRenderLayer = false;
            config.mode = mode;
            config.getWhitelist(mode).clear();
            config.getWhitelist(mode).add("minecraft:dirt");
            var request = SearchRequest.automatic(client.level, client.player.blockPosition(), null,
                    client.player.getDirection(), SearchConfig.from(config),
                    LitematicaIntegration.createContext(mode, false), client.player.isCreative());
            var targets = SearchEngine.search(request).positions();
            check(targets.equals(List.of(TARGET)), "Unexpected search targets in " + mode + ": " + targets);
        });
    }

    private static void waitForMinedTarget(ClientGameTestContext context, ChainMode mode, int batch) {
        LOGGER.info("Waiting for schematic AUTO batch {} in {}", batch, mode);
        try {
            context.waitFor(client -> client.level.getBlockState(TARGET).isAir());
        } catch (AssertionError timeout) {
            String state = context.computeOnClient(client -> {
                var config = SearchConfig.from(ChainveinfabricClient.CONFIG);
                var schematic = LitematicaIntegration.createContext(mode, false);
                var request = SearchRequest.automatic(client.level, client.player.blockPosition(), null,
                        client.player.getDirection(), config, schematic, client.player.isCreative());
                return "mode=" + config.mode()
                        + ", status=" + ChainveinfabricClient.getAutoMiningStatus()
                        + ", cooldown=" + ChainveinfabricClient.getAutoMiningCooldownTicks()
                        + ", target=" + client.level.getBlockState(TARGET)
                        + ", player=" + client.player.position()
                        + ", schematicMatches=" + schematic.matches(client.level, TARGET)
                        + ", " + LitematicaTestFixture.describeTarget()
                        + ", targets=" + SearchEngine.search(request).positions();
            });
            throw new AssertionError("Schematic AUTO batch " + batch + " timed out: " + state, timeout);
        }
    }

    private static void prepareTargets(ClientGameTestContext context, TestSingleplayerContext world) {
        context.runOnClient(client -> ChainveinfabricClient.disarmAutoMining());
        world.getServer().runOnServer(server -> {
            var level = server.overworld();
            level.setBlockAndUpdate(TARGET, Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(MATCHING, Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(NON_WHITELISTED, Blocks.GOLD_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(OUTSIDE, Blocks.DIRT.defaultBlockState());
        });
        context.waitFor(client -> client.level.getBlockState(TARGET).is(Blocks.DIRT)
                && client.level.getBlockState(MATCHING).is(Blocks.DIRT)
                && client.level.getBlockState(NON_WHITELISTED).is(Blocks.GOLD_BLOCK)
                && client.level.getBlockState(OUTSIDE).is(Blocks.DIRT));
    }

    private static void waitForServerMining(ClientGameTestContext context,
                                          TestSingleplayerContext world, ChainMode mode) {
        // The vanilla path predicts removal before the server processes its destroy packet.
        for (int tick = 0; tick < 40; tick++) {
            if (world.getServer().computeOnServer(server -> server.overworld().getBlockState(TARGET).isAir())) {
                return;
            }
            context.waitTick();
        }
        throw new AssertionError("The server did not confirm schematic AUTO mining in " + mode);
    }

    private static void assertServerBlocks(TestSingleplayerContext world, ChainMode mode, boolean mined) {
        world.getServer().runOnServer(server -> {
            var level = server.overworld();
            check(mined ? level.getBlockState(TARGET).isAir() : level.getBlockState(TARGET).is(Blocks.DIRT),
                    "Unexpected target state in " + mode + " (mined=" + mined + ")");
            check(level.getBlockState(MATCHING).is(Blocks.DIRT), "A matching schematic block was mined in " + mode);
            check(level.getBlockState(NON_WHITELISTED).is(Blocks.GOLD_BLOCK),
                    "A non-whitelisted block was mined in " + mode);
            check(level.getBlockState(OUTSIDE).is(Blocks.DIRT), "An out-of-bounds block was mined in " + mode);
        });
    }
}
