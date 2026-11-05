package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryChart;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryTile;

/**
 * A small reading low in front of the base, turned towards whoever is looking, so it stays over the dark stone and leaves the outline of the rings alone.
 * The same family as the knowledge repository's ring: MARKER keeps the hues readable, and its quads do not overlap, with the plates a little further back.
 * At most about 50 quads per armillary in range.
 */
final class ArmillaryDialRender
{
    private static final int ACTIVE_RGB = 0xF4D670;
    private static final int LACK_RGB   = 0xEF8A98;
    private static final int TRACK_RGB  = 0x4A3860;
    private static final int PLATE_RGB  = 0x08050C;
    private static final int DOT_RGB    = 0x8F78B8;

    private static final float PLATE_ALPHA = 0.6F;
    private static final float TRACK_ALPHA = 0.8F;
    private static final float ARC_ALPHA   = 0.9F;
    private static final float DOT_ALPHA   = 0.85F;
    private static final float PLATE_DEPTH = 0.012F;

    private static final float HEIGHT = 1.38F;
    private static final float OUT    = 1.7F;

    private static final float RING_OUTER    = 0.175F;
    private static final float RING_INNER    = 0.15F;
    private static final float PLATE_RIM     = 0.015F;
    private static final int   RING_SEGMENTS = 24;
    private static final int   PLATE_SIDES   = 12;
    private static final float MARK_SIZE     = 0.028F;

    private static final float DOT_HALF    = 0.016F;
    private static final float DOT_SPACING = 0.05F;
    private static final float DOT_DROP    = RING_OUTER + PLATE_RIM + 0.04F;
    private static final int   MAX_DOTS    = 10;

    private static final float FADE_NEAR  = 18;
    private static final float FADE_FAR   = 24;
    private static final float HIDE_CLOSE = 2.8F;
    private static final float SHOW_CLOSE = 3.6F;

    private static final float BREATH_RATE = 0.07F;
    private static final float BREATH_LOW  = 0.65F;
    private static final float LACK_PULSE  = 0.12F;
    private static final float FLICKER     = 0.55F;

    static final class Dial
    {
        final int[] dots = new int[MAX_DOTS];
        int     dotCount;
        float   alpha;
        float   yaw;
        float   arc;
        float   time;
        float   falter;
        int     rgb;
        boolean breathing;
    }

    private ArmillaryDialRender()
    {
    }

    static void extract(ArmillaryTile armillary, Dial dial, float time, float partialTicks, Vec3 cameraPosition)
    {
        var center = Vec3.atBottomCenterOf(armillary.getBlockPos());
        double dx = cameraPosition.x - center.x;
        double dz = cameraPosition.z - center.z;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);

        var display = armillary.getDisplay();
        dial.alpha = display.hud(partialTicks)
                     * Mth.clamp((FADE_FAR - distance) / (FADE_FAR - FADE_NEAR), 0, 1)
                     * Mth.clamp((distance - HIDE_CLOSE) / (SHOW_CLOSE - HIDE_CLOSE), 0, 1);
        if (dial.alpha <= 0)
            return;

        var chart = armillary.getChart();
        var phase = chart.getPhase();
        dial.yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        dial.arc = Mth.clamp(display.arc(partialTicks), 0, 1);
        dial.time = time;
        dial.falter = armillary.falter();
        dial.rgb = phase == ArmillaryChart.Phase.SEARCHING ? ACTIVE_RGB : ArmillaryPortalRender.themeColor(armillary.portalTheme());
        dial.breathing = phase == ArmillaryChart.Phase.REACHED;

