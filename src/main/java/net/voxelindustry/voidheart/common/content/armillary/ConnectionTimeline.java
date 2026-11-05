package net.voxelindustry.voidheart.common.content.armillary;

import org.jspecify.annotations.Nullable;

/** How a connection's last minutes play out, from the ticks it has left. */
public final class ConnectionTimeline
{
    public enum Stage
    {
        STABLE,
        FADING,
        FRAYING,
        FAILING,
        COLLAPSING
    }

    public record Warning(int tick, String key, boolean near)
    {
    }

    private static final int MINUTE = 20 * 60;

    public static final int COUNTDOWN_TICKS      = 20 * 10;
    public static final int TREMOR_INTERVAL      = 10;
    public static final int NEAR_RUMBLE_INTERVAL = 20 * 6;

    private static final Warning[] WARNINGS = {
            new Warning(10 * MINUTE, "voidheart.connection.warning.fading", false),
            new Warning(5 * MINUTE, "voidheart.connection.warning.fraying", false),
            new Warning(2 * MINUTE, "voidheart.connection.warning.failing", false),
            new Warning(MINUTE, "voidheart.connection.warning.collapsing", true)
    };

    private ConnectionTimeline()
    {
    }

    public static Stage stage(long remaining)
    {
        if (remaining <= MINUTE)
            return Stage.COLLAPSING;
        if (remaining <= 2 * MINUTE)
            return Stage.FAILING;
        if (remaining <= 5 * MINUTE)
            return Stage.FRAYING;
        if (remaining <= 10 * MINUTE)
            return Stage.FADING;
        return Stage.STABLE;
    }

    /** The warning given on this exact tick, if any. */
    public static @Nullable Warning warningAt(long remaining)
    {
        for (var warning : WARNINGS)
        {
            if (warning.tick() == remaining)
                return warning;
        }
        return null;
    }

    /** Seconds to show when the final countdown ticks over, 0 on other ticks. */
    public static int countdownAt(long remaining)
    {
        if (remaining <= 0 || remaining > COUNTDOWN_TICKS || remaining % 20 != 0)
            return 0;
        return (int) (remaining / 20);
    }

    /** Strength of the shaking, 0 until the last minute and 1 at the very end. Eased so that it starts gently. */
    public static float tremor(long remaining)
    {
        if (remaining >= MINUTE)
            return 0;
        float elapsed = 1 - Math.max(0, remaining) / (float) MINUTE;
        return elapsed * elapsed;
    }
}
