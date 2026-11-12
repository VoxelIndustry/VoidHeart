package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.UnderwayKeyEntity;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;

import java.util.EnumSet;

/**
 * Throws whatever crowds it back with its shield, then cuts through everything in front of it with wide sweeps of its scimitar.
 */
public class Centurion extends Automaton
{
    public static final int BASH_WINDUP_TICKS = 8;
    public static final int BASH_COOLDOWN_TICKS = 100;
    public static final int SWEEP_WINDUP_TICKS = 12;
    public static final int FOLLOW_THROUGH_TICKS = 6;
    public static final int RECOVER_TICKS = 16;
    public static final float BASH_DAMAGE = 6;
    public static final double BASH_POWER = 3.5;
    public static final double SWEEP_RANGE = 3.8;
    public static final double SWEEP_ARC_DOT = 0.34;

    private static final EntityDataAccessor<Integer> DATA_BASH = SynchedEntityData.defineId(Centurion.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SWEEP = SynchedEntityData.defineId(Centurion.class, EntityDataSerializers.INT);

    private static final int EXPERIENCE = 50;
    private static final float SWEEP_DAMAGE = 16;
    private static final double BASH_RANGE = 3;
    private static final double SWEEP_START_RANGE = 3.4;
    private static final int REPATH_TICKS = 10;
    private static final int SWEEP_PARTICLES = 3;
    private static final double SWEEP_PARTICLE_REACH = 2.4;

    private int bashCooldown;

    public Centurion(EntityType<? extends Centurion> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(150, SWEEP_DAMAGE, 0.27)
                .add(Attributes.ARMOR, 18)
                .add(Attributes.ARMOR_TOUGHNESS, 6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    public int bashTicks()
    {
        return entityData.get(DATA_BASH);
    }

    public int sweepTicks()
    {
        return entityData.get(DATA_SWEEP);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_BASH, 0);
        builder.define(DATA_SWEEP, 0);
    }

    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(3, new FightGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        addIdleGoals(6);
        addPlayerTarget(true);
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.CENTURION;
    }

    @Override
    protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);
        if (bashCooldown > 0)
            bashCooldown--;
    }

    private void bash(ServerLevel level, LivingEntity target)
    {
        playSound(VoidHeartSounds.CENTURION_BASH, 1.5F, 1);
        var center = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 12, 0.3, 0.4, 0.3, 0.2);

        // A raised shield keeps the damage out, but not the shove
        target.hurtServer(level, damageSources().mobAttack(this), BASH_DAMAGE);
        target.knockback(BASH_POWER, getX() - target.getX(), getZ() - target.getZ(), damageSources().mobAttack(this), BASH_DAMAGE);
    }

    private void sweep(ServerLevel level)
    {
        var look = getLookAngle();
        var flat = new Vec3(look.x, 0, look.z).normalize();
        playSound(VoidHeartSounds.CENTURION_SWEEP, 1.4F, 1);

        var side = new Vec3(-flat.z, 0, flat.x);
        for (int i = 0; i < SWEEP_PARTICLES; i++)
        {
            var at = position().add(0, getBbHeight() * 0.5, 0)
                    .add(flat.scale(SWEEP_PARTICLE_REACH))
                    .add(side.scale((i - 1) * SWEEP_PARTICLE_REACH * 0.7));
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }

        var area = new AABB(position(), position()).inflate(SWEEP_RANGE, getBbHeight(), SWEEP_RANGE);
        for (var player : level.getEntitiesOfClass(Player.class, area, EntitySelector.NO_CREATIVE_OR_SPECTATOR))
        {
            var toward = player.position().subtract(position());
            var flatToward = new Vec3(toward.x, 0, toward.z);
            if (flatToward.length() <= SWEEP_RANGE && flatToward.normalize().dot(flat) >= SWEEP_ARC_DOT)
                player.hurtServer(level, damageSources().mobAttack(this), SWEEP_DAMAGE);
        }
        if (getTarget() instanceof UnderwayKeyEntity key)
        {
            // A shielded key is as wide as its shield, so the sweep's reach is measured to the nearest point of its box
            var box = key.getBoundingBox();
            var nearest = new Vec3(Mth.clamp(getX(), box.minX, box.maxX), 0, Mth.clamp(getZ(), box.minZ, box.maxZ)).subtract(getX(), 0, getZ());
            var toward = new Vec3(key.getX() - getX(), 0, key.getZ() - getZ());
            if (box.intersects(area) && nearest.length() <= SWEEP_RANGE && toward.normalize().dot(flat) >= SWEEP_ARC_DOT)
                key.hurtServer(level, damageSources().mobAttack(this), SWEEP_DAMAGE);
        }
    }

    private final class FightGoal extends Goal
    {
        private int repathDelay;
        private int recover;

        private FightGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            var target = getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void start()
        {
            setAggressive(true);
            repathDelay = 0;
        }

        @Override
        public void stop()
        {
            setAggressive(false);
            getNavigation().stop();
            entityData.set(DATA_BASH, 0);
            entityData.set(DATA_SWEEP, 0);
        }

        @Override
        public void tick()
        {
            var target = getTarget();
            if (target == null)
                return;

            var level = (ServerLevel) level();
            if (bashTicks() > 0)
            {
                tickBash(level, target);
                return;
            }
            if (sweepTicks() > 0)
            {
                tickSweep(level, target);
                return;
            }

            getLookControl().setLookAt(target, 30, 30);
            if (recover > 0)
            {
                recover--;
                return;
            }

            double distance = distanceTo(target);
            boolean sees = getSensing().hasLineOfSight(target);
            if (sees && bashCooldown == 0 && distance <= BASH_RANGE)
            {
                getNavigation().stop();
                entityData.set(DATA_BASH, 1);
                return;
            }
            if (sees && distance <= SWEEP_START_RANGE)
            {
                getNavigation().stop();
                entityData.set(DATA_SWEEP, 1);
                return;
            }

            if (--repathDelay <= 0)
            {
                repathDelay = REPATH_TICKS;
                getNavigation().moveTo(target, 1);
            }
        }

        private void tickBash(ServerLevel level, LivingEntity target)
        {
            getLookControl().setLookAt(target, 60, 60);
            int ticks = bashTicks() + 1;
            if (ticks == BASH_WINDUP_TICKS)
            {
                bashCooldown = BASH_COOLDOWN_TICKS;
                if (distanceTo(target) <= BASH_RANGE + 0.5 && Mth.abs(Mth.degreesDifference(yBodyRot, angleTo(target))) < 90)
                    bash(level, target);
            }
            if (ticks >= BASH_WINDUP_TICKS + FOLLOW_THROUGH_TICKS)
            {
                ticks = 0;
                recover = RECOVER_TICKS;
            }
            entityData.set(DATA_BASH, ticks);
        }

        private void tickSweep(ServerLevel level, LivingEntity target)
        {
            getLookControl().setLookAt(target, 30, 30);
            int ticks = sweepTicks() + 1;
            if (ticks == SWEEP_WINDUP_TICKS)
                sweep(level);
            if (ticks >= SWEEP_WINDUP_TICKS + FOLLOW_THROUGH_TICKS)
            {
                ticks = 0;
                recover = RECOVER_TICKS;
            }
            entityData.set(DATA_SWEEP, ticks);
        }

        private float angleTo(LivingEntity target)
        {
            return (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90;
        }
    }
}
