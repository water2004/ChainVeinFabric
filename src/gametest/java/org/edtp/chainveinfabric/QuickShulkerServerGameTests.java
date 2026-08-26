package org.edtp.chainveinfabric;

import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kyrptonaught.quickshulker.api.QuickOpenableRegistry;
import net.kyrptonaught.quickshulker.api.QuickShulkerData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

import org.edtp.chainveinfabric.compat.quickshulker.QuickShulkerIntegration;

public final class QuickShulkerServerGameTests {
    private static final int CONTAINER_SLOTS = 27;
    private static final int STACK_SIZE = 64;
    private static final int SHULKER_CAPACITY = CONTAINER_SLOTS * STACK_SIZE;
    private static final Item OVERFLOW_BOX_ITEM = Items.DYED_SHULKER_BOX.pick(DyeColor.BLUE);
    private static final Item CONTAINED_BOX_ITEM = Items.DYED_SHULKER_BOX.pick(DyeColor.RED);

    @GameTest
    public void filledShulkerFromChestRemainsIntactWhenItCannotBeNested(
            GameTestHelper helper) {
        BlockPos target = new BlockPos(2, 2, 2);
        ChestBlockEntity chest = placeChest(helper, target);
        ServerPlayer player = createPlayer(helper, target);
        ItemStack overflowBox = new ItemStack(OVERFLOW_BOX_ITEM);
        fillPlayerInventory(player, overflowBox);

        ItemStack containedBox = new ItemStack(CONTAINED_BOX_ITEM);
        Container containedInventory = getShulkerInventory(player, containedBox);
        containedInventory.setItem(0, new ItemStack(Items.EMERALD, 13));
        containedInventory.setChanged();
        chest.setItem(0, containedBox);
        chest.setChanged();

        Chainveinfabric.handleMine(
                player, List.of(helper.absolutePos(target)), true, true);

        helper.assertBlockNotPresent(Blocks.CHEST, target);
        Container overflowInventory = getShulkerInventory(player, overflowBox);
        helper.assertValueEqual(countItem(overflowInventory, CONTAINED_BOX_ITEM), 0,
                "Quick Shulker must not nest a shulker box inside another shulker box");

        ItemStack droppedBox = findGroundStack(helper, CONTAINED_BOX_ITEM);
        helper.assertValueEqual(droppedBox.getCount(), 1,
                "The non-nestable shulker box should remain as one ground item");
        helper.assertValueEqual(
                countItem(getShulkerInventory(player, droppedBox), Items.EMERALD), 13,
                "The dropped shulker box must retain its exact contents");
        helper.assertValueEqual(
                countItem(overflowInventory, Items.CHEST)
                        + countGroundItems(helper, Items.CHEST),
                1,
                "The mined chest item must also be preserved");
        helper.succeed();
    }

    @GameTest
    public void chestOverflowFitsCompletelyInsideCarriedShulker(GameTestHelper helper) {
        BlockPos target = new BlockPos(2, 2, 2);
        ChestBlockEntity chest = placeChest(helper, target);
        fillContainer(chest, Items.DIAMOND, 20);

        ServerPlayer player = createPlayer(helper, target);
        ItemStack overflowBox = new ItemStack(OVERFLOW_BOX_ITEM);
        fillPlayerInventory(player, overflowBox);

        Chainveinfabric.handleMine(
                player, List.of(helper.absolutePos(target)), true, true);

        Container overflowInventory = getShulkerInventory(player, overflowBox);
        helper.assertValueEqual(countItem(overflowInventory, Items.DIAMOND), 20 * STACK_SIZE,
                "All chest contents should overflow into the carried shulker box");
        helper.assertValueEqual(countItem(overflowInventory, Items.CHEST), 1,
                "The mined chest should overflow into the carried shulker box too");
        helper.assertValueEqual(countGroundItems(helper, Items.DIAMOND), 0,
                "No chest contents should remain on the ground when shulker capacity is sufficient");
        helper.assertValueEqual(countGroundItems(helper, Items.CHEST), 0,
                "The chest item should not remain on the ground when shulker capacity is sufficient");
        helper.succeed();
    }

