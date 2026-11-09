package net.voxelindustry.voidheart.client.render.energy.fissure;

import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.common.energy.fissure.FissureAnimation;

/**
 * The outline of a crack in its own frame: {@code along} its axis from the block's centre, and {@code across} it, for each joint. Its shape is seeded by
 * the crack's position so it is the same for everyone, and its edges move only by the animation's crawl.
 */
final class CrackShape
{
    static final int JOINTS = FissureAnimation.JOINTS;

    // A fissure runs from a 1 block hairline at width 1 to about 5 blocks long and 1 wide at width 5
    private static final float LENGTH_BASE     = 1;
    private static final float LENGTH_PER_WIDTH = 1;
    private static final float HALF_BASE       = 0.05F;
    private static final float HALF_PER_WIDTH  = 0.11F;
    // A wound is ragged, and wider than any fissure stage
    static final float         WOUND_LENGTH    = 6.5F;
    static final float         WOUND_HALF      = 0.62F;

    private static final float TAPER_POWER     = 0.55F;
    // The middle of the crack meanders in two slow waves, with a little jitter at each joint
    private static final float MEANDER         = 0.55F;
    private static final float MEANDER_MIN     = 0.06F;
    private static final float JOINT_WANDER    = 0.25F;
    private static final float EDGE_JAG        = 0.45F;
    private static final float WOUND_EDGE_JAG  = 0.45F;
    private static final float ALONG_JITTER    = 0.35F;
    // How far from the closing front the crack is fully open
    private static final float CLOSE_SOFTNESS  = 0.15F;

    final float[] along  = new float[JOINTS];
    final float[] center = new float[JOINTS];
    final float[] left   = new float[JOINTS];
    final float[] right  = new float[JOINTS];
    /**
     * 1 where the crack is open, falling to 0 where it has drawn shut.
     */
    final float[] open   = new float[JOINTS];

    float length;

    static float fissureLength(float width)
    {
        return width <= 0 ? 0 : LENGTH_BASE + LENGTH_PER_WIDTH * (width - 1);
    }

    static float fissureHalf(float width)
    {
        return width <= 0 ? 0 : HALF_BASE + HALF_PER_WIDTH * (width - 1);
    }

    interface Jag
    {
        /**
         * @return -1 to 1, how far this edge's joint is pushed out of its rest
         */
        float at(int joint, int edge);
    }

    /**
     * @param closed how far the crack has drawn shut from both ends toward the middle, 0 to 1
     */
    void build(long seed, float length, float half, boolean wound, Jag jag, float closed)
    {
        this.length = length;
        float edgeJag = wound ? WOUND_EDGE_JAG : EDGE_JAG;
        float spacing = length / (JOINTS - 1);
        for (int i = 0; i < JOINTS; i++)
        {
            float u = i / (float) (JOINTS - 1) * 2 - 1;
            float jitter = i == 0 || i == JOINTS - 1 ? 0 : (FissureLook.hash(seed, i, 1) - 0.5F) * ALONG_JITTER * spacing;
            along[i] = u * length / 2 + jitter;

            float taper = (float) Math.pow(Math.max(0, 1 - u * u), TAPER_POWER);
            float amplitude = MEANDER * half + MEANDER_MIN * Math.min(1, length);
            float wave = 0.65F * Mth.sin(u * Mth.PI * (1.2F + FissureLook.hash(seed, 0, 5)) + Mth.TWO_PI * FissureLook.hash(seed, 0, 6))
                    + 0.35F * Mth.sin(u * Mth.PI * (2.6F + FissureLook.hash(seed, 0, 7)) + Mth.TWO_PI * FissureLook.hash(seed, 0, 8));
            float zig = (wave + JOINT_WANDER * (FissureLook.hash(seed, i, 2) * 2 - 1)) * amplitude * (0.35F + 0.65F * taper);
            float wobble = 0.7F + 0.6F * FissureLook.hash(seed, i, 3);
            if (wound)
                wobble *= 0.75F + 0.5F * FissureLook.hash(seed, i, 4);

            open[i] = Mth.clamp(((1 - closed) - Math.abs(u)) / CLOSE_SOFTNESS, 0, 1);
            if (closed <= 0)
                open[i] = 1;
            float edge = half * taper * wobble * open[i];
            center[i] = zig;
            left[i] = zig + Math.max(0.004F * open[i], edge * (1 + edgeJag * jag.at(i, 0)));
            right[i] = zig - Math.max(0.004F * open[i], edge * (1 + edgeJag * jag.at(i, 1)));
        }
    }
}
