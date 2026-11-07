package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.InsightSight;
import net.voxelindustry.voidheart.client.model.mob.FallenBishopModel;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.client.render.BeamTube;
import net.voxelindustry.voidheart.client.render.state.FallenBishopRenderState;
import net.voxelindustry.voidheart.common.content.mob.FallenBishop;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * While charging, a thin wavering line shows where the lance will go, turning steady and bright the moment its aim locks. It then flares into a thick beam that thins
 * out and is gone in a few ticks. Under Insight, light gathers in at the crook's orb before the charge even starts.
 */
public class FallenBishopRender extends MobRenderer<FallenBishop, FallenBishopRenderState, FallenBishopModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/fallen_bishop.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/fallen_bishop_glow.png");
    private static final Identifier BEAM = Identifier.withDefaultNamespace("textures/entity/beacon/beacon_beam.png");

    // The teal of the Fallen's magic, the same as the Elder's orbs
    private static final int LINE_COLOR = 0x36C5B0;
    private static final int LOCKED_COLOR = 0xA8F0E2;
    private static final int CORE_COLOR = 0xFFE8FFFA;
    private static final float MODEL_SCALE = 0.9F;
    private static final int LINE_ALPHA = 90;
    private static final int LOCKED_ALPHA = 200;
    private static final int FLARE_ALPHA = 190;
    private static final float LINE_RADIUS = 0.018F;
    private static final float LINE_WAVER = 0.008F;
    private static final float LOCKED_RADIUS = 0.03F;
    private static final float CORE_RADIUS = 0.1F;
    private static final float FLARE_RADIUS = 0.26F;

    // Insight's warning: a soft core that swells, and rings drawn in toward it
    private static final int GATHER_COLOR = 0x6BDBC0;
    private static final int GATHER_CORE_ALPHA = 150;
    private static final float GATHER_CORE_MIN = 0.06F;
    private static final float GATHER_CORE_MAX = 0.2F;
    private static final int GATHER_RING_ALPHA = 120;
    private static final int GATHER_RINGS = 3;
    private static final float GATHER_RING_FROM = 0.75F;
    private static final float GATHER_RING_WIDTH = 0.05F;
    private static final float GATHER_PERIOD_TICKS = 12;
    private static final int GATHER_SEGMENTS = 12;

    public FallenBishopRender(EntityRendererProvider.Context context)
    {
        super(context, new FallenBishopModel(context.bakeLayer(FallenBishopModel.LAYER)), 0.55F);
        addLayer(new VoidGlowLayer<>(this, GLOW));
    }

    @Override
    public FallenBishopRenderState createRenderState()
    {
        return new FallenBishopRenderState();
    }

    @Override
    protected void scale(FallenBishopRenderState state, PoseStack poseStack)
    {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public Identifier getTextureLocation(FallenBishopRenderState state)
    {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(FallenBishop bishop, Frustum culler, double camX, double camY, double camZ, float partialTicks)
    {
        if (super.shouldRender(bishop, culler, camX, camY, camZ, partialTicks))
            return true;
        return bishop.lanceTicks() > 0 && culler.isVisible(new AABB(bishop.lanceOrigin(partialTicks), bishop.lanceEnd()).inflate(0.5));
    }

    @Override
    public void extractRenderState(FallenBishop bishop, FallenBishopRenderState state, float partialTicks)
    {
        super.extractRenderState(bishop, state, partialTicks);
        state.raise = Mth.lerp(partialTicks, bishop.raiseO, bishop.raise);
        state.kneel = Mth.lerp(partialTicks, bishop.kneelO, bishop.kneel);
        state.attack = bishop.getSwingAnimation(partialTicks);

        int lance = bishop.lanceTicks();
        if (lance > 0)
        {
            state.lanceFrom = bishop.lanceOrigin(partialTicks).subtract(bishop.getPosition(partialTicks));
            state.lanceTo = bishop.lanceEnd().subtract(bishop.getPosition(partialTicks));
            state.lanceTicks = lance + partialTicks;
        }
        else
        {
            state.lanceFrom = null;
            state.lanceTo = null;
        }

        if (bishop.isLanceReady() && InsightSight.active())
        {
            state.gatherAt = bishop.lanceOrigin(partialTicks).subtract(bishop.getPosition(partialTicks));
            state.gatherTicks = bishop.readyTicks + partialTicks;
            state.gather = Mth.clamp(state.gatherTicks / FallenBishop.LANCE_WARN_TICKS, 0, 1);
        }
        else
            state.gatherAt = null;
    }

    @Override
    public void submit(FallenBishopRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        super.submit(state, poseStack, collector, camera);

        if (state.lanceFrom != null && state.lanceTo != null)
        {
            poseStack.pushPose();
            poseStack.translate(state.lanceFrom.x, state.lanceFrom.y, state.lanceFrom.z);
            submitLance(poseStack, collector, state.lanceTo.subtract(state.lanceFrom), state.lanceTicks);
            poseStack.popPose();
        }

        if (state.gatherAt != null)
        {
            poseStack.pushPose();
            poseStack.translate(state.gatherAt.x, state.gatherAt.y, state.gatherAt.z);
            poseStack.rotate(camera.orientation);
            float gather = state.gather;
            float ticks = state.gatherTicks;
            collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
            {
                disc(pose, buffer, Mth.lerp(gather, GATHER_CORE_MIN, GATHER_CORE_MAX), ARGB.color(Math.round(GATHER_CORE_ALPHA * (0.4F + 0.6F * gather)), GATHER_COLOR));
                for (int ring = 0; ring < GATHER_RINGS; ring++)
                {
                    // Each ring closes in from wide and faint to tight and bright, then starts over
                    float phase = Mth.frac(ticks / GATHER_PERIOD_TICKS + (float) ring / GATHER_RINGS);
                    float radius = Mth.lerp(phase, GATHER_RING_FROM, GATHER_CORE_MIN);
                    ring(pose, buffer, radius, ARGB.color(Math.round(GATHER_RING_ALPHA * phase * gather), GATHER_COLOR));
                }
            });
            poseStack.popPose();
        }
    }

    /**
     * A flat disc facing the camera, full color at the middle and gone at the edge.
     */
    private static void disc(PoseStack.Pose pose, VertexConsumer buffer, float radius, int color)
    {
        int clear = ARGB.color(0, color);
        for (int i = 0; i < GATHER_SEGMENTS; i++)
        {
            float a0 = i * Mth.TWO_PI / GATHER_SEGMENTS;
            float a1 = (i + 1) * Mth.TWO_PI / GATHER_SEGMENTS;
            buffer.addVertex(pose, 0, 0, 0).setColor(color);
            buffer.addVertex(pose, 0, 0, 0).setColor(color);
            buffer.addVertex(pose, Mth.cos(a1) * radius, Mth.sin(a1) * radius, 0).setColor(clear);
            buffer.addVertex(pose, Mth.cos(a0) * radius, Mth.sin(a0) * radius, 0).setColor(clear);
        }
    }

    /**
     * A soft ring facing the camera, brightest along its radius and fading to either side.
     */
    private static void ring(PoseStack.Pose pose, VertexConsumer buffer, float radius, int color)
    {
        int clear = ARGB.color(0, color);
        float inner = Math.max(0, radius - GATHER_RING_WIDTH);
        float outer = radius + GATHER_RING_WIDTH;
        for (int i = 0; i < GATHER_SEGMENTS; i++)
        {
            float c0 = Mth.cos(i * Mth.TWO_PI / GATHER_SEGMENTS);
            float s0 = Mth.sin(i * Mth.TWO_PI / GATHER_SEGMENTS);
            float c1 = Mth.cos((i + 1) * Mth.TWO_PI / GATHER_SEGMENTS);
            float s1 = Mth.sin((i + 1) * Mth.TWO_PI / GATHER_SEGMENTS);
            buffer.addVertex(pose, c0 * inner, s0 * inner, 0).setColor(clear);
            buffer.addVertex(pose, c0 * radius, s0 * radius, 0).setColor(color);
            buffer.addVertex(pose, c1 * radius, s1 * radius, 0).setColor(color);
            buffer.addVertex(pose, c1 * inner, s1 * inner, 0).setColor(clear);

            buffer.addVertex(pose, c0 * radius, s0 * radius, 0).setColor(color);
            buffer.addVertex(pose, c0 * outer, s0 * outer, 0).setColor(clear);
            buffer.addVertex(pose, c1 * outer, s1 * outer, 0).setColor(clear);
            buffer.addVertex(pose, c1 * radius, s1 * radius, 0).setColor(color);
        }
    }

    private static void submitLance(PoseStack poseStack, SubmitNodeCollector collector, Vec3 beam, float ticks)
    {
        float length = BeamTube.alignTo(poseStack, beam);
        if (length == 0)
            return;

        float scroll = Mth.frac(-ticks * 0.2F);
        float spin = ticks * 0.3F;
        if (ticks <= FallenBishop.CHARGE_TICKS)
        {
            boolean locked = ticks > FallenBishop.CHARGE_TICKS - FallenBishop.LOCK_TICKS;
            float radius = locked ? LOCKED_RADIUS : LINE_RADIUS + Mth.sin(ticks * 1.7F) * LINE_WAVER;
            int color = locked ? ARGB.color(LOCKED_ALPHA, LOCKED_COLOR) : ARGB.color(LINE_ALPHA, LINE_COLOR);
            collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BEAM, true),
                    (pose, buffer) -> BeamTube.tube(pose, buffer, color, length, radius, spin, scroll, length));
            return;
        }

        float fade = 1 - Mth.clamp((ticks - FallenBishop.CHARGE_TICKS) / FallenBishop.LINGER_TICKS, 0, 1);
        int flareColor = ARGB.color((int) (FLARE_ALPHA * fade), LINE_COLOR);
        BeamTube.submitCoreAndFlare(poseStack, collector, BEAM, length, spin, scroll, CORE_COLOR, CORE_RADIUS * fade, flareColor, FLARE_RADIUS * fade);
    }
}
