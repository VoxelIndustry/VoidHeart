package net.voxelindustry.voidheart.client.curse;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.curse.sky.CurseSkyUniform;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A small static dome over the upper half of the still sky, only there for its fragment shader to light up the sky behind it.
 */
final class OmenDome
{
    private static final int   RINGS    = 6;
    private static final int   SEGMENTS = 16;
    private static final float RADIUS   = 100;

    private static final int VERTEX_COUNT = RINGS * SEGMENTS * 4;
    private static final int INDEX_COUNT  = RINGS * SEGMENTS * 6;

    private static @Nullable GpuBuffer vertices;

    private OmenDome()
    {
    }

    /**
     * Must be called with no render pass open, before the first {@link #draw}.
     */
    static void prepare()
    {
        if (vertices != null)
            return;

        var data = MemoryUtil.memAlloc(VERTEX_COUNT * 3 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        try
        {
            for (int ring = 0; ring < RINGS; ring++)
            {
                for (int segment = 0; segment < SEGMENTS; segment++)
                {
                    putPoint(data, ring, segment);
                    putPoint(data, ring, segment + 1);
                    putPoint(data, ring + 1, segment + 1);
                    putPoint(data, ring + 1, segment);
                }
            }
            data.flip();
            vertices = RenderSystem.getDevice().createBuffer(() -> "Curse omen dome", GpuBuffer.USAGE_VERTEX, data);
        } finally
        {
            MemoryUtil.memFree(data);
        }
    }

    private static void putPoint(ByteBuffer data, int ring, int segment)
    {
        float altitude = ring * Mth.HALF_PI / RINGS;
        float azimuth = segment * Mth.TWO_PI / SEGMENTS;
        float across = Mth.cos(altitude) * RADIUS;
        data.putFloat(Mth.cos(azimuth) * across).putFloat(Mth.sin(altitude) * RADIUS).putFloat(Mth.sin(azimuth) * across);
    }

    static void draw(RenderPass renderPass, RenderPipeline pipeline, GpuBufferSlice uniform)
    {
        if (vertices == null)
            return;

        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
        RenderSystem.bindDefaultUniforms(renderPass);
        renderPass.setUniform(CurseSkyUniform.NAME, uniform);
        renderPass.setVertexBuffer(0, vertices.slice());
        renderPass.setIndexBuffer(indices.getBuffer(INDEX_COUNT), indices.type());
        renderPass.drawIndexed(INDEX_COUNT, 1, 0, 0, 0);
    }
}
