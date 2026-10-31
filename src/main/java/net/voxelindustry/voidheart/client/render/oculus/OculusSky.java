package net.voxelindustry.voidheart.client.render.oculus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import org.joml.Vector3f;

/**
 * The sun and moon over each table, on the arc they travel through the sky: rising east, setting west.
 * They share that arc in Minecraft, half a turn apart, so it is drawn once.
 */
public final class OculusSky
{
    private static final float MIN_RADIUS = 1;
    private static final float MAX_RADIUS = 3;
    private static final int ARC_SEGMENTS = 48;
    private static final float ARC_HALF_WIDTH = 0.006F;
    private static final int ARC_COLOR = ARGB.color(55, 214, 170, 255);

    private static final float SUN_RADIUS = 0.09F;
    private static final int SUN_COLOR = 0xFFFFF1C4;
    private static final int SUN_HALO = ARGB.color(60, 255, 196, 120);
    private static final int SUN_RUNES = 8;

    private static final float MOON_RADIUS = 0.07F;
    private static final int MOON_COLOR = 0xFFE2DAFF;
    private static final int MOON_HALO = ARGB.color(40, 190, 160, 255);
    private static final int MOON_SHADOW = 0xFF120A1C;

    private static final int DISC_SEGMENTS = 16;

    private OculusSky()
    {
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        var levelState = context.levelState();
        var sky = levelState.skyRenderState;
        if (sky.skybox != DimensionType.Skybox.OVERWORLD || OculusRenderer.projectors().isEmpty())
            return;

        var camera = levelState.cameraRenderState;
        var right = new Vector3f(1, 0, 0).rotate(camera.orientation);
        var up = new Vector3f(0, 1, 0).rotate(camera.orientation);
        float seconds = (levelState.gameTime % 24000L + levelState.worldPartialTicks) / 20F;

        for (var projector : OculusRenderer.projectors())
        {
            if (projector.view().asleep())
                continue;
            var footprint = projector.footprint();
            float radius = Mth.clamp(Math.max(footprint.width(), footprint.depth()) / 2F + 0.25F, MIN_RADIUS, MAX_RADIUS);
            var center = new Vec3(footprint.centerX(), projector.hologramTop() - 1, footprint.centerZ()).subtract(camera.pos).toVector3f();
            var sun = bodyAt(center, radius, sky.sunAngle);
            var moon = bodyAt(center, radius, sky.moonAngle);

            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), VoidHeartRenderTypes.GLOW, (pose, buffer) ->
            {
                drawArc(pose, buffer, center, radius);
                if (sun.y > center.y)
                    disc(pose, buffer, sun, right, up, SUN_RADIUS * 2.6F, SUN_HALO);
                if (moon.y > center.y)
                    disc(pose, buffer, moon, right, up, MOON_RADIUS * 2.6F, MOON_HALO);
            });
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), VoidHeartRenderTypes.MARKER, (pose, buffer) ->
            {
                if (sun.y > center.y)
                    drawSun(pose, buffer, sun, right, up, seconds);
                if (moon.y > center.y)
                    drawMoon(pose, buffer, moon, right, up, sky.moonPhase);
            });
        }
    }

    /**
     * Where a body stands on the arc. As in the sky, an angle of zero is straight up and the arc runs east to west.
     */
    private static Vector3f bodyAt(Vector3f center, float radius, float angle)
    {
        return new Vector3f(center).add(-Mth.sin(angle) * radius, Mth.cos(angle) * radius, 0);
    }

    /**
     * The half of the arc above the table, dotted so it stays faint.
     */
    private static void drawArc(PoseStack.Pose pose, VertexConsumer buffer, Vector3f center, float radius)
    {
        for (int segment = 0; segment < ARC_SEGMENTS; segment += 2)
        {
            double from = Math.PI * segment / ARC_SEGMENTS;
            double to = Math.PI * (segment + 1) / ARC_SEGMENTS;
            var start = new Vector3f(center).add((float) Math.cos(from) * radius, (float) Math.sin(from) * radius, 0);
            var end = new Vector3f(center).add((float) Math.cos(to) * radius, (float) Math.sin(to) * radius, 0);

            // Across the arc and the view, so the stroke faces the camera
            var side = new Vector3f(end).sub(start).cross(new Vector3f(start).add(end).mul(-0.5F));
            if (side.lengthSquared() < 1.0E-12F)
                continue;
            side.normalize(ARC_HALF_WIDTH);
            buffer.addVertex(pose, start.x - side.x, start.y - side.y, start.z - side.z).setColor(ARC_COLOR);
            buffer.addVertex(pose, start.x + side.x, start.y + side.y, start.z + side.z).setColor(ARC_COLOR);
            buffer.addVertex(pose, end.x + side.x, end.y + side.y, end.z + side.z).setColor(ARC_COLOR);
            buffer.addVertex(pose, end.x - side.x, end.y - side.y, end.z - side.z).setColor(ARC_COLOR);
        }
    }

    /**
     * A pale disc ringed by rune marks slowly turning around it.
     */
    private static void drawSun(PoseStack.Pose pose, VertexConsumer buffer, Vector3f sun, Vector3f right, Vector3f up, float seconds)
    {
        disc(pose, buffer, sun, right, up, SUN_RADIUS, SUN_COLOR);
        for (int rune = 0; rune < SUN_RUNES; rune++)
        {
            double angle = seconds * 0.3 + rune * Math.PI * 2 / SUN_RUNES;
            float inner = SUN_RADIUS * (rune % 2 == 0 ? 1.35F : 1.5F);
            float outer = SUN_RADIUS * 1.85F;
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            var from = new Vector3f(sun).add(new Vector3f(right).mul(cos * inner)).add(new Vector3f(up).mul(sin * inner));
            var to = new Vector3f(sun).add(new Vector3f(right).mul(cos * outer)).add(new Vector3f(up).mul(sin * outer));
            var side = new Vector3f(right).mul(-sin * 0.006F).add(new Vector3f(up).mul(cos * 0.006F));
            buffer.addVertex(pose, from.x - side.x, from.y - side.y, from.z - side.z).setColor(SUN_COLOR);
            buffer.addVertex(pose, to.x - side.x, to.y - side.y, to.z - side.z).setColor(SUN_COLOR);
            buffer.addVertex(pose, to.x + side.x, to.y + side.y, to.z + side.z).setColor(SUN_COLOR);
            buffer.addVertex(pose, from.x + side.x, from.y + side.y, from.z + side.z).setColor(SUN_COLOR);
        }
    }

    /**
     * A disc darkened by a shadow sliding across it with the phase: off the disc at full moon, over it at new moon.
     */
    private static void drawMoon(PoseStack.Pose pose, VertexConsumer buffer, Vector3f moon, Vector3f right, Vector3f up, MoonPhase phase)
    {
        disc(pose, buffer, moon, right, up, MOON_RADIUS, MOON_COLOR);

        int index = phase.index();
        if (index == 0)
            return;
        // Waning phases darken from one side, waxing ones uncover from the other
        float offset = MOON_RADIUS * 2 * (1 - Math.abs(index - 4) / 4F) * (index < 4 ? -1 : 1);
        // Positions are relative to the camera: pulled toward it, the shadow is sorted in front of the disc
        var shadow = new Vector3f(moon).add(new Vector3f(right).mul(offset)).add(new Vector3f(moon).normalize(-0.004F));
        disc(pose, buffer, shadow, right, up, MOON_RADIUS * 1.02F, MOON_SHADOW);
    }

    /**
     * A disc facing the camera, as a fan of triangles folded into quads.
     */
    private static void disc(PoseStack.Pose pose, VertexConsumer buffer, Vector3f center, Vector3f right, Vector3f up, float radius, int color)
    {
        for (int i = 0; i < DISC_SEGMENTS; i++)
        {
            double from = i * Math.PI * 2 / DISC_SEGMENTS;
            double to = (i + 1) * Math.PI * 2 / DISC_SEGMENTS;
            var a = new Vector3f(center).add(new Vector3f(right).mul((float) Math.cos(from) * radius)).add(new Vector3f(up).mul((float) Math.sin(from) * radius));
            var b = new Vector3f(center).add(new Vector3f(right).mul((float) Math.cos(to) * radius)).add(new Vector3f(up).mul((float) Math.sin(to) * radius));
            buffer.addVertex(pose, center.x, center.y, center.z).setColor(color);
            buffer.addVertex(pose, center.x, center.y, center.z).setColor(color);
            buffer.addVertex(pose, b.x, b.y, b.z).setColor(color);
            buffer.addVertex(pose, a.x, a.y, a.z).setColor(color);
        }
    }
}
