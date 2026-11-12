package net.voxelindustry.voidheart.common.content.underway.security;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.SecurityBalance;
import net.voxelindustry.voidheart.common.content.underway.UnderwaySecurity;
import net.voxelindustry.voidheart.common.world.underway.UnderwayPlans;
import net.voxelindustry.voidheart.common.world.underway.UnderwayUnitKind;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * A repair crawler of the Underway. It harms nobody. While its section's alarm sounds, it goes to the nearest wreck of the section, works on it for a few seconds, and the wreck stands again where it was posted. It cannot rebuild another fitter, nor a sapper that burst, nor a tripwire.
 * <p>
 * So a section stays dangerous for as long as its fitters work: they are worth going for first. Once the alarm stops they go back to their post.
 */
public class Fitter extends SecurityMob
{
    public static final float WIDTH = 0.7F;
    public static final float HEIGHT = 0.7F;
    public static final int WAKE_TICKS = 20;

    private static final EntityDataAccessor<Boolean> DATA_WORKING = SynchedEntityData.defineId(Fitter.class, EntityDataSerializers.BOOLEAN);
    // The wreck it works on, which is where its torch points
    private static final EntityDataAccessor<Vector3fc> DATA_WORK_AT = SynchedEntityData.defineId(Fitter.class, EntityDataSerializers.VECTOR3);
    private static final int LOOK_EVERY = 20;
    private static final int GIVE_UP_TICKS = 30 * 20;
    /** How long it leaves alone a wreck it could not get to. */
    private static final int SKIP_TICKS = 60 * 20;

    /** Kept by the client for show: how far its arm is raised to its work. */
    public float reach;
    public float reachO;

    public Fitter(EntityType<? extends Fitter> type, Level level)
    {
        super(type, level);
        if (getNavigation() instanceof GroundPathNavigation ground)
            ground.setCanOpenDoors(true);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, SecurityBalance.FITTER_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, SecurityBalance.FITTER_SPEED)
                .add(Attributes.ARMOR, SecurityBalance.FITTER_ARMOUR)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData)
    {
        super.defineSynchedData(entityData);
        entityData.define(DATA_WORKING, false);
        entityData.define(DATA_WORK_AT, new Vector3f());
    }

    @Override
    public int wakeTicks()
    {
        return WAKE_TICKS;
    }

    @Override
    protected boolean hunts()
    {
        return false;
    }

    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(1, new RepairGoal());
        goalSelector.addGoal(5, new ReturnToPostGoal());
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6));
    }

    /** @return whether it is at a wreck, rebuilding it */
    public boolean isWorking()
    {
        return entityData.get(DATA_WORKING);
    }

    /** @return the wreck it works on */
    public Vec3 workAt()
    {
        return UnderwayShots.vec(entityData.get(DATA_WORK_AT));
    }

    /** @return whether a fitter can stand that kind of unit up again */
    public static boolean canRebuild(UnderwayUnitKind kind)
    {
        return kind != UnderwayUnitKind.FITTER && kind != UnderwayUnitKind.SAPPER && kind != UnderwayUnitKind.TRIPWIRE;
    }

    @Override
    protected void clientTick()
    {
        reachO = reach;
        reach = Math.clamp(reach + (isWorking() ? 0.15F : -0.15F), 0, 1);
        if (isWorking() && tickCount % 2 == 0)
        {
            var at = workAt();
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, at.x + (random.nextDouble() - 0.5) * 0.5, at.y + (random.nextDouble() - 0.5) * 0.5, at.z + (random.nextDouble() - 0.5) * 0.5,
                    (random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.3);
        }
    }

    /** Goes to the nearest wreck of its section and works on it until it stands again, for as long as the alarm sounds. */
    private final class RepairGoal extends Goal
    {
        // The wrecks it gave up on, each with the tick of its own life it may try again from
        private final Map<Integer, Integer> unreached = new HashMap<>();
        private int wreck = -1;
        private Vec3 at = Vec3.ZERO;
        private int look;
        private int worked;
        private int spent;

        private RepairGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            if (look-- > 0 || !(level() instanceof ServerLevel level) || !link().isAlarmed(level))
                return false;
            look = LOOK_EVERY;
            var security = UnderwaySecurity.of(level);
            var key = link().key();
            if (security == null || key == null)
                return false;
            var fort = UnderwayPlans.fort(level.getSeed(), key.district());
            wreck = -1;
            double nearest = Double.MAX_VALUE;
            for (int index : security.wrecks(key))
            {
                // A wreck it could not get to is left alone for a while, so that one behind a wall does not keep it from the others
                if (index < 0 || index >= fort.units().size() || unreached.getOrDefault(index, 0) > tickCount)
                    continue;
                var unit = fort.units().get(index);
                if (!canRebuild(unit.kind()))
                    continue;
                var place = new Vec3(fort.frame().x(unit.along(), unit.across()) + 0.5, unit.y() + 0.5, fort.frame().z(unit.along(), unit.across()) + 0.5);
                double distance = place.distanceToSqr(position());
                if (distance < nearest)
                {
                    nearest = distance;
                    wreck = index;
                    at = place;
                }
            }
            return wreck >= 0;
        }

        @Override
        public boolean canContinueToUse()
        {
            if (!(level() instanceof ServerLevel level) || wreck < 0 || spent > GIVE_UP_TICKS || !link().isAlarmed(level))
                return false;
            var security = UnderwaySecurity.of(level);
            var key = link().key();
            return security != null && key != null && security.isDestroyed(key, wreck);
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void start()
        {
            worked = 0;
            spent = 0;
            entityData.set(DATA_WORK_AT, new Vector3f((float) at.x, (float) at.y, (float) at.z));
        }

        @Override
        public void tick()
        {
            var level = (ServerLevel) level();
            spent++;
            getLookControl().setLookAt(at.x, at.y, at.z);
            if (Math.hypot(at.x - getX(), at.z - getZ()) > SecurityBalance.FITTER_REACH)
            {
                entityData.set(DATA_WORKING, false);
                if (getNavigation().isDone() || spent % LOOK_EVERY == 0)
                    getNavigation().moveTo(at.x, getY(), at.z, 1.0);
                return;
            }
            getNavigation().stop();
            entityData.set(DATA_WORKING, true);
            if (worked % 15 == 0)
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_USE, getSoundSource(), 0.5F, 1.6F);
            if (++worked < SecurityBalance.FITTER_REPAIR_TICKS)
                return;
            var security = UnderwaySecurity.of(level);
            var key = link().key();
            if (security != null && key != null && security.rebuild(level, key, wreck))
                level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_GOLEM_REPAIR, getSoundSource(), 1.5F, 0.8F);
            wreck = -1;
        }

        @Override
        public void stop()
        {
            if (wreck >= 0 && spent > GIVE_UP_TICKS)
                unreached.put(wreck, tickCount + SKIP_TICKS);
            entityData.set(DATA_WORKING, false);
            getNavigation().stop();
            look = LOOK_EVERY;
        }
    }
}
