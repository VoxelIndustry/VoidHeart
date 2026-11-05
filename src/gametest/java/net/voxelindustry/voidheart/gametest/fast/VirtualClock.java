package net.voxelindustry.voidheart.gametest.fast;

/**
 * The game's clock in fast game tests. It holds still within a frame and moves 50 ms with each frame in a world.
 * Outside a world it follows real time, since loading a world waits on other threads.
 */
public final class VirtualClock
{
    private static final long NANOS_PER_TICK = 50_000_000L;

    private static volatile boolean held;
    private static volatile long heldNanos;
    private static volatile long realOffset;

    private VirtualClock()
    {
    }

    public static long nanos()
    {
        return held ? heldNanos : System.nanoTime() + realOffset;
    }

    public static long millis()
    {
        return nanos() / 1_000_000L;
    }

    /**
     * Called on the render thread at the start of each frame.
     */
    public static void onFrame(boolean inWorld)
    {
        if (inWorld)
        {
            heldNanos = nanos() + NANOS_PER_TICK;
            held = true;
        }
        else if (held)
        {
            realOffset = heldNanos - System.nanoTime();
            held = false;
        }
    }
}
