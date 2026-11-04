package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;

import java.util.EnumSet;

/**
 * Shuffles slower than a zombie, then closes the last few blocks all at once.
 */
public class VoidGhoul extends VoidMonster
{
    private static final EntityDataAccessor<Boolean> DATA_LUNGING = SynchedEntityData.defineId(VoidGhoul.class, EntityDataSerializers.BOOLEAN);

    private static final int EXPERIENCE = 7;
    private static final double LUNGE_MIN_DISTANCE = 2.5;
    private static final double LUNGE_MAX_DISTANCE = 6;
    private static final double LUNGE_SPEED = 0.85;
    private static final double LUNGE_LIFT = 0.32;
    private static final int LUNGE_COOLDOWN = 70;

    public VoidGhoul(EntityType<? extends VoidGhoul> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(34, 7, 0.19);
    }

    public boolean isLunging()
    {
        return entityData.get(DATA_LUNGING);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_LUNGING, false);
    }

    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(2, new LungeGoal());
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        addIdleGoals(6);
        addPlayerTarget(true);
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.VOID_GHOUL;
    }

    private final class LungeGoal extends Goal
    {
        private int cooldown;

        private LungeGoal()
        {
            setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
        }

        @Override
        public boolean canUse()
        {
            if (cooldown > 0)
            {
                cooldown--;
                return false;
            }

            var target = getTarget();
            if (target == null || !onGround() || !hasLineOfSight(target))
                return false;

            double distance = distanceTo(target);
            return distance >= LUNGE_MIN_DISTANCE && distance <= LUNGE_MAX_DISTANCE;
        }

        @Override
        public boolean canContinueToUse()
        {
            return !onGround();
        }

        @Override
        public void start()
        {
            var target = getTarget();
            var toward = target.position().subtract(position()).multiply(1, 0, 1).normalize();
            setDeltaMovement(toward.x * LUNGE_SPEED, LUNGE_LIFT, toward.z * LUNGE_SPEED);
            playSound(VoidHeartSounds.GHOUL_LUNGE, 1, 1);
            entityData.set(DATA_LUNGING, true);
        }

        @Override
        public void stop()
        {
            entityData.set(DATA_LUNGING, false);
            cooldown = LUNGE_COOLDOWN;
        }
    }
}
