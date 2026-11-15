package net.voxelindustry.voidheart.client.render.temple;

import net.minecraft.util.Mth;

/**
 * The tendrils hanging from the flesh, as they come loose. While it stirs each lifts off the wall and sways from its root. When it reaches, each peels away tip
 * first and grows out along a curve toward the player's face, stopping {@value #STOP_SHORT} pixels short of it, each a little later and shorter than the last.
 * <p>
 * Places are in the picture's pixels: {@code u} rightward and {@code v} downward from its top left, and {@code h} out of the wall.
 */
public final class MosaicTendrils
{
    /** How far a stirring tendril stands off the wall, and how far its tip sways, in pixels. */
    static final float LIFT = 1.5F;
    static final float SWAY = 2F;
    static final float SWAY_RATE = 0.09F;
    /** It stops a block and a half short of the player's eyes, and never grows past this, in pixels. */
    static final float STOP_SHORT = 24F;
    static final float MAX_REACH = 88F;
    /** With less room than this before its clearance, a tendril only partly comes away from the wall. */
    static final float MIN_REACH = 8F;
    /** The shortest tendril reaches this share of the longest. */
    static final float MIN_REACH_SHARE = 0.6F;
    /** The last tendril starts this share of the reach after the first. */
    static final float DELAY = 0.2F;
    /** Its tip peels away this share of the reach before its root does. */
    static final float PEEL_SPAN = 0.55F;
    static final float WOBBLE = 3F;
    static final float WOBBLE_RATE = 0.12F;
    static final float WOBBLE_WAVES = 2.5F;

    private MosaicTendrils()
    {
    }

    /** Whether a tendril's root is still on the wall, so it can come loose at all. */
    public static boolean rooted(MosaicArt art, int[] wear, int tendril)
    {
        var path = art.tendrils().get(tendril);
        return wear[MosaicArt.tileOf(MosaicArt.index(path.x()[0], path.y()[0]))] != MosaicWall.FALLEN;
    }

    /**
     * @param rootLift how far the flesh it hangs from stands out of the wall
     * @return each point along it from root to tip, as {@code u, v, h} and its width there, all in pixels
     */
    public static float[][] shape(MosaicArt art, int tendril, float stir, float reach, float time, long seed, double playerU, double playerV, double playerH, float rootLift)
    {
        var path = art.tendrils().get(tendril);
        int length = path.length();
        float own = MosaicWake.ease(reach - (float) MosaicArt.hash(tendril, 21, seed) * DELAY, 0, 1 - DELAY);
        float share = MIN_REACH_SHARE + (1 - MIN_REACH_SHARE) * (float) MosaicArt.hash(tendril, 22, seed);
        float phase = (float) (MosaicArt.hash(tendril, 23, seed) * Math.PI * 2);

        double rootU = path.x()[0] + 0.5;
        double rootV = path.y()[0] + 0.5;
        double toU = playerU - rootU;
        double toV = playerV - rootV;
        double toH = playerH - rootLift;
        double distance = Math.max(1E-3, Math.sqrt(toU * toU + toV * toV + toH * toH));
        double dirU = toU / distance, dirV = toV / distance, dirH = toH / distance;
        // With the player close to it, a tendril has only the room left before its clearance, and with less than a little room it stays on the wall
        double reachTo = Mth.clamp(distance - STOP_SHORT, 0, MAX_REACH * share);
        own *= (float) Math.min(1, reachTo / MIN_REACH);
        // Out of the wall and down a little first, then sagging into the last stretch toward the player
        double[] a = {rootU, rootV, rootLift};
        double[] b = {rootU, rootV + 0.25 * reachTo, rootLift + 0.5 * reachTo};
        double[] d = {rootU + dirU * reachTo, rootV + dirV * reachTo, rootLift + dirH * reachTo};
        double[] c = {d[0] - dirU * 0.35 * reachTo, d[1] - dirV * 0.35 * reachTo + 0.2 * reachTo, d[2] - dirH * 0.35 * reachTo};
        // Across the way it reaches, level with the wall: the direction crossed with up, which is -v
        double sideU = -dirH, sideH = dirU;
        double side = Math.sqrt(sideU * sideU + sideH * sideH);
        if (side < 1E-3)
        {
            sideU = 1;
            sideH = 0;
        }
        else
        {
            sideU /= side;
            sideH /= side;
        }

        var points = new float[length][4];
        for (int s = 0; s < length; s++)
        {
            float tip = length > 1 ? s / (float) (length - 1) : 1;
            float sway = SWAY * stir * tip * Mth.sin(time * SWAY_RATE + phase + s * 0.25F);
            double paintedU = path.x()[s] + 0.5 + sway;
            double paintedV = path.y()[s] + 0.5;
            double paintedH = Math.max(LIFT * stir, rootLift * Math.max(0, 1 - tip * 3));

            float peel = MosaicWake.ease(own - (1 - tip) * PEEL_SPAN, 0, 1 - PEEL_SPAN);
            double wobble = WOBBLE * Math.sin(tip * Math.PI) * Math.sin(tip * Math.PI * WOBBLE_WAVES + time * WOBBLE_RATE + phase) * (1 - 0.5 * own);
            double freeU = bezier(a[0], b[0], c[0], d[0], tip) + sideU * wobble;
            double freeV = bezier(a[1], b[1], c[1], d[1], tip);
            double freeH = bezier(a[2], b[2], c[2], d[2], tip) + sideH * wobble;

            points[s][0] = (float) Mth.lerp(peel, paintedU, freeU);
            points[s][1] = (float) Mth.lerp(peel, paintedV, freeV);
            points[s][2] = (float) Mth.lerp(peel, paintedH, freeH);
            int width = 2 * path.halfWidth()[s] + 1;
            if (s >= length - 3)
                width = Math.max(1, width - (s - (length - 4)));
            points[s][3] = width;
        }
        return points;
    }

    private static double bezier(double a, double b, double c, double d, double t)
    {
        double u = 1 - t;
        return a * u * u * u + 3 * b * u * u * t + 3 * c * u * t * t + d * t * t * t;
    }
}
