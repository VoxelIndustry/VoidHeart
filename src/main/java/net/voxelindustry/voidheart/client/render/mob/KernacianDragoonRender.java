package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.model.mob.KernacianDragoonModel;
import net.voxelindustry.voidheart.client.render.state.KernacianDragoonRenderState;
import net.voxelindustry.voidheart.common.content.mob.KernacianDragoon;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The beam is a white-hot core inside a wide violet flare, both flickering in width so that it reads as fire rather than a laser, widening in as it starts and
 * thinning out as it ends.
 */
public class KernacianDragoonRender extends MobRenderer<KernacianDragoon, KernacianDragoonRenderState, KernacianDragoonModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/kernacian_dragoon.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/kernacian_dragoon_glow.png");
    private static final Identifier BEAM = Identifier.withDefaultNamespace("textures/entity/beacon/beacon_beam.png");

    private static final float MODEL_SCALE = 0.9F;
    private static final int CORE_COLOR = 0xFFFBEFFF;
    private static final int FLARE_COLOR = 0x8A3DF0;
    private static final int FLARE_ALPHA = 170;
    private static final float CORE_RADIUS = 0.09F;
    private static final float FLARE_RADIUS = 0.3F;
    private static final float FLICKER = 0.18F;
    private static final float FLICKER_RATE = 2.3F;
    private static final float EASE_TICKS = 4;

    public KernacianDragoonRender(EntityRendererProvider.Context context)
    {
        super(context, new KernacianDragoonModel(context.bakeLayer(KernacianDragoonModel.LAYER)), 0.8F);
        addLayer(new VoidGlowLayer<>(this, GLOW));
    }

    @Override
    public KernacianDragoonRenderState createRenderState()
    {
        return new KernacianDragoonRenderState();
    }

    @Override
    public Identifier getTextureLocation(KernacianDragoonRenderState state)
    {
        return TEXTURE;
    }

    @Override
    protected void scale(KernacianDragoonRenderState state, PoseStack poseStack)
    {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public boolean shouldRender(KernacianDragoon dragoon, Frustum culler, double camX, double camY, double camZ, float partialTicks)
    {
        if (super.shouldRender(dragoon, culler, camX, camY, camZ, partialTicks))
            return true;
        return dragoon.isFiring() && culler.isVisible(new AABB(dragoon.beamOrigin(partialTicks), dragoon.beamEnd()).inflate(0.5));
    }

    @Override
    public void extractRenderState(KernacianDragoon dragoon, KernacianDragoonRenderState state, float partialTicks)
    {
        super.extractRenderState(dragoon, state, partialTicks);
        int beam = dragoon.beamTicks();
        KernacianDragoonModel.open(state, beam, dragoon.closingTicks, partialTicks);
        int kick = dragoon.kickTicks();
        state.kick = kick > 0 ? kick + partialTicks : 0;
        for (int i = 0; i < state.grown.length; i++)
            state.grown[i] = Mth.lerp(partialTicks, dragoon.grownO[i], dragoon.grown[i]);

        if (dragoon.isFiring())
        {
            var at = dragoon.getPosition(partialTicks);
            state.beamFrom = dragoon.beamOrigin(partialTicks).subtract(at);
            state.beamTo = dragoon.beamEnd().subtract(at);
            state.beamTicks = beam - KernacianDragoon.BEAM_CHARGE_TICKS + partialTicks;
        }
        else
        {
            state.beamFrom = null;
            state.beamTo = null;
        }
    }

    @Override
    public void submit(KernacianDragoonRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        super.submit(state, poseStack, collector, camera);
        if (state.beamFrom == null || state.beamTo == null)
            return;

        poseStack.pushPose();
        poseStack.translate(state.beamFrom.x, state.beamFrom.y, state.beamFrom.z);
        submitBeam(poseStack, collector, state.beamTo.subtract(state.beamFrom), state.beamTicks);
        poseStack.popPose();
    }

    private static void submitBeam(PoseStack poseStack, SubmitNodeCollector collector, Vec3 beam, float ticks)
    {
        float length = (float) beam.length();
        if (length < 0.01F)
            return;
        var direction = beam.scale(1 / length);
        poseStack.rotate(Axis.YP.rotation(Mth.HALF_PI - (float) Mth.atan2(direction.z, direction.x)));
        poseStack.rotate(Axis.XP.rotation((float) Math.acos(Mth.clamp(direction.y, -1, 1))));

        float rise = Mth.clamp(ticks / EASE_TICKS, 0, 1);
        float fall = Mth.clamp((KernacianDragoon.BEAM_FIRE_TICKS - ticks) / EASE_TICKS, 0, 1);
        float width = Math.min(rise, fall) * (1 + Mth.sin(ticks * FLICKER_RATE) * FLICKER * 0.5F + Mth.sin(ticks * FLICKER_RATE * 2.7F) * FLICKER * 0.5F);
        float coreRadius = CORE_RADIUS * width;
        float flareRadius = FLARE_RADIUS * width;
        int flareColor = ARGB.color(FLARE_ALPHA, FLARE_COLOR);
        float scroll = Mth.frac(-ticks * 0.3F);
        float spin = ticks * 0.4F;

        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BEAM, false),
                (pose, buffer) -> tube(pose, buffer, CORE_COLOR, length, coreRadius, spin, scroll, length * 2));
        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BEAM, true),
                (pose, buffer) -> tube(pose, buffer, flareColor, length, flareRadius, -spin, scroll, length));
    }

    /**
     * A square tube along +y, turned around its axis by the given angle.
     */
    private static void tube(PoseStack.Pose pose, VertexConsumer buffer, int color, float length, float radius, float angle, float vStart, float vLength)
    {
        for (int side = 0; side < 4; side++)
        {
            float a0 = angle + side * Mth.HALF_PI;
            float a1 = a0 - Mth.HALF_PI;
            float x0 = Mth.cos(a0) * radius;
            float z0 = Mth.sin(a0) * radius;
            float x1 = Mth.cos(a1) * radius;
            float z1 = Mth.sin(a1) * radius;

            vertex(pose, buffer, color, x0, length, z0, 1, vStart + vLength);
            vertex(pose, buffer, color, x0, 0, z0, 1, vStart);
            vertex(pose, buffer, color, x1, 0, z1, 0, vStart);
            vertex(pose, buffer, color, x1, length, z1, 0, vStart + vLength);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int color, float x, float y, float z, float u, float v)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }
}
