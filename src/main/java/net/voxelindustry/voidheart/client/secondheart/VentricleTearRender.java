package net.voxelindustry.voidheart.client.secondheart;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.client.render.WraithInk;
import net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleTear;

/**
 * A tear the Twin Ventricle's beat cuts: a tall slit turned to the viewer, opening from a line to a narrow lens, the realm's smoke drifting in
 * it behind a thin glowing rim, then zipping shut from both ends toward its middle.
 * <p>
 * Render types: the smoke in vanilla's emissive translucency, the rim in {@link VoidHeartRenderTypes#GLOW}, which overlaps itself where the two
 * edges meet. Per frame: {@link #SEGMENTS} quads of smoke and twice that of rim per tear.
 */
public class VentricleTearRender extends EntityRenderer<VentricleTear, VentricleTearRender.State>
{
    private static final RenderType SMOKE_TYPE = RenderTypes.entityTranslucentEmissive(WraithInk.SMOKE);

    private static final int   SEGMENTS    = 16;
    private static final float HEIGHT      = 2.6F;
    private static final float HALF_WIDTH  = 0.42F;
    private static final float RIM_WIDTH   = 0.045F;
    private static final int   SMOKE_RGB   = 0x6A3FA8;
    private static final float SMOKE_ALPHA = 0.95F;
    private static final int   RIM_RGB     = 0xE8D4FF;
    /** The smoke drifts up through the slit, in texture repeats per tick. */
    private static final float DRIFT       = 0.01F;
    /** The slit's edge wavers a little, so it reads as torn rather than cut. */
    private static final float WAVER       = 0.12F;

    public static class State extends EntityRenderState
    {
        float age;
        int   seed;
    }

    public VentricleTearRender(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(VentricleTear tear, State state, float partialTicks)
    {
        super.extractRenderState(tear, state, partialTicks);
        state.age = tear.tickCount + partialTicks;
        state.seed = tear.getId();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        super.submit(state, poseStack, collector, camera);
        float open = VentricleTear.opennessAt(state.age);
        float length = VentricleTear.lengthAt(state.age);
        if (length <= 0)
            return;

        // Turned about the vertical to face the viewer
        double dx = camera.pos.x - state.x, dz = camera.pos.z - state.z;
        float yaw = (float) Mth.atan2(dx, dz);
        poseStack.pushPose();
        poseStack.rotate(Axis.YP.rotation(yaw));

        float from = 0.5F - length / 2, to = 0.5F + length / 2;
        float scroll = state.age * DRIFT;
        collector.submitCustomGeometry(poseStack, SMOKE_TYPE, (pose, buffer) ->
        {
            int color = ARGB.color(SMOKE_ALPHA, SMOKE_RGB);
            for (int i = 0; i < SEGMENTS; i++)
            {
                float s0 = Mth.lerp(i / (float) SEGMENTS, from, to), s1 = Mth.lerp((i + 1) / (float) SEGMENTS, from, to);
                float w0 = width(s0, open, state.seed), w1 = width(s1, open, state.seed);
                float y0 = s0 * HEIGHT, y1 = s1 * HEIGHT;
                smoke(buffer, pose, -w0, y0, s0 + scroll, 0.5F - w0, color);
                smoke(buffer, pose, w0, y0, s0 + scroll, 0.5F + w0, color);
                smoke(buffer, pose, w1, y1, s1 + scroll, 0.5F + w1, color);
                smoke(buffer, pose, -w1, y1, s1 + scroll, 0.5F - w1, color);
            }
        });
        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            int color = ARGB.color(Math.min(1, 0.4F + open), RIM_RGB);
            for (int i = 0; i < SEGMENTS; i++)
            {
                float s0 = Mth.lerp(i / (float) SEGMENTS, from, to), s1 = Mth.lerp((i + 1) / (float) SEGMENTS, from, to);
                float w0 = width(s0, open, state.seed), w1 = width(s1, open, state.seed);
                float y0 = s0 * HEIGHT, y1 = s1 * HEIGHT;
                for (int side : new int[]{-1, 1})
                {
                    rim(buffer, pose, side * w0, y0, side * w1, y1, color);
                }
            }
        });
        poseStack.popPose();
    }

    /**
     * Half the slit's width at a point along its height: a lens, zero at both tips, with a slight waver.
     */
    private static float width(float along, float open, int seed)
    {
        float lens = Mth.sin(Mth.PI * Mth.clamp(along, 0, 1));
        float waver = 1 + WAVER * Mth.sin(along * 23 + seed * 1.7F);
        return HALF_WIDTH * open * lens * lens * waver;
    }

    private static void smoke(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float v, float u, int color)
    {
        buffer.addVertex(pose, x, y, 0).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 0, 1);
    }

    private static void rim(VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0, float x1, float y1, int color)
    {
        buffer.addVertex(pose, x0 - RIM_WIDTH, y0, 0.002F).setColor(color);
        buffer.addVertex(pose, x0 + RIM_WIDTH, y0, 0.002F).setColor(color);
        buffer.addVertex(pose, x1 + RIM_WIDTH, y1, 0.002F).setColor(color);
        buffer.addVertex(pose, x1 - RIM_WIDTH, y1, 0.002F).setColor(color);
    }
}
