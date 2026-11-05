package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Walks like anything else in open rooms. When a wall stands between it and a player it marks the far side, then crosses straight through
 * and comes out of the mark. Hurting it once takes the ability away for good.
 */
public class Wraith extends VoidMonster
{
    public static final int STAIN_TICKS = 60;

    private static final EntityDataAccessor<Boolean> DATA_CROSSING = SynchedEntityData.defineId(Wraith.class, EntityDataSerializers.BOOLEAN);

    private static final int EXPERIENCE = 10;
    private static final double SENSE_RANGE = 40;
    private static final int BLIND_TICKS_BEFORE_CROSSING = 30;
    private static final int COOLDOWN_TICKS = 120;
    private static final int EMERGE_TICKS = 12;
    private static final double ROUTE_STEP = 0.25;
    private static final int HEADROOM_SEARCH = 2;
    private static final int RESCUE_RADIUS = 8;
    private static final float WITHER_CHANCE = 0.1F;
    private static final int WITHER_TICKS = 8 * 20;

    private boolean lockedSolid;
    private int blindTicks;
    private int cooldown;

    public Wraith(EntityType<? extends Wraith> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(30, 6, 0.27).add(Attributes.FOLLOW_RANGE, SENSE_RANGE);
    }

    public boolean isCrossing()
    {
        return entityData.get(DATA_CROSSING);
    }

    public boolean isLockedSolid()
    {
        return lockedSolid;
    }

    private void setCrossing(boolean crossing)
    {
        entityData.set(DATA_CROSSING, crossing);
        noPhysics = crossing;
        setNoGravity(crossing);
        fallDistance = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_CROSSING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
    {
        super.onSyncedDataUpdated(accessor);
        if (DATA_CROSSING.equals(accessor))
            noPhysics = entityData.get(DATA_CROSSING);
    }

    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(1, new CrossGoal());
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        addIdleGoals(6);
        targetSelector.addGoal(1, new SenseThroughWallsGoal());
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.WRAITH;
    }

    @Override
    public void travel(Vec3 input)
    {
        if (!isCrossing())
            super.travel(input);
    }

    @Override
    protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);

        if (cooldown > 0)
            cooldown--;
        var target = getTarget();
        blindTicks = target == null || getSensing().hasLineOfSight(target) ? 0 : blindTicks + 1;

        if (!isCrossing() && tickCount % 10 == 0 && !level.noCollision(this, getBoundingBox()))
            leaveWall(level);
    }

    /**
     * Saved halfway through a wall: a wraith that cannot cross any more must not stay stuck inside.
     */
    private void leaveWall(ServerLevel level)
    {
        for (var pos : BlockPos.withinManhattan(blockPosition(), RESCUE_RADIUS))
        {
            if (level.noCollision(this, aabbAt(Vec3.atBottomCenterOf(pos))))
            {
                setPos(Vec3.atBottomCenterOf(pos));
                return;
            }
        }
    }

    private AABB aabbAt(Vec3 position)
    {
        return getDimensions(getPose()).makeBoundingBox(position);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        if (isCrossing() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
            return false;

        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && source.getEntity() instanceof Player)
            lockedSolid = true;
        return hurt;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source)
    {
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean isPickable()
    {
        return !isCrossing() && super.isPickable();
    }

    @Override
    public boolean canBeHitByProjectile()
    {
        return !isCrossing() && super.canBeHitByProjectile();
    }

    @Override
    public boolean isPushable()
    {
        return !isCrossing() && super.isPushable();
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target)
    {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living && random.nextFloat() < WITHER_CHANCE)
            living.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS), this);
        return hit;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        output.putBoolean("LockedSolid", lockedSolid);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        lockedSolid = input.getBooleanOr("LockedSolid", false);
    }

    private final class SenseThroughWallsGoal extends NearestAttackableTargetGoal<Player>
    {
        private SenseThroughWallsGoal()
        {
            super(Wraith.this, Player.class, false);
            targetConditions.ignoreLineOfSight();
        }
    }

    private record Route(BlockPos block, Direction face, Vec3 emergeAt)
    {
    }

    private @Nullable Route planRoute(LivingEntity target)
    {
        var from = getEyePosition();
        var to = target.getEyePosition();
        if (from.distanceTo(to) > SENSE_RANGE)
            return null;

        var hit = level().clip(new ClipContext(to, from, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.MISS || !canCrossBetween(from, to))
            return null;

        var emergeAt = emergeSpot(hit);
        return emergeAt == null ? null : new Route(hit.getBlockPos(), hit.getDirection(), emergeAt);
    }

    private boolean canCrossBetween(Vec3 from, Vec3 to)
    {
        var level = level();
        double length = from.distanceTo(to);
        var step = to.subtract(from).normalize().scale(ROUTE_STEP);
        var pos = new BlockPos.MutableBlockPos();
        var at = from;
        for (double travelled = 0; travelled < length; travelled += ROUTE_STEP)
        {
            pos.set(at.x, at.y, at.z);
            var state = level.getBlockState(pos);
            if (state.is(VoidHeartBlocks.POCKET_WALL) || state.getDestroySpeed(level, pos) < 0)
                return false;
            at = at.add(step);
        }
        return true;
    }

    private @Nullable Vec3 emergeSpot(BlockHitResult hit)
    {
        var free = hit.getBlockPos().relative(hit.getDirection());
        for (int down = 0; down <= HEADROOM_SEARCH; down++)
        {
            var spot = Vec3.atBottomCenterOf(free.below(down));
            if (level().noCollision(this, aabbAt(spot)))
                return spot;
        }
        return null;
    }

    private final class CrossGoal extends Goal
    {
        private @Nullable Route route;
        private Vec3 start = Vec3.ZERO;
        private int ticks;

        private CrossGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse()
        {
            var target = getTarget();
            if (lockedSolid || target == null || cooldown > 0 || blindTicks < BLIND_TICKS_BEFORE_CROSSING)
                return false;

            route = planRoute(target);
            return route != null;
        }

        @Override
        public boolean canContinueToUse()
        {
            return ticks < STAIN_TICKS + EMERGE_TICKS;
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void start()
        {
            getNavigation().stop();
            start = position();
            ticks = 0;
            setDeltaMovement(Vec3.ZERO);
            setCrossing(true);
            WraithStain.on((ServerLevel) level(), route.block(), route.face());
        }

        @Override
        public void tick()
        {
            ticks++;
            var inside = Vec3.atBottomCenterOf(route.block());
            Vec3 at;
            if (ticks <= STAIN_TICKS)
                at = start.lerp(inside, Mth.clamp(ticks / (float) STAIN_TICKS, 0, 1));
            else
                at = inside.lerp(route.emergeAt(), (ticks - STAIN_TICKS) / (float) EMERGE_TICKS);

            var heading = at.subtract(position());
            if (heading.horizontalDistanceSqr() > 1e-4)
            {
                float yaw = (float) (Mth.atan2(heading.z, heading.x) * Mth.RAD_TO_DEG) - 90;
                setYRot(yaw);
                setYHeadRot(yaw);
                yBodyRot = yaw;
            }
            setPos(at);

            if (ticks == STAIN_TICKS)
            {
                var level = (ServerLevel) level();
                playSound(VoidHeartSounds.WRAITH_EMERGE, 1, 1);
                var center = route.emergeAt().add(0, getBbHeight() / 2, 0);
                level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 16, 0.4, 0.7, 0.4, 0.02);
            }
        }

        @Override
        public void stop()
        {
            setPos(route.emergeAt());
            setCrossing(false);
            cooldown = COOLDOWN_TICKS;
            blindTicks = 0;
        }
    }
}
