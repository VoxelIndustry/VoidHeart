package net.voxelindustry.voidheart.common.energy.superfluid;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * How superfluid held by a pearl winds into rings, and how full something holding it reads in words. Pure, and unit tested.
 * <p>
 * Rings fill from the innermost out, each closing once it holds a large bundle's worth, so taking superfluid away unwinds the outermost first.
 */
public final class SuperfluidRings
{
    /**
     * How full a pearl's rings or a tesseract read, never as a number.
     */
    public enum Fill implements StringRepresentable
    {
        EMPTY,
        THREAD,
        PART,
        MOST,
        FULL;

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Below this share of its capacity something holds only a thread. */
    static final double THREAD_BELOW = 0.25;
    /** Below this it is partly filled, above it mostly. */
    static final double PART_BELOW   = 0.6;

    private SuperfluidRings()
    {
    }

    /**
     * How far a ring has closed, from 0 to 1.
     *
     * @param units held, in units, fractional while drawn eased
     * @param ring  0 for the innermost
     */
    public static float closed(double units, int ring)
    {
        if (ring < 0 || ring >= SuperfluidBalance.RINGS)
            return 0;
        var into = units - (double) ring * SuperfluidBalance.RING_UNITS;
        return (float) Math.clamp(into / SuperfluidBalance.RING_UNITS, 0, 1);
    }

    /**
     * How many rings have begun, the last perhaps still open.
     */
    public static int begun(double units)
    {
        if (units <= 0)
            return 0;
        return Math.min(SuperfluidBalance.RINGS, (int) Math.ceil(units / SuperfluidBalance.RING_UNITS - 1E-9));
    }

    public static Fill fill(int units, int capacity)
    {
        if (units <= 0 || capacity <= 0)
            return Fill.EMPTY;
        if (units >= capacity)
            return Fill.FULL;
        var share = units / (double) capacity;
        if (share < THREAD_BELOW)
            return Fill.THREAD;
        if (share < PART_BELOW)
            return Fill.PART;
        return Fill.MOST;
    }
}
