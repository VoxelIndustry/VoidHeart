package net.voxelindustry.voidheart.common.content.underway.security;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.voxelindustry.voidheart.common.content.underway.SecurityBalance;

import java.util.EnumSet;

/**
 * A small crawler of the Underway that carries a charge. It is kept folded under the grille of a duct. Woken, it drops, and goes straight for whoever is near without needing to see them, so no cover hides from it. Close enough, it lights its fuse, whistles faster and faster, and bursts.
 * <p>
 * The burst harms whoever stands near with nothing solid between, and breaks no block. It is frail: knocked apart before its fuse runs out, it never bursts.
 */
public class Sapper extends SecurityMob
{
    public static final float WIDTH = 0.5F;
    public static final float HEIGHT = 0.45F;
    public static final int WAKE_TICKS = 10;

    // Ticks its fuse has burnt for, 0 while it is not lit
    private static final EntityDataAccessor<Integer> DATA_FUSE = SynchedEntityData.defineId(Sapper.class, EntityDataSerializers.INT);
    private static final int REPATH_EVERY = 8;
    private static final double FUSE_SPEED = 0.7;

    /** Kept by the client for show: how far its fuse has burnt, from 0 to 1. */
    public float lit;
    public float litO;

    public Sapper(EntityType<? extends Sapper> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, SecurityBalance.SAPPER_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, SecurityBalance.SAPPER_SPEED)
                .add(Attributes.ARMOR, 2)
                .add(Attributes.FOLLOW_RANGE, SecurityBalance.SAPPER_SIGHT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData)
    {
        super.defineSynchedData(entityData);
        entityData.define(DATA_FUSE, 0);
    }

    @Override
    public int wakeTicks()
    {
        return WAKE_TICKS;
    }

    @Override
    protected void registerGoals()
    {
        // Once it has dropped out of its duct it has no post to go back to
        goalSelector.addGoal(1, new ChaseGoal());
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // It needs no sight of whoever it goes for
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    /** @return ticks its fuse has burnt for, 0 while it is not lit */
    public int fuse()
    {
        return entityData.get(DATA_FUSE);
    }

    @Override
    protected void serverTick(ServerLevel level)
    {
        if (!isUnderPressure() || !isAlive())
            return;
        int fuse = fuse();
        var target = getTarget();
        if (fuse == 0)
        {
            if (target == null || distanceToSqr(target) > SecurityBalance.SAPPER_ARM_RANGE * SecurityBalance.SAPPER_ARM_RANGE)
                return;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.TNT_PRIMED, getSoundSource(), 1.0F, 1.6F);
        }
        entityData.set(DATA_FUSE, ++fuse);
        int left = SecurityBalance.SAPPER_FUSE_TICKS - fuse;
        // The whistle quickens as the fuse runs out
        if (fuse % (left < 10 ? 2 : 5) == 0)
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), getSoundSource(), 1.2F, 1.5F + 0.5F * fuse / SecurityBalance.SAPPER_FUSE_TICKS);
        if (left <= 0)
            burst(level);
    }

    private void burst(ServerLevel level)
    {
        var middle = getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.EXPLOSION, middle.x, middle.y, middle.z, 2, 0.4, 0.2, 0.4, 0);
        level.sendParticles(ParticleTypes.CLOUD, middle.x, middle.y, middle.z, 24, 0.6, 0.3, 0.6, 0.08);
        level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.GENERIC_EXPLODE.value(), getSoundSource(), 1.6F, 1.3F);
        double radius = SecurityBalance.SAPPER_BLAST_RADIUS;
        for (var player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(radius), EntitySelector.NO_CREATIVE_OR_SPECTATOR))
        {
            double away = player.getBoundingBox().getCenter().distanceTo(middle);
            if (away > radius || level.clip(new ClipContext(middle, player.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS)
                continue;
            var source = damageSources().explosion(this, this);
            float damage = (float) (SecurityBalance.SAPPER_DAMAGE * (1 - 0.5 * away / radius));
            if (player.hurtServer(level, source, damage))
                player.knockback(0.8, getX() - player.getX(), getZ() - player.getZ(), source, damage);
        }
        // Nothing is left of it to keel over
        link().destroyed(level);
        discard();
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source)
    {
        return false;
    }

    @Override
    protected void clientTick()
    {
        litO = lit;
        lit = Math.clamp(fuse() / (float) SecurityBalance.SAPPER_FUSE_TICKS, 0, 1);
    }

    /** Runs at whoever it goes for, a little slower once its fuse is lit. */
    private final class ChaseGoal extends Goal
    {
        private int repath;

        private ChaseGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            return getTarget() != null && getTarget().isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void start()
        {
            repath = 0;
        }

        @Override
        public void tick()
        {
            var target = getTarget();
            if (target == null)
                return;
            getLookControl().setLookAt(target, 30, 30);
            if (repath-- <= 0)
            {
                repath = REPATH_EVERY;
                getNavigation().moveTo(target, fuse() > 0 ? FUSE_SPEED : 1.0);
            }
        }

        @Override
        public void stop()
        {
            getNavigation().stop();
        }
    }
}
