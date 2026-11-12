package net.voxelindustry.voidheart.common.content.underway.security;

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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A piece of the Underway's security built into a fort: it never moves, and the housing it sits in is a block that stays behind as its wreck. It is a living thing so that it can be struck and can strike back.
 * <p>
 * Whoever harms one is reported: the blow sounds its section's alarm.
 */
public abstract class MountedUnit extends Mob
{
    private static final EntityDataAccessor<Boolean> DATA_AWAKE = SynchedEntityData.defineId(MountedUnit.class, EntityDataSerializers.BOOLEAN);

    private final UnitLink link = new UnitLink();

    protected MountedUnit(EntityType<? extends MountedUnit> type, Level level)
    {
        super(type, level);
        setNoGravity(true);
        setNoAi(true);
        setPersistenceRequired();
        noPhysics = true;
    }

    public UnitLink link()
    {
        return link;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData)
    {
        super.defineSynchedData(entityData);
        entityData.define(DATA_AWAKE, false);
    }

    public boolean isAwake()
    {
        return entityData.get(DATA_AWAKE);
    }

    /** @return the way it was built to face, level with the floor */
    public Vec3 facing()
    {
        return Vec3.directionFromRotation(0, getYRot());
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!(level() instanceof ServerLevel level))
        {
            clientTick();
            return;
        }
        if (link.isWreck(level))
        {
            discard();
            return;
        }
        boolean awake = link.isAwake(level);
        entityData.set(DATA_AWAKE, awake);
        if (isAlive())
            guard(level, awake);
    }

    /** Does its duty for a tick, given whether its section has woken it. */
    protected abstract void guard(ServerLevel level, boolean awake);

    protected void clientTick()
    {
    }

    /** @return whether a blow glances off it */
    protected boolean isArmoured(DamageSource source)
    {
        return false;
    }

    /** @return whether it tells its section when someone harms it */
    protected boolean reports()
    {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        boolean reported = reports() && source.getEntity() instanceof Player player && !player.isCreative();
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && isArmoured(source))
        {
            if (source.getEntity() != null)
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_PLACE, getSoundSource(), 0.5F, 1.8F);
            if (reported)
                link.raiseAlarm(level);
            return false;
        }
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && reported)
            link.raiseAlarm(level);
        return hurt;
    }

    @Override
    public void die(DamageSource source)
    {
        super.die(source);
        if (!(level() instanceof ServerLevel level))
            return;
        link.destroyed(level);
        var middle = getBoundingBox().getCenter();
        level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.GENERIC_EXPLODE.value(), getSoundSource(), 0.6F, 1.6F);
        level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.ANVIL_LAND, getSoundSource(), 0.6F, 0.6F);
        level.sendParticles(ParticleTypes.EXPLOSION, middle.x, middle.y, middle.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, middle.x, middle.y, middle.z, 30, 0.25, 0.25, 0.25, 0.5);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, middle.x, middle.y, middle.z, 8, 0.2, 0.2, 0.2, 0.02);
    }

    /** A wreck does not keel over: it is gone at once, and its housing is what is left of it. */
    @Override
    protected void tickDeath()
    {
        if (!level().isClientSide())
            discard();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        link.save(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        link.load(input);
    }

    @Override
    public void knockback(double power, double xd, double zd, DamageSource source, float damage, boolean comesFromEffect)
    {
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    protected void pushEntities()
    {
    }

    @Override
    public void travel(Vec3 input)
    {
    }

    @Override
    public boolean isAffectedByPotions()
    {
        return false;
    }

    @Override
    public boolean canBeLeashed()
    {
        return false;
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
    public boolean canUsePortal(boolean ignorePassenger)
    {
        return false;
    }

    @Override
    public boolean shouldDropExperience()
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
    public @Nullable SoundEvent getDeathSound()
    {
        return null;
    }
}
