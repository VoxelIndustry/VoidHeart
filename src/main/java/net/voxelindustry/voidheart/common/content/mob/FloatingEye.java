package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * An eye adrift in the air. It flies by steering its velocity rather than walking through the usual controls,
 * and only uses a path to get around what stands between it and its prey.
 */
public abstract class FloatingEye extends VoidMonster
{
    private static final EntityDataAccessor<Boolean> DATA_JAWS_OPEN = SynchedEntityData.defineId(FloatingEye.class, EntityDataSerializers.BOOLEAN);

    private static final float JAWS_PER_TICK = 0.14F;
    private static final float TURN_DEGREES = 18;
    private static final double WALL_WEIGHT = 2.4;
    private static final double WALL_AHEAD_SHARE = 1.5;
    private static final int WALL_SCAN_INTERVAL = 2;
    private static final int PATH_INTERVAL = 20;
    private static final float PATH_SEARCH_MULTIPLIER = 10;
    private static final int GIVE_UP_TICKS = 20 * 20;
    private static final int AMBIENT_INTERVAL = 160;
    private static final int MIN_BLINK_DELAY = 60;
    private static final int BLINK_DELAY_SPREAD = 120;
    private static final int BLINK_TICKS = 4;
    private static final float WANDER_TURN = 0.25F;
    private static final float MAX_WANDER_PITCH = 0.45F;

    private Vec3 wallPush = Vec3.ZERO;
    private float wanderYaw;
    private float wanderPitch;
    private int unseenTicks;

    public float jaws;
    public float jawsO;
    public int blinkTicks;
    private int nextBlink = MIN_BLINK_DELAY;

    protected FloatingEye(EntityType<? extends FloatingEye> type, Level level)
    {
        super(type, level);
        setNoGravity(true);
        moveControl = new WaypointControl(this);
        lookControl = new FixedLookControl(this);
        wanderYaw = random.nextFloat() * Mth.TWO_PI;
    }

    public static AttributeSupplier.Builder eyeAttributes(double health, double damage, double speed)
    {
        return attributes(health, damage, speed).add(Attributes.FLYING_SPEED, speed);
    }

    @Override
    protected PathNavigation createNavigation(Level level)
    {
        var navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setMaxVisitedNodesMultiplier(PATH_SEARCH_MULTIPLIER);
        return navigation;
    }

    public boolean jawsOpen()
    {
        return entityData.get(DATA_JAWS_OPEN);
    }

