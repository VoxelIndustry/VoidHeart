package net.voxelindustry.voidheart.client.render.energy.ley_taint;

import net.voxelindustry.voidheart.common.ley.taint.LeyTaintPayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The shape of the bruise veins around a node, on flat ground: trunks wandering along each line and branches off them, out
 * to the farthest a breached node reaches. Seeded from the node id, so a node looks the same on every visit. A node shows
 * the part within its current reach. Pure, so the preview draws exactly what the game does.
 */
public final class LeyTaintVeins
{
    /**
     * @param distance from the node's center to where the piece starts, so the veins can be shown out to a reach
     * @param level    0 for a trunk, 1 for a branch, 2 for the finest branches
     * @param width    relative to a trunk's
     */
    public record Piece(double x0, double z0, double x1, double z1, int level, double distance, float width)
    {
    }

    private static final long SEED_SALT = 0x5441494E54L;
    private static final double STEP = 0.6;
    private static final double MAX_LENGTH = LeyTaintLook.BREACHED_REACH_END + 6;
    /**
     * Trunks wander up to about this far either side of their line.
     */
    private static final double MAX_OFFSET = 2;
    private static final double TURN = 0.28;
    private static final double PULL = 0.18;
    private static final double MAX_ANGLE_OFF = 0.9;
    /**
     * Each step keeps this much of a trunk's turn away from its line, so the pull back doesn't swing it into a regular wave.
     */
    private static final double DAMPING = 0.8;
    private static final double BRANCH_CHANCE = 0.12;
    private static final double TWIG_CHANCE = 0.1;
    private static final double BRANCH_WIDTH = 0.6;
    /**
     * Lines downstream of the node only receive a share of its taint, so their veins are thinner and sparser.
     */
    private static final float DOWNSTREAM_WIDTH = 0.6F;
    private static final double DOWNSTREAM_BRANCHES = 0.5;
    private static final int RADIAL_MIN = 3, RADIAL_MAX = 5;

    private LeyTaintVeins()
    {
    }

    public static List<Piece> generate(long nodeId, int nodeX, int nodeZ, List<LeyTaintPayload.Spoke> spokes)
    {
        var random = new SplittableRandom(nodeId ^ SEED_SALT);
        var pieces = new ArrayList<Piece>();
        double centerX = nodeX + 0.5, centerZ = nodeZ + 0.5;

        // Sorted by angle so the same lines always draw the same random numbers, whatever order they arrive in
        var sorted = new ArrayList<>(spokes);
        sorted.sort(Comparator.comparingDouble(spoke -> Math.atan2(spoke.z() - nodeZ, spoke.x() - nodeX)));
        for (var spoke : sorted)
        {
            double axis = Math.atan2(spoke.z() - nodeZ, spoke.x() - nodeX);
            int trunks = spoke.downstream() ? 1 : 2;
            float width = spoke.downstream() ? DOWNSTREAM_WIDTH : 1;
            double branches = spoke.downstream() ? BRANCH_CHANCE * DOWNSTREAM_BRANCHES : BRANCH_CHANCE;
            for (int trunk = 0; trunk < trunks; trunk++)
            {
                double startX = centerX + random.nextDouble(-0.5, 0.5);
                double startZ = centerZ + random.nextDouble(-0.5, 0.5);
                walk(pieces, random, centerX, centerZ, startX, startZ, axis + random.nextDouble(-0.25, 0.25), MAX_LENGTH, 0, axis, width, branches);
            }
        }

        int radial = random.nextInt(RADIAL_MIN, RADIAL_MAX + 1);
        for (int i = 0; i < radial; i++)
        {
            double heading = random.nextDouble(0, 2 * Math.PI);
            walk(pieces, random, centerX, centerZ, centerX, centerZ, heading, random.nextDouble(2, 5), 1, Double.NaN, (float) BRANCH_WIDTH, TWIG_CHANCE);
        }
        return pieces;
    }

    private static void walk(List<Piece> pieces, SplittableRandom random, double centerX, double centerZ, double x, double z,
                             double heading, double length, int level, double axis, float width, double branchChance)
    {
        double travelled = 0;
        while (travelled < length)
        {
            heading += random.nextGaussian() * TURN;
            if (!Double.isNaN(axis))
                heading = steer(heading, axis, lateral(centerX, centerZ, x, z, axis));

            double nextX = x + Math.cos(heading) * STEP;
            double nextZ = z + Math.sin(heading) * STEP;
            // Trunks keep their width far out, so their end is shaped by the reach; branches taper to their tips
            float taper = level == 0 ? (float) (1 - 0.3 * travelled / length) : (float) (1 - 0.7 * travelled / length);
            pieces.add(new Piece(x, z, nextX, nextZ, level, Math.hypot(x - centerX, z - centerZ), width * taper));

            if (level < 2 && random.nextDouble() < branchChance)
            {
                double side = random.nextBoolean() ? 1 : -1;
                double branchHeading = heading + side * random.nextDouble(0.5, 1.1);
                double branchLength = level == 0 ? random.nextDouble(2, 6) : random.nextDouble(1, 2.5);
                walk(pieces, random, centerX, centerZ, nextX, nextZ, branchHeading, branchLength, level + 1, Double.NaN,
                        (float) (width * taper * BRANCH_WIDTH), TWIG_CHANCE);
            }

            x = nextX;
            z = nextZ;
            travelled += STEP;
        }
    }

    /**
     * Signed distance from the line through the node along the axis.
     */
    private static double lateral(double centerX, double centerZ, double x, double z, double axis)
    {
        return -(x - centerX) * Math.sin(axis) + (z - centerZ) * Math.cos(axis);
    }

    /**
     * Pulls a trunk back toward its line, harder the farther it strayed, and never lets it turn too far from it.
     */
    private static double steer(double heading, double axis, double lateral)
    {
        double pull = PULL * lateral / MAX_OFFSET;
        if (Math.abs(lateral) > MAX_OFFSET)
            pull *= 3;
        double off = wrap(heading - axis) * DAMPING - pull;
        return axis + Math.clamp(off, -MAX_ANGLE_OFF, MAX_ANGLE_OFF);
    }

    private static double wrap(double angle)
    {
        return Math.IEEEremainder(angle, 2 * Math.PI);
    }
}
