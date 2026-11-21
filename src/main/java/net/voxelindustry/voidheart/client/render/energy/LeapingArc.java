package net.voxelindustry.voidheart.client.render.energy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * The jagged leaping arc the Ravenous Engine, the Azurium Spike, the Void Fissure and the Contravoid Reactor throw to what takes their energy, in the
 * pylons' style: an azurium-light core in an azurium-mid halo, with brighter stretches running along it.
 * <p>
 * Drawn into additive {@code GLOW}, so its crossed planes overlap order-independently. Up to about 200 quads for a 16-block arc, rebuilt each frame from
 * its seed, nothing cached.
 */
public final class LeapingArc
{
    // Azurium tones sampled from the bundle textures
    private static final int AZURIUM_LIGHT = 0x9EECFF;
    private static final int AZURIUM_MID   = 0x3D7ED9;

    private static final float JOINT_SPACING = 1.5F;
    private static final int   MIN_JOINTS    = 6;
    private static final float JOINT_OFFSET  = 0.25F;
    private static final int   SUBDIVISIONS  = 4;
    private static final float CORE_WIDTH    = 0.035F;
    private static final float HALO_WIDTH    = 0.09F;
    private static final float CORE_ALPHA    = 0.75F;
    private static final float HALO_ALPHA    = 0.25F;
    private static final float LEAP_LENGTH   = 1.5F;
    private static final float LEAP_WIDEN    = 1.6F;

    private LeapingArc()
    {
    }

    /**
     * An arc whose leaps run away from {@code start}.
     */
    public static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vector3f start, Vec3 end, long seed, List<Float> leapHeads, float fade)
    {
        draw(pose, buffer, start, new Vector3f((float) end.x, (float) end.y, (float) end.z), seed, leapHeads, false, fade);
    }

    /**
     * @param leapHeads how far each leap has run, from 0 to 1
     * @param inward    whether the leaps run toward {@code start} instead
     */
    public static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vector3f start, Vector3f end, long seed, List<Float> leapHeads, boolean inward,
                            float fade)
    {
        var axis = new Vector3f(end).sub(start);
        var length = axis.length();
        if (length < 0.5F)
            return;
        axis.div(length);

        // Two directions across the arc to offset its joints and cross its planes
        var side = new Vector3f(axis).cross(Math.abs(axis.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        var up = new Vector3f(side).cross(axis).normalize();

        var joints = Math.max(MIN_JOINTS, Mth.ceil(length / JOINT_SPACING));
        var random = RandomSource.create(seed);
        var points = new Vector3f[joints + 1];
        for (int i = 0; i <= joints; i++)
        {
            var point = new Vector3f(start).lerp(end, i / (float) joints);
            if (i > 0 && i < joints)
            {
                point.add(new Vector3f(side).mul((random.nextFloat() * 2 - 1) * JOINT_OFFSET));
                point.add(new Vector3f(up).mul((random.nextFloat() * 2 - 1) * JOINT_OFFSET));
            }
            points[i] = point;
        }

        var pieces = joints * SUBDIVISIONS;
        for (int piece = 0; piece < pieces; piece++)
        {
            var joint = piece / SUBDIVISIONS;
            var from = new Vector3f(points[joint]).lerp(points[joint + 1], (piece % SUBDIVISIONS) / (float) SUBDIVISIONS);
            var to = new Vector3f(points[joint]).lerp(points[joint + 1], (piece % SUBDIVISIONS + 1) / (float) SUBDIVISIONS);

            var along = (piece + 0.5F) / pieces * length;
            var leap = 0F;
            for (var head : leapHeads)
            {
                var progress = inward ? 1 - head : head;
                var center = progress * (length + LEAP_LENGTH) - LEAP_LENGTH / 2;
                leap = Math.max(leap, 1 - Math.abs(along - center) / (LEAP_LENGTH / 2));
            }

            var widen = 1 + (LEAP_WIDEN - 1) * leap;
            ribbon(pose, buffer, from, to, side, up, HALO_WIDTH * widen, alpha(HALO_ALPHA * fade * (1 + leap), AZURIUM_MID));
            ribbon(pose, buffer, from, to, side, up, CORE_WIDTH * widen, alpha(Math.min(1, CORE_ALPHA * (1 + leap)) * fade,
                    ARGB.srgbLerp(0.6F * leap, AZURIUM_LIGHT, 0xFFFFFF)));
        }
    }

    /**
     * Two crossed planes along a piece of an arc, so it reads from every side but straight down its length.
     */
    public static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vector3f from, Vector3f to, Vector3f side, Vector3f up, float width, int color)
    {
        for (var across : new Vector3f[]{side, up})
        {
            var offset = new Vector3f(across).mul(width);
            buffer.addVertex(pose, from.x - offset.x, from.y - offset.y, from.z - offset.z).setColor(color);
            buffer.addVertex(pose, from.x + offset.x, from.y + offset.y, from.z + offset.z).setColor(color);
            buffer.addVertex(pose, to.x + offset.x, to.y + offset.y, to.z + offset.z).setColor(color);
            buffer.addVertex(pose, to.x - offset.x, to.y - offset.y, to.z - offset.z).setColor(color);
        }
    }

    private static int alpha(float alpha, int color)
    {
        return ARGB.color(Math.round(Mth.clamp(alpha, 0, 1) * 255), color);
    }
}
