package net.voxelindustry.voidheart.client.render.oculus;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Opaque hologram terrain drawn with the solid terrain. Both faces are kept: back faces seen through the table edges
 * are painted as the flat cut of the land.
 */
public final class OculusHologramPipeline
{
    /**
     * Position bytes within a chunk slice plus the face ordinal, then the voxel color: eight bytes a vertex.
     */
    public static final VertexFormat VERTEX_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGBA8_UINT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .build();

    public static final String PROJECTION_UNIFORM = "OculusProjection";
    public static final String CHUNK_UNIFORM = "OculusChunk";
    public static final String FOOTPRINT_SAMPLER = "FootprintMask";

    private static final BindGroupLayout BINDINGS = BindGroupLayout.builder()
            .withUniform(PROJECTION_UNIFORM, UniformType.UNIFORM_BUFFER)
            .withUniform(CHUNK_UNIFORM, UniformType.UNIFORM_BUFFER)
            .withUniform(FOOTPRINT_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    public static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/oculus_hologram"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BINDINGS)
            .withVertexShader(Identifier.fromNamespaceAndPath(MODID, "core/oculus_hologram"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(MODID, "core/oculus_hologram"))
            .withVertexBinding(0, VERTEX_FORMAT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build();

    private OculusHologramPipeline()
    {
    }
}
