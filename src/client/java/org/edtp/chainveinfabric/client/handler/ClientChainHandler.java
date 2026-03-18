package org.edtp.chainveinfabric.client.handler;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.logic.InteractLogic;
import org.edtp.chainveinfabric.client.logic.MineLogic;

import java.util.LinkedList;
import java.util.Queue;

public class ClientChainHandler {
    private static boolean isChainOperating = false;
    private static final Queue<Runnable> packetQueue = new LinkedList<>();
    private static int tickCounter = 0;

    public static boolean isChainOperating() {
        return isChainOperating;
    }

    public static void setChainOperating(boolean value) {
        isChainOperating = value;
    }

    public static void addTask(Runnable task) {
        packetQueue.add(task);
    }

    public static void onTick(MinecraftClient client) {
        if (packetQueue.isEmpty()) {
            tickCounter = 0;
            return;
        }

        int interval = ChainveinfabricClient.CONFIG.packetInterval;
        if (interval <= 0) {
            // 无间隔，一刻发完所有包
            while (!packetQueue.isEmpty()) {
                packetQueue.poll().run();
            }
            return;
        }

        // 1 tick = 50ms
        if (interval >= 50) {
            int ticksPerPacket = interval / 50;
            if (++tickCounter >= ticksPerPacket) {
                Runnable task = packetQueue.poll();
                if (task != null) task.run();
                tickCounter = 0;
            }
        } else {
            // 间隔小于 50ms，每刻发多个包
            int packetsPerTick = 50 / interval;
            for (int i = 0; i < packetsPerTick && !packetQueue.isEmpty(); i++) {
                Runnable task = packetQueue.poll();
                if (task != null) task.run();
            }
        }
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
