package org.edtp.chainveinfabric.client.logic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;

import java.util.*;
import java.util.function.Predicate;

public class ChainSearcher {
    public static List<BlockPos> search(MinecraftClient client, BlockPos start, Direction face, Predicate<BlockPos> filter) {
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        return switch (config.searchAlgorithm) {
            case ADJACENT_SAME, ADJACENT_WHITELIST -> findConnected(client, start, face, filter);
            case SPHERE -> findSphere(client, start, face, config.sphereRadius, filter);
            case SQUARE -> findSquare(client, start, face, config.squareLength, config.squareMiningPoint, filter);
            case CUBOID -> findCuboid(client, start, face, config.cuboidL, config.cuboidW, config.cuboidH, config.cuboidMiningPoint, filter);
        };
    }

    private static List<BlockPos> findConnected(MinecraftClient client, BlockPos start, Direction face, Predicate<BlockPos> filter) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        int maxBlocks = ChainveinfabricClient.CONFIG.maxChainBlocks;
        int maxRadius = ChainveinfabricClient.CONFIG.maxRadius;
        boolean diagonalEdge = ChainveinfabricClient.CONFIG.diagonalEdge;
        boolean diagonalCorner = ChainveinfabricClient.CONFIG.diagonalCorner;

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && result.size() < maxBlocks) {
            BlockPos current = queue.poll();
            if (filter.test(current)) {
                result.add(current);

                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            if (x == 0 && y == 0 && z == 0) continue;

                            int absSum = Math.abs(x) + Math.abs(y) + Math.abs(z);
                            if (absSum == 1 || (diagonalEdge && absSum == 2) || (diagonalCorner && absSum == 3)) {
                                BlockPos neighbor = current.add(x, y, z);
                                if (!visited.contains(neighbor) && start.getManhattanDistance(neighbor) <= maxRadius) {
                                    visited.add(neighbor);
                                    queue.add(neighbor);
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private static List<BlockPos> findSphere(MinecraftClient client, BlockPos start, Direction face, int radius, Predicate<BlockPos> filter) {
        List<BlockPos> result = new ArrayList<>();
        int maxBlocks = ChainveinfabricClient.CONFIG.maxChainBlocks;
        double rSq = radius * radius;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= rSq) {
                        BlockPos pos = start.add(x, y, z);
                        if (filter.test(pos)) {
                            result.add(pos);
                            if (result.size() >= maxBlocks) return result;
                        }
                    }
                }
            }
        }
        return result;
    }

    private static List<BlockPos> findSquare(MinecraftClient client, BlockPos start, Direction face, int length, ChainVeinConfig.MiningPoint point, Predicate<BlockPos> filter) {
        List<BlockPos> result = new ArrayList<>();
        int maxBlocks = ChainveinfabricClient.CONFIG.maxChainBlocks;

        // Perspective-aware axes: U (Right), V (Up)
        Direction u, v;
        switch (face) {
            case UP -> { u = Direction.EAST; v = Direction.NORTH; }
            case DOWN -> { u = Direction.EAST; v = Direction.SOUTH; }
            case NORTH -> { u = Direction.EAST; v = Direction.UP; }
            case SOUTH -> { u = Direction.WEST; v = Direction.UP; }
            case WEST -> { u = Direction.SOUTH; v = Direction.UP; }
            case EAST -> { u = Direction.NORTH; v = Direction.UP; }
            default -> { u = Direction.EAST; v = Direction.UP; }
        }

        int[] offsets = getOffsets(length, length, 1, point);

        for (int i = 0; i < length; i++) {
            for (int j = 0; j < length; j++) {
                BlockPos pos = start.offset(u, offsets[0] + i).offset(v, offsets[1] + j);
                if (filter.test(pos)) {
                    result.add(pos);
                    if (result.size() >= maxBlocks) return result;
                }
            }
        }
        return result;
    }

    private static List<BlockPos> findCuboid(MinecraftClient client, BlockPos start, Direction face, int l, int w, int h, ChainVeinConfig.MiningPoint point, Predicate<BlockPos> filter) {
        List<BlockPos> result = new ArrayList<>();
        int maxBlocks = ChainveinfabricClient.CONFIG.maxChainBlocks;

        // Perspective-aware axes: U (Right), V (Up), D (Depth/Into-face)
        Direction u, v;
        Direction d = face.getOpposite();
        switch (face) {
            case UP -> { u = Direction.EAST; v = Direction.NORTH; }
            case DOWN -> { u = Direction.EAST; v = Direction.SOUTH; }
            case NORTH -> { u = Direction.EAST; v = Direction.UP; }
            case SOUTH -> { u = Direction.WEST; v = Direction.UP; }
            case WEST -> { u = Direction.SOUTH; v = Direction.UP; }
            case EAST -> { u = Direction.NORTH; v = Direction.UP; }
            default -> { u = Direction.EAST; v = Direction.UP; }
        }

        int[] offsets = getOffsets(l, w, h, point);

        for (int k = 0; k < h; k++) {
            for (int i = 0; i < l; i++) {
                for (int j = 0; j < w; j++) {
                    BlockPos pos = start.offset(u, offsets[0] + i)
                                        .offset(v, offsets[1] + j)
                                        .offset(d, offsets[2] + k);
                    if (filter.test(pos)) {
                        result.add(pos);
                        if (result.size() >= maxBlocks) return result;
                    }
                }
            }
        }
        return result;
    }

    private static int[] getOffsets(int l, int w, int h, ChainVeinConfig.MiningPoint point) {
        return switch (point) {
            case CENTER -> new int[]{-l / 2, -w / 2, 0};
            case FRONT_TOP_LEFT -> new int[]{0, -w + 1, 0};
            case FRONT_TOP_RIGHT -> new int[]{-l + 1, -w + 1, 0};
            case FRONT_BOTTOM_LEFT -> new int[]{0, 0, 0};
            case FRONT_BOTTOM_RIGHT -> new int[]{-l + 1, 0, 0};
            case BACK_TOP_LEFT -> new int[]{0, -w + 1, -h + 1};
            case BACK_TOP_RIGHT -> new int[]{-l + 1, -w + 1, -h + 1};
            case BACK_BOTTOM_LEFT -> new int[]{0, 0, -h + 1};
            case BACK_BOTTOM_RIGHT -> new int[]{-l + 1, 0, -h + 1};
        };
    }
}
