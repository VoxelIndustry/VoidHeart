package net.voxelindustry.voidheart.common.ley.anchor;

/**
 * Numbers owned by wrought anchors. Values marked GUESS wait for play.
 */
public final class WroughtAnchorBalance
{
    /** Farthest a wrought anchor may stand from a ley line, measured flat from the line to the block's center. */
    public static final int LINE_REACH = 8;

    // GUESS: void energy every trip from a wrought anchor costs, however short
    public static final long TRAVEL_BASE      = 1_000;
    // GUESS: void energy each block of straight distance adds, so 1 000 blocks cost 5 000 and 10 000 blocks 41 000
    public static final long TRAVEL_PER_BLOCK = 4;
    // GUESS: the fastest a wrought anchor gathers a trip's energy, as the armillary gathers an opening. Pylon caps usually limit it first
    public static final long INTAKE           = 240;
    // GUESS: a gathering that draws nothing for this long fizzles, losing what it had gathered
    public static final int  STARVED_TICKS    = 60;
    /** How often a gathering tells the traveller how far along it is. */
    public static final int  PROGRESS_TICKS   = 10;

    public static final int NAME_MAX_LENGTH = 32;

    private WroughtAnchorBalance()
    {
    }

    /**
     * Void energy a trip of this straight distance in blocks costs.
     */
    public static long travelCost(double distance)
    {
        return TRAVEL_BASE + (long) Math.ceil(Math.max(0, distance) * TRAVEL_PER_BLOCK);
    }

    /**
     * A name a player gave, stripped of formatting codes and control characters, with runs of spaces folded and cut to
     * {@link #NAME_MAX_LENGTH}. Empty when nothing is left.
     */
    public static String cleanName(String name)
    {
        var cleaned = new StringBuilder();
        for (int i = 0; i < name.length(); i++)
        {
            char c = name.charAt(i);
            if (c == '§')
                i++;
            else if (!Character.isISOControl(c))
                cleaned.append(c);
        }
        var folded = cleaned.toString().strip().replaceAll("\\s+", " ");
        return folded.length() > NAME_MAX_LENGTH ? folded.substring(0, NAME_MAX_LENGTH).strip() : folded;
    }
}
