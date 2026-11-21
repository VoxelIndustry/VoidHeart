package net.voxelindustry.voidheart.common.content.essence;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * How the essence of a Great Ravenous Storage is shared between its eight blocks. Each block holds its own share, so a
 * block broken out of the cube carries what it held. Shares are kept level: essence goes to the emptiest blocks first and
 * comes out of the fullest.
 */
public final class GreatStorageShares
{
    private GreatStorageShares()
    {
    }

    /**
     * The aspect the blocks agree on when they join: {@code none} when none of them holds essence, null when two of them
     * hold different aspects. A block that holds nothing has no say, whatever it was attuned to.
     */
    public static <T> @Nullable T commonAspect(List<T> aspects, int[] amounts, T none)
    {
        var common = none;
        for (var block = 0; block < aspects.size(); block++)
        {
            if (amounts[block] <= 0 || aspects.get(block) == none)
                continue;
            if (common == none)
                common = aspects.get(block);
            else if (common != aspects.get(block))
                return null;
        }
        return common;
    }

    /**
     * A total spread as evenly as can be over {@code blocks} shares, the first ones taking what does not divide.
     */
    public static int[] spread(long total, int blocks)
    {
        var shares = new int[blocks];
        for (var block = 0; block < blocks; block++)
            shares[block] = (int) (total / blocks + (block < total % blocks ? 1 : 0));
        return shares;
    }

    /**
     * How much each share takes of {@code amount}, raising the lowest shares first and never past {@code capacity}.
     */
    public static int[] fill(int[] amounts, int capacity, int amount)
    {
        var levels = amounts.clone();
        var remaining = amount;
        while (remaining > 0)
        {
            var low = Integer.MAX_VALUE;
            for (var level : levels)
            {
                if (level < capacity)
                    low = Math.min(low, level);
            }
            if (low == Integer.MAX_VALUE)
                break;

            var next = capacity;
            var count = 0;
            for (var level : levels)
            {
                if (level == low)
                    count++;
                else if (level > low)
                    next = Math.min(next, level);
            }
            remaining = shift(levels, low, next - low, count, remaining, 1);
        }
        return difference(levels, amounts);
    }

    /**
     * How much each share gives of {@code amount}, lowering the highest shares first and never below empty.
     */
    public static int[] drain(int[] amounts, int amount)
    {
        var levels = amounts.clone();
        var remaining = amount;
        while (remaining > 0)
        {
            var high = 0;
            for (var level : levels)
                high = Math.max(high, level);
            if (high == 0)
                break;

            var next = 0;
            var count = 0;
            for (var level : levels)
            {
                if (level == high)
                    count++;
                else
                    next = Math.max(next, level);
            }
            remaining = shift(levels, high, high - next, count, remaining, -1);
        }
        return difference(amounts, levels);
    }

    /**
     * Moves every share at {@code level} by {@code step} in {@code sign}'s direction, or as far as {@code remaining} allows,
     * evenly. Returns what is left to move.
     */
    private static int shift(int[] levels, int level, int step, int count, int remaining, int sign)
    {
        if ((long) step * count <= remaining)
        {
            for (var block = 0; block < levels.length; block++)
            {
                if (levels[block] == level)
                    levels[block] += sign * step;
            }
            return remaining - step * count;
        }

        var each = remaining / count;
        var extra = remaining % count;
        for (var block = 0; block < levels.length; block++)
        {
            if (levels[block] != level)
                continue;
            levels[block] += sign * (each + (extra-- > 0 ? 1 : 0));
        }
        return 0;
    }

    private static int[] difference(int[] after, int[] before)
    {
        var difference = new int[after.length];
        for (var block = 0; block < after.length; block++)
            difference[block] = after[block] - before[block];
        return difference;
    }
}
