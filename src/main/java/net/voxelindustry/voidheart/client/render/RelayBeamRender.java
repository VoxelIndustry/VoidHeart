package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.essence.RelayNetwork;
import org.jspecify.annotations.Nullable;

/**
 * Straight beam carrying essence along a relay link: a bright thread in a soft glow, with a pulse of essence leaving
 * the source each time the network routes and reaching the target as the next route starts, so pulses run on
 * through a chain of relays.
 * <p>
 * A beam into something that takes essence into its body, given an {@link Intake}, stops short of it and splits into
 * thinner threads that spread around the body and sink into it from several sides. The pulse reaching the split runs
 * on down every thread at once while the next one leaves the source.
 */
public final class RelayBeamRender
{
    private static final float STEP = 0.2F;

    private static final float CORE_HALF_WIDTH = 0.032F;
    private static final float CORE_ALPHA      = 0.8F;
    private static final float GLOW_HALF_WIDTH = 0.09F;
    private static final float GLOW_ALPHA      = 0.24F;

    private static final float PULSE_PERIOD = RelayNetwork.ROUTE_INTERVAL_TICKS;
    private static final float PULSE_LENGTH = 0.35F;
    private static final float PULSE_SWELL  = 1.6F;
    private static final float PULSE_GLOW   = 0.45F;

    private static final float END_TAPER = 0.12F;
    private static final float SCROLL    = 0.08F;

    // Threads into an intake
    /** How far from the body's surface the beam splits. */
    private static final float SPLIT_AWAY       = 0.4F;
    /** Shortest the beam is kept before it splits, when its source stands close to the body. */
    private static final float SHORTEST_TRUNK   = 0.25F;
    /** How far past the split the threads keep the beam's line before turning away. */
    private static final float THREAD_CARRY     = 0.22F;
    /** How far past the split, in blocks, the threads still lie together as the beam before parting. */
    private static final float THREAD_JOIN      = 0.2F;
    /** Degrees off the beam's line, seen from the body, where the threads come in: the near side's rim, all around. */
    private static final float THREAD_SPREAD    = 70;
    private static final float THREAD_SPREAD_JITTER = 25;
    /** How far out of the body each thread turns to come straight in. */
    private static final float THREAD_APPROACH  = 0.14F;
    /** How deep into the body a thread runs, as a share of its reach, so it ends hidden inside. */
    private static final float THREAD_SINK      = 0.3F;
    /** Pieces each thread is drawn in, enough that its sharp turn just past the split stays a curve. */
    private static final int   THREAD_STEPS     = 24;
    /** Widths at the split, as a share of the beam's, and where they sink in. */
    private static final float THREAD_WIDTH     = 0.6F;
    private static final float THREAD_TIP_WIDTH = 0.2F;
    /** Sideways sway of each thread, in blocks, and how fast it rolls. */
    private static final float THREAD_SWAY      = 0.035F;
    private static final float THREAD_SWAY_RATE = 0.07F;
    /** Share of a route the pulse takes to run down the threads after reaching the split. */
    private static final float THREAD_PULSE     = 0.3F;
    private static final float THREAD_PULSE_LENGTH = 0.14F;

    /**
     * @param source where essence comes from, the beam already set off its surface
     * @param target where essence goes, the beam already set off its surface; the body's centre for an intake
     * @param growth 0 when retracted into the source, 1 when reaching the target
     * @param active whether essence still flows: a beam no longer active fades from its source toward its target
     * @param intake when not null, the beam splits short of the target and sinks into it as threads
     */
    public record Beam(Vec3 source, Vec3 target, float growth, boolean active, int color, @Nullable Intake intake)
    {
        public Beam(Vec3 source, Vec3 target, float growth, boolean active, int color)
        {
            this(source, target, growth, active, color, null);
        }
    }

    /**
     * A body taking the beam's essence in through several threads.
     *
     * @param reach   how far from its centre the body reaches
     * @param threads how many threads the beam splits into
     * @param seed    picks where around the body the threads come in
     */
    public record Intake(float reach, int threads, long seed)
    {
    }

    private RelayBeamRender()
    {
    }

