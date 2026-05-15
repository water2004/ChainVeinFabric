package org.edtp.chainveinfabric.client.renderer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import org.edtp.chainveinfabric.client.ChainveinfabricClient;
import org.edtp.chainveinfabric.client.config.ChainVeinConfig;
import org.edtp.chainveinfabric.client.logic.ChainSearcher;

public class BlockOutlineRenderer implements IRenderer {

    private volatile Set<BlockPos> cachedResult = null;
    private BlockPos lastTargetPos = null;
    private long lastConfigHash = 0;
    private int tickCounter = 0;
    private static final int REFRESH_INTERVAL = 20;

    private static final float LINE_WIDTH = 2.0f;
    private static final double EXPAND = 0.001;
    private static final Color4f COLOR_MINE = new Color4f(0.0f, 1.0f, 1.0f, 0.7f);
    private static final Color4f COLOR_PLANT = new Color4f(0.0f, 1.0f, 0.0f, 0.7f);
    private static final Color4f COLOR_UTILITY = new Color4f(1.0f, 1.0f, 0.0f, 0.7f);

    @Override
    public Supplier<String> getProfilerSectionSupplier() {
        return () -> "chainveinfabric:block_outline_renderer";
    }

    @Override
    public void onExtractWorldLast(DeltaTracker deltaTracker, Camera camera, float ticks, ProfilerFiller profiler) {
        Minecraft client = Minecraft.getInstance();
        ChainVeinConfig config = ChainveinfabricClient.CONFIG;

        if (client.level == null || client.player == null || config == null
                || !config.isChainVeinEnabled || !config.showBlockOutlines) {
            cachedResult = null;
            lastTargetPos = null;
            return;
        }

        BlockPos targetPos = null;
        if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK) {
            targetPos = ((BlockHitResult) client.hitResult).getBlockPos();
        }

        if (targetPos == null) {
            cachedResult = null;
            lastTargetPos = null;
            return;
        }

        long configHash = computeConfigHash(config);
        tickCounter++;
        boolean configChanged = (configHash != lastConfigHash);
        boolean targetChanged = !targetPos.equals(lastTargetPos);
        boolean periodicRefresh = (tickCounter >= REFRESH_INTERVAL);

        if (!configChanged && !targetChanged && !periodicRefresh && cachedResult != null) {
            return;
        }

        tickCounter = 0;
        lastTargetPos = targetPos;
        lastConfigHash = configHash;

        Level world = client.level;
        BlockState targetState = world.getBlockState(targetPos);
        if (targetState.isAir()) {
            cachedResult = null;
            return;
        }

        Predicate<BlockPos> predicate = buildPredicate(client, targetPos, config, targetState);

        Direction face = Direction.UP;
        if (client.hitResult instanceof BlockHitResult hit) {
            face = hit.getDirection();
        }

