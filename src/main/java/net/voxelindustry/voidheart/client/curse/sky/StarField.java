package net.voxelindustry.voidheart.client.curse.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The very same stars as vanilla, down to the shape of each, so that a curse can take over the ones the player was already looking at.
 * Each vertex also tells which star it belongs to, leaving the shaders free to move, recolor or reshape each star on its own.
 */
public final class StarField
{
    // From SkyRenderer.buildStars, which must keep drawing the same random numbers in the same order
    private static final long  VANILLA_SEED     = 10842L;
    private static final int   VANILLA_ATTEMPTS = 1500;
    private static final float DISTANCE         = 100;

    /**
     * The corner of the vanilla star, the center of that star, the corner as (-1, -1) to (1, 1), then four random values for that star.
     */
    public static final VertexFormat VERTEX_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Center", GpuFormat.RGB32_FLOAT)
            .addAttribute("Corner", GpuFormat.RGBA8_SNORM)
            .addAttribute("Seed", GpuFormat.RGBA8_UNORM)
            .build();

    public static final int VERTEX_BYTES = 3 * Float.BYTES + 3 * Float.BYTES + 4 + 4;
    public static final int CENTER_OFFSET = 3 * Float.BYTES;

    private static final int[][] CORNERS = {{1, -1}, {1, 1}, {-1, 1}, {-1, -1}};

    public static final int STAR_VERTICES = CORNERS.length;
    public static final int STAR_BYTES    = STAR_VERTICES * VERTEX_BYTES;

    private static @Nullable GpuBuffer vertices;
    private static int indexCount;

    private StarField()
    {
    }

    /**
     * Must be called with no render pass open, before the first {@link #draw}.
     */
    public static void prepare()
    {
        if (vertices != null)
            return;

        var data = MemoryUtil.memAlloc(VANILLA_ATTEMPTS * CORNERS.length * VERTEX_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        try
        {
            int stars = writeStars(data);
            data.flip();
            indexCount = stars * 6;
            vertices = RenderSystem.getDevice().createBuffer(() -> "Curse star field", GpuBuffer.USAGE_VERTEX, data);
        } finally
        {
            MemoryUtil.memFree(data);
        }
    }

    /**
     * The same data as the shared mesh holds, on the heap, for a curse that needs its own mesh of these stars. Touches nothing on the GPU.
     */
    public static ByteBuffer vanillaStars()
    {
        var data = ByteBuffer.allocate(VANILLA_ATTEMPTS * STAR_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        writeStars(data);
        data.flip();
        return data;
    }

    private static int writeStars(ByteBuffer data)
    {
        var vanilla = RandomSource.createThreadLocalInstance(VANILLA_SEED);
        var seeds = RandomSource.create(VANILLA_SEED);

        int stars = 0;
        for (int attempt = 0; attempt < VANILLA_ATTEMPTS; attempt++)
        {
            float x = vanilla.nextFloat() * 2 - 1;
            float y = vanilla.nextFloat() * 2 - 1;
            float z = vanilla.nextFloat() * 2 - 1;
            float size = 0.15F + vanilla.nextFloat() * 0.1F;
            float lengthSq = Mth.lengthSquared(x, y, z);
            if (lengthSq <= 0.010000001F || lengthSq >= 1)
                continue;

            var center = new Vector3f(x, y, z).normalize(DISTANCE);
            float zRot = (float) (vanilla.nextDouble() * Math.PI * 2);
            var rotation = new Matrix3f().rotateTowards(new Vector3f(center).negate(), new Vector3f(0, 1, 0)).rotateZ(-zRot);
            int seed = seeds.nextInt();

            for (var corner : CORNERS)
            {
                var position = new Vector3f(corner[0] * size, corner[1] * size, 0).mul(rotation).add(center);
                data.putFloat(position.x).putFloat(position.y).putFloat(position.z);
                data.putFloat(center.x).putFloat(center.y).putFloat(center.z);
                data.put((byte) (corner[0] * 127)).put((byte) (corner[1] * 127)).put((byte) 0).put((byte) 0);
                data.putInt(seed);
            }
            stars++;
        }
        return stars;
    }

    /**
     * Draws every star with the given pipeline, which must use {@link #VERTEX_FORMAT} and {@link CurseSkyUniform#BINDINGS}.
     */
    public static void draw(RenderPass renderPass, RenderPipeline pipeline, GpuBufferSlice uniform)
    {
        if (vertices == null)
            return;

        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
        RenderSystem.bindDefaultUniforms(renderPass);
        renderPass.setUniform(CurseSkyUniform.NAME, uniform);
        renderPass.setVertexBuffer(0, vertices.slice());
        renderPass.setIndexBuffer(indices.getBuffer(indexCount), indices.type());
        renderPass.drawIndexed(indexCount, 1, 0, 0, 0);
    }
}
