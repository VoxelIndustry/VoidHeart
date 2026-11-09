package net.voxelindustry.voidheart.client.render.energy.fissure;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Untextured position and color quads for what a fissure draws on and over the ground. Neither writes depth, so their own layers never fight, and both
 * are pulled toward the camera like decals, so the crack lying on the ground never fights the ground's top face.
 */
final class FissureRenderTypes
{
    /**
     * Blended, for the dark parts: the crack's depths, a wound's rim, the seep's wisps and vapor, and scars. Overlaps of the same dark color blend to the
     * same result in any order.
     */
    private static final RenderPipeline DARK_PIPELINE = RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/fissure_dark"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    static final RenderType DARK = RenderType.create(MODID + "_fissure_dark", RenderSetup.builder(DARK_PIPELINE)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .createRenderSetup());

    /**
     * Added, for the light inside a fissure and along its edges, order independent where it overlaps itself.
     */
    private static final RenderPipeline LIGHT_PIPELINE = RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/fissure_light"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    static final RenderType LIGHT = RenderType.create(MODID + "_fissure_light", RenderSetup.builder(LIGHT_PIPELINE)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .createRenderSetup());

    private FissureRenderTypes()
    {
    }
}
