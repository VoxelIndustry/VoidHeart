package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Keeps five or six blocks behind its prey and never shows itself by choice. Being looked at stops it dead and makes it smile, and a long enough stare sets
 * off a rush that normal armor barely slows. Left unnoticed for too long it creeps in on its own.
 */
public class StalkingWraith extends Wraith
{
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "wraith_rush"));
    public static final float RUSH_DAMAGE = 60;
    public static final float CREEP_DAMAGE = RUSH_DAMAGE / 3;
    public static final float SHIELD_DISABLE_SECONDS = 5;

    public enum Phase
    {
        STALK,
        STARE,
        RUSH,
        RECOVER,
        CREEP
    }

    private static final EntityDataAccessor<Integer> DATA_CONTACT = SynchedEntityData.defineId(StalkingWraith.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RUSHING = SynchedEntityData.defineId(StalkingWraith.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_REVEALED = SynchedEntityData.defineId(StalkingWraith.class, EntityDataSerializers.BOOLEAN);

    private static final int EXPERIENCE = 40;
    private static final double SENSE_RANGE = 40;
    private static final double GAZE_CONE = 0.06;
    private static final double FOLLOW_SPEED = 1.15;
    private static final double CREEP_SPEED = 1.1;
    private static final double RUSH_SPEED = 0.8;
    private static final double RUSH_HOMING = 0.12;
    private static final double RUSH_REACH = 0.35;
    private static final int RUSH_MAX_TICKS = 16;
    private static final int RECOVER_TICKS = 40;
    private static final double CREEP_REACH = 1;
    private static final double UNNOTICED_RANGE = 12;
    private static final double BEHIND_REACH = 10;
    private static final int REPATH_TICKS = 10;
    private static final double FINE_APPROACH = 2;
    private static final float SMILE_PER_TICK = 1F / 15;
    // Close enough to stalk is close enough to be seen, and it shows itself quickly but fades away slowly
    public static final double REVEAL_DISTANCE = 7;
    private static final float REVEAL_PER_TICK = 1F / 10;
    private static final float FADE_PER_TICK = 1F / 30;
    private static final double SLURP_RANGE = 16;
    private static final int SLURP_MIN_TICKS = 50;
    private static final int SLURP_SPREAD_TICKS = 40;

    private Phase phase = Phase.STALK;
    private int unnoticedTicks;
    private boolean provoked;
    private int slurpDelay = SLURP_MIN_TICKS;

    public float smile;
    public float smileO;
    public float presence;
    public float presenceO;

    public StalkingWraith(EntityType<? extends StalkingWraith> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(70, CREEP_DAMAGE, 0.3)
                .add(Attributes.FOLLOW_RANGE, SENSE_RANGE)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    public Phase phase()
    {
        return phase;
    }

    public int contact()
    {
        return entityData.get(DATA_CONTACT);
    }

    public boolean isRushing()
    {
        return entityData.get(DATA_RUSHING);
    }

    public boolean isRevealed()
    {
        return entityData.get(DATA_REVEALED);
    }

    public void setRevealed(boolean revealed)
    {
        entityData.set(DATA_REVEALED, revealed);
    }

    public void setContact(int contact)
    {
        entityData.set(DATA_CONTACT, contact);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_CONTACT, 0);
        builder.define(DATA_RUSHING, false);
        builder.define(DATA_REVEALED, false);
    }

    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(1, new CrossGoal());
        goalSelector.addGoal(2, new StalkGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        addIdleGoals(6);
        targetSelector.addGoal(1, new SenseThroughWallsGoal());
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.STALKING_WRAITH;
    }

    @Override
    protected boolean canStartCrossing()
    {
        return phase == Phase.STALK || phase == Phase.CREEP;
    }

    /**
     * Comes out of the wall behind its prey when there is one close enough, the only time it would rather not take the shortest way.
     */
    @Override
    protected @Nullable Route planRoute(LivingEntity target)
    {
        var eye = target.getEyePosition();
        var look = target.getViewVector(1);
        var behind = new Vec3(look.x, 0, look.z);
        if (behind.lengthSqr() > 1e-6)
        {
            var hit = level().clip(new ClipContext(eye, eye.subtract(behind.normalize().scale(BEHIND_REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS)
            {
                var route = routeThrough(hit, hit.getLocation());
                if (route != null)
                    return route;
            }
        }
        return super.planRoute(target);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (level().isClientSide())
        {
            smileO = smile;
            smile = Mth.approach(smile, contact() / (float) WraithStalk.CONTACT_TICKS, SMILE_PER_TICK);
            presenceO = presence;
            presence = isRevealed() ? Math.min(1, presence + REVEAL_PER_TICK) : Math.max(0, presence - FADE_PER_TICK);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);
        var target = getTarget();
        boolean attacking = phase == Phase.RUSH || phase == Phase.RECOVER;
        setRevealed(target != null && (attacking || distanceTo(target) <= REVEAL_DISTANCE));
    }

    /**
     * Quiet while it has someone to follow: the only sound it makes then is the slurping.
     */
    @Override
    public void playAmbientSound()
    {
        if (getTarget() == null)
            super.playAmbientSound();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && source.getEntity() instanceof Player && phase != Phase.RECOVER)
            provoked = true;
        return hurt;
    }

    @Override
    public float getSecondsToDisableBlocking()
    {
        return phase == Phase.RUSH ? SHIELD_DISABLE_SECONDS : super.getSecondsToDisableBlocking();
    }

    private boolean inEyeContact(LivingEntity target)
    {
        return isLookingAtMe(target, GAZE_CONE, false, false, getEyeY());
    }

    private void strike(LivingEntity target, float damage)
    {
        swingForAttack(InteractionHand.MAIN_HAND);
        playSound(VoidHeartSounds.STALKER_STRIKE, 1, 1);
        target.hurtServer((ServerLevel) level(), damageSources().source(DAMAGE, this), damage);
    }

    private final class StalkGoal extends Goal
    {
        private Vec3 rushHeading = Vec3.ZERO;
        private int rushTicks;
        private int recoverTicks;
        private int repathDelay;

        private StalkGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
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
            phase = Phase.STALK;
            repathDelay = 0;
        }

        @Override
        public void stop()
        {
            getNavigation().stop();
            phase = Phase.STALK;
            provoked = false;
            setContact(0);
            entityData.set(DATA_RUSHING, false);
        }

        @Override
        public void tick()
        {
            var target = getTarget();
            if (target == null)
                return;

            if (provoked && phase != Phase.RUSH)
            {
                provoked = false;
                startRush(target);
            }

            switch (phase)
            {
                case STALK -> stalk(target);
                case STARE -> stare(target);
                case RUSH -> rush(target);
                case RECOVER -> recover();
                case CREEP -> creep(target);
            }
        }

        private void stalk(LivingEntity target)
        {
            if (distanceTo(target) <= UNNOTICED_RANGE)
                unnoticedTicks++;

            if (inEyeContact(target))
            {
                phase = Phase.STARE;
                unnoticedTicks = 0;
                playSound(VoidHeartSounds.STALKER_SMILE, 0.8F, 1);
                return;
            }
            if (WraithStalk.creeps(unnoticedTicks))
            {
                phase = Phase.CREEP;
                return;
            }

            getLookControl().setLookAt(target, 30, 30);
            slurp(target);
            if (--repathDelay > 0)
                return;
            repathDelay = REPATH_TICKS;

            var feet = target.position();
            var look = target.getViewVector(1);
            if (WraithStalk.isHolding(distanceTo(target), WraithStalk.isBehind(feet, look, position())))
            {
                getNavigation().stop();
                return;
            }
            var spot = WraithStalk.spotBehind(feet, look);
            // Paths end on block centres, which can sit just outside the holding range, so the last stretch is walked straight
            if (position().distanceToSqr(spot) < FINE_APPROACH * FINE_APPROACH)
            {
                getNavigation().stop();
                getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, FOLLOW_SPEED);
                repathDelay = 1;
            }
            else
                getNavigation().moveTo(spot.x, spot.y, spot.z, FOLLOW_SPEED);
        }

        private void stare(LivingEntity target)
        {
            getNavigation().stop();
            getLookControl().setLookAt(target, 90, 90);
            faceDirectly(target.position().subtract(position()));

            boolean contact = inEyeContact(target);
            int next = WraithStalk.nextContact(contact(), contact);
            setContact(next);
            if (WraithStalk.rushes(next))
                startRush(target);
            else if (next == 0)
                phase = Phase.STALK;
        }

        private void startRush(LivingEntity target)
        {
            phase = Phase.RUSH;
            rushTicks = 0;
            setContact(WraithStalk.CONTACT_TICKS);
            entityData.set(DATA_RUSHING, true);
            getNavigation().stop();
            rushHeading = flatHeading(target);
            playSound(VoidHeartSounds.STALKER_RUSH, 1.4F, 1);
        }

        private void rush(LivingEntity target)
        {
            rushTicks++;
            rushHeading = rushHeading.lerp(flatHeading(target), RUSH_HOMING).normalize();
            faceDirectly(rushHeading);
            move(MoverType.SELF, rushHeading.scale(RUSH_SPEED));

            var level = (ServerLevel) level();
            var center = getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 3, 0.3, 0.6, 0.3, 0.01);

            if (getBoundingBox().inflate(RUSH_REACH).intersects(target.getBoundingBox()))
            {
                strike(target, RUSH_DAMAGE);
                endRush();
            }
            else if (rushTicks >= RUSH_MAX_TICKS || horizontalCollision)
                endRush();
        }

        private void endRush()
        {
            entityData.set(DATA_RUSHING, false);
            phase = Phase.RECOVER;
            recoverTicks = RECOVER_TICKS;
        }

        private void recover()
        {
            getNavigation().stop();
            setContact(WraithStalk.nextContact(contact(), false));
            if (--recoverTicks <= 0)
            {
                phase = Phase.STALK;
                unnoticedTicks = 0;
                setContact(0);
                repathDelay = 0;
            }
        }

        private void creep(LivingEntity target)
        {
            if (inEyeContact(target))
            {
                phase = Phase.STARE;
                unnoticedTicks = 0;
                playSound(VoidHeartSounds.STALKER_SMILE, 0.8F, 1);
                return;
            }

            getLookControl().setLookAt(target, 30, 30);
            slurp(target);
            if (--repathDelay <= 0)
            {
                repathDelay = REPATH_TICKS / 2;
                getNavigation().moveTo(target, CREEP_SPEED);
            }
            if (getBoundingBox().inflate(CREEP_REACH).intersects(target.getBoundingBox()))
            {
                strike(target, CREEP_DAMAGE);
                phase = Phase.RECOVER;
                recoverTicks = RECOVER_TICKS;
                getNavigation().stop();
            }
        }

        private void slurp(LivingEntity target)
        {
            if (--slurpDelay > 0 || distanceTo(target) > SLURP_RANGE)
                return;
            slurpDelay = SLURP_MIN_TICKS + random.nextInt(SLURP_SPREAD_TICKS);
            playSound(VoidHeartSounds.STALKER_SLURP, 1, 0.9F + random.nextFloat() * 0.15F);
        }

        private Vec3 flatHeading(LivingEntity target)
        {
            var toward = target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter());
            var flat = new Vec3(toward.x, 0, toward.z);
            return flat.lengthSqr() < 1e-6 ? getLookAngle() : flat.normalize();
        }

        private void faceDirectly(Vec3 heading)
        {
            float yaw = (float) (Mth.atan2(heading.z, heading.x) * Mth.RAD_TO_DEG) - 90;
            setYRot(yaw);
            setYHeadRot(yaw);
            yBodyRot = yaw;
        }
    }
}
