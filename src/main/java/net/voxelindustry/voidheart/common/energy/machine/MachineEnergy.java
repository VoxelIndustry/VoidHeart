package net.voxelindustry.voidheart.common.energy.machine;

import net.voxelindustry.voidheart.common.world.dungeon.DungeonSize;

import static net.voxelindustry.voidheart.common.energy.machine.MachineEnergyBalance.*;

/**
 * The machines' costs, apart from the machines so they can be tested without a level.
 */
public final class MachineEnergy
{
    private MachineEnergy()
    {
    }

    public static long dungeonOpening(DungeonSize size)
    {
        return switch (size)
        {
            case SMALL -> ARMILLARY_OPEN_SMALL;
            case MEDIUM -> ARMILLARY_OPEN_MEDIUM;
            case LARGE -> ARMILLARY_OPEN_LARGE;
        };
    }

    /**
     * @param aspects    every aspect point the recipe asks for
     * @param refinements how many of empowered, prolonged and remembering the brew is
     */
    public static long brew(int aspects, int refinements)
    {
        return Math.round(Math.max(0, aspects) * ATHANOR_PER_ASPECT * Math.pow(ATHANOR_REFINE, Math.max(0, refinements)));
    }

    /**
     * The part of a cost due on one tick of a run, so that the ticks of the whole run add up to the cost exactly.
     *
     * @param tick from 0 to ticks - 1
     */
    public static long shareOfTick(long cost, int ticks, int tick)
    {
        if (cost <= 0 || ticks <= 0 || tick < 0 || tick >= ticks)
            return 0;
        return cost * (tick + 1) / ticks - cost * tick / ticks;
    }

    /**
     * How much of its range a Beholder sees with this share of the ley it needs.
     */
    public static float beholderSight(float supply)
    {
        return supply < BEHOLDER_MIN_SUPPLY ? 0 : Math.min(1, supply);
    }

    /**
     * The whole number percent of a charge, which only shows 100 once it is complete.
     */
    public static int percent(long charge, long cost)
    {
        if (cost <= 0 || charge >= cost)
            return 100;
        return (int) Math.min(99, Math.max(0, charge * 100 / cost));
    }
}
