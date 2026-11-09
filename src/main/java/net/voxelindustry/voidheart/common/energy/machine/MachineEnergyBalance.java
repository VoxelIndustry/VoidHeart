package net.voxelindustry.voidheart.common.energy.machine;

/**
 * What the machines unlocked after energy draw, from docs/plans/energy-numbers.md (W6). Rates are void energy per tick, ley is a rate.
 */
public final class MachineEnergyBalance
{
    /** While a dungeon search runs. A search lasts 2 to 8 minutes. */
    public static final long ARMILLARY_SEARCH       = 8;
    /** Gathered before a dungeon opens, by its size. */
    public static final long ARMILLARY_OPEN_SMALL   = 60_000;
    public static final long ARMILLARY_OPEN_MEDIUM  = 90_000;
    public static final long ARMILLARY_OPEN_LARGE   = 120_000;
    /** The fastest an opening gathers. Pylon caps usually limit it first. */
    public static final long ARMILLARY_INTAKE       = 240;
    /** While the search for the strata runs. */
    public static final long DAEDALUS_SEARCH        = 8;
    public static final long DAEDALUS_OPEN          = 240_000;
    /** While the Daedalus is held open and not severed. */
    public static final long DAEDALUS_HOLD          = 64;
    /** A hold short by this many ticks of its draw is severed, which then takes the usual two minutes. */
    public static final int  HOLD_STARVED_TICKS     = 30 * 20;
    /** What the deep siphon may put aside at the armillary for the hold, one second of it. */
    public static final long SIPHON_HOLD_STORE      = DAEDALUS_HOLD * 20;

    /** Each aspect point of a brew, spread over the brew. */
    public static final long   ATHANOR_PER_ASPECT   = 24;
    /** Each refinement (empowered, prolonged, remembering) multiplies the brew's cost. */
    public static final double ATHANOR_REFINE       = 1.5;

    /** A linked portal pair, while it is open. */
    public static final long PORTAL_HOLD            = 4;
    // GUESS, not in energy-numbers.md: how long a pair stays open short of power before it goes dark
    public static final int  PORTAL_STARVED_TICKS   = 40;

    /** Ley a Beholder needs to see its full range. */
    public static final double BEHOLDER_NEED        = 2.0;
    /** Under this share of its need a Beholder sees nothing. */
    public static final float  BEHOLDER_MIN_SUPPLY  = 0.1F;

    private MachineEnergyBalance()
    {
    }
}
