package net.voxelindustry.voidheart.client.render.heart;

import java.util.ArrayList;
import java.util.List;

/**
 * The pocket heart as whole pixels of its block, and the faces that show of it. Built on the block's own 16 pixel grid so its texture never meets a part
 * of a pixel, and drawn only where a filled pixel meets an empty one, merged into rectangles, so no two faces ever lie on one plane over each other.
 */
public final class HeartShape
{
    public static final int SIZE = 16;

    /** The body, an egg leaning its apex aside, in pixels: centre, radii, and how far its bottom leans along X. */
    private static final float BODY_X = 8, BODY_Y = 7.2F, BODY_Z = 8;
    private static final float BODY_RX = 3.8F, BODY_RY = 4.6F, BODY_RZ = 3.3F;
    private static final float APEX_LEAN = -1.2F;
    /** The lower half narrows toward the apex by this share at its tip. */
    private static final float APEX_NARROW = 0.3F;

    /** The two atria over the body: centre and radii. */
    private static final float[][] ATRIA = {
            { 5.4F, 11, 8.6F, 1.9F, 1.6F, 1.8F },
            { 10.4F, 11.3F, 7.6F, 2, 1.7F, 2 },
    };

    /** Swelled with a beat, each rounded part is this much wider, enough to step out by a pixel over most of its surface. */
    private static final float SWELL = 0.6F;

    /** The vessels leaving the top, as whole pixel boxes from their low corner to their high one: aorta, its arch, descending aorta, pulmonary trunk, vena cava. */
    private static final int[][] VESSELS = {
            { 7, 10, 7, 9, 14, 9 },
            { 7, 14, 7, 11, 15, 9 },
            { 10, 12, 7, 11, 14, 9 },
            { 5, 11, 9, 7, 14, 11 },
            { 4, 11, 7, 5, 14, 8 },
    };

    /**
     * A rectangle of the surface. {@code axis} is the one it faces along (0 X, 1 Y, 2 Z), {@code plane} where it lies on it. {@code a} and {@code b} span
     * the other two axes in order: Y and Z facing X, X and Z facing Y, X and Y facing Z.
     */
    public record Face(int axis, boolean positive, int plane, int a0, int b0, int a1, int b1)
    {
        public int area()
        {
            return (a1 - a0) * (b1 - b0);
        }
    }

    private HeartShape()
    {
    }

    public static boolean[][][] voxels(boolean swelled)
    {
        float grow = swelled ? SWELL : 0;
        var filled = new boolean[SIZE][SIZE][SIZE];
        for (int x = 0; x < SIZE; x++)
        {
            for (int y = 0; y < SIZE; y++)
            {
                for (int z = 0; z < SIZE; z++)
                    filled[x][y][z] = inside(x + 0.5F, y + 0.5F, z + 0.5F, grow);
            }
        }
        for (var vessel : VESSELS)
        {
            for (int x = vessel[0]; x < vessel[3]; x++)
            {
                for (int y = vessel[1]; y < vessel[4]; y++)
                {
                    for (int z = vessel[2]; z < vessel[5]; z++)
                        filled[x][y][z] = true;
                }
            }
        }
        return filled;
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

    /** Every face where a filled pixel meets an empty one, merged greedily into rectangles on each plane. */
    public static List<Face> faces(boolean[][][] filled)
    {
        return faces(filled, filled);
    }

    /** Every face where a filled pixel meets one that isn't solid, so pixels of something else laid against it hide its faces there. */
    public static List<Face> faces(boolean[][][] filled, boolean[][][] solid)
    {
        return faces(filled, solid, null);
    }

    /**
     * As {@link #faces(boolean[][][], boolean[][][])}, leaving out the face cells marked in {@code skipped}, by {@link #side} then pixel, so something
     * else can draw them in their place.
     */
    public static List<Face> faces(boolean[][][] filled, boolean[][][] solid, boolean[][][][] skipped)
    {
        var faces = new ArrayList<Face>();
        for (int axis = 0; axis < 3; axis++)
        {
            for (int side = 0; side < 2; side++)
            {
                boolean positive = side == 1;
                for (int layer = 0; layer < SIZE; layer++)
                    merge(faces, filled, solid, skipped == null ? null : skipped[side(axis, positive)], axis, positive, layer);
            }
        }
        return faces;
    }

    private static void merge(List<Face> faces, boolean[][][] filled, boolean[][][] solid, boolean[][][] skipped, int axis, boolean positive, int layer)
    {
        var open = new boolean[SIZE][SIZE];
        for (int a = 0; a < SIZE; a++)
        {
            for (int b = 0; b < SIZE; b++)
                open[a][b] = at(filled, axis, layer, a, b) && !at(solid, axis, layer + (positive ? 1 : -1), a, b)
                        && (skipped == null || !at(skipped, axis, layer, a, b));
        }

        int plane = positive ? layer + 1 : layer;
        for (int a = 0; a < SIZE; a++)
        {
            for (int b = 0; b < SIZE; b++)
            {
                if (!open[a][b])
                    continue;
                int b1 = b + 1;
                while (b1 < SIZE && open[a][b1])
                    b1++;
                int a1 = a + 1;
                while (a1 < SIZE && rowOpen(open[a1], b, b1))
                    a1++;
                for (int clearA = a; clearA < a1; clearA++)
                {
                    for (int clearB = b; clearB < b1; clearB++)
                        open[clearA][clearB] = false;
                }
                faces.add(new Face(axis, positive, plane, a, b, a1, b1));
            }
        }
    }

    private static boolean rowOpen(boolean[] row, int from, int to)
    {
        for (int b = from; b < to; b++)
        {
            if (!row[b])
                return false;
        }
        return true;
    }

    /** One of the six sides of a pixel, numbered from the axis it faces along and which way. */
    public static int side(int axis, boolean positive)
    {
        return axis * 2 + (positive ? 1 : 0);
    }

    /** The pixel at a layer along an axis and a place on the other two, empty outside the block. */
    static boolean at(boolean[][][] filled, int axis, int layer, int a, int b)
    {
        if (layer < 0 || layer >= SIZE)
            return false;
        return switch (axis)
        {
            case 0 -> filled[layer][a][b];
            case 1 -> filled[a][layer][b];
            default -> filled[a][b][layer];
        };
    }
}
