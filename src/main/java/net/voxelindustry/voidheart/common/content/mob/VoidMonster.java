package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;

/**
 * What every mob of the void shares. They only ever fight players and never despawn, as a cleared dungeon must stay cleared.
 * In Peaceful they lie dormant rather than being removed, and are back as they were when the difficulty rises again.
 */
public abstract class VoidMonster extends Monster
{
    public static final double NOTICE_RANGE = 24;

    private static final EntityDataAccessor<Boolean> DATA_DORMANT = SynchedEntityData.defineId(VoidMonster.class, EntityDataSerializers.BOOLEAN);

    private static final float STEP_VOLUME = 0.15F;

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
            updateDormancy();
        super.tick();
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
