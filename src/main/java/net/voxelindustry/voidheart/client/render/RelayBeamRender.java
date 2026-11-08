package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.essence.RelayNetwork;

/**
 * Straight beam carrying essence along a relay link: a bright thread in a soft glow, with a pulse of essence leaving
 * the source each time the network routes and reaching the target as the next route starts, so pulses run on
 * through a chain of relays.
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

    /**
     * @param source      where essence comes from, the beam already set off its surface
     * @param target      where essence goes, the beam already set off its surface
     * @param growth      0 when retracted into the source, 1 when reaching the target
     * @param active      whether essence still flows: a beam no longer active fades from its source toward its target
     */
    public record Beam(Vec3 source, Vec3 target, float growth, boolean active, int color)
    {
    }

    private RelayBeamRender()
    {
    }

    public static void render(PoseStack.Pose pose, VertexConsumer buffer, Beam beam, float time)
    {
        var axis = beam.target().subtract(beam.source());
        var length = (float) axis.length();
        if (length < 1.0E-3F || beam.growth() <= 0)
            return;

        var growth = Mth.clamp(beam.growth(), 0F, 1F);
        var from = beam.active() ? 0 : 1 - growth;
        var to = beam.active() ? growth : 1;
        var pulse = (time / PULSE_PERIOD) % 1F;

        var steps = Math.max(2, Mth.ceil(length * (to - from) / STEP));
        float[][] previousCore = null;
        float[][] previousGlow = null;
        for (var i = 0; i <= steps; i++)
        {
            var u = Mth.lerp(i / (float) steps, from, to);
            var point = beam.source().add(axis.scale(u));

            // Only the travelling pulse shows while the beam is fully formed
            var offset = (u - pulse) * length / PULSE_LENGTH;
            var swell = beam.active() && growth >= 1 ? (float) Math.exp(-offset * offset) : 0;

            var distanceToEnd = Math.min(u - from, to - u) * length;
            var taper = Mth.clamp(distanceToEnd / END_TAPER, 0F, 1F);
            var v = u * length - time * SCROLL;

            var core = VoidBeamRender.crossedSection(point, axis, CORE_HALF_WIDTH * (1 + PULSE_SWELL * swell) * taper,
                    Math.min(1, CORE_ALPHA + PULSE_GLOW * swell) * taper, v);
            var glow = VoidBeamRender.crossedSection(point, axis, GLOW_HALF_WIDTH * (1 + PULSE_SWELL * swell) * taper,
                    (GLOW_ALPHA + PULSE_GLOW * 0.5F * swell) * taper, v * 0.5F);
            if (previousCore != null)
            {
                VoidBeamRender.crossedStrip(pose, buffer, beam.color(), previousGlow, glow);
                VoidBeamRender.crossedStrip(pose, buffer, beam.color(), previousCore, core);
            }
            previousCore = core;
            previousGlow = glow;
        }
    }
}
