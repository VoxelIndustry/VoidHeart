package net.voxelindustry.voidheart.common.world.daedalus;

/**
 * Where the parts of a shroud gate stand: a frame of dark voidstone one block proud of a wall, its seal set in the wall between the pillars, and the altar
 * in front of it. Seen from the room the frame is five wide and nine tall, and the seal three wide and five tall above a threshold.
 *
 * @param x      y z the block of the wall behind the threshold's middle, at the height of the room's walkable floor
 * @param facing the {@link Compass} direction from the wall into the room
 */
public record ShroudGate(int x, int y, int z, int facing)
{
    public static final int SEAL_HALF_WIDTH = 1;
    public static final int SEAL_BOTTOM = 1;
    public static final int SEAL_HEIGHT = 5;
    public static final int FRAME_HALF_WIDTH = SEAL_HALF_WIDTH + 1;
    public static final int LINTEL = SEAL_BOTTOM + SEAL_HEIGHT;
    public static final int CORNICE = LINTEL + 1;
    public static final int KEYSTONE = CORNICE + 1;
    /** How far in front of the wall the altar stands, leaving two blocks to step up to the frame. */
    public static final int ALTAR_DEPTH = 4;

    public enum Part
    {
        NONE,
        /** Upright, from the threshold to the lintel. */
        PILLAR,
        /** Lying across the opening: threshold, lintel and cornice. */
        BEAM,
        /** Set end on, facing the room. */
        KEYSTONE,
        SEAL,
        ALTAR;

        public boolean isFrame()
        {
            return this == PILLAR || this == BEAM || this == KEYSTONE;
        }
    }

    /**
     * @param altarFacing the direction from the altar toward its gate
     */
    public static ShroudGate fromAltar(int altarX, int altarY, int altarZ, int altarFacing)
    {
        return new ShroudGate(altarX + Compass.dx(altarFacing) * ALTAR_DEPTH, altarY, altarZ + Compass.dz(altarFacing) * ALTAR_DEPTH, Compass.opposite(altarFacing));
    }

    /** The direction lateral offsets count along, a quarter turn from the facing. */
    public int lateral()
    {
        return Compass.clockwise(facing);
    }

    public int worldX(int across, int depth)
    {
        return x + Compass.dx(lateral()) * across + Compass.dx(facing) * depth;
    }

    public int worldZ(int across, int depth)
    {
        return z + Compass.dz(lateral()) * across + Compass.dz(facing) * depth;
    }

    public int altarX()
    {
        return worldX(0, ALTAR_DEPTH);
    }

    public int altarY()
    {
        return y;
    }

    public int altarZ()
    {
        return worldZ(0, ALTAR_DEPTH);
    }

    /** The altar faces its gate, so the contour on its top stands upright for whoever offers at it. */
    public int altarFacing()
    {
        return Compass.opposite(facing);
    }

    public Part partAt(int blockX, int blockY, int blockZ)
    {
        int dx = blockX - x;
        int dz = blockZ - z;
        int depth = dx * Compass.dx(facing) + dz * Compass.dz(facing);
        int across = dx * Compass.dx(lateral()) + dz * Compass.dz(lateral());
        int up = blockY - y;
        return localPart(across, up, depth);
    }

    static Part localPart(int across, int up, int depth)
    {
        int side = Math.abs(across);
        if (depth == ALTAR_DEPTH)
            return across == 0 && up == 0 ? Part.ALTAR : Part.NONE;
        if (depth == 0)
            return side <= SEAL_HALF_WIDTH && up >= SEAL_BOTTOM && up < LINTEL ? Part.SEAL : Part.NONE;
        if (depth != 1 || up < 0)
            return Part.NONE;
        if (up == KEYSTONE)
            return across == 0 ? Part.KEYSTONE : Part.NONE;
        if (up == CORNICE)
            return side <= FRAME_HALF_WIDTH ? Part.BEAM : Part.NONE;
        if (side == FRAME_HALF_WIDTH)
            return up <= LINTEL ? Part.PILLAR : Part.NONE;
        if (side < FRAME_HALF_WIDTH && (up == 0 || up == LINTEL))
            return Part.BEAM;
        return Part.NONE;
    }

    /** Every block of the gate, the seal's included. */
    public void forEach(PartConsumer consumer)
    {
        for (int depth = 0; depth <= ALTAR_DEPTH; depth++)
        {
            for (int up = 0; up <= KEYSTONE; up++)
            {
                for (int across = -FRAME_HALF_WIDTH; across <= FRAME_HALF_WIDTH; across++)
                {
                    var part = localPart(across, up, depth);
                    if (part != Part.NONE)
                        consumer.accept(worldX(across, depth), y + up, worldZ(across, depth), part);
                }
            }
        }
    }

    /**
     * The seal's blocks, each with its column across the opening from 0 on the left as seen from the room, and its row from 0 at the bottom.
     */
    public void forEachSealBlock(SealConsumer consumer)
    {
        for (int row = 0; row < SEAL_HEIGHT; row++)
        {
            for (int column = 0; column <= 2 * SEAL_HALF_WIDTH; column++)
            {
                // Seen from the room, the lateral direction runs right to left
                int across = SEAL_HALF_WIDTH - column;
                consumer.accept(worldX(across, 0), y + SEAL_BOTTOM + row, worldZ(across, 0), column, row);
            }
        }
    }

    @FunctionalInterface
    public interface PartConsumer
    {
        void accept(int x, int y, int z, Part part);
    }

    @FunctionalInterface
    public interface SealConsumer
    {
        void accept(int x, int y, int z, int column, int row);
    }
}
