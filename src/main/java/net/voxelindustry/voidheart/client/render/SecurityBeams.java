package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The lines the Underway's guns draw: a thin amber line to where a gun aims, which thickens and brightens once it stops following its target, and the short streak of a bullet.
 * <p>
 * Both are beacon beam tubes, as the Fallen Bishop's lance is: translucent, so the tube's own sides blend rather than hide each other, and a few quads each.
 */
public final class SecurityBeams
{
    private static final Identifier BEAM = Identifier.withDefaultNamespace("textures/entity/beacon/beacon_beam.png");

    // The amber of the Underway's machinery, the colour of the shield over a key
    private static final int LINE_COLOR = 0xF0A030;
    private static final int LOCKED_COLOR = 0xFFD890;
    private static final int TRACER_CORE_COLOR = 0xFFFFF6DC;
    private static final int TRACER_FLARE_COLOR = 0xFFC060;
    // The red of the emergency circuit
    private static final int WIRE_COLOR = 0xFF3A28;
    private static final int WIRE_ALPHA = 190;
    private static final float WIRE_RADIUS = 0.012F;
    private static final int LINE_ALPHA = 100;
    private static final int LOCKED_ALPHA = 220;
    private static final int TRACER_FLARE_ALPHA = 170;
    private static final float LINE_RADIUS = 0.015F;
    private static final float LOCKED_RADIUS = 0.028F;
    private static final float TRACER_CORE_RADIUS = 0.03F;
    private static final float TRACER_FLARE_RADIUS = 0.08F;

    private SecurityBeams()
    {
    }

    /**
     * @param from   where the line starts, from the pose
     * @param to     where it ends
     * @param locked whether it has stopped following its target and the shot is about to leave
     */
    public static void submitAim(PoseStack poseStack, SubmitNodeCollector collector, Vec3 from, Vec3 to, boolean locked, float ticks)
    {
        poseStack.pushPose();
        poseStack.translate(from.x, from.y, from.z);
        float length = BeamTube.alignTo(poseStack, to.subtract(from));
        if (length > 0)
        {
            float radius = locked ? LOCKED_RADIUS : LINE_RADIUS;
            int color = locked ? ARGB.color(LOCKED_ALPHA, LOCKED_COLOR) : ARGB.color(LINE_ALPHA, LINE_COLOR);
            float spin = ticks * 0.3F;
            float scroll = Mth.frac(-ticks * 0.2F);
            collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BEAM, true), (pose, buffer) -> BeamTube.tube(pose, buffer, color, length, radius, spin, scroll, length));
        }
        poseStack.popPose();
    }

    /** The beam of a tripwire: a thin steady red line from its emitter to the wall across. */
    public static void submitWire(PoseStack poseStack, SubmitNodeCollector collector, Vec3 from, Vec3 to, float ticks)
    {
        poseStack.pushPose();
        poseStack.translate(from.x, from.y, from.z);
        float length = BeamTube.alignTo(poseStack, to.subtract(from));
        if (length > 0)
        {
            int color = ARGB.color(WIRE_ALPHA, WIRE_COLOR);
            float scroll = Mth.frac(-ticks * 0.05F);
            collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BEAM, true), (pose, buffer) -> BeamTube.tube(pose, buffer, color, length, WIRE_RADIUS, 0.785F, scroll, length));
        }
        poseStack.popPose();
    }

    /**
     * @param fade 1 as the bullet leaves, 0 once its streak is gone
     */
    public static void submitTracer(PoseStack poseStack, SubmitNodeCollector collector, Vec3 from, Vec3 to, float fade)
    {
        if (fade <= 0)
            return;
        poseStack.pushPose();
        poseStack.translate(from.x, from.y, from.z);
        float length = BeamTube.alignTo(poseStack, to.subtract(from));
        if (length > 0)
            BeamTube.submitCoreAndFlare(poseStack, collector, BEAM, length, 0, 0, TRACER_CORE_COLOR, TRACER_CORE_RADIUS * fade,
                    ARGB.color((int) (TRACER_FLARE_ALPHA * fade), TRACER_FLARE_COLOR), TRACER_FLARE_RADIUS * fade);
        poseStack.popPose();
    }
}
