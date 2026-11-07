package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Beams drawn as spinning square tubes of a scrolling beacon texture, as mobs and the Shoulder Eye fire them.
 */
public final class BeamTube
{
    private static final float MIN_LENGTH = 0.01F;

    private BeamTube()
    {
    }

    /**
     * Turns the pose so that +y runs along the beam.
     *
     * @return the beam's length, or 0 when it is too short to draw and the pose was left alone
     */
    public static float alignTo(PoseStack poseStack, Vec3 beam)
    {
        float length = (float) beam.length();
        if (length < MIN_LENGTH)
            return 0;
        var direction = beam.scale(1 / length);

        poseStack.rotate(Axis.YP.rotation(Mth.HALF_PI - (float) Mth.atan2(direction.z, direction.x)));
        poseStack.rotate(Axis.XP.rotation((float) Math.acos(Mth.clamp(direction.y, -1, 1))));
        return length;
    }

    /**
     * An opaque core with a translucent flare around it, spinning the other way.
     */
    public static void submitCoreAndFlare(PoseStack poseStack, SubmitNodeCollector collector, Identifier texture, float length, float spin, float scroll,
                                          int coreColor, float coreRadius, int flareColor, float flareRadius)
    {
        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(texture, false),
                (pose, buffer) -> tube(pose, buffer, coreColor, length, coreRadius, spin, scroll, length * 2));
        collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(texture, true),
                (pose, buffer) -> tube(pose, buffer, flareColor, length, flareRadius, -spin, scroll, length));
    }

    /**
     * A square tube along +y, turned around its axis by the given angle.
     */
    public static void tube(PoseStack.Pose pose, VertexConsumer buffer, int color, float length, float radius, float angle, float vStart, float vLength)
    {
        for (int side = 0; side < 4; side++)
        {
            float a0 = angle + side * Mth.HALF_PI;
            float a1 = a0 - Mth.HALF_PI;
            float x0 = Mth.cos(a0) * radius;
            float z0 = Mth.sin(a0) * radius;
            float x1 = Mth.cos(a1) * radius;
            float z1 = Mth.sin(a1) * radius;

            vertex(pose, buffer, color, x0, length, z0, 1, vStart + vLength);
            vertex(pose, buffer, color, x0, 0, z0, 1, vStart);
            vertex(pose, buffer, color, x1, 0, z1, 0, vStart);
            vertex(pose, buffer, color, x1, length, z1, 0, vStart + vLength);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int color, float x, float y, float z, float u, float v)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }
}