    public static void render(PoseStack.Pose pose, VertexConsumer buffer, Beam beam, float time)
    {
        if (beam.growth() <= 0)
            return;
        var growth = Mth.clamp(beam.growth(), 0F, 1F);
        var pulse = (time / PULSE_PERIOD) % 1F;
        var formed = beam.active() && growth >= 1;

        var intake = beam.intake();
        if (intake == null)
        {
            var from = beam.active() ? 0 : 1 - growth;
            var to = beam.active() ? growth : 1;
            trunk(pose, buffer, beam.color(), beam.source(), beam.target(), from, to, true, formed ? pulse : -1, time);
            return;
        }

        var toBody = beam.target().subtract(beam.source());
        var distance = (float) toBody.length();
        if (distance < 1.0E-3F)
            return;
        var direction = toBody.scale(1 / distance);
        var trunkLength = Math.max(Math.min(SHORTEST_TRUNK, distance), distance - intake.reach() - SPLIT_AWAY);
        var split = beam.source().add(direction.scale(trunkLength));

        // Growth runs over the beam and then its threads, by their lengths, so the threads grow on once the beam reaches the split
        var threadLength = distance - trunkLength;
        var trunkShare = trunkLength / (trunkLength + threadLength);
        float trunkFrom, trunkTo, threadFrom, threadTo;
        if (beam.active())
        {
            trunkFrom = 0;
            trunkTo = Math.min(1, growth / trunkShare);
            threadFrom = 0;
            threadTo = Mth.clamp((growth - trunkShare) / (1 - trunkShare), 0F, 1F);
        }
        else
        {
            trunkFrom = Math.min(1, (1 - growth) / trunkShare);
            trunkTo = 1;
            threadFrom = Mth.clamp((1 - growth - trunkShare) / (1 - trunkShare), 0F, 1F);
            threadTo = 1;
        }

        if (trunkFrom < 1)
            trunk(pose, buffer, beam.color(), beam.source(), split, trunkFrom, trunkTo, trunkTo < 1, formed ? pulse : -1, time);
        if (threadTo > 0 && threadFrom < 1)
        {
            var threadPulse = formed && pulse < THREAD_PULSE ? pulse / THREAD_PULSE : -1;
            for (var i = 0; i < intake.threads(); i++)
                thread(pose, buffer, beam.color(), split, direction, beam.target(), intake, i, threadFrom, threadTo, threadPulse, trunkLength, time);
        }
    }

    /**
     * Opacity of one of several layers lying on each other, so that blended together they show as one at the given opacity, rising to that
     * opacity as they part.
     */
    private static float stacked(float alpha, int layers, float parted)
    {
        var layer = 1 - (float) Math.pow(1 - Mth.clamp(alpha, 0F, 1F), 1.0 / Math.max(1, layers));
        return Mth.lerp(parted, layer, alpha);
    }

    /**
     * @param taperEnd whether the end at the target narrows to nothing, rather than running on into the threads
     * @param pulse    where along the beam the pulse is, or below 0 for none
     */
    private static void trunk(PoseStack.Pose pose, VertexConsumer buffer, int color, Vec3 source, Vec3 target, float from, float to, boolean taperEnd,
                              float pulse, float time)
    {
        var axis = target.subtract(source);
        var length = (float) axis.length();
        if (length < 1.0E-3F)
            return;

        var steps = Math.max(2, Mth.ceil(length * (to - from) / STEP));
        float[][] previousCore = null;
        float[][] previousGlow = null;
        for (var i = 0; i <= steps; i++)
        {
            var u = Mth.lerp(i / (float) steps, from, to);
            var point = source.add(axis.scale(u));

            // Only the travelling pulse shows while the beam is fully formed
            var offset = (u - pulse) * length / PULSE_LENGTH;
            var swell = pulse >= 0 ? (float) Math.exp(-offset * offset) : 0;

            var distanceToEnd = taperEnd ? Math.min(u - from, to - u) * length : (u - from) * length;
            var taper = Mth.clamp(distanceToEnd / END_TAPER, 0F, 1F);
            var v = u * length - time * SCROLL;

            var core = VoidBeamRender.crossedSection(point, axis, CORE_HALF_WIDTH * (1 + PULSE_SWELL * swell) * taper,
                    Math.min(1, CORE_ALPHA + PULSE_GLOW * swell) * taper, v);
            var glow = VoidBeamRender.crossedSection(point, axis, GLOW_HALF_WIDTH * (1 + PULSE_SWELL * swell) * taper,
                    (GLOW_ALPHA + PULSE_GLOW * 0.5F * swell) * taper, v * 0.5F);
            if (previousCore != null)
            {
                VoidBeamRender.crossedStrip(pose, buffer, color, previousGlow, glow);
                VoidBeamRender.crossedStrip(pose, buffer, color, previousCore, core);
            }
            previousCore = core;
            previousGlow = glow;
        }
    }