        dial.dotCount = 0;
        for (var bubble : chart.bubbles())
        {
            if (!bubble.broken() && dial.dotCount < MAX_DOTS)
                dial.dots[dial.dotCount++] = bubble.id() == chart.getTargetId() ? dial.rgb : DOT_RGB;
        }
    }

    static void submit(Dial dial, PoseStack poseStack, SubmitNodeCollector collector)
    {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0, 0.5F);
        poseStack.rotate(MathUtil.quatFromAngleDegrees(dial.yaw, MathUtil.POSITIVE_Y));
        poseStack.translate(0, HEIGHT, OUT);
        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.MARKER, (pose, buffer) -> draw(dial, pose, buffer));
        poseStack.popPose();
    }

    private static void draw(Dial dial, PoseStack.Pose pose, VertexConsumer buffer)
    {
        float breath = dial.breathing ? Mth.lerp(0.5F + 0.5F * Mth.sin(dial.time * BREATH_RATE), BREATH_LOW, 1) : 1;
        float pulse = 0.5F + 0.5F * Mth.sin(dial.time * LACK_PULSE);
        float flicker = 1 - dial.falter * dial.falter * FLICKER * pulse;

        int lit = color(dial.rgb, ARC_ALPHA * dial.alpha * breath * flicker);
        int track = color(ARGB.srgbLerp(dial.falter * pulse, TRACK_RGB, LACK_RGB), TRACK_ALPHA * dial.alpha);

        plate(pose, buffer, color(PLATE_RGB, PLATE_ALPHA * dial.alpha));
        ring(pose, buffer, dial.arc, lit, track);
        mark(pose, buffer, dial.arc > 0.005F ? lit : track);
        dots(dial, pose, buffer);
    }

    private static void plate(PoseStack.Pose pose, VertexConsumer buffer, int color)
    {
        float radius = RING_OUTER + PLATE_RIM;
        for (int side = 0; side < PLATE_SIDES; side++)
        {
            float from = Mth.TWO_PI * side / PLATE_SIDES;
            float to = Mth.TWO_PI * (side + 1) / PLATE_SIDES;
            buffer.addVertex(pose, 0, 0, -PLATE_DEPTH).setColor(color);
            buffer.addVertex(pose, Mth.sin(to) * radius, Mth.cos(to) * radius, -PLATE_DEPTH).setColor(color);
            buffer.addVertex(pose, Mth.sin(from) * radius, Mth.cos(from) * radius, -PLATE_DEPTH).setColor(color);
            buffer.addVertex(pose, 0, 0, -PLATE_DEPTH).setColor(color);
        }
    }

    private static void ring(PoseStack.Pose pose, VertexConsumer buffer, float fill, int lit, int track)
    {
        for (int segment = 0; segment < RING_SEGMENTS; segment++)
        {
            float start = segment / (float) RING_SEGMENTS;
            float end = (segment + 1) / (float) RING_SEGMENTS;
            if (fill >= end)
                arc(pose, buffer, start, end, lit);
            else if (fill <= start)
                arc(pose, buffer, start, end, track);
            else
            {
                arc(pose, buffer, start, fill, lit);
                arc(pose, buffer, fill, end, track);
            }
        }
    }

    /** Clockwise from the top, the angles as fractions of a turn. */
    private static void arc(PoseStack.Pose pose, VertexConsumer buffer, float from, float to, int color)
    {
        float sinFrom = Mth.sin(from * Mth.TWO_PI);
        float cosFrom = Mth.cos(from * Mth.TWO_PI);
        float sinTo = Mth.sin(to * Mth.TWO_PI);
        float cosTo = Mth.cos(to * Mth.TWO_PI);
        buffer.addVertex(pose, sinFrom * RING_OUTER, cosFrom * RING_OUTER, 0).setColor(color);
        buffer.addVertex(pose, sinFrom * RING_INNER, cosFrom * RING_INNER, 0).setColor(color);
        buffer.addVertex(pose, sinTo * RING_INNER, cosTo * RING_INNER, 0).setColor(color);
        buffer.addVertex(pose, sinTo * RING_OUTER, cosTo * RING_OUTER, 0).setColor(color);
    }

    private static void mark(PoseStack.Pose pose, VertexConsumer buffer, int color)
    {
        buffer.addVertex(pose, 0, MARK_SIZE, 0).setColor(color);
        buffer.addVertex(pose, -MARK_SIZE, 0, 0).setColor(color);
        buffer.addVertex(pose, 0, -MARK_SIZE, 0).setColor(color);
        buffer.addVertex(pose, MARK_SIZE, 0, 0).setColor(color);
    }

    /**
     * One for each bubble on the chart, under the ring, the targeted one in the colour of the ring.
     */
    private static void dots(Dial dial, PoseStack.Pose pose, VertexConsumer buffer)
    {
        if (dial.dotCount == 0)
            return;

        float left = -(dial.dotCount - 1) * DOT_SPACING / 2;
        float reach = DOT_HALF + PLATE_RIM;
        rect(pose, buffer, left - reach, -DOT_DROP - reach, -left + reach, -DOT_DROP + reach, -PLATE_DEPTH, color(PLATE_RGB, PLATE_ALPHA * dial.alpha));
        for (int dot = 0; dot < dial.dotCount; dot++)
        {
            float x = left + dot * DOT_SPACING;
            rect(pose, buffer, x - DOT_HALF, -DOT_DROP - DOT_HALF, x + DOT_HALF, -DOT_DROP + DOT_HALF, 0, color(dial.dots[dot], DOT_ALPHA * dial.alpha));
        }
    }

    private static void rect(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float x1, float y1, float z, int color)
    {
        buffer.addVertex(pose, x0, y1, z).setColor(color);
        buffer.addVertex(pose, x0, y0, z).setColor(color);
        buffer.addVertex(pose, x1, y0, z).setColor(color);
        buffer.addVertex(pose, x1, y1, z).setColor(color);
    }

    private static int color(int rgb, float alpha)
    {
        return ARGB.color(Math.round(Mth.clamp(alpha, 0, 1) * 255), rgb);
    }
}
