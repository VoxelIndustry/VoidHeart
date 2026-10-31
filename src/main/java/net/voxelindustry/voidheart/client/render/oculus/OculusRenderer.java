package net.voxelindustry.voidheart.client.render.oculus;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DynamicGpuDataStorage;
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.oculus.OculusChunkPayloads;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableStructure;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableTile;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Keeps a projector for every table loaded around the player and draws their holograms with the solid terrain.
 * Chunk slices are snapshotted on the main thread, meshed on background threads and uploaded before the frame's
 * passes open. Uniforms are written then too, the draws happen inside the solid pass.
 */
public final class OculusRenderer
{
    private static final int PROJECTION_UBO_SIZE = new Std140SizeCalculator().putMat4f().putVec4().putVec4().get();
    private static final int CHUNK_UBO_SIZE = new Std140SizeCalculator().putVec4().get();

    private static final int SNAPSHOTS_PER_TICK = 16;
    /**
     * Main thread time spent copying chunks each tick, so filling a large hologram never costs a frame.
     */
    private static final long SNAPSHOT_NANOS_PER_TICK = 1_000_000;
    private static final int MAX_BUILDS_IN_FLIGHT = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
    private static final int UPLOADS_PER_FRAME = 32;

    private static final Set<BlockPos> TABLES = new HashSet<>();
    private static final Map<BlockPos, OculusProjector> PROJECTORS = new HashMap<>();
    private static final List<TableDraw> FRAME_DRAWS = new ArrayList<>();
    private static final Queue<BuildResult> RESULTS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger IN_FLIGHT = new AtomicInteger();

    private static @Nullable DynamicGpuDataStorageMapped<ProjectionData> projectionUbos;
    private static @Nullable DynamicGpuDataStorageMapped<ChunkData> chunkUbos;
    private static @Nullable GpuBuffer frameIndexBuffer;
    private static IndexType frameIndexType = IndexType.SHORT;
    private static @Nullable ClientLevel lastLevel;
    private static long ticks;

    private OculusRenderer()
    {
    }

    private record ProjectionData(Matrix4fc modelView, float width, float depth, float inverseScale, float height, float presence)
            implements DynamicGpuDataStorage.DynamicGpuData
    {
        @Override
        public void write(ByteBuffer buffer)
        {
            Std140Builder.intoBuffer(buffer).putMat4f(modelView).putVec4(width, depth, inverseScale, height).putVec4(presence, 0, 0, 0);
        }
    }

    private record ChunkData(float x, float y, float z) implements DynamicGpuDataStorage.DynamicGpuData
    {
        @Override
        public void write(ByteBuffer buffer)
        {
            Std140Builder.intoBuffer(buffer).putVec4(x, y, z, 0);
        }
    }

    private record TableDraw(OculusProjector projector, GpuBufferSlice projection, List<RenderPass.Draw<GpuBufferSlice[]>> draws, GpuBufferSlice[] chunks)
    {
    }

    /**
     * @param mesh packed vertices, or nothing when the build failed
     */
    private record BuildResult(OculusProjector.BuildTask task, int @Nullable [] mesh)
    {
    }

    public static void onLoad(BlockEntity blockEntity, ClientLevel level)
    {
        if (blockEntity instanceof OculusTableTile)
            TABLES.add(blockEntity.getBlockPos().immutable());
    }

    public static void onUnload(BlockEntity blockEntity, ClientLevel level)
    {
        if (blockEntity instanceof OculusTableTile)
            TABLES.remove(blockEntity.getBlockPos());
    }

    public static void onSectionDirty(int sectionX, int sectionY, int sectionZ)
    {
        for (var projector : PROJECTORS.values())
            projector.onSectionDirty(sectionX, sectionY, sectionZ);
    }

    /**
     * Land arrived from the server: the chunk can be built, and its neighbors may now hide their border faces.
     */
    static void onRemoteChunk(int chunkX, int chunkZ)
    {
        for (var projector : PROJECTORS.values())
        {
            projector.markChunkDirty(ChunkPos.pack(chunkX, chunkZ));
            for (var direction : Direction.Plane.HORIZONTAL)
                projector.markChunkDirty(ChunkPos.pack(chunkX + direction.getStepX(), chunkZ + direction.getStepZ()));
        }
    }

