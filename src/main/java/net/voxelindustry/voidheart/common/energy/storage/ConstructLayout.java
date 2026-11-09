package net.voxelindustry.voidheart.common.energy.storage;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Geometry of a rubrum construct around its core, in offsets from the core. Frame blocks stand on the cube's edges and
 * the faces stay open. Sockets are open face cells: the center of each face, and on the large cube the four cells
 * beside it too, so its face corners stay open as windows onto the core.
 * <p>
 * Socket order puts the six face centers first, so a core moved between sizes keeps them in place.
 */
public final class ConstructLayout
{
    public enum Size implements StringRepresentable
    {
        SMALL(3),
        LARGE(5);

        public static final Codec<Size> CODEC = StringRepresentable.fromEnum(Size::values);

        private final int edge;

        Size(int edge)
        {
            this.edge = edge;
        }

        public int edge()
        {
            return edge;
        }

        public int half()
        {
            return edge / 2;
        }

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * How a frame block looks: a whole block while loose, then once a construct forms around it, a beam along the
     * cube's edge it stands on, or a capped corner. Each formed part names the side of the cube it sits at on each
     * axis, negative, positive or along it, so its model can hug the cube's outer edge.
     */
    public enum FramePart implements StringRepresentable
    {
        LOOSE(0, 0, 0),
        CORNER_NNN(-1, -1, -1),
        CORNER_NNP(-1, -1, 1),
        CORNER_NPN(-1, 1, -1),
        CORNER_NPP(-1, 1, 1),
        CORNER_PNN(1, -1, -1),
        CORNER_PNP(1, -1, 1),
        CORNER_PPN(1, 1, -1),
        CORNER_PPP(1, 1, 1),
        PILLAR_NN(-1, 0, -1),
        PILLAR_NP(-1, 0, 1),
        PILLAR_PN(1, 0, -1),
        PILLAR_PP(1, 0, 1),
        BEAM_X_NN(0, -1, -1),
        BEAM_X_NP(0, -1, 1),
        BEAM_X_PN(0, 1, -1),
        BEAM_X_PP(0, 1, 1),
        BEAM_Z_NN(-1, -1, 0),
        BEAM_Z_NP(-1, 1, 0),
        BEAM_Z_PN(1, -1, 0),
        BEAM_Z_PP(1, 1, 0);

        private final int x;
        private final int y;
        private final int z;

        FramePart(int x, int y, int z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /**
         * The part at the cube's sides given by each sign, 0 meaning the block runs along that axis.
         */
        public static FramePart of(int x, int y, int z)
        {
            for (var part : values())
            {
                if (part != LOOSE && part.x == Integer.signum(x) && part.y == Integer.signum(y) && part.z == Integer.signum(z))
                    return part;
            }
            throw new IllegalArgumentException("No frame part along " + x + ", " + y + ", " + z);
        }

        public int x()
        {
            return x;
        }

        public int y()
        {
            return y;
        }

        public int z()
        {
            return z;
        }

        public boolean isCorner()
        {
            return x != 0 && y != 0 && z != 0;
        }

        public boolean isPillar()
        {
            return this != LOOSE && y == 0;
        }

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final int MAX_SOCKETS = 30;

    private static final List<BlockPos> SMALL_FRAME   = Collections.unmodifiableList(computeFrame(Size.SMALL));
    private static final List<BlockPos> LARGE_FRAME   = Collections.unmodifiableList(computeFrame(Size.LARGE));
    private static final List<BlockPos> SMALL_SOCKETS = Collections.unmodifiableList(computeSockets(Size.SMALL));
    private static final List<BlockPos> LARGE_SOCKETS = Collections.unmodifiableList(computeSockets(Size.LARGE));

    private ConstructLayout()
    {
    }

    public static List<BlockPos> frame(Size size)
    {
        return size == Size.SMALL ? SMALL_FRAME : LARGE_FRAME;
    }

    public static List<BlockPos> sockets(Size size)
    {
        return size == Size.SMALL ? SMALL_SOCKETS : LARGE_SOCKETS;
    }

    public static int socketCount(Size size)
    {
        return sockets(size).size();
    }

    private static int extremes(Size size, int x, int y, int z)
    {
        var half = size.half();
        return (Math.abs(x) == half ? 1 : 0) + (Math.abs(y) == half ? 1 : 0) + (Math.abs(z) == half ? 1 : 0);
    }

    public static boolean isInside(Size size, BlockPos offset)
    {
        var half = size.half();
        return Math.abs(offset.getX()) <= half && Math.abs(offset.getY()) <= half && Math.abs(offset.getZ()) <= half;
    }

    /**
     * The part a frame block takes at this offset, or null where the construct has no frame.
     */
    public static @Nullable FramePart partAt(Size size, BlockPos offset)
    {
        if (!isInside(size, offset))
            return null;
        var count = extremes(size, offset.getX(), offset.getY(), offset.getZ());
        if (count < 2)
            return null;
        var half = size.half();
        return FramePart.of(Math.abs(offset.getX()) == half ? offset.getX() : 0, Math.abs(offset.getY()) == half ? offset.getY() : 0,
                Math.abs(offset.getZ()) == half ? offset.getZ() : 0);
    }

    /**
     * Index of the socket at this offset, or -1.
     */
    public static int socketIndex(Size size, BlockPos offset)
    {
        return sockets(size).indexOf(offset);
    }

    /**
     * The direction a bundle in this socket points: into the cube, across the face it sits in.
     */
    public static Direction inward(Size size, BlockPos socket)
    {
        var half = size.half();
        if (socket.getX() == half)
            return Direction.WEST;
        if (socket.getX() == -half)
            return Direction.EAST;
        if (socket.getY() == half)
            return Direction.DOWN;
        if (socket.getY() == -half)
            return Direction.UP;
        if (socket.getZ() == half)
            return Direction.NORTH;
        return Direction.SOUTH;
    }

    private static List<BlockPos> computeFrame(Size size)
    {
        var out = new ArrayList<BlockPos>();
        var half = size.half();
        for (var y = -half; y <= half; y++)
        {
            for (var z = -half; z <= half; z++)
            {
                for (var x = -half; x <= half; x++)
                {
                    if (extremes(size, x, y, z) >= 2)
                        out.add(new BlockPos(x, y, z));
                }
            }
        }
        return out;
    }

    private static List<BlockPos> computeSockets(Size size)
    {
        var out = new ArrayList<BlockPos>();
        var half = size.half();
        for (var face : Direction.values())
            out.add(BlockPos.ZERO.relative(face, half));

        for (var face : Direction.values())
        {
            var center = BlockPos.ZERO.relative(face, half);
            for (var side : Direction.values())
            {
                if (side.getAxis() == face.getAxis())
                    continue;
                for (var step = 1; step < half; step++)
                    out.add(center.relative(side, step));
            }
        }
        return out;
    }
}
