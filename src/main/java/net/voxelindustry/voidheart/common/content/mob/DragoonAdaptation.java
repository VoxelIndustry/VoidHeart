package net.voxelindustry.voidheart.common.content.mob;

/**
 * How far a Kernacian Dragoon has adapted to each kind of damage. Every hit of a kind takes a share off that kind for good, up to a cap, so it stays killable
 * but punishes leaning on one weapon. The counts are packed four bits each into one int, to be synced and saved as a single value.
 */
public final class DragoonAdaptation
{
    public enum Kind
    {
        BLADE,
        BLUNT,
        PROJECTILE,
        FIRE,
        EXPLOSION,
        MAGIC,
        /**
         * Only Kernacian Oil deals it. Appended last so saved counts keep their slots, and seven kinds of four bits still fit the int.
         */
        KERNACIAN
    }

    public static final int MAX_HITS = 10;
    public static final float PER_HIT = 0.08F;
    /**
     * Hits at which each next set of parts grows onto the model.
     */
    public static final int[] TIERS = { 1, 4, 8 };

    private static final int BITS = 4;
    private static final int MASK = (1 << BITS) - 1;

    private DragoonAdaptation()
    {
    }

    public static int count(int packed, Kind kind)
    {
        return packed >>> kind.ordinal() * BITS & MASK;
    }

    /**
     * @return the counts with one more hit of this kind, never past the cap
     */
    public static int adapt(int packed, Kind kind)
    {
        int count = count(packed, kind);
        if (count >= MAX_HITS)
            return packed;
        int shift = kind.ordinal() * BITS;
        return packed & ~(MASK << shift) | (count + 1) << shift;
    }

    /**
     * @return what is left of a hit of this kind
     */
    public static float multiplier(int packed, Kind kind)
    {
        return 1 - PER_HIT * Math.min(count(packed, kind), MAX_HITS);
    }

    /**
     * @return how many of the kind's sets of parts have grown, from 0 to 3
     */
    public static int tier(int count)
    {
        int tier = 0;
        for (int threshold : TIERS)
        {
            if (count >= threshold)
                tier++;
        }
        return tier;
    }

    /**
     * @return whether this hit just grew a new set of parts
     */
    public static boolean grewTier(int before, int after)
    {
        return tier(after) > tier(before);
    }
}
