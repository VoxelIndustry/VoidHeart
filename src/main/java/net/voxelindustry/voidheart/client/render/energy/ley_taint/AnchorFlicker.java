package net.voxelindustry.voidheart.client.render.energy.ley_taint;

import net.voxelindustry.voidheart.common.ley.TaintStage;

import java.util.SplittableRandom;

/**
 * When a tainted Old Anchor gutters from ley teal toward its dormant mauve and back, like a candle in a draft: uneven,
 * random intervals, each dip easing in and out over a few ticks. From Breached the anchor stays mostly mauve, with short
 * returns to teal. Pure, so it is unit tested.
 */
public final class AnchorFlicker
{
    /**
     * Ticks between the starts of two dips, and how long each lasts.
     */
    public static final int TAINTED_GAP_MIN = 100, TAINTED_GAP_MAX = 200;
    public static final int TAINTED_LENGTH_MIN = 6, TAINTED_LENGTH_MAX = 12;
    public static final float TAINTED_DEPTH_MIN = 0.55F, TAINTED_DEPTH_MAX = 0.85F;
    public static final int FESTERING_GAP_MIN = 20, FESTERING_GAP_MAX = 60;
    public static final int FESTERING_LENGTH_MIN = 10, FESTERING_LENGTH_MAX = 24;
    public static final float FESTERING_DEPTH_MIN = 0.75F, FESTERING_DEPTH_MAX = 1;
    /**
     * From Breached the dips are the short returns to teal.
     */
    public static final int BREACHED_GAP_MIN = 30, BREACHED_GAP_MAX = 90;
    public static final int BREACHED_LENGTH_MIN = 5, BREACHED_LENGTH_MAX = 12;
    public static final float BREACHED_DEPTH_MIN = 0.6F, BREACHED_DEPTH_MAX = 0.9F;
    /**
     * Ticks a dip takes to ease in, and again to ease out.
     */
    public static final float EASE = 3;
    /**
     * Ticks the anchor takes to settle into mostly mauve on breaching, or back out of it.
     */
    public static final float BREACH_EASE = 20;

    private final SplittableRandom random;
    private TaintStage stage = TaintStage.CLEAN;
    private long dipStart = Long.MIN_VALUE;
    private int dipLength;
    private float dipDepth;
    private long nextDip = Long.MIN_VALUE;
    private float baseFrom;
    private float baseTo;
    private long baseChange = Long.MIN_VALUE;

    public AnchorFlicker(long seed)
    {
        random = new SplittableRandom(seed);
    }

    /**
     * Plans the next dip once the last has started. A change of stage replans at once.
     */
    public void tick(long now, TaintStage stage)
    {
        if (stage != this.stage)
        {
            baseFrom = base(now);
            baseTo = stage == TaintStage.BREACHED ? 1 : 0;
            baseChange = now;
            this.stage = stage;
            nextDip = now + (stage == TaintStage.CLEAN ? 0 : random.nextInt(gapMin(stage), gapMax(stage) + 1) / 2);
        }
        if (stage == TaintStage.CLEAN || now < nextDip)
            return;

        dipStart = now;
        dipLength = random.nextInt(lengthMin(stage), lengthMax(stage) + 1);
        dipDepth = (float) random.nextDouble(depthMin(stage), depthMax(stage));
        // A dip always ends before the next begins, so one never cuts another short
        nextDip = now + Math.max(random.nextInt(gapMin(stage), gapMax(stage) + 1), dipLength + 2);
    }

    /**
     * 0 shows the anchor's own teal, 1 its dormant mauve.
     */
    public float mauve(double time)
    {
        float base = base(time);
        float dip = dip(time);
        // Before breaching a dip moves toward mauve, after it toward teal
        return stage == TaintStage.BREACHED ? base * (1 - dip) : base + (1 - base) * dip;
    }

    private float base(double time)
    {
        if (baseChange == Long.MIN_VALUE)
            return baseTo;
        double t = Math.clamp((time - baseChange) / BREACH_EASE, 0, 1);
        return (float) (baseFrom + (baseTo - baseFrom) * t);
    }

    private float dip(double time)
    {
        double into = time - dipStart;
        if (dipStart == Long.MIN_VALUE || into < 0 || into > dipLength)
            return 0;
        double ease = Math.min(EASE, dipLength / 2D);
        double edge = Math.min(into, dipLength - into);
        double t = Math.clamp(edge / ease, 0, 1);
        return (float) (dipDepth * t * t * (3 - 2 * t));
    }

    private static int gapMin(TaintStage stage)
    {
        return switch (stage)
        {
            case TAINTED -> TAINTED_GAP_MIN;
            case FESTERING -> FESTERING_GAP_MIN;
            default -> BREACHED_GAP_MIN;
        };
    }

    private static int gapMax(TaintStage stage)
    {
        return switch (stage)
        {
            case TAINTED -> TAINTED_GAP_MAX;
            case FESTERING -> FESTERING_GAP_MAX;
            default -> BREACHED_GAP_MAX;
        };
    }

    private static int lengthMin(TaintStage stage)
    {
        return switch (stage)
        {
            case TAINTED -> TAINTED_LENGTH_MIN;
            case FESTERING -> FESTERING_LENGTH_MIN;
            default -> BREACHED_LENGTH_MIN;
        };
    }

    private static int lengthMax(TaintStage stage)
    {
        return switch (stage)
        {
            case TAINTED -> TAINTED_LENGTH_MAX;
            case FESTERING -> FESTERING_LENGTH_MAX;
            default -> BREACHED_LENGTH_MAX;
        };
    }

    private static float depthMin(TaintStage stage)
    {
        return switch (stage)
        {
            case TAINTED -> TAINTED_DEPTH_MIN;
            case FESTERING -> FESTERING_DEPTH_MIN;
            default -> BREACHED_DEPTH_MIN;
        };
    }

    private static float depthMax(TaintStage stage)
    {
        return switch (stage)
        {
            case TAINTED -> TAINTED_DEPTH_MAX;
            case FESTERING -> FESTERING_DEPTH_MAX;
            default -> BREACHED_DEPTH_MAX;
        };
    }
}
