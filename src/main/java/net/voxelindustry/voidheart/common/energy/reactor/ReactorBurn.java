package net.voxelindustry.voidheart.common.energy.reactor;

import java.util.function.LongUnaryOperator;

/**
 * One tick of a lit reactor's burn. It offers what its fullest burn could give, then cancels as many pairs as the network took, never fewer than
 * {@link ReactorBalance#MIN_PAIRS}. What the minimum makes beyond what was taken is lost.
 * <p>
 * Fuel comes out of the intakes as whole pairs, so the part of a pair taken and not yet burned is kept as credit and burned first. Pure, and unit tested.
 */
public final class ReactorBurn
{
    private static final double EPSILON = 1e-9;

    private ReactorBurn()
    {
    }

    /**
     * @param pairs  whole pairs taken from the intakes: that much Contravoid and that much essence
     * @param energy void energy the network took
     * @param burned pairs cancelled this tick
     * @param credit part of a pair taken and not yet burned, always below one
     */
    public record Step(int pairs, long energy, double burned, double credit)
    {
        public static final Step NONE = new Step(0, 0, 0, 0);
    }

    /**
     * @param contravoid Contravoid held
     * @param essence    essence held
     * @param credit     part of a pair taken before and not yet burned
     * @param offer      pushes energy into the network and returns what it accepted, between 0 and the amount
     */
    public static Step step(ReactorEssence fuel, int contravoid, int essence, double credit, LongUnaryOperator offer)
    {
        var held = Math.max(0, Math.min(contravoid, essence));
        var possible = Math.min(ReactorBalance.MAX_PAIRS, credit + held);
        if (possible <= EPSILON)
            return new Step(0, 0, 0, credit);

        var pairEnergy = fuel.pairEnergy();
        var potential = (long) Math.floor(possible * pairEnergy + EPSILON);
        var accepted = potential <= 0 ? 0 : Math.clamp(offer.applyAsLong(potential), 0, potential);

        var burned = Math.min(possible, Math.max(ReactorBalance.MIN_PAIRS, accepted / pairEnergy));
        var owed = burned - credit;
        var taken = owed <= EPSILON ? 0 : Math.min(held, (int) Math.ceil(owed - EPSILON));
        var left = credit + taken - burned;
        return new Step(taken, accepted, burned, Math.clamp(left, 0, 1 - EPSILON));
    }
}
