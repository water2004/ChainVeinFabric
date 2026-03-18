package org.edtp.chainveinfabric.client.logic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;

import java.util.*;
import java.util.function.BiPredicate;

public class ChainSearcher {
    public static List<BlockPos> findConnected(MinecraftClient client, BlockPos startPos, BiPredicate<BlockPos, BlockPos> matcher) {
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();

        queue.add(startPos);
        visited.add(startPos);

        int maxBlocks = ChainveinfabricClient.CONFIG.maxChainBlocks;
        int maxRadiusSq = ChainveinfabricClient.CONFIG.maxRadius * ChainveinfabricClient.CONFIG.maxRadius;

        // The start position itself should be added to the result if it matches
        if (matcher.test(startPos, startPos)) {
            result.add(startPos);
        }

        while (!queue.isEmpty() && result.size() < maxBlocks) {
            BlockPos current = queue.poll();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        // 斜向检测规则
                        int diffs = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
                        if (diffs == 2 && !ChainveinfabricClient.CONFIG.diagonalEdge) continue;
                        if (diffs == 3 && !ChainveinfabricClient.CONFIG.diagonalCorner) continue;

                        BlockPos neighbor = current.add(dx, dy, dz);
                        
                        if (visited.contains(neighbor)) continue;
                        visited.add(neighbor);

                        if (neighbor.getSquaredDistance(startPos) > maxRadiusSq) continue;

                        if (matcher.test(startPos, neighbor)) {
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
}
