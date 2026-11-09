package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Util;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.beholder.BeholderTile;
import net.voxelindustry.voidheart.common.content.oculus.OculusEntityPayloads;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The floating eye of a beholder, bobbing over its pedestal and turning to watch whatever lives nearest.
 * With nothing around, it slowly searches the horizon. Short of ley its iris dims and it only notices what is close, and blind it drowses off:
 * its gaze sinks to the pedestal, it settles a little lower and its bob slows.
 */
public class BeholderRender implements BlockEntityRenderer<BeholderTile, BeholderRender.BeholderRenderState>
{
    private static final Identifier EYE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/beholder_eye.png");
    private static final Identifier EYE_GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/beholder_eye_glow.png");

    private static final double WATCH_RANGE = 12;
    private static final float EYE_HEIGHT = 12 / 16F;
    private static final float HALF_SIZE = 3 / 16F;
    private static final float TURN_RATE = 5;
    private static final float BOB_SPEED  = 0.08F;
    private static final float BOB_HEIGHT = 0.03F;

    /** Ticks the iris takes to fade out as the ley fails, and to shine again once it is back. */
    private static final float GLOW_OUT_TICKS = 20;
    private static final float GLOW_IN_TICKS  = 10;
    /** Below this the iris is not drawn at all. */
    private static final float GLOW_VISIBLE   = 0.01F;
    /** Where a blind eye looks: down toward its pedestal, in radians. */
    private static final float DROWSE_PITCH     = -35 * Mth.DEG_TO_RAD;
    private static final float DROWSE_SINK      = 1 / 16F;
    private static final float DROWSE_BOB_SPEED = 1 / 3F;
    /** A drowsing gaze sinks slower than an awake eye turns. */
    private static final float DROWSE_TURN_RATE = 1.5F;
    /** Longest step the easing takes in one frame, so an eye coming back into view settles rather than jumping. */
    private static final float MAX_STEP_TICKS   = 20;

    public static class BeholderRenderState extends BlockEntityRenderState
    {
        public float yaw;
        public float pitch;
        public float bob;
        public float glow;
    }

    public BeholderRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public BeholderRenderState createRenderState()
    {
        return new BeholderRenderState();
    }

