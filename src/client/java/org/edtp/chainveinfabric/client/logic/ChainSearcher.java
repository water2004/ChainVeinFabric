package org.edtp.chainveinfabric.client.logic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ChainSearcher {

    public static List<BlockPos> search(MinecraftClient client, BlockPos pos, Direction side, Predicate<BlockPos> predicate) {
        Set<BlockPos> result;
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        World world = client.world;

        switch (config.searchAlgorithm) {
            case SPHERE -> result = findSphere(world, pos, config.sphereRadius, s -> predicate.test(s));
            case SQUARE -> result = findSquare(world, pos, config.squareLength, config.squareMiningPoint, client.player, s -> predicate.test(s));
            case CUBOID -> result = findCuboid(world, pos, config.cuboidL, config.cuboidW, config.cuboidH, config.cuboidMiningPoint, client.player, s -> predicate.test(s));
            default -> result = findBlocks(world, pos, config.maxChainBlocks, config.maxRadius, s -> predicate.test(s), config.diagonalEdge, config.diagonalCorner);
        }

        return result.stream()
                .sorted(Comparator.comparingDouble(p -> p.getSquaredDistance(pos)))
                .collect(Collectors.toList());
    }

    public static Set<BlockPos> findBlocks(World world, BlockPos startPos, int maxBlocks, int maxRadius, Predicate<BlockPos> predicate, boolean diagonalEdge, boolean diagonalCorner) {
        Set<BlockPos> result = new HashSet<>();
        Set<BlockPos> nextBorders = new HashSet<>();
        result.add(startPos);
        nextBorders.add(startPos);

        for (int r = 0; r < maxRadius && result.size() < maxBlocks; r++) {
            Set<BlockPos> currentBorders = new HashSet<>(nextBorders);
            nextBorders.clear();
            for (BlockPos pos : currentBorders) {
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            if (x == 0 && y == 0 && z == 0) continue;
                            int absSum = Math.abs(x) + Math.abs(y) + Math.abs(z);
                            if (absSum == 2 && !diagonalEdge) continue;
                            if (absSum == 3 && !diagonalCorner) continue;

                            BlockPos neighbor = pos.add(x, y, z);
                            if (!result.contains(neighbor) && predicate.test(neighbor)) {
                                result.add(neighbor);
                                nextBorders.add(neighbor);
                                if (result.size() >= maxBlocks) return result;
                            }
                        }
                    }
                }
            }
            if (nextBorders.isEmpty()) break;
        }
        return result;
    }

    public static Set<BlockPos> findSphere(World world, BlockPos startPos, int radius, Predicate<BlockPos> predicate) {
        Set<BlockPos> result = new HashSet<>();
        int r2 = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= r2) {
                        BlockPos pos = startPos.add(x, y, z);
                        if (predicate.test(pos)) {
                            result.add(pos);
                        }
                    }
                }
            }
        }
        return result;
    }

    public static Set<BlockPos> findSquare(World world, BlockPos startPos, int length, ChainVeinConfig.MiningPoint point, PlayerEntity player, Predicate<BlockPos> predicate) {
        return findCuboidInternal(world, startPos, length, length, 1, point, player, predicate, true);
    }

    public static Set<BlockPos> findCuboid(World world, BlockPos startPos, int l, int w, int h, ChainVeinConfig.MiningPoint point, PlayerEntity player, Predicate<BlockPos> predicate) {
        return findCuboidInternal(world, startPos, l, w, h, point, player, predicate, false);
    }

    private static Set<BlockPos> findCuboidInternal(World world, BlockPos startPos, int l, int w, int h, ChainVeinConfig.MiningPoint point, PlayerEntity player, Predicate<BlockPos> predicate, boolean flat) {
        Set<BlockPos> result = new HashSet<>();
        
        // Cardinal axes relative to player (W=Z-, S=Z+, A=X-, D=X+)
        // NOTE: Forward (W) is facing, and Back (S) is opposite.
        Direction facing = player.getHorizontalFacing();
        Direction zAxis = facing; 
        Direction xAxis = facing.rotateYClockwise(); // Right (D)
        Direction yAxis = Direction.UP;

        int xMin, xMax, yMin, yMax, zMin, zMax;

        // X Axis (Left/Right)
        switch (point) {
            case FRONT_TOP_LEFT, FRONT_BOTTOM_LEFT, BACK_TOP_LEFT, BACK_BOTTOM_LEFT -> {
                xMin = 0; xMax = w - 1; // Click is Left (A), expands Right (D)
            }
            case FRONT_TOP_RIGHT, FRONT_BOTTOM_RIGHT, BACK_TOP_RIGHT, BACK_BOTTOM_RIGHT -> {
                xMin = -(w - 1); xMax = 0; // Click is Right (D), expands Left (A)
            }
            default -> { // CENTER
                xMin = -((w - 1) / 2); xMax = w / 2;
            }
        }

        // Y Axis (Up/Down)
        if (flat) {
            yMin = 0; yMax = 0;
        } else {
            switch (point) {
                case FRONT_BOTTOM_LEFT, FRONT_BOTTOM_RIGHT, BACK_BOTTOM_LEFT, BACK_BOTTOM_RIGHT -> {
                    yMin = 0; yMax = h - 1; // Click is Bottom, expands Up
                }
                case FRONT_TOP_LEFT, FRONT_TOP_RIGHT, BACK_TOP_LEFT, BACK_TOP_RIGHT -> {
                    yMin = -(h - 1); yMax = 0; // Click is Top, expands Down
                }
                default -> { // CENTER
                    yMin = -((h - 1) / 2); yMax = h / 2;
                }
            }
        }

        // Z Axis (Forward/Backward)
        switch (point) {
            case FRONT_TOP_LEFT, FRONT_TOP_RIGHT, FRONT_BOTTOM_LEFT, FRONT_BOTTOM_RIGHT -> {
                zMin = -(l - 1); zMax = 0; // Click is Front (furthermost), expanding Backward (S)
            }
            case BACK_TOP_LEFT, BACK_TOP_RIGHT, BACK_BOTTOM_LEFT, BACK_BOTTOM_RIGHT -> {
                zMin = 0; zMax = l - 1; // Click is Back (closest), expanding Forward (W)
            }
            default -> { // CENTER
                zMin = -((l - 1) / 2); zMax = l / 2;
            }
        }

        for (int xi = xMin; xi <= xMax; xi++) {
            for (int yi = yMin; yi <= yMax; yi++) {
                for (int zi = zMin; zi <= zMax; zi++) {
                    BlockPos pos = startPos
                            .offset(xAxis, xi)
                            .offset(yAxis, yi)
                            .offset(zAxis, zi);
                    
                    if (predicate.test(pos)) {
                        result.add(pos);
                    }
                }
            }
        }
        
        return result;
    }
}
