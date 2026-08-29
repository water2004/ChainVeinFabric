package org.edtp.chainveinfabric.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.api.ChainVeinClientApi;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class ServerAbsentFallbackGameTest implements FabricClientGameTest {
    private static final BlockPos TARGET = new BlockPos(2, 64, 0);
    private static final BlockPos PLANT_START = new BlockPos(8, 64, 0);
    private static final BlockPos MINE_START = new BlockPos(14, 64, 0);
    private static final BlockPos UTILITY_START = new BlockPos(20, 64, 0);
    private static final BlockPos AUTO_GUARD = new BlockPos(30, 64, 0);
    private static final List<BlockPos> AUTO_TARGETS = List.of(
            new BlockPos(29, 64, 0),
            new BlockPos(31, 64, 0),
            new BlockPos(30, 64, 1));
    private static final Item TEST_SHULKER = Items.BLUE_SHULKER_BOX;

    @Override
    public void runTest(ClientGameTestContext context) {
        if (serverInstalled()) return;

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            prepareWorld(singleplayer);
            singleplayer.getServer().runCommand("gamemode survival @p");
            singleplayer.getServer().runCommand("tp @p 2.5 65 -3.5");

            context.waitTicks(5);
            singleplayer.getClientLevel().waitForChunksDownload();
            context.waitFor(client -> client.player != null
                    && client.level != null
                    && client.level.getBlockState(TARGET).is(Blocks.TORCH)
                    && client.player.getInventory().getItem(9).is(TEST_SHULKER));

            int accepted = context.computeOnClient(client -> {
                if (ChainVeinClientApi.canUseServerMiningProtocol()) {
                    throw new AssertionError("The test did not simulate an absent ChainVein server");
                }
                ChainveinfabricClient.disarmAutoMining();
                ChainveinfabricClient.CONFIG.directToInventory = true;
                ChainveinfabricClient.CONFIG.quickShulkerOverflow = true;
                return ChainVeinClientApi.queueMineJobs(client, List.of(TARGET));
            });
            if (accepted != 1) {
                throw new AssertionError("Vanilla fallback rejected the mining job");
            }

            context.waitFor(client -> client.level != null
                    && client.level.getBlockState(TARGET).isAir());
            context.waitTicks(5);

            FallbackResult result = singleplayer.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                ItemStack box = player.getInventory().getItem(9);
                int boxedStone = countStored(box, Items.STONE);
                int boxedTorch = countStored(box, Items.TORCH);
                int groundTorch = server.overworld().getEntitiesOfClass(
                                ItemEntity.class,
                                player.getBoundingBox().inflate(16.0D))
                        .stream()
                        .map(ItemEntity::getItem)
                        .filter(stack -> stack.is(Items.TORCH))
                        .mapToInt(ItemStack::getCount)
                        .sum();
                return new FallbackResult(
                        server.overworld().getBlockState(TARGET).isAir(),
                        boxedStone, boxedTorch, groundTorch);
            });

            if (!result.broken()
                    || result.boxedStone() != 1
                    || result.boxedTorch() != 0
                    || result.groundTorch() != 1) {
                throw new AssertionError("Absent-server vanilla fallback touched Quick Shulker ("
                        + quickShulkerMode() + "): " + result);
            }

            testPlanting(context, singleplayer);
            testManualMining(context, singleplayer);
            testUtilityInteraction(context, singleplayer);
            testAutomaticMining(context, singleplayer);
        } finally {
            context.runOnClient(client -> {
                ChainveinfabricClient.disarmAutoMining();
                ChainveinfabricClient.CONFIG.isChainVeinEnabled = false;
                ChainveinfabricClient.CONFIG.directToInventory = false;
                ChainveinfabricClient.CONFIG.quickShulkerOverflow = false;
                ChainVeinClientApi.clear();
            });
        }
    }

    private static void testPlanting(ClientGameTestContext context,
                                     TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            var level = server.overworld();
            var player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(Items.WHEAT_SEEDS, 4));
            player.getInventory().setSelectedSlot(0);
            for (int x = 0; x < 2; x++) {
                for (int z = 0; z < 2; z++) {
                    BlockPos soil = PLANT_START.offset(x, 0, z);
                    level.setBlockAndUpdate(soil.below(), Blocks.DIRT.defaultBlockState());
                    level.setBlockAndUpdate(soil, Blocks.FARMLAND.defaultBlockState()
                            .setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
                    level.setBlockAndUpdate(soil.above(), Blocks.AIR.defaultBlockState());
                }
            }
            player.inventoryMenu.sendAllDataToRemote();
        });
        singleplayer.getServer().runCommand("tp @p 8.5 66 -1.5");

        context.waitTicks(5);
        context.waitFor(client -> client.level != null && client.player != null
                && client.level.getBlockState(PLANT_START).is(Blocks.FARMLAND)
                && client.player.getMainHandItem().is(Items.WHEAT_SEEDS)
                && client.player.getMainHandItem().getCount() == 4);
        context.runOnClient(client -> {
            ChainVeinConfig config = ChainveinfabricClient.CONFIG;
            ChainveinfabricClient.disarmAutoMining();
            config.isChainVeinEnabled = true;
            config.mode = ChainVeinConfig.ChainMode.CHAIN_PLANT;
            config.searchAlgorithm = ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME;
            config.maxChainBlocks = 4;
            config.maxRadius = 8;
            config.packetInterval = 0;
            config.diagonalEdge = false;
            config.diagonalCorner = false;
            config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_PLANT).clear();
            config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_PLANT)
                    .add("minecraft:wheat_seeds");
            ChainVeinClientApi.clear();
        });

        InteractionResult interactionResult = context.computeOnClient(client ->
                client.gameMode.useItemOn(
                        client.player,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(
                                Vec3.atCenterOf(PLANT_START).add(0.0D, 0.5D, 0.0D),
                                Direction.UP, PLANT_START, false)));
        if (interactionResult == InteractionResult.FAIL) {
            throw new AssertionError("The original pure-client planting interaction failed");
        }
        context.waitTicks(20);

        PlantResult result = singleplayer.getServer().computeOnServer(server -> {
            int planted = 0;
            for (int x = 0; x < 2; x++) {
                for (int z = 0; z < 2; z++) {
                    if (server.overworld().getBlockState(
                            PLANT_START.offset(x, 1, z)).is(Blocks.WHEAT)) {
                        planted++;
                    }
                }
            }
            return new PlantResult(planted, server.getPlayerList().getPlayers()
                    .getFirst().getMainHandItem().getCount());
        });
        if (result.planted() != 4 || result.remainingSeeds() != 0) {
            throw new AssertionError("Pure-client planting did not process the original and "
                    + "three queued interactions exactly once: " + result);
        }
    }

    private static void testManualMining(ClientGameTestContext context,
                                         TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            var level = server.overworld();
            var player = server.getPlayerList().getPlayers().getFirst();
            fillInventory(player, Items.DIAMOND_SHOVEL);
            for (int x = -2; x <= 3; x++) {
                for (int z = -2; z <= 3; z++) {
                    level.setBlockAndUpdate(
                            MINE_START.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                }
            }
            for (int x = 0; x < 2; x++) {
                for (int z = 0; z < 2; z++) {
                    BlockPos target = MINE_START.offset(x, 0, z);
                    level.setBlockAndUpdate(target, Blocks.COARSE_DIRT.defaultBlockState());
                    level.setBlockAndUpdate(target.above(), Blocks.AIR.defaultBlockState());
                }
            }
            player.inventoryMenu.sendAllDataToRemote();
        });
        singleplayer.getServer().runCommand("tp @p 14.5 66 -1.5");

        context.waitTicks(5);
        context.waitFor(client -> client.level != null && client.player != null
                && client.level.getBlockState(MINE_START).is(Blocks.COARSE_DIRT)
                && client.player.getMainHandItem().is(Items.DIAMOND_SHOVEL));
        context.runOnClient(client -> {
            ChainVeinConfig config = ChainveinfabricClient.CONFIG;
            ChainveinfabricClient.disarmAutoMining();
            config.isChainVeinEnabled = true;
            config.mode = ChainVeinConfig.ChainMode.CHAIN_MINE;
            config.searchAlgorithm = ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME;
            config.maxChainBlocks = 4;
            config.maxRadius = 8;
            config.packetInterval = 0;
            config.directToInventory = true;
            config.quickShulkerOverflow = true;
            config.toolProtection = false;
            config.diagonalEdge = false;
            config.diagonalCorner = false;
            config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_MINE).clear();
            config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_MINE)
                    .add("minecraft:coarse_dirt");
            ChainVeinClientApi.clear();
        });

        boolean started = context.computeOnClient(client ->
                client.gameMode.startDestroyBlock(MINE_START, Direction.UP));
        if (!started) {
            throw new AssertionError("The original pure-client mining action did not start");
        }
        for (int i = 0; i < 10; i++) {
            if (context.computeOnClient(client ->
                    client.level.getBlockState(MINE_START).isAir())) {
                break;
            }
            context.waitTick();
            context.runOnClient(client ->
                    client.gameMode.continueDestroyBlock(MINE_START, Direction.UP));
        }
        context.waitTicks(120);

        MiningResult result = singleplayer.getServer().computeOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            int remaining = 0;
            for (int x = 0; x < 2; x++) {
                for (int z = 0; z < 2; z++) {
                    if (!server.overworld().getBlockState(
                            MINE_START.offset(x, 0, z)).isAir()) {
                        remaining++;
                    }
                }
            }
            int groundDirt = server.overworld().getEntitiesOfClass(
                            ItemEntity.class, player.getBoundingBox().inflate(12.0D))
                    .stream()
                    .map(ItemEntity::getItem)
                    .filter(stack -> stack.is(Items.COARSE_DIRT))
                    .mapToInt(ItemStack::getCount)
                    .sum();
            int inventoryDirt = 0;
            int boxedDirt = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(Items.COARSE_DIRT)) {
                    inventoryDirt += stack.getCount();
                }
                boxedDirt += countStored(stack, Items.COARSE_DIRT);
            }
            return new MiningResult(remaining, groundDirt,
                    inventoryDirt, boxedDirt);
        });
        if (result.remainingBlocks() != 0 || result.groundDirt() != 4
                || result.inventoryDirt() != 0 || result.boxedDirt() != 0) {
            throw new AssertionError("Pure-client mining did not use vanilla drops exactly once: "
                    + result);
        }
    }

    private static void testUtilityInteraction(ClientGameTestContext context,
                                               TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            var level = server.overworld();
            var player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_AXE));
            player.getInventory().setSelectedSlot(0);
            for (int x = 0; x < 3; x++) {
                BlockPos target = UTILITY_START.offset(x, 0, 0);
                level.setBlockAndUpdate(target.below(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(target, Blocks.OAK_LOG.defaultBlockState());
            }
            player.inventoryMenu.sendAllDataToRemote();
        });
        singleplayer.getServer().runCommand("tp @p 21.5 66 -1.5");

        context.waitTicks(5);
        context.waitFor(client -> client.level != null && client.player != null
                && client.level.getBlockState(UTILITY_START).is(Blocks.OAK_LOG)
                && client.player.getMainHandItem().is(Items.DIAMOND_AXE));
        context.runOnClient(client -> {
            ChainVeinConfig config = ChainveinfabricClient.CONFIG;
            ChainveinfabricClient.disarmAutoMining();
            config.isChainVeinEnabled = true;
            config.mode = ChainVeinConfig.ChainMode.CHAIN_UTILITY;
            config.searchAlgorithm = ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME;
            config.maxChainBlocks = 3;
            config.maxRadius = 8;
            config.packetInterval = 50;
            config.toolProtection = false;
            config.diagonalEdge = false;
            config.diagonalCorner = false;
            config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_UTILITY).clear();
            config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_UTILITY)
                    .add("minecraft:oak_log");
            ChainVeinClientApi.clear();
        });

        InteractionResult interactionResult = context.computeOnClient(client ->
                client.gameMode.useItemOn(
                        client.player,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(
                                Vec3.atCenterOf(UTILITY_START),
                                Direction.UP, UTILITY_START, false)));
        if (interactionResult == InteractionResult.FAIL) {
            throw new AssertionError("The original pure-client utility interaction failed");
        }
        context.waitTicks(20);

        int stripped = singleplayer.getServer().computeOnServer(server -> {
            int count = 0;
            for (int x = 0; x < 3; x++) {
                if (server.overworld().getBlockState(
                        UTILITY_START.offset(x, 0, 0)).is(Blocks.STRIPPED_OAK_LOG)) {
                    count++;
                }
            }
            return count;
        });
        if (stripped != 3) {
            throw new AssertionError("Pure-client utility packets stripped "
                    + stripped + " of 3 logs");
        }
    }

    private static void testAutomaticMining(ClientGameTestContext context,
                                            TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            var level = server.overworld();
            var player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SHOVEL));
            player.getInventory().setSelectedSlot(0);
            level.setBlockAndUpdate(AUTO_GUARD, Blocks.STONE.defaultBlockState());
            for (BlockPos target : AUTO_TARGETS) {
                level.setBlockAndUpdate(target, Blocks.COARSE_DIRT.defaultBlockState());
                level.setBlockAndUpdate(target.above(), Blocks.AIR.defaultBlockState());
            }
            player.inventoryMenu.sendAllDataToRemote();
        });
        singleplayer.getServer().runCommand("tp @p 30.5 66 -1.5");

        context.waitTicks(5);
        context.waitFor(client -> client.level != null && client.player != null
                && client.level.getBlockState(AUTO_GUARD).is(Blocks.STONE)
                && client.level.getBlockState(AUTO_TARGETS.getFirst()).is(Blocks.COARSE_DIRT));
        context.runOnClient(client -> {
            ChainVeinConfig config = ChainveinfabricClient.CONFIG;
            config.isChainVeinEnabled = true;
            config.mode = ChainVeinConfig.ChainMode.CHAIN_MINE;
            config.searchAlgorithm = ChainVeinConfig.SearchAlgorithm.SPHERE;
            config.sphereRadius = 4;
            config.maxChainBlocks = AUTO_TARGETS.size();
            config.packetInterval = 0;
            config.autoMineCooldownTicks = 10;
            config.directToInventory = false;
            config.toolProtection = false;
            config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_MINE).clear();
            config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_MINE)
                    .add("minecraft:coarse_dirt");
            ChainVeinClientApi.clear();
            ChainveinfabricClient.disarmAutoMining();
            if (!ChainveinfabricClient.toggleAutoMining()) {
                throw new AssertionError("Failed to arm pure-client automatic mining");
            }
        });

        boolean handled = context.computeOnClient(client ->
                ChainveinfabricClient.handleAutoMiningAttack());
        if (!handled) {
            throw new AssertionError("The pure-client automatic mining attack was not handled");
        }
        context.waitTicks(30);

        AutoResult result = singleplayer.getServer().computeOnServer(server -> {
            int remainingTargets = 0;
            for (BlockPos target : AUTO_TARGETS) {
                if (!server.overworld().getBlockState(target).isAir()) {
                    remainingTargets++;
                }
            }
            return new AutoResult(
                    server.overworld().getBlockState(AUTO_GUARD).is(Blocks.STONE),
                    remainingTargets);
        });
        if (!result.guardPresent() || result.remainingTargets() != 0) {
            throw new AssertionError("Pure-client automatic mining did not suppress the "
                    + "clicked non-whitelisted block or mine its fallback targets: " + result);
        }
        context.runOnClient(client -> ChainveinfabricClient.disarmAutoMining());
    }

    private static void prepareWorld(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            var level = server.overworld();
            var player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SHOVEL));
            for (int slot = 1; slot < 36; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            ItemStack box = new ItemStack(TEST_SHULKER);
            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(
                    List.of(new ItemStack(Items.STONE))));
            player.getInventory().setItem(9, box);
            player.getInventory().setSelectedSlot(0);

            for (int x = -2; x <= 6; x++) {
                for (int z = -6; z <= 3; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 63, z),
                            Blocks.COBBLESTONE.defaultBlockState());
                }
            }
            for (int x = 7; x <= 32; x++) {
                for (int z = -2; z <= -1; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 64, z),
                            Blocks.STONE.defaultBlockState());
                }
            }
            level.setBlockAndUpdate(TARGET, Blocks.TORCH.defaultBlockState());
            level.setBlockAndUpdate(TARGET.above(), Blocks.AIR.defaultBlockState());
            player.inventoryMenu.sendAllDataToRemote();
        });
    }

    private static void fillInventory(net.minecraft.server.level.ServerPlayer player,
                                      Item tool) {
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(tool));
        for (int slot = 1; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        ItemStack box = new ItemStack(TEST_SHULKER);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(
                List.of(new ItemStack(Items.STONE))));
        player.getInventory().setItem(9, box);
        player.getInventory().setSelectedSlot(0);
    }

    private static int countStored(ItemStack box, net.minecraft.world.item.Item item) {
        ItemContainerContents contents = box.get(DataComponents.CONTAINER);
        if (contents == null) return 0;
        return contents.nonEmptyItemCopyStream()
                .filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    private static boolean serverInstalled() {
        return Boolean.parseBoolean(System.getProperty(
                "chainveinfabric.gametest.serverInstalled", "true"));
    }

    private static String quickShulkerMode() {
        return System.getProperty("chainveinfabric.gametest.quickshulker", "legacy");
    }

    private record FallbackResult(boolean broken, int boxedStone,
                                  int boxedTorch, int groundTorch) {
    }

    private record PlantResult(int planted, int remainingSeeds) {
    }

    private record MiningResult(int remainingBlocks, int groundDirt,
                                int inventoryDirt, int boxedDirt) {
    }

    private record AutoResult(boolean guardPresent, int remainingTargets) {
    }
}
