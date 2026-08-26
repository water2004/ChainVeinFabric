package org.edtp.chainveinfabric.client.logic.search;

import java.util.Set;

import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig.ChainMode;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig.MiningPoint;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig.SearchAlgorithm;

/** Immutable search-related configuration captured on the client thread. */
public record SearchConfig(
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
        Set<String> whitelist) {

    public SearchConfig {
        whitelist = Set.copyOf(whitelist);
    }

    public static SearchConfig from(ChainVeinConfig config) {
        return new SearchConfig(
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
                config.getWhitelist(config.mode));
    }
}