    static long ticks()
    {
        return ticks;
    }

    public static int remoteChunkCount()
    {
        return OculusRemoteChunks.size();
    }

    public static void receiveSections(OculusChunkPayloads.Sections payload, ClientPlayNetworking.Context context)
    {
        OculusRemoteChunks.receive(payload, context);
    }

    public static void tick(Minecraft client)
    {
        var level = client.level;
        if (level != lastLevel)
        {
            closeProjectors();
            OculusRemoteChunks.clear();
            lastLevel = level;
        }
        if (level == null)
            return;

        if (OculusPalette.refresh())
            PROJECTORS.values().forEach(OculusProjector::markAllDirty);

        // Counted here rather than read from the level, whose time stops with /tick freeze
        ticks++;
        updateProjectors(level);
        scheduleBuilds(level);
        OculusRemoteChunks.tick(ticks, chunk -> PROJECTORS.values().forEach(projector -> projector.markChunkDirty(chunk)));
        OculusMarkers.tick(level, ticks);
        OculusLeyLayer.tick(ticks);
    }

    private static void updateProjectors(ClientLevel level)
    {
        var assigned = new HashSet<BlockPos>();
        var live = new HashSet<BlockPos>();
        for (var pos : TABLES)
        {
            if (assigned.contains(pos))
                continue;
            assigned.addAll(OculusTableStructure.collect(level, pos));

            var footprint = OculusTableStructure.footprint(level, pos);
            if (footprint.isEmpty())
                continue;

            var anchor = footprint.get().anchor();
            if (!(level.getBlockEntity(anchor) instanceof OculusTableTile tile))
                continue;

            live.add(anchor);
            PROJECTORS.computeIfAbsent(anchor, key -> new OculusProjector(key, footprint.get()))
                    .update(level, footprint.get(), tile.getView());
        }

        PROJECTORS.entrySet().removeIf(entry ->
        {
            if (live.contains(entry.getKey()))
                return false;
            entry.getValue().close();
            return true;
        });
    }

    private static void scheduleBuilds(ClientLevel level)
    {
        long start = System.nanoTime();
        long tick = ticks;
        int budget = Math.min(SNAPSHOTS_PER_TICK, MAX_BUILDS_IN_FLIGHT - IN_FLIGHT.get());
        for (var projector : PROJECTORS.values())
        {
            if (budget <= 0)
                break;
            budget -= projector.scheduleBuilds(level, tick, budget, start + SNAPSHOT_NANOS_PER_TICK, OculusRenderer::submit);
        }
        STATS.scheduleNanos.addAndGet(System.nanoTime() - start);
        STATS.ticks.incrementAndGet();
    }

    private static void submit(OculusProjector.BuildTask task)
    {
        IN_FLIGHT.incrementAndGet();
        CompletableFuture.supplyAsync(() ->
                {
                    long start = System.nanoTime();
                    var mesh = OculusMesher.mesh(task.snapshot().voxelize());
                    STATS.builds.incrementAndGet();
                    STATS.buildNanos.addAndGet(System.nanoTime() - start);
                    return mesh;
                }, Util.backgroundExecutor())
                .whenComplete((mesh, error) ->
                {
                    if (error != null)
                        VoidHeart.LOGGER.log(Level.WARNING, "Failed to build an Oculus hologram chunk", error);
                    RESULTS.add(new BuildResult(task, mesh));
                    IN_FLIGHT.decrementAndGet();
                });
    }

    /**
     * Uploads finished meshes and writes this frame's uniforms. No render pass may be open.
     */
    public static void prepare(CameraRenderState camera)
    {
        long start = System.nanoTime();
        try
        {
            prepareFrame(camera);
        } finally
        {
            STATS.prepareNanos.addAndGet(System.nanoTime() - start);
            STATS.frames.incrementAndGet();
        }
    }

