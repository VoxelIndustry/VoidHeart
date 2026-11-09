package net.voxelindustry.voidheart.client.render.energy.ley_taint;

import net.minecraft.util.ARGB;
import net.voxelindustry.voidheart.common.ley.TaintStage;
import net.voxelindustry.voidheart.common.ley.flow.LeyFlowBalance;
import net.voxelindustry.voidheart.common.ley.taint.LeyTaintMath;

/**
 * How taint looks at each stage, from docs/plans/energy-looks.md ("Taint on the ground" and "Old Anchor flicker").
 * Pure, so the preview and the unit tests use the same numbers as the game.
 */
public final class LeyTaintLook
{
    public static final int LEY_TEAL = 0x36C5B0;
    public static final int LEY_MAUVE = 0x8A6A94;
    public static final int BRUISE = 0x24122C;
    public static final int SEEP_DARK = 0x1A0F24;
    public static final int CONTRAVOID_LAVENDER = 0xE9DDFF;
    public static final int ASH = 0x2B2330;

    /**
     * How far veins reach from the node along each line, in blocks, at the start and end of each stage.
     */
    public static final float TAINTED_REACH = 3, TAINTED_REACH_END = 4.5F;
    public static final float FESTERING_REACH = 10, FESTERING_REACH_END = 16;
    public static final float BREACHED_REACH = 24, BREACHED_REACH_END = 32;
    /**
     * Width of a trunk vein in blocks, rim included.
     */
    public static final float TAINTED_WIDTH = 0.1F;
    public static final float FESTERING_WIDTH = 0.18F, FESTERING_WIDTH_END = 0.24F;
    public static final float BREACHED_WIDTH = 0.3F, BREACHED_WIDTH_END = 0.36F;
    /**
     * Alpha of the darkened ground between veins.
     */
    public static final float FESTERING_DARK = 0.16F, FESTERING_DARK_END = 0.22F;
    public static final float BREACHED_DARK = 0.34F;
    /**
     * Ticks a change of stage or taint step takes to show.
     */
    public static final int EASE_TICKS = 40;
    /**
     * Taint is drawn in steps of this size, so healing doesn't rebuild the veins every second.
     */
    public static final float TAINT_STEP = 2.5F;

    private LeyTaintLook()
    {
    }

    /**
     * What the veins around one node show, eased as a whole from one taint step to the next.
     *
     * @param branches how much of the branches show, 0 to 1
     * @param twigs    how much of the finest branches show, 0 to 1
     * @param breach   strength of the ash motes and Contravoid glints, 0 to 1
     */
    public record Shape(float reach, float width, float branches, float twigs, float dark, float breach)
    {
        public static final Shape NONE = new Shape(0, 0, 0, 0, 0, 0);

        public boolean isNone()
        {
            return reach <= 0.01F && width <= 0.001F;
        }

        public Shape lerp(Shape to, float t)
        {
            return new Shape(lerp(reach, to.reach, t), lerp(width, to.width, t), lerp(branches, to.branches, t),
                    lerp(twigs, to.twigs, t), lerp(dark, to.dark, t), lerp(breach, to.breach, t));
        }

        private static float lerp(float from, float to, float t)
        {
            return from + (to - from) * t;
        }

        public float levelWeight(int level)
        {
            return switch (level)
            {
                case 0 -> 1;
                case 1 -> branches;
                default -> twigs;
            };
        }
    }

    public static float stepped(float taint)
    {
        return Math.round(taint / TAINT_STEP) * TAINT_STEP;
    }

    public static Shape shape(float taint)
    {
        var stage = LeyTaintMath.stage(taint);
        return switch (stage)
        {
            case CLEAN -> Shape.NONE;
            case TAINTED ->
            {
                float f = within(taint, LeyFlowBalance.TAINTED_FROM, LeyFlowBalance.FESTERING_FROM);
                yield new Shape(mix(TAINTED_REACH, TAINTED_REACH_END, f), TAINTED_WIDTH, 0, 0, 0, 0);
            }
            case FESTERING ->
            {
                float f = within(taint, LeyFlowBalance.FESTERING_FROM, LeyFlowBalance.BREACHED_FROM);
                yield new Shape(mix(FESTERING_REACH, FESTERING_REACH_END, f), mix(FESTERING_WIDTH, FESTERING_WIDTH_END, f), 1, 0.5F * f,
                        mix(FESTERING_DARK, FESTERING_DARK_END, f), 0);
            }
            case BREACHED ->
            {
                float f = within(taint, LeyFlowBalance.BREACHED_FROM, LeyFlowBalance.MAX_TAINT);
                yield new Shape(mix(BREACHED_REACH, BREACHED_REACH_END, f), mix(BREACHED_WIDTH, BREACHED_WIDTH_END, f), 1, 1, BREACHED_DARK, 1);
            }
        };
    }

    /**
     * Tones of the Oculus ley layer: a node's or line's own color moves toward this by stage.
     */
    public static int oculusTone(TaintStage stage)
    {
        return switch (stage)
        {
            case CLEAN -> LEY_TEAL;
            case TAINTED -> LEY_MAUVE;
            case FESTERING -> ARGB.srgbLerp(0.5F, LEY_MAUVE, BRUISE);
            case BREACHED -> BRUISE;
        };
    }

    /**
     * How strongly the Oculus shows the tone over a node or line's own color.
     */
    public static float oculusStrength(TaintStage stage)
    {
        return switch (stage)
        {
            case CLEAN -> 0;
            case TAINTED -> 0.6F;
            case FESTERING -> 0.85F;
            case BREACHED -> 1;
        };
    }

    private static float within(float taint, float from, float to)
    {
        return Math.clamp((taint - from) / (to - from), 0, 1);
    }

    private static float mix(float from, float to, float t)
    {
        return from + (to - from) * t;
    }
}
