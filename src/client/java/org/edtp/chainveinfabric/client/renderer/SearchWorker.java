package org.edtp.chainveinfabric.client.renderer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fi.dy.masa.malilib.util.data.Color4f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.logic.search.SearchRequest;
import org.edtp.chainveinfabric.client.logic.search.SearchResult;
import org.edtp.chainveinfabric.client.logic.search.SearchService;

/** Owns preview state while delegating the actual search to the shared worker. */
public class SearchWorker {

    private static final float LINE_WIDTH = 2.0f;
    private static final double EXPAND = 0.001;
    private static final Color4f COLOR_MINE = new Color4f(0.0f, 1.0f, 1.0f, 0.7f);
    private static final Color4f COLOR_PLANT = new Color4f(0.0f, 1.0f, 0.0f, 0.7f);
    private static final Color4f COLOR_UTILITY = new Color4f(1.0f, 1.0f, 0.0f, 0.7f);
    private static final Color4f COLOR_AUTO_MINE = new Color4f(1.0f, 0.55f, 0.0f, 0.7f);
    private static final Color4f COLOR_SCHEMATIC_SELECTION = new Color4f(0.0f, 1.0f, 1.0f, 0.7f);
    private static final Color4f COLOR_SCHEMATIC_EXTRA = new Color4f(1.0f, 0.0f, 0.8f, 0.7f);
    private static final Color4f COLOR_SCHEMATIC_WRONG = new Color4f(1.0f, 0.2f, 0.2f, 0.7f);

    private final SearchService searchService;
    private volatile OutlineData currentData;

    public SearchWorker(SearchService searchService) {
        this.searchService = searchService;
    }

    public OutlineData getCurrentData() {
        return this.currentData;
    }

    /**
     * Called from main thread to immediately clear rendered outlines.
     */
    public void clear() {
        this.searchService.nextPreviewGeneration();
        this.currentData = null;
    }

    /**
     * Called from main thread (ClientTick) to trigger a new search.
     */
    public void signal(SearchRequest request) {
        int generation = this.searchService.nextPreviewGeneration();
        this.searchService.submitPreview(
                request,
                generation,
                result -> toOutline(result, generation),
                data -> this.currentData = data);
    }

    private OutlineData toOutline(SearchResult result, int generation) {
        if (result.positions().isEmpty()) return null;
        Set<BlockPos> resultSet = new HashSet<>(result.positions());
        Color4f color = result.request().automatic()
                ? COLOR_AUTO_MINE
                : colorForMode(result.request().config().mode());
        return new OutlineData(List.copyOf(buildOutlineLines(resultSet, color)), generation);
    }

    private static Color4f colorForMode(ChainVeinConfig.ChainMode mode) {
        return switch (mode) {
            case CHAIN_MINE -> COLOR_MINE;
            case CHAIN_PLANT -> COLOR_PLANT;
            case CHAIN_UTILITY -> COLOR_UTILITY;
            case SCHEMATIC_SELECTION -> COLOR_SCHEMATIC_SELECTION;
            case SCHEMATIC_EXTRA -> COLOR_SCHEMATIC_EXTRA;
            case SCHEMATIC_WRONG -> COLOR_SCHEMATIC_WRONG;
        };
    }

    // ─── Outline geometry in world coordinates ───

    private List<LineSegment> buildOutlineLines(Set<BlockPos> result, Color4f color) {
        List<LineSegment> lines = new ArrayList<>();
        for (BlockPos pos : result) {
            double minX = pos.getX() - EXPAND;
            double minY = pos.getY() - EXPAND;
            double minZ = pos.getZ() - EXPAND;
            double maxX = pos.getX() + 1.0 + EXPAND;
            double maxY = pos.getY() + 1.0 + EXPAND;
            double maxZ = pos.getZ() + 1.0 + EXPAND;

            for (Direction dir : Direction.values()) {
                if (result.contains(pos.relative(dir))) continue; // face not exposed

                collectFaceEdges(lines, result, pos,
                    (float)minX, (float)minY, (float)minZ,
                    (float)maxX, (float)maxY, (float)maxZ,
                    dir, color);
            }
        }
        return lines;
    }

