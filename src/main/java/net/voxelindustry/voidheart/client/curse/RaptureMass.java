package net.voxelindustry.voidheart.client.curse;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.DynamicGpuDataStorage;
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.voxelindustry.voidheart.client.curse.sky.CurseSkyUniform;
import net.voxelindustry.voidheart.common.curse.RaptureLift;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The sky tears at the zenith and something forces its way through, out of blocks of meat and bone, with a painted gullet behind them and, far down it, an eye that stares at the player.
 * The cubes keep real depth among themselves, squeezed into the far end of the depth range where everything else in the world stands in front of them.
 */
final class RaptureMass implements AutoCloseable
{
    private static final String UNIFORM_NAME = "CurseBreach";

    private static final BindGroupLayout BINDINGS = BindGroupLayout.builder()
            .withUniform(UNIFORM_NAME, UniformType.UNIFORM_BUFFER)
            .build();

    private static final VertexFormat MASS_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Offset", GpuFormat.RGB32_FLOAT)
            .addAttribute("Origin", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("Params", GpuFormat.RGBA8_UNORM)
            .build();

    private static final RenderPipeline MASS_PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/curse_rapture_mass"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(CurseSkyUniform.BINDINGS)
            .withBindGroupLayout(BINDINGS)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexShader(Identifier.fromNamespaceAndPath(MODID, "core/curse_rapture_mass"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(MODID, "core/curse_rapture_mass"))
            .withVertexBinding(0, MASS_FORMAT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            // Only faces turned to the camera are in the mesh
            .withCull(false)
            .build();

    private static final RenderPipeline DEEP_PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/curse_rapture_deep"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(CurseSkyUniform.BINDINGS)
            .withBindGroupLayout(BINDINGS)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
            .withVertexShader(Identifier.fromNamespaceAndPath(MODID, "core/curse_rapture_deep"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(MODID, "core/curse_rapture_deep"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(ColorTargetState.DEFAULT)
            // Drawn after the cubes and only where none stands, at the very far end of the depth range
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    private static final Identifier DEEP_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/environment/curse_rapture_deep.png");
    private static final Identifier CORE_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/environment/curse_rapture_core.png");

    // A share of the opening, per tick past it, and the most it gets to
    private static final float WIDENING      = 0.0007F;
    private static final float WIDEST        = 0.09F;
    // Sizes of the pupil, as in the alpha of its sprite
    private static final float PUPIL_START   = 0.2F;
    private static final float PUPIL_END     = 0.52F;
    // Tangents of 6.5 and 8.2 degrees
    private static final float CORE_SIZE_START = 0.114F;
    private static final float CORE_SIZE_END   = 0.144F;
    private static final float CORE_FROM       = 0.76F;
    private static final float CORE_STEPS      = 5;
    // Everything moves in steps of this many ticks
    private static final float WRITHE_STEP_TICKS = 3;

    private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().get();

    private static @Nullable DynamicGpuDataStorageMapped<Data> storage;
    private static @Nullable GpuBuffer                         quad;

    private final int endTick;
    private @Nullable CompletableFuture<Built> building;

    private @Nullable GpuBuffer      mesh;
    private @Nullable DynamicTexture tear;
    private @Nullable GpuBufferSlice uniform;
    private @Nullable AbstractTexture deepTexture;
    private @Nullable AbstractTexture coreTexture;
    private int indexCount;
    private int quads;

    RaptureMass(long seed, int endTick)
    {
        this.endTick = endTick;
        building = CompletableFuture.supplyAsync(() -> Built.of(seed), Util.backgroundExecutor());
    }

    private boolean shows(float now)
    {
        return now >= RaptureLift.MOUTH_TICK && now <= endTick + RaptureLift.MOUTH_SNAP_TICKS;
    }

    /**
     * How many quads of cubes there are, once they are uploaded. For tests.
     */
    int quads()
    {
        return quads;
    }

    /**
     * Must be called with no render pass open.
     */
    void prepare(float now)
    {
        if (building != null && building.isDone())
        {
            upload(building.join());
            building = null;
        }
        if (!shows(now))
        {
            uniform = null;
            return;
        }

        float opening = Mth.clamp((now - RaptureLift.MOUTH_TICK) / RaptureLift.MOUTH_OPEN_TICKS, 0, 1);
        float snap = Mth.clamp((now - endTick) / RaptureLift.MOUTH_SNAP_TICKS, 0, 1);
        float past = Math.max(0, now - RaptureLift.MOUTH_TICK - RaptureLift.MOUTH_OPEN_TICKS);
        float widen = Math.min(WIDEST, past * WIDENING);
        float rising = Mth.clamp((now - RaptureLift.LIFT_TICK) / Math.max(1, endTick - RaptureLift.LIFT_TICK), 0, 1);
        float reveal = Mth.clamp((opening - CORE_FROM) / (1 - CORE_FROM), 0, 1);

        // Interpolated within the frame, and not from Mth's table, which would turn the eye in small steps
        double yaw = Math.toRadians(Minecraft.getInstance().gameRenderer.mainCamera().yRot());

        if (storage == null)
            storage = new DynamicGpuDataStorageMapped<>("Curse breach UBO", UNIFORM_SIZE, GpuBuffer.USAGE_UNIFORM, 4);
        storage.endFrame();
        uniform = storage.writeData(new Data(
                new Vector4f(opening, snap, widen, Mth.floor(now / WRITHE_STEP_TICKS) * WRITHE_STEP_TICKS / 20),
                new Vector4f(opening * (1 - snap * snap), Mth.lerp(rising, PUPIL_START, PUPIL_END),
                        Mth.lerp(rising, CORE_SIZE_START, CORE_SIZE_END), Mth.ceil(reveal * CORE_STEPS) / CORE_STEPS),
                new Vector4f((float) Math.cos(yaw), (float) Math.sin(yaw), 0, 0)));
    }

    private void upload(Built built)
    {
        var minecraft = Minecraft.getInstance();
        var atlas = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        var rects = new float[RaptureMassMesh.SPRITES.length][];
        for (int i = 0; i < rects.length; i++)
        {
            var sprite = atlas.getSprite(Identifier.fromNamespaceAndPath(MODID, "block/curse_rapture_" + RaptureMassMesh.SPRITES[i]));
            rects[i] = new float[]{sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1()};
        }

        quads = built.mesh.quads();
        indexCount = quads * 6;
        var data = MemoryUtil.memAlloc(quads * 4 * RaptureMassMesh.VERTEX_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        try
        {
            built.mesh.write(data, rects);
            data.flip();
            mesh = RenderSystem.getDevice().createBuffer(() -> "Curse breach", GpuBuffer.USAGE_VERTEX, data);
        } finally
        {
            MemoryUtil.memFree(data);
        }

        var image = new NativeImage(RaptureMassShape.TEAR_SIZE, RaptureMassShape.TEAR_SIZE, false);
        for (int y = 0; y < RaptureMassShape.TEAR_SIZE; y++)
        {
            for (int x = 0; x < RaptureMassShape.TEAR_SIZE; x++)
                image.setPixel(x, y, built.tear[y * RaptureMassShape.TEAR_SIZE + x]);
        }
        tear = new DynamicTexture(() -> "Curse breach tear", image);

        if (quad == null)
            quad = buildQuad();

        // All of this first happens long before the sky tears, so that it doesn't stall the very frame it does, and loading a texture can't happen with the pass open
        deepTexture = minecraft.getTextureManager().getTexture(DEEP_TEXTURE);
        coreTexture = minecraft.getTextureManager().getTexture(CORE_TEXTURE);
        RenderSystem.getCompiledPipeline(MASS_PIPELINE);
        RenderSystem.getCompiledPipeline(DEEP_PIPELINE);
    }

    private static GpuBuffer buildQuad()
    {
        var data = MemoryUtil.memAlloc(4 * 3 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        try
        {
            data.putFloat(-1).putFloat(0).putFloat(-1);
            data.putFloat(1).putFloat(0).putFloat(-1);
            data.putFloat(1).putFloat(0).putFloat(1);
            data.putFloat(-1).putFloat(0).putFloat(1);
            data.flip();
            return RenderSystem.getDevice().createBuffer(() -> "Curse breach quad", GpuBuffer.USAGE_VERTEX, data);
        } finally
        {
            MemoryUtil.memFree(data);
        }
    }

    /**
     * The cubes first, nearest first, then the gullet only where they leave any sky.
     */
    void draw(RenderPass renderPass, GpuBufferSlice sky)
    {
        if (uniform == null || mesh == null || tear == null || quad == null || deepTexture == null || coreTexture == null)
            return;

        var quadIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);

        var atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        renderPass.setPipeline(RenderSystem.getCompiledPipeline(MASS_PIPELINE));
        RenderSystem.bindDefaultUniforms(renderPass);
        renderPass.setUniform(CurseSkyUniform.NAME, sky);
        renderPass.setUniform(UNIFORM_NAME, uniform);
        renderPass.setUniform("Sampler0", atlas.getTextureView(), atlas.getSampler());
        renderPass.setVertexBuffer(0, mesh.slice());
        renderPass.setIndexBuffer(quadIndices.getBuffer(indexCount), quadIndices.type());
        renderPass.drawIndexed(indexCount, 1, 0, 0, 0);

        var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        renderPass.setPipeline(RenderSystem.getCompiledPipeline(DEEP_PIPELINE));
        RenderSystem.bindDefaultUniforms(renderPass);
        renderPass.setUniform(CurseSkyUniform.NAME, sky);
        renderPass.setUniform(UNIFORM_NAME, uniform);
        renderPass.setUniform("Sampler0", tear.getTextureView(), nearest);
        renderPass.setUniform("Sampler1", deepTexture.getTextureView(), nearest);
        renderPass.setUniform("Sampler2", coreTexture.getTextureView(), nearest);
        renderPass.setVertexBuffer(0, quad.slice());
        renderPass.setIndexBuffer(quadIndices.getBuffer(6), quadIndices.type());
        renderPass.drawIndexed(6, 1, 0, 0, 0);
    }

    @Override
    public void close()
    {
        if (building != null)
            building.cancel(false);
        building = null;
        if (mesh != null)
            mesh.close();
        if (tear != null)
            tear.close();
        mesh = null;
        tear = null;
    }

    private record Built(RaptureMassMesh mesh, int[] tear)
    {
        private static Built of(long seed)
        {
            var mesh = RaptureMassMesh.build(seed);
            return new Built(mesh, mesh.shape().bakeTear());
        }
    }

    private record Data(Vector4fc motion, Vector4fc core, Vector4fc turn) implements DynamicGpuDataStorage.DynamicGpuData
    {
        @Override
        public void write(ByteBuffer buffer)
        {
            Std140Builder.intoBuffer(buffer).putVec4(motion).putVec4(core).putVec4(turn);
        }
    }
}
