package org.edtp.chainveinfabric.client.renderer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Predicate;

import fi.dy.masa.malilib.util.data.Color4f;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.logic.ChainSearcher;

public class SearchWorker implements Runnable {

    private static final float LINE_WIDTH = 2.0f;
    private static final double EXPAND = 0.001;
    private static final Color4f COLOR_MINE = new Color4f(0.0f, 1.0f, 1.0f, 0.7f);
    private static final Color4f COLOR_PLANT = new Color4f(0.0f, 1.0f, 0.0f, 0.7f);
    private static final Color4f COLOR_UTILITY = new Color4f(1.0f, 1.0f, 0.0f, 0.7f);

    private final Thread thread;
    private volatile boolean running = true;
    private volatile OutlineData currentData;

    // Signalled by main thread, consumed by worker
    private final AtomicInteger generation = new AtomicInteger(0);
    private volatile ConfigSnapshot configSnapshot;
    private volatile BlockPos targetPos;
    private volatile BlockState targetState;
    private volatile Direction hitFace;
    private volatile Direction playerFacing;
    private volatile ClientLevel world;

    public SearchWorker() {
        this.thread = new Thread(this, "ChainVeinFabric-OutlineWorker");
        this.thread.setDaemon(true);
    }

    public void start() {
        this.thread.start();
    }

    public OutlineData getCurrentData() {
        return this.currentData;
    }

    /**
     * Called from main thread to immediately clear rendered outlines.
     */
    public void clear() {
        this.currentData = null;
    }

    /**
     * Called from main thread (ClientTick) to trigger a new search.
     */
    public void signal(int gen, ConfigSnapshot config, BlockPos target,
                        BlockState state, Direction face, Direction pFacing,
                        ClientLevel level) {
        this.configSnapshot = config;
        this.targetPos = target;
        this.targetState = state;
        this.hitFace = face;
        this.playerFacing = pFacing;
        this.world = level;
        this.generation.set(gen);
        LockSupport.unpark(this.thread);
    }

    @Override
    public void run() {
        int lastProcessedGen = -1;

        while (running) {
            LockSupport.park();

            if (!running) break;

            int currentGen = generation.get();
            if (currentGen == lastProcessedGen) continue; // spurious wakeup

            lastProcessedGen = currentGen;

            ConfigSnapshot snap = this.configSnapshot;
            BlockPos pos = this.targetPos;
            BlockState state = this.targetState;
            Direction face = this.hitFace;
            Direction pFacing = this.playerFacing;
            ClientLevel level = this.world;

            if (snap == null || pos == null || state == null || level == null) continue;

            try {
                Predicate<BlockPos> predicate = buildPredicate(level, pos, snap, state);
                List<BlockPos> searchResult = doSearch(level, pos, pFacing, snap, predicate);

                if (searchResult.isEmpty()) {
                    this.currentData = null;
                    continue;
                }

                Set<BlockPos> resultSet = new HashSet<>(searchResult);
                Color4f color = colorForMode(snap.mode());
                List<LineSegment> lines = buildOutlineLines(resultSet, color);

                this.currentData = new OutlineData(List.copyOf(lines), currentGen);
            } catch (Exception e) {
                // World read inconsistency or chunk unload — discard this result
                this.currentData = null;
            }
        }
    }

    // ─── Search dispatch (uses snapshot + Direction, no Minecraft/Player access) ───

    private List<BlockPos> doSearch(ClientLevel level, BlockPos pos, Direction playerFacing,
                                     ConfigSnapshot snap, Predicate<BlockPos> predicate) {
        Set<BlockPos> result = switch (snap.searchAlgorithm()) {
            case SPHERE -> ChainSearcher.findSphere(level, pos, snap.sphereRadius(), predicate);
            case SQUARE -> ChainSearcher.findSquare(level, pos, snap.squareLength(),
                    snap.squareMiningPoint(), playerFacing, predicate);
            case CUBOID -> ChainSearcher.findCuboid(level, pos, snap.cuboidL(), snap.cuboidW(),
                    snap.cuboidH(), snap.cuboidMiningPoint(), playerFacing, predicate);
            default -> ChainSearcher.findBlocks(level, pos, snap.maxChainBlocks(), snap.maxRadius(),
                    predicate, snap.diagonalEdge(), snap.diagonalCorner());
        };

        List<BlockPos> sorted = new ArrayList<>(result);
        sorted.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(pos)));
        return sorted;
    }

    // ─── Predicate building (same as MineLogic/InteractLogic) ───

    private Predicate<BlockPos> buildPredicate(ClientLevel world, BlockPos targetPos,
                                                ConfigSnapshot snap, BlockState targetState) {
        return switch (snap.mode()) {
            case CHAIN_MINE -> {
                yield p -> {
                    BlockState s = world.getBlockState(p);
                    String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
                    if (!snap.whitelistedBlocks().contains(id)) return false;
                    if (snap.searchAlgorithm() == ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME)
                        return s.is(targetState.getBlock());
                    return true;
                };
            }
            case CHAIN_PLANT -> {
                Block targetSoil = targetState.getBlock();
                yield p -> world.getBlockState(p).is(targetSoil)
                        && world.getBlockState(p.above()).isAir();
            }
            case CHAIN_UTILITY -> {
                yield p -> {
                    BlockState s = world.getBlockState(p);
                    String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
                    if (!snap.whitelistedUtilityBlocks().contains(id)) return false;
                    if (snap.searchAlgorithm() == ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME)
                        return s.is(targetState.getBlock());
                    return true;
                };
            }
        };
    }

    private static Color4f colorForMode(ChainVeinConfig.ChainMode mode) {
        return switch (mode) {
            case CHAIN_MINE -> COLOR_MINE;
            case CHAIN_PLANT -> COLOR_PLANT;
            case CHAIN_UTILITY -> COLOR_UTILITY;
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
