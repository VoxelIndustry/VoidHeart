package net.voxelindustry.voidheart.common.energy.pearl;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * Which pearl a whole choir takes its tremor from. Pearls in reach of each other join a choir, and so do pearls reached through others: in a row
 * where each reaches only its neighbours, the two ends never count each other, yet all must shudder as one. So the choir's root is the lowest
 * position among every pearl linked to this one through their neighbours, the same whichever of them walks it. The walk always covers the whole
 * choir, since stopping short would leave each end with a different root; the pearl that walks it hands the root to every other, so a choir is
 * walked about once a scan however large it grows.
 */
public final class PearlChoir
{
    private PearlChoir()
    {
    }

    /**
     * @param root    the lowest packed position among the members
     * @param members the packed positions of every pearl linked to the one walked from, itself included
     */
    public record Walk(long root, Set<Long> members)
    {
    }

    /**
     * The pearls around, as one pearl's scan sees them.
     */
    public interface Field
    {
        /**
         * @return the packed positions of the fed pearls a pearl counts in reach, empty for one gone or not loaded
         */
        List<Long> neighbours(long pearl);

        /**
         * @return the root a pearl last settled on
         */
        long root(long pearl);

        /**
         * Hands a pearl the root its choir was just walked to.
         */
        void hand(long pearl, long root);
    }

    /**
     * What one pearl's scan settles its root to. A fed pearl walks its whole choir, all fed since no pearl counts an unfed one, and hands the root
     * to every other member. An unfed pearl counts its fed neighbours without their counting it, so it only borrows the root their choir already has:
     * walking from it would make it a member of its own making, and two unfed pearls at either end of one choir would then hand it different roots by
     * turns.
     */
    public static long scan(long self, boolean fed, Field field)
    {
        var own = field.neighbours(self);
        if (own.isEmpty())
            return self;
        if (!fed)
        {
            var lowest = Long.MAX_VALUE;
            for (var neighbour : own)
                lowest = Math.min(lowest, field.root(neighbour));
            return lowest;
        }
        var walk = walk(self, field::neighbours);
        for (var member : walk.members())
        {
            if (member != self)
                field.hand(member, walk.root());
        }
        return walk.root();
    }

    /**
     * @param self       this pearl's packed position
     * @param neighbours the packed positions of the pearls a pearl counts in reach, empty for one gone or not loaded
     */
    public static Walk walk(long self, LongFunction<List<Long>> neighbours)
    {
        var lowest = self;
        var members = new HashSet<Long>();
        var queue = new ArrayDeque<Long>();
        members.add(self);
        queue.add(self);
        while (!queue.isEmpty())
        {
            var at = queue.poll();
            lowest = Math.min(lowest, at);
            for (var next : neighbours.apply(at))
            {
                if (members.add(next))
                    queue.add(next);
            }
        }
        return new Walk(lowest, members);
    }
}
