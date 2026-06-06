package org.edtp.chainveinfabric.client.renderer;

import java.util.function.Supplier;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import fi.dy.masa.malilib.interfaces.IRenderer;
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
    public void onRenderWorldLastAdvanced(Matrix4f posMatrix, Matrix4f projMatrix, Frustum frustum,
                                          Camera camera, FogParameters fog, ProfilerFiller profiler) {
        OutlineData data = this.worker.getCurrentData();
        if (data == null || data.lines().isEmpty()) return;

        RenderSystem.lineWidth(LINE_WIDTH);
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        RenderUtils.setupBlend();

        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        try {
            Vec3 camPos = camera.getPosition();
            float cx = (float) camPos.x;
            float cy = (float) camPos.y;
            float cz = (float) camPos.z;

            for (LineSegment segment : data.lines()) {
                buffer.addVertex(segment.x1() - cx, segment.y1() - cy, segment.z1() - cz)
                    .setColor(segment.color().r, segment.color().g, segment.color().b, segment.color().a);
                buffer.addVertex(segment.x2() - cx, segment.y2() - cy, segment.z2() - cz)
                    .setColor(segment.color().r, segment.color().g, segment.color().b, segment.color().a);
            }

            MeshData meshData = buffer.buildOrThrow();
            BufferUploader.drawWithShader(meshData);
            meshData.close();
        } catch (Exception ignored) {
        } finally {
            RenderSystem.disableBlend();
        }
    }
}
