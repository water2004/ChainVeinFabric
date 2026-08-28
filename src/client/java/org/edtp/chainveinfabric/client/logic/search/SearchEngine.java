package org.edtp.chainveinfabric.client.logic.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.logic.ChainSearcher;

/** The single search implementation shared by previews and all actions. */
public final class SearchEngine {
    private SearchEngine() {
    }

    public static SearchResult search(SearchRequest request) {
        SearchConfig config = request.config();
        Predicate<BlockPos> predicate = buildPredicate(request);
        boolean originMustMatch = !request.automatic()
                || config.searchAlgorithm() == ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME;
        if (originMustMatch && !predicate.test(request.origin())) {
            return SearchResult.empty(request);
        }
        Set<BlockPos> found = switch (config.searchAlgorithm()) {
            case SPHERE -> ChainSearcher.findSphere(
                    request.level(), request.origin(), config.sphereRadius(), predicate);
            case SQUARE -> ChainSearcher.findSquare(
                    request.level(), request.origin(), config.squareLength(),
                    request.automatic() ? ChainVeinConfig.MiningPoint.CENTER : config.squareMiningPoint(),
                    request.playerFacing(), predicate);
            case CUBOID -> ChainSearcher.findCuboid(
                    request.level(), request.origin(), config.cuboidL(), config.cuboidW(), config.cuboidH(),
                    request.automatic() ? ChainVeinConfig.MiningPoint.CENTER : config.cuboidMiningPoint(),
                    request.playerFacing(), predicate);
            case ADJACENT_SAME, ADJACENT_WHITELIST -> ChainSearcher.findBlocks(
                    request.level(), request.origin(),
                    request.automatic()
                            && config.searchAlgorithm() == ChainVeinConfig.SearchAlgorithm.ADJACENT_WHITELIST
                            ? config.maxChainBlocks() + 1
                            : config.maxChainBlocks(),
                    config.maxRadius(), predicate, config.diagonalEdge(), config.diagonalCorner());
        };

        if (request.automatic()
                && config.searchAlgorithm() != ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME) {
            found.remove(request.origin());
        }

        List<BlockPos> sorted = new ArrayList<>(found);
        sorted.sort(Comparator.comparingDouble(pos -> pos.distSqr(request.origin())));
        if (sorted.size() > config.maxChainBlocks()) {
            sorted = new ArrayList<>(sorted.subList(0, config.maxChainBlocks()));
        }
        return new SearchResult(request, sorted);
    }

    private static Predicate<BlockPos> buildPredicate(SearchRequest request) {
        SearchConfig config = request.config();
        BlockState targetState = request.targetState();
        String targetId = targetState != null
                ? ChainVeinConfig.getWhitelistItemId(targetState.getBlock())
                : null;

        return switch (config.mode()) {
            case CHAIN_MINE -> pos -> {
                BlockState state = stateAt(request, pos);
                String id = ChainVeinConfig.getWhitelistItemId(state.getBlock());
                if (id == null || !config.whitelist().contains(id)) return false;
                if (!request.creative() && state.getDestroySpeed(request.level(), pos) < 0.0F) return false;
                return config.searchAlgorithm() != ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME
                        || id.equals(targetId);
            };
            case CHAIN_PLANT -> {
                Block targetSoil = targetState.getBlock();
                yield pos -> stateAt(request, pos).is(targetSoil)
                        && (pos.equals(request.origin()) || request.level().getBlockState(pos.above()).isAir());
            }
            case CHAIN_UTILITY -> pos -> {
                BlockState state = stateAt(request, pos);
                String id = ChainVeinConfig.getWhitelistItemId(state.getBlock());
                if (id == null || !config.whitelist().contains(id)) return false;
                return config.searchAlgorithm() != ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME
                        || id.equals(targetId);
            };
            case SCHEMATIC_SELECTION, SCHEMATIC_EXTRA, SCHEMATIC_WRONG -> pos -> {
                BlockState state = stateAt(request, pos);
                String id = ChainVeinConfig.getWhitelistItemId(state.getBlock());
                if (id == null || !config.whitelist().contains(id)
                        || !request.litematicaContext().matches(request.level(), pos)) return false;
                if (!request.creative() && state.getDestroySpeed(request.level(), pos) < 0.0F) return false;
                return config.searchAlgorithm() != ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME
                        || id.equals(targetId);
            };
        };
    }

    private static BlockState stateAt(SearchRequest request, BlockPos pos) {
        if (pos.equals(request.origin()) && request.targetState() != null) {
            return request.targetState();
        }
        return request.level().getBlockState(pos);
    }
}
