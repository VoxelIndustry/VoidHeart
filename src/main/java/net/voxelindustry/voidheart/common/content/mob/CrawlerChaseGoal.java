package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Hunts a player over any surface. In sight it heads straight for them, climbing what is in the way and dropping from above.
 * Out of sight it paths along the floor, and only climbs blind where no path leads to them.
 */
final class CrawlerChaseGoal extends Goal
{
    private static final int PATH_INTERVAL = 10;
    private static final int LET_GO_AFTER_BLIND_TICKS = 80;
    private static final double DROP_RADIUS = 1.6;
    private static final double ABOVE_THRESHOLD = 2;
    private static final double LEAP_REACH = 8;
    private static final double LEAP_SPEED = 0.45;
    private static final double LEAP_LIFT = 0.15;
    private static final double SETTLE_DISTANCE = 1.2;

    private final CrawlerMonster mob;
    private int blindTicks;
    private int pathDelay;
    private int attackDelay;

    CrawlerChaseGoal(CrawlerMonster mob)
    {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean hasTarget()
    {
        var target = mob.getTarget();
        return target != null && target.isAlive() && mob.canChase() && mob.canAttack(target);
    }

    @Override
    public boolean canUse()
    {
        return hasTarget();
    }

    @Override
    public boolean canContinueToUse()
    {
        return hasTarget();
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void start()
    {
        mob.setAggressive(true);
        pathDelay = 0;
        blindTicks = 0;
    }

    @Override
    public void stop()
    {
        mob.setAggressive(false);
        mob.getNavigation().stop();
        mob.setCrawlWish(Vec3.ZERO, 1);
    }

    @Override
    public void tick()
    {
        var target = mob.getTarget();
        if (target == null)
            return;

        boolean sees = mob.getSensing().hasLineOfSight(target);
        blindTicks = sees ? 0 : blindTicks + 1;

        var toTarget = target.getBoundingBox().getCenter().subtract(mob.getBoundingBox().getCenter());
        if (!mob.canCrawl())
            mob.faceAlong(toTarget);
        else if (mob.isClinging())
            clingingTick(toTarget);
        else
            floorTick(target, toTarget, sees);

        attackIfInReach(target, sees);
    }

    private void clingingTick(Vec3 toTarget)
    {
        if (blindTicks > LET_GO_AFTER_BLIND_TICKS)
        {
            mob.letGo();
            return;
        }

        var surface = mob.surface();
        if (surface == Direction.DOWN)
        {
            if (toTarget.horizontalDistance() < DROP_RADIUS)
            {
                mob.letGo();
                return;
            }
        }
        else if (leapIfLevelled(toTarget, surface))
            return;

        mob.setCrawlWish(toTarget, 1);
    }

    /**
     * Held on a wall, nothing is left to gain once the player is straight out from it.
     */
    private boolean leapIfLevelled(Vec3 toTarget, Direction surface)
    {
        var normal = new Vec3(surface.getStepX(), surface.getStepY(), surface.getStepZ());
        double out = toTarget.dot(normal);
        double along = toTarget.subtract(normal.scale(out)).length();
        if (along > SETTLE_DISTANCE)
            return false;

        // They are on the far side of what it holds, the only way around is over the top
        if (out < 0)
        {
            mob.setCrawlWish(new Vec3(0, 1, 0), 1);
            return true;
        }

        mob.letGo();
        if (out < LEAP_REACH)
            mob.setDeltaMovement(normal.scale(LEAP_SPEED).add(0, LEAP_LIFT, 0));
        return true;
    }

    private void floorTick(LivingEntity target, Vec3 toTarget, boolean sees)
    {
        mob.getLookControl().setLookAt(target, 30, 30);

        var navigation = mob.getNavigation();
        if (--pathDelay <= 0)
        {
            pathDelay = PATH_INTERVAL + mob.getRandom().nextInt(5);
            navigation.moveTo(target, 1);
        }

        var path = navigation.getPath();
        boolean canWalkThere = path != null && path.canReach();
        if (!canWalkThere || sees && toTarget.y > ABOVE_THRESHOLD)
        {
            navigation.stop();
            mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1);
            climbIfBlocked(toTarget);
        }
    }

    private void climbIfBlocked(Vec3 toTarget)
    {
        if (!mob.horizontalCollision)
            return;

        var side = Direction.getApproximateNearest(toTarget.x, 0, toTarget.z);
        if (mob.touching(side))
            mob.setSurface(side.getOpposite());
    }

    private void attackIfInReach(LivingEntity target, boolean sees)
    {
        if (attackDelay > 0)
        {
            attackDelay--;
            return;
        }
        if (!sees || !mob.getBoundingBox().inflate(mob.attackReach()).intersects(target.getBoundingBox()))
            return;

        attackDelay = mob.attackCooldown();
        mob.swingForAttack(InteractionHand.MAIN_HAND);
        mob.doHurtTarget(CrawlerMonster.serverLevel(mob), target);
    }
}
