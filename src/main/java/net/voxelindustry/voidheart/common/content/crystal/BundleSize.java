package net.voxelindustry.voidheart.common.content.crystal;

import org.jspecify.annotations.Nullable;

/**
 * A bundle's size and the shards it is worth, which match what a world bundle breaks into: a large one gives up to four.
 */
public enum BundleSize
{
    SMALL(1),
    MEDIUM(2),
    LARGE(4);

    private final int worth;

    BundleSize(int worth)
    {
        this.worth = worth;
    }

    public int worth()
    {
        return worth;
    }

    /**
     * The largest size that many shards are worth, or null below one shard.
     */
    public static @Nullable BundleSize largestWithin(int worth)
    {
        BundleSize largest = null;
        for (var size : values())
        {
            if (size.worth <= worth)
                largest = size;
        }
        return largest;
    }

    /**
     * The smallest size worth more than that many shards, or null past a large bundle.
     */
    public static @Nullable BundleSize nextAbove(double worth)
    {
        for (var size : values())
        {
            if (size.worth > worth)
                return size;
        }
        return null;
    }
}
