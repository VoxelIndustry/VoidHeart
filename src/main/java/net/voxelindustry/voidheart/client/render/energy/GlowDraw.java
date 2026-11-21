package net.voxelindustry.voidheart.client.render.energy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Small drawing helpers shared by the fissure's and the ley's effects, the ley debug view and the Oculus, which draw untextured quads into
 * position-color pipelines such as {@code GLOW}.
 */
public final class GlowDraw
{
    private GlowDraw()
    {
    }

    public static int color(int rgb, float alpha)
    {
        return ARGB.color(Mth.clamp((int) (alpha * 255), 0, 255), rgb);
    }

    public static int lerp(int from, int to, float t)
    {
        return ARGB.srgbLerp(Mth.clamp(t, 0, 1), from | 0xFF000000, to | 0xFF000000) & 0xFFFFFF;
    }

    /**
     * A cheap hash of a few longs into 0..1, for stable randomness that never rerolls between frames.
     */
    public static float hash(long a, long b, long c)
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
    public static Vector3f relative(double x, double y, double z, Vec3 camera)
    {
        return new Vector3f((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z));
    }

    public static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vector3f at, int color)
    {
        buffer.addVertex(pose, at.x, at.y, at.z).setColor(color);
    }

    public static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int color)
    {
        buffer.addVertex(pose, x, y, z).setColor(color);
    }

    /**
     * A soft round dot facing the camera: bright at the center, fading to nothing at its rim. Drawn as a fan of quads, since the glow pipeline has no texture.
     */
    public static void softDot(VertexConsumer buffer, PoseStack.Pose pose, Vector3f center, float radius, int rgb, float alpha)
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
    public static Vector3f facingSide(Vector3f start, Vector3f end)
    {
        return facingSide(start, end, 1.0E-6F);
    }

    /**
     * @param degenerate below this squared length the segment is taken to point at the camera, and the side falls back to east
     */
    public static Vector3f facingSide(Vector3f start, Vector3f end, float degenerate)
    {
        var direction = new Vector3f(end).sub(start);
        var toCamera = new Vector3f(start).add(end).mul(-0.5F);
        var side = direction.cross(toCamera);
        return side.lengthSquared() < degenerate ? new Vector3f(1, 0, 0) : side.normalize();
    }

    /**
     * A flat ribbon from one point to the next, as wide as {@code halfWidth} on each side of it along {@code side}.
     */
    public static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vector3f start, Vector3f end, Vector3f side, float halfWidth, int color)
    {
        float sx = side.x * halfWidth;
        float sy = side.y * halfWidth;
        float sz = side.z * halfWidth;
        buffer.addVertex(pose, start.x - sx, start.y - sy, start.z - sz).setColor(color);
        buffer.addVertex(pose, start.x + sx, start.y + sy, start.z + sz).setColor(color);
        buffer.addVertex(pose, end.x + sx, end.y + sy, end.z + sz).setColor(color);
        buffer.addVertex(pose, end.x - sx, end.y - sy, end.z - sz).setColor(color);
    }

    /**
     * A flat band lying along the ground from one point to the next, as the dashes of a ring.
     */
    public static void flatSegment(PoseStack.Pose pose, VertexConsumer buffer, Vector3f a, Vector3f b, float halfWidth, int color)
    {
        float dx = b.x - a.x, dz = b.z - a.z;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-5F)
            return;
        float sx = -dz / length * halfWidth, sz = dx / length * halfWidth;
        vertex(buffer, pose, a.x - sx, a.y, a.z - sz, color);
        vertex(buffer, pose, a.x + sx, a.y, a.z + sz, color);
        vertex(buffer, pose, b.x + sx, b.y, b.z + sz, color);
        vertex(buffer, pose, b.x - sx, b.y, b.z - sz, color);
    }

    /**
     * The height at a fraction of the way round a ring, between heights sampled evenly around it.
     */
    public static double ringHeight(double[] heights, double fraction)
    {
        double index = ((fraction % 1) + 1) % 1 * heights.length;
        int i = (int) index % heights.length;
        return Mth.lerp(index - Math.floor(index), heights[i], heights[(i + 1) % heights.length]);
    }
}
