package net.voxelindustry.voidheart.common.content.underway.security;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.SecurityBalance;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * What the machines of the Underway that leave their post share. Asleep one stands cold where it was posted, and nothing harms it. Woken by its section, it takes a moment to build pressure, venting steam, before it moves: that moment is the warning.
 * <p>
 * It is tied to its section like a mounted unit: harming it sounds the alarm, having someone in sight keeps the alarm sounding, it is told whom a searchlight caught, and its wreck counts toward securing the section. One nobody posted answers to no section and is always awake.
 */
public abstract class SecurityMob extends Monster
{
    /** Ticks a bolt's streak stays drawn, on the client. */
    public static final int TRACER_TICKS = 3;

    private static final EntityDataAccessor<Boolean> DATA_AWAKE = SynchedEntityData.defineId(SecurityMob.class, EntityDataSerializers.BOOLEAN);
    // Whether its section has power, which lights the rubrum in its boiler
    private static final EntityDataAccessor<Boolean> DATA_LIVE = SynchedEntityData.defineId(SecurityMob.class, EntityDataSerializers.BOOLEAN);
    // Whether it has built its pressure and moves
    private static final EntityDataAccessor<Boolean> DATA_READY = SynchedEntityData.defineId(SecurityMob.class, EntityDataSerializers.BOOLEAN);
    // Ticks it has aimed for, 0 when it does not aim
    private static final EntityDataAccessor<Integer> DATA_AIM_TICKS = SynchedEntityData.defineId(SecurityMob.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3fc> DATA_AIM = SynchedEntityData.defineId(SecurityMob.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3fc> DATA_SHOT = SynchedEntityData.defineId(SecurityMob.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_SHOTS = SynchedEntityData.defineId(SecurityMob.class, EntityDataSerializers.INT);

    private static final double AT_POST = 1.5;
    private static final double RETURN_SPEED = 0.8;
    private static final int SPOTTED_EVERY = 10;
    private static final int REPORT_EVERY = 20;

    private final UnitLink link = new UnitLink();
    private @Nullable BlockPos post;
    private float postYaw;
    private int pressure;
    protected int reload;

    /** Kept by the client for show: ticks the last bolt's streak still shows, and how far it has woken. */
    public int tracer;
    public float wake;
    public float wakeO;

    protected SecurityMob(EntityType<? extends SecurityMob> type, Level level)
    {
        super(type, level);
        setPersistenceRequired();
        setNoAi(true);
        xpReward = 0;
    }

    public UnitLink link()
    {
        return link;
    }

    /** Posts it where it stands: the place it guards asleep and comes back to. */
    public void postHere()
    {
        post = blockPosition();
        postYaw = getYRot();
    }

    protected @Nullable BlockPos post()
    {
        return post;
    }

    /** @return how long it builds pressure between waking and moving */
    public abstract int wakeTicks();

    /** @return whether it runs its goals once it moves. A machine that only follows its track does not. */
    protected boolean thinks()
    {
        return true;
    }

    /** @return whether it goes after whoever a searchlight caught */
    protected boolean hunts()
    {
        return true;
    }

    /** @return whether a blow glances off it although it is awake */
    protected boolean isArmoured(DamageSource source)
    {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData)
    {
        super.defineSynchedData(entityData);
        entityData.define(DATA_AWAKE, false);
        entityData.define(DATA_LIVE, false);
        entityData.define(DATA_READY, false);
        entityData.define(DATA_AIM_TICKS, 0);
        entityData.define(DATA_AIM, new Vector3f());
        entityData.define(DATA_SHOT, new Vector3f());
        entityData.define(DATA_SHOTS, 0);
    }

    public boolean isAwake()
    {
        return entityData.get(DATA_AWAKE);
    }

    /** @return whether its section has power */
    public boolean isLive()
    {
        return entityData.get(DATA_LIVE);
    }

    /** @return whether it has built its pressure and moves */
    public boolean isUnderPressure()
    {
        return entityData.get(DATA_READY);
    }

    /** @return ticks it has aimed for, 0 when it does not aim */
    public int aimTicks()
    {
        return entityData.get(DATA_AIM_TICKS);
    }

    public Vec3 aim()
    {
        return UnderwayShots.vec(entityData.get(DATA_AIM));
    }

    public Vec3 lastShot()
    {
        return UnderwayShots.vec(entityData.get(DATA_SHOT));
    }

    public int shotsFired()
    {
        return entityData.get(DATA_SHOTS);
    }

    /** @return where its bolts leave from */
    public Vec3 muzzle()
    {
        return getEyePosition();
    }

    /** @return where its steam leaves it */
    protected Vec3 vent()
    {
        var back = Vec3.directionFromRotation(0, yBodyRot).scale(-0.35 * getBbWidth() / 0.6);
        return position().add(back.x, getBbHeight() * 0.87, back.z);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!(level() instanceof ServerLevel level))
        {
            wakeO = wake;
            wake = Math.clamp(wake + (isAwake() ? 1F / wakeTicks() : -1F / wakeTicks()), 0, 1);
            if (tracer > 0)
                tracer--;
            clientTick();
            return;
        }
        if (link.isWreck(level))
        {
            discard();
            return;
        }
        entityData.set(DATA_LIVE, link.isLive(level));
        boolean awake = link.isAwake(level);
        if (awake && !isAwake())
        {
            entityData.set(DATA_AWAKE, true);
            pressure = wakeTicks();
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, getSoundSource(), 0.7F, 0.6F);
        }
        else if (!awake && isAwake())
        {
            entityData.set(DATA_AWAKE, false);
            entityData.set(DATA_READY, false);
            setNoAi(true);
        }
        if (isAwake() && !isUnderPressure())
            buildPressure(level);
        if (reload > 0)
            reload--;
        if (isUnderPressure())
        {
            // It does not follow further than its leash: whoever outruns it is left alone
            if (post != null && getTarget() != null && !getTarget().blockPosition().closerThan(post, SecurityBalance.AUTOMATON_LEASH))
                setTarget(null);
            if (hunts() && getTarget() == null && tickCount % SPOTTED_EVERY == 0)
                setTarget(link.spotted(level));
            if (getTarget() instanceof Player && tickCount % REPORT_EVERY == 0)
                link.raiseAlarm(level);
        }
        serverTick(level);
    }

    protected void serverTick(ServerLevel level)
    {
    }

    protected void clientTick()
    {
    }

    /** Stands a moment longer while its boiler comes up, steam leaving it, then moves. */
    private void buildPressure(ServerLevel level)
    {
        if (pressure % 3 == 0)
            steam(level, 2);
        if (--pressure > 0)
            return;
        entityData.set(DATA_READY, true);
        setNoAi(!thinks());
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_REPAIR, getSoundSource(), 1.0F, 0.6F);
    }

