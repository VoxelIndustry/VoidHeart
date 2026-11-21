package net.voxelindustry.voidheart.common.content.crystal;

/**
 * The cradle's numbers. Guesses for the tuning sheet.
 */
public final class CradleBalance
{
    /** Ley the cradle asks of its stone while growing, the same as a grower. */
    public static final double LEY_NEED = 1.0;

    /** Ticks to grow one shard's worth at full supply: 20 minutes, so 80 for a large bundle. */
    public static final int TICKS_PER_WORTH = 20 * 60 * 20;

    /** How often a growing cradle tells its watchers how far it is. */
    public static final int SYNC_TICKS = 20;

    /** A cradle standing in seep this strong drinks it instead of ley and grows Fouled. */
    public static final float FOUL_SEEP = 0.25F;

    /** How much faster seep grows a crystal than full ley. */
    public static final double SEEP_GROWTH = 1.5;

    /** Taint scouring pushes into the line's upstream node per shard's worth: 5 for small, 10 for medium, 20 for large. */
    public static final double TAINT_PER_WORTH = 5;

    /** How often a scouring cradle hands its foulness to the node and checks whether the node can still take it. */
    public static final int SCOUR_TICKS = 20;

    /** Essence a relay-fed cradle uses each tick at full ley supply: Fiery grows Searing, Stillness Still, Suffering Wrung. 2,400 per shard's worth. */
    public static final double ESSENCE_PER_TICK = 0.1;

    /** Essence of each kind a cradle holds ahead of its growth, enough for 2,000 ticks between relay routes. */
    public static final int ESSENCE_TANK = 200;

    /** Void energy a cradle draws each tick for what ley doesn't give it, once its owner knows Deep Growth. */
    public static final long VOID_NEED = 24;

    /** Blocks around a cradle a Deep memoriam records when it finishes, every way. */
    public static final int RECORD_RADIUS = 48;

    /** How often a cradle reads the seep it stands in. */
    public static final int SEEP_TICKS = 10;

    private CradleBalance()
    {
    }
}
