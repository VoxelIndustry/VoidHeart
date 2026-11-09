package net.voxelindustry.voidheart.common.energy.siphon;

/**
 * Numbers owned by the Deep Siphon, copied from docs/plans/energy-numbers.md (N0), and the user's ruling on how it draws Daedalus mobs (2026-11-09).
 */
public final class DeepSiphonBalance
{
    /**
     * Sim: VE/t drawn while the Daedalus link holds, 32 VE/t more than the hold costs, so the link pays for itself.
     */
    public static final long OUTPUT = 96;

    /**
     * Ruled, guess for the amount: void energy drawn per mob of notice, one mob per 4 minutes at full draw.
     */
    public static final long NOTICE_PER_MOB = OUTPUT * 20 * 60 * 4;

    /**
     * Ruled, guess for the amount: the most mobs notice can bank.
     */
    public static final int NOTICE_CAP = 12;

    /**
     * Ruled, guess for the amount: the most mobs one wave brings.
     */
    public static final int WAVE_SIZE = 4;

    /**
     * Ruled, guess for the amount: ticks between waves while a player is near.
     */
    public static final int WAVE_INTERVAL = 20 * 20;

    /**
     * Ruled, guess for the amount: how close a player must be for banked notice to come as waves.
     */
    public static final double PLAYER_RANGE = 48;

    private DeepSiphonBalance()
    {
    }
}
