package org.edtp.chainveinfabric.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.api.ChainVeinClientApi;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;

public final class PlantingClientGameTest implements FabricClientGameTest {
    private static final int FIELD_SIZE = 8;
    private static final int FIELD_Y = 64;
    private static final BlockPos START = new BlockPos(3, FIELD_Y, 0);
    private static final int MINE_SIZE = 5;
    private static final BlockPos MINE_START = new BlockPos(18, FIELD_Y, 0);
    private static final int AUTO_SIZE = 3;
    private static final BlockPos AUTO_CENTER = new BlockPos(32, FIELD_Y, 0);
    private static final int ICE_COUNT = 3;
    private static final BlockPos ICE_START = new BlockPos(48, FIELD_Y, 0);

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getServer().runOnServer(server -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayers().getFirst();

                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(Items.WHEAT_SEEDS, FIELD_SIZE * FIELD_SIZE));

                for (int x = 0; x < FIELD_SIZE; x++) {
                    for (int z = 0; z < FIELD_SIZE; z++) {
                        BlockPos soil = new BlockPos(x, FIELD_Y, z);
                        level.setBlockAndUpdate(soil.below(), Blocks.DIRT.defaultBlockState());
                        level.setBlockAndUpdate(soil, Blocks.FARMLAND.defaultBlockState()
                                .setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
                        level.setBlockAndUpdate(soil.above(), Blocks.AIR.defaultBlockState());
                    }
                }
                for (int x = 2; x <= 4; x++) {
                    for (int z = -2; z <= -1; z++) {
                        level.setBlockAndUpdate(new BlockPos(x, FIELD_Y, z), Blocks.STONE.defaultBlockState());
                    }
                }

                player.inventoryMenu.sendAllDataToRemote();
            });
            singleplayer.getServer().runCommand("tp @p 3.5 66 -1.5");

            context.waitTicks(5);
            singleplayer.getClientLevel().waitForChunksDownload();
            context.waitFor(client -> client.level != null
                    && client.level.getBlockState(START).is(Blocks.FARMLAND)
                    && client.player != null
                    && client.player.getMainHandItem().getCount() == FIELD_SIZE * FIELD_SIZE);

            context.runOnClient(client -> {
                client.player.lookAt(EntityAnchorArgument.Anchor.EYES, START.getCenter());
                ChainVeinConfig config = ChainveinfabricClient.CONFIG;
                config.isChainVeinEnabled = true;
                config.mode = ChainVeinConfig.ChainMode.CHAIN_PLANT;
                config.searchAlgorithm = ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME;
                config.maxChainBlocks = FIELD_SIZE * FIELD_SIZE;
                config.maxRadius = 20;
                config.diagonalEdge = false;
                config.diagonalCorner = false;
                config.packetInterval = 0;
                config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_PLANT).clear();
                config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_PLANT).add("minecraft:wheat_seeds");
                ChainVeinClientApi.clear();
            });

            context.waitTick();
            context.waitFor(client -> client.hitResult instanceof BlockHitResult hit
                    && hit.getBlockPos().equals(START));
            context.getInput().pressMouse(1);
            context.waitTicks(20);

            PlantingResult result = singleplayer.getServer().computeOnServer(server -> {
                var level = server.overworld();
                int planted = 0;
                for (int x = 0; x < FIELD_SIZE; x++) {
                    for (int z = 0; z < FIELD_SIZE; z++) {
                        if (level.getBlockState(new BlockPos(x, FIELD_Y + 1, z)).is(Blocks.WHEAT)) {
                            planted++;
                        }
                    }
                }
                return new PlantingResult(
                        planted,
                        server.getPlayerList().getPlayers().getFirst().getMainHandItem().getCount());
            });
            if (result.planted() != FIELD_SIZE * FIELD_SIZE || result.remainingSeeds() != 0) {
                throw new AssertionError("Expected 64 planted blocks and no seeds, got "
                        + result.planted() + " planted and " + result.remainingSeeds() + " seeds");
            }

            testServerMining(context, singleplayer);
            testIceMining(context, singleplayer);
            testAutomaticMining(context, singleplayer);
        } finally {
            context.runOnClient(client -> ChainVeinClientApi.clear());
        }
    }

    private static void testIceMining(ClientGameTestContext context,
                                      TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            var level = server.overworld();
            var player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().clearContent();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(Items.DIAMOND_PICKAXE));
            for (int x = 0; x < ICE_COUNT; x++) {
                BlockPos target = ICE_START.offset(x, 0, 0);
                level.setBlockAndUpdate(target.below(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(target, Blocks.ICE.defaultBlockState());
                level.setBlockAndUpdate(target.above(), Blocks.AIR.defaultBlockState());
            }
            for (int x = 47; x <= 52; x++) {
                level.setBlockAndUpdate(new BlockPos(x, FIELD_Y, -2),
                        Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, FIELD_Y, -1),
                        Blocks.AIR.defaultBlockState());
            }
            player.inventoryMenu.sendAllDataToRemote();
        });
        singleplayer.getServer().runCommand("tp @p 49.5 66 -1.5");

        context.waitTicks(5);
        context.waitFor(client -> client.level != null
                && client.level.getBlockState(ICE_START).is(Blocks.ICE)
                && client.player != null
                && client.player.getMainHandItem().getItem() == Items.DIAMOND_PICKAXE);
        context.runOnClient(client -> {
            ChainVeinConfig config = ChainveinfabricClient.CONFIG;
            config.isChainVeinEnabled = true;
            config.mode = ChainVeinConfig.ChainMode.CHAIN_MINE;
            config.searchAlgorithm = ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME;
            config.maxChainBlocks = ICE_COUNT;
            config.maxRadius = 10;
            config.directToInventory = false;
            config.toolProtection = false;
            config.diagonalEdge = false;
            config.diagonalCorner = false;
            config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_MINE).clear();
            config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_MINE).add("minecraft:ice");
            ChainVeinClientApi.clear();
        });

        context.runOnClient(client -> client.player.lookAt(
                EntityAnchorArgument.Anchor.EYES, ICE_START.getCenter()));
        context.waitTick();
        context.waitFor(client -> client.hitResult instanceof BlockHitResult hit
                && hit.getBlockPos().equals(ICE_START));
        context.getInput().holdMouseFor(0, 40);
        context.waitTicks(20);

        IceResult result = singleplayer.getServer().computeOnServer(server -> {
            int water = 0;
            int ice = 0;
            int air = 0;
            StringBuilder states = new StringBuilder();
            for (int x = 0; x < ICE_COUNT; x++) {
                var state = server.overworld().getBlockState(ICE_START.offset(x, 0, 0));
                if (state.is(Blocks.WATER)) water++;
                if (state.is(Blocks.ICE)) ice++;
                if (state.isAir()) air++;
                if (!states.isEmpty()) states.append(", ");
                states.append(state);
            }
            return new IceResult(water, ice, air, states.toString());
        });
        if (result.water() != ICE_COUNT) {
            throw new AssertionError("Expected clicked and chained ice to leave "
                    + ICE_COUNT + " water blocks, got " + result);
        }
    }

    private static void testAutomaticMining(ClientGameTestContext context,
                                            TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            var level = server.overworld();
            var player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().clearContent();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(Items.DIAMOND_SHOVEL));
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    level.setBlockAndUpdate(AUTO_CENTER.offset(x, 0, z), Blocks.DIRT.defaultBlockState());
                }
            }
            level.setBlockAndUpdate(AUTO_CENTER.offset(2, 0, 0), Blocks.STONE.defaultBlockState());
            player.inventoryMenu.sendAllDataToRemote();
        });
        singleplayer.getServer().runCommand("tp @p 32.5 66 0.5");

        context.waitTicks(5);
        context.waitFor(client -> client.level != null
                && client.level.getBlockState(AUTO_CENTER).is(Blocks.DIRT)
                && client.player != null
                && client.player.blockPosition().getX() == AUTO_CENTER.getX());
        context.runOnClient(client -> {
            ChainVeinConfig config = ChainveinfabricClient.CONFIG;
            config.isChainVeinEnabled = true;
            config.mode = ChainVeinConfig.ChainMode.AUTO_MINE;
            config.searchAlgorithm = ChainVeinConfig.SearchAlgorithm.SPHERE;
            config.sphereRadius = 4;
            config.maxChainBlocks = AUTO_SIZE * AUTO_SIZE;
            config.directToInventory = true;
            config.toolProtection = false;
            config.getWhitelist(ChainVeinConfig.ChainMode.AUTO_MINE).clear();
            config.getWhitelist(ChainVeinConfig.ChainMode.AUTO_MINE).add("minecraft:dirt");
            ChainVeinClientApi.clear();
        });
        context.waitTicks(30);

        int remaining = singleplayer.getServer().computeOnServer(server -> {
            int count = 0;
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (server.overworld().getBlockState(AUTO_CENTER.offset(x, 0, z)).is(Blocks.DIRT)) {
                        count++;
                    }
                }
            }
            if (!server.overworld().getBlockState(AUTO_CENTER.offset(2, 0, 0)).is(Blocks.STONE)) {
                throw new AssertionError("Automatic mining broke a non-whitelisted block");
            }
            return count;
        });
        if (remaining != 0) {
            throw new AssertionError("Automatic mining left " + remaining + " whitelisted blocks");
        }

        singleplayer.getServer().runOnServer(server ->
                server.overworld().setBlockAndUpdate(AUTO_CENTER, Blocks.DIRT.defaultBlockState()));
        context.waitFor(client -> client.level.getBlockState(AUTO_CENTER).is(Blocks.DIRT));
        context.runOnClient(client ->
                ChainveinfabricClient.CONFIG.searchAlgorithm = ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME);
        context.waitTicks(15);
        boolean stillPresent = singleplayer.getServer().computeOnServer(server ->
                server.overworld().getBlockState(AUTO_CENTER).is(Blocks.DIRT));
        if (!stillPresent) {
            throw new AssertionError("Adjacent Same must not run in Automatic Mining mode");
        }
        context.runOnClient(client -> ChainveinfabricClient.CONFIG.isChainVeinEnabled = false);
    }

    private static void testServerMining(ClientGameTestContext context,
                                         TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            var level = server.overworld();
            var player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().clearContent();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(Items.DIAMOND_SHOVEL));

            for (int x = 16; x < 16 + MINE_SIZE; x++) {
                for (int z = 0; z < MINE_SIZE; z++) {
                    BlockPos target = new BlockPos(x, FIELD_Y, z);
                    level.setBlockAndUpdate(target.below(), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(target, Blocks.DIRT.defaultBlockState());
                    level.setBlockAndUpdate(target.above(), Blocks.AIR.defaultBlockState());
                }
            }
            for (int x = 17; x <= 19; x++) {
                level.setBlockAndUpdate(new BlockPos(x, FIELD_Y, -2), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x, FIELD_Y, -1), Blocks.AIR.defaultBlockState());
            }
            player.inventoryMenu.sendAllDataToRemote();
        });
        singleplayer.getServer().runCommand("tp @p 18.5 66 -1.5");

        context.waitTicks(5);
        context.waitFor(client -> client.level != null
                && client.level.getBlockState(MINE_START).is(Blocks.DIRT)
                && client.player != null
                && client.player.getX() > 18.0
                && client.player.getMainHandItem().getItem() == Items.DIAMOND_SHOVEL);

        context.runOnClient(client -> {
            client.player.lookAt(EntityAnchorArgument.Anchor.EYES, MINE_START.getCenter());
            ChainVeinConfig config = ChainveinfabricClient.CONFIG;
            config.isChainVeinEnabled = true;
            config.mode = ChainVeinConfig.ChainMode.CHAIN_MINE;
            config.searchAlgorithm = ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME;
            config.maxChainBlocks = MINE_SIZE * MINE_SIZE;
            config.maxRadius = 10;
            config.directToInventory = true;
            config.toolProtection = false;
            config.diagonalEdge = false;
            config.diagonalCorner = false;
            config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_MINE).clear();
            config.getWhitelist(ChainVeinConfig.ChainMode.CHAIN_MINE).add("minecraft:dirt");
            ChainVeinClientApi.clear();
        });

        context.waitTick();
        BlockPos aim = context.computeOnClient(client -> {
            if (client.hitResult instanceof BlockHitResult hit) {
                return hit.getBlockPos().immutable();
            }
            return null;
        });
        if (!MINE_START.equals(aim)) {
            throw new AssertionError("Mining test aimed at " + aim + " instead of " + MINE_START);
        }
        context.getInput().holdMouseFor(0, 10);
        context.waitTicks(5);

        MiningResult result = singleplayer.getServer().computeOnServer(server -> {
            var level = server.overworld();
            int remainingBlocks = 0;
            for (int x = 16; x < 16 + MINE_SIZE; x++) {
                for (int z = 0; z < MINE_SIZE; z++) {
                    if (level.getBlockState(new BlockPos(x, FIELD_Y, z)).is(Blocks.DIRT)) {
                        remainingBlocks++;
                    }
                }
            }
            int collectedDirt = server.getPlayerList().getPlayers().getFirst().getInventory()
                    .getNonEquipmentItems().stream()
                    .filter(stack -> stack.getItem() == Items.DIRT)
                    .mapToInt(ItemStack::getCount)
                    .sum();
            return new MiningResult(remainingBlocks, collectedDirt);
        });

        if (result.remainingBlocks() != 0 || result.collectedDirt() != MINE_SIZE * MINE_SIZE) {
            throw new AssertionError("Expected one 25-block server mining batch in the inventory, got "
                    + result.remainingBlocks() + " blocks left and " + result.collectedDirt() + " dirt");
        }
    }

    private record PlantingResult(int planted, int remainingSeeds) {
    }

    private record MiningResult(int remainingBlocks, int collectedDirt) {
    }

    private record IceResult(int water, int ice, int air, String states) {
    }
}
