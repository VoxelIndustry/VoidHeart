package net.voxelindustry.voidheart.client.render.divining;

import net.voxelindustry.voidheart.common.ley.LeyHeights;
import org.jspecify.annotations.Nullable;

/**
 * Where a revealed ley line runs and when each part of what a Ley Divining Rod reveals shows. Pure, so the shape and timing are unit tested.
 * <p>
 * A line is cut into points a block apart along its edge, its heights smoothed between the lattice's samples and swayed a little to the side so it
 * is not ruler straight, then kept only near where the rod was raised. A standing stone's spur is cut the same way, straight between its ends and swaying
 * less.
 */
public final class LeySightPlan
{
    public static final float STEP = 1;
    /**
     * Blocks kept past the reveal radius, so the edge fade has something to fade.
     */
    public static final float CLIP_MARGIN = 2;

    static final float SWAY_AMPLITUDE   = 0.35F;
    static final float SWAY_WAVE_LONG   = 23;
    static final float SWAY_WAVE_SHORT  = 9;
    static final float SWAY_SHORT_SHARE = 0.35F;
    /**
     * Blocks over which the sway grows from nothing at a node, so every line meets its node at the same point.
     */
    static final float SWAY_EASE = 6;
    /**
     * Share of a line's sway a spur keeps, being thinner and shorter.
     */
    static final float SPUR_SWAY_SHARE = 0.5F;

    /**
     * Ticks the reveal takes to spread from the rod to the edge of its radius.
     */
    public static final int SPREAD_TICKS = 20;
    /**
     * Tick at which the reveal starts fading, from its far edge back toward the rod.
     */
    public static final int HOLD_END_TICKS = 200;
    public static final int FADE_TICKS     = 40;
    public static final int LIFE_TICKS     = HOLD_END_TICKS + FADE_TICKS;
    /**
     * Blocks over which the spreading front, the fading back and the radius edge soften.
     */
    static final float FRONT_SOFT = 6;
    static final float EDGE_SOFT  = 14;
    /**
     * Width in blocks and strength of the brightening that runs out with the spreading front.
     */
    static final float FRONT_FLASH_WIDTH = 4;
    static final float FRONT_FLASH       = 0.8F;

    /**
     * Ticks the searching shell lasts: it grows with the spreading front and is gone a little after the front reaches the edge.
     */
    public static final int SHELL_TICKS     = SPREAD_TICKS + 60;
    static final float      SHELL_FADE_IN   = 3;

    private LeySightPlan()
    {
    }

    /**
     * The kept stretch of a line.
     *
     * @param along    share of the whole edge each point sits at, 0 at the from node
     * @param distance distance on the ground from the rod to each point
     * @param start    blocks from the from node to the first kept point
     */
    public record Path(double[] x, double[] y, double[] z, float[] along, float[] distance, float start, float edgeLength)
    {
        public int size()
        {
            return x.length;
        }

        public boolean isEmpty()
        {
            return x.length < 2;
        }
    }

    private interface HeightAt
    {
        double at(float t);
    }

    /**
     * @param heights line heights sampled evenly from the from node to the to node, both ends included
     * @param seed    picks the sway, so a line keeps its shape from one reveal to the next
     */
    public static Path line(int fromX, int fromZ, int toX, int toZ, float[] heights, double originX, double originZ, float radius, long seed)
    {
        return path(fromX + 0.5, fromZ + 0.5, toX + 0.5, toZ + 0.5, t -> height(heights, t) + 0.5, originX, originZ, radius, seed, 1);
    }

    /**
     * A spur's reach, straight from its from point to its to point, both in world coordinates, its height running evenly between theirs.
     */
    public static Path spur(double fromX, double fromY, double fromZ, double toX, double toY, double toZ, double originX, double originZ, float radius, long seed)
    {
        return path(fromX, fromZ, toX, toZ, t -> fromY + (toY - fromY) * t, originX, originZ, radius, seed, SPUR_SWAY_SHARE);
    }

