package net.voxelindustry.voidheart.common.energy.reactor;

/**
 * What a Contravoid reactor is doing, and every way it moves on. Pure, and unit tested on each transition.
 * <ul>
 *     <li>A cold reactor lights only on request, with its containment whole, no redstone holding it down, and both inputs fuelled.</li>
 *     <li>Lighting gathers {@link ReactorBalance#IGNITION} void energy first. Losing the containment or a redstone signal stops it, and the energy
 *     gathered is kept for the next try.</li>
 *     <li>Lit, two mistakes start the warning: an input running dry, or a breach in the containment. Fixing every mistake ends it, and a redstone
 *     signal shuts it down safely at any point before the countdown runs out.</li>
 *     <li>Once the countdown runs out the light collapses for {@link ReactorBalance#COLLAPSE} ticks, then it has failed.</li>
 * </ul>
 * Only loaded ticks reach it, so an unloaded reactor stays exactly where it was.
 */
public final class ReactorCycle
{
    private static final double EPSILON = 1e-9;

    public enum Phase
    {
        COLD,
        IGNITING,
        LIT,
        FAILING,
        COLLAPSING,
        FAILED;

        public boolean isRunning()
        {
            return this == LIT || this == FAILING;
        }
    }

    public enum Cause
    {
        CONTRAVOID_DRY,
        ESSENCE_DRY,
        BREACH;

        public int bit()
        {
            return 1 << ordinal();
        }

        public boolean in(int causes)
        {
            return (causes & bit()) != 0;
        }
    }

    public enum Event
    {
        NONE,
        ABORTED,
        LIT,
        SHUT_DOWN,
        WARNING,
        RECOVERED,
        COLLAPSING,
        FAILED
    }

    public enum Refusal
    {
        NONE,
        RUNNING,
        INCOMPLETE,
        POWERED,
        NO_FUEL
    }

    /**
     * What the reactor finds around it this tick.
     *
     * @param complete   whether the containment and both intakes stand
     * @param powered    whether a redstone signal reaches any of its blocks
     * @param contravoid Contravoid held for burning
     * @param essence    essence held for burning
     * @param credit     part of a pair already taken from the intakes and not yet burned
     */
    public record Situation(boolean complete, boolean powered, int contravoid, int essence, double credit)
    {
    }

    private Phase phase = Phase.COLD;
    private long  charge;
    private int   countdown;
    private int   causes;
    private int   collapse;

    /**
     * A player asks a cold reactor to light.
     */
    public Refusal light(Situation situation)
    {
        if (phase != Phase.COLD)
            return Refusal.RUNNING;
        if (!situation.complete())
            return Refusal.INCOMPLETE;
        if (situation.powered())
            return Refusal.POWERED;
        if (isDry(situation.contravoid(), situation.credit(), false) || isDry(situation.essence(), situation.credit(), false))
            return Refusal.NO_FUEL;
        phase = Phase.IGNITING;
        return Refusal.NONE;
    }

    /**
     * Void energy it still wants for lighting this tick.
     */
    public long ignitionWanted()
    {
        return phase == Phase.IGNITING ? Math.max(0, ReactorBalance.IGNITION - charge) : 0;
    }

    /**
     * One loaded tick.
     *
     * @param drawn void energy drawn for lighting this tick, as asked through {@link #ignitionWanted()}
     */
    public Event tick(Situation situation, long drawn)
    {
        return switch (phase)
        {
            case COLD, FAILED -> Event.NONE;
            case IGNITING -> tickIgniting(situation, drawn);
            case LIT, FAILING -> tickRunning(situation);
            case COLLAPSING ->
            {
                if (--collapse > 0)
                    yield Event.NONE;
                phase = Phase.FAILED;
                yield Event.FAILED;
            }
        };
    }

