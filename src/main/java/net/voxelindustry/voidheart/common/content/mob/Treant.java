package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;

import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * A tall creature of wood that waits inside a trunk until a player comes close, then pulls itself out of the bark over a few seconds. It is slow and rooted,
 * but each wide sweep of its long claws reaches far and throws whatever it catches.
 */
public class Treant extends VoidMonster
{
    public static final int EMERGE_TICKS = TreantMoves.EMERGE_TICKS;
    public static final int SWEEP_WINDUP_TICKS = TreantMoves.SWEEP_WINDUP_TICKS;
    public static final int FOLLOW_THROUGH_TICKS = TreantMoves.FOLLOW_THROUGH_TICKS;
    public static final float SWEEP_DAMAGE = 17;

    private static final EntityDataAccessor<Integer> DATA_EMERGE = SynchedEntityData.defineId(Treant.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SWEEP = SynchedEntityData.defineId(Treant.class, EntityDataSerializers.INT);

    private static final int EXPERIENCE = 30;
    private static final double SWEEP_POWER = 2.4;
    private static final double SWEEP_LIFT = 0.45;
    private static final double SWEEP_START_RANGE = 3.6;
    private static final int REPATH_TICKS = 10;
    private static final int SWEEP_PARTICLES = 5;
    private static final double SWEEP_PARTICLE_REACH = 3;
    private static final int BARK_PARTICLE_INTERVAL = 2;
    private static final int BARK_PARTICLES = 5;
    // Past the trunk's face, whatever way it is coming out
    private static final double LIGHT_PROBE_AHEAD = 1.3;

    private Vec3 emergeFrom = Vec3.ZERO;
    private float emergeYaw;

    public Treant(EntityType<? extends Treant> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(90, SWEEP_DAMAGE, 0.2)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1)
                .add(Attributes.STEP_HEIGHT, 1);
    }

    /**
     * @return ticks since it began to come out of its trunk, or 0 once it stands free
     */
    public int emergeTicks()
    {
        return entityData.get(DATA_EMERGE);
    }

    public boolean isEmerging()
    {
        return emergeTicks() > 0;
    }

    public int sweepTicks()
    {
        return entityData.get(DATA_SWEEP);
    }

    /**
     * Starts it inside the trunk it hid in, its feet on the trunk's base, to come out on the given heading, at whoever woke it if anyone did.
     */
    public void emergeFrom(Vec3 trunkBase, float yaw, @Nullable LivingEntity target)
    {
        emergeFrom = trunkBase;
        emergeYaw = yaw;
        snapTo(trunkBase.x, trunkBase.y, trunkBase.z, yaw, 0);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        setEmerge(1);
        setTarget(target);
    }

