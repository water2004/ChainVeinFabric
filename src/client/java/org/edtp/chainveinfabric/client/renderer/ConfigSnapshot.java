package org.edtp.chainveinfabric.client.renderer;

import java.util.Set;

import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig.ChainMode;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig.MiningPoint;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig.SearchAlgorithm;

public record ConfigSnapshot(
    ChainMode mode,
    SearchAlgorithm searchAlgorithm,
    int maxChainBlocks,
    int maxRadius,
    int sphereRadius,
    int squareLength,
    MiningPoint squareMiningPoint,
    int cuboidL,
    int cuboidW,
    int cuboidH,
    MiningPoint cuboidMiningPoint,
    boolean diagonalEdge,
    boolean diagonalCorner,
    Set<String> whitelistedBlocks,
    Set<String> whitelistedCrops,
    Set<String> whitelistedUtilityBlocks
) {
    public static ConfigSnapshot from(ChainVeinConfig c) {
        return new ConfigSnapshot(
            c.mode,
            c.searchAlgorithm,
            c.maxChainBlocks,
            c.maxRadius,
            c.sphereRadius,
            c.squareLength,
            c.squareMiningPoint,
            c.cuboidL,
            c.cuboidW,
            c.cuboidH,
            c.cuboidMiningPoint,
            c.diagonalEdge,
            c.diagonalCorner,
            Set.copyOf(c.whitelistedBlocks),
            Set.copyOf(c.whitelistedCrops),
            Set.copyOf(c.whitelistedUtilityBlocks)
        );
    }
}
