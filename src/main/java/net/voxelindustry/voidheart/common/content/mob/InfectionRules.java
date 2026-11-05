package net.voxelindustry.voidheart.common.content.mob;

// The first hit starts the one timer, and later hits only add to what will burst out
public final class InfectionRules
{
    public static final int DURATION_TICKS = 120 * 20;
    public static final int MAX_AMOUNT = 5;
    public static final int WARNING_TICKS = 10 * 20;

    private InfectionRules()
    {
    }

    public static int amountAfterHit(int amount)
    {
        return Math.min(amount + 1, MAX_AMOUNT);
    }

    public static int ticksAfterHit(int amount, int ticks)
    {
        return amount == 0 ? DURATION_TICKS : ticks;
    }

    public static boolean isWarning(int ticks)
    {
        return ticks <= WARNING_TICKS;
    }

    /**
     * Ticks between pulses of symptoms, which close in on the burst.
     */
    public static int warningInterval(int ticks)
    {
        return ticks > WARNING_TICKS / 2 ? 30 : ticks > WARNING_TICKS / 4 ? 15 : 7;
    }
}
