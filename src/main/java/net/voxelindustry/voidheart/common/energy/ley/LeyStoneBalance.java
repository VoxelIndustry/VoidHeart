package net.voxelindustry.voidheart.common.energy.ley;

/**
 * Numbers for standing stones, wards and growers, from docs/plans/energy-numbers.md (N0). Values marked PLACEHOLDER aren't in that table yet and wait for I1.
 */
public final class LeyStoneBalance
{
    /**
     * N0, sim: how far a stone reaches, both to tap a line and to chain to the next stone. Measured across the ground, as the lattice is.
     */
    public static final int STONE_REACH = 32;

    // PLACEHOLDER: how near a machine must stand to a connected stone to draw from its spur, matching the creative ley stone
    public static final int STONE_SUPPLY_RADIUS = 8;

    // PLACEHOLDER: how many ticks a machine's need stays on its spur after it last asked, so one that asks every few ticks still counts
    public static final int DEMAND_TICKS = 20;

    /**
     * N0, guess: the ley a ward asks for.
     */
    public static final double WARD_NEED = 1.5;
    /**
     * N0, guess: at full strength, corruption rises at half speed.
     */
    public static final float WARD_SLOW = 0.5F;
    /**
     * N0, guess: strength = supply × (1 − 0.8 × line taint/100) × (1 − seep strength).
     */
    public static final float WARD_TAINT_CUT = 0.8F;
    // PLACEHOLDER: how far a ward holds the air still, enough for a base
    public static final int WARD_RADIUS = 16;

    /**
     * N0, guess: the ley a grower asks for.
     */
    public static final double GROWER_NEED = 1.0;
    /**
     * Ruled by the user: at full supply crops and saplings grow about twice as fast, so the grower adds one more random tick's worth.
     */
    public static final float GROWER_BOOST = 1.0F;
    /**
     * Ruled by the user: 4 blocks each way, a 9x9 farm plot.
     */
    public static final int GROWER_RADIUS = 4;
    // PLACEHOLDER: how far above and below the basin plants count, enough for cane and a sloped field
    public static final int GROWER_HEIGHT = 2;

    private LeyStoneBalance()
    {
    }
}
