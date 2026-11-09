package net.voxelindustry.voidheart.common.energy.grid;

/**
 * Numbers owned by the void grid, from the W1 rows of docs/plans/energy-numbers.md. Values marked GUESS are W1's own and wait for play.
 */
public final class VoidGridBalance
{
    /**
     * Field radius in blocks and throughput cap in void energy per tick, for a small, medium and large azurium bundle.
     */
    public static final int  SMALL_RADIUS  = 6;
    public static final long SMALL_CAP     = 80;
    public static final int  MEDIUM_RADIUS = 10;
    public static final long MEDIUM_CAP    = 128;
    public static final int  LARGE_RADIUS  = 16;
    public static final long LARGE_CAP     = 192;

    // GUESS: two pylons arc when their fields touch, so a chain covers the ground between them
    public static int linkReach(int radiusA, int radiusB)
    {
        return radiusA + radiusB;
    }

    // GUESS: how often links, line of sight and the holders inside fields are looked at again, as relays do
    public static final int REBUILD_TICKS = 40;
    // GUESS: a consumer that asks again within this many ticks keeps its place in line
    public static final int STREAK_GRACE_TICKS = 20;
    // GUESS: energy delivered ahead to a consumer that stopped asking goes back into the grid after this long
    public static final int READY_EXPIRY_TICKS = 100;
    // GUESS: load, arc flow and threads are averaged and sent to clients this often
    public static final int DISPLAY_TICKS = 10;

    private VoidGridBalance()
    {
    }
}
