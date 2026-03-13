package org.edtp.chainveinfabric.client.handler;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import org.edtp.chainveinfabric.client.logic.InteractLogic;
import org.edtp.chainveinfabric.client.logic.MineLogic;

public class ClientChainHandler {
    private static boolean isChainOperating = false;

    public static boolean isChainOperating() {
        return isChainOperating;
    }

    public static void setChainOperating(boolean value) {
        isChainOperating = value;
    }

    public static void performChainMine(MinecraftClient client, BlockPos pos, BlockState state) {
        isChainOperating = true;
        try {
            MineLogic.perform(client, pos, state);
        } finally {
            isChainOperating = false;
        }
    }

    public static void performChainInteract(MinecraftClient client, BlockPos pos, BlockState state, ItemStack stack) {
        isChainOperating = true;
        try {
            InteractLogic.perform(client, pos, state, stack);
        } finally {
            isChainOperating = false;
        }
    }
}
