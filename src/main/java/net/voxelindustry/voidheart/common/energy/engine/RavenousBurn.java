package net.voxelindustry.voidheart.common.energy.engine;

import org.jetbrains.annotations.Nullable;

import java.util.function.LongUnaryOperator;

/**
 * One tick of a Ravenous Engine's burn. It offers what its fuel could give, then burns only the essence that pays for what was taken, so nothing is
 * ever wasted: with nobody taking, it burns nothing.
 * <p>
 * Essence is whole but energy per essence isn't, so the energy an essence paid for beyond what was taken is kept as credit and offered first next tick.
 * Pure, and unit tested.
 */
public final class RavenousBurn
{
    // Below this, a credit is rounding noise rather than energy owed
    private static final double EPSILON = 1e-9;

    private RavenousBurn()
    {
    }

    /**
     * @param essence essence burned
     * @param energy  void energy taken by the network
     * @param credit  energy paid for and not yet taken, always below one essence's worth
     */
    public record Step(int essence, long energy, double credit)
    {
    }

    /**
     * @param stored essence of this fuel in the tank
     * @param credit energy left over from essence burned before
     * @param offer  pushes energy into the network and returns what it accepted, between 0 and the amount
     */
    public static Step step(EngineFuel fuel, int stored, double credit, LongUnaryOperator offer)
    {
        var burnable = Math.clamp(stored, 0, fuel.burn());
        var potential = (long) Math.floor(credit + burnable * fuel.density() + EPSILON);
        if (potential <= 0)
            return new Step(0, 0, credit);

        var accepted = Math.clamp(offer.applyAsLong(potential), 0, potential);
        var owed = accepted - credit;
        var essence = owed <= EPSILON ? 0 : Math.min(burnable, (int) Math.ceil(owed / fuel.density() - EPSILON));
        var left = credit + essence * fuel.density() - accepted;
        return new Step(essence, accepted, Math.max(0, left));
    }

    /**
     * The fuel to burn next: the one burning keeps going until it runs dry, then the fullest takes over, in declaration order on a tie.
     *
     * @param amounts essence held, indexed by {@link EngineFuel#ordinal()}
     */
    @Nullable
    public static EngineFuel choose(@Nullable EngineFuel burning, int[] amounts)
    {
        if (burning != null && amounts[burning.ordinal()] > 0)
            return burning;

        EngineFuel fullest = null;
        for (var fuel : EngineFuel.values())
        {
            if (amounts[fuel.ordinal()] > 0 && (fullest == null || amounts[fuel.ordinal()] > amounts[fullest.ordinal()]))
                fullest = fuel;
        }
        return fullest;
    }
}
