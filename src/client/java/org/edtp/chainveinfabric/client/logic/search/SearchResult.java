package org.edtp.chainveinfabric.client.logic.search;

import java.util.List;

import net.minecraft.core.BlockPos;

public record SearchResult(SearchRequest request, List<BlockPos> positions) {
    public SearchResult {
        positions = List.copyOf(positions);
    }

    public static SearchResult empty(SearchRequest request) {
        return new SearchResult(request, List.of());
    }
}
