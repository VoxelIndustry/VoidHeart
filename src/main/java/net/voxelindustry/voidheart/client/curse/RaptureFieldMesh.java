package net.voxelindustry.voidheart.client.curse;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.curse.sky.CurseSkyUniform;
import net.voxelindustry.voidheart.client.curse.sky.StarField;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The vanilla stars again, plus what a {@link RaptureFieldLayout} made of each. Belongs to one curse, built once as it starts and released when it ends.
 */
public final class RaptureFieldMesh implements AutoCloseable
{
    /**
     * The attributes of {@link StarField#VERTEX_FORMAT}, then the half width of the eye, zero if it never opens, and what it looks like.
     * Corner holds the tilt and the lean of the lid after the corner itself.
     */
    public static final VertexFormat VERTEX_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Center", GpuFormat.RGB32_FLOAT)
            .addAttribute("Corner", GpuFormat.RGBA8_SNORM)
            .addAttribute("Seed", GpuFormat.RGBA8_UNORM)
            .addAttribute("Width", GpuFormat.R32_FLOAT)
            .addAttribute("Shape", GpuFormat.RGBA8_UNORM)
            .addAttribute("Sclera", GpuFormat.RGBA8_UNORM)
            .addAttribute("Iris", GpuFormat.RGBA8_UNORM)
            .addAttribute("Lid", GpuFormat.RGBA8_UNORM)
            .build();

    private static final int VERTEX_BYTES = StarField.VERTEX_BYTES + Float.BYTES + 4 + 4 + 4 + 4;
    // Where the two spare bytes of Corner sit in a vertex of the star field
    private static final int CORNER_SPARE_OFFSET = 3 * Float.BYTES + 3 * Float.BYTES + 2;

    // The same as TILT_RANGE in the field vertex shader: tilts up to this many radians either way fit in a byte
    private static final float TILT_RANGE = 2.4F;
    private static final float LEAN_RANGE = 1;

    private final GpuBuffer vertices;
    private final int       indexCount;

    private RaptureFieldMesh(GpuBuffer vertices, int indexCount)
    {
        this.vertices = vertices;
        this.indexCount = indexCount;
    }

    /**
     * Must be called with no render pass open.
     */
    public static RaptureFieldMesh of(RaptureFieldLayout layout, ByteBuffer stars)
    {
        var data = MemoryUtil.memAlloc(layout.stars() * StarField.STAR_VERTICES * VERTEX_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        try
        {
            write(layout, stars, data);
            data.flip();
            var buffer = RenderSystem.getDevice().createBuffer(() -> "Curse field", GpuBuffer.USAGE_VERTEX, data);
            return new RaptureFieldMesh(buffer, layout.stars() * 6);
        } finally
        {
            MemoryUtil.memFree(data);
        }
    }

    /**
     * Stars that never open go first, for the eyes to cover them should they not have faded in time. No two eyes overlap, so their own order means nothing.
     */
    private static void write(RaptureFieldLayout layout, ByteBuffer stars, ByteBuffer data)
    {
        for (int star = 0; star < layout.stars(); star++)
        {
            if (layout.width(star) <= 0)
                writeStar(stars, star, 0, 0, 0, 0, 0, 0, 0, data);
        }
        for (var eye : layout.eyes())
        {
            int shape = packed(eye.family().ordinal() / 255F, eye.openness(), eye.pupilSize(), eye.irisSize());
            writeStar(stars, eye.star(), eye.tilt() / TILT_RANGE, eye.lean() / LEAN_RANGE, eye.width(), shape,
                    withAlpha(eye.sclera(), eye.veins()), withAlpha(eye.iris(), eye.pupilStrength()), withAlpha(eye.lid(), eye.mirrored() ? 1 : 0), data);
        }
    }

    private static void writeStar(ByteBuffer stars, int star, float tilt, float lean, float width, int shape, int sclera, int iris, int lid, ByteBuffer data)
    {
        int from = stars.position() + star * StarField.STAR_BYTES;
        for (int corner = 0; corner < StarField.STAR_VERTICES; corner++)
        {
            int vertex = from + corner * StarField.VERTEX_BYTES;
            int at = data.position();
            data.put(data.position(), stars, vertex, StarField.VERTEX_BYTES);
            data.put(at + CORNER_SPARE_OFFSET, (byte) (Mth.clamp(tilt, -1, 1) * 127));
            data.put(at + CORNER_SPARE_OFFSET + 1, (byte) (Mth.clamp(lean, -1, 1) * 127));
            data.position(at + StarField.VERTEX_BYTES);
            data.putFloat(width);
            data.putInt(shape);
            data.putInt(sclera);
            data.putInt(iris);
            data.putInt(lid);
        }
    }

    private static int withAlpha(int rgb, float alpha)
    {
        return packed((rgb >> 16 & 0xFF) / 255F, (rgb >> 8 & 0xFF) / 255F, (rgb & 0xFF) / 255F, alpha);
    }

    /**
     * Little endian, so that the first value comes first in memory.
     */
    private static int packed(float first, float second, float third, float fourth)
    {
        return Math.round(fourth * 255) << 24 | Math.round(third * 255) << 16 | Math.round(second * 255) << 8 | Math.round(first * 255);
    }

    /**
     * The pipeline must use {@link #VERTEX_FORMAT} and {@link CurseSkyUniform#BINDINGS}.
     */
    public void draw(RenderPass renderPass, RenderPipeline pipeline, GpuBufferSlice uniform)
    {
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
        RenderSystem.bindDefaultUniforms(renderPass);
        renderPass.setUniform(CurseSkyUniform.NAME, uniform);
        renderPass.setVertexBuffer(0, vertices.slice());
        renderPass.setIndexBuffer(indices.getBuffer(indexCount), indices.type());
        renderPass.drawIndexed(indexCount, 1, 0, 0, 0);
    }

    public boolean isClosed()
    {
        return vertices.isClosed();
    }

    @Override
    public void close()
    {
        vertices.close();
    }
}
