package net.voxelindustry.voidheart.common.content.underway;

/** The numbers of opening a hatch with an Underway key. Guesses, to tune in play. */
public final class HatchBalance
{
    public static final int CHARGE_TICKS = 2 * 60 * 20;
    /** The hatch only charges while a player stands this close, since the district's mobs rest when nobody is near. */
    public static final double PLAYER_RANGE = 32;
    /** Ticks between two columns of plates drawing back once the charge is full. */
    public static final int LEAF_STAGE_TICKS = 14;

    /** Hits the bare key takes before it breaks. The shield takes one per pane, see {@link KeyDefence#PANES}. */
    public static final int KEY_HITS = 3;
    /** Ticks without a hit before the shield comes back whole. */
    public static final int SHIELD_LULL_TICKS = 12 * 20;
    /** The least ticks between two hits that count, so one swing or a crowd striking together never empties the shield at once. */
    public static final int HIT_INTERVAL_TICKS = 10;

    private HatchBalance()
    {
    }
}
