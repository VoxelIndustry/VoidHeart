package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.client.render.state.WraithStainRenderState;
import net.voxelindustry.voidheart.common.content.mob.WraithStain;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * One alpha blended quad a hair off the block face, which WraithStain's position keeps clear of z-fighting. Nothing else draws on that plane
 * and it does not overlap itself, so depth writes are harmless. Two rotating copies would cost little more but read as spinning, not spreading.
 */
public class WraithStainRender extends EntityRenderer<WraithStain, WraithStainRenderState>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/wraith_stain.png");
    private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucent(TEXTURE);

    private static final float START_SCALE = 0.12F;
    private static final float PULSE_RATE = 0.22F;
    private static final float PULSE_RANGE = 0.03F;
    private static final int FULL_ALPHA = 240;

    public WraithStainRender(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public WraithStainRenderState createRenderState()
    {
        return new WraithStainRenderState();
    }

    @Override
    public void extractRenderState(WraithStain stain, WraithStainRenderState state, float partialTicks)
    {
        super.extractRenderState(stain, state, partialTicks);
        state.face = stain.face();
    }

    @Override
    protected AABB getBoundingBoxForCulling(WraithStain stain, float partialTicks)
    {
        return super.getBoundingBoxForCulling(stain, partialTicks).inflate(WraithStain.RADIUS);
    }

    @Override
    public void submit(WraithStainRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        float age = state.ageInTicks;
        float growth = Mth.clamp(age / WraithStain.GROW_TICKS, 0, 1);
        float eased = 1 - (1 - growth) * (1 - growth);
        float fade = 1 - Mth.clamp((age - WraithStain.GROW_TICKS - WraithStain.LINGER_TICKS) / WraithStain.FADE_TICKS, 0, 1);
        float radius = WraithStain.RADIUS * (Mth.lerp(eased, START_SCALE, 1) + Mth.sin(age * PULSE_RATE) * PULSE_RANGE);
        int color = ARGB.color((int) (FULL_ALPHA * fade), 0xFFFFFF);
        int light = state.lightCoords;

        poseStack.pushPose();
        poseStack.rotate(state.face.getRotation());
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) ->
        {
            vertex(pose, buffer, -radius, -radius, 0, 0, color, light);
            vertex(pose, buffer, -radius, radius, 0, 1, color, light);
            vertex(pose, buffer, radius, radius, 1, 1, color, light);
            vertex(pose, buffer, radius, -radius, 1, 0, color, light);
        });
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float z, float u, float v, int color, int light)
    {
        buffer.addVertex(pose, x, 0, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0, 1, 0);
    }
}
