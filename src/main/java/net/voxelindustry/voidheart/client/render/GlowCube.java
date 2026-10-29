package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;

/**
 * Full-bright textured cube faces, used for the barrier link wave and the weaver preview.
 */
public class GlowCube
{
    private static final int FULL_BRIGHT = 15728880;

    private static final float[][] UVS = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};

    public static void cube(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float inflate, int color)
    {
        for (var direction : Direction.values())
            face(pose, buffer, direction, x, y, z, inflate, color);
    }

    public static void face(PoseStack.Pose pose, VertexConsumer buffer, Direction direction, float x, float y, float z, float inflate, int color)
    {
        float x0 = x - inflate, y0 = y - inflate, z0 = z - inflate;
        float x1 = x + 1 + inflate, y1 = y + 1 + inflate, z1 = z + 1 + inflate;

        // Corners walked counter-clockwise when looking at the face from outside
        float[][] corners = switch (direction)
        {
            case DOWN -> new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
            case UP -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
            case NORTH -> new float[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
            case SOUTH -> new float[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
            case WEST -> new float[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
            case EAST -> new float[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
        };

        var normal = direction.getUnitVec3i();
        for (int i = 0; i < 4; i++)
            vertex(pose, buffer, corners[i][0], corners[i][1], corners[i][2], UVS[i][0], UVS[i][1], color, normal.getX(), normal.getY(), normal.getZ());
    }

    public static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int color, int nx, int ny, int nz)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(ARGB.red(color), ARGB.green(color), ARGB.blue(color), ARGB.alpha(color))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }
}
