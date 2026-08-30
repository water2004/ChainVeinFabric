package org.edtp.chainveinfabric;

import java.util.List;
import java.lang.reflect.Field;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kyrptonaught.quickshulker.api.QuickOpenableRegistry;
import net.kyrptonaught.quickshulker.api.QuickShulkerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
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
import org.edtp.chainveinfabric.server.DirectDropCollector;

public final class QuickShulkerServerGameTests {
    private static final int CONTAINER_SLOTS = 27;
    private static final int STACK_SIZE = 64;
    private static final int SHULKER_CAPACITY = CONTAINER_SLOTS * STACK_SIZE;
    private static final Item OVERFLOW_BOX_ITEM = Items.DYED_SHULKER_BOX.pick(DyeColor.BLUE);
    private static final Item CONTAINED_BOX_ITEM = Items.DYED_SHULKER_BOX.pick(DyeColor.RED);

    @GameTest
    public void filledShulkerFromChestRemainsIntactWhenItCannotBeNested(
            GameTestHelper helper) {
        if (skipWithoutQuickShulker(helper)) return;
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
        if (skipWithoutQuickShulker(helper)) return;
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
        if (skipWithoutQuickShulker(helper)) return;
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

    @GameTest
    public void transactionalOverflowMergesStacksBeforeUsingEmptySlots(
            GameTestHelper helper) {
        if (skipWithoutQuickShulker(helper)) return;
        List<BlockPos> targets = List.of(
                new BlockPos(1, 1, 1),
                new BlockPos(2, 1, 1),
                new BlockPos(3, 1, 1));
        for (BlockPos target : targets) helper.setBlock(target, Blocks.DIRT);

        ServerPlayer player = createPlayer(helper, targets.getFirst());
        ItemStack overflowBox = new ItemStack(OVERFLOW_BOX_ITEM);
        fillPlayerInventory(player, overflowBox);
        player.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SHOVEL));
        Container overflowInventory = getShulkerInventory(player, overflowBox);
        overflowInventory.setItem(0, new ItemStack(Items.DIRT, 63));
        overflowInventory.setChanged();

        Chainveinfabric.handleMine(
                player,
                targets.stream().map(helper::absolutePos).toList(),
                true,
                true);

