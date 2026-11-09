package net.voxelindustry.voidheart.common.energy.fissure;

import net.minecraft.util.Mth;

import static net.voxelindustry.voidheart.common.energy.fissure.VoidFissureBalance.*;

/**
 * The pure rules of fissures, wounds and their seep. A width may be fractional for a wound, whose size the reactor chooses, and is held between 1 and
 * {@link VoidFissureBalance#MAX_WIDTH}.
 */
public final class SeepMath
{
    public static final int TICKS_PER_MINUTE = 1200;

    private SeepMath()
    {
    }

    public static float clampWidth(float width)
    {
        return Float.isNaN(width) ? 1 : Mth.clamp(width, 1, MAX_WIDTH);
    }

    /**
     * VE/t a fissure offers. Wounds give none.
     */
    public static long output(int width)
    {
        return OUTPUT[Mth.clamp(width, 1, MAX_WIDTH) - 1];
    }

    /**
     * The radius the seep spreads to at this width, between the table's widths for a fractional one.
     */
    public static float targetRadius(float width)
    {
        width = clampWidth(width);
        int below = (int) Math.floor(width);
        if (below >= MAX_WIDTH)
            return SEEP_RADIUS[MAX_WIDTH - 1];
        return Mth.lerp(width - below, SEEP_RADIUS[below - 1], SEEP_RADIUS[below]);
    }

    /**
     * The seep's radius now, spreading from where it stood when last torn wider at {@link VoidFissureBalance#SEEP_GROWTH_PER_MINUTE} until its target.
     */
    public static float radiusAt(float from, long since, long now, float target)
    {
        if (from >= target)
            return target;
        float grown = Math.max(0, now - since) / (float) TICKS_PER_MINUTE * SEEP_GROWTH_PER_MINUTE;
        return Math.min(target, Math.max(0, from) + grown);
    }

    /**
     * 1 at the fissure, falling linearly to 0 at the seep's edge.
     */
    public static float strength(double distance, float radius)
    {
        if (!(radius > 0) || distance >= radius)
            return 0;
        return (float) Mth.clamp(1 - distance / radius, 0, 1);
    }

    /**
     * Two overlapping seeps: each takes its share of what the other leaves, so they add up toward 1 without passing it.
     */
    public static float combine(float a, float b)
    {
        return 1 - (1 - Mth.clamp(a, 0, 1)) * (1 - Mth.clamp(b, 0, 1));
    }

    public static float corruptionMultiplier(float strength)
    {
        return 1 + CORRUPTION_BOOST * Mth.clamp(strength, 0, 1);
    }

    /**
     * What is left of a ward's strength inside the seep. W4 applies it to its wards.
     */
    public static float wardFactor(float strength)
    {
        return 1 - Mth.clamp(strength, 0, 1);
    }

    /**
     * Points of corruption the seep adds on its own each second, before modifiers.
     */
    public static float risePerSecond(float strength)
    {
        return SEEP_RISE_PER_MINUTE * Mth.clamp(strength, 0, 1) / 60;
    }

    /**
     * Taint a minute on the node of a line this far away. Wounds taint like the width they seep at.
     */
    public static float taintPerMinute(float width, double distanceToLine)
    {
        if (!(distanceToLine < LINE_REACH))
            return 0;
        return (float) (TAINT_PER_WIDTH * clampWidth(width) * (1 - Math.max(0, distanceToLine) / LINE_REACH));
    }

    public static int spawnIntervalTicks(float width)
    {
        return Math.max(1, Math.round(SPAWN_SECONDS * 20 / clampWidth(width)));
    }

    public static int maxAlive(float width)
    {
        return Math.max(1, Math.round(ALIVE_PER_WIDTH * clampWidth(width)));
    }

    public static int closeCost(float width)
    {
        return Math.round(CLOSE_COST_PER_WIDTH * clampWidth(width));
    }

    /**
     * Bundle size (1 small, 2 medium, 3 large) that widens a fissure from this width, or 0 once it is as wide as it gets.
     */
    public static int widenBundle(int width)
    {
        if (width < 1 || width >= MAX_WIDTH)
            return 0;
        return WIDEN_BUNDLE[width - 1];
    }

    /**
     * How far a closing has drawn shut, from 0 as it starts to 1 once closed. The seep thins out with it.
     */
    public static float closing(long since, long now)
    {
        if (since < 0)
            return 0;
        return Mth.clamp((now - since) / (float) CLOSE_TICKS, 0, 1);
    }

    /**
     * How far the latest widening has torn open, from 0 as it starts to 1.
     */
    public static float tearing(long since, long now)
    {
        if (since == Long.MIN_VALUE)
            return 1;
        return Mth.clamp((now - since) / (float) TEAR_TICKS, 0, 1);
    }
}
