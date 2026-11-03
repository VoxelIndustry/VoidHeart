package net.voxelindustry.voidheart.common.content.research;

public final class KnowledgeMath
{
    private static final int[] COLUMN_HEIGHT_LEVELS = { 2, 0, 3, 1, 4 };

    private KnowledgeMath()
    {
    }

    public enum Exhaustion
    {
        UNTOUCHED(1),
        DRAINED(0.3),
        FADING(0.1),
        SPENT(0);

        private final double multiplier;

        Exhaustion(double multiplier)
        {
            this.multiplier = multiplier;
        }
    }

    /** 1st item of a type = untouched, 2nd-5th = drained, 6th-8th = fading, then spent. */
    public static Exhaustion exhaustion(int alreadyFed)
    {
        if (alreadyFed <= 0)
            return Exhaustion.UNTOUCHED;
        if (alreadyFed <= 4)
            return Exhaustion.DRAINED;
        if (alreadyFed <= 7)
            return Exhaustion.FADING;
        return Exhaustion.SPENT;
    }

    public static double feedMultiplier(int alreadyFed)
    {
        return exhaustion(alreadyFed).multiplier;
    }

    /** Which of the four columns of a block space a point falls in: bit 0 for the east half, bit 1 for the south one. */
    public static int columnQuarter(double x, double z)
    {
        return (x >= 0.5 ? 1 : 0) | (z >= 0.5 ? 2 : 0);
    }

    public static double columnCenterX(int quarter)
    {
        return (quarter & 1) == 0 ? 0.25 : 0.75;
    }

    public static double columnCenterZ(int quarter)
    {
        return (quarter & 2) == 0 ? 0.25 : 0.75;
    }

    /** From -1 to 1, spreading outward: the further from the center of its cluster, the later a column reaches the same height. */
    public static double columnWave(double ticks, double distance, double periodTicks, double wavelength)
    {
        return Math.sin(2 * Math.PI * (ticks / periodTicks - distance / wavelength));
    }

    /**
     * Which of five heights a column floats at, from where it stands on the grid of half blocks. Scrambled to look unplanned, yet any two touching columns,
     * diagonals included, always land on different ones: x + 2z changes by 1, 2 or 3 between them, never by 5.
     */
    public static int columnHeightLevel(int halfBlockX, int halfBlockZ)
    {
        return COLUMN_HEIGHT_LEVELS[Math.floorMod(halfBlockX + 2 * halfBlockZ, COLUMN_HEIGHT_LEVELS.length)];
    }

    public static double accrue(double knowledge, double cap, double rate, double seconds)
    {
        return Math.max(knowledge, Math.min(cap, knowledge + rate * seconds));
    }

    /** The seconds spent are less than asked only when the research finished early. */
    public record Flow(double knowledge, double remaining, double seconds)
    {
    }

    /** Drains the pool at full speed until it runs dry, then only as fast as knowledge comes in. Solved in one go, so any elapsed time gives the same result. */
    public static Flow research(double knowledge, double cap, double rate, double remaining, double drain, double seconds)
    {
        double net = drain - rate;
        double untilFinished = remaining / drain;
        double untilDry = net > 0 ? knowledge / net : Double.POSITIVE_INFINITY;
        double fullSpeed = Math.min(seconds, Math.min(untilFinished, untilDry));

        double pool = Math.max(0, Math.min(Math.max(cap, knowledge), knowledge - net * fullSpeed));
        if (untilFinished <= fullSpeed)
            return new Flow(pool, 0, untilFinished);

        double left = remaining - drain * fullSpeed;
        if (untilDry > fullSpeed || rate <= 0)
            return new Flow(pool, left, seconds);

        double trickling = Math.min(seconds - fullSpeed, left / rate);
        return new Flow(0, Math.max(0, left - rate * trickling), fullSpeed + trickling);
    }

    /** How long {@link #research} will take from here. Infinite when the pool runs dry with nothing coming in. */
    public static double secondsToFinish(double knowledge, double rate, double remaining, double drain)
    {
        double net = drain - rate;
        double untilFinished = remaining / drain;
        double untilDry = net > 0 ? knowledge / net : Double.POSITIVE_INFINITY;
        if (untilFinished <= untilDry)
            return untilFinished;
        if (rate <= 0)
            return Double.POSITIVE_INFINITY;
        return untilDry + (remaining - drain * untilDry) / rate;
    }

    /** Infinite when the amount is never reached at this rate of change. */
    public static double secondsToReach(double knowledge, double target, double change)
    {
        if (knowledge >= target)
            return 0;
        return change > 0 ? (target - knowledge) / change : Double.POSITIVE_INFINITY;
    }

    /** How many insights in a row this much knowledge pays for, each dearer than the last, counting no further than max. */
    public static int affordableInsights(double knowledge, double nextCost, double growth, int max)
    {
        if (nextCost <= 0)
            return 0;

        int count = 0;
        double cost = nextCost;
        while (count < max && knowledge >= cost)
        {
            knowledge -= cost;
            cost *= growth;
            count++;
        }
        return count;
    }
}
