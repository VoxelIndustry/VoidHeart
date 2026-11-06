package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A mob that holds on to any face of a block. On the floor it walks and paths like anything else. On a wall or ceiling it steers
 * directly in the plane of that face, turning onto whatever blocks it and around the edges it runs past.
 * <p>
 * The collision box cannot turn with it, so it stays axis aligned and only the model is rotated onto the surface.
 */
public abstract class CrawlerMonster extends VoidMonster
{
    public static final int SURFACE_BLEND_TICKS = 5;

    private static final EntityDataAccessor<Direction> DATA_SURFACE = SynchedEntityData.defineId(CrawlerMonster.class, EntityDataSerializers.DIRECTION);

    private static final double HOLD_SPEED = 0.05;
    private static final double TOUCH_PROBE = 0.12;
    private static final double WRAP_PROBE = 0.6;
    private static final double GROUND_SPEED_SCALE = 2.16;
    private static final double BLOCKED_EPSILON = 1e-3;
    private static final double TURN_SHARE = 0.35;
    private static final float STRIDE_PER_BLOCK = 7;
    private static final float STRIDE_AMOUNT_SCALE = 8;

    private Vec3 crawlWish = Vec3.ZERO;
    private double crawlPace = 1;

    public Direction previousSurface = Direction.UP;
    public int surfaceBlend = SURFACE_BLEND_TICKS;
    private Direction shownSurface = Direction.UP;
    public float stride;
    public float strideO;
    public float strideAmount;

    protected CrawlerMonster(EntityType<? extends CrawlerMonster> type, Level level)
    {
        super(type, level);
    }

    public Direction surface()
    {
        return entityData.get(DATA_SURFACE);
    }

    public boolean isClinging()
    {
        return surface() != Direction.UP;
    }

    public void setSurface(Direction surface)
    {
        entityData.set(DATA_SURFACE, surface);
        resetFallDistance();
    }

    public boolean canCrawl()
    {
        return true;
    }

    public boolean canChase()
    {
        return true;
    }

    public double attackReach()
    {
        return 0.6;
    }

    public int attackCooldown()
    {
        return 20;
    }

    /**
     * The direction to crawl in while on a wall or ceiling, kept until replaced.
     */
    public void setCrawlWish(Vec3 wish, double pace)
    {
        crawlWish = wish;
        crawlPace = pace;
    }

    /**
     * Places it snug against the far side of a free cell, holding the face of the block behind it.
     */
    public void settleOn(BlockPos cell, Direction surface)
    {
        double gap = getBbWidth() / 2 + 0.01;
        double x = cell.getX() + 0.5;
        double y = cell.getY();
        double z = cell.getZ() + 0.5;
        switch (surface)
        {
            case DOWN -> y = cell.getY() + 1 - getBbHeight() - 0.01;
            case EAST -> x = cell.getX() + gap;
            case WEST -> x = cell.getX() + 1 - gap;
            case SOUTH -> z = cell.getZ() + gap;
            case NORTH -> z = cell.getZ() + 1 - gap;
            case UP ->
            {
            }
        }
        setPos(x, y, z);
        setSurface(surface);
    }

    public void letGo()
    {
        crawlWish = Vec3.ZERO;
        setSurface(Direction.UP);
    }

    /**
     * Whether a solid face lies right next to the collision box on that side.
     */
    public boolean touching(Direction side)
    {
        return !level().noCollision(this, getBoundingBox().move(side.getStepX() * TOUCH_PROBE, side.getStepY() * TOUCH_PROBE, side.getStepZ() * TOUCH_PROBE));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_SURFACE, Direction.UP);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
    {
        super.onSyncedDataUpdated(accessor);
        if (DATA_SURFACE.equals(accessor))
        {
            // A mob only just tracked is already on its surface, rather than turning onto it
            boolean tracked = tickCount > 0;
            previousSurface = tracked ? shownSurface : surface();
            shownSurface = surface();
            surfaceBlend = tracked ? 0 : SURFACE_BLEND_TICKS;
        }
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!level().isClientSide())
            return;

        if (surfaceBlend < SURFACE_BLEND_TICKS)
            surfaceBlend++;

