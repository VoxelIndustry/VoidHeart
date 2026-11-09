package net.voxelindustry.voidheart.client.render.energy.ley;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.energy.ley.LeyStonePayloads;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Short effects the server asks for: motes settling onto a player a ward holds, and glints rising from a plant a grower quickened.
 */
final class LeyBursts
{
    private static final int   SETTLE_TICKS    = 15;
    private static final int   SETTLE_MIN      = 3;
    private static final int   SETTLE_SPREAD   = 3;
    private static final float SETTLE_ALPHA    = 0.7F;
    private static final float SETTLE_RADIUS   = 0.09F;
    // In first person the motes stay low, at the edge of view
    private static final float FIRST_PERSON_END = 0.15F;
    private static final float CHEST            = 1.0F;

    private static final int   GLINT_MIN_TICKS = 15;
    private static final int   GLINT_SPREAD    = 6;
    private static final float GLINT_RISE      = 0.5F;
    private static final float GLINT_ALPHA     = 0.9F;
    private static final float GLINT_ARM       = 0.09F;
    private static final float GLINT_WIDTH     = 0.012F;

    private record Settle(int entity, long start, List<Vector3f> from)
    {
    }

    private record Glint(double x, double y, double z, long start, int life, float phase)
    {
    }

    private static final Queue<Object>  INCOMING = new ConcurrentLinkedQueue<>();
    private static final List<Settle>   SETTLES  = new ArrayList<>();
    private static final List<Glint>    GLINTS   = new ArrayList<>();
    private static final RandomSource   RANDOM   = RandomSource.create();

    private LeyBursts()
    {
    }

    static void receive(LeyStonePayloads.WardHold hold)
    {
        INCOMING.add(hold);
    }

    static void receive(LeyStonePayloads.GrowerGlint glint)
    {
        INCOMING.add(glint);
    }

    static void clear()
    {
        INCOMING.clear();
        SETTLES.clear();
        GLINTS.clear();
    }

    static void tick(long time)
    {
        for (Object next; (next = INCOMING.poll()) != null; )
        {
            if (next instanceof LeyStonePayloads.WardHold hold)
                SETTLES.add(settle(hold.playerId(), time));
            else if (next instanceof LeyStonePayloads.GrowerGlint glint)
                glints(glint, time);
        }
        SETTLES.removeIf(settle -> time - settle.start() > SETTLE_TICKS);
        GLINTS.removeIf(glint -> time - glint.start() > glint.life());
    }

    private static Settle settle(int entity, long time)
    {
        var from = new ArrayList<Vector3f>();
        int count = SETTLE_MIN + RANDOM.nextInt(SETTLE_SPREAD);
        for (int i = 0; i < count; i++)
        {
            float angle = RANDOM.nextFloat() * Mth.TWO_PI;
            float distance = 1.5F + RANDOM.nextFloat();
            from.add(new Vector3f(Mth.cos(angle) * distance, 0.2F + RANDOM.nextFloat() * 2, Mth.sin(angle) * distance));
        }
        return new Settle(entity, time, from);
    }

    private static void glints(LeyStonePayloads.GrowerGlint glint, long time)
    {
        int count = 3 + RANDOM.nextInt(3);
        var plant = glint.plant();
        for (int i = 0; i < count; i++)
            GLINTS.add(new Glint(plant.getX() + 0.2 + RANDOM.nextFloat() * 0.6, plant.getY() + 0.1 + RANDOM.nextFloat() * 0.4, plant.getZ() + 0.2 + RANDOM.nextFloat() * 0.6,
                    time + RANDOM.nextInt(4), GLINT_MIN_TICKS + RANDOM.nextInt(GLINT_SPREAD), RANDOM.nextFloat() * Mth.TWO_PI));
    }

    static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, float time, float partialTicks)
    {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null)
            return;

        for (var settle : SETTLES)
        {
            var entity = minecraft.level.getEntity(settle.entity());
            if (entity == null)
                continue;
            float t = Mth.clamp((time - settle.start()) / SETTLE_TICKS, 0, 1);
            float eased = t * t * (3 - 2 * t);
            float alpha = SETTLE_ALPHA * Mth.clamp(Math.min(t / 0.2F, (1 - t) / 0.3F), 0, 1);
            boolean firstPerson = entity == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
            var feet = entity.getPosition(partialTicks);
            for (var from : settle.from())
            {
                float startHeight = firstPerson ? from.y * 0.35F : from.y;
                float endHeight = firstPerson ? FIRST_PERSON_END : CHEST;
                double x = feet.x + from.x * (1 - eased);
                double y = feet.y + Mth.lerp(eased, startHeight, endHeight);
                double z = feet.z + from.z * (1 - eased);
                LeyLook.softDot(buffer, pose, LeyLook.relative(x, y, z, camera), SETTLE_RADIUS, LeyLook.TEAL_LIGHT, alpha);
            }
        }

        for (var glint : GLINTS)
        {
            float t = (time - glint.start()) / glint.life();
            if (t < 0 || t > 1)
                continue;
            float alpha = GLINT_ALPHA * Mth.sin(Mth.PI * t) * (0.7F + 0.3F * Mth.sin(glint.phase() + time * 0.9F));
            var at = LeyLook.relative(glint.x(), glint.y() + GLINT_RISE * t, glint.z(), camera);
            star(pose, buffer, at, alpha);
        }
    }

    /**
     * A tiny four-pointed twinkle, a soft dot crossed by two thin rays facing the camera.
     */
    private static void star(PoseStack.Pose pose, VertexConsumer buffer, Vector3f at, float alpha)
    {
        LeyLook.softDot(buffer, pose, at, GLINT_ARM * 0.6F, LeyLook.TEAL_LIGHT, alpha);
        var toCamera = new Vector3f(at).negate().normalize();
        var right = new Vector3f(0, 1, 0).cross(toCamera);
        if (right.lengthSquared() < 1.0E-6F)
            right.set(1, 0, 0);
        right.normalize();
        var up = new Vector3f(toCamera).cross(right).normalize();
        ray(pose, buffer, at, right, up, alpha);
        ray(pose, buffer, at, up, right, alpha);
    }

    private static void ray(PoseStack.Pose pose, VertexConsumer buffer, Vector3f at, Vector3f along, Vector3f across, float alpha)
    {
        int bright = LeyLook.color(LeyLook.TEAL_LIGHT, alpha);
        int none = LeyLook.color(LeyLook.TEAL_LIGHT, 0);
        var a = new Vector3f(at).sub(new Vector3f(along).mul(GLINT_ARM));
        var b = new Vector3f(at).add(new Vector3f(along).mul(GLINT_ARM));
        var w = new Vector3f(across).mul(GLINT_WIDTH);
        LeyLook.vertex(buffer, pose, new Vector3f(a), none);
        LeyLook.vertex(buffer, pose, new Vector3f(at).add(w), bright);
        LeyLook.vertex(buffer, pose, new Vector3f(b), none);
        LeyLook.vertex(buffer, pose, new Vector3f(at).sub(w), bright);
    }
}
