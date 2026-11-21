package net.voxelindustry.voidheart.client.render.crystal;

import net.voxelindustry.voidheart.common.content.crystal.BundleSize;
import org.jspecify.annotations.Nullable;

/**
 * Which bundle a cradle shows rising and how far, from how much has grown. Pure.
 * <p>
 * Each size grows on from the last: a medium bundle starts at the height the small one reached, so the crystal never pops between sizes.
 */
public final class CradleRise
{
    /** The share of its full size a first small bundle starts from. */
    public static final float FIRST_START = 0.25F;

    /**
     * @param scale how big to draw it, against its full size
     * @param sunk  how much of its height is still in the bed, from 0 to 1
     */
    public record Pose(BundleSize size, float scale, float sunk)
    {
    }

    private CradleRise()
    {
    }

    /**
     * The bundle to show, or null while nothing has started.
     *
     * @param shown  how far it has grown, in shards' worth
     * @param target the bundle its worth makes
     */
    public static @Nullable Pose of(double shown, @Nullable BundleSize target)
    {
        if (target == null)
            return null;
        if (shown >= target.worth())
            return new Pose(target, 1, 0);

        var size = BundleSize.nextAbove(shown);
        if (size == null || size.worth() > target.worth())
            size = target;
        var previous = previous(size);
        double from = previous == null ? 0 : previous.worth();
        float progress = (float) Math.clamp((shown - from) / (size.worth() - from), 0, 1);
        float start = previous == null ? FIRST_START : height(previous) / height(size);
        float eased = 1 - (1 - progress) * (1 - progress);
        return new Pose(size, start + (1 - start) * eased, previous == null ? 1 - eased : 0);
    }

    private static @Nullable BundleSize previous(BundleSize size)
    {
        return size.ordinal() == 0 ? null : BundleSize.values()[size.ordinal() - 1];
    }

    /**
     * Rough crystal heights in pixels, the same for every crystal, enough to carry one size on from the last.
     */
    static float height(BundleSize size)
    {
        return switch (size)
        {
            case SMALL -> 6;
            case MEDIUM -> 10;
            case LARGE -> 15;
        };
    }
}