    private Event tickIgniting(Situation situation, long drawn)
    {
        if (situation.powered())
        {
            phase = Phase.COLD;
            return Event.SHUT_DOWN;
        }
        if (!situation.complete())
        {
            phase = Phase.COLD;
            return Event.ABORTED;
        }

        charge = Math.min(ReactorBalance.IGNITION, charge + Math.max(0, drawn));
        // Fully charged, it waits for both inputs rather than lighting into a warning
        if (charge < ReactorBalance.IGNITION || isDry(situation.contravoid(), situation.credit(), false)
                || isDry(situation.essence(), situation.credit(), false))
            return Event.NONE;

        phase = Phase.LIT;
        charge = 0;
        return Event.LIT;
    }

    private Event tickRunning(Situation situation)
    {
        if (situation.powered())
        {
            phase = Phase.COLD;
            causes = 0;
            countdown = 0;
            return Event.SHUT_DOWN;
        }

        var now = causesOf(situation);
        if (now == 0)
        {
            if (phase == Phase.LIT)
                return Event.NONE;
            phase = Phase.LIT;
            causes = 0;
            countdown = 0;
            return Event.RECOVERED;
        }

        if (phase == Phase.LIT)
        {
            phase = Phase.FAILING;
            causes = now;
            countdown = ReactorBalance.COUNTDOWN;
            return Event.WARNING;
        }

        causes = now;
        if (--countdown > 0)
            return Event.NONE;
        countdown = 0;
        phase = Phase.COLLAPSING;
        collapse = ReactorBalance.COLLAPSE;
        return Event.COLLAPSING;
    }

    private int causesOf(Situation situation)
    {
        var now = 0;
        if (!situation.complete())
            now |= Cause.BREACH.bit();
        if (isDry(situation.contravoid(), situation.credit(), Cause.CONTRAVOID_DRY.in(causes)))
            now |= Cause.CONTRAVOID_DRY.bit();
        if (isDry(situation.essence(), situation.credit(), Cause.ESSENCE_DRY.in(causes)))
            now |= Cause.ESSENCE_DRY.bit();
        return now;
    }

    /**
     * An input is dry once nothing is left to burn even the minimum, and stays dry until it holds {@link ReactorBalance#RECOVER_FUEL} again.
     */
    static boolean isDry(int amount, double credit, boolean wasDry)
    {
        if (wasDry)
            return amount < ReactorBalance.RECOVER_FUEL;
        return amount <= 0 && credit < ReactorBalance.MIN_PAIRS - EPSILON;
    }

    /**
     * Whether it burns fuel this tick: lit, or warned only for a breach, since a dry input leaves nothing to cancel.
     */
    public boolean burns()
    {
        return phase == Phase.LIT || phase == Phase.FAILING && !Cause.CONTRAVOID_DRY.in(causes) && !Cause.ESSENCE_DRY.in(causes);
    }

    public Phase phase()
    {
        return phase;
    }

    public long charge()
    {
        return charge;
    }

    /**
     * Ticks left before the light collapses, while warned.
     */
    public int countdown()
    {
        return countdown;
    }

    public int causes()
    {
        return causes;
    }

    public int collapse()
    {
        return collapse;
    }

    /**
     * Restores a saved cycle, putting any value out of range back in it, so a stale save never starts a reactor failing.
     */
    public void restore(Phase phase, long charge, int countdown, int causes, int collapse)
    {
        this.phase = phase;
        this.charge = Math.clamp(charge, 0, ReactorBalance.IGNITION);
        this.causes = causes & (Cause.CONTRAVOID_DRY.bit() | Cause.ESSENCE_DRY.bit() | Cause.BREACH.bit());
        this.countdown = Math.clamp(countdown, 0, ReactorBalance.COUNTDOWN);
        this.collapse = Math.clamp(collapse, 0, ReactorBalance.COLLAPSE);

        if (phase == Phase.FAILING && (this.causes == 0 || this.countdown == 0))
        {
            // A warning with nothing wrong or no time left can't be told apart from a bad save: it goes back to lit, never on to failing
            this.phase = Phase.LIT;
            this.causes = 0;
            this.countdown = 0;
        }
        else if (phase != Phase.FAILING)
        {
            this.causes = 0;
            this.countdown = 0;
        }
        if (phase == Phase.COLLAPSING && this.collapse == 0)
            this.collapse = 1;
    }
}
