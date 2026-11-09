package net.voxelindustry.voidheart.client.render.energy.ley;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.energy.ley.LeyWardTile;
import org.jspecify.annotations.Nullable;

/**
 * The ring carved on both faces of the ward, glowing ley teal with supply and drifting to a mauve-grey as taint or seep weaken it. Past half its strength
 * lost it stutters. Additive glow over the carved groove: 2 faces × 24 segments × 3 bands, rebuilt every frame.
 */
public class LeyWardRender implements BlockEntityRenderer<LeyWardTile, LeyWardRender.State>
{
    // In model pixels, facing north, matching models/block/ley_ward.json and the ring in ley_ward_face.png
    private static final float CENTRE_X     = 8;
    private static final float CENTRE_Y     = 9;
    private static final float INNER        = 2.6F;
    private static final float OUTER        = 3.7F;
    private static final float HALO         = 1.3F;
    private static final float NORTH_FACE_Z = 6.5F;
    private static final float SOUTH_FACE_Z = 9.5F;
    private static final float LIFT         = 0.05F;
    private static final int   SEGMENTS     = 24;

    private static final float CORE_ALPHA   = 0.9F;
    private static final float HALO_ALPHA   = 0.3F;
    private static final int   STUTTER_TICKS = 3;
    private static final float STUTTER_DIP  = 0.2F;

    public static class State extends BlockEntityRenderState
    {
        float     ring;
        float     lost;
        boolean   stutter;
        Direction facing = Direction.NORTH;
    }

    public LeyWardRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(LeyWardTile ward, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(ward, state, partialTicks, cameraPosition, breakProgress);
        state.ring = ward.ring(partialTicks);
        state.lost = 1 - ward.health();
        state.facing = ward.facing();
        state.stutter = stutters(ward.getBlockPos().asLong(), ward.getLevel().getGameTime(), state.lost);
    }

    /**
     * Past half its strength lost, a ward's light dips now and then, more often the more it has lost.
     */
    static boolean stutters(long seed, long time, float lost)
    {
        if (lost <= 0.5F)
            return false;
        float chance = (lost - 0.5F) * 0.5F;
        return LeyLook.hash(seed, time / STUTTER_TICKS, 7) < chance;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        float level = state.ring * (state.stutter ? STUTTER_DIP : 1);
        if (level < 0.01F)
            return;

        poseStack.pushPose();
        poseStack.translate(0.5, 0, 0.5);
        poseStack.rotate(Axis.YP.rotationDegrees(180 - state.facing.toYRot()));
        poseStack.translate(-0.5, 0, -0.5);

        int rgb = LeyLook.lerp(LeyLook.TEAL, LeyLook.MAUVE_GREY, state.lost);
        int light = LeyLook.lerp(LeyLook.TEAL_LIGHT, LeyLook.MAUVE_GREY, state.lost);
        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            ring(pose, buffer, (NORTH_FACE_Z - LIFT) / 16, rgb, light, level);
            ring(pose, buffer, (SOUTH_FACE_Z + LIFT) / 16, rgb, light, level);
        });
        poseStack.popPose();
    }

    private static void ring(PoseStack.Pose pose, VertexConsumer buffer, float z, int rgb, int light, float level)
    {
        float middle = (INNER + OUTER) / 2;
        int core = LeyLook.color(light, CORE_ALPHA * level);
        int edge = LeyLook.color(rgb, CORE_ALPHA * 0.7F * level);
        int halo = LeyLook.color(rgb, HALO_ALPHA * level);
        int none = LeyLook.color(rgb, 0);

        for (int i = 0; i < SEGMENTS; i++)
        {
            float a0 = Mth.TWO_PI * i / SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            band(pose, buffer, z, a0, a1, INNER, middle, edge, core);
            band(pose, buffer, z, a0, a1, middle, OUTER, core, edge);
            band(pose, buffer, z, a0, a1, INNER - HALO, INNER, none, halo);
            band(pose, buffer, z, a0, a1, OUTER, OUTER + HALO, halo, none);
        }
    }

    private static void band(PoseStack.Pose pose, VertexConsumer buffer, float z, float a0, float a1, float from, float to, int fromColor, int toColor)
    {
        LeyLook.vertex(buffer, pose, x(a0, from), y(a0, from), z, fromColor);
        LeyLook.vertex(buffer, pose, x(a0, to), y(a0, to), z, toColor);
        LeyLook.vertex(buffer, pose, x(a1, to), y(a1, to), z, toColor);
        LeyLook.vertex(buffer, pose, x(a1, from), y(a1, from), z, fromColor);
    }

    private static float x(float angle, float radius)
    {
        return (CENTRE_X + Mth.cos(angle) * radius) / 16;
    }

    private static float y(float angle, float radius)
    {
        return (CENTRE_Y + Mth.sin(angle) * radius) / 16;
    }
}
