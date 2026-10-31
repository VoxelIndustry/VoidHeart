package net.voxelindustry.voidheart.client.render.oculus;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.oculus.OculusFootprint;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableBlock;
import net.voxelindustry.voidheart.common.content.oculus.OculusView;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Hologram of one table: the window of land it shows and the chunk meshes covering it.
 * A chunk keeps showing its last mesh until a newer one is built, so changes never leave holes.
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
    private static final float SURFACE_HEIGHT = 0.15F;
    /**
     * Fraction of the window height the surface must drift before the window follows it.
     */
    private static final float WINDOW_HYSTERESIS = 0.25F;
    /**
     * Ticks before a chunk changing again is rebuilt, so flowing water or a busy farm does not rebuild every tick.
     */
    private static final int REBUILD_COOLDOWN = 10;
    /**
     * World blocks built past the shown land while following, so a moving view does not run into missing chunks.
     */
    private static final int FOLLOW_MARGIN = 16;
    /**
     * How quickly the view catches up with its target, per second.
     */
    private static final float GLIDE_RATE = 6;
    /**
     * Past this many view spans, the view jumps to its target instead of gliding over unbuilt land.
     */
    private static final double SNAP_SPANS = 2;
    private static final int SURFACE_SAMPLES = 7;
    /**
     * Blocks under the ground above them past which a followed player counts as underground.
     */
    private static final int UNDERGROUND_DEPTH = 3;
    /**
     * Share of the window height kept between clamped overlays and the window's top and bottom.
     */
    private static final double CLAMP_INSET = 0.02;
    /**
     * Texels of the footprint distance field per table block.
     */
    static final int FADE_RESOLUTION = 8;
    /**
     * Distance from the table edge, in table blocks, over which the hologram fades out.
     */
    private static final float FADE_RANGE = 0.3F;

    final BlockPos anchor;

    private OculusFootprint footprint;
    private OculusView view = OculusView.DEFAULT;
    private int scale;
    /**
     * Where the view is headed, updated every tick.
     */
    private double targetX;
    private double targetZ;
    /**
     * Where the view is drawn, gliding toward the target every frame.
     */
    private double displayX = Double.NaN;
    private double displayZ;
    private long lastFrameNanos;
    private @Nullable Entity followed;
    private int windowBottom = Integer.MIN_VALUE;
    private int epoch;
    private boolean closed;

    private final Long2ObjectMap<ChunkSlot> slots = new Long2ObjectOpenHashMap<>();

    private @Nullable DynamicTexture mask;
    private boolean maskDirty = true;

    private static final class ChunkSlot
    {
        @Nullable OculusChunkMesh mesh;
        boolean dirty = true;
        boolean building;
        long lastBuildTick = Long.MIN_VALUE;
    }

    /**
     * A chunk slice ready to be meshed off the main thread.
     */
    record BuildTask(OculusProjector projector, long chunk, int epoch, OculusSnapshot snapshot)
    {
    }

    OculusProjector(BlockPos anchor, OculusFootprint footprint)
    {
        this.anchor = anchor;
        this.footprint = footprint;
    }

    void update(ClientLevel level, OculusFootprint footprint, OculusView view)
    {
        this.view = view;
        if (!footprint.equals(this.footprint))
        {
            this.footprint = footprint;
            maskDirty = true;
        }

        followed = view.follow().map(level::getPlayerByUUID).orElse(null);
        if (followed != null)
        {
            targetX = followed.getX();
            targetZ = followed.getZ();
        }
        else if (view.follow().isEmpty())
        {
            targetX = footprint.centerX();
            targetZ = footprint.centerZ();
        }
        // Following someone this client does not see yet keeps the last target until their position is known
        if (Double.isNaN(displayX))
        {
            displayX = targetX;
            displayZ = targetZ;
        }

        int bottom = windowBottom(level, view.scale());
        if (view.scale() != scale || Math.abs(bottom - windowBottom) >= view.scale() * WINDOW_HYSTERESIS)
        {
            scale = view.scale();
            windowBottom = bottom;
            epoch++;
            slots.values().forEach(slot -> slot.dirty = true);
        }

        var wanted = chunksShown(displayX, displayZ, followed != null ? FOLLOW_MARGIN : 0);
        if (Math.abs(targetX - displayX) > 1 || Math.abs(targetZ - displayZ) > 1)
            wanted.addAll(chunksShown(targetX, targetZ, 0));
        slots.long2ObjectEntrySet().removeIf(entry ->
        {
            if (wanted.contains(entry.getLongKey()))
                return false;
            if (entry.getValue().mesh != null)
                entry.getValue().mesh.close();
            return true;
        });
        for (long chunk : wanted)
            slots.computeIfAbsent(chunk, key -> new ChunkSlot());
    }

    /**
     * Rests the window on the typical ground height of the land shown, or centers it on a followed player
     * who went underground, so caves they explore show up.
     */
    private int windowBottom(ClientLevel level, int height)
    {
        int bottom;
        if (followed != null && isUnderground(level, followed))
            bottom = Mth.floor(followed.getY()) - height / 2;
        else
        {
            var surface = medianSurface(level, height);
            if (surface == Integer.MIN_VALUE)
                return windowBottom == Integer.MIN_VALUE ? footprint.y() - height / 2 : windowBottom;
            // The surface is the first air block: at least the ground layer under it stays in the window
            bottom = surface - Math.max(1, Math.round(height * SURFACE_HEIGHT));
        }
        return Mth.clamp(bottom, level.getMinY(), level.getMaxY() + 1 - height);
    }

    private static boolean isUnderground(ClientLevel level, Entity entity)
    {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, entity.getBlockX(), entity.getBlockZ());
        return entity.getY() < surface - UNDERGROUND_DEPTH;
    }

    /**
     * Median ground height over a grid spanning the land shown, ignoring trees and chunks this client does not have.
     */
    private int medianSurface(ClientLevel level, int scale)
    {
        var heights = new IntArrayList(SURFACE_SAMPLES * SURFACE_SAMPLES);
        double spanX = footprint.width() * scale;
        double spanZ = footprint.depth() * scale;
        for (int i = 0; i < SURFACE_SAMPLES; i++)
        {
            for (int j = 0; j < SURFACE_SAMPLES; j++)
            {
                int x = Mth.floor(targetX + spanX * ((i + 0.5) / SURFACE_SAMPLES - 0.5));
                int z = Mth.floor(targetZ + spanZ * ((j + 0.5) / SURFACE_SAMPLES - 0.5));
                if (level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false) != null)
                    heights.add(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
            }
        }
        if (heights.isEmpty())
            return Integer.MIN_VALUE;
        heights.sort(null);
        return heights.getInt(heights.size() / 2);
    }

    /**
     * Chunks under at least one table block of the footprint, with the view centered there.
     *
     * @param margin world blocks shown beyond the edges, built ahead of a moving view
     */
    private LongSet chunksShown(double centerX, double centerZ, int margin)
    {
        var chunks = new LongOpenHashSet();
        for (int cellZ = 0; cellZ < footprint.depth(); cellZ++)
        {
            for (int cellX = 0; cellX < footprint.width(); cellX++)
            {
                if (!footprint.contains(footprint.minX() + cellX, footprint.minZ() + cellZ))
                    continue;

                double minX = centerX + (cellX - footprint.width() / 2D) * scale - margin;
                double minZ = centerZ + (cellZ - footprint.depth() / 2D) * scale - margin;
                double size = scale + margin * 2;
                for (int chunkX = Mth.floor(minX) >> 4; chunkX <= Mth.floor(minX + size - 1E-3) >> 4; chunkX++)
                {
                    for (int chunkZ = Mth.floor(minZ) >> 4; chunkZ <= Mth.floor(minZ + size - 1E-3) >> 4; chunkZ++)
                        chunks.add(ChunkPos.pack(chunkX, chunkZ));
                }
            }
        }
        return chunks;
    }

    /**
     * Glides the drawn view toward its target, exactly onto a followed player. Called once a frame.
     */
    void advance(float partialTick)
    {
        long now = System.nanoTime();
        float seconds = lastFrameNanos == 0 ? 0 : Math.min((now - lastFrameNanos) / 1E9F, 0.25F);
        lastFrameNanos = now;

        double desiredX = targetX;
        double desiredZ = targetZ;
        if (followed != null && !followed.isRemoved())
        {
            var position = followed.getPosition(partialTick);
            desiredX = position.x;
            desiredZ = position.z;
        }

        double span = Math.max(footprint.width(), footprint.depth()) * scale;
        if (Math.abs(desiredX - displayX) > span * SNAP_SPANS || Math.abs(desiredZ - displayZ) > span * SNAP_SPANS)
        {
            displayX = desiredX;
            displayZ = desiredZ;
            return;
        }

        float glide = 1 - (float) Math.exp(-GLIDE_RATE * seconds);
        displayX += (desiredX - displayX) * glide;
        displayZ += (desiredZ - displayZ) * glide;
    }

    /**
     * Marks the chunk slice holding this section for rebuilding, if the section is within the window.
     */
    void onSectionDirty(int sectionX, int sectionY, int sectionZ)
    {
        if (sectionY < SectionPos.blockToSectionCoord(windowBottom) || sectionY > SectionPos.blockToSectionCoord(windowBottom + scale - 1))
            return;

        var slot = slots.get(ChunkPos.pack(sectionX, sectionZ));
        if (slot != null)
            slot.dirty = true;
    }

    void markChunkDirty(long chunk)
    {
        var slot = slots.get(chunk);
        if (slot != null)
            slot.dirty = true;
    }

    void markAllDirty()
    {
        epoch++;
        slots.values().forEach(slot -> slot.dirty = true);
    }

    /**
     * Snapshots up to {@code budget} dirty chunk slices, missing ones first then nearest to the view center.
     *
     * @return the snapshots taken
     */
    int scheduleBuilds(ClientLevel level, long tick, int budget, long deadlineNanos, Consumer<BuildTask> submit)
    {
        var candidates = new ArrayList<Long2ObjectMap.Entry<ChunkSlot>>();
        for (var entry : slots.long2ObjectEntrySet())
        {
            var slot = entry.getValue();
            if (slot.dirty && !slot.building && (slot.mesh == null || tick - slot.lastBuildTick >= REBUILD_COOLDOWN))
                candidates.add(entry);
        }
        candidates.sort(Comparator.<Long2ObjectMap.Entry<ChunkSlot>>comparingInt(entry -> entry.getValue().mesh == null ? 0 : 1)
                .thenComparingDouble(entry -> distanceSq(entry.getLongKey())));

        int scheduled = 0;
        for (var entry : candidates)
        {
            if (scheduled >= budget || System.nanoTime() > deadlineNanos)
                break;

            var slot = entry.getValue();
            long chunk = entry.getLongKey();
            var snapshot = OculusSnapshot.capture(level, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), windowBottom, scale, tick);
            if (snapshot == null)
            {
                // Stays dirty: the server is asked, and asked again later if it never answers
                OculusRemoteChunks.want(anchor, chunk, windowBottom, scale, tick);
                continue;
            }

            slot.dirty = false;
            slot.building = true;
            slot.lastBuildTick = tick;
            submit.accept(new BuildTask(this, chunk, epoch, snapshot));
            scheduled++;
        }
        return scheduled;
    }

    private double distanceSq(long chunk)
    {
        double dx = (ChunkPos.getX(chunk) << 4) + 8 - targetX;
        double dz = (ChunkPos.getZ(chunk) << 4) + 8 - targetZ;
        return dx * dx + dz * dz;
    }

    /**
     * Takes a finished mesh. Render thread only.
     */
    void accept(long chunk, int builtEpoch, int builtWindowBottom, int[] mesh)
    {
        var slot = slots.get(chunk);
        if (closed || slot == null)
            return;

        slot.building = false;
        if (builtEpoch != epoch)
        {
            slot.dirty = true;
            return;
        }

        var previous = slot.mesh;
        slot.mesh = OculusChunkMesh.upload(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), builtWindowBottom, mesh);
        if (previous != null)
            previous.close();
    }

    void buildFailed(long chunk)
    {
        var slot = slots.get(chunk);
        if (slot != null)
        {
            slot.building = false;
            slot.dirty = true;
        }
    }

    boolean isClosed()
    {
        return closed;
    }

    /**
     * Sends the footprint distance field to the GPU. Render thread only.
     */
    void uploadMask()
    {
        if (!maskDirty)
            return;

        int width = footprint.width() * FADE_RESOLUTION;
        int depth = footprint.depth() * FADE_RESOLUTION;
        if (mask == null || mask.getPixels().getWidth() != width || mask.getPixels().getHeight() != depth)
        {
            if (mask != null)
                mask.close();
            mask = new DynamicTexture(() -> "Oculus footprint " + anchor.toShortString(), width, depth, true);
        }

        var pixels = mask.getPixels();
        var fade = edgeFade(footprint);
        for (int z = 0; z < depth; z++)
        {
            for (int x = 0; x < width; x++)
            {
                int value = Math.round(fade[x + z * width] * 255);
                pixels.setPixel(x, z, 0xFF000000 | value << 16 | value << 8 | value);
            }
        }
        mask.upload();
        maskDirty = false;
    }

    /**
     * Per texel, how far it is from leaving the footprint relative to the fade range: 0 outside, 1 well inside.
     */
    static float[] edgeFade(OculusFootprint footprint)
    {
        int width = footprint.width() * FADE_RESOLUTION;
        int depth = footprint.depth() * FADE_RESOLUTION;
        var fade = new float[width * depth];

        for (int z = 0; z < depth; z++)
        {
            for (int x = 0; x < width; x++)
            {
                float px = (x + 0.5F) / FADE_RESOLUTION;
                float pz = (z + 0.5F) / FADE_RESOLUTION;
                int cellX = (int) px;
                int cellZ = (int) pz;
                if (!footprint.contains(footprint.minX() + cellX, footprint.minZ() + cellZ))
                    continue;

                // The fade range is under a block, so only the eight surrounding cells can be the nearest outside
                float nearest = FADE_RANGE;
                for (int dz = -1; dz <= 1; dz++)
                {
                    for (int dx = -1; dx <= 1; dx++)
                    {
                        if (footprint.contains(footprint.minX() + cellX + dx, footprint.minZ() + cellZ + dz))
                            continue;
                        float distanceX = Math.max(0, Math.max(cellX + dx - px, px - (cellX + dx + 1)));
                        float distanceZ = Math.max(0, Math.max(cellZ + dz - pz, pz - (cellZ + dz + 1)));
                        nearest = Math.min(nearest, (float) Math.sqrt(distanceX * distanceX + distanceZ * distanceZ));
                    }
                }
                fade[x + z * width] = nearest / FADE_RANGE;
            }
        }
        return fade;
    }

    @Nullable DynamicTexture mask()
    {
        return mask;
    }

    OculusFootprint footprint()
    {
        return footprint;
    }

    OculusView view()
    {
        return view;
    }

    List<OculusChunkMesh> meshes()
    {
        var meshes = new ArrayList<OculusChunkMesh>(slots.size());
        for (var slot : slots.values())
        {
            if (slot.mesh != null && slot.mesh.vertices() != null)
                meshes.add(slot.mesh);
        }
        return meshes;
    }

    /**
     * Chunks shown, chunks with a mesh, chunks waiting for one, and quads drawn.
     */
    int[] counts()
    {
        int meshes = 0, pending = 0, quads = 0;
        for (var entry : slots.long2ObjectEntrySet())
        {
            var slot = entry.getValue();
            if (slot.mesh != null)
            {
                meshes++;
                quads += slot.mesh.quadCount;
            }
            if (slot.building || slot.dirty && !OculusRemoteChunks.isUnavailable(entry.getLongKey(), windowBottom, scale))
                pending++;
        }
        return new int[] { slots.size(), meshes, pending, quads };
    }

    String describe()
    {
        var text = new StringBuilder("table " + anchor.toShortString() + " scale " + scale + " window " + windowBottom + " epoch " + epoch);
        for (var entry : slots.long2ObjectEntrySet())
        {
            var slot = entry.getValue();
            text.append("\n  chunk ").append(ChunkPos.getX(entry.getLongKey())).append(", ").append(ChunkPos.getZ(entry.getLongKey()))
                    .append(slot.mesh == null ? " no mesh" : " mesh window " + slot.mesh.windowBottom + " quads " + slot.mesh.quadCount)
                    .append(slot.dirty ? " dirty" : "").append(slot.building ? " building" : "");
        }
        return text.toString();
    }

    long gpuBytes()
    {
        long bytes = 0;
        for (var slot : slots.values())
        {
            if (slot.mesh != null)
                bytes += slot.mesh.gpuBytes();
        }
        return bytes;
    }

    /**
     * Camera view of hologram space, whose origin is the footprint corner just above the table top.
     */
    Matrix4f modelView(Matrix4fc viewRotation, Vec3 camera)
    {
        return new Matrix4f(viewRotation).translate(
                (float) (footprint.minX() - camera.x),
                (float) (footprint.y() + OculusTableBlock.HEIGHT + LIFT - camera.y),
                (float) (footprint.minZ() - camera.z));
    }

    Vec3 displayCenter()
    {
        return new Vec3(displayX, windowBottom, displayZ);
    }

    /**
     * Where a point of the land shown floats above the table, in world coordinates.
     *
     * @return nothing when the point is outside the window or beyond the footprint
     */
    @Nullable Vec3 toHologram(double x, double y, double z)
    {
        double holoX = (x - displayX) / scale + footprint.width() / 2D;
        double holoY = (y - windowBottom) / scale;
        double holoZ = (z - displayZ) / scale + footprint.depth() / 2D;
        if (holoY < 0 || holoY > 1 || !footprint.contains(footprint.minX() + Mth.floor(holoX), footprint.minZ() + Mth.floor(holoZ)))
            return null;
        return new Vec3(footprint.minX() + holoX, footprint.y() + OculusTableBlock.HEIGHT + LIFT + holoY, footprint.minZ() + holoZ);
    }

    /**
     * Like {@link #toHologram}, but a point above or below the window is pressed against its top or bottom,
     * for overlays that must stay visible wherever the window sits.
     */
    @Nullable Vec3 toHologramClamped(double x, double y, double z)
    {
        double clampedY = Mth.clamp(y, windowBottom + scale * CLAMP_INSET, windowBottom + scale * (1 - CLAMP_INSET));
        return toHologram(x, clampedY, z);
    }

    /**
     * The land shown, in world coordinates.
     */
    AABB shownArea()
    {
        double halfX = footprint.width() * scale / 2D;
        double halfZ = footprint.depth() * scale / 2D;
        return new AABB(displayX - halfX, windowBottom, displayZ - halfZ, displayX + halfX, windowBottom + scale, displayZ + halfZ);
    }

    /**
     * The top of the hologram, in world coordinates.
     */
    double hologramTop()
    {
        return footprint.y() + OculusTableBlock.HEIGHT + 1 + LIFT;
    }

    int windowBottom()
    {
        return windowBottom;
    }

    int scale()
    {
        return scale;
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
        return (float) ((mesh.chunkX << 4) - displayX);
    }

    float offsetY(OculusChunkMesh mesh)
    {
        return mesh.windowBottom - windowBottom;
    }

    float offsetZ(OculusChunkMesh mesh)
    {
        return (float) ((mesh.chunkZ << 4) - displayZ);
    }

    @Override
    public void close()
    {
        closed = true;
        for (var slot : slots.values())
        {
            if (slot.mesh != null)
                slot.mesh.close();
        }
        slots.clear();
        if (mask != null)
        {
            mask.close();
            mask = null;
        }
    }
}
