package net.voxelindustry.voidheart.common.energy;

/**
 * Numbers owned by the energy foundation. Values marked PLACEHOLDER wait for docs/plans/energy-numbers.md.
 */
public final class FoundationBalance
{
    /**
     * From the plan: the creative void well projects a 16 block field.
     */
    public static final int CREATIVE_WELL_RADIUS = 16;
    // PLACEHOLDER: how near a block must be for the creative ley stone to power it
    public static final int CREATIVE_LEY_STONE_RADIUS = 8;

    // PLACEHOLDER: how close to a node or a line the default flow still counts as on it
    public static final int NODE_REACH = 8;
    public static final int LINE_REACH = 4;

    /**
     * From the plan: a node's taint runs from 0 to 100.
     */
    public static final float MAX_TAINT = 100;
    // PLACEHOLDER: the taint at which each stage begins
    public static final float TAINTED_FROM   = 25;
    public static final float FESTERING_FROM = 50;
    public static final float BREACHED_FROM  = 75;

    private FoundationBalance()
    {
    }
}
