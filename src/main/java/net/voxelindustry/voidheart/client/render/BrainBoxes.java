package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Solid boxes of brain or flesh, for the Cogitant and the Meat Proxy. Their textures are bands 32 pixels wide: the middle of the left half is the top,
 * the middle of the right half the sides, so a box never shows the clear rim the folds were cut from.
 */
final class BrainBoxes
{
    private static final float TOP_U0  = 3 / 32F;
    private static final float TOP_U1  = 13 / 32F;
    private static final float SIDE_U0 = 19 / 32F;
    private static final float SIDE_U1 = 29 / 32F;
    private static final float BAND_V0 = 3;
    private static final float BAND_V1 = 13;
    private static final float BAND    = 16;

    private BrainBoxes()
    {
    }

    /**
     * @param band  which 16 pixel band of the texture to use
     * @param bands how many bands the texture holds
     */
    static void box(PoseStack.Pose pose, VertexConsumer buffer, int light, float x0, float y0, float z0, float x1, float y1, float z1, int band, int bands)
    {
        float v0 = (band * BAND + BAND_V0) / (bands * BAND);
        float v1 = (band * BAND + BAND_V1) / (bands * BAND);

        face(pose, buffer, light, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, TOP_U0, v0, TOP_U1, v1, 0, 1, 0);
        face(pose, buffer, light, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, TOP_U0, v0, TOP_U1, v1, 0, -1, 0);
        face(pose, buffer, light, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, SIDE_U0, v0, SIDE_U1, v1, 0, 0, -1);
        face(pose, buffer, light, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, SIDE_U0, v0, SIDE_U1, v1, 0, 0, 1);
        face(pose, buffer, light, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, SIDE_U0, v0, SIDE_U1, v1, -1, 0, 0);
        face(pose, buffer, light, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, SIDE_U0, v0, SIDE_U1, v1, 1, 0, 0);
    }

    /** Corners from the top left, counterclockwise as seen from outside. */
    private static void face(PoseStack.Pose pose, VertexConsumer buffer, int light,
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

    /** Swells and shrinks around 1, the brain thinking. */
    static float pulse(float time, float periodTicks, float amplitude, int seed)
    {
        return 1 + amplitude * (float) Math.sin(2 * Math.PI * (time / periodTicks) + seed * 0.37F);
    }
}
