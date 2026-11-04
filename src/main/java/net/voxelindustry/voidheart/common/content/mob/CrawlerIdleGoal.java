package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * On a wall or ceiling with nothing to chase, it mostly waits and sometimes shifts a little.
 */
final class CrawlerIdleGoal extends Goal
{
    private static final int START_ONE_IN = 120;
    private static final int MIN_TICKS = 15;
    private static final int EXTRA_TICKS = 30;
    private static final double PACE = 0.5;

    private final CrawlerMonster mob;
    private int ticks;

    CrawlerIdleGoal(CrawlerMonster mob)
    {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        return mob.isClinging() && mob.canCrawl() && mob.getTarget() == null && mob.getRandom().nextInt(START_ONE_IN) == 0;
    }

    @Override
    public boolean canContinueToUse()
    {
        return ticks > 0 && mob.isClinging() && mob.getTarget() == null;
    }

    @Override
    public void start()
    {
        var random = mob.getRandom();
        ticks = MIN_TICKS + random.nextInt(EXTRA_TICKS);
        mob.setCrawlWish(new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5), PACE);
    }

    @Override
    public void tick()
    {
        ticks--;
    }

    @Override
    public void stop()
    {
        mob.setCrawlWish(Vec3.ZERO, 1);
    }
}
