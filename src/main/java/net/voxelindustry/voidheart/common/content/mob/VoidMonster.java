package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneBalance;
import net.voxelindustry.voidheart.common.energy.ley.LeyWards;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusFence;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;

/**
 * What every mob of the void shares. They only ever fight players and never despawn, as a cleared dungeon must stay cleared.
 * In Peaceful they lie dormant rather than being removed, and are back as they were when the difficulty rises again.
 * In the Daedalus they never leave their district, so a player on a bridge is out of their reach but for what they throw or fire.
 */
public abstract class VoidMonster extends Monster
{
    public static final double NOTICE_RANGE = 24;

    private static final EntityDataAccessor<Boolean> DATA_DORMANT = SynchedEntityData.defineId(VoidMonster.class, EntityDataSerializers.BOOLEAN);

    private static final float STEP_VOLUME = 0.15F;
    // Beyond every mob's notice and follow range, so a resting mob misses nothing it could have reacted to
    private static final double REST_RANGE = 40;
    private static final int REST_CHECK_TICKS = 20;

    private boolean resting;

    private static final int    WARD_CHECK_TICKS  = 10;
    private static final double WARD_LEAVE_MARGIN = 2;
    private static final double WARD_LEAVE_SPEED  = 1.2;
    private static final double WARD_PUSH         = 0.3;

    protected VoidMonster(EntityType<? extends VoidMonster> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes(double health, double damage, double speed)
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.FOLLOW_RANGE, NOTICE_RANGE);
    }

    protected abstract MobSounds sounds();

    protected void addIdleGoals(int priority)
    {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(priority, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(priority, new RandomLookAroundGoal(this));
    }

    protected void addPlayerTarget(boolean mustSee)
    {
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, mustSee));
    }

    public boolean isDormant()
    {
        return entityData.get(DATA_DORMANT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_DORMANT, false);
    }

    @Override
    public void tick()
    {
        if (!level().isClientSide())
        {
            updateDormancy();
            if ((tickCount + getId()) % WARD_CHECK_TICKS == 0)
                leaveWard();
            if ((tickCount + getId()) % REST_CHECK_TICKS == 0)
                resting = isInDaedalus() && level().getNearestPlayer(this, REST_RANGE) == null;
            if (isResting())
                return;
        }
        super.tick();
    }

    /**
     * The Daedalus keeps a hundred mobs to a district and loads several districts around each player, so those with no one near rest where they stand and skip
     * their ticks altogether, which is most of what so many would cost. Only one held up by something rests, so none hangs in the air, and anything happening to it
     * wakes it.
     */
    public boolean isResting()
    {
        return resting && getTarget() == null && hurtTime == 0 && !isDeadOrDying() && !isOnFire() && (onGround() || isNoGravity() || isHeldInPlace());
    }

    /**
     * @return whether something other than the ground or a lack of gravity holds it where it is, as a wall holds a crawler
     */
    protected boolean isHeldInPlace()
    {
        return false;
    }

    private void updateDormancy()
    {
        boolean peaceful = level().getDifficulty() == Difficulty.PEACEFUL;
        if (peaceful == isDormant())
            return;

        entityData.set(DATA_DORMANT, peaceful);
        setNoAi(peaceful);
        setInvisible(peaceful);
        setPermanentlyInvulnerable(peaceful);
        if (peaceful)
            setTarget(null);
    }

    @Override
    public boolean isPickable()
    {
        return !isDormant() && super.isPickable();
    }

    @Override
    public boolean isPushable()
    {
        return !isDormant() && super.isPushable();
    }

    @Override
    public void move(MoverType type, Vec3 delta)
    {
        if (isInDaedalus())
        {
            var box = getBoundingBox();
            delta = new Vec3(DaedalusFence.clipX(getX(), getZ(), box.minX, box.maxX, delta.x), DaedalusFence.clipY(box.minY, delta.y), DaedalusFence.clipZ(getX(), getZ(), box.minZ, box.maxZ, delta.z));
        }
        // A ward remembering its kind is a wall it can't step through, though it may always step out of one
        if (type == MoverType.SELF && (delta.x != 0 || delta.z != 0) && level() instanceof ServerLevel server && keptOutOf(server, delta))
            delta = new Vec3(0, delta.y, 0);
        super.move(type, delta);
    }

    /**
     * @return whether it may be put at this spot by its own doing, as by crossing a wall, which only the Daedalus' district edges and floor forbid
     */
    public boolean mayGoTo(Vec3 spot)
    {
        if (level() instanceof ServerLevel server && LeyWards.keepsOut(server, getType(), spot.x, spot.y, spot.z))
            return false;
        return !isInDaedalus() || DaedalusFence.allows(getX(), getZ(), spot.x, spot.y, spot.z);
    }

    /**
     * Whether this step would take it from outside a ward keeping its kind out to inside one.
     */
    private boolean keptOutOf(ServerLevel level, Vec3 delta)
    {
        if (!LeyWards.keepsOut(level, getType(), getX() + delta.x, getY() + delta.y, getZ() + delta.z))
            return false;
        return !LeyWards.keepsOut(level, getType(), getX(), getY(), getZ());
    }

    @Override
    protected PathNavigation createNavigation(Level level)
    {
        return WardedPaths.ground(this, level);
    }

    /**
     * One that finds itself inside a ward keeping its kind out, set there or caught as the ward woke, walks back out of its reach.
     */
    private void leaveWard()
    {
        var ward = LeyWards.keptOutOf(this);
        if (ward == null)
            return;
        var away = position().subtract(ward).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4)
            away = new Vec3(1, 0, 0);
        var target = ward.add(away.normalize().scale(LeyStoneBalance.WARD_RADIUS + WARD_LEAVE_MARGIN));
        setTarget(null);
        getNavigation().moveTo(target.x, getY(), target.z, WARD_LEAVE_SPEED);
        // Pressed outward too, so a goal steering it back in can't keep it there
        addDeltaMovement(away.normalize().scale(WARD_PUSH));
    }

    /** Whoever goes down into the Underway is out of the void's reach: nothing of the Daedalus follows, hunts or strikes at them from above. */
    @Override
    public boolean canAttack(LivingEntity target)
    {
        return super.canAttack(target) && !(isInDaedalus() && DaedalusFence.isUnderFloor(target.getY()));
    }

    protected boolean isInDaedalus()
    {
        return level().dimension() == VoidDaedalus.WORLD_KEY;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr)
    {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && source.getEntity() instanceof Player player && !player.isCreative() && !player.isSpectator())
            setTarget(player);
        return hurt;
    }

    @Override
    public void playAmbientSound()
    {
        if (!isDormant())
            super.playAmbientSound();
    }

    @Override
    public SoundEvent getAmbientSound()
    {
        return sounds().ambient();
    }

    @Override
    public SoundEvent getHurtSound(DamageSource source)
    {
        return sounds().hurt();
    }

    @Override
    public SoundEvent getDeathSound()
    {
        return sounds().death();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block)
    {
        playSound(sounds().step(), STEP_VOLUME, 1);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        output.putBoolean("Dormant", isDormant());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        entityData.set(DATA_DORMANT, input.getBooleanOr("Dormant", false));
    }
}