        overflowInventory = getShulkerInventory(player, overflowBox);
        helper.assertValueEqual(countItem(overflowInventory, Items.DIRT), 66,
                "Compatible overflow should fill the existing stack before using an empty slot");
        helper.assertValueEqual(countOccupiedSlots(overflowInventory, Items.DIRT), 2,
                "Three mined blocks should occupy only the two required stack slots");
        helper.assertValueEqual(countGroundItems(helper, Items.DIRT), 0,
                "All overflow should fit in the resolved storage slots");
        for (BlockPos target : targets) helper.assertBlockNotPresent(Blocks.DIRT, target);
        helper.succeed();
    }

    @GameTest
    public void everyPathFillsEarlierBoxBeforeLaterPartialStack(
            GameTestHelper helper) {
        if (skipWithoutQuickShulker(helper)) return;
        List<BlockPos> targets = List.of(
                new BlockPos(1, 1, 1),
                new BlockPos(2, 1, 1));
        for (BlockPos target : targets) helper.setBlock(target, Blocks.DIRT);

        ServerPlayer player = createPlayer(helper, targets.getFirst());
        ItemStack earlierEmptyBox = new ItemStack(OVERFLOW_BOX_ITEM);
        ItemStack laterMergeBox = new ItemStack(Items.SHULKER_BOX);
        fillPlayerInventory(player, earlierEmptyBox);
        player.getInventory().setItem(2, laterMergeBox);
        player.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SHOVEL));
        Container laterInventory = getShulkerInventory(player, laterMergeBox);
        laterInventory.setItem(0, new ItemStack(Items.DIRT, 63));
        laterInventory.setChanged();

        Chainveinfabric.handleMine(
                player,
                targets.stream().map(helper::absolutePos).toList(),
                true,
                true);

        Container earlierInventory = getShulkerInventory(player, earlierEmptyBox);
        laterInventory = getShulkerInventory(player, laterMergeBox);
        helper.assertValueEqual(countItem(earlierInventory, Items.DIRT), 2,
                "Every path must preserve carried-box priority");
        helper.assertValueEqual(countItem(laterInventory, Items.DIRT), 63,
                "No path should prefer a later partial stack");
        helper.assertValueEqual(countGroundItems(helper, Items.DIRT), 0,
                "Both drops should fit in the earlier carried box");
        helper.succeed();
    }

    @GameTest
    public void fullCarriedShulkerLeavesUnacceptedDropsInWorld(
            GameTestHelper helper) {
        if (skipWithoutQuickShulker(helper)) return;
        List<BlockPos> targets = List.of(
                new BlockPos(1, 1, 1),
                new BlockPos(2, 1, 1),
                new BlockPos(3, 1, 1));
        for (BlockPos target : targets) helper.setBlock(target, Blocks.DIRT);

        ServerPlayer player = createPlayer(helper, targets.getFirst());
        ItemStack overflowBox = new ItemStack(OVERFLOW_BOX_ITEM);
        fillPlayerInventory(player, overflowBox);
        player.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SHOVEL));
        Container overflowInventory = getShulkerInventory(player, overflowBox);
        fillContainer(overflowInventory, Items.STONE, CONTAINER_SLOTS);

        Chainveinfabric.handleMine(
                player,
                targets.stream().map(helper::absolutePos).toList(),
                true,
                true);

        overflowInventory = getShulkerInventory(player, overflowBox);
        helper.assertValueEqual(countItem(overflowInventory, Items.DIRT), 0,
                "A full carried shulker must not overwrite existing contents");
        helper.assertValueEqual(countGroundItems(helper, Items.DIRT), targets.size(),
                "Every item that fits neither inventory nor shulker must spawn in the world");
        helper.assertValueEqual(countItems(overflowInventory), SHULKER_CAPACITY,
                "Rejected overflow must leave the full box unchanged");
        for (BlockPos target : targets) helper.assertBlockNotPresent(Blocks.DIRT, target);
        helper.succeed();
    }

    @GameTest
    public void configuredQuickShulkerPathIsActuallyAvailable(GameTestHelper helper) {
        String mode = quickShulkerMode();
        if (mode.equals("none")) {
            helper.assertTrue(!QuickShulkerIntegration.isAvailable(),
                    "Quick Shulker integration must remain safely disabled when the mod is absent");
            ItemStack remainder = new ItemStack(Items.STONE, 7);
            ServerPlayer player = createPlayer(helper, new BlockPos(1, 1, 1));
            helper.assertValueEqual(QuickShulkerIntegration.insertOverflow(player, remainder), 0,
                    "Absent integration must not claim any items");
            helper.assertValueEqual(remainder.getCount(), 7,
                    "Absent integration must preserve the complete remainder");
            helper.succeed();
            return;
        }

        helper.assertTrue(QuickShulkerIntegration.isAvailable(),
                "Configured Quick Shulker integration should be available");
        String selectedPath = selectedPath();
        helper.assertValueEqual(selectedPath, mode.equals("new") ? "DIRECT" : "LEGACY",
                "The adapter selected the wrong Quick Shulker path");
        helper.succeed();
    }

    @GameTest
    public void batchedTorchOverflowKeepsStackLimitAndOriginalDropPositions(
            GameTestHelper helper) {
        if (skipWithoutQuickShulker(helper)) return;
        helper.assertValueEqual(new ItemStack(Items.TORCH).getMaxStackSize(), 64,
                "Torch should exercise the standard 64-item stack limit");

        List<BlockPos> targets = List.of(
                new BlockPos(1, 2, 2),
                new BlockPos(5, 2, 2),
                new BlockPos(9, 2, 2));
        for (BlockPos target : targets) {
            helper.setBlock(target.below(), Blocks.STONE);
            helper.setBlock(target, Blocks.TORCH);
        }

        ServerPlayer player = createPlayer(helper, targets.get(1));
        ItemStack overflowBox = new ItemStack(OVERFLOW_BOX_ITEM);
        fillPlayerInventory(player, overflowBox);
        Container overflowInventory = getShulkerInventory(player, overflowBox);
        fillContainer(overflowInventory, Items.COBBLESTONE, CONTAINER_SLOTS);
        overflowInventory.setItem(0, new ItemStack(Items.TORCH, 63));
        overflowInventory.setChanged();

        Chainveinfabric.handleMine(
                player,
                targets.stream().map(helper::absolutePos).toList(),
                true,
                true);

        overflowInventory = getShulkerInventory(player, overflowBox);
        helper.assertValueEqual(countItem(overflowInventory, Items.TORCH), 64,
                "Batched torch insertion must stop at the slot's stack limit");
        assertConsumedThenDroppedAtOriginalPositions(helper, Items.TORCH, targets);
        for (BlockPos target : targets) helper.assertBlockNotPresent(Blocks.TORCH, target);
        helper.succeed();
    }

    @GameTest
    public void batchedEnderPearlOverflowKeepsSixteenItemStackLimitAndPositions(
            GameTestHelper helper) {
        if (skipWithoutQuickShulker(helper)) return;
        helper.assertValueEqual(new ItemStack(Items.ENDER_PEARL).getMaxStackSize(), 16,
                "Ender pearls should exercise the 16-item stack limit");

        List<BlockPos> origins = List.of(
                new BlockPos(1, 2, 2),
                new BlockPos(5, 2, 2),
                new BlockPos(9, 2, 2));
        ServerPlayer player = createPlayer(helper, origins.get(1));
        ItemStack overflowBox = new ItemStack(OVERFLOW_BOX_ITEM);
        fillPlayerInventory(player, overflowBox);
        Container overflowInventory = getShulkerInventory(player, overflowBox);
        fillContainer(overflowInventory, Items.COBBLESTONE, CONTAINER_SLOTS);
        overflowInventory.setItem(0, new ItemStack(Items.ENDER_PEARL, 15));
        overflowInventory.setChanged();

        captureDropsAt(helper, player, origins, Items.ENDER_PEARL);

        overflowInventory = getShulkerInventory(player, overflowBox);
        helper.assertValueEqual(countItem(overflowInventory, Items.ENDER_PEARL), 16,
                "Batched pearl insertion must stop at the 16-item stack limit");
        assertConsumedThenDroppedAtOriginalPositions(helper, Items.ENDER_PEARL, origins);
        helper.succeed();
    }

    @GameTest
    public void batchedTorchStacksKeepPartialCountsAtOriginalPositions(
            GameTestHelper helper) {
        if (skipWithoutQuickShulker(helper)) return;

        List<BlockPos> origins = List.of(
                new BlockPos(1, 2, 2),
                new BlockPos(5, 2, 2),
                new BlockPos(9, 2, 2));
        ServerPlayer player = createPlayer(helper, origins.get(1));
        ItemStack overflowBox = new ItemStack(OVERFLOW_BOX_ITEM);
        fillPlayerInventory(player, overflowBox);
        Container overflowInventory = getShulkerInventory(player, overflowBox);
        fillContainer(overflowInventory, Items.COBBLESTONE, CONTAINER_SLOTS);
        overflowInventory.setItem(0, new ItemStack(Items.TORCH, 60));
        overflowInventory.setChanged();

        captureStacksAt(helper, player, origins, List.of(
                new ItemStack(Items.TORCH, 2),
                new ItemStack(Items.TORCH, 5),
                new ItemStack(Items.TORCH, 9)));

        overflowInventory = getShulkerInventory(player, overflowBox);
        helper.assertValueEqual(countItem(overflowInventory, Items.TORCH), 64,
                "The batched request should insert exactly the four available torches");
        helper.assertItemEntityNotPresent(Items.TORCH, origins.get(0), 1.25);
        helper.assertItemEntityCountIs(Items.TORCH, origins.get(1), 1.25, 3);
        helper.assertItemEntityCountIs(Items.TORCH, origins.get(2), 1.25, 9);
        helper.succeed();
    }

    @GameTest
    public void batchedUnstackableOverflowKeepsOneItemLimitAndPosition(
            GameTestHelper helper) {
        if (skipWithoutQuickShulker(helper)) return;
        helper.assertValueEqual(new ItemStack(Items.DIAMOND_PICKAXE).getMaxStackSize(), 1,
                "Diamond pickaxes should exercise the unstackable-item limit");

        List<BlockPos> origins = List.of(
                new BlockPos(2, 2, 2),
                new BlockPos(7, 2, 2));
        ServerPlayer player = createPlayer(helper, origins.getFirst());
        ItemStack overflowBox = new ItemStack(OVERFLOW_BOX_ITEM);
        fillPlayerInventory(player, overflowBox);
        Container overflowInventory = getShulkerInventory(player, overflowBox);
        fillContainer(overflowInventory, Items.COBBLESTONE, CONTAINER_SLOTS);
        overflowInventory.setItem(0, ItemStack.EMPTY);
        overflowInventory.setChanged();

        captureDropsAt(helper, player, origins, Items.DIAMOND_PICKAXE);

        overflowInventory = getShulkerInventory(player, overflowBox);
        helper.assertValueEqual(countItem(overflowInventory, Items.DIAMOND_PICKAXE), 1,
                "A batched unstackable item must occupy exactly one storage slot");
        helper.assertValueEqual(countGroundItems(helper, Items.DIAMOND_PICKAXE), 1,
                "The second unstackable item must remain in the world");
        helper.assertItemEntityNotPresent(Items.DIAMOND_PICKAXE, origins.get(0), 1.25);
        helper.assertItemEntityCountIs(Items.DIAMOND_PICKAXE, origins.get(1), 1.25, 1);
        helper.succeed();
    }

    @GameTest
    public void itemFrameDropsOverflowThroughEntityFallback(
            GameTestHelper helper) {
        if (skipWithoutQuickShulker(helper)) return;

        BlockPos framePosition = new BlockPos(3, 2, 3);
        ServerPlayer player = createPlayer(helper, framePosition);
        ItemStack overflowBox = new ItemStack(OVERFLOW_BOX_ITEM);
        fillPlayerInventory(player, overflowBox);
        ItemFrame frame = breakDiamondItemFrame(helper, player, framePosition);

        Container overflowInventory = getShulkerInventory(player, overflowBox);
        helper.assertTrue(frame.isRemoved(),
                "The item frame should be removed after its second hit");
        helper.assertValueEqual(countItem(overflowInventory, Items.DIAMOND), 1,
                "The displayed item must overflow exactly once");
        helper.assertValueEqual(countItem(overflowInventory, Items.ITEM_FRAME), 1,
                "The frame item must overflow exactly once");
        helper.assertValueEqual(countGroundItems(helper, Items.DIAMOND), 0,
                "A stored displayed item must not also remain in the world");
        helper.assertValueEqual(countGroundItems(helper, Items.ITEM_FRAME), 0,
                "A stored frame item must not also remain in the world");
        helper.succeed();
    }

    @GameTest
    public void fullShulkerLeavesItemFrameDropsInWorldThroughEntityFallback(
            GameTestHelper helper) {
        if (skipWithoutQuickShulker(helper)) return;

        BlockPos framePosition = new BlockPos(3, 2, 3);
        ServerPlayer player = createPlayer(helper, framePosition);
        ItemStack overflowBox = new ItemStack(OVERFLOW_BOX_ITEM);
        fillPlayerInventory(player, overflowBox);
        Container overflowInventory = getShulkerInventory(player, overflowBox);
        fillContainer(overflowInventory, Items.COBBLESTONE, CONTAINER_SLOTS);
        ItemFrame frame = breakDiamondItemFrame(helper, player, framePosition);

        overflowInventory = getShulkerInventory(player, overflowBox);
        helper.assertTrue(frame.isRemoved(),
                "The item frame should still be removed when storage is full");
        helper.assertValueEqual(countItem(overflowInventory, Items.DIAMOND), 0,
                "A full shulker must not overwrite contents with the displayed item");
        helper.assertValueEqual(countItem(overflowInventory, Items.ITEM_FRAME), 0,
                "A full shulker must not overwrite contents with the frame item");
        helper.assertValueEqual(countGroundItems(helper, Items.DIAMOND), 1,
                "The rejected displayed item must remain in the world exactly once");
        helper.assertValueEqual(countGroundItems(helper, Items.ITEM_FRAME), 1,
                "The rejected frame item must remain in the world exactly once");
        helper.succeed();
    }

    private static ItemFrame breakDiamondItemFrame(
            GameTestHelper helper,
            ServerPlayer player,
            BlockPos framePosition) {
        helper.setBlock(framePosition.north(), Blocks.STONE);
        ItemFrame frame = new ItemFrame(
                helper.getLevel(),
                helper.absolutePos(framePosition),
                Direction.SOUTH);
        frame.setItem(new ItemStack(Items.DIAMOND));
        helper.assertTrue(helper.getLevel().addFreshEntity(frame),
                "The item frame should be added before drop capture starts");

        DirectDropCollector.run(player, true, 64, () -> {
            boolean removedItem = frame.hurtServer(
                    helper.getLevel(),
                    player.damageSources().playerAttack(player),
                    1.0F);
            boolean removedFrame = frame.hurtServer(
                    helper.getLevel(),
                    player.damageSources().playerAttack(player),
                    1.0F);
            return removedItem && removedFrame;
        });
        return frame;
    }

    private static void captureDropsAt(
            GameTestHelper helper,
            ServerPlayer player,
            List<BlockPos> origins,
            Item item) {
        captureStacksAt(
                helper,
                player,
                origins,
                origins.stream().map(ignored -> new ItemStack(item)).toList());
    }

    private static void captureStacksAt(
            GameTestHelper helper,
            ServerPlayer player,
            List<BlockPos> origins,
            List<ItemStack> stacks) {
        if (origins.size() != stacks.size()) {
            throw new IllegalArgumentException("Each captured stack needs one origin");
        }
        DirectDropCollector.run(player, true, 64, () -> {
            for (int index = 0; index < origins.size(); index++) {
                BlockPos origin = origins.get(index);
                Vec3 position = Vec3.atCenterOf(helper.absolutePos(origin));
                helper.getLevel().addFreshEntity(new ItemEntity(
                        helper.getLevel(), position.x, position.y, position.z,
                        stacks.get(index)));
            }
            return true;
        });
    }

    private static void assertConsumedThenDroppedAtOriginalPositions(
            GameTestHelper helper,
            Item item,
            List<BlockPos> origins) {
        helper.assertItemEntityNotPresent(item, origins.getFirst(), 1.25);
        for (int index = 1; index < origins.size(); index++) {
            helper.assertItemEntityCountIs(item, origins.get(index), 1.25, 1);
        }
    }

    private static boolean skipWithoutQuickShulker(GameTestHelper helper) {
        if (!quickShulkerMode().equals("none")) return false;
        helper.succeed();
        return true;
    }

    private static String quickShulkerMode() {
        return System.getProperty("chainveinfabric.gametest.quickshulker", "legacy");
    }

    private static String selectedPath() {
        try {
            Field field = QuickShulkerIntegration.class.getDeclaredField("SELECTED_PATH");
            field.setAccessible(true);
            return field.get(null).toString();
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Unable to inspect the encapsulated adapter in GameTest", error);
        }
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

    private static int countOccupiedSlots(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(item)) count++;
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
