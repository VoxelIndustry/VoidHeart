package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Draws a tentacle as a run of square boxes along a path, each a little narrower than the last, turned a little more about the path, so it reads
 * as pixel flesh rather than a smooth tube. Each box shows the whole texture down each side.
 */
public final class FleshTubes
{
    private FleshTubes()
    {
    }

    /**
     * @param points the path, from root to tip, in the pose's space
     * @param root   the half width at the root
     * @param tip    the half width at the tip
     */
    public static void tube(PoseStack.Pose pose, VertexConsumer buffer, List<Vec3> points, float root, float tip, float twist, int light)
    {
        int count = points.size() - 1;
        for (int i = 0; i < count; i++)
        {
            float t0 = (float) i / count, t1 = (float) (i + 1) / count;
            // Each box runs a hair past the next one's start, so no gap opens where the path bends
            var from = points.get(i);
            var to = points.get(i + 1);
            var along = to.subtract(from);
            box(pose, buffer, from.subtract(along.scale(0.08)), to, root + (tip - root) * t0, root + (tip - root) * (t0 + t1) / 2, twist * i, light);
        }
    }

    /** A box running from one point to another, square across with the given half widths at each end. */
    public static void box(PoseStack.Pose pose, VertexConsumer buffer, Vec3 from, Vec3 to, float halfFrom, float halfTo, float twist, int light)
    {
        var dir = to.subtract(from);
        if (dir.lengthSqr() < 1e-8)
            return;
        dir = dir.normalize();
        var up = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        var a = dir.cross(up).normalize();
        var b = dir.cross(a).normalize();
        double cos = Math.cos(twist), sin = Math.sin(twist);
        var side1 = a.scale(cos).add(b.scale(sin));
        var side2 = dir.cross(side1).normalize();

        var corners = new Vec3[8];
        for (int k = 0; k < 4; k++)
        {
            double s1 = (k == 0 || k == 3) ? -1 : 1, s2 = k < 2 ? -1 : 1;
            corners[k] = from.add(side1.scale(s1 * halfFrom)).add(side2.scale(s2 * halfFrom));
            corners[k + 4] = to.add(side1.scale(s1 * halfTo)).add(side2.scale(s2 * halfTo));
        }
        for (int k = 0; k < 4; k++)
        {
            int n = (k + 1) % 4;
            var normal = corners[k].add(corners[n]).scale(0.5).subtract(from).normalize();
            quad(pose, buffer, corners[k], corners[n], corners[n + 4], corners[k + 4], normal, light);
        }
        quad(pose, buffer, corners[3], corners[2], corners[1], corners[0], dir.reverse(), light);
        quad(pose, buffer, corners[4], corners[5], corners[6], corners[7], dir, light);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal, int light)
    {
        vertex(pose, buffer, a, 0, 1, normal, light);
        vertex(pose, buffer, b, 1, 1, normal, light);
        vertex(pose, buffer, c, 1, 0, normal, light);
        vertex(pose, buffer, d, 0, 0, normal, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, float u, float v, Vec3 normal, int light)
    {
        buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
