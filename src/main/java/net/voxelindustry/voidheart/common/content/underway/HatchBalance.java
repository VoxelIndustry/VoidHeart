package net.voxelindustry.voidheart.common.content.underway;

/** The numbers of opening a hatch with an Underway key. Guesses, to tune in play. */
public final class HatchBalance
{
    public static final int CHARGE_TICKS = 2 * 60 * 20;
    /** The hatch only charges while a player stands this close, since the district's mobs rest when nobody is near. */
    public static final double PLAYER_RANGE = 32;
    /** Ticks between two columns of plates drawing back once the charge is full. */
    public static final int LEAF_STAGE_TICKS = 14;

    private HatchBalance()
    {
    }
}
