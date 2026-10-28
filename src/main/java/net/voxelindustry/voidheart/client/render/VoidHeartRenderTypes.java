package net.voxelindustry.voidheart.client.render;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartRenderTypes
{
    /**
     * Vanilla lightning (additive, position + color quads) without depth writes or culling.
     * Additive blending is order independent, so overlapping glow can't flicker as the camera moves
     * and quads re-sort, while terrain still hides it through the depth test.
     */
    private static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/glow"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    public static final RenderType GLOW = RenderType.create(MODID + "_glow", RenderSetup.builder(GLOW_PIPELINE).createRenderSetup());
}
