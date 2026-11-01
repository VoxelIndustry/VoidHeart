package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.model.ShoulderEyeModel;
import net.voxelindustry.voidheart.client.render.state.ShoulderEyeRenderState;
import net.voxelindustry.voidheart.common.content.shouldereye.ShoulderEyeEntity;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Draws the eye where its owner is this frame rather than where the last position update put it, so it never lags behind their shoulder.
 * Hidden from its owner in first person, though its beam still shows.
 */
public class ShoulderEyeRender extends MobRenderer<ShoulderEyeEntity, ShoulderEyeRenderState, ShoulderEyeModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/shoulder_eye.png");
    private static final Identifier GLOW    = Identifier.fromNamespaceAndPath(MODID, "textures/entity/shoulder_eye_glow.png");
    private static final Identifier BEAM    = Identifier.withDefaultNamespace("textures/entity/beacon/beacon_beam.png");

    private static final int BEAM_CORE_CHARGING = 0xFF5CE65C;
    private static final int BEAM_CORE_FIRING   = 0xFFD4FFB0;
    private static final int BEAM_GLOW          = 0x39E05A;

    public ShoulderEyeRender(EntityRendererProvider.Context context)
    {
        super(context, new ShoulderEyeModel(context.bakeLayer(ShoulderEyeModel.LAYER)), 0);
        addLayer(new EyesLayer<>(this)
        {
            @Override
            public RenderType renderType()
            {
                return RenderTypes.eyes(GLOW);
            }
        });
    }

    @Override
    public ShoulderEyeRenderState createRenderState()
    {
        return new ShoulderEyeRenderState();
    }

    @Override
    public Identifier getTextureLocation(ShoulderEyeRenderState state)
    {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(ShoulderEyeEntity eye, Frustum culler, double camX, double camY, double camZ, float partialTicks)
    {
        if (super.shouldRender(eye, culler, camX, camY, camZ, partialTicks))
            return true;

        var target = eye.getBeamTarget();
        return target != null && culler.isVisible(new AABB(eye.getPosition(partialTicks), target.getPosition(partialTicks)).inflate(0.5));
    }

    @Override
    public void extractRenderState(ShoulderEyeEntity eye, ShoulderEyeRenderState state, float partialTicks)
    {
        super.extractRenderState(eye, state, partialTicks);

        state.renderOffset = Vec3.ZERO;
        state.hiddenToCamera = false;
        var owner = eye.getOwnerOnClient();
        if (owner != null && eye.isPerched() && eye.isAlive())
        {
            state.renderOffset = ShoulderEyeEntity.shoulderPosition(owner, partialTicks).subtract(state.x, state.y, state.z);

            var minecraft = Minecraft.getInstance();
            state.hiddenToCamera = owner == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
        }

        float age = state.ageInTicks;
        state.bob = Mth.sin(age * 0.09F) * 0.045F + Mth.sin(age * 0.23F) * 0.012F;
        state.bodyRot = Mth.rotLerp(partialTicks, eye.lookYawO, eye.lookYaw);
        state.yRot = 0;
        state.xRot = Mth.lerp(partialTicks, eye.lookPitchO, eye.lookPitch);
        state.blink = eye.blinkTicks > 0 ? Mth.sin(Mth.PI * Math.max(eye.blinkTicks - partialTicks, 0) / 4F) : 0;

        // Small darting of the iris, as if it kept scanning around what it looks at
        float dart = Mth.floor(age / 17) * 12.9898F;
        state.pupilX = Mth.sin(dart) * 0.45F;
        state.pupilY = Mth.cos(dart * 1.7F) * 0.35F;

        var target = eye.getBeamTarget();
        if (target != null && eye.isAlive())
        {
            state.beamFrom = new Vec3(state.x, state.y, state.z).add(state.renderOffset).add(0, ShoulderEyeEntity.SIZE / 2 + state.bob, 0);
            state.beamTo = target.getPosition(partialTicks).add(0, target.getBbHeight() / 2, 0);
            state.beamTicks = eye.clientBeamTicks + partialTicks;
            state.pupilX = 0;
            state.pupilY = 0;
        }
        else
        {
            state.beamFrom = null;
            state.beamTo = null;
        }
    }

    @Override
    public Vec3 getRenderOffset(ShoulderEyeRenderState state)
    {
        return state.renderOffset;
    }

    @Override
    protected boolean isBodyVisible(ShoulderEyeRenderState state)
    {
        return !state.hiddenToCamera && super.isBodyVisible(state);
    }

    @Override
    protected boolean shouldRenderLayers(ShoulderEyeRenderState state)
    {
        return !state.hiddenToCamera && !state.isInvisible;
    }

    @Override
    public void submit(ShoulderEyeRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        poseStack.pushPose();
        poseStack.translate(0, state.bob, 0);
        super.submit(state, poseStack, collector, camera);
        poseStack.popPose();

        if (state.beamFrom != null && state.beamTo != null)
        {
            poseStack.pushPose();
            poseStack.translate(0, ShoulderEyeEntity.SIZE / 2 + state.bob, 0);
            submitBeam(poseStack, collector, state.beamTo.subtract(state.beamFrom), state.beamTicks);
            poseStack.popPose();
        }
    }

    /**
     * A thin beam thickening while the eye charges, then flaring when it strikes.
     */
    private static void submitBeam(PoseStack poseStack, SubmitNodeCollector collector, Vec3 beam, float ticks)
    {
        float length = (float) beam.length();
        if (length < 0.01F)
            return;
        var direction = beam.scale(1 / length);

        poseStack.rotate(Axis.YP.rotation(Mth.HALF_PI - (float) Mth.atan2(direction.z, direction.x)));
        poseStack.rotate(Axis.XP.rotation((float) Math.acos(Mth.clamp(direction.y, -1, 1))));

        float charge = Mth.clamp(ticks / ShoulderEyeEntity.CHARGE_TICKS, 0, 1);
        boolean firing = ticks >= ShoulderEyeEntity.CHARGE_TICKS;
        float flare = firing ? 1 - Mth.clamp((ticks - ShoulderEyeEntity.CHARGE_TICKS) / ShoulderEyeEntity.LINGER_TICKS, 0, 1) : 0;

        float coreRadius = 0.012F + 0.02F * charge + 0.05F * flare;
        float glowRadius = 0.035F + 0.05F * charge + 0.11F * flare;
        int coreColor = firing ? BEAM_CORE_FIRING : BEAM_CORE_CHARGING;
        int glowColor = ARGB.color((int) (50 + 90 * Math.max(charge * 0.5F, flare)), BEAM_GLOW);

        float spin = ticks * 0.35F;
        float scroll = Mth.frac(-ticks * 0.15F);
        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BEAM, false),
                (pose, buffer) -> tube(pose, buffer, coreColor, length, coreRadius, spin, scroll, length * 2));
        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BEAM, true),
                (pose, buffer) -> tube(pose, buffer, glowColor, length, glowRadius, -spin, scroll, length));
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
