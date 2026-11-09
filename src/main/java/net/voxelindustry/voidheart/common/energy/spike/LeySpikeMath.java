package net.voxelindustry.voidheart.common.energy.spike;

import java.util.function.LongUnaryOperator;

/**
 * One tick of an Azurium Spike: how much ley it reaches for, how much of it the network takes as void energy, and the Contravoid that comes out of a
 * tainted node. Pure, and unit tested.
 */
public final class LeySpikeMath
{
    // Below this, a credit is rounding noise rather than energy owed
    private static final double EPSILON = 1e-9;

    private LeySpikeMath()
    {
    }

    /**
     * @param ley    ley converted, which taints the node
     * @param energy void energy taken by the network
     * @param credit energy converted and not yet taken, always below one VE
     */
    public record Step(double ley, long energy, double credit)
    {
    }

    public static int clampSetting(int setting)
    {
        return Math.clamp(setting, 0, LeySpikeBalance.SHARES.length - 1);
    }

    public static double share(int setting)
    {
        return LeySpikeBalance.SHARES[clampSetting(setting)];
    }

    /**
     * @param flow the current flow where the spike stands, already cut by taint
     * @return the ley the spike reaches for: its share of the flow, never above {@link LeySpikeBalance#MAX_LEY}
     */
    public static double targetLey(double flow, int setting)
    {
        if (!(flow > 0))
            return 0;
        return Math.min(LeySpikeBalance.MAX_LEY, flow * share(setting));
    }

    /**
     * Offers the energy the granted ley could give, then converts only the ley that pays for what was taken. Ley flows rather than coming in whole
     * units, so when the network takes everything the whole grant is converted and the fraction of a VE left over is offered first next tick.
     *
     * @param granted ley the line would grant the spike this tick
     * @param credit  energy converted before and not yet taken
     * @param offer   pushes energy into the network and returns what it accepted, between 0 and the amount
     */
    public static Step step(double granted, double credit, LongUnaryOperator offer)
    {
        double grantedEnergy = Math.max(0, granted) * LeySpikeBalance.VE_PER_LEY;
        long potential = (long) Math.floor(credit + grantedEnergy + EPSILON);
        if (potential <= 0)
            return new Step(0, 0, credit);

        long accepted = Math.clamp(offer.applyAsLong(potential), 0, potential);
        if (accepted == potential)
            return new Step(Math.max(0, granted), accepted, Math.max(0, credit + grantedEnergy - accepted));

        double owed = accepted - credit;
        if (owed <= EPSILON)
            return new Step(0, accepted, credit - accepted);
        return new Step(Math.min(Math.max(0, granted), owed / LeySpikeBalance.VE_PER_LEY), accepted, 0);
    }

    /**
     * @param ley   ley converted this tick
     * @param taint the node's taint, 0 to 100
     */
    public static double contravoidPerTick(double ley, float taint)
    {
        if (!(ley > 0) || !(taint > 0))
            return 0;
        return ley * LeySpikeBalance.CONTRAVOID_PER_LEY * Math.min(taint, 100) / 100;
    }
}
