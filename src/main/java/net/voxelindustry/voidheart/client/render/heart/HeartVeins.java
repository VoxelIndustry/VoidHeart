package net.voxelindustry.voidheart.client.render.heart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static net.voxelindustry.voidheart.client.render.heart.HeartShape.SIZE;

/**
 * Veins grown over a heart from its owner's seed: each starts high on its surface, runs down over it pixel by pixel and hangs from where the heart
 * narrows away below it to the floor of its block, where {@link FloorVeins} carries it on. Every vein pixel is numbered by how far along its vein it
 * lies, so the veins can grow and pulse outward from the heart.
 */
public final class HeartVeins
{
    public static final int NONE = -1;

    private static final int   VEINS        = 5;
    /** Veins start on the layers between these, around the atria. */
    private static final int   START_LOW    = 10;
    private static final int   START_HIGH   = 12;
    /** How often a vein steps aside while running down, so it wanders instead of running straight. */
    private static final float WANDER       = 0.4F;
    /** A clinging vein follows the underside in toward the apex before it hangs, the rest hang from the heart's widest. */
    private static final float CLING_CHANCE = 0.3F;
    /** Below this layer the heart narrows, so a vein moving sideways goes in under it rather than out over it. */
    private static final int   EQUATOR      = 7;
    private static final int   MAX_STEPS    = 48;
    /** Kept off the sides of the block, so nothing touches a neighbouring block's face. */
    private static final int   MARGIN       = 1;

    private static final int[][] SIDEWAYS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    private HeartVeins()
    {
    }

    /** How far along its vein each pixel lies, {@link #NONE} where there is no vein. */
    public static int[][][] grow(boolean[][][] flesh, int seed)
    {
        var along = new int[SIZE][SIZE][SIZE];
        for (var plane : along)
        {
            for (var row : plane)
                Arrays.fill(row, NONE);
        }

        var random = new Random(seed);
        float turn = random.nextFloat();
        for (int vein = 0; vein < VEINS; vein++)
        {
            double angle = 2 * Math.PI * (vein + turn + (random.nextFloat() - 0.5F) * 0.5F) / VEINS;
            int y = START_LOW + random.nextInt(START_HIGH - START_LOW + 1);
            boolean cling = random.nextFloat() < CLING_CHANCE;
            var start = start(flesh, y, angle);
            if (start != null)
                walk(flesh, along, start, cling, random);
        }
        return along;
    }

    public static int length(int[][][] along)
    {
        int longest = NONE;
        for (var plane : along)
        {
            for (var row : plane)
            {
                for (int at : row)
                    longest = Math.max(longest, at);
            }
        }
        return longest + 1;
    }

    /** Where the veins reach the floor, as pixels of the block {x, z, along}. */
    public static List<int[]> feet(int[][][] along)
    {
        var feet = new ArrayList<int[]>();
        for (int x = 0; x < SIZE; x++)
        {
            for (int z = 0; z < SIZE; z++)
            {
                if (along[x][0][z] != NONE)
                    feet.add(new int[] { x, z, along[x][0][z] });
            }
        }
        return feet;
    }

    /** The surface pixel on this layer lying furthest toward the angle from the heart's axis. */
    private static int[] start(boolean[][][] flesh, int y, double angle)
    {
        double dirX = Math.cos(angle), dirZ = Math.sin(angle);
        int[] best = null;
        double bestScore = -2;
        for (int x = MARGIN; x < SIZE - MARGIN; x++)
        {
            for (int z = MARGIN; z < SIZE - MARGIN; z++)
            {
                if (!surface(flesh, x, y, z))
                    continue;
                double offX = x + 0.5 - SIZE / 2D, offZ = z + 0.5 - SIZE / 2D;
                double length = Math.hypot(offX, offZ);
                double score = length == 0 ? -1 : (offX * dirX + offZ * dirZ) / length;
                if (score > bestScore)
                {
                    bestScore = score;
                    best = new int[] { x, y, z };
                }
            }
        }
        return best;
    }

