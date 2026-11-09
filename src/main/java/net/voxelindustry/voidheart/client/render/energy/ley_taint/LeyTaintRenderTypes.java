package net.voxelindustry.voidheart.client.render.energy.ley_taint;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Render types for taint drawn over the world.
 */
public final class LeyTaintRenderTypes
{
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/misc/ley_taint.png");

    /**
     * Vanilla entity translucent, lit by the world, without its alpha cutout so the darkened ground and vein edges fade softly,
     * and without depth writes so its own layers never fight: the darkened ground, veins and motes are blended in the order
     * they are drawn, which never changes since nothing sorts them. The decal offset of entity shadows lifts it off the ground.
     */
    private static final RenderPipeline GROUND_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/ley_taint_ground"))
            .withShaderDefine("PER_FACE_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    public static final RenderType GROUND = RenderType.create(MODID + "_ley_taint_ground", RenderSetup.builder(GROUND_PIPELINE)
            .withTexture("Sampler0", TEXTURE)
            .useLightmap()
            .useOverlay()
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .createRenderSetup());

    /**
     * Vanilla translucent blocks without depth writes, pulled toward the camera like decals, so a dormant Old Anchor's model can
     * be blended over the awake one it covers exactly.
     */
    private static final RenderPipeline OVERLAY_BLOCK_PIPELINE = RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/ley_taint_overlay_block"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build();

    public static final RenderType OVERLAY_BLOCK = RenderType.create(MODID + "_ley_taint_overlay_block", RenderSetup.builder(OVERLAY_BLOCK_PIPELINE)
            .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
            .useLightmap()
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .createRenderSetup());

    private LeyTaintRenderTypes()
    {
    }
}
