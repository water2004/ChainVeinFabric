package org.edtp.chainveinfabric;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ChainVeinServerGameTests {
    @GameTest
    public void miningIceWithoutSilkTouchLeavesWater(GameTestHelper helper) {
        BlockPos vanillaTarget = new BlockPos(1, 2, 1);
        List<BlockPos> chainTargets = List.of(
                new BlockPos(3, 2, 1),
                new BlockPos(4, 2, 1),
                new BlockPos(5, 2, 1));

        helper.setBlock(vanillaTarget.below(), Blocks.STONE);
        helper.setBlock(vanillaTarget, Blocks.ICE);
        for (BlockPos target : chainTargets) {
            helper.setBlock(target.below(), Blocks.STONE);
            helper.setBlock(target, Blocks.ICE);
        }

        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(chainTargets.getFirst()));
        player.setPosRaw(center.x, center.y + 1.0, center.z);

        // First establish that water can exist here and the unenchanted tool
        // produces vanilla's expected ice-to-water result.
        helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(vanillaTarget)),
                "Vanilla control ice should be breakable");
        helper.assertBlockPresent(Blocks.WATER, vanillaTarget);

        Chainveinfabric.handleMine(
                player,
                chainTargets.stream().map(helper::absolutePos).toList(),
                false,
                false);

        for (BlockPos target : chainTargets) {
            helper.assertBlockPresent(Blocks.WATER, target);
        }
        helper.succeed();
    }

    @GameTest
    public void directMiningPreservesContainerContents(GameTestHelper helper) {
        List<BlockPos> targets = List.of(
                new BlockPos(1, 2, 1),
                new BlockPos(3, 2, 1));
        for (BlockPos target : targets) {
            helper.setBlock(target.below(), Blocks.STONE);
            helper.setBlock(target, Blocks.CHEST);
        }

        ChestBlockEntity first = requireChest(helper, targets.get(0));
        ChestBlockEntity second = requireChest(helper, targets.get(1));
        first.setItem(0, new ItemStack(Items.DIAMOND, 7));
        second.setItem(0, new ItemStack(Items.STICK, 19));
        first.setChanged();
        second.setChanged();

        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(targets.getFirst()));
        player.setPosRaw(center.x, center.y + 1.0, center.z);

        Chainveinfabric.handleMine(
                player,
                targets.stream().map(helper::absolutePos).toList(),
                true,
                false);

        for (BlockPos target : targets) {
            helper.assertBlockNotPresent(Blocks.CHEST, target);
        }
        helper.assertValueEqual(countItem(player, Items.CHEST), targets.size(),
                "Every mined chest should enter the inventory exactly once");
        helper.assertValueEqual(countItem(player, Items.DIAMOND), 7,
                "The first chest's contents should be preserved exactly");
        helper.assertValueEqual(countItem(player, Items.STICK), 19,
                "The second chest's contents should be preserved exactly");
        helper.assertItemEntityNotPresent(Items.CHEST);
        helper.assertItemEntityNotPresent(Items.DIAMOND);
        helper.assertItemEntityNotPresent(Items.STICK);
        helper.succeed();
    }

    @GameTest
    public void directMiningCollectsEveryDrop(GameTestHelper helper) {
        List<BlockPos> relativeTargets = List.of(
                new BlockPos(1, 1, 1),
                new BlockPos(2, 1, 1),
                new BlockPos(3, 1, 1),
                new BlockPos(4, 1, 1));
        List<BlockPos> absoluteTargets = relativeTargets.stream()
                .map(helper::absolutePos)
                .toList();

        for (BlockPos target : relativeTargets) {
            helper.setBlock(target, Blocks.DIRT);
        }

        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SHOVEL));
        Vec3 center = Vec3.atCenterOf(absoluteTargets.getFirst());
        // Fabric's mock player intentionally has no network connection. Use the
        // raw entity setter so positioning the test double stays server-local.
        player.setPosRaw(center.x, center.y + 1.0, center.z);
        helper.assertTrue(player.getMainHandItem().getItem() == Items.DIAMOND_SHOVEL,
                "Mock player should hold the configured shovel");
        helper.assertFalse(player.isCreative(), "Mock player should use survival drop rules");
        helper.assertTrue(player.hasCorrectToolForDrops(
                        helper.getLevel().getBlockState(absoluteTargets.getFirst())),
                "Mock player should be able to harvest dirt");

        Chainveinfabric.handleMine(player, absoluteTargets, true, false);

        for (BlockPos target : relativeTargets) {
            helper.assertBlockNotPresent(Blocks.DIRT, target);
        }
        int dirt = countItem(player, Items.DIRT);
        helper.assertValueEqual(dirt, relativeTargets.size(),
                "Direct mining should insert every drop into the inventory");
        helper.assertItemEntityNotPresent(Items.DIRT);
        helper.succeed();
    }

    private static ChestBlockEntity requireChest(GameTestHelper helper, BlockPos relativePos) {
        if (helper.getLevel().getBlockEntity(helper.absolutePos(relativePos))
                instanceof ChestBlockEntity chest) {
            return chest;
        }
        throw new AssertionError("Expected chest block entity at " + relativePos);
    }

    private static int countItem(ServerPlayer player, Item item) {
        return player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.getItem() == item)
                .mapToInt(ItemStack::getCount)
                .sum();
    }
}
