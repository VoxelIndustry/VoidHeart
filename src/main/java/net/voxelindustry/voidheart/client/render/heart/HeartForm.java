package net.voxelindustry.voidheart.client.render.heart;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.client.render.heart.HeartShape.SIZE;

/**
 * The faces of a heart and the veins on it. Veins have no thickness, as vines don't: where one runs over the heart it is painted into the flesh's own
 * faces, which the flesh then leaves out, and where it hangs free it is a strand of two crossed planes one pixel wide. So nothing lies over the flesh
 * and nothing shares a plane.
 *
 * @param flesh   merged faces of the flesh, without the painted ones
 * @param paint   the flesh faces a vein runs over, one per pixel so each lights with its own place along its vein
 * @param strands the pixels where a vein hangs free
 */
public record HeartForm(List<HeartShape.Face> flesh, List<VeinFace> paint, List<Strand> strands)
{
    /** @param along how far along its vein this pixel lies */
    public record VeinFace(HeartShape.Face face, int along)
    {
    }

    public record Strand(int x, int y, int z, int along)
    {
    }

    private static final int[][] SIDES = { { -1, 0, 0 }, { 1, 0, 0 }, { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 } };

    /**
     * @param veins     how far along its vein each pixel lies, from {@link HeartVeins#grow}, or null for a heart without veins
     * @param veinReach vein pixels are shown up to this far along, so veins grow by raising it
     */
    public static HeartForm build(boolean swelled, int[][][] veins, int veinReach)
    {
        var flesh = HeartShape.voxels(swelled);
        if (veins == null)
            return new HeartForm(HeartShape.faces(flesh), List.of(), List.of());

        var rest = HeartShape.voxels(false);
        var painted = new boolean[6][SIZE][SIZE][SIZE];
        var paintAlong = new int[6][SIZE][SIZE][SIZE];
        var strands = new ArrayList<Strand>();
        for (int x = 0; x < SIZE; x++)
        {
            for (int y = 0; y < SIZE; y++)
            {
                for (int z = 0; z < SIZE; z++)
                {
                    int along = veins[x][y][z];
                    if (along == HeartVeins.NONE || along >= veinReach)
                        continue;
                    if (flesh[x][y][z])
                    {
                        // Swelled over by a beat, so the vein shows on the swelled pixel's own faces
                        for (int side = 0; side < 6; side++)
                        {
                            if (!filled(flesh, x + SIDES[side][0], y + SIDES[side][1], z + SIDES[side][2]))
                                paint(painted, paintAlong, side, x, y, z, along);
                        }
                    }
                    else if (touches(rest, x, y, z))
                    {
                        for (int side = 0; side < 6; side++)
                        {
                            int nx = x + SIDES[side][0], ny = y + SIDES[side][1], nz = z + SIDES[side][2];
                            if (filled(flesh, nx, ny, nz))
                                paint(painted, paintAlong, side ^ 1, nx, ny, nz, along);
                        }
                    }
                    else
                        strands.add(new Strand(x, y, z, along));
                }
            }
        }

        var paint = new ArrayList<VeinFace>();
        for (int side = 0; side < 6; side++)
        {
            int axis = side / 2;
            boolean positive = (side & 1) == 1;
            for (int x = 0; x < SIZE; x++)
            {
                for (int y = 0; y < SIZE; y++)
                {
                    for (int z = 0; z < SIZE; z++)
                    {
                        if (painted[side][x][y][z])
                            paint.add(new VeinFace(face(axis, positive, x, y, z), paintAlong[side][x][y][z]));
                    }
                }
            }
        }
        return new HeartForm(HeartShape.faces(flesh, flesh, painted), List.copyOf(paint), List.copyOf(strands));
    }

    private static void paint(boolean[][][][] painted, int[][][][] paintAlong, int side, int x, int y, int z, int along)
    {
        if (!painted[side][x][y][z] || along < paintAlong[side][x][y][z])
            paintAlong[side][x][y][z] = along;
        painted[side][x][y][z] = true;
    }

    private static boolean touches(boolean[][][] flesh, int x, int y, int z)
    {
        for (var side : SIDES)
        {
            if (filled(flesh, x + side[0], y + side[1], z + side[2]))
                return true;
        }
        return false;
    }

    private static boolean filled(boolean[][][] flesh, int x, int y, int z)
    {
        return x >= 0 && y >= 0 && z >= 0 && x < SIZE && y < SIZE && z < SIZE && flesh[x][y][z];
    }

    /** The face of one pixel on one side. */
    static HeartShape.Face face(int axis, boolean positive, int x, int y, int z)
    {
        return switch (axis)
        {
            case 0 -> new HeartShape.Face(0, positive, positive ? x + 1 : x, y, z, y + 1, z + 1);
            case 1 -> new HeartShape.Face(1, positive, positive ? y + 1 : y, x, z, x + 1, z + 1);
            default -> new HeartShape.Face(2, positive, positive ? z + 1 : z, x, y, x + 1, y + 1);
        };
    }
}
