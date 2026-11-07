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
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.research.VoidLoomBlock;
import net.voxelindustry.voidheart.common.content.research.VoidLoomTile;
import org.jspecify.annotations.Nullable;

/**
 * Loose void drifting into the frame and leaving it woven. Faint strands fade in out of nothing a few blocks around, writhe their way to the warp,
 * and one tight three-strand braid runs from the frame to the tablet of the repository, its twist rolling toward it. The strands are uneven on purpose,
 * the braid regular, since that is what shows it was woven. All on the translucent unlit veil the relays use, kept fainter than the repository itself.
 * Per loom, at most a repository's one: 7 warp threads, 5 strands of 16 segments and 3 braid strands of up to 96, about 500 crossed strips.
 */
public class VoidLoomRender implements BlockEntityRenderer<VoidLoomTile, VoidLoomRender.State>
{
    private static final int   WARP_RGB      = 0x277D63;
    private static final float WARP_ALPHA    = 0.6F;
    private static final float WARP_IDLE     = 0.15F;
    private static final int   WARP_THREADS  = 7;
    private static final float WARP_LEFT     = 3.5F / 16;
    private static final float WARP_RIGHT    = 12.5F / 16;
    private static final float WARP_BOTTOM   = 3.5F / 16;
    private static final float WARP_TOP      = 14F / 16;
    private static final float WARP_WIDTH    = 0.008F;

    private static final int   LOOSE_RGB     = 0x36C5B0;
    private static final int   LOOSE_STRANDS = 5;
    private static final int   LOOSE_STEPS   = 16;
    private static final float LOOSE_ALPHA   = 0.5F;
    private static final float LOOSE_WIDTH   = 0.016F;
    private static final float LOOSE_NEAR    = 2;
    private static final float LOOSE_FAR     = 3;
    private static final float LOOSE_LIFE    = 240;
    /** Share of a strand's length its free end takes to fade in from nothing. */
    private static final float LOOSE_FADE    = 0.35F;
    private static final float LOOSE_BOW     = 0.22F;

    private static final int   BRAID_RGB     = 0x6BDBC0;
    private static final float BRAID_ALPHA   = 0.8F;
    private static final float BRAID_WIDTH   = 0.012F;
    private static final float BRAID_RADIUS  = 0.035F;
    private static final float BRAID_PITCH   = 0.45F;
    private static final float BRAID_SPEED   = 0.02F;
    private static final float BRAID_RISE    = 0.25F;
    private static final int   BRAID_STEPS_PER_BLOCK = 12;
    private static final int   BRAID_MAX_STEPS = 96;
    private static final Vec3  BRAID_FROM    = new Vec3(0.5, 0.55, 0.5);
    /** Near the tablet of the repository, from its corner. */
    private static final Vec3  REPOSITORY_AT = new Vec3(0.5, 1.05, 0.5);

    public static class State extends BlockEntityRenderState
    {
        public boolean acrossX;
        public boolean active;
        public float time;
        public long seed;
        public @Nullable Vec3 repository;
    }

    public VoidLoomRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(VoidLoomTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);

