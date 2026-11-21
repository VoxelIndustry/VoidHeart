package net.voxelindustry.voidheart.client.render.blight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.content.blight.BlightBalance;
import net.voxelindustry.voidheart.common.content.blight.SpearHole;
import net.voxelindustry.voidheart.common.content.blight.ThrownBlightSpear;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The spear's sphere of nothing: a disc of black facing the camera, so from any side it is a hole, with a thin pale edge that wobbles, a ring snapping
 * out as it opens, and dark streaks drawn in toward it from as far as it pulls. What it is drawing in darkens the nearer it gets.
 * The black is blended over the scene without writing depth and still hidden behind what stands in front of it; the edge and ring are added light on top.
 */
public final class HoleLook
{
    private static final RenderType BODY   = VoidHeartRenderTypes.tint(Identifier.fromNamespaceAndPath(MODID, "textures/entity/blight/hole_body.png"));
    private static final RenderType RIM    = VoidHeartRenderTypes.radiant(Identifier.fromNamespaceAndPath(MODID, "textures/entity/blight/hole_rim.png"));
    private static final RenderType RING   = VoidHeartRenderTypes.radiant(Identifier.fromNamespaceAndPath(MODID, "textures/entity/blight/snap_ring.png"));
    private static final RenderType STREAK = VoidHeartRenderTypes.tint(Identifier.fromNamespaceAndPath(MODID, "textures/entity/blight/hole_streak.png"));

    /** Set on a living render state: how far it has gone into the dark, from 0 to 1. */
    public static final RenderStateDataKey<Float> SWALLOWED = RenderStateDataKey.create(() -> "voidheart:swallowed");

    private static final int   RIM_COLOR      = 0xD9C8FF;
    private static final float RIM_ALPHA      = 0.85F;
    /** The second, fainter copy of the edge turning the other way, so it shimmers rather than spins. */
    private static final float RIM_ECHO_ALPHA = 0.4F;
    /** Turns a tick of each edge copy, in radians. */
    private static final float RIM_SPIN       = 0.02F;
    /** How much the outline stretches one way and the other as it wobbles, and how fast. */
    private static final float WOBBLE         = 0.05F;
    private static final float WOBBLE_RATE    = 0.37F;

    private static final float RING_TICKS     = 7;
    private static final float RING_FROM      = 0.4F;
    private static final float RING_TO        = 3.4F;
    private static final float RING_ALPHA     = 0.9F;
    private static final int   RING_COLOR     = 0xE8DEFF;

    private static final int   STREAKS        = 24;
    /** How long a streak takes to fall in, the shortest and longest. */
    private static final float STREAK_MIN     = 10;
    private static final float STREAK_MAX     = 18;
    private static final float STREAK_LENGTH  = 1.2F;
    private static final float STREAK_WIDTH   = 2F / 16;
    private static final float STREAK_ALPHA   = 0.55F;
    /** Streaks fade out this near the camera, so one falling past it never fills the view. */
    private static final float STREAK_CLEAR   = 2.5F;
    /** How near the edge a streak starts, as a share of the pull's reach. */
    private static final float STREAK_FROM    = 0.45F;

    /** How dark a mob caught at the hole's edge goes, and how near it must be before it starts to darken. */
    private static final float SWALLOW_DARK   = 0.85F;
    private static final double SWALLOW_FROM  = 3.5;

    /** The holes open around the client, refreshed each tick, so each mob drawn looks only at these. */
    private static final List<ThrownBlightSpear> OPEN = new ArrayList<>();