    /**
     * The point of a path nearest a spot on the ground, between its points, as x, y, z and the squared ground distance to it, or null for an empty path.
     * A spur leaving a line starts here, so it meets the swaying strand and not the straight edge under it.
     */
    public static double @Nullable [] nearest(Path path, double x, double z)
    {
        if (path.isEmpty())
            return null;
        double[] best = null;
        for (int i = 0; i < path.size() - 1; i++)
        {
            double ax = path.x()[i], az = path.z()[i];
            double dx = path.x()[i + 1] - ax, dz = path.z()[i + 1] - az;
            double lengthSq = dx * dx + dz * dz;
            double t = lengthSq == 0 ? 0 : Math.clamp(((x - ax) * dx + (z - az) * dz) / lengthSq, 0, 1);
            double px = ax + dx * t, pz = az + dz * t;
            double distanceSq = (px - x) * (px - x) + (pz - z) * (pz - z);
            if (best == null || distanceSq < best[3])
                best = new double[] { px, path.y()[i] + (path.y()[i + 1] - path.y()[i]) * t, pz, distanceSq };
        }
        return best;
    }

    private static Path path(double fromX, double fromZ, double toX, double toZ, HeightAt heights, double originX, double originZ, float radius, long seed,
                             float swayShare)
    {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        float length = (float) Math.hypot(dx, dz);
        int steps = Math.max(1, (int) Math.ceil(length / STEP));
        double sideX = length == 0 ? 0 : -dz / length;
        double sideZ = length == 0 ? 0 : dx / length;

        float phaseLong = hashUnit(seed, 1) * (float) (2 * Math.PI);
        float phaseShort = hashUnit(seed, 2) * (float) (2 * Math.PI);
        float keep = radius + CLIP_MARGIN;

        int first = -1, last = -1;
        var all = new double[steps + 1][];
        for (int i = 0; i <= steps; i++)
        {
            float t = (float) i / steps;
            float s = t * length;
            double sway = swayShare * sway(s, length, phaseLong, phaseShort);
            double x = fromX + dx * t + sideX * sway;
            double z = fromZ + dz * t + sideZ * sway;
            double distance = Math.hypot(x - originX, z - originZ);
            if (distance <= keep)
            {
                if (first < 0)
                    first = i;
                last = i;
            }
            all[i] = new double[] { x, heights.at(t), z, t, distance };
        }
        if (first < 0 || first == last)
            return new Path(new double[0], new double[0], new double[0], new float[0], new float[0], 0, length);

        // Keep one point past each end of the kept stretch, so it reaches all the way into the soft edge
        first = Math.max(0, first - 1);
        last = Math.min(steps, last + 1);
        int count = last - first + 1;
        var path = new Path(new double[count], new double[count], new double[count], new float[count], new float[count], (float) first / steps * length, length);
        for (int i = 0; i < count; i++)
        {
            var point = all[first + i];
            path.x[i] = point[0];
            path.y[i] = point[1];
            path.z[i] = point[2];
            path.along[i] = (float) point[3];
            path.distance[i] = (float) point[4];
        }
        return path;
    }

    /**
     * Sideways offset of a line, nothing at its nodes.
     */
    static double sway(float s, float length, float phaseLong, float phaseShort)
    {
        float ease = Math.min(1, Math.min(s, length - s) / SWAY_EASE);
        ease = ease * ease * (3 - 2 * ease);
        double wave = (1 - SWAY_SHORT_SHARE) * Math.sin(s * 2 * Math.PI / SWAY_WAVE_LONG + phaseLong)
                + SWAY_SHORT_SHARE * Math.sin(s * 2 * Math.PI / SWAY_WAVE_SHORT + phaseShort);
        return SWAY_AMPLITUDE * ease * wave;
    }

