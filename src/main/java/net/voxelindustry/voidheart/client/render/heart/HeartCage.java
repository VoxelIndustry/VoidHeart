package net.voxelindustry.voidheart.client.render.heart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static net.voxelindustry.voidheart.client.render.heart.HeartShape.SIZE;

/**
 * The bone a heart grows around itself with Marrow: a spine standing on the floor at its back, and ribs one pixel thin curving from it around both
 * sides toward the front, where they stop short of meeting. Laid from the owner's seed in whole pixels of the block, clear of the heart however it swells
 * and of its veins. Every bone pixel is numbered by how far along the cage's growth it lies: the spine rises from the floor, and each rib sets out from
 * it once the spine reaches its height, so the cage closes from the back to the front.
 */
public final class HeartCage
{
    public static final int NONE = -1;

    /** The spine, two pixels wide at the middle of the back, from the floor up to its top layer. */
    private static final int SPINE_X   = 7;
    private static final int SPINE_Z   = 2;
    private static final int SPINE_TOP = 13;
    /** Every this many layers a vertebra narrows to one pixel, so the spine reads as stacked bones. */
    private static final int VERTEBRA  = 3;

    /** The ribs leave the spine at these layers, three apart so they never touch, and none on a narrowed vertebra. */
    private static final int[]   RIB_LAYERS = { 12, 9, 6, 3 };
    /** How far around the heart the ribs curve, measured from the spine. */
    private static final float   RIB_RADIUS = 5.8F;
    /** How many layers a rib drops from the spine to its tip, as ribs slope down toward the front, at most. */
    private static final int     RIB_DROP   = 2;
    /** Ribs end this far short of the front, in radians from the middle, plus up to {@link #RIB_SHORTEN} more where the seed shortens one. */
    private static final double  RIB_GAP     = 0.35;
    private static final double  RIB_SHORTEN = 0.7;
    private static final float   SHORT_CHANCE = 0.35F;
    private static final int     MARGIN     = 1;
    private static final double  ARC_STEP   = 0.02;

    private HeartCage()
    {
    }

    /**
     * @param flesh the heart swelled with a beat, its fullest, so no bone lies where flesh can be
     * @param veins from {@link HeartVeins#grow}, which bone keeps clear of
     * @return how far along the cage's growth each pixel lies, {@link #NONE} where there is no bone
     */
    public static int[][][] grow(boolean[][][] flesh, int[][][] veins, int seed)
    {
        var along = new int[SIZE][SIZE][SIZE];
        for (var plane : along)
        {
            for (var row : plane)
                Arrays.fill(row, NONE);
        }

        for (int y = 0; y <= SPINE_TOP; y++)
        {
            boolean narrow = y % VERTEBRA == VERTEBRA - 1;
            for (int x = SPINE_X; x < SPINE_X + (narrow ? 1 : 2); x++)
            {
                for (int z = SPINE_Z; z < SPINE_Z + 2; z++)
                {
                    if (clear(flesh, veins, x, y, z))
                        along[x][y][z] = 2 * y + x - SPINE_X;
                }
            }
        }

        var random = new Random(seed * 17L + 3);
        for (int layer : RIB_LAYERS)
        {
            int drop = 1 + random.nextInt(RIB_DROP);
            for (int side = -1; side <= 1; side += 2)
            {
                double end = Math.PI - RIB_GAP - (random.nextFloat() < SHORT_CHANCE ? random.nextDouble() * RIB_SHORTEN : 0);
                rib(flesh, veins, along, layer, drop, side, end);
            }
        }
        return along;
    }