    private static void prepareFrame(CameraRenderState camera)
    {
        FRAME_DRAWS.clear();
        uploadResults();
        if (PROJECTORS.isEmpty())
            return;

        if (projectionUbos == null || chunkUbos == null)
        {
            projectionUbos = new DynamicGpuDataStorageMapped<>("Oculus projection UBO", PROJECTION_UBO_SIZE, GpuBuffer.USAGE_UNIFORM, 4);
            chunkUbos = new DynamicGpuDataStorageMapped<>("Oculus chunk UBO", CHUNK_UBO_SIZE, GpuBuffer.USAGE_UNIFORM, 256);
        }
        projectionUbos.endFrame();
        chunkUbos.endFrame();

        int largestIndexCount = 0;
        for (var projector : PROJECTORS.values())
        {
            projector.advance(camera.cameraEntityPartialTicks);
            projector.uploadMask();
            var meshes = projector.meshes();
            if (projector.mask() == null || meshes.isEmpty() || projector.presence() <= 0)
                continue;

            var projection = projectionUbos.writeData(new ProjectionData(projector.modelView(camera.viewRotationMatrix, camera.pos),
                    projector.width(), projector.depth(), projector.inverseScale(), 1, projector.presence()));

            var chunkData = new ChunkData[meshes.size()];
            var draws = new ArrayList<RenderPass.Draw<GpuBufferSlice[]>>(meshes.size());
            for (int i = 0; i < meshes.size(); i++)
            {
                var mesh = meshes.get(i);
                chunkData[i] = new ChunkData(projector.offsetX(mesh), projector.offsetY(mesh), projector.offsetZ(mesh));

                int index = i;
                int indexCount = mesh.quadCount * 6;
                largestIndexCount = Math.max(largestIndexCount, indexCount);
                draws.add(new RenderPass.Draw<>(0, mesh.vertices(), null, null, 0, indexCount, 0,
                        (slices, uploader) -> uploader.setUniform(OculusHologramPipeline.CHUNK_UNIFORM, slices[index])));
            }
            FRAME_DRAWS.add(new TableDraw(projector, projection, draws, chunkUbos.writeData(chunkData)));
        }

        if (!FRAME_DRAWS.isEmpty())
        {
            var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            frameIndexBuffer = indices.getBuffer(largestIndexCount);
            frameIndexType = indices.type();
        }
    }

    private static void uploadResults()
    {
        for (int uploaded = 0; uploaded < UPLOADS_PER_FRAME; uploaded++)
        {
            var result = RESULTS.poll();
            if (result == null)
                return;

            var task = result.task();
            if (result.mesh() == null)
                task.projector().buildFailed(task.chunk());
            else
                task.projector().accept(task.chunk(), task.epoch(), task.snapshot().windowBottom, result.mesh());
        }
    }

    /**
     * Draws the holograms prepared for this frame into the open solid pass.
     */
    public static void draw(RenderPass renderPass)
    {
        if (FRAME_DRAWS.isEmpty() || frameIndexBuffer == null)
            return;

        long start = System.nanoTime();
        renderPass.pushDebugGroup(() -> "Oculus holograms");
        renderPass.setPipeline(RenderSystem.getCompiledPipeline(OculusHologramPipeline.PIPELINE));
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        for (var table : FRAME_DRAWS)
        {
            renderPass.setUniform(OculusHologramPipeline.PROJECTION_UNIFORM, table.projection());
            renderPass.setUniform(OculusHologramPipeline.FOOTPRINT_SAMPLER, table.projector().mask().getTextureView(), sampler);
            renderPass.drawMultipleIndexed(table.draws(), frameIndexBuffer, frameIndexType, List.of(OculusHologramPipeline.CHUNK_UNIFORM), table.chunks());
            STATS.draws.addAndGet(table.draws().size());
        }
        renderPass.popDebugGroup();
        FRAME_DRAWS.clear();
        STATS.drawNanos.addAndGet(System.nanoTime() - start);
    }

