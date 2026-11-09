package net.voxelindustry.voidheart.common.ley.flow;

/**
 * How much ley a node and its lines carry, from the node's raw power and what shapes it. Pure, so it is unit tested.
 */
public final class LeyFlowMath
{
    public static final int MOON_PHASES = 8;

    private LeyFlowMath()
    {
    }

    /**
     * Phase 0 is the full moon and phase 4 the new moon.
     */
    public static double tide(int moonPhase)
    {
        return 1 + LeyFlowBalance.TIDE * Math.cos(2 * Math.PI * Math.floorMod(moonPhase, MOON_PHASES) / MOON_PHASES);
    }

    public static double taintFactor(float taint)
    {
        return 1 - LeyFlowBalance.TAINT_FLOW_CUT * Math.clamp(taint, 0, LeyFlowBalance.MAX_TAINT) / LeyFlowBalance.MAX_TAINT;
    }

    public static double nodeFlow(double power, int moonPhase, boolean awakened, float taint)
    {
        return Math.max(0, power) * tide(moonPhase) * (awakened ? LeyFlowBalance.AWAKENED : 1) * taintFactor(taint);
    }

    /**
     * What one of a node's downstream lines carries: the node's flow left after the claims on the node itself, shared evenly.
     */
    public static double lineFlow(double nodeFlow, double claimedOnNode, int downstreamLines)
    {
        if (downstreamLines <= 0)
            return 0;
        return Math.max(0, nodeFlow - claimedOnNode) / downstreamLines;
    }
}