    public void setJawsOpen(boolean open)
    {
        entityData.set(DATA_JAWS_OPEN, open);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_JAWS_OPEN, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
    {
        super.onSyncedDataUpdated(accessor);
        if (DATA_JAWS_OPEN.equals(accessor) && tickCount == 0)
        {
            jaws = jawsOpen() ? 1 : 0;
            jawsO = jaws;
        }
    }

    @Override
    public void tick()
    {
        if (isNoAi())
            setDeltaMovement(Vec3.ZERO);
        super.tick();

        if (level().isClientSide())
            animate();
    }

    private void animate()
    {
        jawsO = jaws;
        jaws = Mth.approach(jaws, jawsOpen() ? 1 : 0, JAWS_PER_TICK);

        if (blinkTicks > 0)
            blinkTicks--;
        else if (--nextBlink <= 0)
        {
            blinkTicks = BLINK_TICKS;
            nextBlink = MIN_BLINK_DELAY + random.nextInt(BLINK_DELAY_SPREAD);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);

        var target = getTarget();
        unseenTicks = target != null && !getSensing().hasLineOfSight(target) ? unseenTicks + 1 : 0;
    }

    public boolean seesPrey()
    {
        return getTarget() != null && unseenTicks == 0;
    }

    @Override
    public void travel(Vec3 input)
    {
        move(MoverType.SELF, getDeltaMovement());
    }

    protected void steer(Vec3 wanted, double turnRate)
    {
        setDeltaMovement(getDeltaMovement().lerp(wanted, turnRate));
    }

    protected double flightSpeed()
    {
        return getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    /**
     * Which way to fly so as not to run into what is around it, weighted to overrule anything but a dash.
     */
    protected Vec3 wallPush(double reach)
    {
        if ((tickCount + getId()) % WALL_SCAN_INTERVAL != 0)
            return wallPush;

        var box = getBoundingBox();
        var push = Vec3.ZERO;
        for (var side : Direction.values())
        {
            if (!level().noBlockCollision(this, box.move(side.getStepX() * reach, side.getStepY() * reach, side.getStepZ() * reach)))
                push = push.subtract(side.getStepX(), side.getStepY(), side.getStepZ());
        }

        var motion = getDeltaMovement();
        if (motion.lengthSqr() > 1e-4)
        {
            var ahead = motion.normalize();
            var far = ahead.scale(reach * WALL_AHEAD_SHARE);
            if (!level().noBlockCollision(this, box.move(far.x, far.y, far.z)))
                push = push.subtract(ahead);
        }
        wallPush = push.scale(WALL_WEIGHT);
        return wallPush;
    }

    /**
     * Which way to go to reach a point near the target: straight at it while the target is in sight, along a path when not.
     */
    protected Vec3 headingTo(LivingEntity target, Vec3 point)
    {
        if (getSensing().hasLineOfSight(target))
        {
            getNavigation().stop();
            return point.subtract(position()).normalize();
        }

        if ((tickCount + getId()) % PATH_INTERVAL == 0)
        {
            var path = getNavigation().getPath();
            if (path != null && !path.canReach())
                getNavigation().stop();
            getNavigation().moveTo(target, 1);
        }
        if (getNavigation().isInProgress() && moveControl.hasWanted())
            return new Vec3(moveControl.getWantedX(), moveControl.getWantedY(), moveControl.getWantedZ()).subtract(position()).normalize();
        return point.subtract(position()).normalize();
    }

    /**
     * A slow meander with no destination, for when there is nothing to hunt.
     */
    protected Vec3 wander()
    {
        wanderYaw += (random.nextFloat() - 0.5F) * WANDER_TURN;
        wanderPitch = Mth.clamp(wanderPitch + (random.nextFloat() - 0.5F) * WANDER_TURN, -MAX_WANDER_PITCH, MAX_WANDER_PITCH);
        return new Vec3(Mth.cos(wanderYaw) * Mth.cos(wanderPitch), Mth.sin(wanderPitch), Mth.sin(wanderYaw) * Mth.cos(wanderPitch));
    }

    protected void faceAlong(Vec3 direction)
    {
        if (direction.lengthSqr() < 1e-4)
            return;

        float yaw = (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(direction.y, direction.horizontalDistance()) * Mth.RAD_TO_DEG);
        setYRot(Mth.approachDegrees(getYRot(), yaw, TURN_DEGREES));
        setXRot(Mth.approachDegrees(getXRot(), pitch, TURN_DEGREES));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    protected void faceTarget(LivingEntity target)
    {
        faceAlong(target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()));
    }

    protected boolean stillHunted(LivingEntity target)
    {
        return target.isAlive() && canAttack(target) && unseenTicks <= GIVE_UP_TICKS && distanceTo(target) <= getAttributeValue(Attributes.FOLLOW_RANGE) * 1.5;
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return AMBIENT_INTERVAL;
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState block, BlockPos pos)
    {
    }

    @Override
    public boolean onClimbable()
    {
        return false;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block)
    {
    }

    /**
     * Leaves where it looks alone, as the usual control resets the pitch of a head that an eye turns by itself.
     */
    private static final class FixedLookControl extends LookControl
    {
        private FixedLookControl(Mob mob)
        {
            super(mob);
        }

        @Override
        public void tick()
        {
        }
    }

    /**
     * Only holds where the path points to next, as flying is done by steering.
     */
    private static final class WaypointControl extends MoveControl<Mob>
    {
        private WaypointControl(Mob mob)
        {
            super(mob);
        }

        @Override
        public void tick()
        {
        }
    }
}
