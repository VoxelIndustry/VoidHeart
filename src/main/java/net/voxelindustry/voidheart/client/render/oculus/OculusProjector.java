package net.voxelindustry.voidheart.client.render.oculus;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.oculus.OculusFootprint;
import net.voxelindustry.voidheart.common.content.oculus.OculusView;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * Hologram of one table: the window of land it shows and the chunk meshes covering it.
 */
final class OculusProjector implements AutoCloseable
{
    /**
     * Keeps the bottom of the hologram from fighting with the table top.
     */
    private static final float LIFT = 1 / 16F;
    /**
     * Share of the window kept below the surface at the view center.
     */
    private static final float SURFACE_HEIGHT = 0.6F;
    private static final int BUILDS_PER_TICK = 8;

    final BlockPos anchor;

    private OculusFootprint footprint;
    private int scale;
    private double centerX;
    private double centerZ;
    private int windowBottom = Integer.MIN_VALUE;

    private final Long2ObjectMap<OculusChunkMesh> meshes = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<int[]> pendingUploads = new Long2ObjectOpenHashMap<>();

    private @Nullable DynamicTexture mask;
    private boolean maskDirty = true;

    OculusProjector(BlockPos anchor, OculusFootprint footprint)
    {
        this.anchor = anchor;
        this.footprint = footprint;
    }

    void update(ClientLevel level, OculusFootprint footprint, OculusView view)
    {
        if (!footprint.equals(this.footprint))
        {
            this.footprint = footprint;
            maskDirty = true;
        }

        centerX = footprint.centerX();
        centerZ = footprint.centerZ();

        int bottom = windowBottom(level, view.scale());
        if (view.scale() != scale || bottom != windowBottom)
        {
            scale = view.scale();
            windowBottom = bottom;
            discardMeshes();
        }

        var wanted = chunksShown();
        meshes.long2ObjectEntrySet().removeIf(entry ->
        {
            if (wanted.contains(entry.getLongKey()))
                return false;
            entry.getValue().close();
            return true;
        });
        pendingUploads.keySet().removeIf(key -> !wanted.contains(key));

        int built = 0;
        for (long key : wanted)
        {
            if (built >= BUILDS_PER_TICK)
                break;
            if (meshes.containsKey(key) || pendingUploads.containsKey(key))
                continue;

            var volume = OculusVoxels.snapshot(level, ChunkPos.getX(key), ChunkPos.getZ(key), windowBottom, scale);
            pendingUploads.put(key, OculusMesher.mesh(volume));
            built++;
        }
    }

    private int windowBottom(ClientLevel level, int height)
    {
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(centerX), Mth.floor(centerZ));
        return Mth.clamp(surface - (int) (height * SURFACE_HEIGHT), level.getMinY(), level.getMaxY() + 1 - height);
    }

    /**
     * Chunks under at least one table block of the footprint.
     */
    private LongSet chunksShown()
    {
        var chunks = new LongOpenHashSet();
        for (int cellZ = 0; cellZ < footprint.depth(); cellZ++)
        {
            for (int cellX = 0; cellX < footprint.width(); cellX++)
            {
                if (!footprint.contains(footprint.minX() + cellX, footprint.minZ() + cellZ))
                    continue;

                double minX = centerX + (cellX - footprint.width() / 2D) * scale;
                double minZ = centerZ + (cellZ - footprint.depth() / 2D) * scale;
                for (int chunkX = Mth.floor(minX) >> 4; chunkX <= Mth.floor(minX + scale - 1E-3) >> 4; chunkX++)
                {
                    for (int chunkZ = Mth.floor(minZ) >> 4; chunkZ <= Mth.floor(minZ + scale - 1E-3) >> 4; chunkZ++)
                        chunks.add(ChunkPos.pack(chunkX, chunkZ));
                }
            }
        }
        return chunks;
    }

    /**
     * Sends built meshes and the footprint mask to the GPU. Called on the render thread before the frame's passes open.
     */
    void upload()
    {
        for (var entry : pendingUploads.long2ObjectEntrySet())
        {
            long key = entry.getLongKey();
            var previous = meshes.put(key, OculusChunkMesh.upload(ChunkPos.getX(key), ChunkPos.getZ(key), windowBottom, entry.getValue()));
            if (previous != null)
                previous.close();
        }
        pendingUploads.clear();

        if (maskDirty)
            uploadMask();
    }

    private void uploadMask()
    {
        if (mask == null || mask.getPixels().getWidth() != footprint.width() || mask.getPixels().getHeight() != footprint.depth())
        {
            if (mask != null)
                mask.close();
            mask = new DynamicTexture(() -> "Oculus footprint " + anchor.toShortString(), footprint.width(), footprint.depth(), true);
        }

        var pixels = mask.getPixels();
        for (int cellZ = 0; cellZ < footprint.depth(); cellZ++)
        {
            for (int cellX = 0; cellX < footprint.width(); cellX++)
                pixels.setPixel(cellX, cellZ, footprint.contains(footprint.minX() + cellX, footprint.minZ() + cellZ) ? 0xFFFFFFFF : 0);
        }
        mask.upload();
        maskDirty = false;
    }

    @Nullable DynamicTexture mask()
    {
        return mask;
    }

    Collection<OculusChunkMesh> meshes()
    {
        return meshes.values();
    }

    /**
     * Camera view of hologram space, whose origin is the footprint corner just above the table top.
     */
    Matrix4f modelView(Matrix4fc viewRotation, Vec3 camera)
    {
        return new Matrix4f(viewRotation).translate(
                (float) (footprint.minX() - camera.x),
                (float) (footprint.y() + 1 + LIFT - camera.y),
                (float) (footprint.minZ() - camera.z));
    }

    float width()
    {
        return footprint.width();
    }

    float depth()
    {
        return footprint.depth();
    }

    float inverseScale()
    {
        return 1F / scale;
    }

    float offsetX(OculusChunkMesh mesh)
    {
        return (float) ((mesh.chunkX << 4) - centerX);
    }

    float offsetY(OculusChunkMesh mesh)
    {
        return mesh.windowBottom - windowBottom;
    }

    float offsetZ(OculusChunkMesh mesh)
    {
        return (float) ((mesh.chunkZ << 4) - centerZ);
    }

    private void discardMeshes()
    {
        meshes.values().forEach(OculusChunkMesh::close);
        meshes.clear();
        pendingUploads.clear();
    }

    @Override
    public void close()
    {
        discardMeshes();
        if (mask != null)
        {
            mask.close();
            mask = null;
        }
    }
}
