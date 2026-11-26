package net.voxelindustry.voidheart.client.render.heart;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Veins carried on from where a heart's strands reach the floor, creeping out over the floor around it like vines over a wall: thin wandering lines one
 * pixel wide that fork now and then and never thicken. They lie on the block pixel grid of a square of floor centred on the heart, and stop where the
 * floor does.
 */
public final class FloorVeins
{
    public static final int NONE = -1;

    /** How many blocks out from the heart the veins may reach. */
    public static final int RADIUS = 4;
    public static final int CELLS  = 2 * RADIUS + 1;
    public static final int PIXELS = CELLS * 16;

    private static final int    TENDRILS_MIN  = 2;
    private static final int    TENDRILS_MAX  = 3;
    private static final int    LENGTH_MIN    = 40;
    private static final int    LENGTH_MAX    = 90;
    /** How far a tendril's heading drifts each pixel, and how hard it is pulled back outward from the heart. */
    private static final double WANDER        = 0.45;
    private static final double OUTWARD_PULL  = 0.12;
    private static final float  BRANCH_CHANCE = 0.06F;
    private static final double BRANCH_TURN   = 0.9;
    private static final double BRANCH_SHARE  = 0.6;

    private static final int[][] SIDES = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    /** @param along how far along its vein this pixel lies, counting on from the heart */
    public record Pixel(int x, int z, int along)
    {
    }

    /**
     * @param pixels every vein pixel, nearest the heart first
     * @param first  how far along the nearest pixel lies, where the floor's growth starts
     * @param last   how far along the furthest pixel lies
     */
    public record Growth(List<Pixel> pixels, int first, int last)
    {
    }

    private FloorVeins()
    {
    }

    /** The heart's foot pixels {x, z} as they lie once the heart has settled this many quarter turns, matching how it is drawn. */
    public static int[] turn(int x, int z, int quarters)
    {
        for (int i = 0; i < (quarters & 3); i++)
        {
            int turned = 15 - z;
            z = x;
            x = turned;
        }
        return new int[] { x, z };
    }

    /**
     * @param floor where there is floor to creep over, by block from the square's corner, the heart in the middle
     * @param feet  where the strands land, as pixels of the heart's block {x, z, along}, already turned
     */
    public static Growth grow(boolean[][] floor, List<int[]> feet, int seed)
    {
        var along = new int[PIXELS][PIXELS];
        for (var row : along)
            Arrays.fill(row, NONE);

        var random = new Random(seed * 31L + 7);
        var tendrils = new ArrayDeque<Tendril>();
        double middle = RADIUS * 16 + 8;
        for (var foot : feet)
        {
            int x = RADIUS * 16 + foot[0], z = RADIUS * 16 + foot[1];
            if (!onFloor(floor, x, z))
                continue;
            along[x][z] = along[x][z] == NONE ? foot[2] : Math.min(along[x][z], foot[2]);
            double outward = Math.atan2(z + 0.5 - middle, x + 0.5 - middle);
            int count = TENDRILS_MIN + random.nextInt(TENDRILS_MAX - TENDRILS_MIN + 1);
            for (int i = 0; i < count; i++)
            {
                double heading = outward + (random.nextDouble() - 0.5) * 1.6;
                tendrils.add(new Tendril(x, z, heading, LENGTH_MIN + random.nextInt(LENGTH_MAX - LENGTH_MIN + 1), foot[2]));
            }
        }

        while (!tendrils.isEmpty())
            creep(floor, along, tendrils.poll(), tendrils, middle, random);

        var pixels = new ArrayList<Pixel>();
        int first = Integer.MAX_VALUE, last = NONE;
        for (int x = 0; x < PIXELS; x++)
        {
            for (int z = 0; z < PIXELS; z++)
            {
                if (along[x][z] == NONE)
                    continue;
                pixels.add(new Pixel(x, z, along[x][z]));
                first = Math.min(first, along[x][z]);
                last = Math.max(last, along[x][z]);
            }
        }
        pixels.sort((a, b) -> Integer.compare(a.along(), b.along()));
        return new Growth(List.copyOf(pixels), pixels.isEmpty() ? 0 : first, last);
    }

    private static void creep(boolean[][] floor, int[][] along, Tendril tendril, ArrayDeque<Tendril> tendrils, double middle, Random random)
    {
        int x = tendril.x, z = tendril.z, step = tendril.along;
        double heading = tendril.heading;
        for (int left = tendril.length; left > 0; left--)
        {
            double outward = Math.atan2(z + 0.5 - middle, x + 0.5 - middle);
            heading += (random.nextDouble() - 0.5) * 2 * WANDER + OUTWARD_PULL * Math.sin(outward - heading);
            double dx = Math.cos(heading), dz = Math.sin(heading);
            int stepX = dx < 0 ? -1 : 1, stepZ = dz < 0 ? -1 : 1;
            boolean alongX = random.nextDouble() * (Math.abs(dx) + Math.abs(dz)) < Math.abs(dx);
            // Blocked one way, it tries the other before it gives up
            int nx = alongX ? x + stepX : x, nz = alongX ? z : z + stepZ;
            if (!open(floor, along, nx, nz, x, z))
            {
                nx = alongX ? x : x + stepX;
                nz = alongX ? z + stepZ : z;
                if (!open(floor, along, nx, nz, x, z))
                    return;
            }
            along[nx][nz] = ++step;
            x = nx;
            z = nz;

            if (random.nextFloat() < BRANCH_CHANCE)
            {
                double turn = random.nextBoolean() ? BRANCH_TURN : -BRANCH_TURN;
                tendrils.add(new Tendril(x, z, heading + turn, (int) (left * BRANCH_SHARE), step));
            }
        }
    }

    private static boolean open(boolean[][] floor, int[][] along, int x, int z, int fromX, int fromZ)
    {
        return onFloor(floor, x, z) && along[x][z] == NONE && !crowded(along, x, z, fromX, fromZ);
    }

    /** Whether a pixel lies beside a vein other than the one it comes from, which would thicken the line or join two into a blot. */
    private static boolean crowded(int[][] along, int x, int z, int fromX, int fromZ)
    {
        for (var side : SIDES)
        {
            int nx = x + side[0], nz = z + side[1];
            if ((nx != fromX || nz != fromZ) && nx >= 0 && nz >= 0 && nx < PIXELS && nz < PIXELS && along[nx][nz] != NONE)
                return true;
        }
        return false;
    }

    private static boolean onFloor(boolean[][] floor, int x, int z)
    {
        return x >= 0 && z >= 0 && x < PIXELS && z < PIXELS && floor[x / 16][z / 16];
    }

    private record Tendril(int x, int z, double heading, int length, int along)
    {
    }
}
