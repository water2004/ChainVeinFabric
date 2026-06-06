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
    public static ConfigSnapshot from(ChainVeinConfig config) {
        return new ConfigSnapshot(
            config.mode,
            config.searchAlgorithm,
            config.maxChainBlocks,
            config.maxRadius,
            config.sphereRadius,
            config.squareLength,
            config.squareMiningPoint,
            config.cuboidL,
            config.cuboidW,
            config.cuboidH,
            config.cuboidMiningPoint,
            config.diagonalEdge,
            config.diagonalCorner,
            Set.copyOf(config.whitelistedBlocks),
            Set.copyOf(config.whitelistedCrops),
            Set.copyOf(config.whitelistedUtilityBlocks)
        );
    }
}
