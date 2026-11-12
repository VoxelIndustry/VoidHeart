package net.voxelindustry.voidheart.common.content.underway;

/**
 * What stands between a key at work and the mobs sent against it: a shield of panes, one lost to each hit, then the key itself for a few hits more.
 * Left alone for a while, the shield comes back whole. The key's own hits never do. Pure, so it is unit tested.
 */
public final class KeyDefence
{
    /** The four sides by compass, then the two halves of the roof, west and east of the line where the leaves meet. */
    public static final int PANES = 6;
    public static final int ROOF_WEST = 4;
    public static final int ROOF_EAST = 5;
    public static final int ALL_PANES = (1 << PANES) - 1;

    private static final int SIDES = 4;

    public enum Outcome
    {
        ABSORBED,
        SHIELD_BROKEN,
        KEY_HIT,
        KEY_DESTROYED
    }

    private int panes;
    private int keyHits;
    private int lull;

    public KeyDefence()
    {
        this(ALL_PANES, HatchBalance.KEY_HITS, 0);
    }

    public KeyDefence(int panes, int keyHits, int lull)
    {
        this.panes = panes & ALL_PANES;
        this.keyHits = Math.clamp(keyHits, 0, HatchBalance.KEY_HITS);
        this.lull = Math.max(0, lull);
    }

    /** @return a bit for each pane still lit */
    public int panes()
    {
        return panes;
    }

    public int keyHits()
    {
        return keyHits;
    }

    public int lull()
    {
        return lull;
    }

    public boolean isShielded()
    {
        return panes != 0;
    }

    /**
     * @param dx where the blow came from, east of the key
     * @param dz where the blow came from, south of the key
     * @return the lit pane that takes it: the side it came from, else the roof on that side, else whichever is left. -1 with the shield down.
     */
    public int paneFor(double dx, double dz)
    {
        if (panes == 0)
            return -1;
        int side = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0);
        int roof = dx > 0 ? ROOF_EAST : ROOF_WEST;
        int[] order = { side, roof, ROOF_WEST + ROOF_EAST - roof, (side + 1) % SIDES, (side + 3) % SIDES, (side + 2) % SIDES };
        for (int pane : order)
        {
            if ((panes & 1 << pane) != 0)
                return pane;
        }
        return -1;
    }

    public Outcome hit(double dx, double dz)
    {
        lull = 0;
        int pane = paneFor(dx, dz);
        if (pane >= 0)
        {
            panes &= ~(1 << pane);
            return panes == 0 ? Outcome.SHIELD_BROKEN : Outcome.ABSORBED;
        }
        keyHits = Math.max(0, keyHits - 1);
        return keyHits == 0 ? Outcome.KEY_DESTROYED : Outcome.KEY_HIT;
    }

    /** @return whether the shield came back whole on this tick */
    public boolean tick()
    {
        if (panes == ALL_PANES)
            return false;
        if (++lull < HatchBalance.SHIELD_LULL_TICKS)
            return false;
        panes = ALL_PANES;
        lull = 0;
        return true;
    }
}
