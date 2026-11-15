package net.voxelindustry.voidheart.common.content.sanctum;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.voidheart.common.content.mob.VoidMonster;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

import static net.voxelindustry.voidheart.common.content.sanctum.SanctumBalance.*;

/**
 * A heap of black protoplasm the mass threw off, never quite one shape: eyes and mouths well up in it and sink again as it goes, and it lashes
 * with whatever limb it grows. Cut open, it falls in two smaller ones, once. Badly hurt, it crawls back to the mass and is taken in, healing it.
 */
public class LesserShoggoth extends VoidMonster
{
    private static final EntityDataAccessor<Boolean> DATA_SMALL = SynchedEntityData.defineId(LesserShoggoth.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_FEEDING = SynchedEntityData.defineId(LesserShoggoth.class, EntityDataSerializers.BOOLEAN);

    public static final byte EVENT_LASH = 70;

    private static final double CHASE_SPEED = 1.0;
    private static final double FEED_SPEED = 1.25;
    private static final int REPATH_TICKS = 10;
    private static final float SPLIT_SPREAD = 0.4F;
    /** Within this of the mass, a shoggoth whose path gave out pours itself over whatever is in the way. */
    private static final double SLITHER_REACH = 8;
    private static final double SLITHER_SPEED = 0.16;

    private int lashTicks;
    private int lashCooldown;
    private boolean fed;

    /** Client only: ticks since the last lash began, or -1. */
    public int lashAge = -1;

    public LesserShoggoth(EntityType<? extends LesserShoggoth> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(SHOGGOTH_HEALTH, SHOGGOTH_DAMAGE, SHOGGOTH_SPEED)
                .add(Attributes.ARMOR, SHOGGOTH_ARMOR)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.LESSER_SHOGGOTH;
    }

    @Override
    protected void registerGoals()
    {
        addIdleGoals(6);
        goalSelector.addGoal(1, new FeedGoal());
        goalSelector.addGoal(2, new ChaseGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        addPlayerTarget(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_SMALL, false);
        builder.define(DATA_FEEDING, false);
    }

    public boolean isSmall()
    {
        return entityData.get(DATA_SMALL);
    }

    public boolean isFeeding()
    {
        return entityData.get(DATA_FEEDING);
    }

    /** Makes it one of the two a shoggoth falls into: weaker, quicker, and done splitting. */
    public void makeSmall()
    {
        entityData.set(DATA_SMALL, true);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(SHOGGOTH_SMALL_HEALTH);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(SHOGGOTH_SMALL_DAMAGE);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SHOGGOTH_SMALL_SPEED);
        getAttribute(Attributes.SCALE).setBaseValue(SHOGGOTH_SMALL_SCALE);
        setHealth(getMaxHealth());
    }

    @Override
    protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);
        if (lashCooldown > 0)
            lashCooldown--;
        var target = getTarget();
        if (lashTicks > 0)
        {
            lashTicks--;
            if (target != null)
                getLookControl().setLookAt(target);
            if (lashTicks == 0 && target != null && target.isAlive() && reaches(target, LASH_REACH + 0.6))
            {
                doHurtTarget(level, target);
                playSound(VoidHeartSounds.SHOGGOTH_LASH, 1, 0.8F + random.nextFloat() * 0.3F);
            }
            return;
        }
        if (target != null && target.isAlive() && !isFeeding() && lashCooldown == 0 && reaches(target, LASH_REACH) && hasLineOfSight(target))
        {
            lashTicks = LASH_WINDUP;
            lashCooldown = LASH_COOLDOWN;
            level.broadcastEntityEvent(this, EVENT_LASH);
        }
    }

    private boolean reaches(LivingEntity target, double reach)
    {
        double edge = getBbWidth() / 2 + target.getBbWidth() / 2;
        return distanceTo(target) <= reach * getScale() + edge;
    }

    @Override
    public void handleEntityEvent(byte event)
    {
        if (event == EVENT_LASH)
            lashAge = 0;
        else
            super.handleEntityEvent(event);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (level().isClientSide() && lashAge >= 0 && ++lashAge > LASH_WINDUP + 10)
            lashAge = -1;
    }

    @Override
    public void die(DamageSource source)
    {
        if (!fed && !isSmall() && level() instanceof ServerLevel level && !isRemoved())
            split(level);
        super.die(source);
    }

    private void split(ServerLevel level)
    {
        for (int i = 0; i < 2; i++)
        {
            var part = VoidHeartEntities.LESSER_SHOGGOTH.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (part == null)
                continue;
            float side = i == 0 ? -SPLIT_SPREAD : SPLIT_SPREAD;
            part.snapTo(getX() + side, getY(), getZ() - side, getYRot() + i * 180, 0);
            part.makeSmall();
            part.setPersistenceRequired();
            part.setTarget(getTarget());
            part.setDeltaMovement(side * 0.6, 0.3, -side * 0.6);
            level.addFreshEntity(part);
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, VoidHeartBlocks.VILE_FLESH.defaultBlockState()), getX(), getY() + 0.5, getZ(), 40, 0.4, 0.3, 0.4, 0.15);
        level.playSound(null, getX(), getY(), getZ(), VoidHeartSounds.SHOGGOTH_SPLIT, SoundSource.HOSTILE, 1, 0.9F);
    }

    private @Nullable VileFleshMass nearestMass()
    {
        var masses = level().getEntitiesOfClass(VileFleshMass.class, getBoundingBox().inflate(FEED_RANGE), mass -> mass.isAlive() && !mass.isDeadOrDying());
        VileFleshMass nearest = null;
        for (var mass : masses)
        {
            if (nearest == null || distanceToSqr(mass) < distanceToSqr(nearest))
                nearest = mass;
        }
        return nearest;
    }

    /** Taken back into the mass it came from. */
    private void feed(VileFleshMass mass)
    {
        fed = true;
        mass.feed(this);
        discard();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        output.putBoolean("Small", isSmall());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        entityData.set(DATA_SMALL, input.getBooleanOr("Small", false));
    }

    /** Keeps on its prey, close enough to lash, without pressing into it. */
    private class ChaseGoal extends Goal
    {
        private int repath;

        ChaseGoal()
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
        public void tick()
        {
            var target = getTarget();
            if (target == null)
                return;
            getLookControl().setLookAt(target, 30, 30);
            if (--repath > 0)
                return;
            repath = REPATH_TICKS;
            if (reaches(target, LASH_REACH * 0.6))
                getNavigation().stop();
            else
                getNavigation().moveTo(target, CHASE_SPEED);
        }
    }

    /** Badly hurt, it gives up the fight and crawls to the mass to be taken back in. */
    private class FeedGoal extends Goal
    {
        private @Nullable VileFleshMass mass;
        private int repath;

        FeedGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            if (getHealth() / getMaxHealth() >= FEED_BELOW)
                return false;
            mass = nearestMass();
            return mass != null;
        }

        @Override
        public boolean canContinueToUse()
        {
            return mass != null && mass.isAlive() && !mass.isDeadOrDying();
        }

        @Override
        public void start()
        {
            entityData.set(DATA_FEEDING, true);
            repath = 0;
        }

        @Override
        public void stop()
        {
            entityData.set(DATA_FEEDING, false);
            mass = null;
        }

        @Override
        public void tick()
        {
            if (mass == null)
                return;
            getLookControl().setLookAt(mass);
            double reach = mass.getBbWidth() / 2 + getBbWidth() / 2 + 0.8;
            if (distanceTo(mass) <= reach)
            {
                feed(mass);
                return;
            }
            if (getNavigation().isDone() && distanceTo(mass) < reach + SLITHER_REACH)
            {
                var toward = mass.position().subtract(position()).multiply(1, 0, 1).normalize().scale(SLITHER_SPEED);
                setDeltaMovement(toward.x, horizontalCollision && onGround() ? 0.42 : getDeltaMovement().y, toward.z);
            }
            if (--repath <= 0)
            {
                repath = REPATH_TICKS;
                getNavigation().moveTo(mass, FEED_SPEED);
            }
        }
    }
}
