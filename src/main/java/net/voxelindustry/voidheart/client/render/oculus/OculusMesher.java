package net.voxelindustry.voidheart.client.render.oculus;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.Arrays;

/**
 * Turns a chunk column slice of colored voxels into quads, hiding faces between filled voxels
 * and merging touching faces of the same color into a single rectangle. Hollow voxels, the open space under cover,
 * form a layer of their own: between a hollow and a solid voxel, both faces show, each drawn in its turn.
 * <p>
 * Each vertex is two ints: the position bytes x, y, z and the face ordinal, then the color as ABGR,
 * matching {@link OculusHologramPipeline#VERTEX_FORMAT} once written little endian.
 */
public final class OculusMesher
{
    public static final int CHUNK = 16;
    public static final int INTS_PER_VERTEX = 2;
    public static final int INTS_PER_QUAD = INTS_PER_VERTEX * 4;
    /**
     * Face ordinal past the six directions, for the void floor laid where the window bottom is empty.
     */
    public static final int FLOOR_FACE = 6;
    private static final int FLOOR_COLOR = 0xFF000000;
    /**
     * Alpha that marks hollow voxels, told apart by the shader.
     */
    public static final int HOLLOW_ALPHA = 0x40;
    /**
     * Stands for a hollow voxel of a neighboring chunk, which only decides which faces show.
     */
    public static final int HOLLOW_BORDER = HOLLOW_ALPHA << 24 | 1;

    /**
     * Face ordinals follow {@link net.minecraft.core.Direction}: down, up, north, south, west, east.
     * For each one, the normal axis and sign, then the two in-plane axes ordered so that u cross v is the normal,
     * which makes quads counter-clockwise when seen from outside.
     */
    private static final int[][] FACES = {
            { 1, -1, 0, 2 },
            { 1, 1, 2, 0 },
            { 2, -1, 1, 0 },
            { 2, 1, 0, 1 },
            { 0, -1, 2, 1 },
            { 0, 1, 1, 2 }
    };

    private OculusMesher()
    {
    }

    /**
     * Colors of a chunk column between the bottom and top of the projected window, plus a one voxel border
     * on the horizontal sides taken from the neighboring chunks. Zero is empty. Above and below the window is empty,
     * so the slice is closed where the window cuts through the land.
     */
    public record Volume(int height, int[] colors)
    {
        public static final int SIDE = CHUNK + 2;

        public static Volume empty(int height)
        {
            return new Volume(height, new int[SIDE * SIDE * height]);
        }

        public static int index(int x, int y, int z)
        {
            return x + 1 + (z + 1) * SIDE + y * SIDE * SIDE;
        }

        public void set(int x, int y, int z, int color)
        {
            colors[index(x, y, z)] = color;
        }

        public int get(int x, int y, int z)
        {
            if (y < 0 || y >= height)
                return 0;
            return colors[index(x, y, z)];
        }

        private int size(int axis)
        {
            return axis == 1 ? height : CHUNK;
        }
    }

    /**
     * @return the packed vertices, {@link #INTS_PER_QUAD} ints per quad
     */
    public static int[] mesh(Volume volume)
    {
        var out = new IntArrayList();
        var position = new int[3];
        var neighbor = new int[3];

        for (int face = 0; face < FACES.length; face++)
        {
            int axis = FACES[face][0];
            int sign = FACES[face][1];
            int uAxis = FACES[face][2];
            int vAxis = FACES[face][3];
            int sizeU = volume.size(uAxis);
            int sizeV = volume.size(vAxis);
            var mask = new int[sizeU * sizeV];

            for (int slice = 0; slice < volume.size(axis); slice++)
            {
                boolean any = false;
                for (int v = 0; v < sizeV; v++)
                {
                    for (int u = 0; u < sizeU; u++)
                    {
                        position[axis] = slice;
                        position[uAxis] = u;
                        position[vAxis] = v;
                        int color = volume.get(position[0], position[1], position[2]);

                        neighbor[0] = position[0];
                        neighbor[1] = position[1];
                        neighbor[2] = position[2];
                        neighbor[axis] += sign;
                        int next = volume.get(neighbor[0], neighbor[1], neighbor[2]);
                        boolean visible = color != 0 && (next == 0 || isHollow(next) != isHollow(color));

                        mask[u + v * sizeU] = visible ? color : 0;
                        any |= visible;
                    }
                }

                if (any)
                    mergeSlice(mask, sizeU, sizeV, face, axis, sign > 0 ? slice + 1 : slice, uAxis, vAxis, out);
            }
        }

        // A floor facing up under the whole window keeps the table under the hologram out of sight, where the window
        // bottom is empty and wherever the land dissolves to show the hollows
        var floor = new int[CHUNK * CHUNK];
        Arrays.fill(floor, FLOOR_COLOR);
        mergeSlice(floor, CHUNK, CHUNK, FLOOR_FACE, 1, 0, 2, 0, out);
        return out.toIntArray();
    }

    public static boolean isHollow(int color)
    {
        return color >>> 24 == HOLLOW_ALPHA;
    }

    private static void mergeSlice(int[] mask, int sizeU, int sizeV, int face, int axis, int plane, int uAxis, int vAxis, IntArrayList out)
    {
        for (int v = 0; v < sizeV; v++)
        {
            for (int u = 0; u < sizeU; )
            {
                int color = mask[u + v * sizeU];
                if (color == 0)
                {
                    u++;
                    continue;
                }

                int width = 1;
                while (u + width < sizeU && mask[u + width + v * sizeU] == color)
                    width++;

                int height = 1;
                grow:
                while (v + height < sizeV)
                {
                    for (int k = 0; k < width; k++)
                    {
                        if (mask[u + k + (v + height) * sizeU] != color)
                            break grow;
                    }
                    height++;
                }

                for (int dv = 0; dv < height; dv++)
                {
                    for (int du = 0; du < width; du++)
                        mask[u + du + (v + dv) * sizeU] = 0;
                }

                emitQuad(out, face, axis, plane, uAxis, u, width, vAxis, v, height, color);
                u += width;
            }
        }
    }

    private static void emitQuad(IntArrayList out, int face, int axis, int plane, int uAxis, int u, int width, int vAxis, int v, int height, int color)
    {
        int abgr = toAbgr(color);
        var corner = new int[3];
        corner[axis] = plane;

        // Counter-clockwise from outside: (0, 0), (1, 0), (1, 1), (0, 1) in the face's (u, v) frame
        for (int i = 0; i < 4; i++)
        {
            corner[uAxis] = u + (i == 1 || i == 2 ? width : 0);
            corner[vAxis] = v + (i >= 2 ? height : 0);
            out.add(corner[0] | corner[1] << 8 | corner[2] << 16 | face << 24);
            out.add(abgr);
        }
    }

    private static int toAbgr(int argb)
    {
        return argb & 0xFF00FF00 | (argb & 0xFF) << 16 | argb >> 16 & 0xFF;
    }
}