    /**
     * Running totals of the work done for holograms, read and reset by {@link #takeStats}.
     */
    private static final class Stats
    {
        final AtomicLong builds = new AtomicLong();
        final AtomicLong buildNanos = new AtomicLong();
        final AtomicLong ticks = new AtomicLong();
        final AtomicLong scheduleNanos = new AtomicLong();
        final AtomicLong frames = new AtomicLong();
        final AtomicLong prepareNanos = new AtomicLong();
        final AtomicLong drawNanos = new AtomicLong();
        final AtomicLong draws = new AtomicLong();
    }

    private static final Stats STATS = new Stats();

    /**
     * Averages since the last call: background build time per chunk, main thread time per tick and render thread
     * time per frame.
     */
    public static String takeStats()
    {
        long builds = STATS.builds.getAndSet(0);
        long buildNanos = STATS.buildNanos.getAndSet(0);
        long ticks = Math.max(1, STATS.ticks.getAndSet(0));
        long scheduleNanos = STATS.scheduleNanos.getAndSet(0);
        long frames = Math.max(1, STATS.frames.getAndSet(0));
        long prepareNanos = STATS.prepareNanos.getAndSet(0);
        long drawNanos = STATS.drawNanos.getAndSet(0);
        long draws = STATS.draws.getAndSet(0);
        return String.format(Locale.ROOT,
                "%d builds at %.3f ms each (background) | snapshots %.3f ms a tick | prepare %.3f ms + draw %.3f ms a frame, %d draws a frame",
                builds, builds == 0 ? 0 : buildNanos / 1E6 / builds, scheduleNanos / 1E6 / ticks,
                prepareNanos / 1E6 / frames, drawNanos / 1E6 / frames, draws / frames);
    }

    static Collection<OculusProjector> projectors()
    {
        return PROJECTORS.values();
    }

    /**
     * Where the hologram of the table anchored there is centered: x and z of the land shown, y of the window bottom.
     */
    public static Optional<Vec3> displayCenter(BlockPos anchor)
    {
        return Optional.ofNullable(PROJECTORS.get(anchor)).map(OculusProjector::displayCenter);
    }

    /**
     * World blocks per table block the hologram anchored there is drawn at this frame, gliding after a zoom.
     */
    public static Optional<Float> drawnScale(BlockPos anchor)
    {
        return Optional.ofNullable(PROJECTORS.get(anchor)).map(OculusProjector::drawnScale);
    }

    /**
     * One line totals over every projector, for debugging and tests.
     */
    public static String summary()
    {
        int chunks = 0, meshes = 0, pending = 0, quads = 0;
        long bytes = 0;
        for (var projector : PROJECTORS.values())
        {
            var counts = projector.counts();
            chunks += counts[0];
            meshes += counts[1];
            pending += counts[2];
            quads += counts[3];
            bytes += projector.gpuBytes();
        }
        return String.format(Locale.ROOT, "%d chunks shown, %d meshed, %d pending, %d in flight, %d remote | %d quads, %d KiB of vertices",
                chunks, meshes, pending, IN_FLIGHT.get(), OculusRemoteChunks.size(), quads, bytes / 1024);
    }

    /**
     * Whether every chunk shown has an up to date mesh.
     */
    public static boolean isSettled()
    {
        return !PROJECTORS.isEmpty() && IN_FLIGHT.get() == 0 && RESULTS.isEmpty()
                && PROJECTORS.values().stream().allMatch(projector -> projector.counts()[2] == 0);
    }

    /**
     * State of every projector, for debugging and tests.
     */
    public static String describe()
    {
        var text = new StringBuilder("Oculus: " + PROJECTORS.size() + " projectors, " + IN_FLIGHT.get() + " builds in flight, "
                + OculusRemoteChunks.size() + " remote chunks");
        long bytes = 0;
        for (var projector : PROJECTORS.values())
        {
            text.append('\n').append(projector.describe());
            bytes += projector.gpuBytes();
        }
        return text.append("\nGPU vertex memory ").append(bytes / 1024).append(" KiB").toString();
    }

    private static void closeProjectors()
    {
        PROJECTORS.values().forEach(OculusProjector::close);
        PROJECTORS.clear();
        FRAME_DRAWS.clear();
    }

    public static void clear()
    {
        closeProjectors();
        TABLES.clear();
        OculusRemoteChunks.clear();
    }
}
