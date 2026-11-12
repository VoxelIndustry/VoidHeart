package net.voxelindustry.voidheart.common.content.underway.security;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.SecurityBalance;

/**
 * A machine of the Underway that walks behind a shield of riveted copper as tall as itself. Every blow from in front of the shield glances off. It comes on slowly, throws back whoever it reaches, and from a distance opens a slit in the shield to fire a short burst through it.
 * <p>
 * It brings its shield around slower than a runner circles it, so it is beaten from the side or from behind, or through its slit, which stays open a moment after each burst.
 */
public class Mantlet extends SecurityMob
{
    public static final float WIDTH = 0.9F;
    public static final float HEIGHT = 2.1F;
    public static final int WAKE_TICKS = 30;

    private static final EntityDataAccessor<Boolean> DATA_SLIT = SynchedEntityData.defineId(Mantlet.class, EntityDataSerializers.BOOLEAN);
    // The way its shield faces, which lags behind the way it wants to face
    private static final EntityDataAccessor<Float> DATA_SHIELD_YAW = SynchedEntityData.defineId(Mantlet.class, EntityDataSerializers.FLOAT);
    private static final double SLIT_HEIGHT = 1.5;
    private static final double SLIT_OUT = 0.75;
    private static final float COS_ARC = Mth.cos(SecurityBalance.MANTLET_SHIELD_ARC * Mth.DEG_TO_RAD);

    private int slitTicks;
    private boolean posed;

    /** Kept by the client for show: the way its shield faces, and how far its slit is open. */
    public float shield;
    public float shieldO;
    public float slit;
    public float slitO;

    public Mantlet(EntityType<? extends Mantlet> type, Level level)
    {
        super(type, level);
        if (getNavigation() instanceof GroundPathNavigation ground)
            ground.setCanOpenDoors(true);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, SecurityBalance.MANTLET_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, SecurityBalance.MANTLET_BASH_DAMAGE)
                .add(Attributes.ATTACK_KNOCKBACK, SecurityBalance.MANTLET_BASH_KNOCKBACK)
                .add(Attributes.MOVEMENT_SPEED, SecurityBalance.MANTLET_SPEED)
                .add(Attributes.ARMOR, SecurityBalance.MANTLET_ARMOUR)
                .add(Attributes.ARMOR_TOUGHNESS, 4)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1)
                .add(Attributes.FOLLOW_RANGE, SecurityBalance.AUTOMATON_SIGHT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData)
    {
        super.defineSynchedData(entityData);
        entityData.define(DATA_SLIT, false);
        entityData.define(DATA_SHIELD_YAW, 0F);
    }

    @Override
    public int wakeTicks()
    {
        return WAKE_TICKS;
    }

    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(2, new BoltGoal(SecurityBalance.MANTLET_BASH_RANGE, SecurityBalance.MANTLET_SHOT_RANGE, SecurityBalance.MANTLET_AIM_TICKS, SecurityBalance.MANTLET_LOCK_TICKS,
                SecurityBalance.MANTLET_BURST_SHOTS, SecurityBalance.MANTLET_SHOT_EVERY, SecurityBalance.MANTLET_RELOAD_TICKS, SecurityBalance.MANTLET_SHOT_DAMAGE)
        {
            @Override
            public void start()
            {
                super.start();
                slitTicks = Integer.MAX_VALUE;
                entityData.set(DATA_SLIT, true);
                playSound(SoundEvents.IRON_TRAPDOOR_OPEN, 1.0F, 1.4F);
            }

            @Override
            public void stop()
            {
                super.stop();
                slitTicks = SecurityBalance.MANTLET_SLIT_TICKS;
            }
        });
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(5, new ReturnToPostGoal());
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** @return whether the slit in its shield is open, which is when a blow from in front goes through */
    public boolean isSlitOpen()
    {
        return entityData.get(DATA_SLIT);
    }

    /** @return the way its shield faces, as a yaw in degrees */
    public float shieldYaw()
    {
        return entityData.get(DATA_SHIELD_YAW);
    }

    public boolean isAimLocked()
    {
        return aimTicks() > SecurityBalance.MANTLET_AIM_TICKS - SecurityBalance.MANTLET_LOCK_TICKS;
    }

    /** @return the slit in its shield, where its bolts leave from */
    @Override
    public Vec3 muzzle()
    {
        var forward = Vec3.directionFromRotation(0, shieldYaw());
        return new Vec3(getX() + forward.x * SLIT_OUT, getY() + SLIT_HEIGHT, getZ() + forward.z * SLIT_OUT);
    }

    @Override
    protected void serverTick(ServerLevel level)
    {
        // The shield swings toward whoever it hunts, or the way it walks, only so fast
        var target = getTarget();
        float wanted = target != null ? (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90 : yBodyRot;
        if (!posed)
        {
            // Until it first moves its shield faces the way it was posted
            posed = true;
            entityData.set(DATA_SHIELD_YAW, yBodyRot);
        }
        else if (isUnderPressure())
            entityData.set(DATA_SHIELD_YAW, Mth.approachDegrees(shieldYaw(), wanted, SecurityBalance.MANTLET_TURN_PER_TICK));
        if (slitTicks != Integer.MAX_VALUE && isSlitOpen() && --slitTicks <= 0)
        {
            entityData.set(DATA_SLIT, false);
            playSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.0F, 1.4F);
        }
    }

    @Override
    protected boolean isArmoured(DamageSource source)
    {
        var from = source.getSourcePosition();
        if (from == null || isSlitOpen())
            return false;
        var way = new Vec3(from.x - getX(), 0, from.z - getZ());
        if (way.lengthSqr() < 1.0E-4)
            return false;
        return way.normalize().dot(Vec3.directionFromRotation(0, shieldYaw())) >= COS_ARC;
    }

    @Override
    protected void clientTick()
    {
        shieldO = shield;
        shield = shieldO + Mth.wrapDegrees(shieldYaw() - shieldO);
        slitO = slit;
        slit = Math.clamp(slit + (isSlitOpen() ? 0.25F : -0.2F), 0, 1);
    }
}
