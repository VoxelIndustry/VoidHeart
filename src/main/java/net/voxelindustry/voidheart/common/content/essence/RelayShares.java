package net.voxelindustry.voidheart.common.content.essence;

import java.util.Arrays;

/**
 * How a source's essence for one route is shared among the machines it reaches. Each gets an even part, those needing less than that take only
 * what they need and leave the rest to the others, and what an even split can't divide goes to the nearest of those needing the same.
 */
public final class RelayShares
{
    private RelayShares()
    {
    }

    /**
     * @param budget what the source may send this route
     * @param spaces room each machine has, nearest first
     * @return what each machine gets, in the same order, never more than its room and adding up to at most the budget
     */
    public static int[] split(int budget, int[] spaces)
    {
        var shares = new int[spaces.length];
        var left = Math.max(0, budget);
        var waiting = 0;
        for (var space : spaces)
            if (space > 0)
                waiting++;

        // Smallest needs first, so whatever they leave over is shared among the larger ones; rounding up hands the odd units to the nearest of equals
        var order = new Integer[spaces.length];
        for (var i = 0; i < order.length; i++)
            order[i] = i;
        Arrays.sort(order, (a, b) -> spaces[a] != spaces[b] ? Integer.compare(spaces[a], spaces[b]) : Integer.compare(a, b));
        for (var index : order)
        {
            if (spaces[index] <= 0)
                continue;
            var share = Math.min(spaces[index], (left + waiting - 1) / waiting);
            shares[index] = share;
            left -= share;
            waiting--;
        }

        for (var i = 0; i < spaces.length && left > 0; i++)
        {
            var extra = Math.min(left, Math.max(0, spaces[i] - shares[i]));
            shares[i] += extra;
            left -= extra;
        }
        return shares;
    }
}
