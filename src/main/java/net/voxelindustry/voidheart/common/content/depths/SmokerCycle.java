package net.voxelindustry.voidheart.common.content.depths;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.core.BlockPos;

/**
 * A smoker's rhythm, as a pure function of its position and the game time. Server and client work it out on their own, so nothing is saved or synced
 * and every smoker keeps its own clock without a single packet.
 */
public final class SmokerCycle
{
    public enum Phase
    {
        QUIET,
        WARNING,
        ERUPTING
    }

    public static final int MIN_QUIET_TICKS = 120;
    public static final int QUIET_SPREAD_TICKS = 81;
    public static final int WARNING_TICKS = 30;
    public static final int ERUPTING_TICKS = 60;
    public static final int MIN_JET_HEIGHT = 5;
    public static final int JET_HEIGHT_SPREAD = 3;

    private SmokerCycle()
    {
    }

    public static long seed(BlockPos pos)
    {
        return HashCommon.murmurHash3(pos.asLong());
    }

    private static int quietTicks(long seed)
    {
        return MIN_QUIET_TICKS + (int) Long.remainderUnsigned(seed, QUIET_SPREAD_TICKS);
    }

    public static int period(long seed)
    {
        return quietTicks(seed) + WARNING_TICKS + ERUPTING_TICKS;
    }

    public static int ticksIntoCycle(long seed, long gameTime)
    {
        return (int) Long.remainderUnsigned(gameTime + (seed >>> 16), period(seed));
    }

    public static Phase phase(long seed, long gameTime)
    {
        int ticks = ticksIntoCycle(seed, gameTime);
        int quiet = quietTicks(seed);
        if (ticks < quiet)
            return Phase.QUIET;
        return ticks < quiet + WARNING_TICKS ? Phase.WARNING : Phase.ERUPTING;
    }

    public static int jetHeight(long seed)
    {
        return MIN_JET_HEIGHT + (int) Long.remainderUnsigned(seed >>> 32, JET_HEIGHT_SPREAD);
    }
}
