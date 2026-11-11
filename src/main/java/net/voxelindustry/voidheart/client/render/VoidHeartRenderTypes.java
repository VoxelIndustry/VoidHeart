package net.voxelindustry.voidheart.client.render;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

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
     * Vanilla's emissive entity translucency, but writing depth: the walls of a Wraith Veil tunnel stand in for the stone they hide, so whatever is
     * behind them is hidden too and shows only as an Insight silhouette. Nearly opaque, so sorting barely matters.
     */
    private static final RenderPipeline SHADOW_REALM_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/shadow_realm"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    public static RenderType shadowRealm(Identifier texture)
    {
        return RenderType.create(MODID + "_shadow_realm", RenderSetup.builder(SHADOW_REALM_PIPELINE).withTexture("Sampler0", texture).useOverlay().sortOnUpload().createRenderSetup());
    }

    /**
     * Vanilla eyes, added rather than blended and drawn only where something nearer already covers it, so a mob shows through stone and nowhere else.
     * Depth is reversed in 26.3, so "behind" is LESS_THAN. The bias pulls the fragments toward the camera, so the mob's own body doesn't let its copy through.
     */
    private static final RenderPipeline SILHOUETTE_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_NO_LIGHTMAP_SNIPPET, RenderPipelines.EYES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/silhouette"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN, false, 3.0F, 30.0F))
            .build();

    /**
     * Wraith ink, a mark that spreads by time rather than by scale, see {@code wraith_ink.fsh}. Vanilla's entity vertex stage, opaque and
     * writing depth, with the timing mask beside the texture as vanilla's dissolve does. The ink takes world light, the glowing fronts and the
     * realm seen through its hole do not.
     */
    private static final RenderPipeline WRAITH_INK_PIPELINE = wraithInkPipeline("wraith_ink", false);

    /**
     * The other side of a wraith ink hole, seen from inside a Wraith Veil tunnel: the realm's wall around it.
     */
    private static final RenderPipeline WRAITH_MOUTH_PIPELINE = wraithInkPipeline("wraith_mouth", true);

    private static final Map<Identifier, RenderType> WRAITH_INK   = new HashMap<>();
    private static final Map<Identifier, RenderType> WRAITH_MOUTH = new HashMap<>();

    private static RenderPipeline wraithInkPipeline(String name, boolean mouth)
    {
        var builder = RenderPipeline.builder(RenderPipelines.ENTITY_NO_LIGHTMAP_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/" + name))
                .withFragmentShader(Identifier.fromNamespaceAndPath(MODID, "core/wraith_ink"))
                .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
                .withBindGroupLayout(BindGroupLayouts.DISSOLVE_MASK_SAMPLER)
                .withShaderDefine("NO_OVERLAY")
                .withShaderDefine("NO_CARDINAL_LIGHTING")
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withCull(false);
        if (mouth)
            builder.withShaderDefine("MOUTH");
        return builder.build();
    }

    public static RenderType wraithInk(Identifier texture, Identifier mask)
    {
        return WRAITH_INK.computeIfAbsent(mask, key -> RenderType.create(MODID + "_wraith_ink",
                RenderSetup.builder(WRAITH_INK_PIPELINE).withTexture("Sampler0", texture).withTexture("DissolveMaskSampler", mask).useLightmap().createRenderSetup()));
    }

    public static RenderType wraithMouth(Identifier texture, Identifier mask)
    {
        return WRAITH_MOUTH.computeIfAbsent(mask, key -> RenderType.create(MODID + "_wraith_mouth",
                RenderSetup.builder(WRAITH_MOUTH_PIPELINE).withTexture("Sampler0", texture).withTexture("DissolveMaskSampler", mask).useLightmap().createRenderSetup()));
    }

    private static final Identifier SILHOUETTE_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/insight_silhouette.png");

    public static final RenderType SILHOUETTE = RenderType.create(MODID + "_silhouette",
            RenderSetup.builder(SILHOUETTE_PIPELINE).withTexture("Sampler0", SILHOUETTE_TEXTURE).createRenderSetup());

    /**
     * Vanilla eyes added rather than blended: vanilla's translucent eyes darken what is behind a tile's faint ground, where runes should only ever brighten.
     * Back faces stay culled, so a convex hull of these never overlaps itself.
     */
    private static final RenderPipeline SHIELD_RUNES_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_NO_LIGHTMAP_SNIPPET, RenderPipelines.EYES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MODID, "pipeline/shield_runes"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build();

    private static final Identifier SHIELD_RUNES_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/shield_runes.png");

    public static final RenderType SHIELD_RUNES = RenderType.create(MODID + "_shield_runes",
            RenderSetup.builder(SHIELD_RUNES_PIPELINE).withTexture("Sampler0", SHIELD_RUNES_TEXTURE).createRenderSetup());
}