    private static void walk(boolean[][][] flesh, int[][][] along, int[] start, boolean cling, Random random)
    {
        var seen = new boolean[SIZE][SIZE][SIZE];
        int x = start[0], y = start[1], z = start[2];
        int step = 0;
        mark(along, seen, x, y, z, step);

        for (int moves = 0; moves < MAX_STEPS; moves++)
        {
            if (!cling && hangsFree(flesh, x, y, z))
                break;

            if (random.nextFloat() < WANDER)
            {
                int[] aside = sideways(flesh, seen, x, y, z, cling, true, random);
                if (aside != null)
                {
                    x = aside[0];
                    z = aside[1];
                    mark(along, seen, x, y, z, ++step);
                    continue;
                }
            }

            if (y > 0 && surface(flesh, x, y - 1, z) && !seen[x][y - 1][z])
            {
                mark(along, seen, x, --y, z, ++step);
                continue;
            }

            int[] next = sideways(flesh, seen, x, y, z, cling, false, random);
            if (next == null)
                break;
            x = next[0];
            z = next[1];
            mark(along, seen, x, y, z, ++step);
        }

        if (!hangsFree(flesh, x, y, z))
            return;
        while (y > 0)
            mark(along, seen, x, --y, z, ++step);
    }

    /**
     * The next surface pixel beside this one, one it can go down from first. Above the equator it prefers out over the shoulder, below it in under the
     * heart, and a clinging vein below the equator never goes out again. A vein wandering only steps aside where it can go down next.
     */
    private static int[] sideways(boolean[][][] flesh, boolean[][][] seen, int x, int y, int z, boolean cling, boolean wander, Random random)
    {
        boolean below = y < EQUATOR;
        double from = reach(x, z);
        int[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (var side : SIDEWAYS)
        {
            int nx = x + side[0], nz = z + side[1];
            if (nx < MARGIN || nz < MARGIN || nx >= SIZE - MARGIN || nz >= SIZE - MARGIN || seen[nx][y][nz] || !surface(flesh, nx, y, nz))
                continue;
            double outward = reach(nx, nz) - from;
            if (cling && below && outward > 0)
                continue;
            boolean descends = y > 0 && surface(flesh, nx, y - 1, nz) && !seen[nx][y - 1][nz];
            if (wander && !descends)
                continue;
            double score = (descends ? 0 : 10) + (below ? outward : -outward) + random.nextFloat() * 0.5;
            if (score < bestScore)
            {
                bestScore = score;
                best = new int[] { nx, nz };
            }
        }
        return best;
    }

    private static void mark(int[][][] along, boolean[][][] seen, int x, int y, int z, int step)
    {
        seen[x][y][z] = true;
        along[x][y][z] = along[x][y][z] == NONE ? step : Math.min(along[x][y][z], step);
    }

    /** Whether nothing of the heart lies under this pixel, so a vein can hang straight down from it. */
    private static boolean hangsFree(boolean[][][] flesh, int x, int y, int z)
    {
        for (int below = 0; below < y; below++)
        {
            if (flesh[x][below][z])
                return false;
        }
        return true;
    }

    /** An empty pixel against the heart, where a vein can lie on it. */
    static boolean surface(boolean[][][] flesh, int x, int y, int z)
    {
        if (x < 0 || y < 0 || z < 0 || x >= SIZE || y >= SIZE - 1 || z >= SIZE || flesh[x][y][z])
            return false;
        return filled(flesh, x + 1, y, z) || filled(flesh, x - 1, y, z) || filled(flesh, x, y + 1, z) || filled(flesh, x, y - 1, z)
                || filled(flesh, x, y, z + 1) || filled(flesh, x, y, z - 1);
    }

    private static boolean filled(boolean[][][] flesh, int x, int y, int z)
    {
        return x >= 0 && y >= 0 && z >= 0 && x < SIZE && y < SIZE && z < SIZE && flesh[x][y][z];
    }

    private static double reach(int x, int z)
    {
        return Math.hypot(x + 0.5 - SIZE / 2D, z + 0.5 - SIZE / 2D);
    }
}
