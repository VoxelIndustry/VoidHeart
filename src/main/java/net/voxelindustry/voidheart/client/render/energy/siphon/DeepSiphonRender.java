package net.voxelindustry.voidheart.client.render.energy.siphon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.energy.siphon.DeepSiphonTile;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.client.render.energy.siphon.SiphonGeometry.disc;
import static net.voxelindustry.voidheart.client.render.energy.siphon.SiphonGeometry.ribbon;

/**
 * A Deep Siphon drawing: pale threads rising out of the chasm's dark into its crown, faint far below and brightening near it, with pulses climbing them
 * more often the more it draws, and the crown lit with a soft halo. Not drawing, both fade and the crown is its plain model.
 * <p>
 * Render type: additive {@code GLOW} for everything, which writes no depth, so threads crossing each other and the halo over the crown's crossed planes add up
 * in any order instead of flickering. Threads are depth tested, so the deck hides them where they pass through it. Per siphon and frame: 5 threads of
 * 16 segments plus up to about 30 pulse segments and 2 halo discs, about 150 quads, rebuilt each frame and nothing cached.
 */
public class DeepSiphonRender implements BlockEntityRenderer<DeepSiphonTile, DeepSiphonRender.State>
{
    // Tones sampled from the azurium bundle textures: their lightest, and their middle blue
    private static final int AZURIUM_LIGHT = 0xCCFFF2;
    private static final int AZURIUM_MID   = 0x3D7ED9;

    private static final float PIXEL = 1 / 16F;

    // Crown: the middle of the crystal planes in the model
    private static final float CROWN_Y          = 19 * PIXEL;
    private static final float CROWN_HALO       = 0.75F;
    private static final int   CROWN_HALO_ALPHA = 110;
    private static final float CROWN_CORE       = 0.28F;
    private static final int   CROWN_CORE_ALPHA = 200;
    private static final float CROWN_BREATH     = 0.12F;

    // Threads: from the dark far below up into the crown
    private static final int   THREADS          = 5;
    private static final int   SEGMENTS         = 16;
    private static final float DEPTH            = 40;
    private static final float SPREAD           = 2.6F;
    private static final float SWAY             = 0.35F;
    private static final float SWAY_SPEED       = 0.03F;
    private static final float THREAD_HALF      = 0.022F;
    private static final int   THREAD_ALPHA     = 150;
    private static final float THREAD_HALO_HALF = 0.06F;
    private static final int   THREAD_HALO_ALPHA = 45;
    // Faint below: alpha follows the height along the thread to this power
    private static final float FADE_POWER       = 1.6F;

    // Pulses climbing the threads, blocks per tick, and their spacing from one at a trickle to the full output
    private static final float PULSE_SPEED      = 0.6F;
    private static final float PULSE_LENGTH     = 1.4F;
    private static final float PULSE_HALF       = 0.045F;
    private static final int   PULSE_ALPHA      = 230;
    private static final float SPACING_SLOW     = 30;
    private static final float SPACING_FAST     = 9;

    private static final int VIEW_DISTANCE = 96;

    public static class State extends BlockEntityRenderState
    {
        float shown;
        float time;
        long  seed;
        Vector3f camera = new Vector3f();
    }

    public DeepSiphonRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(DeepSiphonTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);

