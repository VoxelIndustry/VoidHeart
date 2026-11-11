package net.voxelindustry.voidheart.common.content.apparatus;

import java.util.Optional;
import java.util.Set;

/**
 * What an apparatus gives with its components in place.
 *
 * @param regenPerTick points refilled each tick once the wait is over
 */
public record ShieldStats(float capacity, float regenPerTick, int delayTicks, Set<WardKind> wards, Optional<BreakEffect> breakEffect)
{
    public static final int   MIN_DELAY_TICKS = 20;
    /** What a warded hit costs the shield, against what it would cost unwarded. */
    public static final float WARD_COST       = 0.5F;
    /** A broken shield waits this much longer than the usual delay before it comes back. */
    public static final float BROKEN_DELAY_SCALE = 1.5F;

    public int brokenDelayTicks()
    {
        return Math.round(delayTicks * BROKEN_DELAY_SCALE);
    }
}
