package net.voxelindustry.voidheart.common.content.underway;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a section of a fort has been through: the cell in its cabinet, how much of its security an alarm or power has woken, and which of its units are wrecks.
 * <p>
 * Waking only ever grows. A unit that woke stays awake when the power goes, so powering a section is a commitment.
 */
public final class SectionState
{
    /** How much of a section's security is awake. */
    public enum Wake
    {
        /** Only its eyes watch. */
        NONE,
        /** An alarm woke what runs on the emergency circuit. */
        POST,
        /** Power woke everything. */
        ALL;

        private static final Wake[] VALUES = values();

        public static Wake of(int ordinal)
        {
            return VALUES[Math.clamp(ordinal, 0, VALUES.length - 1)];
        }
    }

    private boolean cell;
    private long charge;
    private Wake wake = Wake.NONE;
    private int alarm;
    private final Set<Integer> destroyed = new TreeSet<>();

    public SectionState()
    {
    }

    public SectionState(boolean cell, long charge, Wake wake, int alarm, Collection<Integer> destroyed)
    {
        this.cell = cell;
        this.charge = cell ? Math.max(0, charge) : 0;
        this.wake = wake;
        this.alarm = Math.max(0, alarm);
        this.destroyed.addAll(destroyed);
    }

    public boolean hasCell()
    {
        return cell;
    }

    public long charge()
    {
        return charge;
    }

    public Wake wake()
    {
        return wake;
    }

    public int alarm()
    {
        return alarm;
    }

    public List<Integer> destroyed()
    {
        return List.copyOf(destroyed);
    }

    /** @return whether a cell with charge left powers it */
    public boolean isLive()
    {
        return cell && charge > 0;
    }

    /** Sets a cell in its cabinet. With any charge in it the whole section wakes, for good. */
    public void insert(long cellCharge)
    {
        cell = true;
        charge = Math.max(0, cellCharge);
        if (isLive())
        {
            wake = Wake.ALL;
            alarm = SecurityBalance.ALARM_TICKS;
        }
    }

    /** @return the charge left in the cell taken out of its cabinet */
    public long remove()
    {
        long left = charge;
        cell = false;
        charge = 0;
        return left;
    }

    /** @return whether the cell just ran dry */
    public boolean drain()
    {
        if (!isLive())
            return false;
        charge = Math.max(0, charge - SecurityBalance.DRAIN_PER_TICK);
        return charge == 0;
    }

    /** Sounds the alarm: without power it wakes the emergency circuit, and it never puts anything back to sleep. */
    public void raiseAlarm()
    {
        alarm = SecurityBalance.ALARM_TICKS;
        if (wake == Wake.NONE)
            wake = Wake.POST;
    }

    public void tick()
    {
        if (alarm > 0)
            alarm--;
    }

    public boolean isAlarmed()
    {
        return alarm > 0;
    }

    /** @return whether a unit is awake, given whether it runs on the emergency circuit */
    public boolean isAwake(boolean emergency)
    {
        return wake == Wake.ALL || wake == Wake.POST && emergency;
    }

    /** @return whether that unit was not a wreck already */
    public boolean destroy(int unit)
    {
        return destroyed.add(unit);
    }

    /** @return whether that unit was a wreck, which a fitter just rebuilt */
    public boolean restore(int unit)
    {
        return destroyed.remove(unit);
    }

    /** @return whether its armoured doors are held shut: it has power and its alarm sounds */
    public boolean isLockedDown()
    {
        return isLive() && isAlarmed();
    }

    /** @return whether nothing of its security is left, given how many units it was built with */
    public boolean isSecured(int unitCount)
    {
        return destroyed.size() >= unitCount;
    }

    /** Stops the klaxon once nothing is left to sound it for. */
    public void standDown()
    {
        alarm = 0;
    }

    /** @return whether it holds nothing worth saving */
    public boolean isBlank()
    {
        return !cell && wake == Wake.NONE && alarm == 0 && destroyed.isEmpty();
    }
}
