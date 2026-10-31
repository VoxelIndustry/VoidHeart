package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
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
 * With nothing around, it slowly searches the horizon.
 */
public class BeholderRender implements BlockEntityRenderer<BeholderTile, BeholderRender.BeholderRenderState>
{
    private static final Identifier EYE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/beholder_eye.png");
    private static final Identifier EYE_GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/beholder_eye_glow.png");

    private static final double WATCH_RANGE = 12;
    private static final float EYE_HEIGHT = 12 / 16F;
    private static final float HALF_SIZE = 3 / 16F;
    private static final float TURN_RATE = 5;

    public static class BeholderRenderState extends BlockEntityRenderState
    {
        public float yaw;
        public float pitch;
        public float bob;
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

        float wantedYaw;
        float wantedPitch;
        var watched = level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(WATCH_RANGE), OculusEntityPayloads::isShown).stream()
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(eye)))
                .orElse(null);
        if (watched != null)
        {
            var toward = watched.getEyePosition(partialTicks).subtract(eye);
            wantedYaw = (float) Mth.atan2(toward.x, toward.z);
            wantedPitch = (float) Mth.atan2(toward.y, toward.horizontalDistance());
        }
        else
        {
            wantedYaw = time * 0.01F + Mth.sin(time * 0.023F) * 0.8F;
            wantedPitch = Mth.sin(time * 0.017F) * 0.25F;
        }

        long now = System.nanoTime();
        float seconds = beholder.lastEyeNanos == 0 ? 1 : Math.min((now - beholder.lastEyeNanos) / 1E9F, 0.25F);
        beholder.lastEyeNanos = now;
        float ease = 1 - (float) Math.exp(-TURN_RATE * seconds);
        beholder.eyeYaw += Mth.wrapDegrees((wantedYaw - beholder.eyeYaw) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * ease;
        beholder.eyePitch += (wantedPitch - beholder.eyePitch) * ease;

        state.yaw = beholder.eyeYaw;
        state.pitch = beholder.eyePitch;
        state.bob = Mth.sin(time * 0.08F) * 0.03F;
    }

    @Override
    public void submit(BeholderRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera)
    {
        matrices.pushPose();
        matrices.translate(0.5, EYE_HEIGHT + state.bob, 0.5);
        matrices.rotate(new Quaternionf().rotationY(state.yaw).rotateX(-state.pitch));

        int light = state.lightCoords;
        collector.submitCustomGeometry(matrices, RenderTypes.entityCutout(EYE), (pose, buffer) -> cube(pose, buffer, light));
        // The iris shines on its own, whatever the light around
        collector.submitCustomGeometry(matrices, RenderTypes.eyes(EYE_GLOW), (pose, buffer) -> cube(pose, buffer, light));
        matrices.popPose();
    }

    /**
     * The eye looks along +z: its front takes the left half of the texture, every other face the right half.
     */
    private static void cube(PoseStack.Pose pose, VertexConsumer buffer, int light)
    {
        float s = HALF_SIZE;
        face(pose, buffer, light, 0, 0, 1, -s, -s, s, s, -s, s, s, s, s, -s, s, s, 0F);
        face(pose, buffer, light, 0, 0, -1, s, -s, -s, -s, -s, -s, -s, s, -s, s, s, -s, 0.5F);
        face(pose, buffer, light, 1, 0, 0, s, -s, s, s, -s, -s, s, s, -s, s, s, s, 0.5F);
        face(pose, buffer, light, -1, 0, 0, -s, -s, -s, -s, -s, s, -s, s, s, -s, s, -s, 0.5F);
        face(pose, buffer, light, 0, 1, 0, -s, s, s, s, s, s, s, s, -s, -s, s, -s, 0.5F);
        face(pose, buffer, light, 0, -1, 0, -s, -s, -s, s, -s, -s, s, -s, s, -s, -s, s, 0.5F);
    }

    /**
     * A quad counter-clockwise from its bottom left corner as seen from outside.
     */
    private static void face(PoseStack.Pose pose, VertexConsumer buffer, int light, float normalX, float normalY, float normalZ,
                             float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                             float u0)
    {
        vertex(pose, buffer, light, x0, y0, z0, u0, 1, normalX, normalY, normalZ);
        vertex(pose, buffer, light, x1, y1, z1, u0 + 0.5F, 1, normalX, normalY, normalZ);
        vertex(pose, buffer, light, x2, y2, z2, u0 + 0.5F, 0, normalX, normalY, normalZ);
        vertex(pose, buffer, light, x3, y3, z3, u0, 0, normalX, normalY, normalZ);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int light, float x, float y, float z, float u, float v, float normalX, float normalY, float normalZ)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, normalX, normalY, normalZ);
    }
}
