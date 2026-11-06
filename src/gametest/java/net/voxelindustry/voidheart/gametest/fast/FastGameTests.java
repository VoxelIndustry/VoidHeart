package net.voxelindustry.voidheart.gametest.fast;

/**
 * Client game tests run on a virtual clock, about ten times faster than real time. Fabric otherwise holds every tick to
 * a real 50 ms, which was nearly all of a full run's half hour.
 * <p>
 * In a world each frame is one tick and moves the game's clock by exactly 50 ms, so anything timed by ticks or by
 * {@code Util.getMillis} behaves as it would at 20 ticks per second. Work on other threads still runs in real time, so a
 * tick keeps its real 50 ms while the server has background work going, and screenshots wait for the GPU.
 * <p>
 * {@code -PgameTestRealtime} runs the tests at real speed instead.
 */
public final class FastGameTests
{
    public static final boolean ENABLED = !Boolean.getBoolean("voidheart.gametest.realtime");

    private FastGameTests()
    {
    }
}
