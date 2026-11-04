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
import net.voxelindustry.voidheart.common.content.mob.VoidOrb;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * One camera facing quad that ignores world light, so its teal rim shows in the dark around a black core. A single quad never overlaps itself.
 */
public class VoidOrbRender extends EntityRenderer<VoidOrb, EntityRenderState>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/void_orb.png");
    private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucentEmissive(TEXTURE);

    private static final float RADIUS = 0.42F;
    private static final float PULSE_RATE = 0.4F;
    private static final float PULSE_RANGE = 0.05F;

    public VoidOrbRender(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState()
    {
        return new EntityRenderState();
    }

    @Override
    protected int getBlockLightLevel(VoidOrb orb, BlockPos pos)
    {
        return 15;
    }

    @Override
    public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        float radius = RADIUS + Mth.sin(state.ageInTicks * PULSE_RATE) * PULSE_RANGE;

        poseStack.pushPose();
        poseStack.translate(0, state.boundingBoxHeight / 2, 0);
        poseStack.rotate(camera.orientation);
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) ->
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