    private void collectFaceEdges(List<LineSegment> lines, Set<BlockPos> result, BlockPos pos,
                                   float minX, float minY, float minZ,
                                   float maxX, float maxY, float maxZ,
                                   Direction face, Color4f color) {
        switch (face) {
            case DOWN -> {
                if (!shouldMerge(result, pos, face, Direction.NORTH))  lines.add(line(minX, minY, minZ, maxX, minY, minZ, color));
                if (!shouldMerge(result, pos, face, Direction.EAST))   lines.add(line(maxX, minY, minZ, maxX, minY, maxZ, color));
                if (!shouldMerge(result, pos, face, Direction.SOUTH))  lines.add(line(maxX, minY, maxZ, minX, minY, maxZ, color));
                if (!shouldMerge(result, pos, face, Direction.WEST))   lines.add(line(minX, minY, maxZ, minX, minY, minZ, color));
            }
            case UP -> {
                if (!shouldMerge(result, pos, face, Direction.NORTH))  lines.add(line(minX, maxY, minZ, maxX, maxY, minZ, color));
                if (!shouldMerge(result, pos, face, Direction.EAST))   lines.add(line(maxX, maxY, minZ, maxX, maxY, maxZ, color));
                if (!shouldMerge(result, pos, face, Direction.SOUTH))  lines.add(line(maxX, maxY, maxZ, minX, maxY, maxZ, color));
                if (!shouldMerge(result, pos, face, Direction.WEST))   lines.add(line(minX, maxY, maxZ, minX, maxY, minZ, color));
            }
            case NORTH -> {
                if (!shouldMerge(result, pos, face, Direction.DOWN))   lines.add(line(minX, minY, minZ, maxX, minY, minZ, color));
                if (!shouldMerge(result, pos, face, Direction.EAST))   lines.add(line(maxX, minY, minZ, maxX, maxY, minZ, color));
                if (!shouldMerge(result, pos, face, Direction.UP))     lines.add(line(maxX, maxY, minZ, minX, maxY, minZ, color));
                if (!shouldMerge(result, pos, face, Direction.WEST))   lines.add(line(minX, maxY, minZ, minX, minY, minZ, color));
            }
            case SOUTH -> {
                if (!shouldMerge(result, pos, face, Direction.DOWN))   lines.add(line(minX, minY, maxZ, maxX, minY, maxZ, color));
                if (!shouldMerge(result, pos, face, Direction.EAST))   lines.add(line(maxX, minY, maxZ, maxX, maxY, maxZ, color));
                if (!shouldMerge(result, pos, face, Direction.UP))     lines.add(line(maxX, maxY, maxZ, minX, maxY, maxZ, color));
                if (!shouldMerge(result, pos, face, Direction.WEST))   lines.add(line(minX, maxY, maxZ, minX, minY, maxZ, color));
            }
            case WEST -> {
                if (!shouldMerge(result, pos, face, Direction.DOWN))   lines.add(line(minX, minY, minZ, minX, minY, maxZ, color));
                if (!shouldMerge(result, pos, face, Direction.SOUTH))  lines.add(line(minX, minY, maxZ, minX, maxY, maxZ, color));
                if (!shouldMerge(result, pos, face, Direction.UP))     lines.add(line(minX, maxY, maxZ, minX, maxY, minZ, color));
                if (!shouldMerge(result, pos, face, Direction.NORTH))  lines.add(line(minX, maxY, minZ, minX, minY, minZ, color));
            }
            case EAST -> {
                if (!shouldMerge(result, pos, face, Direction.DOWN))   lines.add(line(maxX, minY, minZ, maxX, minY, maxZ, color));
                if (!shouldMerge(result, pos, face, Direction.SOUTH))  lines.add(line(maxX, minY, maxZ, maxX, maxY, maxZ, color));
                if (!shouldMerge(result, pos, face, Direction.UP))     lines.add(line(maxX, maxY, maxZ, maxX, maxY, minZ, color));
                if (!shouldMerge(result, pos, face, Direction.NORTH))  lines.add(line(maxX, maxY, minZ, maxX, minY, minZ, color));
            }
        }
    }

    private boolean shouldMerge(Set<BlockPos> result, BlockPos pos,
                                 Direction faceDir, Direction mergeDir) {
        BlockPos neighbor = pos.relative(mergeDir);
        return result.contains(neighbor)
                && !result.contains(neighbor.relative(faceDir));
    }

    private static LineSegment line(float x1, float y1, float z1,
                                     float x2, float y2, float z2,
                                     Color4f color) {
        return new LineSegment(x1, y1, z1, x2, y2, z2, color);
    }
}
