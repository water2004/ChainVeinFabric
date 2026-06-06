package org.edtp.chainveinfabric.client.renderer;

import java.util.function.Supplier;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

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
    public void onRenderWorldLastAdvanced(RenderTarget fb, Matrix4f posMatrix, Matrix4f projMatrix,
                                          Frustum frustum, Camera camera, RenderBuffers buffers,
                                          ProfilerFiller profiler) {
        OutlineData data = this.worker.getCurrentData();
        if (data == null || data.lines().isEmpty()) return;

        try (RenderContext ctx = new RenderContext(
                () -> "chainveinfabric:block_outlines",
                MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL)) {

            ctx.lineWidth(LINE_WIDTH);
            BufferBuilder buffer = ctx.getBuilder();
            Vec3 camPos = RenderUtils.camPos();
            float cx = (float) camPos.x;
            float cy = (float) camPos.y;
            float cz = (float) camPos.z;

            for (LineSegment segment : data.lines()) {
                buffer.addVertex(segment.x1() - cx, segment.y1() - cy, segment.z1() - cz)
                    .setColor(segment.color().r, segment.color().g, segment.color().b, segment.color().a);
                buffer.addVertex(segment.x2() - cx, segment.y2() - cy, segment.z2() - cz)
                    .setColor(segment.color().r, segment.color().g, segment.color().b, segment.color().a);
            }

            MeshData meshData = buffer.build();
            if (meshData != null) {
                ctx.draw(meshData, false, true);
                meshData.close();
            }
        } catch (Exception ignored) {
        }
    }
}
