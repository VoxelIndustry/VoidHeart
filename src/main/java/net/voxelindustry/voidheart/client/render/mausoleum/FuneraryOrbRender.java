package net.voxelindustry.voidheart.client.render.mausoleum;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.mausoleum.FuneraryOrbTile;
import org.jspecify.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A ball of green fire floating free, a small dark heart burning inside it and flames licking off its top. It always faces the viewer, bobs slowly
 * and swells a little on a beat of its own, taken from where it hangs, so a row of them never moves in step.
 * <p>
 * Per frame: one quad, rebuilt from the time alone.
 */
public class FuneraryOrbRender implements BlockEntityRenderer<FuneraryOrbTile, FuneraryOrbRender.State>
{
    /** Half the ball's size across, in blocks. */
    private static final float HALF_SIZE = 0.42F;
    private static final float BOB = 0.07F;
    private static final float BOB_RATE = 0.05F;
    private static final float SWELL = 0.04F;
    private static final float SWELL_RATE = 0.13F;
    private static final int VIEW_DISTANCE = 64;

    public static class State extends BlockEntityRenderState
    {
        float bob;
        float scale;
    }

    public FuneraryOrbRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(FuneraryOrbTile orb, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(orb, state, partialTicks, cameraPosition, breakProgress);
        long hash = orb.getBlockPos().asLong() * 0x9E3779B97F4A7C15L;
        float phase = (hash >>> 40) / (float) (1 << 24) * Mth.TWO_PI;
        float time = (float) ((orb.getLevel().getGameTime() + (double) partialTicks) % 100_000);
        state.bob = Mth.sin(time * BOB_RATE + phase) * BOB;
        state.scale = 1 + Mth.sin(time * SWELL_RATE + phase * 3) * SWELL;
    }

    @Override
    public int getViewDistance()
    {
        return VIEW_DISTANCE;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        var right = camera.orientation.transform(new Vector3f(1, 0, 0));
        var up = camera.orientation.transform(new Vector3f(0, 1, 0));
        float half = HALF_SIZE * state.scale;
        var sprite = FuneraryFlames.sprite(FuneraryFlames.FIREBALL);
        collector.submitCustomGeometry(poseStack, FuneraryFlames.TYPE, (pose, buffer) ->
                FuneraryFlames.billboard(pose, buffer, sprite, 0.5F, 0.5F + state.bob, 0.5F, right, up, half, half));
    }
}
