package net.voxelindustry.voidheart.client.render.heart;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.client.render.heart.HeartShape.SIZE;

/**
 * The smaller heart a Second Heart grows beside the cage, and the vein that joins it to the first. The twin stands on the floor just outside the ribs on
 * one side, its apex leaning away from the first heart as a mirror would, and is laid in whole pixels of its own block grid, moved a whole number of pixels
 * aside so its texels land as the first heart's do. It buds from where the bridge meets it and swells down to the floor.
 * <p>
 * Coordinates outside this class's twin grid are the first heart's pixels, which run past its block on the twin's side.
 */
public final class HeartTwin
{
    /** How many pixels the twin's grid lies aside of the first heart's, toward its side. */
    public static final int OFFSET = 11;

    private static final float BODY_X = 8, BODY_Y = 3.4F, BODY_Z = 8;
    private static final float BODY_RX = 2.6F, BODY_RY = 3.1F, BODY_RZ = 2.3F;
    /** Leans its apex away from the first heart, as the first leans its own the other way. */
    private static final float APEX_LEAN   = 0.8F;
    private static final float APEX_NARROW = 0.3F;
    /** Its atria, the near one first, toward the first heart. */
    private static final float[][] ATRIA = {
            { 6.4F, 6.1F, 8.4F, 1.3F, 1.1F, 1.3F },
            { 9.6F, 6.3F, 7.6F, 1.4F, 1.2F, 1.3F },
    };
    /** A short aorta, shown once it has nearly grown. */
    private static final int[][] VESSELS = {
            { 7, 6, 7, 9, 9, 9 },
    };
    private static final float VESSELS_FROM = 0.75F;
    private static final float SWELL        = 0.5F;
    /** Where the bridge meets it, in its grid: above the near atrium, which it buds from. */
    public static final int JOIN_X = 6, JOIN_Z = 8;
    public static final int JOIN_Y = restTop();

    /** The bridge starts on the first heart's flesh at one of these heights, the first it can reach clear of bone. */
    private static final int[] BRIDGE_HEIGHTS = { 10, 9, 11, 13, 8, 12, 14, 7 };
    private static final int[] BRIDGE_ROWS    = { 8, 7, 9, 6, 10 };

    private HeartTwin()
    {
    }

    /**
     * The twin in its own grid, grown this far from its join, 0 to 1, and swelled with a beat or not. Mirrored on the first heart's low side, so its apex
     * still leans away.
     */
    public static boolean[][][] voxels(float size, boolean swelled, int side)
    {
        var filled = new boolean[SIZE][SIZE][SIZE];
        if (size <= 0)
            return filled;
        int joinY = JOIN_Y;
        float grow = swelled ? SWELL * size : 0;
        for (int x = 0; x < SIZE; x++)
        {
            for (int y = 0; y < SIZE; y++)
            {
                for (int z = 0; z < SIZE; z++)
                {
                    // Grown about its join, so it buds from the bridge
                    float fx = JOIN_X + 0.5F + (x + 0.5F - JOIN_X - 0.5F) / size;
                    float fy = joinY + (y + 0.5F - joinY) / size;
                    float fz = JOIN_Z + 0.5F + (z + 0.5F - JOIN_Z - 0.5F) / size;
                    filled[side > 0 ? x : SIZE - 1 - x][y][z] = inside(fx, fy, fz, grow / size);
                }
            }
        }
        if (size >= VESSELS_FROM)
        {
            for (var vessel : VESSELS)
            {
                for (int x = vessel[0]; x < vessel[3]; x++)
                {
                    for (int y = vessel[1]; y < vessel[4]; y++)
                    {
                        for (int z = vessel[2]; z < vessel[5]; z++)
                            filled[side > 0 ? x : SIZE - 1 - x][y][z] = true;
                    }
                }
            }
        }
        return filled;
    }

    /** The height the bridge meets the twin at, the pixel over its near atrium at rest, which the atrium swells into. */
    private static int restTop()
    {
        int y = 0;
        for (int at = 0; at < SIZE; at++)
        {
            if (inside(JOIN_X + 0.5F, at + 0.5F, JOIN_Z + 0.5F, 0))
                y = at + 1;
        }
        return y;
    }

    private static boolean inside(float x, float y, float z, float grow)
    {
        float low = Math.clamp((BODY_Y - y) / BODY_RY, 0, 1);
        float narrow = 1 - APEX_NARROW * low;
        float bodyX = BODY_X + APEX_LEAN * low;
        if (ellipsoid(x, y, z, bodyX, BODY_Y, BODY_Z, (BODY_RX + grow) * narrow, BODY_RY + grow, (BODY_RZ + grow) * narrow))
            return true;
        for (var atrium : ATRIA)
        {
            if (ellipsoid(x, y, z, atrium[0], atrium[1], atrium[2], atrium[3] + grow, atrium[4] + grow, atrium[5] + grow))
                return true;
        }
        return false;
    }

    private static boolean ellipsoid(float x, float y, float z, float cx, float cy, float cz, float rx, float ry, float rz)
    {
        float dx = (x - cx) / rx, dy = (y - cy) / ry, dz = (z - cz) / rz;
        return dx * dx + dy * dy + dz * dz <= 1;
    }

    /** Where the twin's grid lies from the first heart's, in its pixels along X. */
    public static int offset(int side)
    {
        return side * OFFSET;
    }

