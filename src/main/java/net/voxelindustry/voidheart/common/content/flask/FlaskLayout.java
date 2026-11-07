package net.voxelindustry.voidheart.common.content.flask;

/**
 * Where placed flasks stand on their block, in pixels from its north-west corner, and how each is turned. Spaced so neighbours' bases clear each
 * other at the placed scale even turned by their full tilt.
 */
public final class FlaskLayout
{
    public static final int MAX_FLASKS = 4;

    /**
     * Size of a placed flask against its item model, which is drawn a block tall.
     */
    public static final float PLACED_SCALE = 0.6F;

    /**
     * Half the width of the widest flask model, the elixir's base, in model pixels.
     */
    public static final double WIDEST_HALF = 5;

    /**
     * Largest turn of a flask away from its neighbours' alignment, in degrees, either way.
     */
    public static final float MAX_TURN = 10;

    private static final double[][][] SPOTS = {
            {{8, 8}},
            {{4.5, 8}, {11.5, 8}},
            {{4.5, 5}, {11.5, 5}, {8, 12}},
            {{4.5, 4.5}, {11.5, 4.5}, {4.5, 11.5}, {11.5, 11.5}}
    };

    private FlaskLayout()
    {
    }

    /**
     * The x and z of each of the given number of flasks.
     */
    public static double[][] spots(int count)
    {
        if (count < 1 || count > MAX_FLASKS)
            throw new IllegalArgumentException("A block holds 1 to " + MAX_FLASKS + " flasks, not " + count);
        return SPOTS[count - 1];
    }

    /**
     * A steady turn for one of the given number of flasks, the same every frame and on every client. A lone flask has no neighbour to clear, so it
     * may face any way.
     */
    public static float turn(long seed, int index, int count)
    {
        long mixed = (seed + index * 0x9E3779B97F4A7C15L) * 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 31;
        double unit = (mixed >>> 11) / (double) (1L << 53);
        return (float) ((unit * 2 - 1) * (count == 1 ? 180 : MAX_TURN));
    }
}
