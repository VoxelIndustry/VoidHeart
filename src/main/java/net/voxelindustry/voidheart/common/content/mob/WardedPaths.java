package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.voxelindustry.voidheart.common.energy.ley.LeyWards;

import java.util.List;

/**
 * Paths that go around the reach of every Ley Ward keeping the mob's kind out, as wraiths go around salt. The wards are gathered once per search, since a
 * node's type is asked thousands of times in one. Wards the mob already stands in are left out, so it can always walk out of one.
 */
final class WardedPaths
{
    private WardedPaths()
    {
    }

    static GroundPathNavigation ground(Mob mob, Level level)
    {
        return new GroundPathNavigation(mob, level)
        {
            @Override
            protected PathFinder createPathFinder(int maxVisitedNodes)
            {
                nodeEvaluator = new Walk();
                return new PathFinder(nodeEvaluator, maxVisitedNodes);
            }
        };
    }

    static FlyingPathNavigation flying(Mob mob, Level level)
    {
        return new FlyingPathNavigation(mob, level)
        {
            @Override
            protected PathFinder createPathFinder(int maxVisitedNodes)
            {
                nodeEvaluator = new Fly();
                return new PathFinder(nodeEvaluator, maxVisitedNodes);
            }
        };
    }

    private static boolean kept(List<LeyWards.Keep> keeps, int x, int y, int z)
    {
        for (var keep : keeps)
        {
            if (keep.contains(x + 0.5, y + 0.5, z + 0.5))
                return true;
        }
        return false;
    }

    private static final class Walk extends WalkNodeEvaluator
    {
        private List<LeyWards.Keep> keeps = List.of();

        @Override
        public void prepare(PathNavigationRegion region, Mob mob)
        {
            super.prepare(region, mob);
            keeps = LeyWards.keepingOut(mob);
        }

        @Override
        public void done()
        {
            super.done();
            keeps = List.of();
        }

        @Override
        public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob)
        {
            if (!keeps.isEmpty() && kept(keeps, x, y, z))
                return PathType.BLOCKED;
            return super.getPathTypeOfMob(context, x, y, z, mob);
        }
    }

    private static final class Fly extends FlyNodeEvaluator
    {
        private List<LeyWards.Keep> keeps = List.of();

        @Override
        public void prepare(PathNavigationRegion region, Mob mob)
        {
            super.prepare(region, mob);
            keeps = LeyWards.keepingOut(mob);
        }

        @Override
        public void done()
        {
            super.done();
            keeps = List.of();
        }

        @Override
        public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob)
        {
            if (!keeps.isEmpty() && kept(keeps, x, y, z))
                return PathType.BLOCKED;
            return super.getPathTypeOfMob(context, x, y, z, mob);
        }
    }
}
