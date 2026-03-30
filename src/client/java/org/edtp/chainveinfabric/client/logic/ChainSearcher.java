package org.edtp.chainveinfabric.client.logic;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;

import java.util.*;
import java.util.function.Predicate;

public class ChainSearcher {
    
    public static List<BlockPos> search(MinecraftClient client, BlockPos startPos, Direction face, Predicate<BlockPos> filter) {
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        
        return switch (config.searchAlgorithm) {
            case ADJACENT_SAME, ADJACENT_WHITELIST -> findConnected(client, startPos, filter);
            case SPHERE -> findSphere(client, startPos, config.sphereRadius, filter);
            case SQUARE -> findSquare(client, startPos, face, config.squareLength, config.squareMiningPoint, filter);
            case CUBOID -> findCuboid(client, startPos, face, config.cuboidL, config.cuboidW, config.cuboidH, config.cuboidMiningPoint, filter);
        };
    }

    private static List<BlockPos> findConnected(MinecraftClient client, BlockPos startPos, Predicate<BlockPos> matcher) {
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();

        int maxBlocks = ChainveinfabricClient.CONFIG.maxChainBlocks;
        int maxRadiusSq = ChainveinfabricClient.CONFIG.maxRadius * ChainveinfabricClient.CONFIG.maxRadius;

        queue.add(startPos);
        visited.add(startPos);
        if (matcher.test(startPos)) {
            result.add(startPos);
        }

        while (!queue.isEmpty() && result.size() < maxBlocks) {
            BlockPos current = queue.poll();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        int diffs = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
                        if (diffs == 2 && !ChainveinfabricClient.CONFIG.diagonalEdge) continue;
                        if (diffs == 3 && !ChainveinfabricClient.CONFIG.diagonalCorner) continue;

                        BlockPos neighbor = current.add(dx, dy, dz);
                        if (visited.contains(neighbor)) continue;
                        visited.add(neighbor);

                        if (neighbor.getSquaredDistance(startPos) > maxRadiusSq) continue;

                        if (matcher.test(neighbor)) {
                            result.add(neighbor);
                            queue.add(neighbor);
                            if (result.size() >= maxBlocks) break;
                        }
                    }
                    if (result.size() >= maxBlocks) break;
                }
                if (result.size() >= maxBlocks) break;
            }
        }
        return result;
    }

    private static List<BlockPos> findSphere(MinecraftClient client, BlockPos center, int radius, Predicate<BlockPos> filter) {
        List<BlockPos> result = new ArrayList<>();
        int radiusSq = radius * radius;
        int maxBlocks = ChainveinfabricClient.CONFIG.maxChainBlocks;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                        BlockPos pos = center.add(dx, dy, dz);
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

        // Determine plane axes
        Direction u = (face.getAxis() == Direction.Axis.Y) ? Direction.EAST : Direction.EAST;
        if (face.getAxis() == Direction.Axis.X) u = Direction.SOUTH;
        if (face.getAxis() == Direction.Axis.Z) u = Direction.EAST;
        
        Direction v = (face.getAxis() == Direction.Axis.Y) ? Direction.SOUTH : Direction.UP;
        if (face.getAxis() == Direction.Axis.X) v = Direction.UP;
        if (face.getAxis() == Direction.Axis.Z) v = Direction.UP;

        int[] offsets = getOffsets(length, length, point);

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

        Direction d = face.getOpposite();
        Direction u = (face.getAxis() == Direction.Axis.Y) ? Direction.EAST : Direction.EAST;
        if (face.getAxis() == Direction.Axis.X) u = Direction.SOUTH;
        if (face.getAxis() == Direction.Axis.Z) u = Direction.EAST;
        
        Direction v = (face.getAxis() == Direction.Axis.Y) ? Direction.SOUTH : Direction.UP;
        if (face.getAxis() == Direction.Axis.X) v = Direction.UP;
        if (face.getAxis() == Direction.Axis.Z) v = Direction.UP;

        int[] offsets = getOffsets(l, w, point);
        
        for (int k = 0; k < h; k++) {
            for (int i = 0; i < l; i++) {
                for (int j = 0; j < w; j++) {
                    BlockPos pos = start.offset(u, offsets[0] + i).offset(v, offsets[1] + j).offset(d, k);
                    if (filter.test(pos)) {
                        result.add(pos);
                        if (result.size() >= maxBlocks) return result;
                    }
                }
            }
        }
        return result;
    }

    private static int[] getOffsets(int l, int w, ChainVeinConfig.MiningPoint point) {
        return switch (point) {
            case CENTER -> new int[]{-l / 2, -w / 2};
            case TOP_LEFT -> new int[]{0, 0};
            case TOP_RIGHT -> new int[]{-l + 1, 0};
            case BOTTOM_LEFT -> new int[]{0, -w + 1};
            case BOTTOM_RIGHT -> new int[]{-l + 1, -w + 1};
        };
    }
}
