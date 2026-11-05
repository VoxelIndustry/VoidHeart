package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A torn hole into somewhere else, facing whoever looks at it: near black inside, with an uneven rim in the colour of what lies behind it.
 * The hole blends over what is behind it and the rim adds light; neither writes depth, so nothing they overlap can flicker.
 */
public final class ArmillaryPortalRender
{
    private static final int   SIDES        = 60;
    private static final int   HOLE_DARK    = 0xFC020106;
    private static final float HOLE_TINT    = 0.12F;
    private static final int   HOLE_EDGE_ALPHA = 245;
    private static final float DARK_SHARE   = 0.72F;
    // A rip rather than a disc: taller than wide, drawn to a point at the top and the bottom
    private static final float WIDEN        = 0.74F;
    private static final float STRETCH      = 1.26F;
    private static final float POINTS       = 0.28F;
    private static final int   POINT_SHARPNESS = 8;
    private static final float[][] TEAR     = { { 2, 0.03F, 0.07F }, { 5, -0.05F, 0.05F }, { 11, 0.09F, 0.02F } };
    private static final float[] RIPS       = { 0.5F, 1.75F, 2.45F, 3.9F, 5.3F };
    private static final float[] RIP_LENGTH = { 0.13F, 0.08F, 0.16F, 0.1F, 0.12F };
    private static final float RIP_WIDTH    = 0.12F;
    private static final float RIP_DRIFT    = 0.11F;
    private static final float RIP_SPEED    = 0.013F;
    private static final float JAGGED       = 0.03F;
    private static final float LIP_INNER    = 0.955F;
    private static final float LIP_OUTER    = 1.02F;
    private static final float HALO_OUTER   = 1.5F;
    private static final int   LIP_ALPHA    = 245;
    private static final int   LIP_OUT_ALPHA = 110;
    private static final int   HALO_ALPHA   = 48;
    private static final int   MOTES        = 9;
    private static final float MOTE_SIZE    = 0.022F;
    private static final float MOTE_PERIOD  = 70;
    private static final float MOTE_SPIN    = 1.3F;
    private static final float MOTE_REACH   = 1.75F;
    private static final int   MOTE_ALPHA   = 170;
    private static final int   NEUTRAL_RGB  = 0xB89CF2;
    private static final long  TIME_WRAP    = 1 << 20;

    private ArmillaryPortalRender()
    {
    }

    /** Game time wrapped to what a float holds to the partial tick. The animations it drives can't show the jump, which comes once in many hours of play. */
    public static float animationTime(long gameTime, float partialTicks)
    {
        return gameTime % TIME_WRAP + partialTicks;
    }

    public static int themeColor(@Nullable DungeonTheme theme)
    {
        if (theme == null)
            return NEUTRAL_RGB;

        return switch (theme)
        {
            case CATHEDRAL -> 0xC0A8F0;
            case FOUNDRY -> 0xF08A3C;
            case PALACE -> 0xEFCB6E;
            case TEMPLE -> 0x36C5B0;
            case FOREST -> 0x86D63E;
            case MYCOSIS -> 0xF04F36;
            case DEPTHS -> 0x4A9CE8;
        };
    }