        var pos = tile.getBlockPos();
        state.acrossX = tile.getBlockState().getValue(VoidLoomBlock.FACING).getAxis() == Direction.Axis.Z;
        state.active = tile.isActive() && tile.getLink().isPresent();
        state.time = tile.getLevel().getGameTime() % 240000 + partialTicks;
        state.seed = pos.asLong();
        state.repository = tile.getLink().map(link -> link.repository())
                .map(repository -> new Vec3(repository.getX() - pos.getX(), repository.getY() - pos.getY(), repository.getZ() - pos.getZ()).add(REPOSITORY_AT))
                .orElse(null);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(VoidBeamRender.VEIL_TEXTURE, true), (pose, buffer) ->
        {
            warp(state, pose, buffer);
            if (!state.active || state.repository == null)
                return;
            for (int strand = 0; strand < LOOSE_STRANDS; strand++)
                loose(state, pose, buffer, strand);
            braid(state, pose, buffer, state.repository);
        });
    }

    /** A point on the plane of the warp, across from 0 to 1 and up in block units. */
    private static Vec3 onWarp(State state, float across, float up)
    {
        return state.acrossX ? new Vec3(across, up, 0.5) : new Vec3(0.5, up, across);
    }

    private static void warp(State state, PoseStack.Pose pose, VertexConsumer buffer)
    {
        float alpha = state.active ? WARP_ALPHA : WARP_IDLE;
        for (int thread = 0; thread < WARP_THREADS; thread++)
        {
            float across = Mth.lerp(thread / (WARP_THREADS - 1F), WARP_LEFT, WARP_RIGHT);
            var bottom = onWarp(state, across, WARP_BOTTOM);
            var top = onWarp(state, across, WARP_TOP);
            var up = top.subtract(bottom);
            VoidBeamRender.crossedStrip(pose, buffer, WARP_RGB,
                    VoidBeamRender.crossedSection(bottom, up, WARP_WIDTH, alpha, 0),
                    VoidBeamRender.crossedSection(top, up, WARP_WIDTH, alpha, 1));
        }
    }

    /**
     * Each strand lives a while from a fresh spot around the loom, fading in and out, so the void it draws on never looks fixed in place.
     */
    private static void loose(State state, PoseStack.Pose pose, VertexConsumer buffer, int strand)
    {
        float lived = state.time + strand * LOOSE_LIFE / LOOSE_STRANDS;
        int life = (int) (lived / LOOSE_LIFE);
        float age = lived / LOOSE_LIFE - life;
        float envelope = Mth.sin(Mth.PI * age);

        long seed = state.seed * 31 + strand * 0x632BE59BD9B4E019L + life * 0x9E3779B97F4A7C15L;
        float angle = Mth.TWO_PI * random(seed, 0);
        float distance = Mth.lerp(random(seed, 1), LOOSE_NEAR, LOOSE_FAR);
        float height = Mth.lerp(random(seed, 2), -0.5F, 1.6F);
        var from = new Vec3(0.5 + Mth.cos(angle) * distance, height, 0.5 + Mth.sin(angle) * distance);
        var to = onWarp(state, Mth.lerp(random(seed, 3), WARP_LEFT, WARP_RIGHT), Mth.lerp(random(seed, 4), WARP_BOTTOM + 0.1F, WARP_TOP - 0.1F));
        float unevenness = 0.6F + 0.4F * random(seed, 5);

        var axis = to.subtract(from);
        var side = axis.cross(new Vec3(0, 1, 0)).normalize();
        float bowAngle = state.time * (0.04F + 0.03F * random(seed, 6)) + random(seed, 7) * Mth.TWO_PI;
        var bow = side.scale(Mth.cos(bowAngle)).add(0, Mth.sin(bowAngle), 0).scale(LOOSE_BOW * axis.length());
        var control = from.add(to).scale(0.5).add(bow);

        float[][] previous = null;
        for (int step = 0; step <= LOOSE_STEPS; step++)
        {
            float u = step / (float) LOOSE_STEPS;
            float inverse = 1 - u;
            var point = from.scale(inverse * inverse).add(control.scale(2 * inverse * u)).add(to.scale(u * u));
            var tangent = control.subtract(from).scale(inverse).add(to.subtract(control).scale(u));

            float ripple = 0.7F + 0.3F * Mth.sin(u * 9 - state.time * 0.3F + strand);
            float alpha = LOOSE_ALPHA * unevenness * envelope * ripple * Mth.clamp(u / LOOSE_FADE, 0, 1);
            var current = VoidBeamRender.crossedSection(point, tangent, LOOSE_WIDTH * ripple, alpha, u * 2 - state.time * 0.05F);
            if (previous != null)
                VoidBeamRender.crossedStrip(pose, buffer, LOOSE_RGB, previous, current);
            previous = current;
        }
    }

    /** Three strands twisting around one arched path, a third of a turn apart. */
    private static void braid(State state, PoseStack.Pose pose, VertexConsumer buffer, Vec3 repository)
    {
        var from = BRAID_FROM;
        var axis = repository.subtract(from);
        double length = axis.length();
        if (length < 0.5)
            return;

        var control = from.add(repository).scale(0.5).add(0, BRAID_RISE * length, 0);
        int steps = Math.min(BRAID_MAX_STEPS, Math.max(8, (int) (length * BRAID_STEPS_PER_BLOCK)));

        for (int strand = 0; strand < 3; strand++)
        {
            float[][] previous = null;
            for (int step = 0; step <= steps; step++)
            {
                float u = step / (float) steps;
                float inverse = 1 - u;
                var point = from.scale(inverse * inverse).add(control.scale(2 * inverse * u)).add(repository.scale(u * u));
                var tangent = control.subtract(from).scale(inverse).add(repository.subtract(control).scale(u)).normalize();
                var side = tangent.cross(Math.abs(tangent.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
                var other = tangent.cross(side);

                float twist = Mth.TWO_PI * ((float) (u * length) / BRAID_PITCH - state.time * BRAID_SPEED) + strand * Mth.TWO_PI / 3;
                var offset = side.scale(Mth.cos(twist) * BRAID_RADIUS).add(other.scale(Mth.sin(twist) * BRAID_RADIUS));
                // The braid tightens out of the frame and loosens again into the tablet
                float ends = Mth.clamp(Math.min(u, 1 - u) * 8, 0, 1);
                float alpha = BRAID_ALPHA * ends;

                var current = VoidBeamRender.crossedSection(point.add(offset.scale(ends)), tangent, BRAID_WIDTH, alpha, u * (float) length - state.time * 0.08F);
                if (previous != null)
                    VoidBeamRender.crossedStrip(pose, buffer, BRAID_RGB, previous, current);
                previous = current;
            }
        }
    }

    private static float random(long seed, int salt)
    {
        long hash = seed + salt * 0xD1B54A32D192ED03L;
        hash ^= hash >>> 33;
        hash *= 0xFF51AFD7ED558CCDL;
        hash ^= hash >>> 33;
        return (hash >>> 40) / (float) (1L << 24);
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }
}
