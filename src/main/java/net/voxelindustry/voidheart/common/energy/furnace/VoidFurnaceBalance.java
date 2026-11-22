package net.voxelindustry.voidheart.common.energy.furnace;

/**
 * Every number of the Void Furnace, guesses to tune from play, and the pace they give.
 * <p>
 * The furnace holds heat. Each tick an item cooks warms it, each tick it doesn't cools it, and its pace climbs with its heat from
 * {@link #COLD_SPEED} to {@link #HOT_SPEED} times a furnace's. An item it burned Fiery for cooks at {@link #FIERY_FACTOR} times that. A furnace kept
 * busy, fed and supplied so does the work of many; one used now and then, only a few.
 * <p>
 * Energy follows the pace, so an item costs the same however hot the furnace runs: {@link #DRAW_PER_PACE} VE a tick for each times a furnace's pace,
 * 600 for a 200-tick item. Fiery items cost a third more, {@link #FIERY_DRAW_PER_PACE}, the waste of burning that fast.
 */
public final class VoidFurnaceBalance
{
    /** Times a vanilla furnace's pace when cold, and when at full heat, on void energy. */
    public static final double COLD_SPEED   = 4;
    public static final double HOT_SPEED    = 8;
    /** How much faster an item cooks once it burned Fiery, cold or hot. */
    public static final double FIERY_FACTOR = 2;

    /** Ticks of cooking that take a cold furnace to full heat: a minute. */
    public static final int HEAT_UP_TICKS = 1_200;
    /** Ticks without cooking that take a furnace at full heat back to cold: two minutes, so short waits between batches cost little. */
    public static final int COOL_TICKS    = 2_400;

    /** Void energy a tick for each times a furnace's pace. */
    public static final double DRAW_PER_PACE       = 3;
    public static final double FIERY_DRAW_PER_PACE = 4;

    /** Fiery burned when an item starts cooking, so it cooks at {@link #FIERY_FACTOR} times the pace. */
    public static final int FIERY_PER_ITEM = 40;
    /** Fiery the furnace holds, filled through relays. */
    public static final int FIERY_TANK     = 2_000;

    private VoidFurnaceBalance()
    {
    }

    /**
     * @param heat from 0 when cold to 1 at full heat
     * @return times a vanilla furnace's pace
     */
    public static double pace(double heat, boolean fiery)
    {
        var pace = COLD_SPEED + (HOT_SPEED - COLD_SPEED) * Math.clamp(heat, 0, 1);
        return fiery ? pace * FIERY_FACTOR : pace;
    }

    /**
     * @param vanillaTicks the recipe's cooking time in a vanilla furnace
     * @return the share of an item cooked in a tick
     */
    public static double progressPerTick(int vanillaTicks, double pace)
    {
        return pace / Math.max(1, vanillaTicks);
    }

    /**
     * @return the void energy a tick of cooking draws, never none
     */
    public static long draw(double pace, boolean fiery)
    {
        return Math.max(1, Math.round(pace * (fiery ? FIERY_DRAW_PER_PACE : DRAW_PER_PACE)));
    }

    public static double warm(double heat)
    {
        return Math.min(1, heat + 1D / HEAT_UP_TICKS);
    }

    public static double cool(double heat)
    {
        return Math.max(0, heat - 1D / COOL_TICKS);
    }

    /**
     * @return whether the furnace holds enough Fiery to cook its next item fast
     */
    public static boolean canBurnFiery(int fiery)
    {
        return fiery >= FIERY_PER_ITEM;
    }

    /**
     * How a run of items goes on a full supply, tick by tick as the furnace runs it.
     *
     * @param heat the heat it starts at
     * @return the ticks the items take, and the heat left at the end
     */
    public static Run run(int items, int vanillaTicks, double heat, boolean fiery)
    {
        int ticks = 0;
        for (int item = 0; item < items; item++)
        {
            double progress = 0;
            while (progress < 1)
            {
                progress += progressPerTick(vanillaTicks, pace(heat, fiery));
                heat = warm(heat);
                ticks++;
            }
        }
        return new Run(ticks, heat);
    }

    public record Run(int ticks, double heat)
    {
    }

    /**
     * @return 0 cold, 1 warming, 2 hot, 3 at full heat
     */
    public static int heatStage(double heat)
    {
        if (heat < 0.05)
            return 0;
        if (heat < 0.5)
            return 1;
        return heat < 0.95 ? 2 : 3;
    }

    /**
     * @return a pace as it is read, whole or to a tenth, such as 4 or 5.3
     */
    public static String paceText(double pace)
    {
        var tenths = Math.round(pace * 10);
        return tenths % 10 == 0 ? Long.toString(tenths / 10) : String.format(java.util.Locale.ROOT, "%.1f", tenths / 10D);
    }
}