    /**
     * Which side the twin grows on, +1 or -1 along the first heart's X: the high side unless the floor there is missing or walled and the low side's isn't.
     *
     * @param floor        the floor around the heart from the tile, by block, {@code null} if not yet found
     * @param quarterTurns how far the rooted heart has settled, so its X is found in the world
     */
    public static int side(boolean[][] floor, int quarterTurns)
    {
        if (floor == null || free(floor, 1, quarterTurns) || !free(floor, -1, quarterTurns))
            return 1;
        return -1;
    }

    private static boolean free(boolean[][] floor, int side, int quarterTurns)
    {
        int dx = side, dz = 0;
        for (int i = 0; i < (quarterTurns & 3); i++)
        {
            int turned = -dz;
            dz = dx;
            dx = turned;
        }
        int middle = floor.length / 2;
        return floor[middle + dx][middle + dz];
    }

    /**
     * The vein from the first heart to the twin, pixel by pixel from the first heart's flesh to over the twin's near atrium, in the first heart's pixels.
     * It leaves the heart at the first height and row it can reach the twin's side from clear of bone and veins, then sags down to the twin, every pixel
     * joined face to face to the last.
     *
     * @param rest    the first heart at rest, which the bridge starts on
     * @param swelled the first heart swelled, which only the bridge's first pixel may lie in, as only its last may lie in the twin swelled
     * @param veins  the first heart's veins
     * @param cage   its cage, from {@link HeartCage#grow}
     */
    public static List<int[]> bridge(boolean[][][] rest, boolean[][][] swelled, int[][][] veins, int[][][] cage, int side)
    {
        var twin = new boolean[][][][] { voxels(1, false, side), voxels(1, true, side) };
        int joinX = offset(side) + (side > 0 ? JOIN_X : SIZE - 1 - JOIN_X), joinY = JOIN_Y, joinZ = JOIN_Z;
        for (int height : BRIDGE_HEIGHTS)
        {
            for (int row : BRIDGE_ROWS)
            {
                var path = trace(rest, swelled, veins, cage, twin, side, height, row, joinX, joinY, joinZ);
                if (path != null)
                    return path;
            }
        }
        return List.of();
    }

    private static int[] start(boolean[][][] rest, int side, int y, int z)
    {
        // The outermost flesh on the twin's side, then a pixel past it
        for (int i = 0; i < SIZE; i++)
        {
            int x = side > 0 ? SIZE - 1 - i : i;
            if (rest[x][y][z])
                return new int[] { x + side, y, z };
        }
        return null;
    }

    private static List<int[]> trace(boolean[][][] rest, boolean[][][] swelled, int[][][] veins, int[][][] cage, boolean[][][][] twin, int side,
                                     int height, int row, int joinX, int joinY, int joinZ)
    {
        var from = start(rest, side, height, row);
        if (from == null)
            return null;

        // Out level from the heart through the ribs, past the cage's side, then down and across to the twin
        var path = new ArrayList<int[]>();
        int[] at = from.clone();
        path.add(at.clone());
        int past = side > 0 ? SIZE : -1;
        while (at[0] != past)
        {
            at[0] += side;
            path.add(at.clone());
        }
        while (at[2] != joinZ || at[1] != joinY || at[0] != joinX)
        {
            // Steps down before across, so it sags rather than runs square
            if (at[1] > joinY && (at[1] - joinY >= Math.abs(joinX - at[0]) || at[0] == joinX))
                at[1]--;
            else if (at[2] != joinZ)
                at[2] += Integer.signum(joinZ - at[2]);
            else if (at[0] != joinX)
                at[0] += Integer.signum(joinX - at[0]);
            else
                at[1] += Integer.signum(joinY - at[1]);
            path.add(at.clone());
        }

        for (int i = 0; i < path.size(); i++)
        {
            var pixel = path.get(i);
            if (!clear(rest, swelled, veins, cage, twin, side, pixel, i == 0, i == path.size() - 1))
                return null;
        }
        return thin(path);
    }

    private static boolean clear(boolean[][][] rest, boolean[][][] swelled, int[][][] veins, int[][][] cage, boolean[][][][] twin, int side, int[] pixel, boolean first, boolean last)
    {
        int x = pixel[0], y = pixel[1], z = pixel[2];
        if (y < 0 || y >= SIZE || z < 0 || z >= SIZE)
            return false;
        if (x >= 0 && x < SIZE)
        {
            if (rest[x][y][z] || !first && swelled[x][y][z])
                return false;
            if (veins[x][y][z] != HeartVeins.NONE || cage[x][y][z] != HeartCage.NONE)
                return false;
        }
        int tx = x - offset(side);
        return tx < 0 || tx >= SIZE || !twin[0][tx][y][z] && (last || !twin[1][tx][y][z]);
    }

    /** Drops the inner pixel of each turn, where the pixels either side of it meet across an edge, so the bridge runs aslant one pixel thin. */
    private static List<int[]> thin(List<int[]> path)
    {
        var thin = new ArrayList<>(path);
        for (int i = 1; i + 1 < thin.size(); i++)
        {
            int[] before = thin.get(i - 1), after = thin.get(i + 1);
            int dx = Math.abs(before[0] - after[0]), dy = Math.abs(before[1] - after[1]), dz = Math.abs(before[2] - after[2]);
            if (dx + dy + dz == 2 && dx <= 1 && dy <= 1 && dz <= 1)
                thin.remove(i);
        }
        return List.copyOf(thin);
    }
}
