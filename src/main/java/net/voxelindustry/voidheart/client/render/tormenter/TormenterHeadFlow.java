package net.voxelindustry.voidheart.client.render.tormenter;

import net.minecraft.util.Mth;

/**
 * Where a head sits in the droplet as the flesh carries it up. Most of it stays drowned in the mass, only its face breaks the
 * surface. Every value depends on how far it has risen alone, so that heads need no state.
 */
final class TormenterHeadFlow
{
    /**
     * The side of a head at the belly of the droplet.
     */
    static final float SIZE = 0.8F;

    /**
     * Share of a head that stands out of the flesh.
     */
    static final float STANDING_OUT = 0.12F;

    /**
     * How far from the middle of a side of the droplet a column of heads runs at the belly.
     */
    static final float COLUMN_OFFSET = 0.36F;

    // Width at which a head has its full size, as a share of the droplet's widest
    private static final float FULL_SIZE_AT = 1F;

    private static final float SURFACES_UNTIL = 0.12F;
    private static final float SLOPE_STEP     = 0.02F;

    // Share of the slope of the surface a face follows, more would lift the back of the head out of the flesh
    private static final float TILT_SHARE = 0.4F;

    private TormenterHeadFlow()
    {
    }

    /**
     * Grows with the width of the flesh around it, so that it goes under where the droplet narrows at both ends.
     */
    static float size(float rise)
    {
        var fit = Mth.clamp(TormenterDroplet.halfWidth(rise) / (TormenterDroplet.MOST_WIDTH * FULL_SIZE_AT), 0, 1);
        var surfacing = Mth.clamp(rise / SURFACES_UNTIL, 0, 1);
        return SIZE * fit * surfacing * surfacing * (3 - 2 * surfacing);
    }

    /**
     * @return distance from the axis of the droplet to the middle of the head, along the side it looks out of
     */
    static float depth(float rise)
    {
        var size = size(rise);
        return Math.max(0, TormenterDroplet.halfWidth(rise) + size * STANDING_OUT - size / 2);
    }

    /**
     * @return distance from the middle of a side to the middle of the head, its columns close in as the droplet narrows
     */
    static float lateral(float rise)
    {
        return COLUMN_OFFSET * TormenterDroplet.halfWidth(rise) / TormenterDroplet.MOST_WIDTH;
    }

    static float height(float rise)
    {
        return rise * TormenterDroplet.HEIGHT;
    }

    /**
     * @return radians the face looks up by to follow the slope of the surface, negative where it looks down
     */
    static float tilt(float rise)
    {
        var run = TormenterDroplet.halfWidth(rise + SLOPE_STEP) - TormenterDroplet.halfWidth(rise - SLOPE_STEP);
        return TILT_SHARE * (float) Math.atan2(-run, 2 * SLOPE_STEP * TormenterDroplet.HEIGHT);
    }
}
