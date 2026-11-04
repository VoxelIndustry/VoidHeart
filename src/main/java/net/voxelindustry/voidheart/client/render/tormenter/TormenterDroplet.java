package net.voxelindustry.voidheart.client.render.tormenter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.common.content.tormenter.TormenterDropletShape;

/**
 * A huge droplet of flesh flowing upward out of its block: a narrow tip at the bottom, a wide belly above it and a round
 * dome on top, three blocks wide at most and four tall. It is a stack of flat cuboids rising through that outline, each widening and
 * narrowing as it goes, which keeps it blocky while the whole of it moves. Coordinates start from the middle of the base.
 */
final class TormenterDroplet
{
    static final float HEIGHT     = TormenterDropletShape.HEIGHT;
    static final float MOST_WIDTH = TormenterDropletShape.MOST_WIDTH;

    private static final float TIP_WIDTH  = 0.22F;
    private static final float TIP_HEIGHT = 0.5F;

    static final int LAYERS = 24;

    private static final float LAYER_HEIGHT = HEIGHT / LAYERS;

    private static final float SWELL  = 0.035F;
    private static final float WANDER = 0.015F;

    /**
     * Greatest distance any layer can stray from the outline.
     */
    static final float MOST_STRAY = SWELL + WANDER;

    private TormenterDroplet()
    {
    }

    /**
     * @param rise share of the height, 0 at the tip and 1 on top of the dome
     * @return half the width of the droplet at that height
     */
    static float halfWidth(float rise)
    {
        return TormenterDropletShape.halfWidth(rise);
    }

    /**
     * @param flow  how many layers' worth of climb it has done so far, wrapped at any whole number of {@code LAYERS}
     * @param swell an angle going around at the pace of the swelling
     * @param vigor share of the full swelling
     */
    static void render(PoseStack.Pose pose, VertexConsumer buffer, int light, float flow, float swell, float vigor)
    {
        box(pose, buffer, light, -TIP_WIDTH, 0, -TIP_WIDTH, TIP_WIDTH, TIP_HEIGHT, TIP_WIDTH, 0);

        for (var layer = 0; layer < LAYERS; layer++)
        {
            var climbed = (flow + layer) / LAYERS;
            var rise = climbed - Mth.floor(climbed);
            var bottom = rise * HEIGHT;
            var half = halfWidth(rise + 0.5F / LAYERS) + vigor * SWELL * Mth.sin(swell + layer * 1.9F);
            if (half <= 0)
                continue;

            var centerX = vigor * WANDER * Mth.sin(swell * 2 + layer * 2.7F);
            var centerZ = vigor * WANDER * Mth.cos(swell * 2 + layer * 1.3F);
            var narrow = half * TormenterDropletShape.ROUNDING;
            var band = layer * LAYER_HEIGHT;
            box(pose, buffer, light, centerX - half, bottom, centerZ - narrow, centerX + half, bottom + LAYER_HEIGHT, centerZ + narrow, band);
            box(pose, buffer, light, centerX - narrow, bottom, centerZ - half, centerX + narrow, bottom + LAYER_HEIGHT, centerZ + half, band);
        }
    }

    /**
     * The skin keeps to the texture density of a block and wraps around, each layer carrying its own band of it upward.
     */
    private static void box(PoseStack.Pose pose, VertexConsumer buffer, int light, float x0, float y0, float z0, float x1, float y1, float z1, float band)
    {
        var v0 = band;
        var v1 = band + y1 - y0;
        side(pose, buffer, light, x1, y0, z0, x0, y1, z0, 0, -1, x1, x0, v0, v1);
        side(pose, buffer, light, x1, y0, z1, x1, y1, z0, 1, 0, z1, z0, v0, v1);
        side(pose, buffer, light, x0, y0, z1, x1, y1, z1, 0, 1, x0, x1, v0, v1);
        side(pose, buffer, light, x0, y0, z0, x0, y1, z1, -1, 0, z0, z1, v0, v1);

        vertex(pose, buffer, light, x0, y1, z0, x0, z0, 0, 1, 0);
        vertex(pose, buffer, light, x0, y1, z1, x0, z1, 0, 1, 0);
        vertex(pose, buffer, light, x1, y1, z1, x1, z1, 0, 1, 0);
        vertex(pose, buffer, light, x1, y1, z0, x1, z0, 0, 1, 0);

        vertex(pose, buffer, light, x0, y0, z1, x0, z1, 0, -1, 0);
        vertex(pose, buffer, light, x0, y0, z0, x0, z0, 0, -1, 0);
        vertex(pose, buffer, light, x1, y0, z0, x1, z0, 0, -1, 0);
        vertex(pose, buffer, light, x1, y0, z1, x1, z1, 0, -1, 0);
    }

    /** From the bottom left to the top right, as seen from outside. */
    private static void side(PoseStack.Pose pose, VertexConsumer buffer, int light, float leftX, float bottom, float leftZ, float rightX, float top, float rightZ, int normalX, int normalZ, float leftU, float rightU, float v0, float v1)
    {
        vertex(pose, buffer, light, leftX, top, leftZ, leftU, v0, normalX, 0, normalZ);
        vertex(pose, buffer, light, leftX, bottom, leftZ, leftU, v1, normalX, 0, normalZ);
        vertex(pose, buffer, light, rightX, bottom, rightZ, rightU, v1, normalX, 0, normalZ);
        vertex(pose, buffer, light, rightX, top, rightZ, rightU, v0, normalX, 0, normalZ);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int light, float x, float y, float z, float u, float v, int normalX, int normalY, int normalZ)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, normalX, normalY, normalZ);
    }
}
