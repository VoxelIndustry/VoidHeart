package net.voxelindustry.voidheart.client.render.oculus;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteOrder;

/**
 * Hologram quads of one chunk column within a projection window, uploaded once and kept on the GPU.
 */
final class OculusChunkMesh implements AutoCloseable
{
    final int chunkX;
    final int chunkZ;
    final int windowBottom;
    final int quadCount;

    private @Nullable GpuBuffer vertices;

    private OculusChunkMesh(int chunkX, int chunkZ, int windowBottom, int quadCount, @Nullable GpuBuffer vertices)
    {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.windowBottom = windowBottom;
        this.quadCount = quadCount;
        this.vertices = vertices;
    }

    static OculusChunkMesh upload(int chunkX, int chunkZ, int windowBottom, int[] mesh)
    {
        int quadCount = mesh.length / OculusMesher.INTS_PER_QUAD;
        if (quadCount == 0)
            return new OculusChunkMesh(chunkX, chunkZ, windowBottom, 0, null);

        var data = MemoryUtil.memAlloc(mesh.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        try
        {
            data.asIntBuffer().put(mesh);
            var buffer = RenderSystem.getDevice().createBuffer(() -> "Oculus hologram " + chunkX + ", " + chunkZ, GpuBuffer.USAGE_VERTEX, data);
            return new OculusChunkMesh(chunkX, chunkZ, windowBottom, quadCount, buffer);
        } finally
        {
            MemoryUtil.memFree(data);
        }
    }

    @Nullable GpuBuffer vertices()
    {
        return vertices;
    }

    long gpuBytes()
    {
        return (long) quadCount * OculusMesher.INTS_PER_QUAD * Integer.BYTES;
    }

    @Override
    public void close()
    {
        if (vertices != null)
        {
            vertices.close();
            vertices = null;
        }
    }
}
