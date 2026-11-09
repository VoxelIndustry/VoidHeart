package net.voxelindustry.voidheart.client.render.energy.ley;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.energy.ley.LeyGrowerTile;
import org.jspecify.annotations.Nullable;

/**
 * Ley pooled in the grower's basin like water: a faint glow in its depth and a surface whose light ripples outward from the middle, as bright as its supply.
 * Additive over the dark basin floor, which nothing else covers. Two layers of 6 × 6 quads, rebuilt every frame.
 */
public class LeyGrowerRender implements BlockEntityRenderer<LeyGrowerTile, LeyGrowerRender.State>
{
    // In model pixels, matching models/block/ley_grower.json
    private static final float POOL_MIN     = 4;
    private static final float POOL_MAX     = 12;
    private static final float SURFACE      = 6.5F;
    private static final float DEPTH        = 3.5F;
    private static final int   GRID         = 6;

    private static final float SURFACE_ALPHA = 0.7F;
    private static final float DEPTH_ALPHA   = 0.3F;
    private static final float RIPPLE        = 0.35F;
    private static final float RIPPLE_WAVES  = 2.2F;
    private static final float RIPPLE_SPEED  = 0.09F;
    private static final float RIPPLE_HEIGHT = 0.12F;

    public static class State extends BlockEntityRenderState
    {
        float pool;
        float time;
    }

    public LeyGrowerRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(LeyGrowerTile grower, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(grower, state, partialTicks, cameraPosition, breakProgress);
        state.pool = grower.pool(partialTicks);
        state.time = (grower.getLevel().getGameTime() % 24000L) + partialTicks;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.pool < 0.01F)
            return;

        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            float step = (POOL_MAX - POOL_MIN) / GRID;
            for (int i = 0; i < GRID; i++)
            {
                for (int j = 0; j < GRID; j++)
                {
                    float x0 = POOL_MIN + i * step, x1 = x0 + step;
                    float z0 = POOL_MIN + j * step, z1 = z0 + step;

                    int depth = LeyLook.color(LeyLook.TEAL, DEPTH_ALPHA * state.pool);
                    LeyLook.vertex(buffer, pose, x0 / 16, DEPTH / 16, z0 / 16, depth);
                    LeyLook.vertex(buffer, pose, x0 / 16, DEPTH / 16, z1 / 16, depth);
                    LeyLook.vertex(buffer, pose, x1 / 16, DEPTH / 16, z1 / 16, depth);
                    LeyLook.vertex(buffer, pose, x1 / 16, DEPTH / 16, z0 / 16, depth);

                    surface(pose, buffer, x0, z0, state);
                    surface(pose, buffer, x0, z1, state);
                    surface(pose, buffer, x1, z1, state);
                    surface(pose, buffer, x1, z0, state);
                }
            }
        });
    }

    private static void surface(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, float x, float z, State state)
    {
        float centre = (POOL_MIN + POOL_MAX) / 2;
        float distance = Mth.sqrt((x - centre) * (x - centre) + (z - centre) * (z - centre)) / ((POOL_MAX - POOL_MIN) / 2);
        float wave = Mth.sin((distance * RIPPLE_WAVES - state.time * RIPPLE_SPEED) * Mth.TWO_PI);
        // The rim of the pool stays still, as water does against stone
        float rim = 1 - Mth.clamp(distance - 0.8F, 0, 1) * 2;
        float alpha = SURFACE_ALPHA * state.pool * (1 - RIPPLE + RIPPLE * (0.5F + 0.5F * wave) * rim);
        int color = LeyLook.color(LeyLook.lerp(LeyLook.TEAL, LeyLook.TEAL_LIGHT, 0.5F + 0.5F * wave), alpha);
        LeyLook.vertex(buffer, pose, x / 16, (SURFACE + RIPPLE_HEIGHT * wave * rim) / 16, z / 16, color);
    }
}
