package net.voxelindustry.voidheart.common.content.armillary;

/**
 * How the rings move from one tick to the next, shared by a real armillary and the research scene that films one, so the two never drift apart.
 */
public final class ArmillaryMotion
{
    public static final int RINGS = 3;

    /** How fast the rings turn while scanning or holding a way open, and the speeds the rest are measured against. */
    public static final float STEADY_SPIN    = 1;
    public static final float SEARCHING_SPIN = 3;
    public static final float REACHED_SPIN   = 0.5F;

    private static final float[] RING_SPEEDS       = { 0.012F, -0.021F, 0.034F };
    private static final float   SPEED_EASE        = 0.02F;
    private static final float   FORM_STEP         = 1 / 60F;
    private static final float   FALTER_SPEED_DROP = 0.6F;
    /** Ticks the inner ring takes to go cold once its power fails, and to light again once it comes back. */
    private static final float   COOLING_TICKS     = 40;
    private static final float   WARMING_TICKS     = 20;

    private ArmillaryMotion()
    {
    }

    public static float ringSpeed(int ring)
    {
        return RING_SPEEDS[ring];
    }

    /**
     * The rings rise in three seconds once the structure stands, and sink as fast when it breaks.
     */
    public static float form(float formation, boolean formed)
    {
        float wanted = formed ? 1 : 0;
        return formation + Math.clamp(wanted - formation, -FORM_STEP, FORM_STEP);
    }

    public static float easeSpin(float spin, float wanted)
    {
        return spin + (wanted - spin) * SPEED_EASE;
    }

    /**
     * Slows a wanted spin as a failing connection makes the rings falter.
     */
    public static float faltering(float spin, float falter)
    {
        return spin * (1 - falter * FALTER_SPEED_DROP);
    }

    public static float warm(float warmth, float wanted)
    {
        return warmth + (wanted < warmth ? Math.max(wanted - warmth, -1 / COOLING_TICKS) : Math.min(wanted - warmth, 1 / WARMING_TICKS));
    }
}
