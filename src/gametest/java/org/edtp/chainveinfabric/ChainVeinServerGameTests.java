package org.edtp.chainveinfabric;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.edtp.chainveinfabric.server.ChainVeinServerConfig;

import java.util.List;
import java.util.UUID;

public final class ChainVeinServerGameTests {
    @GameTest
    public void miningRequestIsCappedInPayloadOrder(GameTestHelper helper) {
        List<BlockPos> targets = List.of(
                new BlockPos(1, 1, 1),
                new BlockPos(2, 1, 1),
                new BlockPos(3, 1, 1));
        targets.forEach(target -> helper.setBlock(target, Blocks.DIRT));

        ServerPlayer player = createSurvivalPlayer(helper, targets.getFirst(), Items.DIAMOND_SHOVEL);
        int previous = ChainVeinServerConfig.values().maxBlocks();
        try {
            ChainVeinServerConfig.setMaxBlocks(2);
            Chainveinfabric.handleMine(
                    player,
                    targets.stream().map(helper::absolutePos).toList(),
                    false,
                    false);
        } finally {
            ChainVeinServerConfig.setMaxBlocks(previous);
        }

        helper.assertBlockNotPresent(Blocks.DIRT, targets.get(0));
        helper.assertBlockNotPresent(Blocks.DIRT, targets.get(1));
        helper.assertBlockPresent(Blocks.DIRT, targets.get(2));
        helper.succeed();
    }

    @GameTest
    public void serverMiningHasNoPlayerDistanceLimit(GameTestHelper helper) {
        BlockPos target = new BlockPos(1, 1, 1);
        helper.setBlock(target, Blocks.DIRT);

        ServerPlayer player = createSurvivalPlayer(helper, target, Items.DIAMOND_SHOVEL);
        Vec3 originalPosition = player.position();
        player.setPosRaw(originalPosition.x + 20.0, originalPosition.y, originalPosition.z);

        Chainveinfabric.handleMine(
                player,
                List.of(helper.absolutePos(target)),
                false,
                false);

        helper.assertBlockNotPresent(Blocks.DIRT, target);
        helper.succeed();
    }

    @GameTest
    public void directPickupOnlyCapturesDropsInsideConfiguredRadius(GameTestHelper helper) {
        BlockPos near = new BlockPos(1, 1, 1);
        BlockPos far = new BlockPos(5, 1, 1);
        helper.setBlock(near, Blocks.DIRT);
        helper.setBlock(far, Blocks.DIRT);

        ServerPlayer player = createSurvivalPlayer(helper, near, Items.DIAMOND_SHOVEL);
        int previous = ChainVeinServerConfig.values().pickupRadius();
        try {
            ChainVeinServerConfig.setPickupRadius(2);
            Chainveinfabric.handleMine(
                    player,
                    List.of(helper.absolutePos(near), helper.absolutePos(far)),
                    true,
                    true);
        } finally {
            ChainVeinServerConfig.setPickupRadius(previous);
        }

        helper.assertValueEqual(countItem(player, Items.DIRT), 1,
                "Only the near drop should enter inventory or Quick Shulker handling");
        helper.assertItemEntityNotPresent(Items.DIRT, near, 1.5);
        helper.assertItemEntityPresent(Items.DIRT, far, 1.5);
        helper.succeed();
    }

    @GameTest
    public void chainInteractionUsesVanillaBlockInteractionRange(GameTestHelper helper) {
        BlockPos near = new BlockPos(1, 1, 1);
        BlockPos far = new BlockPos(5, 1, 1);
        helper.setBlock(near, Blocks.FARMLAND);
        helper.setBlock(far, Blocks.FARMLAND);

        ServerPlayer player = createSurvivalPlayer(helper, near, Items.WHEAT_SEEDS);
        player.getMainHandItem().setCount(2);
        player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).setBaseValue(2.0);
        Chainveinfabric.handleInteract(
                player,
                List.of(helper.absolutePos(near), helper.absolutePos(far)));

        helper.assertBlockPresent(Blocks.WHEAT, near.above());
        helper.assertBlockNotPresent(Blocks.WHEAT, far.above());
        helper.succeed();
    }

    @GameTest
    public void chainInteractionFollowsExpandedServerRange(GameTestHelper helper) {
        BlockPos target = new BlockPos(1, 1, 1);
        helper.setBlock(target, Blocks.FARMLAND);

        ServerPlayer player = createSurvivalPlayer(helper, target, Items.WHEAT_SEEDS);
        Vec3 position = player.position();
        player.setPosRaw(position.x + 20.0, position.y, position.z);
        player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).setBaseValue(32.0);

        Chainveinfabric.handleInteract(player, List.of(helper.absolutePos(target)));

        helper.assertBlockPresent(Blocks.WHEAT, target.above());
        helper.succeed();
    }

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

        ServerPlayer player = makeSurvivalPlayer(helper);
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

        ServerPlayer player = makeSurvivalPlayer(helper);
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

        ServerPlayer player = makeSurvivalPlayer(helper);
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

    private static ServerPlayer makeSurvivalPlayer(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "chainvein-gametest"),
                ClientInformation.createDefault()) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }
        };
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        return player;
    }

    private static ChestBlockEntity requireChest(GameTestHelper helper, BlockPos relativePos) {
        if (helper.getLevel().getBlockEntity(helper.absolutePos(relativePos))
                instanceof ChestBlockEntity chest) {
            return chest;
        }
        throw new AssertionError("Expected chest block entity at " + relativePos);
    }

    private static ServerPlayer createSurvivalPlayer(
            GameTestHelper helper, BlockPos near, Item heldItem) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(heldItem));
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(near));
        player.setPosRaw(center.x, center.y + 1.0, center.z);
        return player;
    }

    private static int countItem(ServerPlayer player, Item item) {
        return player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.getItem() == item)
                .mapToInt(ItemStack::getCount)
                .sum();
    }
}