    @Override
    public void extractRenderState(BeholderTile beholder, BeholderRenderState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(beholder, state, partialTicks, cameraPosition, breakProgress);

        var level = beholder.getLevel();
        float time = level.getGameTime() + partialTicks;
        var eye = Vec3.atBottomCenterOf(beholder.getBlockPos()).add(0, EYE_HEIGHT, 0);

        float sight = beholder.sight();
        boolean blind = sight <= 0;
        float ticks = Float.isNaN(beholder.lastLookTime) ? 0 : Mth.clamp(time - beholder.lastLookTime, 0, MAX_STEP_TICKS);
        beholder.lastLookTime = time;
        if (Float.isNaN(beholder.glow))
        {
            beholder.glow = sight;
            beholder.drowse = blind ? 1 : 0;
        }
        beholder.glow = approach(beholder.glow, sight, ticks);
        beholder.drowse = approach(beholder.drowse, blind ? 1 : 0, ticks);

        float wantedYaw;
        float wantedPitch;
        var watched = blind ? null : watchedBy(level, eye, WATCH_RANGE * sight);
        if (watched != null)
        {
            var toward = watched.getEyePosition(partialTicks).subtract(eye);
            wantedYaw = (float) Mth.atan2(toward.x, toward.z);
            wantedPitch = (float) Mth.atan2(toward.y, toward.horizontalDistance());
        }
        else if (blind)
        {
            wantedYaw = beholder.eyeYaw;
            wantedPitch = DROWSE_PITCH;
        }
        else
        {
            wantedYaw = time * 0.01F + Mth.sin(time * 0.023F) * 0.8F;
            wantedPitch = Mth.sin(time * 0.017F) * 0.25F;
        }

        long now = Util.getNanos();
        float seconds = beholder.lastEyeNanos == 0 ? 1 : Math.min((now - beholder.lastEyeNanos) / 1E9F, 0.25F);
        beholder.lastEyeNanos = now;
        float ease = 1 - (float) Math.exp(-Mth.lerp(beholder.drowse, TURN_RATE, DROWSE_TURN_RATE) * seconds);
        beholder.eyeYaw += Mth.wrapDegrees((wantedYaw - beholder.eyeYaw) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * ease;
        beholder.eyePitch += (wantedPitch - beholder.eyePitch) * ease;

        beholder.bobPhase = (beholder.bobPhase + ticks * BOB_SPEED * Mth.lerp(beholder.drowse, 1, DROWSE_BOB_SPEED)) % Mth.TWO_PI;

        state.yaw = beholder.eyeYaw;
        state.pitch = beholder.eyePitch;
        state.bob = Mth.sin(beholder.bobPhase) * BOB_HEIGHT - beholder.drowse * DROWSE_SINK;
        state.glow = beholder.glow;
    }

    /**
     * The nearest shown creature within reach of the eye, a reach that shrinks with the ley it gets.
     */
    private static @Nullable LivingEntity watchedBy(Level level, Vec3 eye, double range)
    {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(range), OculusEntityPayloads::isShown).stream()
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(eye)))
                .orElse(null);
    }

    /**
     * Moves toward a value, a full swing down taking GLOW_OUT_TICKS and a full swing up GLOW_IN_TICKS.
     */
    private static float approach(float from, float to, float ticks)
    {
        return to < from ? Math.max(to, from - ticks / GLOW_OUT_TICKS) : Math.min(to, from + ticks / GLOW_IN_TICKS);
    }

    @Override
    public void submit(BeholderRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera)
    {
        matrices.pushPose();
        matrices.translate(0.5, EYE_HEIGHT + state.bob, 0.5);
        matrices.rotate(new Quaternionf().rotationY(state.yaw).rotateX(-state.pitch));

        int light = state.lightCoords;
        collector.submitCustomGeometry(matrices, RenderTypes.entityCutout(EYE), (pose, buffer) -> cube(pose, buffer, light, 0xFFFFFFFF));
        // The iris shines on its own, whatever the light around, as bright as the ley lets it see
        if (state.glow > GLOW_VISIBLE)
        {
            int glow = ARGB.white(state.glow);
            collector.submitCustomGeometry(matrices, RenderTypes.eyes(EYE_GLOW), (pose, buffer) -> cube(pose, buffer, light, glow));
        }
        matrices.popPose();
    }

    /**
     * The eye looks along +z: its front takes the left half of the texture, every other face the right half.
     */
    private static void cube(PoseStack.Pose pose, VertexConsumer buffer, int light, int color)
    {
        float s = HALF_SIZE;
        face(pose, buffer, light, color, 0, 0, 1, -s, -s, s, s, -s, s, s, s, s, -s, s, s, 0F);
        face(pose, buffer, light, color, 0, 0, -1, s, -s, -s, -s, -s, -s, -s, s, -s, s, s, -s, 0.5F);
        face(pose, buffer, light, color, 1, 0, 0, s, -s, s, s, -s, -s, s, s, -s, s, s, s, 0.5F);
        face(pose, buffer, light, color, -1, 0, 0, -s, -s, -s, -s, -s, s, -s, s, s, -s, s, -s, 0.5F);
        face(pose, buffer, light, color, 0, 1, 0, -s, s, s, s, s, s, s, s, -s, -s, s, -s, 0.5F);
        face(pose, buffer, light, color, 0, -1, 0, -s, -s, -s, s, -s, -s, s, -s, s, -s, -s, s, 0.5F);
    }

    /**
     * A quad counter-clockwise from its bottom left corner as seen from outside.
     */
    private static void face(PoseStack.Pose pose, VertexConsumer buffer, int light, int color, float normalX, float normalY, float normalZ,
                             float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                             float u0)
    {
        vertex(pose, buffer, light, color, x0, y0, z0, u0, 1, normalX, normalY, normalZ);
        vertex(pose, buffer, light, color, x1, y1, z1, u0 + 0.5F, 1, normalX, normalY, normalZ);
        vertex(pose, buffer, light, color, x2, y2, z2, u0 + 0.5F, 0, normalX, normalY, normalZ);
        vertex(pose, buffer, light, color, x3, y3, z3, u0, 0, normalX, normalY, normalZ);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int light, int color, float x, float y, float z, float u, float v,
                               float normalX, float normalY, float normalZ)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, normalX, normalY, normalZ);
    }
}
