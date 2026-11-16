package net.voxelindustry.voidheart.common.energy.pearl;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.function.LongFunction;

/**
 * Which pearl a whole choir takes its tremor from. Pearls in reach of each other join a choir, and so do pearls reached through others: in a row
 * where each reaches only its neighbours, the two ends never count each other, yet all must shudder as one. So the choir's root is the lowest
 * position among every pearl linked to this one through their neighbours, the same whichever of them asks.
 */
public final class PearlChoir
{
    /**
     * Most pearls followed through, so a sprawling field of them stays cheap to walk.
     */
    public static final int MOST_FOLLOWED = 256;

    private PearlChoir()
    {
    }

    /**
     * @param self       this pearl's packed position
     * @param neighbours the packed positions of the pearls a pearl counts in reach, empty for one gone or not loaded
     * @return the lowest packed position among the pearls linked to this one, itself included
     */
    public static long root(long self, LongFunction<List<Long>> neighbours)
    {
        var lowest = self;
        var seen = new HashSet<Long>();
        var queue = new ArrayDeque<Long>();
        seen.add(self);
        queue.add(self);
        while (!queue.isEmpty() && seen.size() <= MOST_FOLLOWED)
        {
            var at = queue.poll();
            lowest = Math.min(lowest, at);
            for (var next : neighbours.apply(at))
            {
                if (seen.add(next))
                    queue.add(next);
            }
        }
        for (var pos : seen)
            lowest = Math.min(lowest, pos);
        return lowest;
    }
}
