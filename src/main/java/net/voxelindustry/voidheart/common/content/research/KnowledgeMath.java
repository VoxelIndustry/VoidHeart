package net.voxelindustry.voidheart.common.content.research;

public final class KnowledgeMath
{
    private KnowledgeMath()
    {
    }

    /** Yield of the next item of a type: 1st = 100%, 2nd-5th = 30%, 6th-8th = 10%, then nothing. */
    public static double feedMultiplier(int alreadyFed)
    {
        if (alreadyFed <= 0)
            return 1;
        if (alreadyFed <= 4)
            return 0.3;
        if (alreadyFed <= 7)
            return 0.1;
        return 0;
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
}
