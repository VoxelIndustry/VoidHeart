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

    /**
     * Same glow, seen through terrain: for what is hidden by definition, such as a relay link a wall blocks.
     */
    private static final RenderPipeline GLOW_THROUGH_PIPELINE = RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/glow_through"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    public static final RenderType GLOW_THROUGH = RenderType.create(MODID + "_glow_through", RenderSetup.builder(GLOW_THROUGH_PIPELINE).createRenderSetup());

    /**
     * Same quads blended over what is behind instead of added to it, so their hue survives on bright backgrounds.
     */
    private static final RenderPipeline MARKER_PIPELINE = RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/marker"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    public static final RenderType MARKER = RenderType.create(MODID + "_marker", RenderSetup.builder(MARKER_PIPELINE).sortOnUpload().createRenderSetup());

    /**
     * Vanilla eyes, added rather than blended and drawn only where something nearer already covers it, so a mob shows through stone and nowhere else.
     * Depth is reversed in 26.3, so "behind" is LESS_THAN. The bias pulls the fragments toward the camera, so the mob's own body doesn't let its copy through.
     */
    private static final RenderPipeline SILHOUETTE_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_NO_LIGHTMAP_SNIPPET, RenderPipelines.EYES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/silhouette"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN, false, 3.0F, 30.0F))
            .build();

    private static final Identifier SILHOUETTE_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/insight_silhouette.png");

    public static final RenderType SILHOUETTE = RenderType.create(MODID + "_silhouette",
            RenderSetup.builder(SILHOUETTE_PIPELINE).withTexture("Sampler0", SILHOUETTE_TEXTURE).createRenderSetup());
}