    /**
     * Height at a share of the line, as {@link LeyHeights#at} smooths it, so spurs leaving the line meet it.
     */
    static float height(float[] heights, float t)
    {
        return LeyHeights.at(heights, t);
    }

    /**
     * How much of what sits this far from the rod shows at this age, from 0 to 1, before any brightening from the front.
     */
    public static float reveal(float age, float distance, float radius)
    {
        if (age < 0 || age >= LIFE_TICKS)
            return 0;

        float front = radius * easeOut(Math.min(1, age / SPREAD_TICKS));
        float shown = smoothstep(front, front - FRONT_SOFT, distance);
        if (age > HOLD_END_TICKS)
        {
            float back = radius * (1 - (age - HOLD_END_TICKS) / FADE_TICKS);
            shown *= smoothstep(back, back - FRONT_SOFT, distance);
        }
        return shown * smoothstep(radius, radius - EDGE_SOFT, distance);
    }

    /**
     * Extra brightness where the spreading front is passing, 0 once it has reached the edge.
     */
    public static float frontFlash(float age, float distance, float radius)
    {
        if (age < 0 || age >= SPREAD_TICKS)
            return 0;
        float front = radius * easeOut(age / SPREAD_TICKS);
        float offset = (distance - front) / FRONT_FLASH_WIDTH;
        return FRONT_FLASH * (float) Math.exp(-offset * offset);
    }

    /**
     * Radius of the searching shell at this age: it runs with the spreading front.
     */
    public static float shellRadius(float age, float radius)
    {
        return radius * easeOut(Math.clamp(age / SPREAD_TICKS, 0, 1));
    }

    /**
     * Where the searching shell meets the terrain in one direction from its center, as distance on the ground from the center and height, or null if the
     * terrain sampled never reaches it. The first sample whose top lies at or beyond the shell is taken, so the nearest crossing wins, and the point is
     * where the terrain between it and the sample before crosses the sphere, so it lies on the shell however steep the ground.
     *
     * @param heights terrain tops sampled every {@code step} blocks outward from the center, the first under the center itself
     */
    public static float @Nullable [] shellContact(float[] heights, float step, float centerY, float radius)
    {
        for (int i = 0; i < heights.length; i++)
        {
            if (Math.hypot(i * step, heights[i] - centerY) < radius)
                continue;
            if (i == 0)
                return new float[] { 0, heights[0] };

            // The segment from the sample before, inside the sphere, to this one, outside: solve |start + t * along| = radius for t in [0, 1]
            double startD = (i - 1) * step, startH = heights[i - 1] - centerY;
            double alongH = heights[i] - heights[i - 1];
            double a = step * step + alongH * alongH;
            double b = 2 * (startD * step + startH * alongH);
            double c = startD * startD + startH * startH - (double) radius * radius;
            double t = Math.clamp((-b + Math.sqrt(Math.max(0, b * b - 4 * a * c))) / (2 * a), 0, 1);
            return new float[] { (float) (startD + step * t), (float) (heights[i - 1] + alongH * t) };
        }
        return null;
    }

    /**
     * How much of the searching shell shows at this age, from 0 to 1: in quickly, then thinning as it spreads and lingers, gone at {@link #SHELL_TICKS}.
     */
    public static float shellFade(float age)
    {
        if (age < 0 || age >= SHELL_TICKS)
            return 0;
        float in = Math.min(1, age / SHELL_FADE_IN);
        float out = 1 - age / SHELL_TICKS;
        return in * out;
    }

    static float easeOut(float t)
    {
        return 1 - (1 - t) * (1 - t);
    }

    /**
     * 0 at {@code from}, 1 at {@code to}, smooth between. Either may be the larger.
     */
    static float smoothstep(float from, float to, float value)
    {
        if (from == to)
            return value < to ? 1 : 0;
        float t = Math.clamp((value - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /**
     * A stable value in 0..1 from a seed and a salt.
     */
    static float hashUnit(long seed, long salt)
    {
        long h = seed * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }
}