    private void setEmerge(int ticks)
    {
        entityData.set(DATA_EMERGE, ticks);
        noPhysics = ticks > 0;
        setNoGravity(ticks > 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_EMERGE, 0);
        builder.define(DATA_SWEEP, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
    {
        super.onSyncedDataUpdated(accessor);
        if (DATA_EMERGE.equals(accessor))
            noPhysics = isEmerging();
    }

    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(0, new EmergeGoal());
        goalSelector.addGoal(3, new FightGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        addIdleGoals(6);
        addPlayerTarget(true);
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.TREANT;
    }

    @Override
    public void aiStep()
    {
        if (!level().isClientSide() && isEmerging())
            tickEmerge((ServerLevel) level());
        super.aiStep();
    }

    private void tickEmerge(ServerLevel level)
    {
        int ticks = emergeTicks();
        if (ticks == 1)
            playSound(VoidHeartSounds.TREANT_EMERGE, 1.6F, 1);
        if (ticks == TreantMoves.EMERGE_STIR_TICKS || ticks == EMERGE_TICKS * 2 / 3)
            playSound(VoidHeartSounds.TREANT_TEAR, 1.4F, 0.9F + random.nextFloat() * 0.2F);

        var heading = Vec3.directionFromRotation(0, emergeYaw);
        var at = emergeFrom.add(heading.scale(TreantMoves.EMERGE_DISTANCE * TreantMoves.emergeProgress(ticks)));
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
        setYRot(emergeYaw);
        setYBodyRot(emergeYaw);
        setYHeadRot(emergeYaw);

        if (ticks % BARK_PARTICLE_INTERVAL == 0)
            shedBark(level, heading);

        if (ticks >= EMERGE_TICKS)
        {
            setEmerge(0);
            playSound(sounds().ambient(), 2, 0.8F);
        }
        else
            setEmerge(ticks + 1);
    }

    /**
     * Chips of bark fall where its body parts from the trunk, the face it comes out of.
     */
    private void shedBark(ServerLevel level, Vec3 heading)
    {
        var bark = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DARK_OAK_LOG.defaultBlockState());
        var face = emergeFrom.add(heading.scale(0.55));
        for (int i = 0; i < BARK_PARTICLES; i++)
        {
            double side = (random.nextDouble() - 0.5) * getBbWidth();
            double x = face.x - heading.z * side;
            double z = face.z + heading.x * side;
            level.sendParticles(bark, x, face.y + random.nextDouble() * getBbHeight(), z, 1, 0.05, 0.05, 0.05, 0.05);
        }
    }

    private void sweep(ServerLevel level)
    {
        var look = Vec3.directionFromRotation(0, getYRot());
        playSound(VoidHeartSounds.TREANT_SWEEP, 1.6F, 1);

        var side = new Vec3(-look.z, 0, look.x);
        for (int i = 0; i < SWEEP_PARTICLES; i++)
        {
            double spread = (i - (SWEEP_PARTICLES - 1) / 2.0) / ((SWEEP_PARTICLES - 1) / 2.0);
            var at = position().add(0, getBbHeight() * 0.4, 0)
                    .add(look.scale(SWEEP_PARTICLE_REACH * (1 - Math.abs(spread) * 0.5)))
                    .add(side.scale(spread * SWEEP_PARTICLE_REACH));
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }

        var area = new AABB(position(), position()).inflate(TreantMoves.SWEEP_RANGE, getBbHeight(), TreantMoves.SWEEP_RANGE);
        for (var player : level.getEntitiesOfClass(Player.class, area, EntitySelector.NO_CREATIVE_OR_SPECTATOR))
        {
            if (TreantMoves.inSweep(getX(), getZ(), getYRot(), player.getX(), player.getZ()) && player.hurtServer(level, damageSources().mobAttack(this), SWEEP_DAMAGE))
            {
                player.knockback(SWEEP_POWER, getX() - player.getX(), getZ() - player.getZ(), damageSources().mobAttack(this), SWEEP_DAMAGE);
                player.push(0, SWEEP_LIFT, 0);
                player.needsSync = true;
            }
        }
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source)
    {
        // It lives in wood, and its broad frame can brush a neighbouring trunk
        return source.is(DamageTypes.IN_WALL) || isEmerging() && source.is(DamageTypeTags.IS_FALL) || super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean isPushable()
    {
        return !isEmerging() && super.isPushable();
    }

    @Override
    public Vec3 getLightProbePosition(float partialTicks)
    {
        // Inside the trunk it would take the wood's darkness and come out black, so it takes the light of the air it is coming out into
        var probe = super.getLightProbePosition(partialTicks);
        return isEmerging() ? probe.add(Vec3.directionFromRotation(0, getYRot()).scale(LIGHT_PROBE_AHEAD)) : probe;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        if (!isEmerging())
            return;
        output.putInt("Emerge", emergeTicks());
        output.putDouble("EmergeFromX", emergeFrom.x);
        output.putDouble("EmergeFromY", emergeFrom.y);
        output.putDouble("EmergeFromZ", emergeFrom.z);
        output.putFloat("EmergeYaw", emergeYaw);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        int emerge = Mth.clamp(input.getIntOr("Emerge", 0), 0, EMERGE_TICKS);
        emergeFrom = new Vec3(input.getDoubleOr("EmergeFromX", getX()), input.getDoubleOr("EmergeFromY", getY()), input.getDoubleOr("EmergeFromZ", getZ()));
        emergeYaw = input.getFloatOr("EmergeYaw", getYRot());
        // A Treant saved far from where it was coming out of has been moved since, and stands free
        setEmerge(emergeFrom.distanceTo(position()) <= TreantMoves.EMERGE_DISTANCE + 0.5 ? emerge : 0);
    }

    /**
     * Holds every other goal back while it comes out of the trunk.
     */
    private final class EmergeGoal extends Goal
    {
        private EmergeGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse()
        {
            return isEmerging();
        }

        @Override
        public void start()
        {
            getNavigation().stop();
        }
    }

    private final class FightGoal extends Goal
    {
        private int repathDelay;
        private int recover;

        private FightGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            var target = getTarget();
            return target != null && target.isAlive() && !isEmerging();
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void start()
        {
            setAggressive(true);
            repathDelay = 0;
        }

        @Override
        public void stop()
        {
            setAggressive(false);
            getNavigation().stop();
            entityData.set(DATA_SWEEP, 0);
        }

        @Override
        public void tick()
        {
            var target = getTarget();
            if (target == null)
                return;

            if (sweepTicks() > 0)
            {
                tickSweep((ServerLevel) level(), target);
                return;
            }

            getLookControl().setLookAt(target, 20, 30);
            if (recover > 0)
            {
                recover--;
                return;
            }

            if (distanceTo(target) <= SWEEP_START_RANGE && getSensing().hasLineOfSight(target))
            {
                getNavigation().stop();
                entityData.set(DATA_SWEEP, 1);
                playSound(VoidHeartSounds.TREANT_WINDUP, 1.4F, 1);
                return;
            }

            if (--repathDelay <= 0)
            {
                repathDelay = REPATH_TICKS;
                getNavigation().moveTo(target, 1);
            }
        }

        /**
         * It keeps turning toward its target while it rears back, slower than a player can circle it, then commits to the sweep.
         */
        private void tickSweep(ServerLevel level, LivingEntity target)
        {
            int ticks = sweepTicks() + 1;
            if (ticks < SWEEP_WINDUP_TICKS)
                getLookControl().setLookAt(target, 6, 30);
            if (ticks == SWEEP_WINDUP_TICKS)
                sweep(level);
            if (ticks >= SWEEP_WINDUP_TICKS + FOLLOW_THROUGH_TICKS)
            {
                ticks = 0;
                recover = TreantMoves.RECOVER_TICKS;
            }
            entityData.set(DATA_SWEEP, ticks);
        }
    }
}
