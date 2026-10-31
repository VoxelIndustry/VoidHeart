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
import net.minecraft.util.Util;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableStructure;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableTile;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Keeps a projector for every table loaded around the player and draws their holograms with the solid terrain.
 * Chunk slices are snapshotted on the main thread, meshed on background threads and uploaded before the frame's
 * passes open. Uniforms are written then too, the draws happen inside the solid pass.
 */
public final class OculusRenderer
{
    private static final int PROJECTION_UBO_SIZE = new Std140SizeCalculator().putMat4f().putVec4().get();
    private static final int CHUNK_UBO_SIZE = new Std140SizeCalculator().putVec4().get();

    private static final int SNAPSHOTS_PER_TICK = 16;
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

    private record ProjectionData(Matrix4fc modelView, float width, float depth, float inverseScale, float height)
            implements DynamicGpuDataStorage.DynamicGpuData
    {
        @Override
        public void write(ByteBuffer buffer)
        {
            Std140Builder.intoBuffer(buffer).putMat4f(modelView).putVec4(width, depth, inverseScale, height);
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

    public static void tick(Minecraft client)
    {
        var level = client.level;
        if (level != lastLevel)
        {
            closeProjectors();
            lastLevel = level;
        }
        if (level == null)
            return;

        if (OculusPalette.refresh())
            PROJECTORS.values().forEach(OculusProjector::markAllDirty);

        updateProjectors(level);
        scheduleBuilds(level);
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
        // Counted here rather than read from the level, whose time stops with /tick freeze
        long tick = ++ticks;
        int budget = Math.min(SNAPSHOTS_PER_TICK, MAX_BUILDS_IN_FLIGHT - IN_FLIGHT.get());
        for (var projector : PROJECTORS.values())
        {
            if (budget <= 0)
                return;
            budget -= projector.scheduleBuilds(level, tick, budget, OculusRenderer::submit);
        }
    }

    private static void submit(OculusProjector.BuildTask task)
    {
        IN_FLIGHT.incrementAndGet();
        CompletableFuture.supplyAsync(() -> OculusMesher.mesh(task.snapshot().voxelize()), Util.backgroundExecutor())
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
            projector.uploadMask();
            var meshes = projector.meshes();
            if (projector.mask() == null || meshes.isEmpty())
                continue;

            var projection = projectionUbos.writeData(new ProjectionData(projector.modelView(camera.viewRotationMatrix, camera.pos),
                    projector.width(), projector.depth(), projector.inverseScale(), 1));

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

        renderPass.pushDebugGroup(() -> "Oculus holograms");
        renderPass.setPipeline(RenderSystem.getCompiledPipeline(OculusHologramPipeline.PIPELINE));
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        for (var table : FRAME_DRAWS)
        {
            renderPass.setUniform(OculusHologramPipeline.PROJECTION_UNIFORM, table.projection());
            renderPass.setUniform(OculusHologramPipeline.FOOTPRINT_SAMPLER, table.projector().mask().getTextureView(), sampler);
            renderPass.drawMultipleIndexed(table.draws(), frameIndexBuffer, frameIndexType, List.of(OculusHologramPipeline.CHUNK_UNIFORM), table.chunks());
        }
        renderPass.popDebugGroup();
        FRAME_DRAWS.clear();
    }

    /**
     * State of every projector, for debugging and tests.
     */
    public static String describe()
    {
        var text = new StringBuilder("Oculus: " + PROJECTORS.size() + " projectors, " + IN_FLIGHT.get() + " builds in flight");
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
    }
}
