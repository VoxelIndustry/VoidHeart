package net.voxelindustry.voidheart.common.content.mausoleum;

/**
 * The rules of the Dullahan's crown of flames, apart from the rider. Its health is cut into one more share than it has flames: each time a share is
 * spent, one flame leaves the crown as a wisp, and nothing harms the rider again until that wisp is put out. The last share can only be spent once
 * every flame has gone and every wisp is out.
 */
public final class DullahanCrown
{
    public static final int FLAMES = 8;

    private DullahanCrown()
    {
    }

    /**
     * @return the share of its health at which the next flame leaves, once {@code released} have gone, which is also the lowest a blow may leave
     * it with until that flame has gone
     */
    public static float threshold(int released)
    {
        if (released >= FLAMES)
            return 0;
        return (FLAMES - Math.max(0, released)) / (float) (FLAMES + 1);
    }

    /** @return whether a blow may harm it at all */
    public static boolean vulnerable(boolean wispsBurning)
    {
        return !wispsBurning;
    }

    /** @return whether health this low lets the next flame go */
    public static boolean releases(int released, float share)
    {
        return released < FLAMES && share <= threshold(released) + 1e-4F;
    }

    /** @return how many wraiths come with the flame that leaves as the {@code released}th, from 1 for the first */
    public static int wave(int released)
    {
        return 1 + Math.max(1, Math.min(FLAMES, released)) / 2;
    }
}
