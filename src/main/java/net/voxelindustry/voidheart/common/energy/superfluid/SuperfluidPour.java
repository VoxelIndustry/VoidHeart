package net.voxelindustry.voidheart.common.energy.superfluid;

/**
 * One tick of a cradle of azurium pouring into a mature pearl. Pure, and unit tested.
 * <p>
 * The cradle keeps its shards whole: the units of the shard now pouring are a credit against it, and the shard only leaves the cradle once all of
 * them have reached the pearl. A pearl never takes more than it has room for, so azurium is never lost on the way, only once a shard has started
 * pouring and the cradle is emptied before it finishes.
 */
public final class SuperfluidPour
{
    /**
     * @param poured  units that reached the pearl this tick
     * @param credit  units of the cradle's next shard already poured
     * @param drained whole shards that have left the cradle this tick
     */
    public record Step(int poured, int credit, int drained)
    {
    }

    private SuperfluidPour()
    {
    }

    /**
     * @param credit units of the shard now pouring already given
     * @param worth  shards the cradle holds, the one pouring included
     * @param room   units the pearl still has room for
     */
    public static Step tick(int credit, int worth, int room)
    {
        return pour(credit, worth, room, SuperfluidBalance.POUR_PER_TICK);
    }

    /**
     * Pours up to that many units at once, as a tick does with {@link SuperfluidBalance#POUR_PER_TICK}.
     */
    public static Step pour(int credit, int worth, int room, int units)
    {
        credit = Math.clamp(credit, 0, SuperfluidBalance.SHARD_UNITS - 1);
        if (worth <= 0 || room <= 0 || units <= 0)
            return new Step(0, credit, 0);
        var left = (long) worth * SuperfluidBalance.SHARD_UNITS - credit;
        var poured = (int) Math.min(units, Math.min(room, left));
        var total = credit + poured;
        var drained = total / SuperfluidBalance.SHARD_UNITS;
        return new Step(poured, total - drained * SuperfluidBalance.SHARD_UNITS, drained);
    }

    /**
     * Shards a cradle gives back when emptied: the one already pouring is spent, since part of it is in the pearl.
     */
    public static int kept(int worth, int credit)
    {
        return Math.max(0, credit > 0 ? worth - 1 : worth);
    }

    /**
     * Shards' worth a cradle still holds, counting what is left of the one pouring.
     */
    public static double held(int worth, int credit)
    {
        return Math.max(0, worth - credit / (double) SuperfluidBalance.SHARD_UNITS);
    }
}
