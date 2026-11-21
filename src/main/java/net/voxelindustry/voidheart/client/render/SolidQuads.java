package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Textured, world-lit quads in white, for hand-built boxes drawn into an entity render type such as {@code entitySolid}.
 */
public final class SolidQuads
{
    private SolidQuads()
    {
    }

    /**
     * Corners from the top left, counterclockwise as seen from outside, so culling render types keep the face.
     */
    public static void quad(PoseStack.Pose pose, VertexConsumer buffer, int light,
                            float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                            float u0, float v0, float u1, float v1, int nx, int ny, int nz)
    {
        vertex(pose, buffer, light, ax, ay, az, u0, v0, nx, ny, nz);
        vertex(pose, buffer, light, bx, by, bz, u0, v1, nx, ny, nz);
        vertex(pose, buffer, light, cx, cy, cz, u1, v1, nx, ny, nz);
        vertex(pose, buffer, light, dx, dy, dz, u1, v0, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int light, float x, float y, float z, float u, float v, int nx, int ny, int nz)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
