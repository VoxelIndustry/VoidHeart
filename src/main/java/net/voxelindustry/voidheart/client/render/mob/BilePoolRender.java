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
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.content.sanctum.BilePool;
import net.voxelindustry.voidheart.common.content.sanctum.SanctumBalance;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * One flat quad of bile lying a hair over the floor, faintly lit from within, turning slowly so it seems to seethe. Pools that overlap each sit
 * at their own height so they never fight for depth.
 */
public class BilePoolRender extends EntityRenderer<BilePool, BilePoolRender.State>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/bile_pool.png");
    private static final RenderType TYPE = RenderTypes.entityTranslucentEmissive(TEXTURE);
    private static final float LIFT = 0.012F;
    private static final float LIFT_STEP = 0.003F;
    private static final int LIFT_STEPS = 6;
    private static final float TURN_SPEED = 0.6F;
    private static final int ALPHA = 220;
    private static final int GLOW_LIGHT = LightCoordsUtil.pack(10, 10);

    public static class State extends EntityRenderState
    {
        float spread;
        float turn;
        float lift;
    }

    public BilePoolRender(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(BilePool pool, State state, float partialTicks)
    {
        super.extractRenderState(pool, state, partialTicks);
        float age = pool.tickCount + partialTicks;
        state.spread = BilePool.spread(age);
        state.turn = age * TURN_SPEED + pool.getId() * 37;
        state.lift = LIFT + Math.floorMod(pool.getId(), LIFT_STEPS) * LIFT_STEP;
    }

    @Override
    protected AABB getBoundingBoxForCulling(BilePool pool, float partialTicks)
    {
        return super.getBoundingBoxForCulling(pool, partialTicks).inflate(SanctumBalance.POOL_RADIUS);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        super.submit(state, poseStack, collector, camera);
        if (state.spread <= 0)
            return;
        float half = (float) SanctumBalance.POOL_RADIUS * state.spread;
        poseStack.pushPose();
        poseStack.translate(0, state.lift, 0);
        poseStack.rotate(MathUtil.quatFromAngleDegrees(state.turn, MathUtil.POSITIVE_Y));
        int light = Math.max(state.lightCoords, GLOW_LIGHT);
        int color = ARGB.color((int) (ALPHA * Mth.clamp(state.spread * 1.5F, 0, 1)), 0xFFFFFF);
        collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) ->
        {
            vertex(pose, buffer, -half, -half, 0, 0, color, light);
            vertex(pose, buffer, -half, half, 0, 1, color, light);
            vertex(pose, buffer, half, half, 1, 1, color, light);
            vertex(pose, buffer, half, -half, 1, 0, color, light);
        });
        poseStack.popPose();
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