    /**
     * One thread from the split into the body: it keeps the beam's line a little, turns out to come at the body from the side of it, then runs
     * straight in. A cubic curve through those four points, narrowing as it goes.
     */
    private static void thread(PoseStack.Pose pose, VertexConsumer buffer, int color, Vec3 split, Vec3 direction, Vec3 center, Intake intake, int index,
                               float from, float to, float pulse, float vStart, float time)
    {
        var side = direction.cross(Math.abs(direction.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        var other = direction.cross(side);

        var around = Mth.TWO_PI * (index + 0.6F * RavenousCollectorRender.PlasmaLoopLayer.noise(intake.seed(), index, 0, 0)) / intake.threads();
        var spread = (THREAD_SPREAD + THREAD_SPREAD_JITTER * (RavenousCollectorRender.PlasmaLoopLayer.noise(intake.seed(), index, 0, 1) * 2 - 1))
                * Mth.DEG_TO_RAD;
        var ring = side.scale(Mth.cos(around)).add(other.scale(Mth.sin(around)));
        // From the body's centre out to where this thread comes in, tilted back toward the split
        var inward = direction.scale(-Mth.cos(spread)).add(ring.scale(Mth.sin(spread)));

        var sway = ring.scale(THREAD_SWAY * Mth.sin(time * THREAD_SWAY_RATE + index * 2.1F))
                .add(direction.cross(ring).scale(THREAD_SWAY * Mth.cos(time * THREAD_SWAY_RATE * 0.8F + index * 1.3F)));

        var p0 = split;
        var p1 = split.add(direction.scale(THREAD_CARRY));
        var p2 = center.add(inward.scale(intake.reach() + THREAD_APPROACH)).add(sway);
        var p3 = center.add(inward.scale(intake.reach() * THREAD_SINK));

        var length = (float) (p1.subtract(p0).length() + p2.subtract(p1).length() + p3.subtract(p2).length());
        var steps = Math.max(2, Mth.ceil(THREAD_STEPS * (to - from)));
        float[][] previousCore = null;
        float[][] previousGlow = null;
        for (var i = 0; i <= steps; i++)
        {
            var u = Mth.lerp(i / (float) steps, from, to);
            var inverse = 1 - u;
            var point = p0.scale(inverse * inverse * inverse)
                    .add(p1.scale(3 * inverse * inverse * u))
                    .add(p2.scale(3 * inverse * u * u))
                    .add(p3.scale(u * u * u));
            var tangent = p1.subtract(p0).scale(inverse * inverse)
                    .add(p2.subtract(p1).scale(2 * inverse * u))
                    .add(p3.subtract(p2).scale(u * u));

            var offset = (u - pulse) * length / THREAD_PULSE_LENGTH;
            var swell = pulse >= 0 ? (float) Math.exp(-offset * offset) : 0;

            // Ends only narrow where the thread is growing or fading, never where it leaves the beam or sinks into the body
            var taper = 1F;
            if (from > 0)
                taper = Math.min(taper, (u - from) * length / END_TAPER);
            if (to < 1)
                taper = Math.min(taper, (to - u) * length / END_TAPER);
            taper = Mth.clamp(taper, 0F, 1F);

            // Leaving the split the threads lie together as the beam, its width and texture, faint enough that all of them stacked look as it does,
            // then part into their own
            var along = u * length;
            var parted = Mth.clamp(along / THREAD_JOIN, 0F, 1F);
            parted = parted * parted * (3 - 2 * parted);
            var width = Mth.lerp(parted, 1, Mth.lerp(u, THREAD_WIDTH, THREAD_TIP_WIDTH)) * (1 + PULSE_SWELL * swell) * taper;
            var v = vStart + along - time * SCROLL + index * 0.37F * parted;
            var coreAlpha = stacked(Math.min(1, CORE_ALPHA + PULSE_GLOW * swell), intake.threads(), parted) * taper;
            var glowAlpha = stacked(GLOW_ALPHA + PULSE_GLOW * 0.5F * swell, intake.threads(), parted) * taper;

            var core = VoidBeamRender.crossedSection(point, tangent, CORE_HALF_WIDTH * width, coreAlpha, v);
            var glow = VoidBeamRender.crossedSection(point, tangent, GLOW_HALF_WIDTH * width, glowAlpha, v * 0.5F);
            if (previousCore != null)
            {
                VoidBeamRender.crossedStrip(pose, buffer, color, previousGlow, glow);
                VoidBeamRender.crossedStrip(pose, buffer, color, previousCore, core);
            }
            previousCore = core;
            previousGlow = glow;
        }
    }
}