        List<BlockPos> searchResult = ChainSearcher.search(client, targetPos, face, predicate);
        cachedResult = new HashSet<>(searchResult);
    }

    @Override
    public void onRenderWorldLast(RenderTarget fb, Matrix4fc modelViewMatrix, CameraRenderState cameraState,
                                   Frustum culling, RenderBuffers buffers, GpuBufferSlice terrainFog,
                                   Vector4f fogColor, ProfilerFiller profiler) {
        Set<BlockPos> result = this.cachedResult;
        if (result == null || result.isEmpty()) {
            return;
        }

        ChainVeinConfig config = ChainveinfabricClient.CONFIG;
        if (config == null) return;
        Color4f color = getColorForMode(config.mode);

        try (RenderContext ctx = new RenderContext(
                () -> "chainveinfabric:block_outlines",
                MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL)) {

            BufferBuilder buffer = ctx.getBuilder();
            net.minecraft.world.phys.Vec3 camPos = RenderUtils.camPos();

            for (BlockPos pos : result) {
                float minX = (float) (pos.getX() - camPos.x - EXPAND);
                float minY = (float) (pos.getY() - camPos.y - EXPAND);
                float minZ = (float) (pos.getZ() - camPos.z - EXPAND);
                float maxX = (float) (pos.getX() - camPos.x + 1.0 + EXPAND);
                float maxY = (float) (pos.getY() - camPos.y + 1.0 + EXPAND);
                float maxZ = (float) (pos.getZ() - camPos.z + 1.0 + EXPAND);

                for (Direction dir : Direction.values()) {
                    if (!result.contains(pos.relative(dir))) {
                        addFaceEdges(buffer, result, pos,
                                minX, minY, minZ, maxX, maxY, maxZ,
                                dir, color);
                    }
                }
            }

            MeshData meshData = buffer.build();
            if (meshData != null) {
                ctx.draw(meshData, false, true);
                meshData.close();
            }
        } catch (Exception e) {
            // Silently fail to avoid render spam
        }
    }

    private Predicate<BlockPos> buildPredicate(Minecraft client, BlockPos targetPos,
                                                ChainVeinConfig config, BlockState targetState) {
        Level world = client.level;
        ChainVeinConfig.SearchAlgorithm algo = config.searchAlgorithm;

        return switch (config.mode) {
            case CHAIN_MINE -> {
                yield p -> {
                    BlockState s = world.getBlockState(p);
                    String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
                    if (!config.whitelistedBlocks.contains(id)) return false;
                    if (algo == ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME) {
                        return s.is(targetState.getBlock());
                    }
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
                    if (!config.whitelistedUtilityBlocks.contains(id)) return false;
                    if (algo == ChainVeinConfig.SearchAlgorithm.ADJACENT_SAME) {
                        return s.is(targetState.getBlock());
                    }
                    return true;
                };
            }
        };
    }

    private long computeConfigHash(ChainVeinConfig config) {
        long hash = config.mode.ordinal();
        hash = 31 * hash + config.searchAlgorithm.ordinal();
        hash = 31 * hash + config.maxChainBlocks;
        hash = 31 * hash + config.maxRadius;
        hash = 31 * hash + config.sphereRadius;
        hash = 31 * hash + config.squareLength;
        hash = 31 * hash + config.squareMiningPoint.ordinal();
        hash = 31 * hash + config.cuboidL;
        hash = 31 * hash + config.cuboidW;
        hash = 31 * hash + config.cuboidH;
        hash = 31 * hash + config.cuboidMiningPoint.ordinal();
        hash = 31 * hash + (config.diagonalEdge ? 1 : 0);
        hash = 31 * hash + (config.diagonalCorner ? 1 : 0);
        hash = 31 * hash + config.whitelistedBlocks.hashCode();
        hash = 31 * hash + config.whitelistedCrops.hashCode();
        hash = 31 * hash + config.whitelistedUtilityBlocks.hashCode();
        return hash;
    }

    private Color4f getColorForMode(ChainVeinConfig.ChainMode mode) {
        return switch (mode) {
            case CHAIN_MINE -> COLOR_MINE;
            case CHAIN_PLANT -> COLOR_PLANT;
            case CHAIN_UTILITY -> COLOR_UTILITY;
        };
    }

    private void addFaceEdges(BufferBuilder buffer, Set<BlockPos> result, BlockPos pos,
                               float minX, float minY, float minZ,
                               float maxX, float maxY, float maxZ,
                               Direction face, Color4f color) {
        // Each edge's "merge check" direction: the neighbor across that edge
        // If that neighbor also has an exposed face in the same direction,
        // skip the edge (it's an internal grid line of a merged surface).
        switch (face) {
            case DOWN -> {
                if (!shouldMerge(result, pos, face, Direction.NORTH))  line(buffer, minX, minY, minZ, maxX, minY, minZ, color);
                if (!shouldMerge(result, pos, face, Direction.EAST))   line(buffer, maxX, minY, minZ, maxX, minY, maxZ, color);
                if (!shouldMerge(result, pos, face, Direction.SOUTH))  line(buffer, maxX, minY, maxZ, minX, minY, maxZ, color);
                if (!shouldMerge(result, pos, face, Direction.WEST))   line(buffer, minX, minY, maxZ, minX, minY, minZ, color);
            }
            case UP -> {
                if (!shouldMerge(result, pos, face, Direction.NORTH))  line(buffer, minX, maxY, minZ, maxX, maxY, minZ, color);
                if (!shouldMerge(result, pos, face, Direction.EAST))   line(buffer, maxX, maxY, minZ, maxX, maxY, maxZ, color);
                if (!shouldMerge(result, pos, face, Direction.SOUTH))  line(buffer, maxX, maxY, maxZ, minX, maxY, maxZ, color);
                if (!shouldMerge(result, pos, face, Direction.WEST))   line(buffer, minX, maxY, maxZ, minX, maxY, minZ, color);
            }
            case NORTH -> {
                if (!shouldMerge(result, pos, face, Direction.DOWN))   line(buffer, minX, minY, minZ, maxX, minY, minZ, color);
                if (!shouldMerge(result, pos, face, Direction.EAST))   line(buffer, maxX, minY, minZ, maxX, maxY, minZ, color);
                if (!shouldMerge(result, pos, face, Direction.UP))     line(buffer, maxX, maxY, minZ, minX, maxY, minZ, color);
                if (!shouldMerge(result, pos, face, Direction.WEST))   line(buffer, minX, maxY, minZ, minX, minY, minZ, color);
            }
            case SOUTH -> {
                if (!shouldMerge(result, pos, face, Direction.DOWN))   line(buffer, minX, minY, maxZ, maxX, minY, maxZ, color);
                if (!shouldMerge(result, pos, face, Direction.EAST))   line(buffer, maxX, minY, maxZ, maxX, maxY, maxZ, color);
                if (!shouldMerge(result, pos, face, Direction.UP))     line(buffer, maxX, maxY, maxZ, minX, maxY, maxZ, color);
                if (!shouldMerge(result, pos, face, Direction.WEST))   line(buffer, minX, maxY, maxZ, minX, minY, maxZ, color);
            }
            case WEST -> {
                if (!shouldMerge(result, pos, face, Direction.DOWN))   line(buffer, minX, minY, minZ, minX, minY, maxZ, color);
                if (!shouldMerge(result, pos, face, Direction.SOUTH))  line(buffer, minX, minY, maxZ, minX, maxY, maxZ, color);
                if (!shouldMerge(result, pos, face, Direction.UP))     line(buffer, minX, maxY, maxZ, minX, maxY, minZ, color);
                if (!shouldMerge(result, pos, face, Direction.NORTH))  line(buffer, minX, maxY, minZ, minX, minY, minZ, color);
            }
            case EAST -> {
                if (!shouldMerge(result, pos, face, Direction.DOWN))   line(buffer, maxX, minY, minZ, maxX, minY, maxZ, color);
                if (!shouldMerge(result, pos, face, Direction.SOUTH))  line(buffer, maxX, minY, maxZ, maxX, maxY, maxZ, color);
                if (!shouldMerge(result, pos, face, Direction.UP))     line(buffer, maxX, maxY, maxZ, maxX, maxY, minZ, color);
                if (!shouldMerge(result, pos, face, Direction.NORTH))  line(buffer, maxX, maxY, minZ, maxX, minY, minZ, color);
            }
        }
    }

    /**
     * Checks whether an edge of an exposed face should be hidden (merged).
     * The edge is internal to the outer surface if the adjacent block across
     * the edge also has an exposed face in the same direction.
     */
    private boolean shouldMerge(Set<BlockPos> result, BlockPos pos,
                                 Direction faceDir, Direction mergeDir) {
        BlockPos neighbor = pos.relative(mergeDir);
        return result.contains(neighbor)
                && !result.contains(neighbor.relative(faceDir));
    }

    private void line(BufferBuilder buffer,
                       float x1, float y1, float z1,
                       float x2, float y2, float z2,
                       Color4f color) {
        buffer.addVertex(x1, y1, z1).setColor(color.r, color.g, color.b, color.a).setLineWidth(LINE_WIDTH);
        buffer.addVertex(x2, y2, z2).setColor(color.r, color.g, color.b, color.a).setLineWidth(LINE_WIDTH);
    }
}
