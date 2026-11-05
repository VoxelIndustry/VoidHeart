package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * One camera facing quad that ignores world light, so it shows in the dark. A single quad never overlaps itself.
 */
public class GlowQuadRender<T extends Entity> extends EntityRenderer<T, EntityRenderState>
{
    private static final float PULSE_RATE = 0.4F;
    private static final float PULSE_SHARE = 0.12F;

    private final RenderType renderType;
    private final float baseRadius;

    public GlowQuadRender(EntityRendererProvider.Context context, Identifier texture, float radius)
    {
        super(context);
        renderType = RenderTypes.entityTranslucentEmissive(texture);
        baseRadius = radius;
    }

    @Override
    public EntityRenderState createRenderState()
    {
        return new EntityRenderState();
    }

    @Override
    protected int getBlockLightLevel(T entity, BlockPos pos)
    {
        return 15;
    }

    @Override
    public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        float radius = baseRadius * (1 + Mth.sin(state.ageInTicks * PULSE_RATE) * PULSE_SHARE);

        poseStack.pushPose();
        poseStack.translate(0, state.boundingBoxHeight / 2, 0);
        poseStack.rotate(camera.orientation);
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) ->
        {
            vertex(pose, buffer, -radius, -radius, 0, 1);
            vertex(pose, buffer, radius, -radius, 1, 1);
            vertex(pose, buffer, radius, radius, 1, 0);
            vertex(pose, buffer, -radius, radius, 0, 0);
        });
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float u, float v)
    {
        buffer.addVertex(pose, x, y, 0)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }
}
