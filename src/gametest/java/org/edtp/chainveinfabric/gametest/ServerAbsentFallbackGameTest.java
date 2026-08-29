package org.edtp.chainveinfabric.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.api.ChainVeinClientApi;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class ServerAbsentFallbackGameTest implements FabricClientGameTest {
    private static final BlockPos TARGET = new BlockPos(2, 64, 0);
    private static final Item TEST_SHULKER = Items.DYED_SHULKER_BOX.pick(DyeColor.BLUE);

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
        } finally {
            context.runOnClient(client -> ChainVeinClientApi.clear());
        }
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
            level.setBlockAndUpdate(TARGET, Blocks.TORCH.defaultBlockState());
            level.setBlockAndUpdate(TARGET.above(), Blocks.AIR.defaultBlockState());
            player.inventoryMenu.sendAllDataToRemote();
        });
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
}