        var pos = tile.getBlockPos();
        state.shown = tile.shown(partialTicks);
        state.time = tile.getLevel() == null ? 0 : tile.getLevel().getGameTime() + partialTicks;
        state.seed = pos.asLong();
        // Subtracted in double, so it stays precise far from the world origin
        state.camera = new Vector3f((float) (cameraPosition.x - pos.getX()), (float) (cameraPosition.y - pos.getY()), (float) (cameraPosition.z - pos.getZ()));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.shown <= 0.01F)
            return;
        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            threads(pose, buffer, state);
            crown(pose, buffer, state);
        });
    }

    private static void crown(PoseStack.Pose pose, VertexConsumer buffer, State state)
    {
        var center = new Vector3f(0.5F, CROWN_Y, 0.5F);
        float breath = 1 + CROWN_BREATH * Mth.sin(state.time * 0.15F);
        disc(pose, buffer, state.camera, center, CROWN_HALO * breath, ARGB.color((int) (CROWN_HALO_ALPHA * state.shown), AZURIUM_MID));
        disc(pose, buffer, state.camera, center, CROWN_CORE, ARGB.color((int) (CROWN_CORE_ALPHA * state.shown), AZURIUM_LIGHT));
    }

    private static void threads(PoseStack.Pose pose, VertexConsumer buffer, State state)
    {
        var top = new Vector3f(0.5F, CROWN_Y, 0.5F);
        float spacing = Mth.lerp(state.shown, SPACING_SLOW, SPACING_FAST);
        for (int thread = 0; thread < THREADS; thread++)
        {
            var points = thread(state, thread, top);
            float[] lengths = new float[SEGMENTS + 1];
            for (int i = 1; i <= SEGMENTS; i++)
                lengths[i] = lengths[i - 1] + points[i].distance(points[i - 1]);
            float length = lengths[SEGMENTS];

            for (int i = 0; i < SEGMENTS; i++)
            {
                int from = ARGB.color((int) (THREAD_ALPHA * state.shown * rise(i / (float) SEGMENTS)), AZURIUM_LIGHT);
                int to = ARGB.color((int) (THREAD_ALPHA * state.shown * rise((i + 1) / (float) SEGMENTS)), AZURIUM_LIGHT);
                ribbon(pose, buffer, state.camera, points[i], points[i + 1], THREAD_HALF, from, to);
                int haloFrom = ARGB.color((int) (THREAD_HALO_ALPHA * state.shown * rise(i / (float) SEGMENTS)), AZURIUM_MID);
                int haloTo = ARGB.color((int) (THREAD_HALO_ALPHA * state.shown * rise((i + 1) / (float) SEGMENTS)), AZURIUM_MID);
                ribbon(pose, buffer, state.camera, points[i], points[i + 1], THREAD_HALO_HALF, haloFrom, haloTo);
            }

            // Each thread on its own phase, so pulses don't arrive at the crown together
            float phase = (state.time * PULSE_SPEED + thread * spacing * 0.37F) % spacing;
            for (float head = phase; head < length + PULSE_LENGTH; head += spacing)
                pulse(pose, buffer, state, points, lengths, head - PULSE_LENGTH, head);
        }
    }

    /**
     * A thread's joints from the bottom up: from a spot in the dark below, spread around the siphon, curving up into the crown and swaying a little.
     */
    private static Vector3f[] thread(State state, int thread, Vector3f top)
    {
        long hash = state.seed * 31 + thread * 0x9E3779B97F4A7C15L;
        float angle = (thread + (hash & 0xFF) / 255F * 0.6F) / THREADS * Mth.TWO_PI;
        float reach = SPREAD * (0.6F + ((hash >>> 8) & 0xFF) / 255F * 0.4F);
        var bottom = new Vector3f(0.5F + Mth.cos(angle) * reach, CROWN_Y - DEPTH, 0.5F + Mth.sin(angle) * reach);

        var points = new Vector3f[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++)
        {
            float t = i / (float) SEGMENTS;
            // Straight up from below for most of the way, gathering in toward the crown near the top
            float gather = t * t * t;
            var point = new Vector3f(Mth.lerp(gather, bottom.x, top.x), Mth.lerp(t, bottom.y, top.y), Mth.lerp(gather, bottom.z, top.z));
            if (i > 0 && i < SEGMENTS)
            {
                float sway = SWAY * (1 - t) * Mth.sin(state.time * SWAY_SPEED + thread * 1.7F + i * 0.45F);
                point.add(Mth.cos(angle + Mth.HALF_PI) * sway, 0, Mth.sin(angle + Mth.HALF_PI) * sway);
            }
            points[i] = point;
        }
        return points;
    }

    /**
     * Brightness at a height along a thread, 0 at the bottom and 1 at the crown.
     */
    private static float rise(float t)
    {
        return (float) Math.pow(t, FADE_POWER);
    }

    private static void pulse(PoseStack.Pose pose, VertexConsumer buffer, State state, Vector3f[] points, float[] lengths, float start, float end)
    {
        float length = lengths[lengths.length - 1];
        for (int i = 0; i + 1 < points.length; i++)
        {
            float a = Math.max(Math.max(0, start), lengths[i]);
            float b = Math.min(Math.min(length, end), lengths[i + 1]);
            if (b <= a)
                continue;
            float span = lengths[i + 1] - lengths[i];
            var from = new Vector3f(points[i]).lerp(points[i + 1], (a - lengths[i]) / span);
            var to = new Vector3f(points[i]).lerp(points[i + 1], (b - lengths[i]) / span);
            int fromColor = ARGB.color((int) (PULSE_ALPHA * state.shown * rise(a / length)), AZURIUM_LIGHT);
            int toColor = ARGB.color((int) (PULSE_ALPHA * state.shown * rise(b / length)), AZURIUM_LIGHT);
            ribbon(pose, buffer, state.camera, from, to, PULSE_HALF, fromColor, toColor);
        }
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        // The threads reach far below the block
        return true;
    }

    @Override
    public int getViewDistance()
    {
        return VIEW_DISTANCE;
    }
}
