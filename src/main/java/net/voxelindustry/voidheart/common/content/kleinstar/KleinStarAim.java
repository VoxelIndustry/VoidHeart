package net.voxelindustry.voidheart.common.content.kleinstar;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Where a Klein Star goes to burst. Everything it may burst on is weighed by its health, and by how near it is to where the caster looks: what they
 * look straight at counts many times over, so a caster steers the star by looking, and without a gaze to follow the healthiest wins. The star starts
 * on the weightiest, then slides to the middle of everything its blast would reach from there, each by its weight, and again from the new spot until
 * it settles. What the blast would not reach never pulls on it, and the one it started on is never let out of the blast.
 */
public final class KleinStarAim
{
    private static final int    MAX_STEPS  = 8;
    private static final double SETTLED    = 0.01;
    // What the caster looks straight at counts this many times more, falling off to nothing more this many degrees from their gaze
    private static final double GAZE_BOOST = 9;
    private static final double GAZE_CONE  = 30;

    /**
     * Something the star may burst on.
     *
     * @param at     the middle of its body
     * @param health what it has left
     */
    public record Mark(Vec3 at, double health)
    {
    }

    /**
     * Where the caster looks from, and which way.
     */
    public record Gaze(Vec3 eyes, Vec3 look)
    {
    }

    /**
     * @param at     where to burst
     * @param anchor which of the marks it started on, by its place in the list
     */
    public record Aim(Vec3 at, int anchor)
    {
    }

    private KleinStarAim()
    {
    }

    /**
     * @return how much a mark pulls on the star
     */
    public static double weight(Mark mark, @Nullable Gaze gaze)
    {
        // Something at its last breath still counts for a little, so a crowd of the dying is not ignored
        double health = Math.max(mark.health(), 1);
        if (gaze == null)
            return health;

        var toMark = mark.at().subtract(gaze.eyes());
        double distance = toMark.length();
        if (distance < 1e-6)
            return health * (1 + GAZE_BOOST);
        double angle = Math.toDegrees(Math.acos(Math.clamp(toMark.dot(gaze.look()) / distance, -1, 1)));
        double nearness = Math.max(0, 1 - angle / GAZE_CONE);
        return health * (1 + GAZE_BOOST * nearness * nearness);
    }

    /**
     * @return where to burst and on which mark it started, or null when there is nothing to aim at
     */
    public static @Nullable Aim aim(List<Mark> marks, double radius, @Nullable Gaze gaze)
    {
        int anchor = -1;
        double heaviest = 0;
        for (int i = 0; i < marks.size(); i++)
        {
            double weight = weight(marks.get(i), gaze);
            if (anchor < 0 || weight > heaviest)
            {
                anchor = i;
                heaviest = weight;
            }
        }
        return anchor < 0 ? null : new Aim(settle(marks, anchor, radius, gaze), anchor);
    }

    /**
     * @return where a blast started on one of the marks ends up, without ever letting that mark out of it
     */
    public static Vec3 settle(List<Mark> marks, int anchor, double radius, @Nullable Gaze gaze)
    {
        var start = marks.get(anchor).at();
        var center = start;
        for (int step = 0; step < MAX_STEPS; step++)
        {
            var next = barycenter(marks, center, radius, gaze);
            if (next.distanceTo(start) > radius)
                break;
            boolean settled = next.distanceTo(center) < SETTLED;
            center = next;
            if (settled)
                break;
        }
        return center;
    }

    /**
     * @return how many of the marks a blast at this spot reaches
     */
    public static int reached(List<Mark> marks, Vec3 center, double radius)
    {
        int count = 0;
        for (var mark : marks)
            if (mark.at().distanceTo(center) <= radius)
                count++;
        return count;
    }

    private static Vec3 barycenter(List<Mark> marks, Vec3 center, double radius, @Nullable Gaze gaze)
    {
        double x = 0, y = 0, z = 0, total = 0;
        for (var mark : marks)
        {
            if (mark.at().distanceTo(center) > radius)
                continue;
            double weight = weight(mark, gaze);
            x += mark.at().x * weight;
            y += mark.at().y * weight;
            z += mark.at().z * weight;
            total += weight;
        }
        return total <= 0 ? center : new Vec3(x / total, y / total, z / total);
    }
}
