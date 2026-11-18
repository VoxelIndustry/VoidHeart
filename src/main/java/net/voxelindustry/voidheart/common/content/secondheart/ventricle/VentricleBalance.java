package net.voxelindustry.voidheart.common.content.secondheart.ventricle;

/**
 * The Twin Ventricle's numbers, all in one place for tuning.
 */
public final class VentricleBalance
{
    public static final int KERNACIAN_CORES   = 1;
    public static final int CONTRAPTIONS      = 1;
    public static final int MEMORIAM_SHARDS   = 8;
    public static final int WRAITH_INKS       = 4;
    /** One full experience skull, about thirty levels. */
    public static final int EXPERIENCE        = 1395;
    public static final int EXPERIENCE_PER_TICK = 25;
    public static final int SKULL_RADIUS      = 5;

    /** About a lit Contravoid reactor's output, through kindling and beating. */
    public static final long ENERGY_PER_TICK  = 400;

    public static final int KINDLING_TICKS    = 30 * 20;
    public static final int BEATING_TICKS     = 2 * 60 * 20;
    public static final int BEAT_TICKS        = 24;
    /** Out of step while kindling, each basin on its own beat, drawing together as it nears the end. */
    public static final int LEFT_KINDLE_BEAT  = 34;
    public static final int RIGHT_KINDLE_BEAT = 26;

    public static final int BASIN_TANK        = 4000;
    public static final int KINDLING_FUEL_PER_TICK = 3;
    public static final int BEATING_FUEL_PER_TICK  = 1;

    public static final int WAVES             = 8;
    public static final int DUNGEON_WAVES     = 5;
    public static final int FIRST_WAVE_BEAT   = 6;
    public static final int BEATS_PER_WAVE    = 12;
    public static final int WAVE_SIZE         = 2;
    public static final int WAVE_GROWTH_EVERY = 2;
    public static final double TEAR_MIN_RANGE = 6;
    public static final double TEAR_MAX_RANGE = 10;
    public static final int TEAR_TICKS        = 40;
    public static final int TEAR_OPEN_TICKS   = 10;
    /** The creature steps out once the tear is open. */
    public static final int TEAR_RELEASE_TICK = 12;
    public static final int TEAR_CLOSE_TICKS  = 10;

    /** How close to the cradle's centre the celebrant must stand. */
    public static final double CRADLE_REACH   = 0.8;

    private VentricleBalance()
    {
    }
}
