package net.voxelindustry.voidheart.common.energy.superfluid;

import net.voxelindustry.voidheart.common.content.crystal.BundleSize;

/**
 * Numbers owned by superfluid azurium. Guesses for the tuning sheet.
 * <p>
 * Superfluid is counted in whole units so a tesseract is either exactly full or not: a shard of azurium makes {@link #SHARD_UNITS} of them, and a
 * pouring cradle gives {@link #POUR_PER_TICK} a tick.
 */
public final class SuperfluidBalance
{
    /** Units a shard of azurium makes. At one a tick, a shard takes five minutes to pour. */
    public static final int SHARD_UNITS = 5 * 60 * 20;

    /** Units one cradle pours into its pearl each tick. */
    public static final int POUR_PER_TICK = 1;

    /** Shards' worth one ring holds once closed: a large bundle. */
    public static final int RING_SHARDS = BundleSize.LARGE.worth();
    public static final int RING_UNITS  = RING_SHARDS * SHARD_UNITS;

    /** Rings a pearl can wind about itself. */
    public static final int RINGS = 4;

    /** Units a pearl holds as rings, and a tesseract holds: sixteen shards. */
    public static final int PEARL_CAPACITY     = RINGS * RING_UNITS;
    public static final int TESSERACT_CAPACITY = PEARL_CAPACITY;

    /** The farthest a cradle stands from a mature pearl, block to block, and still pours into it. */
    public static final double REACH = 5;

    /** Ticks between a cradle looking for the pearl to pour into. */
    public static final int SCAN_PERIOD = 20;

    /** Ticks between telling clients how far a pouring cradle and a filling pearl are. */
    public static final int SYNC_TICKS = 20;

    private SuperfluidBalance()
    {
    }
}