        // The usual walk animation ignores climbing, so legs are driven by any distance covered
        float moved = (float) position().distanceTo(new Vec3(xo, yo, zo));
        strideO = stride;
        stride += moved * STRIDE_PER_BLOCK;
        strideAmount += (Math.min(moved * STRIDE_AMOUNT_SCALE, 1) - strideAmount) * 0.3F;
    }

    @Override
    protected boolean isHeldInPlace()
    {
        return isClinging();
    }

    @Override
    public void travel(Vec3 input)
    {
        if (isClinging())
            crawl();
        else
            super.travel(input);
    }

    private void crawl()
    {
        var surface = surface();
        var normal = new Vec3(surface.getStepX(), surface.getStepY(), surface.getStepZ());
        var tangent = crawlWish.subtract(normal.scale(crawlWish.dot(normal)));
        var step = canCrawl() && tangent.lengthSqr() > 1e-4 ? tangent.normalize().scale(crawlSpeed() * crawlPace) : Vec3.ZERO;
        var motion = step.subtract(normal.scale(HOLD_SPEED));

        var before = position();
        move(MoverType.SELF, motion);
        setDeltaMovement(Vec3.ZERO);
        resetFallDistance();

        if (step != Vec3.ZERO)
            faceAlong(step);

        var blocked = blockedSide(step, position().subtract(before));
        if (blocked != null)
            setSurface(blocked.getOpposite());
        else if (!touching(surface.getOpposite()))
            wrapOrLetGo(normal, step);
    }

    private double crawlSpeed()
    {
        double speed = getAttributeValue(Attributes.MOVEMENT_SPEED);
        return speed * speed * GROUND_SPEED_SCALE;
    }

    private static Direction blockedSide(Vec3 step, Vec3 moved)
    {
        double length = step.length();
        if (length == 0)
            return null;

        if (isBlocked(step.x, moved.x, length))
            return step.x > 0 ? Direction.EAST : Direction.WEST;
        if (isBlocked(step.y, moved.y, length))
            return step.y > 0 ? Direction.UP : Direction.DOWN;
        if (isBlocked(step.z, moved.z, length))
            return step.z > 0 ? Direction.SOUTH : Direction.NORTH;
        return null;
    }

    private static boolean isBlocked(double wanted, double moved, double length)
    {
        return Math.abs(wanted) > length * TURN_SHARE && Math.abs(moved - wanted) > BLOCKED_EPSILON;
    }

    private void wrapOrLetGo(Vec3 normal, Vec3 step)
    {
        if (step == Vec3.ZERO)
        {
            letGo();
            return;
        }

        var along = Direction.getApproximateNearest(step.x, step.y, step.z);
        double extent = (normal.y != 0 ? getBbHeight() : getBbWidth()) + 0.05;
        var inward = normal.scale(-extent);
        var box = getBoundingBox().move(inward);
        var back = box.move(along.getStepX() * -WRAP_PROBE, along.getStepY() * -WRAP_PROBE, along.getStepZ() * -WRAP_PROBE);
        if (level().noCollision(this, box) && !level().noCollision(this, back))
        {
            setPos(position().add(inward));
            setSurface(along);
        }
        else
            letGo();
    }

    /**
     * Turns the body toward a direction given in the world, expressed in the plane of the surface it holds.
     */
    public void faceAlong(Vec3 direction)
    {
        var local = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
        new Quaternionf(surface().getRotation()).conjugate().transform(local);
        float yaw = (float) (Mth.atan2(-local.x, local.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(local.y, Mth.sqrt(local.x * local.x + local.z * local.z)) * Mth.RAD_TO_DEG);
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
        setXRot(pitch);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source)
    {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        output.store("Surface", Direction.CODEC, surface());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        setSurface(input.read("Surface", Direction.CODEC).orElse(Direction.UP));
    }

    protected final class StrollGoal extends WaterAvoidingRandomStrollGoal
    {
        public StrollGoal(double speed)
        {
            super(CrawlerMonster.this, speed);
        }

        @Override
        public boolean canUse()
        {
            return !isClinging() && canCrawl() && super.canUse();
        }

        @Override
        public boolean canContinueToUse()
        {
            return !isClinging() && canCrawl() && super.canContinueToUse();
        }
    }

    protected static ServerLevel serverLevel(CrawlerMonster mob)
    {
        return (ServerLevel) mob.level();
    }
}