    /** One rib, curving from the spine at this layer around one side, numbered on from where the spine reaches it. */
    private static void rib(boolean[][][] flesh, int[][][] veins, int[][][] along, int y, int drop, int side, double end)
    {
        double centreX = SPINE_X + 1, centreZ = SPINE_Z + 0.5 + RIB_RADIUS;
        var path = new ArrayList<int[]>();
        // Starts just past the spine's edge, so it joins its side
        for (double angle = 0.25; angle <= end; angle += ARC_STEP)
        {
            int ry = y - (int) Math.round(drop * angle / Math.PI);
            int[] at = null;
            for (double radius = RIB_RADIUS; radius <= RIB_RADIUS + 2; radius += 0.5)
            {
                int x = (int) Math.floor(centreX + side * radius * Math.sin(angle));
                int z = (int) Math.floor(centreZ - radius * Math.cos(angle));
                if (x < MARGIN || z < MARGIN || x >= SIZE - MARGIN || z >= SIZE - MARGIN)
                    break;
                if (clear(flesh, veins, x, ry, z))
                {
                    at = new int[] { x, ry, z };
                    break;
                }
            }
            // Blocked here, the rib ends rather than leaving a gap
            if (at == null || !path.isEmpty() && !connect(flesh, veins, path, path.getLast(), at))
                break;
            if (path.isEmpty())
                path.add(at);
        }

        int[] step = { 2 * y + 2 };
        for (var pixel : thin(path))
            place(along, pixel, step);
    }

    /** Steps from one rib pixel to the next face to face, across before down or the other way, failing if both run into something. */
    private static boolean connect(boolean[][][] flesh, int[][][] veins, List<int[]> path, int[] from, int[] to)
    {
        for (int[] order : new int[][] { { 0, 2, 1 }, { 2, 0, 1 }, { 1, 0, 2 } })
        {
            var steps = new ArrayList<int[]>();
            int[] at = from.clone();
            boolean blocked = false;
            for (int axis : order)
            {
                while (at[axis] != to[axis] && !blocked)
                {
                    at[axis] += Integer.signum(to[axis] - at[axis]);
                    if (!clear(flesh, veins, at[0], at[1], at[2]))
                        blocked = true;
                    else
                        steps.add(at.clone());
                }
            }
            if (blocked)
                continue;
            path.addAll(steps);
            return true;
        }
        return false;
    }

    /**
     * Drops the inner pixel of each turn on a layer, where the pixels either side of it meet across a corner, so a rib running aslant is a line one pixel
     * thin as pixel art draws one rather than a staircase twice as heavy.
     */
    private static List<int[]> thin(List<int[]> path)
    {
        var thin = new ArrayList<>(path);
        for (int i = 1; i + 1 < thin.size(); i++)
        {
            int[] before = thin.get(i - 1), after = thin.get(i + 1);
            if (before[1] == after[1] && Math.abs(before[0] - after[0]) == 1 && Math.abs(before[2] - after[2]) == 1)
                thin.remove(i);
        }
        return thin;
    }

    /** Numbers a pixel by how far along its rib it lies, counting on over pixels another rib already took so the count never falls behind. */
    private static void place(int[][][] along, int[] at, int[] step)
    {
        int now = along[at[0]][at[1]][at[2]];
        along[at[0]][at[1]][at[2]] = now == NONE ? step[0] : Math.min(now, step[0]);
        step[0]++;
    }

    private static boolean clear(boolean[][][] flesh, int[][][] veins, int x, int y, int z)
    {
        return y >= 0 && y < SIZE && !flesh[x][y][z] && veins[x][y][z] == HeartVeins.NONE;
    }

    public static int length(int[][][] along)
    {
        return HeartVeins.length(along);
    }

    /** The bone shown once the cage has grown this far along. */
    public static boolean[][][] shown(int[][][] along, int reach)
    {
        var bone = new boolean[SIZE][SIZE][SIZE];
        for (int x = 0; x < SIZE; x++)
        {
            for (int y = 0; y < SIZE; y++)
            {
                for (int z = 0; z < SIZE; z++)
                    bone[x][y][z] = along[x][y][z] != NONE && along[x][y][z] < reach;
            }
        }
        return bone;
    }
}