    private HoleLook()
    {
    }

    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register(client ->
        {
            OPEN.clear();
            if (client.level == null)
                return;
            for (var entity : client.level.entitiesForRendering())
                if (entity instanceof ThrownBlightSpear spear && spear.isHoleOpen())
                    OPEN.add(spear);
        });
    }

    /** How far a mob has gone into the dark of the nearest hole, from 0 to 1. */
    public static float swallowed(LivingEntity living, float partialTicks)
    {
        if (OPEN.isEmpty())
            return 0;
        var middle = living.getPosition(partialTicks).add(0, living.getBbHeight() / 2, 0);
        float most = 0;
        for (var spear : OPEN)
        {
            // What the hole spares is never drawn as caught, though it stands in it
            if (spear.isRemoved() || spear.level() != living.level() || ThrownBlightSpear.isSpared(living, spear.getOwner()))
                continue;
            double distance = middle.distanceTo(spear.holeCenter()) - BlightBalance.HOLE_RADIUS;
            float near = (float) Mth.clamp(1 - distance / (SWALLOW_FROM - BlightBalance.HOLE_RADIUS), 0, 1);
            most = Math.max(most, near * near * SpearHole.openness(spear.holeAge(partialTicks)));
        }
        return most * SWALLOW_DARK;
    }

    /**
     * Draws the hole around its middle, which is {@code center} from the pose's origin, as the camera is {@code camera} from it.
     *
     * @param age  ticks since it opened
     * @param seed the same for one hole all its life, so its streaks keep their paths
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Quaternionf orientation, Vec3 center, Vec3 camera, float age, int seed)
    {
        float scale = SpearHole.scale(age);
        var right = orientation.transform(new Vector3f(1, 0, 0));
        var up = orientation.transform(new Vector3f(0, 1, 0));
        var facing = new Vector3f(right).cross(up).normalize();

        if (scale > 0.01F)
        {
            float size = (float) (BlightBalance.HOLE_RADIUS * 2) * scale;
            float wobble = WOBBLE * Mth.sin(age * WOBBLE_RATE + seed);
            var wide = new Vector3f(right).mul(size / 2 * (1 + wobble));
            var tall = new Vector3f(up).mul(size / 2 * (1 - wobble));
            collector.submitCustomGeometry(poseStack, BODY, (pose, buffer) -> quad(pose, buffer, center, wide, tall, 0xFFFFFFFF));
            collector.submitCustomGeometry(poseStack, RIM, (pose, buffer) ->
            {
                turned(pose, buffer, center, wide, tall, facing, age * RIM_SPIN, ARGB.color((int) (255 * RIM_ALPHA), RIM_COLOR));
                turned(pose, buffer, center, wide, tall, facing, -age * RIM_SPIN * 1.6F + 1.3F, ARGB.color((int) (255 * RIM_ECHO_ALPHA), RIM_COLOR));
            });
        }

        if (age < RING_TICKS)
        {
            float t = age / RING_TICKS;
            float eased = 1 - (1 - t) * (1 - t);
            float ring = Mth.lerp(eased, RING_FROM, RING_TO);
            collector.submitCustomGeometry(poseStack, RING, (pose, buffer) -> quad(pose, buffer, center, new Vector3f(right).mul(ring / 2),
                    new Vector3f(up).mul(ring / 2), ARGB.color((int) (255 * RING_ALPHA * (1 - t)), RING_COLOR)));
        }

        float open = SpearHole.openness(age);
        if (open <= 0.01F)
            return;
        collector.submitCustomGeometry(poseStack, STREAK, (pose, buffer) ->
        {
            for (int i = 0; i < STREAKS; i++)
                streak(pose, buffer, center, camera, facing, age, seed * 31 + i, open);
        });
    }

    /**
     * One streak falling in along its own line, from far out to the edge, then again from somewhere else. Its path and pace come from its number alone.
     */
    private static void streak(PoseStack.Pose pose, VertexConsumer buffer, Vec3 center, Vec3 camera, Vector3f facing, float age, int number, float open)
    {
        float period = Mth.lerp(random(number, 0), STREAK_MIN, STREAK_MAX);
        float cycle = (age + random(number, 1) * period) / period;
        int round = Mth.floor(cycle);
        float t = cycle - round;
        // A fresh direction every time it falls in, spread over the whole sphere
        float yaw = random(number, 2 + round * 3) * Mth.TWO_PI;
        float pitch = (random(number, 3 + round * 3) * 2 - 1) * Mth.HALF_PI * 0.9F;
        var direction = new Vector3f(Mth.cos(pitch) * Mth.cos(yaw), Mth.sin(pitch), Mth.cos(pitch) * Mth.sin(yaw));

        float reach = (float) BlightBalance.HOLE_PULL_RANGE;
        float radius = (float) BlightBalance.HOLE_RADIUS;
        float from = Mth.lerp(STREAK_FROM + (1 - STREAK_FROM) * random(number, 4 + round * 3), radius * 1.6F, reach);
        // Slow far out, quicker as it nears
        float distance = Mth.lerp(t * t, from, radius);
        float length = STREAK_LENGTH * (0.4F + 0.6F * (1 - t * t));
        var middle = center.add(direction.x * (distance + length / 2), direction.y * (distance + length / 2), direction.z * (distance + length / 2));
        float clear = (float) Mth.clamp(middle.distanceTo(camera) / STREAK_CLEAR - 0.4, 0, 1);
        float fade = Mth.clamp(t / 0.25F, 0, 1) * Mth.clamp((1 - t) / 0.2F, 0, 1) * clear;
        int alpha = (int) (255 * STREAK_ALPHA * fade * open);
        if (alpha <= 2)
            return;

        // Laid along its line as seen from the camera, so it reads as falling in from any side, its solid head toward the hole
        var along = new Vector3f(direction).sub(new Vector3f(facing).mul(direction.dot(facing)));
        if (along.lengthSquared() < 1.0E-4F)
            return;
        along.normalize();
        var across = new Vector3f(facing).cross(along).normalize().mul(STREAK_WIDTH / 2);
        quad(pose, buffer, middle, along.mul(length / 2), across, ARGB.color(alpha, 0xFFFFFF));
    }

    private static float random(int number, int salt)
    {
        int hash = number * 0x9E3779B1 + salt * 0x85EBCA6B;
        hash ^= hash >>> 15;
        hash *= 0x2C1B3C6D;
        hash ^= hash >>> 12;
        return (hash & 0xFFFFFF) / (float) 0x1000000;
    }

    private static void turned(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, Vector3f wide, Vector3f tall, Vector3f facing, float angle, int color)
    {
        var spin = new Quaternionf().rotationAxis(angle, facing);
        quad(pose, buffer, at, spin.transform(new Vector3f(wide)), spin.transform(new Vector3f(tall)), color);
    }

    /**
     * A full-bright textured quad centered on a camera-relative point, for the blight render types.
     */
    static void quad(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, Vector3f along, Vector3f across, int color)
    {
        float x = (float) at.x, y = (float) at.y, z = (float) at.z;
        vertex(pose, buffer, x - along.x - across.x, y - along.y - across.y, z - along.z - across.z, 0, 1, color);
        vertex(pose, buffer, x + along.x - across.x, y + along.y - across.y, z + along.z - across.z, 1, 1, color);
        vertex(pose, buffer, x + along.x + across.x, y + along.y + across.y, z + along.z + across.z, 1, 0, color);
        vertex(pose, buffer, x - along.x + across.x, y - along.y + across.y, z - along.z + across.z, 0, 0, color);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int color)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }
}
