package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;

/**
 * Follows from afar and stops dead when looked at. Left unwatched it comes in close, fires and backs away again.
 */
public class ArcaneEye extends FloatingEye
{
    public static final int FIRE_INTERVAL = 2 * 20;
    public static final int CHARGE_TICKS = 10;
    public static final float BOLT_DAMAGE = 6;

    private static final EntityDataAccessor<Boolean> DATA_WATCHED = SynchedEntityData.defineId(ArcaneEye.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CHARGING = SynchedEntityData.defineId(ArcaneEye.class, EntityDataSerializers.BOOLEAN);

    private static final int EXPERIENCE = 12;
    private static final float HEALTH = 24;
    private static final double SPEED = 0.11;
    private static final int BACK_OFF_TICKS = 40;
    private static final double DRIFT_SHARE = 0.4;
    private static final double IDLE_SHARE = 0.5;
    private static final double TURN_RATE = 0.12;
    private static final double WALL_REACH = 1;
    private static final double GAZE_CONE = 0.25;
    private static final float LID_RESTING = 0.45F;
    private static final float LID_WATCHED = 0.8F;
    private static final float LID_PER_TICK = 0.12F;

    private int fireCooldown;
    private int chargeTicks = -1;
    private int backOffTicks;

    public float lid = LID_RESTING;
    public float lidO = LID_RESTING;

    public ArcaneEye(EntityType<? extends ArcaneEye> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return eyeAttributes(HEALTH, BOLT_DAMAGE, SPEED);
    }

    public boolean watched()
    {
        return entityData.get(DATA_WATCHED);
    }

    public boolean charging()
    {
        return entityData.get(DATA_CHARGING);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_WATCHED, false);
        builder.define(DATA_CHARGING, false);
    }

    @Override
    protected void registerGoals()
    {
        addPlayerTarget(false);
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.ARCANE_EYE;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (level().isClientSide())
        {
            float wanted = charging() ? 0 : watched() ? LID_WATCHED : LID_RESTING;
            lidO = lid;
            lid = Mth.approach(lid, wanted, LID_PER_TICK);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);

        var target = getTarget();
        if (target != null && !stillHunted(target))
        {
            setTarget(null);
            target = null;
        }
        if (fireCooldown > 0)
            fireCooldown--;
        if (backOffTicks > 0)
            backOffTicks--;

        if (target == null)
        {
            entityData.set(DATA_WATCHED, false);
            stopCharging();
            steer(EyeFlock.velocity(wander(), Vec3.ZERO, wallPush(WALL_REACH), flightSpeed() * IDLE_SHARE), TURN_RATE);
            faceAlong(getDeltaMovement());
            return;
        }

        faceTarget(target);
        double distance = reach(target);
        boolean sees = getSensing().hasLineOfSight(target);
        boolean watched = isLookingAtMe(target, GAZE_CONE, false, false, getBoundingBox().getCenter().y);
        entityData.set(DATA_WATCHED, watched);

        stalk(target, distance, watched);
        fire(level, target, distance, sees);
    }

    private double reach(LivingEntity target)
    {
        return getBoundingBox().getCenter().distanceTo(target.getBoundingBox().getCenter());
    }

    private void stalk(LivingEntity target, double distance, boolean watched)
    {
        var away = position().subtract(target.position()).normalize();
        switch (ArcaneStalk.decide(distance, watched, backOffTicks > 0))
        {
            case CLOSE_IN -> fly(headingTo(target, target.getEyePosition()), flightSpeed());
            case HOLD -> fly(Vec3.ZERO, 0);
            case DRIFT_BACK -> fly(away, flightSpeed() * DRIFT_SHARE);
            case BACK_OFF -> fly(away, flightSpeed());
        }
    }

    private void fly(Vec3 wish, double speed)
    {
        var walls = wallPush(WALL_REACH);
        steer(EyeFlock.velocity(wish, Vec3.ZERO, walls, walls.lengthSqr() > 0 ? Math.max(speed, flightSpeed() * DRIFT_SHARE) : speed), TURN_RATE);
    }

    private void fire(ServerLevel level, LivingEntity target, double distance, boolean sees)
    {
        if (!ArcaneStalk.canFire(distance, sees))
        {
            stopCharging();
            return;
        }

        if (chargeTicks < 0)
        {
            if (fireCooldown > 0)
                return;
            chargeTicks = 0;
            entityData.set(DATA_CHARGING, true);
            playSound(VoidHeartSounds.ARCANE_EYE_CHARGE, 1, 1);
            return;
        }

        if (++chargeTicks < CHARGE_TICKS)
            return;

        stopCharging();
        fireCooldown = FIRE_INTERVAL;
        backOffTicks = BACK_OFF_TICKS;
        var center = getBoundingBox().getCenter();
        var aim = target.getBoundingBox().getCenter().subtract(center).normalize();
        var muzzle = center.add(aim.scale(getBbWidth() / 2 + 0.2));
        level.addFreshEntity(new ArcaneBolt(level, this, muzzle, aim, BOLT_DAMAGE));
        playSound(VoidHeartSounds.ARCANE_EYE_FIRE, 1, 1);
    }

    private void stopCharging()
    {
        chargeTicks = -1;
        entityData.set(DATA_CHARGING, false);
    }
}
