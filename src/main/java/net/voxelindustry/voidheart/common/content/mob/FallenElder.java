package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Never comes close. It keeps its distance, throws slow homing orbs and raises Novices to do the rest.
 */
public class FallenElder extends VoidMonster
{
    public static final int ORB_INTERVAL = 3 * 20;
    public static final int RAISE_INTERVAL = 30 * 20;
    public static final int RAISED_AT_ONCE = 2;
    public static final int MAX_RAISED = 4;

    private static final EntityDataAccessor<Integer> DATA_CAST_TICKS = SynchedEntityData.defineId(FallenElder.class, EntityDataSerializers.INT);

    private static final int EXPERIENCE = 25;
    private static final int CAST_SHOW_TICKS = 14;
    private static final double KEEP_MIN = 8;
    private static final double KEEP_MAX = 14;
    private static final double ORB_RANGE = 24;
    private static final float ORB_DAMAGE = 8;
    private static final double RAISE_RADIUS = 2.5;
    private static final int RAISE_ATTEMPTS = 6;

    private final List<UUID> raised = new ArrayList<>();
    private int orbCooldown = ORB_INTERVAL;
    private int raiseCooldown = ORB_INTERVAL * 2;

    public FallenElder(EntityType<? extends FallenElder> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(80, ORB_DAMAGE, 0.25);
    }

    public int castTicks()
    {
        return entityData.get(DATA_CAST_TICKS);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_CAST_TICKS, 0);
    }

    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(2, new KeepDistanceGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        addIdleGoals(6);
        addPlayerTarget(true);
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.FALLEN_ELDER;
    }

    @Override
    protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);

        if (castTicks() > 0)
            entityData.set(DATA_CAST_TICKS, castTicks() - 1);
        if (orbCooldown > 0)
            orbCooldown--;
        if (raiseCooldown > 0)
            raiseCooldown--;

        var target = getTarget();
        if (target == null || !target.isAlive())
            return;

        if (orbCooldown == 0 && distanceTo(target) <= ORB_RANGE && getSensing().hasLineOfSight(target))
        {
            orbCooldown = ORB_INTERVAL;
            castOrb(level, target);
        }
        if (raiseCooldown == 0)
        {
            raiseCooldown = RAISE_INTERVAL;
            raise(level, target);
        }
    }

    private void castOrb(ServerLevel level, LivingEntity target)
    {
        entityData.set(DATA_CAST_TICKS, CAST_SHOW_TICKS);
        playSound(VoidHeartSounds.ELDER_CAST, 1.2F, 1);

        var orb = new VoidOrb(level, this, target, ORB_DAMAGE);
        var hand = getEyePosition().add(getLookAngle().scale(1.1));
        orb.setPos(hand.x, hand.y - 0.2, hand.z);
        level.addFreshEntity(orb);
    }

    private void raise(ServerLevel level, LivingEntity target)
    {
        raised.removeIf(id ->
        {
            var entity = level.getEntity(id);
            return entity == null || !entity.isAlive();
        });

        int count = Math.min(RAISED_AT_ONCE, MAX_RAISED - raised.size());
        if (count <= 0)
            return;

        boolean any = false;
        for (int i = 0; i < count; i++)
            any |= raiseOne(level, target);
        if (any)
        {
            entityData.set(DATA_CAST_TICKS, CAST_SHOW_TICKS);
            playSound(VoidHeartSounds.ELDER_RAISE, 1.2F, 1);
        }
    }

    private boolean raiseOne(ServerLevel level, LivingEntity target)
    {
        for (int attempt = 0; attempt < RAISE_ATTEMPTS; attempt++)
        {
            double angle = random.nextDouble() * Math.PI * 2;
            var spot = new Vec3(getX() + Math.cos(angle) * RAISE_RADIUS, getY(), getZ() + Math.sin(angle) * RAISE_RADIUS);
            var novice = VoidHeartEntities.FALLEN_NOVICE.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (novice == null)
                return false;

            novice.snapTo(spot.x, spot.y, spot.z, random.nextFloat() * 360, 0);
            if (!level.noCollision(novice) || level.getBlockState(novice.blockPosition().below()).isAir())
                continue;

            novice.setTarget(target);
            level.addFreshEntity(novice);
            raised.add(novice.getUUID());
            level.sendParticles(ParticleTypes.SOUL, spot.x, spot.y + 0.6, spot.z, 14, 0.3, 0.5, 0.3, 0.02);
            return true;
        }
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        output.store("Raised", UUIDUtil.CODEC.listOf(), raised);
        output.putInt("RaiseCooldown", raiseCooldown);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        raised.clear();
        input.read("Raised", UUIDUtil.CODEC.listOf()).ifPresent(raised::addAll);
        raiseCooldown = input.getIntOr("RaiseCooldown", 0);
    }

    private final class KeepDistanceGoal extends Goal
    {
        private int pathDelay;

        private KeepDistanceGoal()
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
        public void stop()
        {
            getNavigation().stop();
        }

        @Override
        public void tick()
        {
            var target = getTarget();
            if (target == null)
                return;

            getLookControl().setLookAt(target, 30, 30);
            if (--pathDelay > 0)
                return;
            pathDelay = 10;

            double distance = distanceTo(target);
            if (distance < KEEP_MIN)
            {
                var away = DefaultRandomPos.getPosAway(FallenElder.this, 12, 6, target.position());
                if (away != null)
                    getNavigation().moveTo(away.x, away.y, away.z, 1.15);
            }
            else if (distance > KEEP_MAX || !getSensing().hasLineOfSight(target))
                getNavigation().moveTo(target, 1);
            else
                getNavigation().stop();
        }
    }
}