    @GameTest
    public void excessBeyondInventoryAndShulkerCapacityRemainsOnGround(
            GameTestHelper helper) {
        List<BlockPos> targets = List.of(
                new BlockPos(1, 2, 2),
                new BlockPos(3, 2, 2));
        for (BlockPos target : targets) {
            ChestBlockEntity chest = placeChest(helper, target);
            fillContainer(chest, Items.DIAMOND, CONTAINER_SLOTS);
        }

        ServerPlayer player = createPlayer(helper, targets.getFirst());
        ItemStack overflowBox = new ItemStack(OVERFLOW_BOX_ITEM);
        fillPlayerInventory(player, overflowBox);

        Chainveinfabric.handleMine(
                player,
                targets.stream().map(helper::absolutePos).toList(),
                true,
                true);

        Container overflowInventory = getShulkerInventory(player, overflowBox);
        int storedDiamonds = countItem(overflowInventory, Items.DIAMOND);
        int droppedDiamonds = countGroundItems(helper, Items.DIAMOND);
        int storedChests = countItem(overflowInventory, Items.CHEST);
        int droppedChests = countGroundItems(helper, Items.CHEST);

        helper.assertValueEqual(countItems(overflowInventory), SHULKER_CAPACITY,
                "The carried shulker box should be filled to its exact capacity");
        helper.assertValueEqual(
                storedDiamonds + droppedDiamonds,
                targets.size() * CONTAINER_SLOTS * STACK_SIZE,
                "All diamonds must be preserved across shulker storage and ground drops");
        helper.assertValueEqual(
                storedChests + droppedChests,
                targets.size(),
                "Every mined chest item must be preserved when all storage is full");
        helper.assertTrue(droppedDiamonds > 0,
                "Diamonds beyond inventory and shulker capacity must remain on the ground");
        helper.assertTrue(droppedChests > 0,
                "Chest items beyond inventory and shulker capacity must remain on the ground");
        helper.succeed();
    }

    private static ChestBlockEntity placeChest(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position.below(), Blocks.STONE);
        helper.setBlock(position, Blocks.CHEST);
        if (helper.getLevel().getBlockEntity(helper.absolutePos(position))
                instanceof ChestBlockEntity chest) {
            return chest;
        }
        throw new AssertionError("Expected chest block entity at " + position);
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, BlockPos near) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(near));
        player.setPosRaw(center.x, center.y + 1.0, center.z);
        return player;
    }

    private static void fillPlayerInventory(ServerPlayer player, ItemStack overflowBox) {
        player.getInventory().clearContent();
        player.getInventory().setSelectedSlot(0);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, STACK_SIZE));
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
        player.getInventory().setItem(1, overflowBox);
    }

    private static void fillContainer(Container container, Item item, int slots) {
        for (int slot = 0; slot < slots; slot++) {
            container.setItem(slot, new ItemStack(item, STACK_SIZE));
        }
        container.setChanged();
    }

    private static Container getShulkerInventory(ServerPlayer player, ItemStack shulkerBox) {
        if (!QuickShulkerIntegration.isAvailable()) {
            throw new AssertionError("Quick Shulker integration should be available in this test run");
        }
        QuickShulkerData data = QuickOpenableRegistry.getQuickie(shulkerBox.getItem());
        if (data == null || !data.supportsBundleing) {
            throw new AssertionError("Quick Shulker should register shulker box bundling support");
        }
        Container inventory = data.getInventory(player, shulkerBox);
        if (inventory == null) {
            throw new AssertionError("Quick Shulker should expose the shulker box inventory");
        }
        return inventory;
    }

    private static int countItem(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static int countItems(Container container) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            count += container.getItem(slot).getCount();
        }
        return count;
    }

    private static int countGroundItems(GameTestHelper helper, Item item) {
        return helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, helper.getBoundsWithPadding())
                .stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    private static ItemStack findGroundStack(GameTestHelper helper, Item item) {
        return helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, helper.getBoundsWithPadding())
                .stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(item))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected ground item " + item));
    }
}
