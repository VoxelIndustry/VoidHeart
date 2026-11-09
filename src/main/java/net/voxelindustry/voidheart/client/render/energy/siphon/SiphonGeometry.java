package net.voxelindustry.voidheart.client.render.energy.siphon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;
import org.joml.Vector3f;

/**
 * Camera-facing strips and discs for additive glow, with the camera at a given point of the pose's space.
 */
final class SiphonGeometry
{
    private static final int DISC_SEGMENTS = 16;

    private SiphonGeometry()
    {
    }

    static Vector3f perpendicular(Vector3f direction)
    {
        var side = new Vector3f(direction).cross(0, 1, 0);
        if (side.lengthSquared() < 1.0E-6F)
            side = new Vector3f(direction).cross(1, 0, 0);
        return side.normalize();
    }

    /**
     * A strip from start to end turned to face the camera, its color fading from one end to the other.
     */
    static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vector3f camera, Vector3f start, Vector3f end, float halfWidth, int startColor, int endColor)
    {
        var direction = new Vector3f(end).sub(start);
        var toCamera = new Vector3f(camera).sub(new Vector3f(start).add(end).mul(0.5F));
        var side = direction.cross(toCamera);
        if (side.lengthSquared() < 1.0E-8F)
            return;
        side.normalize(halfWidth);

        buffer.addVertex(pose, start.x - side.x, start.y - side.y, start.z - side.z).setColor(startColor);
        buffer.addVertex(pose, start.x + side.x, start.y + side.y, start.z + side.z).setColor(startColor);
        buffer.addVertex(pose, end.x + side.x, end.y + side.y, end.z + side.z).setColor(endColor);
        buffer.addVertex(pose, end.x - side.x, end.y - side.y, end.z - side.z).setColor(endColor);
    }

    static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vector3f camera, Vector3f start, Vector3f end, float halfWidth, int color)
    {
        ribbon(pose, buffer, camera, start, end, halfWidth, color, color);
    }

    /**
     * A camera-facing disc fading from its color at the center to nothing at its rim, as degenerate quads.
     */
    static void disc(PoseStack.Pose pose, VertexConsumer buffer, Vector3f camera, Vector3f center, float radius, int color)
    {
        var toCamera = new Vector3f(camera).sub(center);
        if (toCamera.lengthSquared() < 1.0E-6F)
            return;
        var right = perpendicular(toCamera).mul(radius);
        var up = new Vector3f(toCamera).cross(right).normalize(radius);
        int rim = ARGB.color(0, color);
        for (int i = 0; i < DISC_SEGMENTS; i++)
        {
            double a = Math.PI * 2 * i / DISC_SEGMENTS;
            double b = Math.PI * 2 * (i + 1) / DISC_SEGMENTS;
            var edgeA = new Vector3f(center).add(new Vector3f(right).mul((float) Math.cos(a))).add(new Vector3f(up).mul((float) Math.sin(a)));
            var edgeB = new Vector3f(center).add(new Vector3f(right).mul((float) Math.cos(b))).add(new Vector3f(up).mul((float) Math.sin(b)));
            buffer.addVertex(pose, center.x, center.y, center.z).setColor(color);
            buffer.addVertex(pose, center.x, center.y, center.z).setColor(color);
            buffer.addVertex(pose, edgeA.x, edgeA.y, edgeA.z).setColor(rim);
            buffer.addVertex(pose, edgeB.x, edgeB.y, edgeB.z).setColor(rim);
        }
    }
}
