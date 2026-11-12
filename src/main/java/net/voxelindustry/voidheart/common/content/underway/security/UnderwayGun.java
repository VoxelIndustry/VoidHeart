package net.voxelindustry.voidheart.common.content.underway.security;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.SecurityBalance;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * A mounted gun of the Underway. Awake and with someone in sight it readies itself, shows a thin line to where it aims, holds that line still for a moment, then fires a short burst straight along it. Stepping off the line or behind something solid during the hold is how a burst is escaped.
 */
public abstract class UnderwayGun extends MountedUnit
{
    public enum Phase
    {
        /** Put away, or asleep. */
        FOLDED,
        DEPLOYING,
        /** Out and looking for someone. */
        IDLE,
        AIMING,
        FIRING,
        COOLING;

        private static final Phase[] VALUES = values();

        static Phase of(int ordinal)
        {
            return VALUES[Math.clamp(ordinal, 0, VALUES.length - 1)];
        }
    }

    /** Ticks a shot's streak stays drawn, on the client. */
    public static final int TRACER_TICKS = 3;

    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(UnderwayGun.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3fc> DATA_AIM = SynchedEntityData.defineId(UnderwayGun.class, EntityDataSerializers.VECTOR3);
    // Where the last bullet stopped, and how many were fired so far so that two bullets stopping at one place still read as a change
    private static final EntityDataAccessor<Vector3fc> DATA_SHOT = SynchedEntityData.defineId(UnderwayGun.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_SHOTS = SynchedEntityData.defineId(UnderwayGun.class, EntityDataSerializers.INT);

    private int phaseTicks;
    private int idleTicks;
    private int burstLeft;

    /** Kept by the client for show: ticks since the phase changed, how far out the gun is, and ticks the last shot's streak still shows. */
    public int phaseAge;
    public float out;
    public float outO;
    public int tracer;

    protected UnderwayGun(EntityType<? extends UnderwayGun> type, Level level)
    {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData)
    {
        super.defineSynchedData(entityData);
        entityData.define(DATA_PHASE, Phase.FOLDED.ordinal());
        entityData.define(DATA_AIM, new Vector3f());
        entityData.define(DATA_SHOT, new Vector3f());
        entityData.define(DATA_SHOTS, 0);
    }

    public Phase phase()
    {
        return Phase.of(entityData.get(DATA_PHASE));
    }

    private void setPhase(Phase phase, int ticks)
    {
        entityData.set(DATA_PHASE, phase.ordinal());
        phaseTicks = ticks;
    }

    /** @return the point it aims at, which only means something while it aims or fires */
    public Vec3 aim()
    {
        return UnderwayShots.vec(entityData.get(DATA_AIM));
    }

    /** @return where its last bullet stopped */
    public Vec3 lastShot()
    {
        return UnderwayShots.vec(entityData.get(DATA_SHOT));
    }

    public int shotsFired()
    {
        return entityData.get(DATA_SHOTS);
    }

    /** @return whether its aiming line has stopped following its target */
    public boolean isLocked()
    {
        return phase() == Phase.FIRING || phase() == Phase.AIMING && phaseAge >= SecurityBalance.GUN_AIM_TICKS - SecurityBalance.GUN_LOCK_TICKS;
    }

    /** @return where its bullets leave from */
    public abstract Vec3 muzzle();

    /** @return whether it can be brought to bear on a point */
    protected abstract boolean bears(Vec3 point);

    /** @return whether it puts itself away when nobody is in sight */
    protected boolean folds()
    {
        return true;
    }

    private @Nullable Player findTarget(ServerLevel level)
    {
        // The nearest it can be brought to bear on, not the nearest of all: someone out of its arc must not hide someone in it
        return UnderwayShots.nearestSeen(level, this, muzzle(), SecurityBalance.GUN_RANGE, player -> bears(player.getBoundingBox().getCenter()));
    }

    private void aimAt(Player target)
    {
        var point = target.getBoundingBox().getCenter();
        entityData.set(DATA_AIM, new Vector3f((float) point.x, (float) point.y, (float) point.z));
    }

    @Override
    protected void guard(ServerLevel level, boolean awake)
    {
        var phase = phase();
        if (!awake)
        {
            if (phase != Phase.FOLDED)
                setPhase(Phase.FOLDED, 0);
            return;
        }
        var target = phase == Phase.FIRING || phase == Phase.COOLING ? null : findTarget(level);
        // A gun with someone in its sights keeps the alarm sounding
        if (target != null)
            link().raiseAlarm(level);
        switch (phase)
        {
            case FOLDED ->
            {
                if (target == null)
                    break;
                setPhase(Phase.DEPLOYING, SecurityBalance.GUN_DEPLOY_TICKS);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_EXTEND, getSoundSource(), 0.8F, 1.4F);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, getSoundSource(), 0.5F, 0.8F);
                UnderwayShots.puff(level, muzzle());
            }
            case DEPLOYING ->
            {
                if (--phaseTicks <= 0)
                    setPhase(Phase.IDLE, 0);
            }
            case IDLE ->
            {
                if (target != null)
                {
                    idleTicks = 0;
                    aimAt(target);
                    setPhase(Phase.AIMING, SecurityBalance.GUN_AIM_TICKS);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), getSoundSource(), 1.0F, 0.7F);
                }
                else if (folds() && ++idleTicks >= SecurityBalance.GUN_STAND_DOWN_TICKS)
                {
                    idleTicks = 0;
                    setPhase(Phase.FOLDED, 0);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_CONTRACT, getSoundSource(), 0.8F, 1.4F);
                }
            }
            case AIMING ->
            {
                // The line follows until the lock, then holds still: that is the moment to step off it
                if (target != null && phaseTicks > SecurityBalance.GUN_LOCK_TICKS)
                    aimAt(target);
                if (--phaseTicks <= 0)
                {
                    burstLeft = SecurityBalance.GUN_BURST_SHOTS;
                    setPhase(Phase.FIRING, 0);
                }
            }
            case FIRING ->
            {
                if (phaseTicks-- > 0)
                    break;
                shoot(level);
                phaseTicks = SecurityBalance.GUN_SHOT_EVERY - 1;
                if (--burstLeft <= 0)
                    setPhase(Phase.COOLING, SecurityBalance.GUN_COOL_TICKS);
            }
            case COOLING ->
            {
                if (--phaseTicks <= 0)
                    setPhase(Phase.IDLE, 0);
            }
        }
    }

    private void shoot(ServerLevel level)
    {
        var muzzle = muzzle();
        var end = UnderwayShots.fire(level, this, muzzle, aim(), SecurityBalance.GUN_RANGE + 4, SecurityBalance.GUN_SHOT_WIDTH, SecurityBalance.GUN_SHOT_DAMAGE);
        entityData.set(DATA_SHOT, new Vector3f((float) end.x, (float) end.y, (float) end.z));
        entityData.set(DATA_SHOTS, shotsFired() + 1);
        UnderwayShots.puff(level, muzzle);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_BLAST, getSoundSource(), 2.0F, 0.6F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
    {
        super.onSyncedDataUpdated(accessor);
        if (!level().isClientSide())
            return;
        if (DATA_PHASE.equals(accessor))
            phaseAge = 0;
        else if (DATA_SHOTS.equals(accessor))
            tracer = TRACER_TICKS;
    }

    @Override
    protected void clientTick()
    {
        phaseAge++;
        outO = out;
        float step = 1F / SecurityBalance.GUN_DEPLOY_TICKS;
        out = Math.clamp(out + (phase() == Phase.FOLDED ? -step : step), 0, 1);
        if (tracer > 0)
            tracer--;
    }
}
