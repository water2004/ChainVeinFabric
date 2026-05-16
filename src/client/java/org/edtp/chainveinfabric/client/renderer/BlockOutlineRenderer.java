package org.edtp.chainveinfabric.client.renderer;

import java.util.function.Supplier;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.render.RenderUtils;

public class BlockOutlineRenderer implements IRenderer {

    private static final float LINE_WIDTH = 2.0f;
    private final SearchWorker worker;

    public BlockOutlineRenderer(SearchWorker worker) {
        this.worker = worker;
    }

    @Override
    public Supplier<String> getProfilerSectionSupplier() {
        return () -> "chainveinfabric:block_outline_renderer";
    }

    @Override
    public void onExtractWorldLast(DeltaTracker deltaTracker, Camera camera, float ticks, ProfilerFiller profiler) {
        // Search happens on the worker thread — nothing to do here
    }

    @Override
    public void onRenderWorldLast(RenderTarget fb, Matrix4fc modelViewMatrix, CameraRenderState cameraState,
                                   Frustum culling, RenderBuffers buffers, GpuBufferSlice terrainFog,
                                   Vector4f fogColor, ProfilerFiller profiler) {
        OutlineData data = worker.getCurrentData();
        if (data == null || data.lines().isEmpty()) return;

        try (RenderContext ctx = new RenderContext(
                () -> "chainveinfabric:block_outlines",
                MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL)) {

            BufferBuilder buffer = ctx.getBuilder();
            Vec3 camPos = RenderUtils.camPos();
            float cx = (float) camPos.x;
            float cy = (float) camPos.y;
            float cz = (float) camPos.z;

            for (LineSegment seg : data.lines()) {
                buffer.addVertex(seg.x1() - cx, seg.y1() - cy, seg.z1() - cz)
                      .setColor(seg.color().r, seg.color().g, seg.color().b, seg.color().a)
                      .setLineWidth(LINE_WIDTH);
                buffer.addVertex(seg.x2() - cx, seg.y2() - cy, seg.z2() - cz)
                      .setColor(seg.color().r, seg.color().g, seg.color().b, seg.color().a)
                      .setLineWidth(LINE_WIDTH);
            }

            MeshData meshData = buffer.build();
            if (meshData != null) {
                ctx.draw(meshData, false, true);
                meshData.close();
            }
        } catch (Exception e) {
            // Silently fail
        }
    }
}
