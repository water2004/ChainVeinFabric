package org.edtp.chainveinfabric;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public final class ChainVeinServerGameTests {
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
        int dirt = player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.getItem() == Items.DIRT)
                .mapToInt(ItemStack::getCount)
                .sum();
        helper.assertValueEqual(dirt, relativeTargets.size(),
                "Direct mining should insert every drop into the inventory");
        helper.assertItemEntityNotPresent(Items.DIRT);
        helper.succeed();
    }
}
