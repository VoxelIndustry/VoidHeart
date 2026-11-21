package net.voxelindustry.voidheart.common.energy.storage;

import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;

/**
 * What a Rubrum Cell's habit changes in its socket. Pure.
 * <ul>
 *     <li>Searing lets go: let go of, it empties its whole charge in about five seconds.</li>
 *     <li>Still holds: it charges from the field whether its socket is powered or not, and never gives to it.</li>
 *     <li>Wrung drinks harm: what living things nearby are hurt by charges it, banked and taken in at a steady rate.</li>
 * </ul>
 */
public final class CellHabits
{
    public enum Way
    {
        IDLE,
        CHARGE,
        DISCHARGE
    }

    private CellHabits()
    {
    }

    /**
     * Which way a socket moves its cell's energy: in from the field while powered, out into it otherwise.
     */
    public static Way way(CrystalHabit habit, boolean powered, long charge)
    {
        if (habit == CrystalHabit.STILL || powered)
            return charge < RubrumBalance.CELL_CAPACITY ? Way.CHARGE : Way.IDLE;
        return charge > 0 ? Way.DISCHARGE : Way.IDLE;
    }

    public static long dischargeRate(CrystalHabit habit)
    {
        return habit == CrystalHabit.SEARING ? RubrumBalance.SEARING_BURST_RATE : RubrumBalance.CELL_SOCKET_RATE;
    }

    /**
     * What a Wrung cell banks from harm this deep, added to what it already holds and kept under its bank.
     */
    public static long bank(long banked, float damage)
    {
        if (!(damage > 0) || !Float.isFinite(damage))
            return banked;
        return Math.min(RubrumBalance.WRUNG_BANK, banked + Math.round(damage * RubrumBalance.WRUNG_PER_DAMAGE));
    }

    /**
     * What a Wrung cell takes in from its bank this tick, never past its capacity.
     */
    public static long drink(long banked, long charge)
    {
        return Math.max(0, Math.min(Math.min(banked, RubrumBalance.WRUNG_RATE), RubrumBalance.CELL_CAPACITY - charge));
    }
}