    /**
     * @param center   middle of the hole, relative to the pose
     * @param toCamera from the middle of the hole to the camera
     * @param opened   from 0 when it starts to tear to 1 when fully open
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 center, Vec3 toCamera, float radius, float opened, int rgb, float time)
    {
        if (opened <= 0)
            return;

        var look = new Vector3f((float) toCamera.x, (float) toCamera.y, (float) toCamera.z);
        if (look.lengthSquared() < 1.0E-4F)
            look.set(0, 0, 1);
        look.normalize();

        var right = new Vector3f(0, 1, 0).cross(look);
        if (right.lengthSquared() < 1.0E-4F)
            right.set(1, 0, 0);
        right.normalize();
        var up = new Vector3f(look).cross(right).normalize();

        float size = radius * opened;
        float[] edge = edge(size, time);

        poseStack.pushPose();
        poseStack.translate(center.x, center.y, center.z);
        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.MARKER, (pose, buffer) ->
        {
            hole(pose, buffer, right, up, edge, rgb);
            band(pose, buffer, right, up, edge, LIP_INNER, LIP_OUTER, rgb, LIP_ALPHA, LIP_OUT_ALPHA);
        });
        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            band(pose, buffer, right, up, edge, LIP_OUTER, HALO_OUTER, rgb, HALO_ALPHA, 0);
            motes(pose, buffer, right, up, size, rgb, time);
        });
        poseStack.popPose();
    }

    private static float[] edge(float size, float time)
    {
        float[] edge = new float[SIDES];
        for (int side = 0; side < SIDES; side++)
        {
            float angle = side * Mth.TWO_PI / SIDES;
            float tear = 1;
            for (var wave : TEAR)
                tear += Mth.sin(angle * wave[0] + time * wave[1]) * wave[2];
            for (int rip = 0; rip < RIPS.length; rip++)
            {
                float where = RIPS[rip] + Mth.sin(time * RIP_SPEED + rip * 1.7F) * RIP_DRIFT;
                float away = Mth.abs(Mth.wrapDegrees((angle - where) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD);
                tear += RIP_LENGTH[rip] * Math.max(0, 1 - away / RIP_WIDTH);
            }
            tear += ((side * 7919 % 13) / 6F - 1) * JAGGED;
            tear += POINTS * (float) Math.pow(Math.abs(Mth.sin(angle)), POINT_SHARPNESS);
            edge[side] = size * tear;
        }
        return edge;
    }

    /**
     * Plain black most of the way, so that the fan it is built from can't show, with a little of what lies behind seeping in near the edge.
     */
    private static void hole(PoseStack.Pose pose, VertexConsumer buffer, Vector3f right, Vector3f up, float[] edge, int rgb)
    {
        int rim = ARGB.color(HOLE_EDGE_ALPHA, ARGB.srgbLerp(HOLE_TINT, HOLE_DARK, ARGB.opaque(rgb)));
        for (int side = 0; side < SIDES; side++)
        {
            int next = (side + 1) % SIDES;
            buffer.addVertex(pose, 0, 0, 0).setColor(HOLE_DARK);
            point(pose, buffer, right, up, side, edge[side] * DARK_SHARE, HOLE_DARK);
            point(pose, buffer, right, up, next, edge[next] * DARK_SHARE, HOLE_DARK);
            buffer.addVertex(pose, 0, 0, 0).setColor(HOLE_DARK);

            point(pose, buffer, right, up, side, edge[side] * DARK_SHARE, HOLE_DARK);
            point(pose, buffer, right, up, side, edge[side] * LIP_INNER, rim);
            point(pose, buffer, right, up, next, edge[next] * LIP_INNER, rim);
            point(pose, buffer, right, up, next, edge[next] * DARK_SHARE, HOLE_DARK);
        }
    }

    private static void band(PoseStack.Pose pose, VertexConsumer buffer, Vector3f right, Vector3f up, float[] edge, float inner, float outer, int rgb, int innerAlpha, int outerAlpha)
    {
        int innerColor = ARGB.color(innerAlpha, rgb);
        int outerColor = ARGB.color(outerAlpha, rgb);
        for (int side = 0; side < SIDES; side++)
        {
            int next = (side + 1) % SIDES;
            point(pose, buffer, right, up, side, edge[side] * inner, innerColor);
            point(pose, buffer, right, up, side, edge[side] * outer, outerColor);
            point(pose, buffer, right, up, next, edge[next] * outer, outerColor);
            point(pose, buffer, right, up, next, edge[next] * inner, innerColor);
        }
    }

    private static void point(PoseStack.Pose pose, VertexConsumer buffer, Vector3f right, Vector3f up, int side, float radius, int color)
    {
        float angle = side * Mth.TWO_PI / SIDES;
        float across = Mth.cos(angle) * radius * WIDEN;
        float along = Mth.sin(angle) * radius * STRETCH;
        buffer.addVertex(pose, right.x * across + up.x * along, right.y * across + up.y * along, right.z * across + up.z * along).setColor(color);
    }

    /**
     * Motes drawn in toward the hole, spinning as they go and fading as they fall in.
     */
    private static void motes(PoseStack.Pose pose, VertexConsumer buffer, Vector3f right, Vector3f up, float size, int rgb, float time)
    {
        for (int mote = 0; mote < MOTES; mote++)
        {
            float life = (time / MOTE_PERIOD + mote * 0.618F) % 1;
            float distance = size * (MOTE_REACH - (MOTE_REACH - 1) * life);
            float angle = mote * 2.4F + life * MOTE_SPIN;
            float across = Mth.cos(angle) * distance * WIDEN;
            float along = Mth.sin(angle) * distance * STRETCH;
            float alpha = Mth.sin(life * Mth.PI);
            int color = ARGB.color((int) (alpha * MOTE_ALPHA), rgb);

            float x = right.x * across + up.x * along;
            float y = right.y * across + up.y * along;
            float z = right.z * across + up.z * along;
            buffer.addVertex(pose, x - right.x * MOTE_SIZE, y - right.y * MOTE_SIZE, z - right.z * MOTE_SIZE).setColor(color);
            buffer.addVertex(pose, x - up.x * MOTE_SIZE, y - up.y * MOTE_SIZE, z - up.z * MOTE_SIZE).setColor(color);
            buffer.addVertex(pose, x + right.x * MOTE_SIZE, y + right.y * MOTE_SIZE, z + right.z * MOTE_SIZE).setColor(color);
            buffer.addVertex(pose, x + up.x * MOTE_SIZE, y + up.y * MOTE_SIZE, z + up.z * MOTE_SIZE).setColor(color);
        }
    }
}