    protected void steam(ServerLevel level, int puffs)
    {
        var vent = vent();
        level.sendParticles(ParticleTypes.CLOUD, vent.x, vent.y, vent.z, puffs, 0.12 * getBbWidth(), 0.05, 0.12 * getBbWidth(), 0.01);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        boolean reported = source.getEntity() instanceof Player player && !player.isCreative();
        if ((!isUnderPressure() || isArmoured(source)) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            if (source.getEntity() != null)
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_PLACE, getSoundSource(), 0.5F, 1.8F);
            if (reported)
                link.raiseAlarm(level);
            return false;
        }
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt)
            steam(level, 3);
        if (hurt && reported)
            link.raiseAlarm(level);
        return hurt;
    }

    @Override
    public void die(DamageSource source)
    {
        super.die(source);
        if (level() instanceof ServerLevel level)
            wreck(level);
    }

    /** Counts it as a wreck of its section, in sparks and a last breath of steam. */
    protected void wreck(ServerLevel level)
    {
        link.destroyed(level);
        double middle = getY() + getBbHeight() / 2;
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), middle, getZ(), 20, 0.4 * getBbWidth(), 0.25 * getBbHeight(), 0.4 * getBbWidth(), 0.4);
        level.sendParticles(ParticleTypes.CLOUD, getX(), middle, getZ(), 14, 0.4 * getBbWidth(), 0.2 * getBbHeight(), 0.4 * getBbWidth(), 0.04);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, getSoundSource(), 1.0F, 0.5F);
    }

    protected void aimAt(Vec3 point)
    {
        entityData.set(DATA_AIM, new Vector3f((float) point.x, (float) point.y, (float) point.z));
    }

    /** Fires one bolt along its aiming line. */
    protected void shoot(ServerLevel level, double range, float damage)
    {
        var muzzle = muzzle();
        var end = UnderwayShots.fire(level, this, muzzle, aim(), range + 4, SecurityBalance.GUN_SHOT_WIDTH, damage);
        entityData.set(DATA_SHOT, new Vector3f((float) end.x, (float) end.y, (float) end.z));
        entityData.set(DATA_SHOTS, entityData.get(DATA_SHOTS) + 1);
        UnderwayShots.puff(level, muzzle);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.PISTON_EXTEND, getSoundSource(), 1.5F, 1.6F);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_BLAST, getSoundSource(), 1.2F, 0.8F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
    {
        super.onSyncedDataUpdated(accessor);
        if (DATA_SHOTS.equals(accessor) && level().isClientSide())
            tracer = TRACER_TICKS;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        link.save(output);
        if (post != null)
        {
            output.store("Post", BlockPos.CODEC, post);
            output.putFloat("PostYaw", postYaw);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        link.load(input);
        post = input.read("Post", BlockPos.CODEC).orElse(null);
        postYaw = input.getFloatOr("PostYaw", 0);
        // Whatever it was doing when saved, it stands until its section tells it otherwise
        setNoAi(true);
    }

    @Override
    public boolean isPushable()
    {
        return isUnderPressure() && super.isPushable();
    }

    @Override
    public void checkDespawn()
    {
    }

    @Override
    public boolean removeWhenFarAway(double distSqr)
    {
        return false;
    }

    @Override
    public boolean shouldDropExperience()
    {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean ignorePassenger)
    {
        return false;
    }

    @Override
    public @Nullable SoundEvent getAmbientSound()
    {
        return null;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource source)
    {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    public SoundEvent getDeathSound()
    {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block)
    {
        playSound(SoundEvents.IRON_GOLEM_STEP, 0.5F, 1.3F);
    }

    /** Stands still, shows the line of its bolt, holds it for a moment and fires along it, once or in a short burst. */
    protected class BoltGoal extends Goal
    {
        private final double minRange;
        private final double maxRange;
        private final int aimTicks;
        private final int lockTicks;
        private final int shots;
        private final int shotEvery;
        private final int reloadTicks;
        private final float damage;
        private int aimed;
        private int shotsLeft;
        private int untilShot;

        /**
         * @param minRange  closer than this it does not shoot
         * @param lockTicks the last of the aim, when the line no longer follows its target
         */
        public BoltGoal(double minRange, double maxRange, int aimTicks, int lockTicks, int shots, int shotEvery, int reloadTicks, float damage)
        {
            this.minRange = minRange;
            this.maxRange = maxRange;
            this.aimTicks = aimTicks;
            this.lockTicks = lockTicks;
            this.shots = shots;
            this.shotEvery = shotEvery;
            this.reloadTicks = reloadTicks;
            this.damage = damage;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            var target = getTarget();
            if (target == null || !target.isAlive() || reload > 0)
                return false;
            double distance = distanceToSqr(target);
            return distance > minRange * minRange && distance < maxRange * maxRange && getSensing().hasLineOfSight(target);
        }

        @Override
        public boolean canContinueToUse()
        {
            return (aimed < aimTicks || shotsLeft > 0) && getTarget() != null && getTarget().isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void start()
        {
            getNavigation().stop();
            aimed = 0;
            shotsLeft = 0;
        }

        @Override
        public void tick()
        {
            var target = getTarget();
            if (target == null)
                return;
            getLookControl().setLookAt(target, 30, 30);
            if (aimed < aimTicks)
            {
                aimed++;
                if (aimed <= aimTicks - lockTicks)
                    aimAt(target.getBoundingBox().getCenter());
                entityData.set(DATA_AIM_TICKS, aimed);
                if (aimed < aimTicks)
                    return;
                shotsLeft = shots;
                untilShot = 0;
            }
            if (untilShot-- > 0)
                return;
            shoot((ServerLevel) level(), maxRange, damage);
            untilShot = shotEvery - 1;
            if (--shotsLeft <= 0)
                reload = reloadTicks;
        }

        @Override
        public void stop()
        {
            entityData.set(DATA_AIM_TICKS, 0);
        }
    }

    /** Goes back to its post once nobody is left to hunt, and turns the way it was posted. */
    protected class ReturnToPostGoal extends Goal
    {
        public ReturnToPostGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse()
        {
            return post != null && getTarget() == null && !post.closerToCenterThan(position(), AT_POST);
        }

        @Override
        public boolean canContinueToUse()
        {
            return canUse() && !getNavigation().isDone();
        }

        @Override
        public void start()
        {
            getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, RETURN_SPEED);
        }

        @Override
        public void stop()
        {
            getNavigation().stop();
            if (post != null && post.closerToCenterThan(position(), AT_POST))
            {
                setYRot(postYaw);
                setYBodyRot(postYaw);
                setYHeadRot(postYaw);
            }
        }
    }
}
