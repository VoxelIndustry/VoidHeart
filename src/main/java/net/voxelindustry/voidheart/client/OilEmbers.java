package net.voxelindustry.voidheart.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.oil.OilCoating;
import net.voxelindustry.voidheart.common.content.oil.OilType;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A few embers drift off any coated weapon held nearby, and a small burst flies off on each swing, so a coat shows in hand to everyone.
 */
public final class OilEmbers
{
    /**
     * Chance each tick that one ember leaves a held coated weapon.
     */
    private static final float  IDLE_CHANCE     = 1 / 10F;
    private static final int    SWING_BURST     = 4;
    private static final double VISIBLE_RANGE   = 32;
    private static final double SPREAD          = 0.12;
    private static final double RISE            = 0.015;

    /**
     * Where the blade sits from a third person body: to the side of the main arm, a little ahead, at this share of the body's height.
     */
    private static final double BODY_SIDE       = 0.4;
    private static final double BODY_FORWARD    = 0.35;
    private static final double BODY_HEIGHT     = 0.55;
    /**
     * Where the held blade shows in first person, from the eyes along the view. Nearer than this, an ember fills a corner of the screen.
     */
    private static final double VIEW_FORWARD    = 1.8;
    private static final double VIEW_SIDE       = 1.15;
    private static final double VIEW_DOWN       = 0.75;

    private static final Map<Entity, Boolean> WAS_SWINGING = new WeakHashMap<>();

    private OilEmbers()
    {
    }

    public static void tick(Minecraft minecraft)
    {
        var level = minecraft.level;
        if (level == null || minecraft.isPaused())
            return;

        var camera = minecraft.gameRenderer.mainCamera().position();
        var random = level.getRandom();
        for (var entity : level.entitiesForRendering())
        {
            if (!(entity instanceof LivingEntity living) || entity.position().distanceTo(camera) > VISIBLE_RANGE)
                continue;

            var coating = OilCoating.coatingOf(living.getMainHandItem());
            boolean swinging = living.isSwinging();
            boolean swingStarted = swinging && !WAS_SWINGING.getOrDefault(living, false);
            WAS_SWINGING.put(living, swinging);
            if (coating == null)
                continue;

            int embers = (swingStarted ? SWING_BURST : 0) + (random.nextFloat() < IDLE_CHANCE ? 1 : 0);
            if (embers == 0)
                continue;

            boolean firstPerson = living == minecraft.getCameraEntity() && minecraft.options.getCameraType() == CameraType.FIRST_PERSON;
            var blade = firstPerson ? viewBlade(living) : bodyBlade(living);
            for (int i = 0; i < embers; i++)
            {
                level.addParticle(particleOf(coating.type()),
                        blade.x + random.nextGaussian() * SPREAD, blade.y + random.nextGaussian() * SPREAD, blade.z + random.nextGaussian() * SPREAD,
                        random.nextGaussian() * 0.01, RISE + random.nextFloat() * RISE, random.nextGaussian() * 0.01);
            }
        }
    }

    private static ParticleOptions particleOf(OilType type)
    {
        return switch (type)
        {
            case SEARING -> ParticleTypes.SMALL_FLAME;
        };
    }

    private static double side(LivingEntity living)
    {
        return living.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
    }

    private static Vec3 bodyBlade(LivingEntity living)
    {
        float yaw = living.yBodyRot * Mth.DEG_TO_RAD;
        var forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        var right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return living.position()
                .add(right.scale(BODY_SIDE * side(living)))
                .add(forward.scale(BODY_FORWARD))
                .add(0, living.getBbHeight() * BODY_HEIGHT, 0);
    }

    private static Vec3 viewBlade(LivingEntity living)
    {
        var look = living.getLookAngle();
        var right = look.cross(new Vec3(0, 1, 0)).normalize();
        var down = right.cross(look).normalize().scale(-1);
        return living.getEyePosition()
                .add(look.scale(VIEW_FORWARD))
                .add(right.scale(VIEW_SIDE * side(living)))
                .add(down.scale(VIEW_DOWN));
    }
}
