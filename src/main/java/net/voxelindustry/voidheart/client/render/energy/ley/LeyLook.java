package net.voxelindustry.voidheart.client.render.energy.ley;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Colors and small drawing helpers shared by every ley effect, from docs/plans/energy-looks.md.
 */
final class LeyLook
{
    /**
     * An awake Old Anchor.
     */
    static final int TEAL       = 0x36C5B0;
    static final int TEAL_LIGHT = 0x6BDBC0;
    /**
     * A dormant Old Anchor.
     */
    static final int MAUVE      = 0x8A6A94;
    /**
     * Where a weakened ward's motes and ring drift to.
     */
    static final int MAUVE_GREY = 0x8C8292;

    /**
     * The flow at which ley effects show at their brightest, the p90 line from N0.
     */
    static final float FULL_FLOW = 7;

    private LeyLook()
    {
    }

    /**
     * How bright a flow shows, from 0 to 1. A square root, so weak lines still show and the tides still swell.
     */
    static float flowLevel(float flow)
    {
        return flow <= 0 ? 0 : Mth.clamp((float) Math.sqrt(flow / FULL_FLOW), 0, 1);
    }

    static int lerp(int from, int to, float t)
    {
        return ARGB.srgbLerp(Mth.clamp(t, 0, 1), from | 0xFF000000, to | 0xFF000000) & 0xFFFFFF;
    }

    static int color(int rgb, float alpha)
    {
        return ARGB.color(Mth.clamp((int) (alpha * 255), 0, 255), rgb);
    }

    /**
     * A cheap hash of a few ints into 0..1, for stable per-element randomness that never rerolls between frames.
     */
    static float hash(long a, long b, long c)
    {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + c * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 40) / (float) (1L << 24);
    }

    /**
     * Camera relative position, subtracted in double so it stays precise far from the world origin.
     */
    static Vector3f relative(double x, double y, double z, Vec3 camera)
    {
        return new Vector3f((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z));
    }

    static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vector3f at, int color)
    {
        buffer.addVertex(pose, at.x, at.y, at.z).setColor(color);
    }

    static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int color)
    {
        buffer.addVertex(pose, x, y, z).setColor(color);
    }

    /**
     * A soft round dot facing the camera: bright at the center, fading to nothing at its rim. Drawn as a fan of quads, since the glow pipeline has no texture.
     */
    static void softDot(VertexConsumer buffer, PoseStack.Pose pose, Vector3f center, float radius, int rgb, float alpha)
    {
        if (alpha <= 0.004F)
            return;
        var toCamera = new Vector3f(center).negate();
        if (toCamera.lengthSquared() < 1.0E-6F)
            return;
        toCamera.normalize();
        var right = new Vector3f(0, 1, 0).cross(toCamera);
        if (right.lengthSquared() < 1.0E-6F)
            right.set(1, 0, 0);
        right.normalize();
        var up = new Vector3f(toCamera).cross(right).normalize();

        int core = color(rgb, alpha);
        int rim = color(rgb, 0);
        int segments = 8;
        for (int i = 0; i < segments; i++)
        {
            float a0 = Mth.TWO_PI * i / segments;
            float a1 = Mth.TWO_PI * (i + 1) / segments;
            vertex(buffer, pose, center, core);
            vertex(buffer, pose, center, core);
            vertex(buffer, pose, edge(center, right, up, a0, radius), rim);
            vertex(buffer, pose, edge(center, right, up, a1, radius), rim);
        }
    }

    private static Vector3f edge(Vector3f center, Vector3f right, Vector3f up, float angle, float radius)
    {
        return new Vector3f(center)
                .add(right.x * Mth.cos(angle) * radius, right.y * Mth.cos(angle) * radius, right.z * Mth.cos(angle) * radius)
                .add(up.x * Mth.sin(angle) * radius, up.y * Mth.sin(angle) * radius, up.z * Mth.sin(angle) * radius);
    }

    /**
     * Unit vector across a segment, perpendicular to both the segment and the view, so a ribbon along it faces the camera.
     */
    static Vector3f facingSide(Vector3f start, Vector3f end)
    {
        var direction = new Vector3f(end).sub(start);
        var toCamera = new Vector3f(start).add(end).mul(-0.5F);
        var side = direction.cross(toCamera);
        return side.lengthSquared() < 1.0E-6F ? new Vector3f(1, 0, 0) : side.normalize();
    }
}
