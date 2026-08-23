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
        } finally {
            context.runOnClient(client -> ChainVeinClientApi.clear());
        }
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
}
