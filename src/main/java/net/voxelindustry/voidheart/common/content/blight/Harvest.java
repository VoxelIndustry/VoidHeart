package net.voxelindustry.voidheart.common.content.blight;

import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * When a mob slain by the scythe rises, and the timeline of the wraith it becomes: ink spreads under the corpse, the wraith comes up out of the
 * ground through it, fights, then sinks back.
 */
public final class Harvest
{
    public enum Phase
    {
        STAIN,
        RISING,
        FIGHTING,
        SINKING,
        GONE
    }

    /** The sink start of a wraith that isn't sinking. Any other value can be one, below zero too, see {@link #sinkStartAt}. */
    public static final int NOT_SINKING = Integer.MIN_VALUE;

    private Harvest()
    {
    }

    /** Whether a slain mob rises: big enough to be worth a wraith, never a player, a boss or a wraith already. */
    public static boolean canRise(float maxHealth, boolean player, boolean unharvestable, boolean wraith)
    {
        return !player && !unharvestable && !wraith && maxHealth >= BlightBalance.WRAITH_SOURCE_HEALTH;
    }

    /**
     * @param age       ticks since the wraith was raised
     * @param sinkStart the age its sink counts from, or {@link #NOT_SINKING}
     */
    public static Phase phase(int age, int sinkStart)
    {
        if (sinkStart != NOT_SINKING)
            return age - sinkStart >= BlightBalance.WRAITH_SINK_TICKS ? Phase.GONE : Phase.SINKING;
        if (age < BlightBalance.WRAITH_STAIN_TICKS)
            return Phase.STAIN;
        if (age < BlightBalance.WRAITH_STAIN_TICKS + BlightBalance.WRAITH_RISE_TICKS)
            return Phase.RISING;
        return Phase.FIGHTING;
    }

    /** Whether its time is up and it should begin sinking. */
    public static boolean isSpent(int age)
    {
        return age >= BlightBalance.WRAITH_STAIN_TICKS + BlightBalance.WRAITH_RISE_TICKS + BlightBalance.WRAITH_LIFE_TICKS;
    }

    /** How far below the ground it is, from 1 a whole body under to 0 standing on it. */
    public static float buried(float age, int sinkStart)
    {
        if (sinkStart != NOT_SINKING)
            return clamp((age - sinkStart) / BlightBalance.WRAITH_SINK_TICKS);
        float rise = (age - BlightBalance.WRAITH_STAIN_TICKS) / BlightBalance.WRAITH_RISE_TICKS;
        // Slow out of the ground at first, then pulled up
        float risen = clamp(rise);
        return 1 - risen * risen * (3 - 2 * risen);
    }

    /**
     * Where to count a sink from so that it starts as deep as the wraith is now: one sent back before it is all the way up goes down from there,
     * rather than first standing on the ground.
     */
    public static int sinkStartAt(int age)
    {
        return age - Math.round(buried(age, NOT_SINKING) * BlightBalance.WRAITH_SINK_TICKS);
    }

    /**
     * The wraiths of one wielder to send back so a new one fits: the oldest first, as many as go over the most kept.
     *
     * @param age how long each has been out
     */
    public static <T> List<T> toRetire(List<T> wraiths, ToIntFunction<T> age)
    {
        int over = wraiths.size() + 1 - BlightBalance.WRAITH_MAX;
        if (over <= 0)
            return List.of();
        return wraiths.stream().sorted(Comparator.comparingInt(age).reversed()).limit(over).toList();
    }

    private static float clamp(float value)
    {
        return Math.max(0, Math.min(1, value));
    }
}
